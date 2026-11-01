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
        Path subDir = Files.createDirectories(tempDir.resolve("subpasta"));

        Files.writeString(tempDir.resolve("arquitetura.txt"),
                "O Apache Lucene utiliza o algoritmo BM25 para ranking de busca textual.");
        Files.writeString(subDir.resolve("banco.md"),
                "O H2 Database embarcado integra busca full-text com FTL_INIT.");
        Files.write(tempDir.resolve("ignorado.bin"), new byte[]{0, 1, 2, 3});

        createSamplePdf(tempDir.resolve("manual.pdf").toFile(),
                "Manual tecnico sobre indices invertidos e Finite State Transducers.");
        createSampleDocx(subDir.resolve("relatorio.docx").toFile(),
                "Relatorio executivo de performance com MemorySegment no Java 21.");

        Luhx.IndexStats stats = Luhx.indexFolder(tempDir);
        assertEquals(4, stats.created());
        assertEquals(0, stats.modified());
        assertEquals(0, stats.deleted());
        assertEquals(0, stats.unchanged());

        Path dbFile = tempDir.resolve(Luhx.INDEX_DIR_NAME).resolve(Luhx.DB_NAME + ".mv.db");
        assertTrue(Files.exists(dbFile), "O arquivo do banco H2 deve ser salvo dentro da própria pasta");

        List<Luhx.SearchResult> bm25Results = Luhx.searchFolder(tempDir, "BM25");
        assertEquals(1, bm25Results.size());
        assertEquals("arquitetura.txt", bm25Results.get(0).relativePath());

        List<Luhx.SearchResult> pdfResults = Luhx.searchFolder(tempDir, "Transducers");
        assertEquals(1, pdfResults.size());
        assertEquals("manual.pdf", pdfResults.get(0).relativePath());

        List<Luhx.SearchResult> docxResults = Luhx.searchFolder(tempDir, "MemorySegment", "Java");
        assertEquals(1, docxResults.size());
        assertEquals("subpasta/relatorio.docx", docxResults.get(0).relativePath());

        List<Luhx.SearchResult> orResults = Luhx.searchFolder(tempDir, "BM25", "OR", "FTL_INIT");
        assertEquals(2, orResults.size());
    }

    @Test
    void shouldUpdateIndexWhenFilesAreCreatedModifiedOrDeleted() throws Exception {
        Path doc1 = tempDir.resolve("doc1.txt");
        Path doc2 = tempDir.resolve("doc2.txt");
        Path doc3 = tempDir.resolve("doc3.txt");

        Files.writeString(doc1, "Conteudo original sobre algoritmos de busca nativa.");
        Files.writeString(doc2, "Documento que permanecera inalterado durante o teste.");
        Files.writeString(doc3, "Documento temporario contendo a palavra efemero.");

        // 1. Primeira indexação
        Luhx.IndexStats initialStats = Luhx.indexFolder(tempDir);
        assertEquals(3, initialStats.created());
        assertEquals(0, initialStats.modified());
        assertEquals(0, initialStats.deleted());
        assertEquals(0, initialStats.unchanged());

        assertEquals(1, Luhx.searchFolder(tempDir, "efemero").size());
        assertEquals(1, Luhx.searchFolder(tempDir, "nativa").size());

        // 2. Reindexação sem nenhuma mudança nos arquivos
        Luhx.IndexStats unchangedStats = Luhx.indexFolder(tempDir);
        assertEquals(0, unchangedStats.created());
        assertEquals(0, unchangedStats.modified());
        assertEquals(0, unchangedStats.deleted());
        assertEquals(3, unchangedStats.unchanged());

        // 3. Criar doc4, modificar doc1 e excluir doc3
        Path doc4 = tempDir.resolve("doc4.txt");
        Files.writeString(doc4, "Novo arquivo adicionado com o termo criptografia.");

        Files.writeString(doc1, "Conteudo atualizado sobre motores de indexacao distribuida.");
        Files.setLastModifiedTime(doc1, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        Files.delete(doc3);

        Luhx.IndexStats updatedStats = Luhx.indexFolder(tempDir);
        assertEquals(1, updatedStats.created(), "Deve detectar 1 arquivo criado (doc4.txt)");
        assertEquals(1, updatedStats.modified(), "Deve detectar 1 arquivo modificado (doc1.txt)");
        assertEquals(1, updatedStats.deleted(), "Deve detectar 1 arquivo excluído (doc3.txt)");
        assertEquals(1, updatedStats.unchanged(), "Deve manter 1 arquivo inalterado (doc2.txt)");

        // Verifica que o arquivo criado é encontrado
        List<Luhx.SearchResult> createdSearch = Luhx.searchFolder(tempDir, "criptografia");
        assertEquals(1, createdSearch.size());
        assertEquals("doc4.txt", createdSearch.get(0).relativePath());

        // Verifica que o arquivo modificado responde pelo novo conteúdo e não pelo antigo
        List<Luhx.SearchResult> modifiedNewTerm = Luhx.searchFolder(tempDir, "distribuida");
        assertEquals(1, modifiedNewTerm.size());
        assertEquals("doc1.txt", modifiedNewTerm.get(0).relativePath());
        assertTrue(Luhx.searchFolder(tempDir, "nativa").isEmpty(),
                "Termo antigo do arquivo modificado não deve mais retornar o documento");

        // Verifica que o arquivo excluído não retorna mais na busca
        assertTrue(Luhx.searchFolder(tempDir, "efemero").isEmpty(),
                "Arquivo excluído não deve mais aparecer nos resultados de busca");
    }

    @Test
    void shouldExecuteCliCommandsViaMainMethod() throws Exception {
        Path file = tempDir.resolve("cli_test.txt");
        Files.writeString(file, "Teste de linha de comando com Luhx e palavra-chave exclusiva.");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));

            Luhx.main(new String[]{"--index", tempDir.toString()});
            String indexOutput = outBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(indexOutput.contains("Criados: 1"));

            outBuffer.reset();
            Luhx.main(new String[]{"--search", tempDir.toString(), "exclusiva"});
            String searchOutput = outBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(searchOutput.contains("Arquivos encontrados (1):"));
            assertTrue(searchOutput.contains("cli_test.txt"));

            outBuffer.reset();
            Luhx.main(new String[]{"--search", tempDir.toString(), "inexistente"});
            String emptySearchOutput = outBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(emptySearchOutput.contains("Nenhum arquivo encontrado para: inexistente"));
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
