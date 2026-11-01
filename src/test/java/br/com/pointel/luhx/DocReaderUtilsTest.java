package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocReaderUtilsTest {

    @Test
    void shouldIdentifyPlainTextExtensions() {
        assertTrue(DocReaderTXTUtils.isTXTFile("notes.txt"));
        assertTrue(DocReaderTXTUtils.isTXTFile("README.MD"));
        assertTrue(DocReaderTXTUtils.isTXTFile(new File("App.java")));
        assertTrue(DocReaderTXTUtils.isTXTFile(new File("index.html")));
        assertTrue(DocReaderTXTUtils.isTXTFile(new File("script.py")));
        assertTrue(DocReaderTXTUtils.isTXTFile(new File("server.log")));

        assertFalse(DocReaderTXTUtils.isTXTFile("document.pdf"));
        assertFalse(DocReaderTXTUtils.isTXTFile(new File("image.png")));
        assertFalse(DocReaderTXTUtils.isTXTFile("no_extension"));

        List<String> extensions = DocReaderTXTUtils.getAllExtensions();
        assertTrue(extensions.contains("txt"));
        assertTrue(extensions.contains("md"));
        assertTrue(extensions.contains("java"));
    }

    @Test
    void shouldIdentifyPdfExtensions() {
        assertTrue(DocReaderPDFUtils.isPDFFile("report.pdf"));
        assertTrue(DocReaderPDFUtils.isPDFFile("SCAN.PDF"));
        assertTrue(DocReaderPDFUtils.isPDFFile(new File("form.fdf")));
        assertTrue(DocReaderPDFUtils.isPDFFile(new File("data.xfdf")));

        assertFalse(DocReaderPDFUtils.isPDFFile("report.docx"));
        assertFalse(DocReaderPDFUtils.isPDFFile(new File("notes.txt")));

        List<String> extensions = DocReaderPDFUtils.getAllExtensions();
        assertTrue(extensions.contains("pdf"));
    }

    @Test
    void shouldIdentifyMicrosoftOfficeExtensions() {
        assertTrue(DocReaderMSOUtils.isMSWordFile("contract.doc"));
        assertTrue(DocReaderMSOUtils.isMSWordFile(new File("contract.DOCX")));
        assertFalse(DocReaderMSOUtils.isMSWordFile("spreadsheet.xlsx"));

        assertTrue(DocReaderMSOUtils.isMSExcelFile("budget.xls"));
        assertTrue(DocReaderMSOUtils.isMSExcelFile(new File("budget.XLSX")));
        assertFalse(DocReaderMSOUtils.isMSExcelFile("slides.pptx"));

        assertTrue(DocReaderMSOUtils.isMSPowerPointFile("deck.ppt"));
        assertTrue(DocReaderMSOUtils.isMSPowerPointFile(new File("deck.PPTX")));
        assertFalse(DocReaderMSOUtils.isMSPowerPointFile("contract.docx"));

        List<String> allExtensions = DocReaderMSOUtils.getAllExtensions();
        assertTrue(allExtensions.containsAll(List.of("doc", "docx", "xls", "xlsx", "ppt", "pptx")));
    }

}

