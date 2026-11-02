package br.com.pointel.luhx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Command-line interface for indexing and searching documents. */
public final class LuhxCMD {

    private LuhxCMD() {}

    /**
     * Runs the command-line interface, or launches the graphical interface
     * when no arguments are provided.
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
        if ("--version".equals(args[0])) {
            if (args.length != 1) {
                printUsage();
                return;
            }
            printVersion();
            return;
        }

        if (args.length < 2) {
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
                Luhx.IndexStats stats = Luhx.indexFolder(folder);
                System.out.printf(
                        "Index updated at %s (Created: %d, Modified: %d, Deleted: %d, Unchanged: %d)%n",
                        folder.resolve(Luhx.INDEX_DIR_NAME),
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
        Path indexFile = folder.resolve(Luhx.INDEX_DIR_NAME).resolve(Luhx.DB_FILE_NAME);
        if (!Files.exists(indexFile)) {
            System.err.println("Index not found in folder: " + folder);
            System.err.println("Run this first: --index " + folder);
            return;
        }
        if (Arrays.stream(keys).allMatch(key -> key == null || key.isBlank())) {
            System.err.println("No valid search terms were provided.");
            return;
        }

        List<Luhx.SearchResult> results = Luhx.searchFolder(folder, keys);
        if (results.isEmpty()) {
            System.out.println("No files found for: " + String.join(" ", keys));
            return;
        }

        System.out.println("Files found (" + results.size() + "):");
        for (Luhx.SearchResult result : results) {
            System.out.printf("[Score: %.4f] %s%n", result.score(), result.file());
        }
    }

    private static void configureLogging() {
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.OFF);
        Logger.getLogger("org.apache.fontbox").setLevel(Level.OFF);
        Logger.getLogger("org.apache.lucene").setLevel(Level.WARNING);
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    }

    private static void printVersion() {
        String version = LuhxCMD.class.getPackage().getImplementationVersion();
        System.out.println("Luhx " + (version == null ? "development" : version));
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  --version                  Prints the application version");
        System.out.println("  --index <path>             Creates or updates the folder index");
        System.out.println("  --search <path> <keys>...  Searches the folder index for the given terms");
    }
}
