package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.List;
import java.util.Objects;

public final class MainMenuScreen {

    public enum Action {
        NONE,
        PLAY,
        SETTINGS,
        QUIT
    }

    private final TextureData background;
    private final TextureData logo;

    // Existing menu text
    private static final String SUBTITLE_TEXT =
            "OCEAN WORLD  •  FIND YOUR WAY";

    private static final String FOOTER_TEXT =
            "ISKUT  •  ADVENTURE AWAITS";

    // Deep ocean palette
    private static final int OVERLAY_COLOR = 0x68050D1C;
    private static final int SUBTITLE_COLOR = 0xF0D9F0FF;
    private static final int FOOTER_COLOR = 0x9BB6CAD8;

    // Responsive layout proportions
    private static final float LOGO_MAX_WIDTH = 470.0f;
    private static final float LOGO_MAX_HEIGHT_RATIO = 0.245f;

    private static final float LOGO_TOP_RATIO = 0.065f;
    private static final float LOGO_SUBTITLE_GAP = 16.0f;

    private static final float HORIZONTAL_PADDING = 36.0f;
    private static final float FOOTER_BOTTOM_PADDING = 25.0f;

    public MainMenuScreen(
            TextureData background,
            TextureData logo
    ) {
        this.background = Objects.requireNonNull(
                background,
                "background"
        );

        this.logo = Objects.requireNonNull(
                logo,
                "logo"
        );
    }

    public UiDrawList build(
            int width,
            int height,
            double mouseX,
            double mouseY
    ) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);

        float scale = MenuScreenSupport.scale(
                safeWidth,
                safeHeight
        );

        /*
         * 1. Responsive logo layout
         *
         * The logo respects both the screen width and height.
         * This prevents it from becoming oversized on short
         * screens or cramped on narrow windows.
         */

        float horizontalPadding = Math.min(
                HORIZONTAL_PADDING * scale,
                safeWidth * 0.06f
        );

        float availableWidth = Math.max(
                1.0f,
                safeWidth - horizontalPadding * 2.0f
        );

        float logoAspectRatio =
                Math.max(1, logo.width())
                        / (float) Math.max(1, logo.height());

        float desiredLogoWidth =
                LOGO_MAX_WIDTH * scale;

        float maxLogoHeight =
                safeHeight * LOGO_MAX_HEIGHT_RATIO;

        float maxWidthByHeight =
                maxLogoHeight * logoAspectRatio;

        float logoWidth = Math.min(
                desiredLogoWidth,
                Math.min(
                        availableWidth,
                        maxWidthByHeight
                )
        );

        logoWidth = Math.max(1.0f, logoWidth);

        float logoHeight =
                logoWidth / logoAspectRatio;

        float logoY = Math.max(
                20.0f * scale,
                safeHeight * LOGO_TOP_RATIO
        );

        /*
         * 2. Subtitle positioning
         *
         * Keep the subtitle visually attached to the logo
         * without making the header feel crowded.
         */

        float subtitleY =
                logoY
                        + logoHeight
                        + Math.max(
                                LOGO_SUBTITLE_GAP * scale,
                                safeHeight * 0.018f
                        );

        float subtitleSize = Math.max(
                8.0f,
                10.0f * scale
        );

        /*
         * 3. Build the draw list
         */

        List<MenuScreenSupport.Button> buttons =
                buttons(safeWidth, safeHeight);

        UiDrawList.Builder draw =
                new UiDrawList.Builder();

        /*
         * Layer 1: Background
         *
         * The deeper tint improves contrast and gives
         * the ocean background a more cinematic mood.
         */

        MenuScreenSupport.background(
                draw,
                background,
                safeWidth,
                safeHeight,
                OVERLAY_COLOR
        );

        /*
         * Layer 2: Logo
         */

        draw.image(
                (safeWidth - logoWidth) / 2.0f,
                logoY,
                logoWidth,
                logoHeight,
                logo
        );

        /*
         * Layer 3: Subtitle
         */

        draw.text(
                safeWidth / 2.0f,
                subtitleY,
                SUBTITLE_TEXT,
                subtitleSize,
                SUBTITLE_COLOR,
                true,
                true,
                UiDrawList.TextAlign.CENTER
        );

        /*
         * Layer 4: Main menu buttons
         *
         * Keep the existing button implementation so
         * hover effects, alignment and click detection
         * continue working through MenuScreenSupport.
         */

        for (MenuScreenSupport.Button button : buttons) {
            MenuScreenSupport.button(
                    draw,
                    button,
                    mouseX,
                    mouseY,
                    scale
            );
        }

        /*
         * Layer 5: Footer
         *
         * Use a consistent bottom margin and a quieter
         * color so the footer does not compete with
         * the logo or primary actions.
         */

        float footerSize = Math.max(
                7.5f,
                9.0f * scale
        );

        float footerY = safeHeight - Math.max(
                FOOTER_BOTTOM_PADDING * scale,
                safeHeight * 0.035f
        );

        draw.text(
                safeWidth / 2.0f,
                footerY,
                FOOTER_TEXT,
                footerSize,
                FOOTER_COLOR,
                false,
                true,
                UiDrawList.TextAlign.CENTER
        );

        return draw.build();
    }

    public Action handleClick(
            int width,
            int height,
            double mouseX,
            double mouseY
    ) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);

        return switch (
                MenuScreenSupport.hit(
                        buttons(safeWidth, safeHeight),
                        mouseX,
                        mouseY
                )
        ) {
            case "play" -> Action.PLAY;
            case "settings" -> Action.SETTINGS;
            case "quit" -> Action.QUIT;
            default -> Action.NONE;
        };
    }

    private static List<MenuScreenSupport.Button> buttons(
            int width,
            int height
    ) {
        return List.of(
                MenuScreenSupport.button(
                        "play",
                        "PLAY",
                        width,
                        height,
                        0,
                        3
                ),
                MenuScreenSupport.button(
                        "settings",
                        "SETTINGS",
                        width,
                        height,
                        1,
                        3
                ),
                MenuScreenSupport.button(
                        "quit",
                        "QUIT",
                        width,
                        height,
                        2,
                        3
                )
        );
    }
}