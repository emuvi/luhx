package br.com.pointel.luhx;

import java.awt.AWTEvent;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.RootPaneContainer;

/** Reads and writes saved window bounds in the application's configuration file. */
final class WindowConfig {

    private static final Logger LOGGER = Logger.getLogger(WindowConfig.class.getName());
    private static final Map<Window, String> TRACKED_WINDOWS = new WeakHashMap<>();
    private static final Map<Window, Rectangle> NORMAL_BOUNDS = new WeakHashMap<>();
    private static final WindowConfig APPLICATION_CONFIG = new WindowConfig(Path.of("config.ini"));
    private static boolean trackingInstalled;

    private static final Object LOCK = new Object();

    private final Path file;

    static final int GAP = 4;

    static final int PADDING = 8;

    WindowConfig(Path file) {
        this.file = file;
    }

    static void track(Window window, String windowId) {
        installWindowTracking();
        registerWindow(window, windowId, window.getOwner());
    }

    static void track(Window window, String windowId, Window positioningOwner) {
        installWindowTracking();
        registerWindow(window, windowId, positioningOwner);
    }

    private static synchronized void installWindowTracking() {
        if (trackingInstalled) {
            return;
        }
        AWTEventListener listener = event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED
                    && windowEvent.getWindow() instanceof RootPaneContainer) {
                Window window = windowEvent.getWindow();
                registerWindow(window, defaultWindowId(window), window.getOwner());
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
        trackingInstalled = true;
    }

    private static void registerWindow(Window window, String windowId, Window positioningOwner) {
        synchronized (TRACKED_WINDOWS) {
            if (TRACKED_WINDOWS.containsKey(window)) {
                return;
            }
            TRACKED_WINDOWS.put(window, windowId);
        }

        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveWindowBounds(window, windowId);
            }

            @Override
            public void windowClosed(WindowEvent event) {
                saveWindowBounds(window, windowId);
            }
        });
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                rememberNormalBounds(window);
            }

            @Override
            public void componentResized(ComponentEvent event) {
                rememberNormalBounds(window);
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                saveWindowBounds(window, windowId);
            }
        });
        restoreWindowBounds(window, windowId, positioningOwner);
    }

    private static String defaultWindowId(Window window) {
        String title = window instanceof Frame frame ? frame.getTitle()
                : window instanceof Dialog dialog ? dialog.getTitle() : "";
        String identity = window.getClass().getName() + "\n" + (title == null ? "" : title);
        return "window." + window.getClass().getName().replace('.', '_')
                + "." + Integer.toHexString(identity.hashCode());
    }

    private static void restoreWindowBounds(Window window, String windowId, Window positioningOwner) {
        try {
            Rectangle bounds = APPLICATION_CONFIG.loadBounds(windowId);
            if (bounds != null && isOnScreen(bounds)) {
                synchronized (NORMAL_BOUNDS) {
                    NORMAL_BOUNDS.put(window, bounds);
                }
                window.setBounds(bounds);
                if (window instanceof Frame frame && APPLICATION_CONFIG.loadMaximized(windowId)) {
                    frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
                }
            } else {
                window.setLocationRelativeTo(positioningOwner);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not read config.ini window geometry for " + windowId, ex);
            window.setLocationRelativeTo(positioningOwner);
        }
    }

    private static void saveWindowBounds(Window window, String windowId) {
        try {
            boolean maximized = window instanceof Frame frame
                    && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            Rectangle bounds = window.getBounds();
            if (maximized) {
                synchronized (NORMAL_BOUNDS) {
                    bounds = NORMAL_BOUNDS.getOrDefault(window, bounds);
                }
            } else {
                rememberNormalBounds(window);
                synchronized (NORMAL_BOUNDS) {
                    bounds = NORMAL_BOUNDS.getOrDefault(window, bounds);
                }
            }
            APPLICATION_CONFIG.saveBounds(windowId, bounds, maximized);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not save config.ini window geometry for " + windowId, ex);
        }
    }

    private static void rememberNormalBounds(Window window) {
        if (!(window instanceof Frame frame)
                || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            synchronized (NORMAL_BOUNDS) {
                NORMAL_BOUNDS.put(window, window.getBounds());
            }
        }
    }

    private boolean loadMaximized(String windowId) throws IOException {
        synchronized (LOCK) {
            if (!Files.exists(file)) {
                return false;
            }
            String value = loadProperties().getProperty(windowId + ".maximized");
            return Boolean.parseBoolean(value);
        }
    }

    Rectangle loadBounds(String windowId) throws IOException {
        synchronized (LOCK) {
            if (!Files.exists(file)) {
                return null;
            }
            Properties properties = loadProperties();
            String prefix = windowId + ".";
            String x = properties.getProperty(prefix + "x");
            String y = properties.getProperty(prefix + "y");
            String width = properties.getProperty(prefix + "width");
            String height = properties.getProperty(prefix + "height");
            if (x == null && y == null && width == null && height == null) {
                return null;
            }
            if (x == null || y == null || width == null || height == null) {
                throw new IOException("Incomplete window geometry for " + windowId);
            }
            try {
                int parsedWidth = Integer.parseInt(width);
                int parsedHeight = Integer.parseInt(height);
                if (parsedWidth <= 0 || parsedHeight <= 0) {
                    throw new NumberFormatException("Window dimensions must be positive");
                }
                return new Rectangle(Integer.parseInt(x), Integer.parseInt(y), parsedWidth, parsedHeight);
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid window geometry for " + windowId, ex);
            }
        }
    }

    void saveBounds(String windowId, Rectangle bounds) throws IOException {
        saveBounds(windowId, bounds, false);
    }

    void saveBounds(String windowId, Rectangle bounds, boolean maximized) throws IOException {
        synchronized (LOCK) {
            Properties properties = Files.exists(file) ? loadProperties() : new Properties();
            String prefix = windowId + ".";
            properties.setProperty(prefix + "x", Integer.toString(bounds.x));
            properties.setProperty(prefix + "y", Integer.toString(bounds.y));
            properties.setProperty(prefix + "width", Integer.toString(bounds.width));
            properties.setProperty(prefix + "height", Integer.toString(bounds.height));
            properties.setProperty(prefix + "maximized", Boolean.toString(maximized));

            Path absoluteFile = file.toAbsolutePath().normalize();
            Path parent = absoluteFile.getParent();
            Files.createDirectories(parent);
            Path temporaryFile = Files.createTempFile(parent, absoluteFile.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Luhx window geometry");
                }
                try {
                    Files.move(temporaryFile, absoluteFile, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    static boolean isOnScreen(Rectangle bounds) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle intersection = device.getDefaultConfiguration().getBounds().intersection(bounds);
            if (intersection.width >= Math.min(64, bounds.width)
                    && intersection.height >= Math.min(64, bounds.height)) {
                return true;
            }
        }
        return false;
    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
