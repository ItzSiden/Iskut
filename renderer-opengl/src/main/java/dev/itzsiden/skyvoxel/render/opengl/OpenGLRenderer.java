package dev.itzsiden.skyvoxel.render.opengl;

import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.RenderFrame;
import dev.itzsiden.skyvoxel.render.api.RenderMesh;
import dev.itzsiden.skyvoxel.render.api.RenderTexture;
import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.SkyboxSettings;
import dev.itzsiden.skyvoxel.render.api.WindowConfig;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;
import dev.itzsiden.skyvoxel.render.api.UiRasterizer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/** First backend: conservative OpenGL 2.1 compatibility profile for older PCs. */
public final class OpenGLRenderer implements Renderer {
    private GLFWErrorCallback errorCallback;
    private OpenGLWindow window;
    private int shaderProgram;
    private int viewProjectionLocation;
    private int modelLocation;
    private int tintLocation;
    private int textureLocation;
    private int useTextureLocation;
    private int fogRangeLocation;
    private int fogColorLocation;
    private final Set<OpenGLTexture> textures = new HashSet<>();
    private final Set<OpenGLMesh> meshes = new HashSet<>();
    private boolean closed;
    private boolean glfwInitialized;
    private int uiTexture;
    private int uiTextureWidth;
    private int uiTextureHeight;
    private UiDrawList cachedUiList;

    @Override
    public RenderWindow createWindow(WindowConfig config) {
        ensureNotClosed();
        if (window != null) {
            throw new IllegalStateException("This renderer already owns a window.");
        }

        errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        if (!glfwInit()) {
            glfwSetErrorCallback(null);
            errorCallback.free();
            errorCallback = null;
            throw new IllegalStateException("GLFW initialization failed.");
        }

        glfwInitialized = true;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);

            window = new OpenGLWindow(config.width(), config.height(), config.title());
            glfwMakeContextCurrent(window.handle());
            GL.createCapabilities();
            glfwSwapInterval(1);

            if (!org.lwjgl.opengl.GL.getCapabilities().OpenGL20) {
                throw new IllegalStateException(
                    "This graphics driver does not expose OpenGL 2.0 shaders. Update the graphics driver if possible."
                );
            }

            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glFrontFace(GL_CCW);
            glClearColor(0.44f, 0.64f, 0.86f, 1.0f);
            shaderProgram = createShaderProgram();
            viewProjectionLocation = glGetUniformLocation(shaderProgram, "uViewProjection");
            modelLocation = glGetUniformLocation(shaderProgram, "uModel");
            tintLocation = glGetUniformLocation(shaderProgram, "uTint");
            textureLocation = glGetUniformLocation(shaderProgram, "uTexture");
            useTextureLocation = glGetUniformLocation(shaderProgram, "uUseTexture");
            fogRangeLocation = glGetUniformLocation(shaderProgram, "uFogRange");
            fogColorLocation = glGetUniformLocation(shaderProgram, "uFogColor");
            glUseProgram(shaderProgram);
            glUniform1i(textureLocation, 0); // texture unit 0
            glUseProgram(0);
            return window;
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    @Override
    public RenderMesh createMesh(MeshData meshData) {
        ensureReady();
        int vertexBuffer = glGenBuffers();
        int indexBuffer = glGenBuffers();
        float[] vertexArray = meshData.vertices();
        int[] indexArray = meshData.indices();
        FloatBuffer vertices = MemoryUtil.memAllocFloat(vertexArray.length);
        IntBuffer indices = MemoryUtil.memAllocInt(indexArray.length);

        try {
            vertices.put(vertexArray).flip();
            indices.put(indexArray).flip();

            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        } catch (RuntimeException | Error failure) {
            glDeleteBuffers(vertexBuffer);
            glDeleteBuffers(indexBuffer);
            throw failure;
        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
        }

        OpenGLMesh mesh = new OpenGLMesh(this, vertexBuffer, indexBuffer, meshData.indexCount());
        meshes.add(mesh);
        return mesh;
    }

    @Override
    public RenderTexture createTexture(TextureData data) {
        ensureReady();
        int width = data.width(), height = data.height();
        int[] argb = data.pixels();
        ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
        int id = glGenTextures();
        try {
            for (int pixel : argb) {
                rgba.put((byte) ((pixel >>> 16) & 0xFF));
                rgba.put((byte) ((pixel >>> 8) & 0xFF));
                rgba.put((byte) (pixel & 0xFF));
                rgba.put((byte) ((pixel >>> 24) & 0xFF));
            }
            rgba.flip();
            glBindTexture(GL_TEXTURE_2D, id);
            // Nearest filtering and no mipmaps: the blocky Minecraft look, and cheapest on old GPUs.
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glBindTexture(GL_TEXTURE_2D, 0);
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(id);
            throw failure;
        } finally {
            MemoryUtil.memFree(rgba);
        }
        OpenGLTexture texture = new OpenGLTexture(this, id);
        textures.add(texture);
        return texture;
    }

    @Override
    public void beginFrame(RenderFrame frame) {
        ensureReady();
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        glViewport(0, 0, width, height);
        glClearColor(frame.skybox().horizonRed(), frame.skybox().horizonGreen(), frame.skybox().horizonBlue(), 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawSkybox(frame.skybox(), frame.viewProjectionMatrix(), width, height);
        glClear(GL_DEPTH_BUFFER_BIT);
        glUseProgram(shaderProgram);
        // Match fog to the bright atmospheric horizon so distance fading feels like depth haze.
        glUniform3f(fogColorLocation, frame.skybox().horizonRed(), frame.skybox().horizonGreen(), frame.skybox().horizonBlue());
        if (frame.fogEnabled()) glUniform2f(fogRangeLocation, frame.fogStart(), frame.fogEnd());
        else glUniform2f(fogRangeLocation, 0.0f, 0.0f);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            matrix.put(frame.viewProjectionMatrix()).flip();
            glUniformMatrix4fv(viewProjectionLocation, false, matrix);
        }
    }


    /** Cheap compatibility-profile sky gradient and sun disc. No textures or extra shaders required. */
    private void drawSkybox(SkyboxSettings sky, float[] vp, int width, int height) {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_CURRENT_BIT | GL_TEXTURE_BIT);
        glUseProgram(0);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
        glDepthMask(false);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        float horizonY = height * 0.70f;
        glBegin(GL_QUADS);
        glColor3f(sky.topRed(), sky.topGreen(), sky.topBlue());
        glVertex2f(0, 0); glVertex2f(width, 0);
        glColor3f(sky.horizonRed(), sky.horizonGreen(), sky.horizonBlue());
        glVertex2f(width, horizonY); glVertex2f(0, horizonY);
        glColor3f(sky.horizonRed(), sky.horizonGreen(), sky.horizonBlue());
        glVertex2f(0, horizonY); glVertex2f(width, horizonY);
        glColor3f(sky.lowerRed(), sky.lowerGreen(), sky.lowerBlue());
        glVertex2f(width, height); glVertex2f(0, height);
        glEnd();

        if (sky.sunEnabled()) {
            float dx = sky.sunDirectionX(), dy = sky.sunDirectionY(), dz = sky.sunDirectionZ();
            float clipX = vp[0] * dx + vp[4] * dy + vp[8] * dz;
            float clipY = vp[1] * dx + vp[5] * dy + vp[9] * dz;
            float clipW = vp[3] * dx + vp[7] * dy + vp[11] * dz;
            if (clipW > 0.0001f) {
                float nx = clipX / clipW, ny = clipY / clipW;
                float sx = (nx * 0.5f + 0.5f) * width;
                float sy = (0.5f - ny * 0.5f) * height;
                float radius = Math.max(2.0f, height * sky.sunRadiusFraction());
                if (sx > -radius * 4 && sx < width + radius * 4 && sy > -radius * 4 && sy < height + radius * 4) {
                    glEnable(GL_BLEND);
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                    drawSkyHalo(sx, sy, radius * 3.0f, 0xFFE8AC);
                    drawSkyDisc(sx, sy, radius, 0xFFFFE0, 1.0f);
                    glDisable(GL_BLEND);
                }
            }
        }

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopAttrib();
        glDepthMask(true);
    }

    private static void drawSkyHalo(float cx, float cy, float radius, int rgb) {
        float r = ((rgb >>> 16) & 255) / 255.0f;
        float g = ((rgb >>> 8) & 255) / 255.0f;
        float b = (rgb & 255) / 255.0f;
        glBegin(GL_TRIANGLE_FAN);
        glColor4f(r, g, b, 0.20f);
        glVertex2f(cx, cy);
        glColor4f(r, g, b, 0.0f);
        for (int i = 0; i <= 48; i++) {
            double angle = i * (Math.PI * 2.0 / 48.0);
            glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        glEnd();
    }

    private static void drawSkyDisc(float cx, float cy, float radius, int rgb, float alpha) {
        float r = ((rgb >>> 16) & 255) / 255.0f;
        float g = ((rgb >>> 8) & 255) / 255.0f;
        float b = (rgb & 255) / 255.0f;
        glColor4f(r, g, b, alpha);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= 48; i++) {
            double angle = i * (Math.PI * 2.0 / 48.0);
            glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        glEnd();
    }

    @Override
    public void draw(RenderMesh renderMesh, RenderTexture renderTexture, float[] modelMatrix,
                     float tintRed, float tintGreen, float tintBlue) {
        ensureReady();
        if (!(renderMesh instanceof OpenGLMesh mesh) || mesh.owner() != this || mesh.isClosed()) {
            throw new IllegalArgumentException("Mesh belongs to another renderer or is already closed.");
        }
        if (modelMatrix == null || modelMatrix.length != 16) {
            throw new IllegalArgumentException("Model matrix must contain 16 floats.");
        }
        OpenGLTexture texture = null;
        if (renderTexture != null) {
            if (!(renderTexture instanceof OpenGLTexture t) || t.owner() != this || t.isClosed()) {
                throw new IllegalArgumentException("Texture belongs to another renderer or is already closed.");
            }
            texture = t;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            matrix.put(modelMatrix).flip();
            glUniformMatrix4fv(modelLocation, false, matrix);
        }
        glUniform3f(tintLocation, tintRed, tintGreen, tintBlue);
        if (texture != null) {
            glBindTexture(GL_TEXTURE_2D, texture.id());
            glUniform1f(useTextureLocation, 1.0f);
        } else {
            glUniform1f(useTextureLocation, 0.0f);
        }

        glBindBuffer(GL_ARRAY_BUFFER, mesh.vertexBuffer());
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mesh.indexBuffer());
        final int stride = MeshData.FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, (long) MeshData.OFFSET_POSITION * Float.BYTES);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, (long) MeshData.OFFSET_UV * Float.BYTES);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, (long) MeshData.OFFSET_COLOR * Float.BYTES);
        glDrawElements(GL_TRIANGLES, mesh.indexCount(), GL_UNSIGNED_INT, 0L);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);
        if (texture != null) glBindTexture(GL_TEXTURE_2D, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void drawUi(UiDrawList drawList) {
        ensureReady();
        if (drawList == null || drawList.isEmpty()) return;
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        if (width < 1 || height < 1) return;
        if (uiTexture == 0 || width != uiTextureWidth || height != uiTextureHeight || !drawList.equals(cachedUiList)) {
            BufferedImage layer = UiRasterizer.rasterize(drawList, width, height);
            int[] argb = ((DataBufferInt) layer.getRaster().getDataBuffer()).getData();
            ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
            try {
                for (int pixel : argb) {
                    rgba.put((byte) ((pixel >>> 16) & 0xFF));
                    rgba.put((byte) ((pixel >>> 8) & 0xFF));
                    rgba.put((byte) (pixel & 0xFF));
                    rgba.put((byte) ((pixel >>> 24) & 0xFF));
                }
                rgba.flip();
                if (uiTexture == 0) uiTexture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, uiTexture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
                glBindTexture(GL_TEXTURE_2D, 0);
            } finally {
                MemoryUtil.memFree(rgba);
            }
            uiTextureWidth = width;
            uiTextureHeight = height;
            cachedUiList = drawList;
        }

        // OpenGL 2.1 compatibility-profile 2D overlay. Restore state so UI stays backend-local.
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glUseProgram(0);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, uiTexture);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(0, 0);
        glTexCoord2f(1, 0); glVertex2f(width, 0);
        glTexCoord2f(1, 1); glVertex2f(width, height);
        glTexCoord2f(0, 1); glVertex2f(0, height);
        glEnd();
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glBindTexture(GL_TEXTURE_2D, 0);
        glPopAttrib();
        glUseProgram(shaderProgram);
    }

    @Override
    public void endFrame() {
        ensureReady();
        glfwSwapBuffers(window.handle());
    }

    @Override
    public BufferedImage captureScreenshot() {
        ensureReady();
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        if (width < 1 || height < 1) {
            throw new IllegalStateException("Cannot capture a screenshot from an empty framebuffer.");
        }

        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        BufferedImage screenshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] imagePixels = ((DataBufferInt) screenshot.getRaster().getDataBuffer()).getData();
        int previousReadBuffer = glGetInteger(GL_READ_BUFFER);
        int previousPackAlignment = glGetInteger(GL_PACK_ALIGNMENT);
        try {
            glReadBuffer(GL_BACK);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < height; y++) {
                int sourceRow = (height - 1 - y) * width * 4;
                int targetRow = y * width;
                for (int x = 0; x < width; x++) {
                    int source = sourceRow + x * 4;
                    int red = pixels.get(source) & 0xFF;
                    int green = pixels.get(source + 1) & 0xFF;
                    int blue = pixels.get(source + 2) & 0xFF;
                    imagePixels[targetRow + x] = 0xFF000000 | red << 16 | green << 8 | blue;
                }
            }
            return screenshot;
        } finally {
            glReadBuffer(previousReadBuffer);
            glPixelStorei(GL_PACK_ALIGNMENT, previousPackAlignment);
            MemoryUtil.memFree(pixels);
        }
    }

    @Override
    public String description() {
        return "OpenGL 2.1 compatibility backend";
    }

    void destroyTexture(OpenGLTexture texture) {
        if (textures.remove(texture)) {
            texture.disposeFromOwner();
        }
    }

    void destroyMesh(OpenGLMesh mesh) {
        if (meshes.remove(mesh)) {
            mesh.disposeFromOwner();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (window != null) {
            glfwMakeContextCurrent(window.handle());
            for (OpenGLMesh mesh : new ArrayList<>(meshes)) {
                mesh.disposeFromOwner();
            }
            meshes.clear();
            for (OpenGLTexture texture : new ArrayList<>(textures)) {
                texture.disposeFromOwner();
            }
            textures.clear();
            if (uiTexture != 0) {
                glDeleteTextures(uiTexture);
                uiTexture = 0;
            }
            cachedUiList = null;
            if (shaderProgram != 0) {
                glDeleteProgram(shaderProgram);
                shaderProgram = 0;
            }
            window.destroy();
            window = null;
        }
        if (glfwInitialized) {
            glfwTerminate();
            glfwInitialized = false;
        }

        if (errorCallback != null) {
            glfwSetErrorCallback(null);
            errorCallback.free();
            errorCallback = null;
        }
    }

    private int createShaderProgram() {
        String vertexSource = """
            #version 120
            attribute vec3 aPosition;
            attribute vec2 aUv;
            attribute vec3 aColor;
            uniform mat4 uViewProjection;
            uniform mat4 uModel;
            varying vec2 vUv;
            varying vec3 vColor;
            varying float vFogDepth;
            void main() {
                vec4 clip = uViewProjection * uModel * vec4(aPosition, 1.0);
                gl_Position = clip;
                vUv = aUv;
                vColor = aColor;
                vFogDepth = clip.w; // view-space distance along the camera axis
            }
            """;

        String fragmentSource = """
            #version 120
            varying vec2 vUv;
            varying vec3 vColor;
            varying float vFogDepth;
            uniform vec3 uTint;
            uniform sampler2D uTexture;
            uniform float uUseTexture;
            uniform vec3 uFogColor;
            uniform vec2 uFogRange; // x = start, y = end; (0,0) disables fog
            void main() {
                vec4 texel = uUseTexture > 0.5 ? texture2D(uTexture, vUv) : vec4(1.0);
                if (texel.a < 0.5) discard; // cut-out textures (leaves, glass panes)
                vec3 colour = texel.rgb * vColor * uTint;
                float fog = uFogRange.y > uFogRange.x
                    ? clamp((vFogDepth - uFogRange.x) / (uFogRange.y - uFogRange.x), 0.0, 1.0)
                    : 0.0;
                gl_FragColor = vec4(mix(colour, uFogColor, fog), 1.0);
            }
            """;

        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glBindAttribLocation(program, 0, "aPosition");
        glBindAttribLocation(program, 1, "aUv");
        glBindAttribLocation(program, 2, "aColor");
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            throw new IllegalStateException("Could not link basic shader: " + log);
        }

        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return program;
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Could not compile basic shader: " + log);
        }
        return shader;
    }

    private void ensureReady() {
        ensureNotClosed();
        if (window == null || shaderProgram == 0) {
            throw new IllegalStateException("Create a window before using the renderer.");
        }
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("Renderer is closed.");
        }
    }
}
