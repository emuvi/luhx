package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
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
        Files.writeString(txtPath, "First line for testing\nSecond line with Lucene");
        File file = txtPath.toFile();

        assertTrue(DocReader.canRead(file));
        DocReader reader = new DocReader(file);
        assertEquals("sample.txt", reader.getFileName());
        assertEquals("txt", reader.getExtension());
        assertTrue(reader.getFileSize() > 0);
        assertTrue(reader.getLastModified() > 0);

        String content = reader.read();
        assertTrue(content.contains("First line for testing"));
        assertTrue(content.contains("Second line with Lucene"));
        assertEquals(2, reader.readLines().size());
        assertEquals("First li", reader.readPreview(8));
        assertEquals(8, reader.getWordCount());
        assertEquals(content.length(), reader.getCharCount());
        assertEquals(1, reader.countPages());
        assertNotNull(reader.getCreationDate());
        assertNull(reader.getPageAsImage(0, 72));
    }

    @Test
    void shouldReplaceMalformedUtf8InPlainTextFiles() throws Exception {
        Path txtPath = tempDir.resolve("malformed.txt");
        Files.write(txtPath, new byte[] {'v', 'a', 'l', 'i', 'd', ' ', (byte) 0xC3, '(', ' ', 't', 'e', 'x', 't'});

        assertEquals("valid \uFFFD( text", new DocReader(txtPath.toFile()).read());
    }

    @Test
    void shouldReadAndWritePlainTextWithAnExplicitCharset() throws Exception {
        Path textPath = tempDir.resolve("charset.txt");
        DocReaderTXT reader = new DocReaderTXT(textPath.toFile());
        reader.write("café");

        assertEquals("café", reader.read(StandardCharsets.UTF_8));
        assertEquals(List.of("café"), reader.readLines(StandardCharsets.UTF_8));

        reader.append(" au lait");
        assertEquals("café au lait", reader.read());
        assertEquals("café", reader.readPreview(4));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readPreview(-1));
    }

    @Test
    void shouldReadPdfFileThroughDocReader() throws Exception {
        File pdfFile = tempDir.resolve("document.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("PDF Title");
            info.setAuthor("Test Author");
            doc.setDocumentInformation(info);

            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Content extracted from a PDF file for Lucene");
                contentStream.endText();
            }
            doc.save(pdfFile);
        }

        assertTrue(DocReader.canRead(pdfFile));
        DocReader reader = new DocReader(pdfFile);
        String text = reader.read();
        assertTrue(text.contains("Content extracted from a PDF file for Lucene"));
        assertEquals(1, reader.countPages());
        assertEquals("PDF Title", reader.getTitle());
        assertEquals("Test Author", reader.getAuthor());
        assertNotNull(reader.getPageAsImage(0, 72));
    }

    @Test
    void shouldIncludePdfAnnotationsAndRejectOutOfRangePages() throws Exception {
        Path pdfPath = tempDir.resolve("annotated.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDAnnotationText annotation = new PDAnnotationText();
            annotation.setContents("review comment");
            page.getAnnotations().add(annotation);
            document.save(pdfPath.toFile());
        }

        DocReaderPDF reader = new DocReaderPDF(pdfPath.toFile());
        assertTrue(reader.readPage(0).contains("review comment"));
        assertTrue(reader.read().contains("review comment"));
        assertThrows(IllegalArgumentException.class, () -> reader.readPage(-1));
        assertThrows(IllegalArgumentException.class, () -> reader.getPageAsImage(1, 72));
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
            var sheet = workbook.createSheet("Finance");
            workbook.createSheet("Summary");
            workbook.getProperties().getCoreProperties().setCreator("Test Author");
            workbook.getProperties().getCoreProperties().setTitle("Finance workbook");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Revenue");
            row.createCell(1).setCellValue("Quarterly");
            for (int i = 1; i < 200; i++) {
                sheet.createRow(i).createCell(0).setCellValue("Row " + i);
            }
            workbook.write(out);
        }

        assertTrue(DocReader.canRead(xlsxFile));
        DocReader reader = new DocReader(xlsxFile);
        String text = reader.read();
        assertTrue(text.contains("Finance"));
        assertTrue(text.contains("Revenue"));
        assertTrue(text.contains("Quarterly"));
        assertTrue(text.contains("Row 199"));
        assertEquals(2, reader.countPages());
        assertEquals("Test Author", reader.getAuthor());
        assertEquals("Finance workbook", reader.getTitle());
    }

    @Test
    void shouldDetectExcelFormatWhenExtensionDoesNotMatch() throws Exception {
        File xlsFile = tempDir.resolve("spreadsheet.xls").toFile();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(xlsFile)) {
            var sheet = workbook.createSheet("Finance");
            sheet.createRow(0).createCell(0).setCellValue("Revenue");
            workbook.write(out);
        }

        DocReader reader = new DocReader(xlsFile);
        String text = reader.read();
        assertTrue(text.contains("Finance"));
        assertTrue(text.contains("Revenue"));
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
            box.setText("Presentation about search engine architecture");
            ppt.createSlide();
            ppt.write(out);
        }

        assertTrue(DocReader.canRead(pptxFile));
        DocReader reader = new DocReader(pptxFile);
        String text = reader.read();
        assertTrue(text.contains("Presentation about search engine architecture"));
        assertEquals(2, reader.countPages());
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
