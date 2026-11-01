package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadPlainTextFileThroughDocReader() throws Exception {
        Path txtPath = tempDir.resolve("sample.txt");
        Files.writeString(txtPath, "Primeira linha de teste\nSegunda linha com Lucene");
        File file = txtPath.toFile();

        assertTrue(DocReader.canRead(file));
        DocReader reader = new DocReader(file);
        assertEquals("sample.txt", reader.getFileName());
        assertEquals("txt", reader.getExtension());
        assertTrue(reader.getFileSize() > 0);
        assertTrue(reader.getLastModified() > 0);

        String content = reader.read();
        assertTrue(content.contains("Primeira linha de teste"));
        assertTrue(content.contains("Segunda linha com Lucene"));
        assertEquals(2, reader.readLines().size());
        assertEquals("Primeira", reader.readPreview(8));
        assertEquals(8, reader.getWordCount());
        assertTrue(reader.getCharCount() >= 47);
        assertEquals(1, reader.countPages());
        assertNotNull(reader.getCreationDate());
        assertNull(reader.getPageAsImage(0, 72));
    }

    @Test
    void shouldReadPdfFileThroughDocReader() throws Exception {
        File pdfFile = tempDir.resolve("document.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Titulo PDF");
            info.setAuthor("Autor Teste");
            doc.setDocumentInformation(info);

            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Conteudo extraido de um arquivo PDF para o Lucene");
                contentStream.endText();
            }
            doc.save(pdfFile);
        }

        assertTrue(DocReader.canRead(pdfFile));
        DocReader reader = new DocReader(pdfFile);
        String text = reader.read();
        assertTrue(text.contains("Conteudo extraido de um arquivo PDF para o Lucene"));
        assertEquals(1, reader.countPages());
        assertEquals("Titulo PDF", reader.getTitle());
        assertEquals("Autor Teste", reader.getAuthor());
        assertNotNull(reader.getPageAsImage(0, 72));
    }

    @Test
    void shouldReadWordDocxFileThroughDocReader() throws Exception {
        File docxFile = tempDir.resolve("document.docx").toFile();
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile)) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("Contrato corporativo indexado no H2 Database.");
            doc.write(out);
        }

        assertTrue(DocReader.canRead(docxFile));
        DocReader reader = new DocReader(docxFile);
        String text = reader.read();
        assertTrue(text.contains("Contrato corporativo indexado no H2 Database."));
        assertTrue(reader.getWordCount() >= 6);
    }

    @Test
    void shouldReadExcelXlsxFileThroughDocReader() throws Exception {
        File xlsxFile = tempDir.resolve("spreadsheet.xlsx").toFile();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(xlsxFile)) {
            var sheet = workbook.createSheet("Financeiro");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Faturamento");
            row.createCell(1).setCellValue("Trimestral");
            workbook.write(out);
        }

        assertTrue(DocReader.canRead(xlsxFile));
        DocReader reader = new DocReader(xlsxFile);
        String text = reader.read();
        assertTrue(text.contains("Financeiro"));
        assertTrue(text.contains("Faturamento"));
        assertTrue(text.contains("Trimestral"));
        assertEquals(1, reader.countPages());
    }

    @Test
    void shouldReadPowerPointPptxFileThroughDocReader() throws Exception {
        File pptxFile = tempDir.resolve("presentation.pptx").toFile();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream out = new FileOutputStream(pptxFile)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new Rectangle(50, 50, 400, 100));
            box.setText("Apresentacao sobre arquitetura de motores de busca");
            ppt.write(out);
        }

        assertTrue(DocReader.canRead(pptxFile));
        DocReader reader = new DocReader(pptxFile);
        String text = reader.read();
        assertTrue(text.contains("Apresentacao sobre arquitetura de motores de busca"));
        assertEquals(1, reader.countPages());
    }

    @Test
    void shouldHandleUnsupportedFileGracefully() throws Exception {
        Path binPath = tempDir.resolve("archive.bin");
        Files.write(binPath, new byte[]{1, 2, 3, 4});
        File binFile = binPath.toFile();

        assertFalse(DocReader.canRead(binFile));
        DocReader reader = new DocReader(binFile);
        assertEquals("", reader.read());
        assertEquals(List.of(), reader.readLines());
        assertEquals("", reader.readPreview(10));
        assertEquals(0, reader.countPages());
        assertEquals(0, reader.getWordCount());
        assertEquals(0, reader.getCharCount());
    }

}

