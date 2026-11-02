package br.com.pointel.luhx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * A specialized document reader that extracts text, page counts, and images from PDFs.
 * It leverages the Apache PDFBox library to perform deep text extraction, including 
 * parsing and formatting embedded PDF annotations/comments.
 */
public class DocReaderPDF {
    
    /**
     * Checks if the file is a supported PDF format.
     *
     * @param file the file to check
     * @return true if the file is a PDF
     */
    public static boolean canRead(File file) {
        return DocReaderPDFUtils.isPDFFile(file);
    }
    
    /** The underlying PDF file. */
    private final File file;

    /**
     * Constructs a reader for a PDF document.
     *
     * @param file the file to read
     */
    public DocReaderPDF(File file) {
        this.file = file;
    }

    /**
     * Extracts the text from all pages in the document.
     * Pages are separated by "---" markers in the resulting string.
     *
     * @return the combined extracted text
     * @throws Exception if an error occurs reading the PDF
     */
    public String read() throws Exception {
        // Temp-file buffering keeps large PDFs out of the heap during bulk indexing.
        try (var doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
            var count = doc.getNumberOfPages();
            var builder = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    builder.append(System.lineSeparator());
                    builder.append("---");
                    builder.append(System.lineSeparator());
                    builder.append(System.lineSeparator());
                }
                builder.append(readPage(doc, i));
            }
            return builder.toString();
        }
    }

    /**
     * Obtains the total number of physical pages in the PDF.
     *
     * @return the total page count
     * @throws Exception if an error occurs reading the PDF metadata
     */
    public Integer countPages() throws Exception {
        try (var doc = PDDocument.load(file)) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Extracts the text content from a single specific page.
     *
     * @param pageNumber the zero-based index of the page
     * @return the extracted text of the page
     * @throws Exception if the page is out of bounds or reading fails
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document's page range
     */
    public String readPage(int pageNumber) throws Exception {
        try (var doc = PDDocument.load(file)) {
            return readPage(doc, pageNumber);
        }
    }

    /**
     * Core static logic for parsing text and annotations from a loaded PDF page.
     * Appends any found PDF annotations/comments to the bottom of the page text.
     *
     * @param doc        the loaded PDDocument object
     * @param pageNumber the zero-based index of the page
     * @return the text and annotations combined
     * @throws IOException if an error occurs extracting the data
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document's page range
     */
    public static String readPage(PDDocument doc, int pageNumber) throws IOException {
        int numPages = doc.getNumberOfPages();
        if (pageNumber < 0 || pageNumber >= numPages) {
            throw new IllegalArgumentException("Page number " + pageNumber + " is out of bounds. The PDF has " + numPages + " pages.");
        }
        var stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber + 1);
        stripper.setEndPage(pageNumber + 1);
        String text = stripper.getText(doc).trim();

        var page = doc.getPage(pageNumber);
        var annotations = page.getAnnotations();
        StringBuilder comments = new StringBuilder();
        for (var annotation : annotations) {
            var contents = annotation.getContents();
            if (contents != null && !contents.isBlank()) {
                if (comments.length() > 0) {
                    comments.append(System.lineSeparator());
                    comments.append("--:");
                    comments.append(System.lineSeparator());
                    comments.append(System.lineSeparator());
                }
                comments.append(contents.trim());
            }
        }

        if (comments.length() > 0) {
            text = text
                            + System.lineSeparator()
                            + "--\\" 
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + comments.toString();
        }
        return text;
    }

    /**
     * Renders a specific page into a standard BufferedImage raster.
     *
     * @param pageNumber the zero-based index of the page
     * @param dpi        the resolution (Dots Per Inch) to render the image at
     * @return the rendered image
     * @throws Exception if an error occurs reading the PDF or rendering the page
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document's page range
     */
    public BufferedImage getPageAsImage(int pageNumber, int dpi) throws Exception {
        try (var doc = PDDocument.load(file)) {
            if (pageNumber < 0 || pageNumber >= doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Page number " + pageNumber + " is out of bounds. The PDF has " + doc.getNumberOfPages() + " pages.");
            }
            PDFRenderer pdfRenderer = new PDFRenderer(doc);
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pageNumber, dpi);
            return bufferedImage;
        }
    }
    /**
     * Reads all lines from the document text.
     *
     * @return a list containing all lines
     * @throws Exception if an error occurs
     */
    public List<String> readLines() throws Exception {
        return Arrays.asList(read().split("\\r?\\n"));
    }

    /**
     * Reads a preview of the document contents up to the given length.
     *
     * @param length the maximum number of characters to return; must not be negative
     * @return up to {@code length} characters
     * @throws Exception if an error occurs
     * @throws IndexOutOfBoundsException if {@code length} is negative
     */
    public String readPreview(int length) throws Exception {
        String full = read();
        if (full.length() <= length) {
            return full;
        }
        return full.substring(0, length);
    }

    /**
     * Calculates the total number of words in the extracted text.
     *
     * @return the word count
     * @throws Exception if an error occurs
     */
    public int getWordCount() throws Exception {
        String full = read();
        if (full == null || full.trim().isEmpty()) return 0;
        return full.trim().split("\\s+").length;
    }

    /**
     * Calculates the total number of characters in the extracted text.
     *
     * @return the character count
     * @throws Exception if an error occurs
     */
    public int getCharCount() throws Exception {
        return read().length();
    }

    /**
     * Retrieves the author of the document.
     *
     * @return the author, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getAuthor() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getAuthor() : null;
        }
    }

    /**
     * Retrieves the title of the document.
     *
     * @return the title, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getTitle() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getTitle() : null;
        }
    }

    /**
     * Retrieves the subject of the document.
     *
     * @return the subject, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getSubject() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getSubject() : null;
        }
    }

    /**
     * Retrieves the creator of the document.
     *
     * @return the creator, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getCreator() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getCreator() : null;
        }
    }

    /**
     * Retrieves the document creation date.
     *
     * @return the creation date from PDF metadata, or the file creation date if metadata is unavailable
     * @throws Exception if an error occurs
     */
    public Date getCreationDate() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            if (info != null && info.getCreationDate() != null) {
                return info.getCreationDate().getTime();
            }
        }
        var attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return new Date(attr.creationTime().toMillis());
    }

    /**
     * Extracts images directly referenced by a page's image XObjects.
     *
     * @param pageNumber the zero-based index of the page
     * @return the images found in the page resources
     * @throws Exception if an error occurs reading the PDF or extracting an image
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document's page range
     */
    public List<BufferedImage> getImages(int pageNumber) throws Exception {
        var images = new ArrayList<BufferedImage>();
        try (var doc = PDDocument.load(file)) {
            if (pageNumber < 0 || pageNumber >= doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Page number " + pageNumber + " is out of bounds.");
            }
            var page = doc.getPage(pageNumber);
            var resources = page.getResources();
            for (COSName c : resources.getXObjectNames()) {
                var o = resources.getXObject(c);
                if (o instanceof PDImageXObject) {
                    images.add(((PDImageXObject) o).getImage());
                }
            }
        }
        return images;
    }

}
