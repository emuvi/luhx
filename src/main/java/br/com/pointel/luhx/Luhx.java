package br.com.pointel.luhx;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Indexes supported documents in a folder and searches their extracted content.
 *
 * <p>The index is stored in a {@value #INDEX_DIR_NAME} subdirectory of the
 * indexed folder. Supported files are discovered and read by {@link DocReader}.
 * This class also provides the command-line entry point for indexing and search.
 */
public class Luhx {

    /** Name of the hidden directory that stores an index. */
    public static final String INDEX_DIR_NAME = ".luhx";

    /** Base name of the H2 database file that stores an index. */
    public static final String DB_NAME = "index";

    /**
     * Counts the files created, updated, removed, or left unchanged in an indexing run.
     *
     * @param created   number of new files added to the index
     * @param modified  number of changed files reindexed
     * @param deleted   number of missing files removed from the index
     * @param unchanged number of files that did not need reindexing
     */
    public record IndexStats(int created, int modified, int deleted, int unchanged) {}

    /**
     * A file matched by a search, together with its path and relevance score.
     *
     * @param file         normalized path to the matched file
     * @param relativePath path relative to the indexed folder, using forward slashes
     * @param score        relevance score calculated by Lucene's BM25 similarity
     */
    public record SearchResult(Path file, String relativePath, float score) {}

    private record DocMeta(long id, String filePath, long lastModified, long fileSize) {}

    /**
     * Runs the command-line interface.
     *
     * <p>Accepts {@code --index <path>} to create or update an index, or
     * {@code --search <path> <term>...} to search an existing index.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        configureLogging();
        if (args == null || args.length == 0) {
            LuhxGUI.launch();
            return;
        }
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args == null || args.length < 2) {
            printUsage();
            return;
        }

        String command = args[0];
        Path folder = Paths.get(args[1]).toAbsolutePath().normalize();
        switch (command) {
            case "--index" -> {
                if (args.length != 2) {
                    printUsage();
                    return;
                }
                IndexStats stats = indexFolder(folder);
                System.out.printf(
                        "Index updated at %s (Created: %d, Modified: %d, Deleted: %d, Unchanged: %d)%n",
                        folder.resolve(INDEX_DIR_NAME),
                        stats.created(), stats.modified(), stats.deleted(), stats.unchanged());
            }
            case "--search" -> {
                if (args.length < 3) {
                    System.err.println("Error: Provide at least one search term for --search.");
                    printUsage();
                    return;
                }
                search(folder, Arrays.copyOfRange(args, 2, args.length));
            }
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static void search(Path folder, String[] keys) throws Exception {
        Path indexFile = folder.resolve(INDEX_DIR_NAME).resolve(DB_NAME + ".mv.db");
        if (!Files.exists(indexFile)) {
            System.err.println("Index not found in folder: " + folder);
            System.err.println("Run this first: --index " + folder);
            return;
        }
        if (Arrays.stream(keys).allMatch(key -> key == null || key.isBlank())) {
            System.err.println("No valid search terms were provided.");
            return;
        }

        List<SearchResult> results = searchFolder(folder, keys);
        if (results.isEmpty()) {
            System.out.println("No files found for: " + String.join(" ", keys));
            return;
        }

        System.out.println("Files found (" + results.size() + "):");
        for (SearchResult result : results) {
            System.out.printf("[Score: %.4f] %s%n", result.score(), result.file());
        }
    }

    private static void configureLogging() {
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.OFF);
        Logger.getLogger("org.apache.fontbox").setLevel(Level.OFF);
        Logger.getLogger("org.apache.lucene").setLevel(Level.WARNING);
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  --index <path>             Creates or updates the folder index");
        System.out.println("  --search <path> <keys>...  Searches the folder index for the given terms");
    }

    static String getJdbcUrl(Path folderPath) {
        Path dbBasePath = folderPath.resolve(INDEX_DIR_NAME).resolve(DB_NAME).toAbsolutePath().normalize();
        return "jdbc:h2:file:" + dbBasePath.toString();
    }

    private static void initDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE ALIAS IF NOT EXISTS FTL_INIT FOR 'org.h2.fulltext.FullTextLucene.init'");
            stmt.execute("CALL FTL_INIT()");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS DOCUMENTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    FILE_PATH VARCHAR(2048) UNIQUE NOT NULL,
                    FILE_NAME VARCHAR(512) NOT NULL,
                    LAST_MODIFIED BIGINT NOT NULL,
                    FILE_SIZE BIGINT NOT NULL,
                    CONTENT CLOB
                )
                """);

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM FTL.INDEXES WHERE SCHEMA = 'PUBLIC' AND \"TABLE\" = 'DOCUMENTS'")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("CALL FTL_CREATE_INDEX('PUBLIC', 'DOCUMENTS', 'FILE_NAME, CONTENT')");
                }
            }
        }
    }

    /**
     * Creates or updates the index for a folder.
     *
     * <p>Supported files are scanned recursively, excluding the index directory.
     * New files are indexed, files whose size or modification time changed are
     * reindexed, and entries for files that are no longer present are removed.
     *
     * @param folderPath folder to index
     * @return counts of created, modified, deleted, and unchanged files
     * @throws IllegalArgumentException if {@code folderPath} is null, does not exist,
     *                                  or is not a directory
     * @throws Exception if the index cannot be created or updated, or a supported
     *                   file cannot be read
     */
    public static IndexStats indexFolder(Path folderPath) throws Exception {
        if (folderPath == null || !Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException("The specified path is not a valid folder: " + folderPath);
        }

        Path normalizedFolder = folderPath.toAbsolutePath().normalize();
        Path indexDir = normalizedFolder.resolve(INDEX_DIR_NAME);
        if (!Files.exists(indexDir)) {
            Files.createDirectories(indexDir);
        }

        try (Connection conn = DriverManager.getConnection(getJdbcUrl(normalizedFolder))) {
            initDatabase(conn);

            Map<String, DocMeta> existingDocs = loadExistingDocs(conn);
            Set<String> seenPaths = new HashSet<>();

            int[] counts = new int[4]; // [0]=created, [1]=modified, [2]=deleted, [3]=unchanged

            String insertSql = "INSERT INTO DOCUMENTS (FILE_PATH, FILE_NAME, LAST_MODIFIED, FILE_SIZE, CONTENT) VALUES (?, ?, ?, ?, ?)";
            String updateSql = "UPDATE DOCUMENTS SET FILE_NAME = ?, LAST_MODIFIED = ?, FILE_SIZE = ?, CONTENT = ? WHERE ID = ?";
            String deleteSql = "DELETE FROM DOCUMENTS WHERE ID = ?";

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {

                Files.walkFileTree(normalizedFolder, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (dir.equals(indexDir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                        File file = filePath.toFile();
                        if (!DocReader.canRead(file)) {
                            return FileVisitResult.CONTINUE;
                        }

                        String relPath = normalizedFolder.relativize(filePath).toString().replace('\\', '/');
                        seenPaths.add(relPath);

                        long lastModified = file.lastModified();
                        long fileSize = file.length();
                        DocMeta existing = existingDocs.get(relPath);

                        try {
                            if (existing == null) {
                                String content = extractText(file);
                                insertStmt.setString(1, relPath);
                                insertStmt.setString(2, file.getName());
                                insertStmt.setLong(3, lastModified);
                                insertStmt.setLong(4, fileSize);
                                insertStmt.setString(5, content);
                                insertStmt.executeUpdate();
                                counts[0]++;
                            } else if (existing.lastModified() != lastModified || existing.fileSize() != fileSize) {
                                String content = extractText(file);
                                updateStmt.setString(1, file.getName());
                                updateStmt.setLong(2, lastModified);
                                updateStmt.setLong(3, fileSize);
                                updateStmt.setString(4, content);
                                updateStmt.setLong(5, existing.id());
                                updateStmt.executeUpdate();
                                counts[1]++;
                            } else {
                                counts[3]++;
                            }
                        } catch (Exception e) {
                            throw new IOException("Failed to index " + relPath, e);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                });

                for (DocMeta doc : existingDocs.values()) {
                    if (!seenPaths.contains(doc.filePath())) {
                        deleteStmt.setLong(1, doc.id());
                        deleteStmt.executeUpdate();
                        counts[2]++;
                    }
                }
            }

            return new IndexStats(counts[0], counts[1], counts[2], counts[3]);
        }
    }

    private static String extractText(File file) throws Exception {
        String text = new DocReader(file).read();
        return text != null ? text : "";
    }

    private static Map<String, DocMeta> loadExistingDocs(Connection conn) throws SQLException {
        Map<String, DocMeta> map = new HashMap<>();
        String sql = "SELECT ID, FILE_PATH, LAST_MODIFIED, FILE_SIZE FROM DOCUMENTS";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                DocMeta meta = new DocMeta(
                        rs.getLong("ID"),
                        rs.getString("FILE_PATH"),
                        rs.getLong("LAST_MODIFIED"),
                        rs.getLong("FILE_SIZE")
                );
                map.put(meta.filePath(), meta);
            }
        }
        return map;
    }

    /**
     * Searches the index associated with a folder.
     *
     * <p>Results are ordered by descending BM25 score, with relative paths used
     * to break ties. When multiple non-empty terms are supplied without Boolean
     * operators, they are joined with {@code AND}. An absent index or an empty
     * query produces an empty list.
     *
     * @param folderPath folder containing the index
     * @param keys search terms or a Lucene query, optionally including Boolean operators
     * @return matching files, or an empty list if no index or query is available
     * @throws IllegalArgumentException if {@code folderPath} is null
     * @throws Exception if the index cannot be opened or the query cannot be executed
     */
    public static List<SearchResult> searchFolder(Path folderPath, String... keys) throws Exception {
        if (folderPath == null) {
            throw new IllegalArgumentException("Folder path cannot be null.");
        }
        Path normalizedFolder = folderPath.toAbsolutePath().normalize();
        Path dbFile = normalizedFolder.resolve(INDEX_DIR_NAME).resolve(DB_NAME + ".mv.db");
        if (!Files.exists(dbFile)) {
            return Collections.emptyList();
        }

        String query = buildLuceneQuery(keys);
        if (query.isBlank()) {
            return Collections.emptyList();
        }

        try (Connection conn = DriverManager.getConnection(getJdbcUrl(normalizedFolder))) {
            String sql = """
                SELECT D.FILE_PATH, FT.SCORE
                FROM FTL_SEARCH_DATA(?, 0, 0) FT
                JOIN DOCUMENTS D ON FT."TABLE" = 'DOCUMENTS' AND D.ID = CAST(FT."KEYS"[1] AS BIGINT)
                ORDER BY FT.SCORE DESC, D.FILE_PATH ASC
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, query);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<SearchResult> results = new ArrayList<>();
                    while (rs.next()) {
                        String relPath = rs.getString("FILE_PATH");
                        float score = rs.getFloat("SCORE");
                        Path fullPath = normalizedFolder.resolve(relPath).normalize();
                        results.add(new SearchResult(fullPath, relPath, score));
                    }

                    return results;
                }
            }
        }
    }

    static String buildLuceneQuery(String... keys) {
        if (keys == null || keys.length == 0) {
            return "";
        }
        List<String> nonEmpty = new ArrayList<>();
        boolean hasBooleanOperators = false;
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                String trimmed = key.trim();
                nonEmpty.add(trimmed);
                String upper = trimmed.toUpperCase();
                if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT")
                        || upper.contains(" AND ") || upper.contains(" OR ") || upper.contains(" NOT ")) {
                    hasBooleanOperators = true;
                }
            }
        }
        if (nonEmpty.isEmpty()) {
            return "";
        }
        if (hasBooleanOperators || nonEmpty.size() == 1) {
            return String.join(" ", nonEmpty);
        }
        return String.join(" AND ", nonEmpty);
    }

}
