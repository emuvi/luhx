package br.com.pointel.luhx;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
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
import javax.swing.JLabel;
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

    private static final String PATHS_FILE = "paths.ini";
    private static final int SEARCH_PAGE_SIZE = 50;

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
        JButton index = new JButton("#");
        JButton search = new JButton("Search");
        add.addActionListener(e -> addFolder());
        remove.addActionListener(e -> removeSelected());
        index.addActionListener(e -> indexSelected());
        search.addActionListener(e -> search());
        searchField.addActionListener(e -> search());

        JPanel top = new JPanel(new BorderLayout(WindowConfig.GAP, 0));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, WindowConfig.GAP, 0));
        leftButtons.add(add);
        leftButtons.add(remove);
        leftButtons.add(index);
        top.add(leftButtons, BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        top.add(search, BorderLayout.EAST);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                        openPath(model.get(index));
                    }
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, WindowConfig.GAP));
        content.setBorder(BorderFactory.createEmptyBorder(
                WindowConfig.PADDING, WindowConfig.PADDING, WindowConfig.PADDING, WindowConfig.PADDING));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        setContentPane(content);
        setPreferredSize(new Dimension(760, 380));
        pack();
        WindowConfig.track(this, "main");
        loadPaths();
    }

    private void addFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (!model.contains(path)) {
                model.addElement(path);
                savePaths();
            }
        }
    }

    private void removeSelected() {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            model.remove(index);
            savePaths();
        }
    }

    private void loadPaths() {
        Path file = Path.of(PATHS_FILE);
        if (!java.nio.file.Files.exists(file)) {
            return;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                String text = line.trim();
                if (!text.isEmpty()) {
                    Path path = Path.of(text).toAbsolutePath().normalize();
                    if (!model.contains(path)) {
                        model.addElement(path);
                    }
                }
            }
        } catch (java.io.IOException | java.nio.file.InvalidPathException ex) {
            JOptionPane.showMessageDialog(this, "Could not read " + PATHS_FILE + ": " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void savePaths() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            lines.add(model.get(i).toString());
        }
        try {
            java.nio.file.Files.write(Path.of(PATHS_FILE), lines, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save " + PATHS_FILE + ": " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void indexSelected() {
        Path folder = list.getSelectedValue();
        if (folder == null) {
            JOptionPane.showMessageDialog(this, "Select a folder from the list.");
            return;
        }
        startIndexing(folder);
    }

    private void startIndexing(Path folder) {
        if (!indexing.add(folder)) {
            return;
        }
        IndexMonitor monitor = new IndexMonitor(this, folder);
        monitor.setVisible(true);
        new SwingWorker<Luhx.IndexStats, Void>() {
            @Override
            protected Luhx.IndexStats doInBackground() throws Exception {
                return Luhx.indexFolder(folder, monitor);
            }

            @Override
            protected void done() {
                indexing.remove(folder);
                monitor.ended();
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
        if (!java.nio.file.Files.exists(folder.resolve(Luhx.INDEX_DIR_NAME).resolve(Luhx.DB_FILE_NAME))) {
            JOptionPane.showMessageDialog(this,
                    "Folder not indexed yet. Double-click it to index.");
            return;
        }
        String[] keys = text.split("\\s+");
        new SwingWorker<List<Luhx.SearchResult>, Void>() {
            @Override
            protected List<Luhx.SearchResult> doInBackground() throws Exception {
                return Luhx.searchFolderPage(folder, 0, SEARCH_PAGE_SIZE + 1, keys);
            }

            @Override
            protected void done() {
                try {
                    List<Luhx.SearchResult> results = get();
                    if (results.isEmpty()) {
                        JOptionPane.showMessageDialog(LuhxGUI.this, "No files found.",
                                "No results for: " + text, JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        showResults(results, text, folder, keys);
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(LuhxGUI.this, "Search failed: " + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showResults(
            List<Luhx.SearchResult> firstPage, String query, Path folder, String[] keys) {
        List<Luhx.SearchResult> loaded = new ArrayList<>();
        DefaultListModel<String> resultModel = new DefaultListModel<>();
        int firstPageSize = Math.min(firstPage.size(), SEARCH_PAGE_SIZE);
        for (int i = 0; i < firstPageSize; i++) {
            addResult(firstPage.get(i), loaded, resultModel);
        }
        JList<String> view = new JList<>(resultModel);
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = view.locationToIndex(e.getPoint());
                    if (index >= 0 && view.getCellBounds(index, index).contains(e.getPoint())) {
                        openPath(loaded.get(index).file());
                    }
                }
            }
        });

        JLabel status = new JLabel();
        JButton loadMore = new JButton();
        updateSearchStatus(status, loadMore, loaded.size(), firstPage.size() > SEARCH_PAGE_SIZE);
        loadMore.addActionListener(e ->
                loadMoreResults(folder, keys, loaded, resultModel, status, loadMore));

        JPanel content = new JPanel(new BorderLayout(0, WindowConfig.GAP));
        content.add(new JScrollPane(view), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(WindowConfig.GAP, 0));
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(loadMore, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        content.setBorder(BorderFactory.createEmptyBorder(
                WindowConfig.PADDING, WindowConfig.PADDING, WindowConfig.PADDING, WindowConfig.PADDING));
        content.setPreferredSize(new Dimension(700, 340));

        JFrame resultsWindow = new JFrame("Results for: " + query);
        resultsWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        resultsWindow.setContentPane(content);
        resultsWindow.pack();
        WindowConfig.track(resultsWindow, "results");
        resultsWindow.setVisible(true);
    }

    private static void addResult(
            Luhx.SearchResult result, List<Luhx.SearchResult> loaded, DefaultListModel<String> model) {
        loaded.add(result);
        model.addElement(String.format("[%.4f] %s", result.score(), result.file()));
    }

    private static void updateSearchStatus(JLabel status, JButton loadMore, int loaded, boolean hasMore) {
        status.setText("Showing " + loaded + " results");
        loadMore.setText(hasMore ? "Load more" : "All results loaded");
        loadMore.setEnabled(hasMore);
    }

    private void loadMoreResults(
            Path folder,
            String[] keys,
            List<Luhx.SearchResult> loaded,
            DefaultListModel<String> model,
            JLabel status,
            JButton loadMore) {
        int offset = loaded.size();
        loadMore.setEnabled(false);
        loadMore.setText("Loading...");
        new SwingWorker<List<Luhx.SearchResult>, Void>() {
            @Override
            protected List<Luhx.SearchResult> doInBackground() throws Exception {
                return Luhx.searchFolderPage(folder, offset, SEARCH_PAGE_SIZE + 1, keys);
            }

            @Override
            protected void done() {
                try {
                    List<Luhx.SearchResult> page = get();
                    int pageSize = Math.min(page.size(), SEARCH_PAGE_SIZE);
                    for (int i = 0; i < pageSize; i++) {
                        addResult(page.get(i), loaded, model);
                    }
                    updateSearchStatus(status, loadMore, loaded.size(), page.size() > SEARCH_PAGE_SIZE);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    loadMore.setText("Load more");
                    loadMore.setEnabled(true);
                    JOptionPane.showMessageDialog(LuhxGUI.this, "Could not load more results: "
                            + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openPath(Path path) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Opening files is not supported on this system.");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                throw new IOException("Opening files is not supported on this system.");
            }
            desktop.open(path.toFile());
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            JOptionPane.showMessageDialog(this, "Could not open " + path + ": " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Installs the FlatLaf look and feel and shows the window. */
    public static void launch() {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new LuhxGUI().setVisible(true));
    }

    /** Launches the graphical interface. */
    public static void main(String[] args) {
        launch();
    }
}
