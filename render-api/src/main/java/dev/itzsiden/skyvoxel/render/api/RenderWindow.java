package dev.itzsiden.skyvoxel.render.api;

/** Window and input surface exposed without any GLFW or OpenGL types. */
public interface RenderWindow {
    void pollEvents();

    boolean shouldClose();

    void requestClose();

    boolean isKeyDown(Key key);

    /** Current mouse button state. Default keeps custom backends source-compatible. */
    default boolean isMouseButtonDown(MouseButton button) { return false; }

    /** Cursor in UI viewport coordinates; unsupported backends return INVALID. */
    default MousePosition cursorPosition() { return MousePosition.INVALID; }

    MouseDelta consumeMouseDelta();

    void setMouseCaptured(boolean captured);

    int framebufferWidth();

    int framebufferHeight();

    /** UI layout viewport. Software rendering can use native window pixels for crisp text. */
    default int uiWidth() { return framebufferWidth(); }

    default int uiHeight() { return framebufferHeight(); }
}
