package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuhxIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIndexAndSearchAcrossMultipleFileFormatsAndSubdirectories() throws Exception {
        Path subDir = Files.createDirectories(tempDir.resolve("subfolder"));

        Files.writeString(tempDir.resolve("architecture.txt"),
                "Apache Lucene uses the BM25 algorithm for text search ranking.");
        Files.writeString(subDir.resolve("database.md"),
                "The embedded H2 Database integrates full-text search with FTL_INIT.");
        Files.write(tempDir.resolve("ignorado.bin"), new byte[]{0, 1, 2, 3});

        createSamplePdf(tempDir.resolve("manual.pdf").toFile(),
                "Technical manual about inverted indexes and Finite State Transducers.");
        createSampleDocx(subDir.resolve("relatorio.docx").toFile(),
                "Executive performance report about MemorySegment in Java 21.");

        Luhx.IndexStats stats = Luhx.indexFolder(tempDir);
        assertEquals(4, stats.created());
        assertEquals(0, stats.modified());
        assertEquals(0, stats.deleted());
        assertEquals(0, stats.unchanged());

        Path dbFile = tempDir.resolve(Luhx.INDEX_DIR_NAME).resolve(Luhx.DB_FILE_NAME);
        assertTrue(Files.exists(dbFile), "The H2 database file should be saved in the folder itself");

        List<Luhx.SearchResult> bm25Results = Luhx.searchFolder(tempDir, "BM25");
        assertEquals(1, bm25Results.size());
        assertEquals("architecture.txt", bm25Results.get(0).relativePath());

        List<Luhx.SearchResult> pdfResults = Luhx.searchFolder(tempDir, "Transducers");
        assertEquals(1, pdfResults.size());
        assertEquals("manual.pdf", pdfResults.get(0).relativePath());

        List<Luhx.SearchResult> docxResults = Luhx.searchFolder(tempDir, "MemorySegment", "Java");
        assertEquals(1, docxResults.size());
        assertEquals("subfolder/relatorio.docx", docxResults.get(0).relativePath());

        List<Luhx.SearchResult> orResults = Luhx.searchFolder(tempDir, "BM25", "OR", "FTL_INIT");
        assertEquals(2, orResults.size());
    }

    @Test
    void shouldContinueIndexingWhenOneOfficeFileIsEmpty() throws Exception {
        Files.writeString(tempDir.resolve("first.txt"), "A searchable valid document.");
        Files.writeString(tempDir.resolve("second.txt"), "Another searchable valid document.");
        Files.createFile(tempDir.resolve("empty.docx"));
        List<String> errors = new CopyOnWriteArrayList<>();
        Luhx.IndexObserver observer = new Luhx.IndexObserver() {
            @Override
            public void onError(String relativePath, Exception error) {
                errors.add(relativePath);
            }
        };

        Luhx.IndexStats stats = Luhx.indexFolder(tempDir, observer);

        assertEquals(new Luhx.IndexStats(2, 0, 0, 0), stats);
        assertEquals(List.of("empty.docx"), errors);
        assertEquals(2, Luhx.searchFolder(tempDir, "searchable").size());
    }

    @Test
    void shouldReturnSearchResultsInRankedPages() throws Exception {
        Files.writeString(tempDir.resolve("first.txt"), "ranking ranking ranking");
        Files.writeString(tempDir.resolve("second.txt"), "ranking ranking");
        Files.writeString(tempDir.resolve("third.txt"), "ranking");
        Luhx.indexFolder(tempDir);

        List<Luhx.SearchResult> allResults = Luhx.searchFolder(tempDir, "ranking");
        List<Luhx.SearchResult> firstPage = Luhx.searchFolderPage(tempDir, 0, 2, "ranking");
        List<Luhx.SearchResult> secondPage = Luhx.searchFolderPage(tempDir, 2, 2, "ranking");

        assertEquals(2, firstPage.size());
        assertEquals(1, secondPage.size());
        assertEquals(allResults, java.util.stream.Stream.concat(
                firstPage.stream(), secondPage.stream()).toList());
        assertTrue(Luhx.searchFolderPage(tempDir, 3, 2, "ranking").isEmpty());
    }

    @Test
    void shouldReturnMoreThanOneHundredResultsAcrossPages() throws Exception {
        for (int i = 0; i < 130; i++) {
            Files.writeString(tempDir.resolve("doc" + i + ".txt"), "manyhits");
        }
        Luhx.indexFolder(tempDir);

        assertEquals(130, Luhx.searchFolder(tempDir, "manyhits").size());
        assertEquals(30, Luhx.searchFolderPage(tempDir, 100, 50, "manyhits").size());
    }

    @Test
    void shouldReportIndexLifecycleAndExcludeTheIndexDirectory() throws Exception {
        Path document = tempDir.resolve("visible.txt");
        Files.writeString(document, "A searchable lifecycle document.");
        Path hiddenDocument = Files.createDirectories(tempDir.resolve(Luhx.INDEX_DIR_NAME))
                .resolve("hidden.txt");
        Files.writeString(hiddenDocument, "This hiddenindexword must not be indexed.");

        List<Path> startedFolders = new CopyOnWriteArrayList<>();
        List<String> indexedFiles = new CopyOnWriteArrayList<>();
        List<Luhx.IndexAction> actions = new CopyOnWriteArrayList<>();
        List<Luhx.IndexStats> finishedStats = new CopyOnWriteArrayList<>();
        List<String> errors = new CopyOnWriteArrayList<>();
        Luhx.IndexObserver observer = new Luhx.IndexObserver() {
            @Override
            public void onStart(Path folder) {
                startedFolders.add(folder);
            }

            @Override
            public void onFile(String relativePath, Luhx.IndexAction action) {
                indexedFiles.add(relativePath);
                actions.add(action);
            }

            @Override
            public void onError(String relativePath, Exception error) {
                errors.add(relativePath);
            }

            @Override
            public void onFinish(Luhx.IndexStats stats) {
                finishedStats.add(stats);
            }
        };

        Luhx.IndexStats stats = Luhx.indexFolder(tempDir, observer);

        assertEquals(new Luhx.IndexStats(1, 0, 0, 0), stats);
        assertEquals(List.of(tempDir.toAbsolutePath().normalize()), startedFolders);
        assertEquals(List.of("visible.txt"), indexedFiles);
        assertEquals(List.of(Luhx.IndexAction.CREATED), actions);
        assertEquals(List.of(stats), finishedStats);
        assertTrue(errors.isEmpty());
        assertEquals(1, Luhx.searchFolder(tempDir, "lifecycle").size());
        assertTrue(Luhx.searchFolder(tempDir, "hiddenindexword").isEmpty());
    }

    @Test
    void shouldPreserveTheExistingIndexWhenIndexingIsCancelled() throws Exception {
        Path originalDocument = tempDir.resolve("original.txt");
        Files.writeString(originalDocument, "previouslyindexedword remains in the index.");
        Luhx.indexFolder(tempDir);

        Files.delete(originalDocument);
        Files.writeString(tempDir.resolve("new.txt"), "newlycreatedword must wait for indexing.");

        List<Luhx.IndexStats> cancelledStats = new CopyOnWriteArrayList<>();
        List<Luhx.IndexStats> finishedStats = new CopyOnWriteArrayList<>();
        Luhx.IndexObserver observer = new Luhx.IndexObserver() {
            @Override
            public boolean isCancelled() {
                return true;
            }

            @Override
            public void onCancel(Luhx.IndexStats stats) {
                cancelledStats.add(stats);
            }

            @Override
            public void onFinish(Luhx.IndexStats stats) {
                finishedStats.add(stats);
            }
        };

        Luhx.IndexStats stats = Luhx.indexFolder(tempDir, observer);

        assertEquals(new Luhx.IndexStats(0, 0, 0, 0), stats);
        assertEquals(List.of(stats), cancelledStats);
        assertTrue(finishedStats.isEmpty());
        List<Luhx.SearchResult> priorResults = Luhx.searchFolder(tempDir, "previouslyindexedword");
        assertEquals(1, priorResults.size());
        assertFalse(Files.exists(priorResults.get(0).file()));
        assertTrue(Luhx.searchFolder(tempDir, "newlycreatedword").isEmpty());
    }

    @Test
    void shouldUpdateIndexWhenFilesAreCreatedModifiedOrDeleted() throws Exception {
        Path doc1 = tempDir.resolve("doc1.txt");
        Path doc2 = tempDir.resolve("doc2.txt");
        Path doc3 = tempDir.resolve("doc3.txt");

        Files.writeString(doc1, "Original content about native search algorithms.");
        Files.writeString(doc2, "Document that will remain unchanged during the test.");
        Files.writeString(doc3, "Temporary document containing the word ephemeral.");

        // 1. Initial indexing
        Luhx.IndexStats initialStats = Luhx.indexFolder(tempDir);
        assertEquals(3, initialStats.created());
        assertEquals(0, initialStats.modified());
        assertEquals(0, initialStats.deleted());
        assertEquals(0, initialStats.unchanged());

        assertEquals(1, Luhx.searchFolder(tempDir, "ephemeral").size());
        assertEquals(1, Luhx.searchFolder(tempDir, "native").size());

        // 2. Reindexing without any file changes
        Luhx.IndexStats unchangedStats = Luhx.indexFolder(tempDir);
        assertEquals(0, unchangedStats.created());
        assertEquals(0, unchangedStats.modified());
        assertEquals(0, unchangedStats.deleted());
        assertEquals(3, unchangedStats.unchanged());

        // 3. Create doc4, modify doc1, and delete doc3
        Path doc4 = tempDir.resolve("doc4.txt");
        Files.writeString(doc4, "New file added with the term encryption.");

        Files.writeString(doc1, "Updated content about distributed indexing engines.");
        Files.setLastModifiedTime(doc1, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        Files.delete(doc3);

        Luhx.IndexStats updatedStats = Luhx.indexFolder(tempDir);
        assertEquals(1, updatedStats.created(), "Should detect 1 created file (doc4.txt)");
        assertEquals(1, updatedStats.modified(), "Should detect 1 modified file (doc1.txt)");
        assertEquals(1, updatedStats.deleted(), "Should detect 1 deleted file (doc3.txt)");
        assertEquals(1, updatedStats.unchanged(), "Should keep 1 unchanged file (doc2.txt)");

        // Verify that the created file can be found
        List<Luhx.SearchResult> createdSearch = Luhx.searchFolder(tempDir, "encryption");
        assertEquals(1, createdSearch.size());
        assertEquals("doc4.txt", createdSearch.get(0).relativePath());

        // Verify that the modified file matches its new content, not the old content
        List<Luhx.SearchResult> modifiedNewTerm = Luhx.searchFolder(tempDir, "distributed");
        assertEquals(1, modifiedNewTerm.size());
        assertEquals("doc1.txt", modifiedNewTerm.get(0).relativePath());
        assertTrue(Luhx.searchFolder(tempDir, "native").isEmpty(),
                "The old term from the modified file should no longer return the document");

        // Verify that the deleted file no longer appears in search results
        assertTrue(Luhx.searchFolder(tempDir, "ephemeral").isEmpty(),
                "The deleted file should no longer appear in search results");
    }

    @Test
    void shouldContinueIndexingAndReportErrorsWhenAFileCannotBeRead() throws Exception {
        Files.writeString(tempDir.resolve("broken.pdf"), "not a PDF");
        Files.writeString(tempDir.resolve("valid.txt"), "A valid document with a searchable keyword.");
        ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        List<String> failedFiles = new CopyOnWriteArrayList<>();
        try {
            System.setErr(new PrintStream(errorBuffer, true, StandardCharsets.UTF_8));

            Luhx.IndexStats stats = Luhx.indexFolder(tempDir, new Luhx.IndexObserver() {
                @Override
                public void onError(String relativePath, Exception error) {
                    failedFiles.add(relativePath);
                }
            });

            assertEquals(1, stats.created());
            assertEquals(1, Luhx.searchFolder(tempDir, "searchable").size());
            assertTrue(failedFiles.contains("broken.pdf"));
            assertTrue(errorBuffer.toString(StandardCharsets.UTF_8).contains("Failed to index broken.pdf"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void shouldContinueProcessingAfterAWorkerFailsUnexpectedly() throws Exception {
        Files.writeString(tempDir.resolve("worker-error.txt"), "This worker will fail.");
        Files.writeString(tempDir.resolve("valid.txt"), "A valid document with a searchable keyword.");
        AtomicBoolean injectedFailure = new AtomicBoolean();
        List<String> failedFiles = new CopyOnWriteArrayList<>();
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errorBuffer, true, StandardCharsets.UTF_8));

            Luhx.IndexStats stats = Luhx.indexFolder(tempDir, new Luhx.IndexObserver() {
                @Override
                public void onFile(String relativePath, Luhx.IndexAction action) {
                    if (relativePath.equals("worker-error.txt")
                            && action == Luhx.IndexAction.CREATED
                            && injectedFailure.compareAndSet(false, true)) {
                        throw new AssertionError("Unexpected worker failure");
                    }
                }

                @Override
                public void onError(String relativePath, Exception error) {
                    failedFiles.add(relativePath);
                }
            });

            assertEquals(2, stats.created());
            assertEquals(1, Luhx.searchFolder(tempDir, "searchable").size());
            assertEquals(1, failedFiles.size());
            assertTrue(errorBuffer.toString(StandardCharsets.UTF_8).contains("Failed to index worker-error.txt"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void shouldExecuteCliCommandsViaMainMethod() throws Exception {
        Path file = tempDir.resolve("cli_test.txt");
        Files.writeString(file, "Command-line test with Luhx and a unique keyword.");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));

            Luhx.main(new String[]{"--index", tempDir.toString()});
            String indexOutput = outBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(indexOutput.contains("Created: 1"));

            outBuffer.reset();
            LuhxCMD.main(new String[]{"--search", tempDir.toString(), "unique"});
            String searchOutput = outBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(searchOutput.contains("Files found (1):"));
            assertTrue(searchOutput.contains("cli_test.txt"));

            outBuffer.reset();
            LuhxCMD.main(new String[]{"--search", tempDir.toString(), "nonexistent"});
            String emptySearchOutput = outBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(emptySearchOutput.contains("No files found for: nonexistent"));
            assertFalse(emptySearchOutput.contains("cli_test.txt"));
        } finally {
            System.setOut(originalOut);
        }
    }

    private void createSamplePdf(File file, String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            doc.save(file);
        }
    }

    private void createSampleDocx(File file, String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(file)) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
        }
    }

}
