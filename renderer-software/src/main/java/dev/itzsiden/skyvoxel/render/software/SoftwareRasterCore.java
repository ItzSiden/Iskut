package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.RenderQuality;
import dev.itzsiden.skyvoxel.render.api.SkyboxSettings;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Window-free CPU rasterizer core (so it can be tested/benchmarked headless).
 *
 * <p>Pipeline per frame:
 * <ol>
 *   <li>{@link #draw}: transform a mesh once with a combined MVP matrix, trivially reject/accept
 *       triangles with outcodes, clip only against near/far, set up screen-space gradients and
 *       <em>bin</em> each triangle into 16-row bands. No pixels are touched here.</li>
 *   <li>{@link #flush}: worker threads (plus the caller) grab bands, clear them and rasterize the
 *       binned triangles. Bands are disjoint, so there are no locks and no overdraw races.</li>
 * </ol>
 *
 * <p>Inner loops: scanline spans, depth stored as 1/w (also gives perspective-correct UVs),
 * 16-pixel perspective runs, nearest sampling from pre-shaded textures (FAST) or per-pixel
 * interpolated vertex colour (SMOOTH), smooth depth-based fog, optional alpha test.
 */
final class SoftwareRasterCore implements AutoCloseable {
    private static final int VS = 9;   // clip vertex: x y z w u v r g b
    private static final int SS = 8;   // screen vertex: sx sy iw u v r g b
    private static final int CLIP_CAP = 8;
    private static final int OC_NEAR = 16;
    private static final int OC_FAR = 32;
    private static final int OC_Z = OC_NEAR | OC_FAR;

    private static final int TF = 24;  // floats per queued triangle
    private static final int TI = 4;   // ints per queued triangle
    static final int BAND_ROWS = 16;
    private static final int RUN = 16; // perspective-correct run length in pixels

    private static final int KIND_SOLID_FLAT = 0;
    private static final int KIND_SOLID_SMOOTH = 1;
    private static final int KIND_TEX_FLAT = 2;
    private static final int KIND_TEX_SMOOTH = 3;

    final int width;
    final int height;
    private final int[] pixels;
    private final float[] depth;
    private final int numBands;

    // Worker pool
    private final int workerThreads;
    private final ExecutorService pool;
    private final AtomicInteger nextBand = new AtomicInteger();
    private volatile Throwable failure;

    // Frame state
    private float[] viewProjection = identity();
    private int clearColor;
    private int skyTopColor, skyHorizonColor, skyLowerColor;
    private float sunScreenX, sunScreenY, sunRadius;
    private boolean sunVisible;
    private int fogR, fogG, fogB;
    private boolean fogOn;
    private float fogStart, fogEnd, fogInvRange;
    private volatile RenderQuality quality = RenderQuality.FAST;

    // Queued triangles
    private float[] tf = new float[TF * 4096];
    private int[] ti = new int[TI * 4096];
    private int triCount;
    private final int[][] bandList;
    private final int[] bandCount;
    private SoftwareTexture[] textureTable = new SoftwareTexture[8];

    // Per-draw scratch
    private final float[] mvp = new float[16];
    private float[] clip = new float[VS * 1024];
    private float[] scr = new float[SS * 1024];
    private byte[] outcodes = new byte[1024];
    private final float[] polyA = new float[CLIP_CAP * VS];
    private final float[] polyB = new float[CLIP_CAP * VS];
    private final float[] polyS = new float[CLIP_CAP * SS];
    private final float[] colorTmp = new float[9];

    // Per-draw settings used by triangle setup
    private SoftwareTexture curTex;
    private float curTexW, curTexH;
    private boolean curFlat;

    // Statistics (for HUD / benchmarks)
    private int statTrianglesIn;
    private int statTrianglesQueued;

    SoftwareRasterCore(int[] pixels, int width, int height, int threads) {
        if (pixels.length < width * height) throw new IllegalArgumentException("Pixel buffer too small.");
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.depth = new float[width * height];
        this.numBands = (height + BAND_ROWS - 1) / BAND_ROWS;
        this.bandList = new int[numBands][];
        this.bandCount = new int[numBands];
        for (int b = 0; b < numBands; b++) bandList[b] = new int[256];
        this.workerThreads = Math.max(0, threads - 1);
        if (workerThreads > 0) {
            AtomicInteger counter = new AtomicInteger();
            pool = Executors.newFixedThreadPool(workerThreads, runnable -> {
                Thread t = new Thread(runnable, "Iskut-Raster-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
        } else {
            pool = null;
        }
    }

    int threadCount() { return workerThreads + 1; }
    RenderQuality quality() { return quality; }
    void setQuality(RenderQuality q) { this.quality = q; }
    int trianglesIn() { return statTrianglesIn; }
    int trianglesQueued() { return statTrianglesQueued; }

    // ------------------------------------------------------------------------------------------
    // Frame control
    // ------------------------------------------------------------------------------------------

    void begin(float[] vp, float cr, float cg, float cb, float fogStartDistance, float fogEndDistance) {
        begin(vp, cr, cg, cb, fogStartDistance, fogEndDistance, SkyboxSettings.fromHorizon(cr, cg, cb));
    }

    void begin(float[] vp, float cr, float cg, float cb, float fogStartDistance, float fogEndDistance,
               SkyboxSettings skybox) {
        System.arraycopy(vp, 0, viewProjection, 0, 16);
        fogR = to255(cr); fogG = to255(cg); fogB = to255(cb);
        clearColor = (fogR << 16) | (fogG << 8) | fogB;
        skyTopColor = rgb(skybox.topRed(), skybox.topGreen(), skybox.topBlue());
        skyHorizonColor = rgb(skybox.horizonRed(), skybox.horizonGreen(), skybox.horizonBlue());
        skyLowerColor = rgb(skybox.lowerRed(), skybox.lowerGreen(), skybox.lowerBlue());
        sunRadius = height * skybox.sunRadiusFraction();
        sunVisible = skybox.sunEnabled() && projectSun(vp, skybox.sunDirectionX(), skybox.sunDirectionY(), skybox.sunDirectionZ());
        fogOn = fogEndDistance > fogStartDistance && fogEndDistance > 0.0f;
        fogStart = fogStartDistance;
        fogEnd = fogEndDistance;
        fogInvRange = fogOn ? 1.0f / (fogEnd - fogStart) : 0.0f;
        triCount = 0;
        Arrays.fill(bandCount, 0);
        statTrianglesIn = 0;
        statTrianglesQueued = 0;
    }

    private boolean projectSun(float[] m, float x, float y, float z) {
        // A direction uses homogeneous w=0, so camera translation cannot move the sun.
        float cx = m[0] * x + m[4] * y + m[8] * z;
        float cy = m[1] * x + m[5] * y + m[9] * z;
        float cw = m[3] * x + m[7] * y + m[11] * z;
        if (!(cw > 0.0001f)) return false;
        float nx = cx / cw, ny = cy / cw;
        sunScreenX = (nx * 0.5f + 0.5f) * width;
        sunScreenY = (0.5f - ny * 0.5f) * height;
        float halo = Math.max(2.0f, sunRadius * 3.0f);
        return sunScreenX > -halo && sunScreenX < width + halo
            && sunScreenY > -halo && sunScreenY < height + halo;
    }

    private static int rgb(float r, float g, float b) { return (to255(r) << 16) | (to255(g) << 8) | to255(b); }
    private static int mixChannel(int a, int b, float t) { return Math.max(0, Math.min(255, Math.round(a + (b - a) * t))); }
    private static int mixRgb(int a, int b, float t) {
        int r = mixChannel((a >>> 16) & 255, (b >>> 16) & 255, t);
        int g = mixChannel((a >>> 8) & 255, (b >>> 8) & 255, t);
        int bl = mixChannel(a & 255, b & 255, t);
        return (r << 16) | (g << 8) | bl;
    }
    private static int alphaMix(int source, int over, float alpha) { return mixRgb(source, over, Math.max(0, Math.min(1, alpha))); }

    private void fillSkyRows(int rowMin, int rowMax) {
        int horizonY = Math.max(1, Math.round(height * 0.70f));
        float invTop = 1.0f / Math.max(1, horizonY);
        float invBottom = 1.0f / Math.max(1, height - horizonY);
        int haloRadius = Math.max(2, Math.round(sunRadius * 3.0f));
        int diskRadius = Math.max(2, Math.round(sunRadius));
        int x0 = Math.max(0, (int) Math.floor(sunScreenX - haloRadius));
        int x1 = Math.min(width - 1, (int) Math.ceil(sunScreenX + haloRadius));
        for (int y = rowMin; y < rowMax; y++) {
            int rowColor;
            if (y < horizonY) {
                float t = Math.max(0.0f, Math.min(1.0f, y * invTop));
                t = t * t * (3.0f - 2.0f * t);
                rowColor = mixRgb(skyTopColor, skyHorizonColor, t);
            } else {
                float t = Math.max(0.0f, Math.min(1.0f, (y - horizonY) * invBottom));
                rowColor = mixRgb(skyHorizonColor, skyLowerColor, t);
            }
            int base = y * width;
            Arrays.fill(pixels, base, base + width, rowColor);
            if (!sunVisible || y < sunScreenY - haloRadius || y > sunScreenY + haloRadius) continue;
            float dy = y + 0.5f - sunScreenY;
            float distanceY = Math.abs(dy);
            if (distanceY >= haloRadius) continue;
            float outerX = (float) Math.sqrt(Math.max(0.0f, haloRadius * (double) haloRadius - dy * dy));
            int left = Math.max(0, (int) Math.ceil(sunScreenX - outerX));
            int right = Math.min(width - 1, (int) Math.floor(sunScreenX + outerX));
            for (int x = left; x <= right; x++) {
                float dx = x + 0.5f - sunScreenX;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                int index = base + x;
                if (distance <= diskRadius) {
                    pixels[index] = 0xFFF5D0;
                } else {
                    float haloT = 1.0f - (distance - diskRadius) / Math.max(1.0f, haloRadius - diskRadius);
                    pixels[index] = alphaMix(pixels[index], 0xFFE7A1, 0.22f * haloT * haloT);
                }
            }
        }
    }

    void flush() {
        nextBand.set(0);
        failure = null;
        if (workerThreads == 0) {
            runBands();
        } else {
            CountDownLatch latch = new CountDownLatch(workerThreads);
            for (int i = 0; i < workerThreads; i++) {
                pool.execute(() -> {
                    try { runBands(); } catch (Throwable t) { failure = t; } finally { latch.countDown(); }
                });
            }
            try { runBands(); } catch (Throwable t) { failure = t; }
            boolean interrupted = false;
            while (true) {
                try { latch.await(); break; } catch (InterruptedException e) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
        Throwable t = failure;
        if (t != null) throw new IllegalStateException("Rasterizer worker failed.", t);
    }

    private void runBands() {
        int band;
        while ((band = nextBand.getAndIncrement()) < numBands) rasterBand(band);
    }

    @Override
    public void close() {
        if (pool != null) pool.shutdownNow();
    }

    // ------------------------------------------------------------------------------------------
    // Submission: transform, cull, clip, setup, bin
    // ------------------------------------------------------------------------------------------

    void draw(SoftwareMesh mesh, SoftwareTexture texture, float[] model, float tr, float tg, float tb) {
        // Combined matrix once per draw (column-major: element = column * 4 + row).
        final float[] vp = viewProjection;
        final float[] m = mvp;
        for (int c = 0; c < 4; c++) {
            float m0 = model[c * 4], m1 = model[c * 4 + 1], m2 = model[c * 4 + 2], m3 = model[c * 4 + 3];
            for (int r = 0; r < 4; r++) {
                m[c * 4 + r] = vp[r] * m0 + vp[4 + r] * m1 + vp[8 + r] * m2 + vp[12 + r] * m3;
            }
        }

        curTex = texture;
        curTexW = texture == null ? 1.0f : texture.width;
        curTexH = texture == null ? 1.0f : texture.height;
        boolean neutralTint = Math.max(tr, Math.max(tg, tb)) - Math.min(tr, Math.min(tg, tb)) < 0.02f;
        curFlat = quality == RenderQuality.FAST && (texture == null || neutralTint);
        if (texture != null) registerTexture(texture);

        final int vc = mesh.vertexCount;
        ensureVertexScratch(vc);
        final float[] src = mesh.vertices;
        final float[] cl = clip;
        final float[] sc = scr;
        final byte[] oc = outcodes;
        final float halfW = width * 0.5f, halfH = height * 0.5f;
        final float uScale = curTexW, vScale = curTexH;
        final int stride = MeshData.FLOATS_PER_VERTEX;

        for (int v = 0; v < vc; v++) {
            int s = v * stride;
            float x = src[s], y = src[s + 1], z = src[s + 2];
            float cx = m[0] * x + m[4] * y + m[8] * z + m[12];
            float cy = m[1] * x + m[5] * y + m[9] * z + m[13];
            float cz = m[2] * x + m[6] * y + m[10] * z + m[14];
            float cw = m[3] * x + m[7] * y + m[11] * z + m[15];
            float u = src[s + 3] * uScale, tv = src[s + 4] * vScale;
            float r = clamp01(src[s + 5] * tr), g = clamp01(src[s + 6] * tg), b = clamp01(src[s + 7] * tb);

            int co = v * VS;
            cl[co] = cx; cl[co + 1] = cy; cl[co + 2] = cz; cl[co + 3] = cw;
            cl[co + 4] = u; cl[co + 5] = tv; cl[co + 6] = r; cl[co + 7] = g; cl[co + 8] = b;

            int code = 0;
            if (cx < -cw) code |= 1;
            if (cx > cw) code |= 2;
            if (cy < -cw) code |= 4;
            if (cy > cw) code |= 8;
            if (cz < -cw) code |= OC_NEAR;
            if (cz > cw) code |= OC_FAR;
            oc[v] = (byte) code;

            int so = v * SS;
            if (cw > 1.0e-5f) {
                float iw = 1.0f / cw;
                sc[so] = (cx * iw + 1.0f) * halfW;
                sc[so + 1] = (1.0f - cy * iw) * halfH;
                sc[so + 2] = iw;
            } else {
                sc[so] = Float.NaN; sc[so + 1] = Float.NaN; sc[so + 2] = 0.0f;
            }
            sc[so + 3] = u; sc[so + 4] = tv; sc[so + 5] = r; sc[so + 6] = g; sc[so + 7] = b;
        }

        final int[] idx = mesh.indices;
        statTrianglesIn += idx.length / 3;
        for (int i = 0; i < idx.length; i += 3) {
            int a = idx[i], b = idx[i + 1], c = idx[i + 2];
            int oa = oc[a], ob = oc[b], ocC = oc[c];
            if ((oa & ob & ocC) != 0) continue;   // entirely outside one frustum plane
            if (((oa | ob | ocC) & OC_Z) == 0) {
                setupTriangle(sc, a * SS, b * SS, c * SS);
            } else {
                clipAndEmit(a, b, c, oa | ob | ocC);
            }
        }
    }

    private void clipAndEmit(int a, int b, int c, int codes) {
        float[] in = polyA, out = polyB;
        System.arraycopy(clip, a * VS, in, 0, VS);
        System.arraycopy(clip, b * VS, in, VS, VS);
        System.arraycopy(clip, c * VS, in, 2 * VS, VS);
        int n = 3;
        if ((codes & OC_NEAR) != 0) {
            n = clipPlane(in, n, out, 0);
            float[] t = in; in = out; out = t;
        }
        if (n >= 3 && (codes & OC_FAR) != 0) {
            n = clipPlane(in, n, out, 1);
            float[] t = in; in = out; out = t;
        }
        if (n < 3) return;

        final float halfW = width * 0.5f, halfH = height * 0.5f;
        for (int v = 0; v < n; v++) {
            int o = v * VS, so = v * SS;
            float w = in[o + 3];
            if (!(w > 1.0e-5f)) return;
            float iw = 1.0f / w;
            polyS[so] = (in[o] * iw + 1.0f) * halfW;
            polyS[so + 1] = (1.0f - in[o + 1] * iw) * halfH;
            polyS[so + 2] = iw;
            polyS[so + 3] = in[o + 4]; polyS[so + 4] = in[o + 5];
            polyS[so + 5] = in[o + 6]; polyS[so + 6] = in[o + 7]; polyS[so + 7] = in[o + 8];
        }
        for (int k = 1; k < n - 1; k++) {
            setupTriangle(polyS, 0, k * SS, (k + 1) * SS);
        }
    }

    /** Sutherland-Hodgman against one z plane in clip space; plane 0 = near (z+w>=0), 1 = far (w-z>=0). */
    private static int clipPlane(float[] in, int count, float[] out, int plane) {
        int outCount = 0;
        int prev = (count - 1) * VS;
        float prevDist = plane == 0 ? in[prev + 2] + in[prev + 3] : in[prev + 3] - in[prev + 2];
        boolean prevInside = prevDist >= 0.0f;
        for (int i = 0; i < count; i++) {
            int cur = i * VS;
            float curDist = plane == 0 ? in[cur + 2] + in[cur + 3] : in[cur + 3] - in[cur + 2];
            boolean curInside = curDist >= 0.0f;
            if (curInside != prevInside && outCount < CLIP_CAP) {
                float denom = prevDist - curDist;
                if (denom != 0.0f) {
                    float t = prevDist / denom;
                    int target = outCount++ * VS;
                    for (int k = 0; k < VS; k++) out[target + k] = in[prev + k] + (in[cur + k] - in[prev + k]) * t;
                }
            }
            if (curInside && outCount < CLIP_CAP) {
                System.arraycopy(in, cur, out, outCount++ * VS, VS);
            }
            prev = cur; prevDist = curDist; prevInside = curInside;
        }
        return outCount;
    }

    /** Back-face/degenerate/off-screen culling, plane-equation gradients, and band binning. */
    private void setupTriangle(float[] s, int a, int b, int c) {
        float x0 = s[a], y0 = s[a + 1], x1 = s[b], y1 = s[b + 1], x2 = s[c], y2 = s[c + 1];
        float dx1 = x1 - x0, dy1 = y1 - y0, dx2 = x2 - x0, dy2 = y2 - y0;
        float area = dx1 * dy2 - dx2 * dy1;
        // Front faces are counter-clockwise in NDC, i.e. negative here (screen y points down).
        if (!(area < -0.02f)) return;

        float minX = Math.min(x0, Math.min(x1, x2)), maxX = Math.max(x0, Math.max(x1, x2));
        float minY = Math.min(y0, Math.min(y1, y2)), maxY = Math.max(y0, Math.max(y1, y2));
        if (!(maxX > 0.0f && minX < width && maxY > 0.0f && minY < height)) return;
        if (minX < -1.0e7f || maxX > 1.0e7f || minY < -1.0e7f || maxY > 1.0e7f) return;
        float fy0 = Math.max(0.0f, (float) Math.ceil(minY - 0.5f));
        float fy1 = Math.min((float) height, (float) Math.ceil(maxY - 0.5f));
        if (!(fy0 < fy1)) return;
        int rowStart = (int) fy0, rowEnd = (int) fy1;

        // Fog is evaluated per pixel from perspective-correct depth in the scanline loops.
        // Avoid the old triangle-average fog, which produced visible polygon-shaped haze bands
        // and dropped whole triangles at the fog end distance.

        int t = triCount;
        if (t * TF + TF > tf.length) growTriangles();
        final float[] f = tf;
        final int[] iv = ti;
        int fo = t * TF, io = t * TI;
        f[fo] = x0; f[fo + 1] = y0; f[fo + 2] = x1; f[fo + 3] = y1; f[fo + 4] = x2; f[fo + 5] = y2;
        float inv = 1.0f / area;

        float i0 = s[a + 2], i1 = s[b + 2], i2 = s[c + 2];
        gradient(f, fo + 6, i0, i1, i2, dx1, dy1, dx2, dy2, inv);

        if (curTex != null) {
            float tw = curTexW, th = curTexH;
            float u0 = s[a + 3], u1 = s[b + 3], u2 = s[c + 3];
            float v0 = s[a + 4], v1 = s[b + 4], v2 = s[c + 4];
            // Textures repeat, so shifting by whole texture sizes keeps 16.16 fixed point in range.
            float uOff = (float) Math.floor(Math.min(u0, Math.min(u1, u2)) / tw) * tw;
            float vOff = (float) Math.floor(Math.min(v0, Math.min(v1, v2)) / th) * th;
            float ur0 = u0 - uOff, ur1 = u1 - uOff, ur2 = u2 - uOff;
            float vr0 = v0 - vOff, vr1 = v1 - vOff, vr2 = v2 - vOff;
            gradient(f, fo + 9, ur0 * i0, ur1 * i1, ur2 * i2, dx1, dy1, dx2, dy2, inv);
            gradient(f, fo + 12, vr0 * i0, vr1 * i1, vr2 * i2, dx1, dy1, dx2, dy2, inv);
            int mipLevel = chooseMipLevel(curTex, i0, i1, i2,
                ur0 * i0, ur1 * i1, ur2 * i2, vr0 * i0, vr1 * i1, vr2 * i2,
                f[fo + 7], f[fo + 8], f[fo + 10], f[fo + 11], f[fo + 13], f[fo + 14]);
            if (curFlat) {
                float lum = (s[a + 5] + s[a + 6] + s[a + 7] + s[b + 5] + s[b + 6] + s[b + 7]
                    + s[c + 5] + s[c + 6] + s[c + 7]) * (1.0f / 9.0f);
                int level = Math.max(0, Math.min(SoftwareTexture.LEVELS - 1,
                    Math.round(lum * (SoftwareTexture.LEVELS - 1))));
                curTex.level(level, mipLevel); // build mip/shade before raster workers read it
                iv[io] = KIND_TEX_FLAT | (level << 4) | (curTex.slot << 8);
            } else {
                curTex.level(SoftwareTexture.LEVELS - 1, mipLevel);
                // Colour multipliers 0..256 (1.0 == 256).
                gradient(f, fo + 15, s[a + 5] * 256.0f, s[b + 5] * 256.0f, s[c + 5] * 256.0f, dx1, dy1, dx2, dy2, inv);
                gradient(f, fo + 18, s[a + 6] * 256.0f, s[b + 6] * 256.0f, s[c + 6] * 256.0f, dx1, dy1, dx2, dy2, inv);
                gradient(f, fo + 21, s[a + 7] * 256.0f, s[b + 7] * 256.0f, s[c + 7] * 256.0f, dx1, dy1, dx2, dy2, inv);
                iv[io] = KIND_TEX_SMOOTH | ((SoftwareTexture.LEVELS - 1) << 4) | (curTex.slot << 8);
            }
            iv[io + 2] = mipLevel;
        } else if (curFlat) {
            float r = (s[a + 5] + s[b + 5] + s[c + 5]) * (255.0f / 3.0f);
            float g = (s[a + 6] + s[b + 6] + s[c + 6]) * (255.0f / 3.0f);
            float bl = (s[a + 7] + s[b + 7] + s[c + 7]) * (255.0f / 3.0f);
            iv[io] = KIND_SOLID_FLAT;
            iv[io + 1] = (to255(r / 255.0f) << 16) | (to255(g / 255.0f) << 8) | to255(bl / 255.0f);
            iv[io + 2] = 0;
        } else {
            float[] col = colorTmp;
            int[] vs = {a, b, c};
            for (int k = 0; k < 3; k++) {
                int o = vs[k];
                col[k * 3] = s[o + 5] * 255.0f; col[k * 3 + 1] = s[o + 6] * 255.0f; col[k * 3 + 2] = s[o + 7] * 255.0f;
            }
            gradient(f, fo + 15, col[0], col[3], col[6], dx1, dy1, dx2, dy2, inv);
            gradient(f, fo + 18, col[1], col[4], col[7], dx1, dy1, dx2, dy2, inv);
            gradient(f, fo + 21, col[2], col[5], col[8], dx1, dy1, dx2, dy2, inv);
            iv[io] = KIND_SOLID_SMOOTH;
            iv[io + 2] = 0;
        }

        // Bin into every band the triangle's rows touch.
        int b0 = rowStart / BAND_ROWS, b1 = (rowEnd - 1) / BAND_ROWS;
        for (int band = b0; band <= b1; band++) {
            int n = bandCount[band];
            int[] list = bandList[band];
            if (n == list.length) { list = Arrays.copyOf(list, n * 2); bandList[band] = list; }
            list[n] = t;
            bandCount[band] = n + 1;
        }
        triCount = t + 1;
        statTrianglesQueued++;
    }

    /** Stores value at vertex 0 and d/dx, d/dy of the plane through the three vertex values. */
    private static void gradient(float[] f, int o, float a0, float a1, float a2,
                                 float dx1, float dy1, float dx2, float dy2, float inv) {
        f[o] = a0;
        f[o + 1] = ((a1 - a0) * dy2 - (a2 - a0) * dy1) * inv;
        f[o + 2] = (dx1 * (a2 - a0) - dx2 * (a1 - a0)) * inv;
    }

    // ------------------------------------------------------------------------------------------
    // Rasterization (runs on worker threads; each band is owned by exactly one thread)
    // ------------------------------------------------------------------------------------------

    /**
     * Chooses a compact nearest mip level from perspective-correct UV gradients. The choice is
     * made once per triangle, keeping the inner pixel loop cheap while reducing distant shimmer.
     */
    private static int chooseMipLevel(SoftwareTexture texture,
                                      float i0, float i1, float i2,
                                      float uw0, float uw1, float uw2,
                                      float vw0, float vw1, float vw2,
                                      float iwDx, float iwDy,
                                      float uwDx, float uwDy, float vwDx, float vwDy) {
        float iw = Math.max(1.0e-8f, (i0 + i1 + i2) / 3.0f);
        float uw = (uw0 + uw1 + uw2) / 3.0f;
        float vw = (vw0 + vw1 + vw2) / 3.0f;
        float iw2 = iw * iw;
        float duDx = (uwDx * iw - uw * iwDx) / iw2;
        float duDy = (uwDy * iw - uw * iwDy) / iw2;
        float dvDx = (vwDx * iw - vw * iwDx) / iw2;
        float dvDy = (vwDy * iw - vw * iwDy) / iw2;
        float footprintX = (float) Math.sqrt(duDx * duDx + dvDx * dvDx);
        float footprintY = (float) Math.sqrt(duDy * duDy + dvDy * dvDy);
        float footprint = Math.max(footprintX, footprintY);
        if (!(footprint > 1.35f)) return 0;
        int lod = Math.round((float) (Math.log(footprint) / Math.log(2.0)));
        return Math.max(0, Math.min(texture.maxMipLevel(), lod));
    }

    private void rasterBand(int band) {
        int y0 = band * BAND_ROWS;
        int y1 = Math.min(height, y0 + BAND_ROWS);
        fillSkyRows(y0, y1);
        Arrays.fill(depth, y0 * width, y1 * width, 0.0f);
        final int[] list = bandList[band];
        final int n = bandCount[band];
        for (int k = 0; k < n; k++) rasterTriangle(list[k], y0, y1);
    }

    private void rasterTriangle(int t, int rowMin, int rowMax) {
        final float[] f = tf;
        final int fo = t * TF;
        final int io = t * TI;
        final int packed = ti[io];
        final int kind = packed & 15;
        final float x0 = f[fo], y0 = f[fo + 1], x1 = f[fo + 2], y1 = f[fo + 3], x2 = f[fo + 4], y2 = f[fo + 5];

        float xt, yt, xm, ym, xb, yb;
        if (y0 <= y1) {
            if (y1 <= y2) { xt = x0; yt = y0; xm = x1; ym = y1; xb = x2; yb = y2; }
            else if (y0 <= y2) { xt = x0; yt = y0; xm = x2; ym = y2; xb = x1; yb = y1; }
            else { xt = x2; yt = y2; xm = x0; ym = y0; xb = x1; yb = y1; }
        } else {
            if (y0 <= y2) { xt = x1; yt = y1; xm = x0; ym = y0; xb = x2; yb = y2; }
            else if (y1 <= y2) { xt = x1; yt = y1; xm = x2; ym = y2; xb = x0; yb = y0; }
            else { xt = x2; yt = y2; xm = x1; ym = y1; xb = x0; yb = y0; }
        }
        int ys = Math.max(rowMin, (int) Math.ceil(yt - 0.5f));
        int ye = Math.min(rowMax, (int) Math.ceil(yb - 0.5f));
        if (ys >= ye) return;

        float dyL = yb - yt, dyT = ym - yt, dyB = yb - ym;
        float slopeL = dyL > 0.0f ? (xb - xt) / dyL : 0.0f;
        float slopeT = dyT > 0.0f ? (xm - xt) / dyT : 0.0f;
        float slopeB = dyB > 0.0f ? (xb - xm) / dyB : 0.0f;

        final float iw0 = f[fo + 6], iwDx = f[fo + 7], iwDy = f[fo + 8];

        int[] texels = null;
        int wMask = 0, hMask = 0, wShift = 0;
        boolean cutout = false;
        float uw0 = 0, uwDx = 0, uwDy = 0, vw0 = 0, vwDx = 0, vwDy = 0;
        float r0 = 0, rDx = 0, rDy = 0, g0 = 0, gDx = 0, gDy = 0, b0 = 0, bDx = 0, bDy = 0;
        int flatColor = 0;
        if (kind >= KIND_TEX_FLAT) {
            SoftwareTexture tex = textureTable[packed >>> 8];
            int mipLevel = Math.max(0, Math.min(tex.maxMipLevel(), ti[io + 2]));
            int mipScale = 1 << mipLevel;
            texels = tex.levelOrNull((packed >> 4) & 15, mipLevel);
            wMask = tex.widthMask(mipLevel); hMask = tex.heightMask(mipLevel);
            wShift = tex.widthShift(mipLevel); cutout = tex.cutout;
            uw0 = f[fo + 9] / mipScale; uwDx = f[fo + 10] / mipScale; uwDy = f[fo + 11] / mipScale;
            vw0 = f[fo + 12] / mipScale; vwDx = f[fo + 13] / mipScale; vwDy = f[fo + 14] / mipScale;
        } else if (kind == KIND_SOLID_FLAT) {
            flatColor = ti[io + 1];
        }
        if (kind == KIND_TEX_SMOOTH || kind == KIND_SOLID_SMOOTH) {
            r0 = f[fo + 15]; rDx = f[fo + 16]; rDy = f[fo + 17];
            g0 = f[fo + 18]; gDx = f[fo + 19]; gDy = f[fo + 20];
            b0 = f[fo + 21]; bDx = f[fo + 22]; bDy = f[fo + 23];
        }

        for (int y = ys; y < ye; y++) {
            float yc = y + 0.5f;
            float xa = xt + (yc - yt) * slopeL;
            float xbb = yc < ym ? xt + (yc - yt) * slopeT : xm + (yc - ym) * slopeB;
            float xl, xr;
            if (xa < xbb) { xl = xa; xr = xbb; } else { xl = xbb; xr = xa; }
            int xs = (int) Math.ceil(xl - 0.5f);
            int xe = (int) Math.ceil(xr - 0.5f);
            if (xs < 0) xs = 0;
            if (xe > width) xe = width;
            if (xs >= xe) continue;

            float dxp = xs + 0.5f - x0, dyp = yc - y0;
            float iw = iw0 + iwDx * dxp + iwDy * dyp;
            int rowBase = y * width + xs;
            int count = xe - xs;

            switch (kind) {
                case KIND_SOLID_FLAT -> spanSolid(rowBase, count, iw, iwDx, flatColor);
                case KIND_SOLID_SMOOTH -> spanSolidSmooth(rowBase, count, iw, iwDx,
                    r0 + rDx * dxp + rDy * dyp, rDx, g0 + gDx * dxp + gDy * dyp, gDx,
                    b0 + bDx * dxp + bDy * dyp, bDx);
                case KIND_TEX_FLAT -> spanTexFlat(texels, wMask, hMask, wShift, cutout, rowBase, count,
                    iw, uw0 + uwDx * dxp + uwDy * dyp, vw0 + vwDx * dxp + vwDy * dyp, iwDx, uwDx, vwDx);
                default -> spanTexSmooth(texels, wMask, hMask, wShift, cutout, rowBase, count,
                    iw, uw0 + uwDx * dxp + uwDy * dyp, vw0 + vwDx * dxp + vwDy * dyp, iwDx, uwDx, vwDx,
                    r0 + rDx * dxp + rDy * dyp, rDx, g0 + gDx * dxp + gDy * dyp, gDx,
                    b0 + bDx * dxp + bDy * dyp, bDx);
            }
        }
    }

    private void spanSolid(int i, int n, float iw, float diw, int color) {
        final float[] dp = depth;
        final int[] px = pixels;
        while (n > 0) {
            int run = Math.min(RUN, n);
            float iwEnd = iw + diw * run;
            int fogValue = fogFactor(iw) << 16;
            int fogStep = ((fogFactor(iwEnd) - (fogValue >> 16)) << 16) / run;
            for (int k = 0; k < run; k++, i++, iw += diw, fogValue += fogStep) {
                if (iw > dp[i]) {
                    int fogAmount = fogValue >> 16;
                    dp[i] = iw;
                    px[i] = fogAmount > 0 ? fogBlend(color, fogAmount) : color;
                }
            }
            n -= run;
        }
    }

    private void spanSolidSmooth(int i, int n, float iw, float diw,
                                 float r, float dr, float g, float dg, float b, float db) {
        final float[] dp = depth;
        final int[] px = pixels;
        int fr = (int) (r * 65536.0f), fg = (int) (g * 65536.0f), fb = (int) (b * 65536.0f);
        int sr = (int) (dr * 65536.0f), sg = (int) (dg * 65536.0f), sb = (int) (db * 65536.0f);
        while (n > 0) {
            int run = Math.min(RUN, n);
            float iwEnd = iw + diw * run;
            int fogValue = fogFactor(iw) << 16;
            int fogStep = ((fogFactor(iwEnd) - (fogValue >> 16)) << 16) / run;
            for (int k = 0; k < run; k++, i++, iw += diw, fr += sr, fg += sg, fb += sb, fogValue += fogStep) {
                if (iw > dp[i]) {
                    int cr = Math.min(255, Math.max(0, fr >> 16));
                    int cg = Math.min(255, Math.max(0, fg >> 16));
                    int cb = Math.min(255, Math.max(0, fb >> 16));
                    int color = (cr << 16) | (cg << 8) | cb;
                    int fogAmount = fogValue >> 16;
                    dp[i] = iw;
                    px[i] = fogAmount > 0 ? fogBlend(color, fogAmount) : color;
                }
            }
            n -= run;
        }
    }

    private void spanTexFlat(int[] tex, int wMask, int hMask, int wShift, boolean cutout,
                             int i, int n, float iw, float uw, float vw, float diw, float duw, float dvw) {
        final float[] dp = depth;
        final int[] px = pixels;
        float rw = 1.0f / iw;
        float u0 = uw * rw, v0 = vw * rw;
        while (n > 0) {
            int run = n < RUN ? n : RUN;
            float iwE = iw + diw * run, uwE = uw + duw * run, vwE = vw + dvw * run;
            if (!(iwE > 1.0e-12f)) { iwE = iw; uwE = uw; vwE = vw; }
            float rwE = 1.0f / iwE;
            float u1 = uwE * rwE, v1 = vwE * rwE;
            int fu = (int) (u0 * 65536.0f), fv = (int) (v0 * 65536.0f);
            float step = 65536.0f / run;
            int du = (int) ((u1 - u0) * step), dv = (int) ((v1 - v0) * step);
            int fogValue = fogFactor(iw) << 16;
            int fogStep = ((fogFactor(iwE) - (fogValue >> 16)) << 16) / run;
            float d = iw;
            for (int k = 0; k < run; k++, i++, d += diw, fu += du, fv += dv, fogValue += fogStep) {
                if (d > dp[i]) {
                    int c = tex[(((fv >> 16) & hMask) << wShift) | ((fu >> 16) & wMask)];
                    if (cutout && (c >>> 24) < 128) continue;
                    int fogAmount = fogValue >> 16;
                    if (fogAmount > 0) c = fogBlend(c, fogAmount);
                    dp[i] = d;
                    px[i] = c;
                }
            }
            iw = iwE; uw = uwE; vw = vwE; u0 = u1; v0 = v1;
            n -= run;
        }
    }

    private void spanTexSmooth(int[] tex, int wMask, int hMask, int wShift, boolean cutout,
                               int i, int n, float iw, float uw, float vw, float diw, float duw, float dvw,
                               float r, float dr, float g, float dg, float b, float db) {
        final float[] dp = depth;
        final int[] px = pixels;
        float rw = 1.0f / iw;
        float u0 = uw * rw, v0 = vw * rw;
        int cr = (int) (r * 65536.0f), cg = (int) (g * 65536.0f), cb = (int) (b * 65536.0f);
        int sr = (int) (dr * 65536.0f), sg = (int) (dg * 65536.0f), sb = (int) (db * 65536.0f);
        while (n > 0) {
            int run = n < RUN ? n : RUN;
            float iwE = iw + diw * run, uwE = uw + duw * run, vwE = vw + dvw * run;
            if (!(iwE > 1.0e-12f)) { iwE = iw; uwE = uw; vwE = vw; }
            float rwE = 1.0f / iwE;
            float u1 = uwE * rwE, v1 = vwE * rwE;
            int fu = (int) (u0 * 65536.0f), fv = (int) (v0 * 65536.0f);
            float step = 65536.0f / run;
            int du = (int) ((u1 - u0) * step), dv = (int) ((v1 - v0) * step);
            int fogValue = fogFactor(iw) << 16;
            int fogStep = ((fogFactor(iwE) - (fogValue >> 16)) << 16) / run;
            float d = iw;
            for (int k = 0; k < run; k++, i++, d += diw, fu += du, fv += dv, cr += sr, cg += sg, cb += sb, fogValue += fogStep) {
                if (d > dp[i]) {
                    int c = tex[(((fv >> 16) & hMask) << wShift) | ((fu >> 16) & wMask)];
                    if (cutout && (c >>> 24) < 128) continue;
                    int mr = Math.min(256, Math.max(0, cr >> 16));
                    int mg = Math.min(256, Math.max(0, cg >> 16));
                    int mb = Math.min(256, Math.max(0, cb >> 16));
                    int out = (((((c >> 16) & 255) * mr) >> 8) << 16)
                        | (((((c >> 8) & 255) * mg) >> 8) << 8)
                        | ((((c & 255) * mb) >> 8));
                    int fogAmount = fogValue >> 16;
                    if (fogAmount > 0) out = fogBlend(out, fogAmount);
                    dp[i] = d;
                    px[i] = out;
                }
            }
            iw = iwE; uw = uwE; vw = vwE; u0 = u1; v0 = v1;
            n -= run;
        }
    }

    /** Perspective-correct view depth is 1/(1/w); fog fades continuously across surfaces. */
    private int fogFactor(float inverseW) {
        if (!fogOn || inverseW <= 1.0e-12f) return 0;
        float distance = 1.0f / inverseW;
        if (distance <= fogStart) return 0;
        if (distance >= fogEnd) return 256;
        return Math.max(0, Math.min(256, Math.round((distance - fogStart) * fogInvRange * 256.0f)));
    }

    private int fogBlend(int c, int f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r += ((fogR - r) * f) >> 8;
        g += ((fogG - g) * f) >> 8;
        b += ((fogB - b) * f) >> 8;
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private void registerTexture(SoftwareTexture texture) {
        if (texture.slot >= textureTable.length) {
            textureTable = Arrays.copyOf(textureTable, Math.max(texture.slot + 1, textureTable.length * 2));
        }
        textureTable[texture.slot] = texture;
    }

    private void ensureVertexScratch(int vertexCount) {
        if (vertexCount * VS > clip.length) {
            int cap = Math.max(vertexCount, clip.length / VS * 2);
            clip = new float[cap * VS];
            scr = new float[cap * SS];
            outcodes = new byte[cap];
        }
    }

    private void growTriangles() {
        tf = Arrays.copyOf(tf, tf.length * 2);
        ti = Arrays.copyOf(ti, ti.length * 2);
    }

    private static float clamp01(float v) { return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v); }

    private static int to255(float v) {
        int i = Math.round(clamp01(v) * 255.0f);
        return i;
    }

    private static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1.0f;
        return m;
    }
}
