package dev.itzsiden.skyvoxel.render.opengl;

import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.MouseButton;
import dev.itzsiden.skyvoxel.render.api.MousePosition;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;

final class OpenGLWindow implements RenderWindow {
    private final long handle;
    private double lastMouseX;
    private double lastMouseY;
    private boolean hasMousePosition;
    private boolean mouseCaptured;

    OpenGLWindow(int width, int height, String title) {
        handle = glfwCreateWindow(width, height, title, 0L, 0L);
        if (handle == 0L) {
            throw new IllegalStateException(
                "Could not create an OpenGL window. Check the graphics driver and OpenGL support."
            );
        }
        glfwSetWindowCloseCallback(handle, window -> glfwSetWindowShouldClose(window, false));

        GLFWVidMode videoMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (videoMode != null) {
            glfwSetWindowPos(
                handle,
                Math.max(0, (videoMode.width() - width) / 2),
                Math.max(0, (videoMode.height() - height) / 2)
            );
        }
    }

    long handle() {
        return handle;
    }

    void destroy() {
        glfwDestroyWindow(handle);
    }

    @Override
    public void pollEvents() {
        glfwPollEvents();
    }

    @Override
    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    @Override
    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    @Override
    public boolean isKeyDown(Key key) {
        int glfwKey = switch (key) {
            case W -> GLFW_KEY_W;
            case A -> GLFW_KEY_A;
            case S -> GLFW_KEY_S;
            case D -> GLFW_KEY_D;
            case SPACE -> GLFW_KEY_SPACE;
            case LEFT_SHIFT -> GLFW_KEY_LEFT_SHIFT;
            case LEFT_CONTROL -> GLFW_KEY_LEFT_CONTROL;
            case ESCAPE -> GLFW_KEY_ESCAPE;
            case F1 -> GLFW_KEY_F1;
            case F3 -> GLFW_KEY_F3;
            case E -> GLFW_KEY_E;
            case I -> GLFW_KEY_I;
            case R -> GLFW_KEY_R;
            case NUMBER_1 -> GLFW_KEY_1;
            case NUMBER_2 -> GLFW_KEY_2;
            case NUMBER_3 -> GLFW_KEY_3;
            case NUMBER_4 -> GLFW_KEY_4;
            case NUMBER_5 -> GLFW_KEY_5;
            case NUMBER_6 -> GLFW_KEY_6;
            case NUMBER_7 -> GLFW_KEY_7;
            case NUMBER_8 -> GLFW_KEY_8;
            case NUMBER_9 -> GLFW_KEY_9;
        };
        int state = glfwGetKey(handle, glfwKey);
        return state == GLFW_PRESS || state == GLFW_REPEAT;
    }


    @Override
    public boolean isMouseButtonDown(MouseButton button) {
        int glfwButton = switch (button) {
            case LEFT -> GLFW_MOUSE_BUTTON_LEFT;
            case RIGHT -> GLFW_MOUSE_BUTTON_RIGHT;
        };
        return glfwGetMouseButton(handle, glfwButton) == GLFW_PRESS;
    }

    @Override
    public MousePosition cursorPosition() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xBuffer = stack.mallocDouble(1);
            DoubleBuffer yBuffer = stack.mallocDouble(1);
            var windowWidth = stack.mallocInt(1);
            var windowHeight = stack.mallocInt(1);
            glfwGetCursorPos(handle, xBuffer, yBuffer);
            glfwGetWindowSize(handle, windowWidth, windowHeight);
            double sx = windowWidth.get(0) > 0 ? (double) framebufferWidth() / windowWidth.get(0) : 1.0;
            double sy = windowHeight.get(0) > 0 ? (double) framebufferHeight() / windowHeight.get(0) : 1.0;
            return new MousePosition(xBuffer.get(0) * sx, yBuffer.get(0) * sy);
        }
    }

    @Override
    public MouseDelta consumeMouseDelta() {
        if (!mouseCaptured) {
            return MouseDelta.ZERO;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xBuffer = stack.mallocDouble(1);
            DoubleBuffer yBuffer = stack.mallocDouble(1);
            glfwGetCursorPos(handle, xBuffer, yBuffer);
            double x = xBuffer.get(0);
            double y = yBuffer.get(0);

            if (!hasMousePosition) {
                lastMouseX = x;
                lastMouseY = y;
                hasMousePosition = true;
                return MouseDelta.ZERO;
            }

            double deltaX = x - lastMouseX;
            double deltaY = y - lastMouseY;
            lastMouseX = x;
            lastMouseY = y;
            return new MouseDelta(deltaX, deltaY);
        }
    }

    @Override
    public void setMouseCaptured(boolean captured) {
        mouseCaptured = captured;
        hasMousePosition = false;
        glfwSetInputMode(handle, GLFW_CURSOR, captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    @Override
    public int framebufferWidth() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, width, height);
            return width.get(0);
        }
    }

    @Override
    public int framebufferHeight() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, width, height);
            return height.get(0);
        }
    }
}
