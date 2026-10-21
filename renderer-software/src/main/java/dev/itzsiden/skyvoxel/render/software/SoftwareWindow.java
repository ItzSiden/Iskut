package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.MouseButton;
import dev.itzsiden.skyvoxel.render.api.MousePosition;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.WindowConfig;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;
import dev.itzsiden.skyvoxel.render.api.UiRasterizer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferStrategy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class SoftwareWindow implements RenderWindow {
    private final JFrame frame;
    private final Canvas canvas;
    private final int renderWidth;
    private final int renderHeight;
    private final Set<Key> pressed = ConcurrentHashMap.newKeySet();
    private final Set<MouseButton> pressedMouse = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closeRequested = new AtomicBoolean(false);
    private volatile boolean mouseCaptured;
    private volatile double cursorX = -1;
    private volatile double cursorY = -1;
    private Robot robot;
    private Cursor normalCursor;
    private Cursor hiddenCursor;
    private UiDrawList cachedUiList;
    private BufferedImage cachedUiImage;
    private int cachedUiWidth = -1;
    private int cachedUiHeight = -1;

    SoftwareWindow(WindowConfig config, int renderWidth, int renderHeight) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Software renderer needs a desktop environment; AWT is headless.");
        }
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;

        JFrame[] frameHolder = new JFrame[1];
        Canvas[] canvasHolder = new Canvas[1];
        runOnEdt(() -> {
            JFrame createdFrame = new JFrame(config.title());
            Canvas createdCanvas = new Canvas();
            createdCanvas.setBackground(Color.BLACK);
            createdCanvas.setFocusable(true);
            createdCanvas.setIgnoreRepaint(true);
            createdCanvas.setSize(config.width(), config.height());
            createdCanvas.setPreferredSize(new java.awt.Dimension(config.width(), config.height()));
            createdFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            createdFrame.add(createdCanvas);
            createdCanvas.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    Key key = translateKey(event.getKeyCode());
                    if (key != null) pressed.add(key);
                }

                @Override
                public void keyReleased(KeyEvent event) {
                    Key key = translateKey(event.getKeyCode());
                    if (key != null) pressed.remove(key);
                }
            });
            createdCanvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent event) { cursorX = event.getX(); cursorY = event.getY(); }
                @Override public void mouseDragged(MouseEvent event) { cursorX = event.getX(); cursorY = event.getY(); }
            });
            createdCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    cursorX = event.getX(); cursorY = event.getY();
                    MouseButton button = translateMouseButton(event.getButton());
                    if (button != null) pressedMouse.add(button);
                    createdCanvas.requestFocusInWindow();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    cursorX = event.getX(); cursorY = event.getY();
                    MouseButton button = translateMouseButton(event.getButton());
                    if (button != null) pressedMouse.remove(button);
                }
            });
            createdCanvas.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent event) {
                    pressed.clear();
                    pressedMouse.clear();
                }
            });
            createdFrame.pack();
            createdFrame.setLocationRelativeTo(null);
            createdFrame.setVisible(true);
            createdCanvas.createBufferStrategy(2);
            createdCanvas.requestFocusInWindow();
            frameHolder[0] = createdFrame;
            canvasHolder[0] = createdCanvas;
        });
        frame = frameHolder[0];
        canvas = canvasHolder[0];
        normalCursor = canvas.getCursor();
        try {
            robot = new Robot();
        } catch (AWTException | SecurityException ignored) {
            robot = null;
        }
        try {
            BufferedImage transparent = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            hiddenCursor = Toolkit.getDefaultToolkit().createCustomCursor(transparent, new Point(0, 0), "skyvoxel-hidden");
        } catch (RuntimeException ignored) {
            hiddenCursor = normalCursor;
        }
    }

    private static void runOnEdt(Runnable runnable) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                runnable.run();
            } else {
                SwingUtilities.invokeAndWait(runnable);
            }
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Could not create software-renderer window.", cause);
        }
    }

    private static Key translateKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_W -> Key.W;
            case KeyEvent.VK_A -> Key.A;
            case KeyEvent.VK_S -> Key.S;
            case KeyEvent.VK_D -> Key.D;
            case KeyEvent.VK_SPACE -> Key.SPACE;
            case KeyEvent.VK_SHIFT -> Key.LEFT_SHIFT;
            case KeyEvent.VK_CONTROL -> Key.LEFT_CONTROL;
            case KeyEvent.VK_ESCAPE -> Key.ESCAPE;
            case KeyEvent.VK_F1 -> Key.F1;
            case KeyEvent.VK_F3 -> Key.F3;
            case KeyEvent.VK_E -> Key.E;
            case KeyEvent.VK_I -> Key.I;
            case KeyEvent.VK_R -> Key.R;
            case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> Key.NUMBER_1;
            case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> Key.NUMBER_2;
            case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> Key.NUMBER_3;
            case KeyEvent.VK_4, KeyEvent.VK_NUMPAD4 -> Key.NUMBER_4;
            case KeyEvent.VK_5, KeyEvent.VK_NUMPAD5 -> Key.NUMBER_5;
            case KeyEvent.VK_6, KeyEvent.VK_NUMPAD6 -> Key.NUMBER_6;
            case KeyEvent.VK_7, KeyEvent.VK_NUMPAD7 -> Key.NUMBER_7;
            case KeyEvent.VK_8, KeyEvent.VK_NUMPAD8 -> Key.NUMBER_8;
            case KeyEvent.VK_9, KeyEvent.VK_NUMPAD9 -> Key.NUMBER_9;
            default -> null;
        };
    }

    private static MouseButton translateMouseButton(int button) {
        return switch (button) {
            case MouseEvent.BUTTON1 -> MouseButton.LEFT;
            case MouseEvent.BUTTON3 -> MouseButton.RIGHT;
            default -> null;
        };
    }

    Canvas canvas() {
        return canvas;
    }

    private static final int UI_TILE = 32;
    private int[] uiRects = new int[0];   // x, y, w, h quadruples of non-empty UI areas

    void present(BufferedImage image, UiDrawList uiDrawList) {
        BufferStrategy strategy = canvas.getBufferStrategy();
        if (strategy == null) {
            runOnEdt(() -> canvas.createBufferStrategy(2));
            strategy = canvas.getBufferStrategy();
            if (strategy == null) return;
        }
        int uiW = Math.max(1, canvas.getWidth());
        int uiH = Math.max(1, canvas.getHeight());
        UiDrawList safeList = uiDrawList == null ? UiDrawList.EMPTY : uiDrawList;
        if (cachedUiImage == null || cachedUiWidth != uiW || cachedUiHeight != uiH || !safeList.equals(cachedUiList)) {
            cachedUiImage = UiRasterizer.rasterize(safeList, uiW, uiH);
            cachedUiList = safeList;
            cachedUiWidth = uiW;
            cachedUiHeight = uiH;
            uiRects = safeList.isEmpty() ? new int[0] : findOccupiedRects(cachedUiImage);
        }
        do {
            do {
                Graphics2D graphics = (Graphics2D) strategy.getDrawGraphics();
                try {
                    // Blend the final framebuffer when it is scaled to the window. World textures
                    // remain pixel-art (nearest sampled in the rasterizer); bilinear output scaling
                    // softens harsh one-pixel stair-steps without blurring the native-resolution UI.
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.drawImage(image, 0, 0, uiW, uiH, null);
                    // Composite the UI only where it actually has pixels (the full-window ARGB blend is costly).
                    for (int i = 0; i + 3 < uiRects.length; i += 4) {
                        int x = uiRects[i], y = uiRects[i + 1], w = uiRects[i + 2], h = uiRects[i + 3];
                        graphics.drawImage(cachedUiImage, x, y, x + w, y + h, x, y, x + w, y + h, null);
                    }
                } finally {
                    graphics.dispose();
                }
            } while (strategy.contentsRestored());
            strategy.show();
            Toolkit.getDefaultToolkit().sync();
        } while (strategy.contentsLost());
    }

    /** Horizontal runs of non-transparent 32x32 tiles, merged into rectangles. */
    private static int[] findOccupiedRects(BufferedImage ui) {
        int w = ui.getWidth(), h = ui.getHeight();
        int[] data = ((java.awt.image.DataBufferInt) ui.getRaster().getDataBuffer()).getData();
        int tilesX = (w + UI_TILE - 1) / UI_TILE, tilesY = (h + UI_TILE - 1) / UI_TILE;
        java.util.ArrayList<int[]> rects = new java.util.ArrayList<>();
        for (int ty = 0; ty < tilesY; ty++) {
            int runStart = -1;
            for (int tx = 0; tx <= tilesX; tx++) {
                boolean occupied = tx < tilesX && tileHasPixels(data, w, h, tx * UI_TILE, ty * UI_TILE);
                if (occupied && runStart < 0) runStart = tx;
                if (!occupied && runStart >= 0) {
                    int x = runStart * UI_TILE, y = ty * UI_TILE;
                    rects.add(new int[] {x, y, Math.min(w, tx * UI_TILE) - x, Math.min(h, y + UI_TILE) - y});
                    runStart = -1;
                }
            }
        }
        int[] out = new int[rects.size() * 4];
        for (int i = 0; i < rects.size(); i++) System.arraycopy(rects.get(i), 0, out, i * 4, 4);
        return out;
    }

    private static boolean tileHasPixels(int[] data, int w, int h, int x0, int y0) {
        int x1 = Math.min(w, x0 + UI_TILE), y1 = Math.min(h, y0 + UI_TILE);
        for (int y = y0; y < y1; y++) {
            int row = y * w;
            for (int x = x0; x < x1; x++) if ((data[row + x] >>> 24) != 0) return true;
        }
        return false;
    }

    @Override
    public void pollEvents() {
        // AWT dispatches input/window events on its event-dispatch thread.
        if (frame == null || !frame.isDisplayable()) closeRequested.set(true);
    }

    @Override
    public boolean shouldClose() {
        return closeRequested.get();
    }

    @Override
    public void requestClose() {
        closeRequested.set(true);
    }

    @Override
    public boolean isKeyDown(Key key) {
        return pressed.contains(key);
    }

    @Override
    public boolean isMouseButtonDown(MouseButton button) {
        return pressedMouse.contains(button);
    }

    @Override
    public MousePosition cursorPosition() { return new MousePosition(cursorX, cursorY); }

    @Override
    public MouseDelta consumeMouseDelta() {
        if (!mouseCaptured || robot == null || !canvas.isShowing()) return MouseDelta.ZERO;
        try {
            Point origin = canvas.getLocationOnScreen();
            int centerX = origin.x + canvas.getWidth() / 2;
            int centerY = origin.y + canvas.getHeight() / 2;
            PointerInfo pointer = MouseInfo.getPointerInfo();
            if (pointer == null) return MouseDelta.ZERO;
            Point current = pointer.getLocation();
            double dx = current.x - centerX;
            double dy = current.y - centerY;
            if (dx != 0.0 || dy != 0.0) robot.mouseMove(centerX, centerY);
            return new MouseDelta(dx, dy);
        } catch (IllegalStateException | SecurityException ignored) {
            return MouseDelta.ZERO;
        }
    }

    @Override
    public void setMouseCaptured(boolean captured) {
        mouseCaptured = captured;
        runOnEdt(() -> {
            canvas.setCursor(captured ? hiddenCursor : normalCursor);
            if (captured) canvas.requestFocusInWindow();
        });
        if (captured && robot != null && canvas.isShowing()) {
            try {
                Point origin = canvas.getLocationOnScreen();
                robot.mouseMove(origin.x + canvas.getWidth() / 2, origin.y + canvas.getHeight() / 2);
            } catch (IllegalStateException ignored) {
                // Window may be transitioning during startup/shutdown.
            }
        }
    }

    @Override
    public int uiWidth() { return Math.max(1, canvas.getWidth()); }

    @Override
    public int uiHeight() { return Math.max(1, canvas.getHeight()); }

    @Override
    public int framebufferWidth() {
        return renderWidth;
    }

    @Override
    public int framebufferHeight() {
        return renderHeight;
    }

    void closeWindow() {
        mouseCaptured = false;
        cachedUiImage = null;
        cachedUiList = null;
        runOnEdt(() -> frame.dispose());
    }
}
