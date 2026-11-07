package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.RenderQuality;
import dev.itzsiden.skyvoxel.render.api.RenderTexture;
import dev.itzsiden.skyvoxel.render.api.TextureData;

/** Ensures model atlases never blend unrelated texture cells through global mipmaps. */
public final class ModelAtlasMipPolicySmokeTest {
    private ModelAtlasMipPolicySmokeTest() { }

    public static void main(String[] args) {
        SoftwareRenderer renderer = SoftwareRenderer.offscreen(16, 16, 1, RenderQuality.FAST);
        try {
            int[] pixels = new int[64];
            java.util.Arrays.fill(pixels, 0xFFFFFFFF);
            RenderTexture atlasTexture = renderer.createTexture(new TextureData("model-atlas-no-mip", 8, 8, pixels));
            RenderTexture worldTexture = renderer.createTexture(new TextureData("world-test", 8, 8, pixels));
            int atlasLevels = ((SoftwareTexture) atlasTexture).maxMipLevel();
            int worldLevels = ((SoftwareTexture) worldTexture).maxMipLevel();
            if (atlasLevels != 0) throw new AssertionError("model atlas must use LOD0 only, got maxMipLevel=" + atlasLevels);
            if (worldLevels != 3) throw new AssertionError("ordinary textures must keep mipmaps, got maxMipLevel=" + worldLevels);
            System.out.println("MODEL_ATLAS_MIP_POLICY_PASS | atlasMipmaps=disabled | worldMipmaps=preserved");
        } finally {
            renderer.close();
        }
    }
}
