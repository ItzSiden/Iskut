package dev.itzsiden.skyvoxel.render.api;

/** Backend-neutral stylized skybox configuration. Colours are linear-ish display RGB in [0,1].
 * The sun direction is a world-space direction pointing from the scene toward the sun.
 */
public record SkyboxSettings(
    float topRed, float topGreen, float topBlue,
    float horizonRed, float horizonGreen, float horizonBlue,
    float lowerRed, float lowerGreen, float lowerBlue,
    float sunDirectionX, float sunDirectionY, float sunDirectionZ,
    float sunRadiusFraction, boolean sunEnabled
) {
    public static final SkyboxSettings DAYLIGHT = new SkyboxSettings(
        0.105f, 0.235f, 0.49f,
        0.43f, 0.68f, 0.88f,
        0.67f, 0.80f, 0.90f,
        0.34f, 0.56f, -1.0f,
        0.020f, true
    );

    public SkyboxSettings {
        color(topRed, "topRed"); color(topGreen, "topGreen"); color(topBlue, "topBlue");
        color(horizonRed, "horizonRed"); color(horizonGreen, "horizonGreen"); color(horizonBlue, "horizonBlue");
        color(lowerRed, "lowerRed"); color(lowerGreen, "lowerGreen"); color(lowerBlue, "lowerBlue");
        if (!Float.isFinite(sunDirectionX) || !Float.isFinite(sunDirectionY) || !Float.isFinite(sunDirectionZ)
            || (sunDirectionX * sunDirectionX + sunDirectionY * sunDirectionY + sunDirectionZ * sunDirectionZ) < 1.0e-8f) {
            throw new IllegalArgumentException("Sun direction must be finite and non-zero.");
        }
        if (!Float.isFinite(sunRadiusFraction) || sunRadiusFraction < 0.002f || sunRadiusFraction > 0.15f) {
            throw new IllegalArgumentException("Sun radius fraction must be within [0.002, 0.15].");
        }
    }

    /** Derives a pleasant blue gradient around an existing clear/fog colour. */
    public static SkyboxSettings fromHorizon(float red, float green, float blue) {
        color(red, "red"); color(green, "green"); color(blue, "blue");
        return new SkyboxSettings(
            Math.min(1.0f, red * 0.40f), Math.min(1.0f, green * 0.50f), Math.min(1.0f, blue * 0.70f),
            red, green, blue,
            Math.min(1.0f, red * 1.12f + 0.025f), Math.min(1.0f, green * 1.08f + 0.02f), Math.min(1.0f, blue * 1.04f + 0.015f),
            0.34f, 0.56f, -1.0f, 0.020f, true
        );
    }

    private static void color(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1].");
        }
    }
}
