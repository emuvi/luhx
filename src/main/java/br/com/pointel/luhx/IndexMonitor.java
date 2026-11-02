package br.com.pointel.luhx;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Non-modal Swing window that displays the progress and outcome of an indexing run.
 *
 * <p>Observer callbacks update this window on the Swing event-dispatch thread.
 * Use the Stop button to request cancellation; completed changes are retained.
 */
public class IndexMonitor extends JFrame implements Luhx.IndexObserver {

    private final DefaultListModel<String> log = new DefaultListModel<>();
    private final JList<String> logList = new JList<>(log);
    private final JLabel counters = new JLabel("Starting...");
    private final JProgressBar bar = new JProgressBar();
    private final JButton close = new JButton("Close");
    private final JButton stop = new JButton("Stop");
    private volatile boolean cancelRequested;
    private int created, modified, deleted, unchanged, failed;

    /**
     * Creates a monitor for an indexing run. The caller should show the window
     * on the Swing event-dispatch thread and pass this instance as the
     * {@link Luhx.IndexObserver}.
     *
     * @param owner  parent window
     * @param folder folder being indexed
     */
    public IndexMonitor(Window owner, Path folder) {
        super("Indexing " + folder.getFileName());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        bar.setIndeterminate(true);
        close.setEnabled(false);
        close.addActionListener(e -> dispose());
        stop.addActionListener(e -> {
            cancelRequested = true;
            stop.setEnabled(false);
            append("Stopping...");
        });

        JPanel top = new JPanel(new BorderLayout(0, WindowConfig.GAP));
        top.add(counters, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, WindowConfig.GAP, 0));
        bottom.add(stop);
        bottom.add(close);

        JPanel content = new JPanel(new BorderLayout(0, WindowConfig.GAP));
        content.setBorder(BorderFactory.createEmptyBorder(
                WindowConfig.PADDING, WindowConfig.PADDING, WindowConfig.PADDING, WindowConfig.PADDING));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(logList), BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(640, 420));
        pack();
        WindowConfig.track(this, "indexMonitor", owner);
    }

    private void updateCounters() {
        counters.setText(String.format("Created: %d  Modified: %d  Deleted: %d  Unchanged: %d  Failed: %d",
                created, modified, deleted, unchanged, failed));
    }

    private void append(String line) {
        log.addElement(line);
        logList.ensureIndexIsVisible(log.size() - 1);
    }

    private void finished(String message) {
        bar.setIndeterminate(false);
        bar.setValue(bar.getMaximum());
        close.setEnabled(true);
        stop.setEnabled(false);
        append(message);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return cancelRequested;
    }

    /** {@inheritDoc} */
    @Override
    public void onCancel(Luhx.IndexStats stats) {
        SwingUtilities.invokeLater(() -> finished("Stopped by user. Files processed so far were kept in the index."));
    }

    /** {@inheritDoc} */
    @Override
    public void onStart(Path folder) {
        SwingUtilities.invokeLater(() -> append("Started: " + folder));
    }

    /** {@inheritDoc} */
    @Override
    public void onFile(String relativePath, Luhx.IndexAction action) {
        SwingUtilities.invokeLater(() -> {
            switch (action) {
                case CREATED -> created++;
                case MODIFIED -> modified++;
                case DELETED -> deleted++;
                case UNCHANGED -> unchanged++;
                case FAILED -> failed++;
            }
            updateCounters();
            if (action != Luhx.IndexAction.UNCHANGED) {
                append(action + "  " + relativePath);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onError(String relativePath, Exception error) {
        SwingUtilities.invokeLater(() -> {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            append("Error on " + relativePath + ": " + cause);
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onFinish(Luhx.IndexStats stats) {
        SwingUtilities.invokeLater(() -> finished("Finished."));
    }

    /**
     * Enables closing and disables cancellation after the background worker
     * has ended, including when it failed before delivering a completion event.
     */
    public void ended() {
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            stop.setEnabled(false);
            close.setEnabled(true);
        });
    }
}
