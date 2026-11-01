package br.com.pointel.luhx;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Swing graphical interface for indexing and searching folders. */
public class LuhxGUI extends JFrame {

    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final JTextField searchField = new JTextField();
    private final Set<Path> indexing = new HashSet<>();

    /** Creates the main window. */
    public LuhxGUI() {
        super("Luhx");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JButton add = new JButton("+");
        JButton remove = new JButton("-");
        JButton search = new JButton("Search");
        add.addActionListener(e -> addFolder());
        remove.addActionListener(e -> removeSelected());
        search.addActionListener(e -> search());
        searchField.addActionListener(e -> search());

        JPanel left = new JPanel();
        left.add(add);
        left.add(remove);
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(left, BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        top.add(search, BorderLayout.EAST);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                        startIndexing(model.get(index));
                    }
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        setContentPane(content);
        setPreferredSize(new Dimension(760, 380));
        pack();
        setLocationRelativeTo(null);
    }

    private void addFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (!model.contains(path)) {
                model.addElement(path);
            }
        }
    }

    private void removeSelected() {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            model.remove(index);
        }
    }

    private void startIndexing(Path folder) {
        if (!indexing.add(folder)) {
            return;
        }
        new SwingWorker<Luhx.IndexStats, Void>() {
            @Override
            protected Luhx.IndexStats doInBackground() throws Exception {
                return Luhx.indexFolder(folder);
            }

            @Override
            protected void done() {
                indexing.remove(folder);
                try {
                    Luhx.IndexStats s = get();
                    JOptionPane.showMessageDialog(LuhxGUI.this, String.format(
                            "Indexing completed at %s%nCreated: %d, Modified: %d, Deleted: %d, Unchanged: %d",
                            folder, s.created(), s.modified(), s.deleted(), s.unchanged()));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(LuhxGUI.this,
                            "Failed to index " + folder + ": " + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void search() {
        Path folder = list.getSelectedValue();
        if (folder == null) {
            JOptionPane.showMessageDialog(this, "Select a folder from the list.");
            return;
        }
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter search terms.");
            return;
        }
        if (!java.nio.file.Files.exists(folder.resolve(Luhx.INDEX_DIR_NAME).resolve(Luhx.DB_NAME + ".mv.db"))) {
            JOptionPane.showMessageDialog(this,
                    "Folder not indexed yet. Double-click it to index.");
            return;
        }
        String[] keys = text.split("\\s+");
        new SwingWorker<List<Luhx.SearchResult>, Void>() {
            @Override
            protected List<Luhx.SearchResult> doInBackground() throws Exception {
                return Luhx.searchFolder(folder, keys);
            }

            @Override
            protected void done() {
                try {
                    showResults(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(LuhxGUI.this, "Search failed: " + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showResults(List<Luhx.SearchResult> results) {
        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No files found.");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Luhx.SearchResult r : results) {
            lines.add(String.format("[%.4f] %s", r.score(), r.file()));
        }
        JList<String> view = new JList<>(lines.toArray(new String[0]));
        JScrollPane pane = new JScrollPane(view);
        pane.setPreferredSize(new Dimension(700, 300));
        JOptionPane.showMessageDialog(this, pane, "Results (" + results.size() + ")",
                JOptionPane.PLAIN_MESSAGE);
    }

    /** Installs the FlatLaf look and feel and shows the window. */
    public static void launch() {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new LuhxGUI().setVisible(true));
    }
}
