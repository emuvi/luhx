package br.com.pointel.luhx;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Non-modal window that shows the live progress of an indexing run. */
public class IndexMonitor extends JDialog implements Luhx.IndexObserver {

    private final DefaultListModel<String> log = new DefaultListModel<>();
    private final JList<String> logList = new JList<>(log);
    private final JLabel counters = new JLabel("Starting...");
    private final JProgressBar bar = new JProgressBar();
    private final JButton close = new JButton("Close");
    private int created, modified, deleted, unchanged, failed;

    /**
     * Creates the monitor for a folder.
     *
     * @param owner  parent window
     * @param folder folder being indexed
     */
    public IndexMonitor(Window owner, Path folder) {
        super(owner, "Indexing " + folder, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        bar.setIndeterminate(true);
        close.setEnabled(false);
        close.addActionListener(e -> dispose());

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(counters, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        JPanel bottom = new JPanel();
        bottom.add(close);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(logList), BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(640, 420));
        pack();
        setLocationRelativeTo(owner);
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
        append(message);
    }

    @Override
    public void onStart(Path folder) {
        SwingUtilities.invokeLater(() -> append("Started: " + folder));
    }

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

    @Override
    public void onError(String relativePath, Exception error) {
        SwingUtilities.invokeLater(() -> {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            finished("Error on " + relativePath + ": " + cause);
        });
    }

    @Override
    public void onFinish(Luhx.IndexStats stats) {
        SwingUtilities.invokeLater(() -> finished("Finished."));
    }

    /** Re-enables closing if the run ended without a finish or error event. */
    public void ended() {
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            close.setEnabled(true);
        });
    }
}
