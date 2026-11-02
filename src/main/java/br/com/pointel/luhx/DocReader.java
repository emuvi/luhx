package br.com.pointel.luhx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * A generalized Facade class for reading text from various document formats.
 * It automatically delegates extraction logic to the appropriate specialized reader
 * (PDF, Microsoft Office, or Plain Text) based on the file extension.
 */
public class DocReader {

    private enum Format {
        PDF,
        OFFICE,
        TEXT,
        UNSUPPORTED
    }

    /**
     * Checks if any of the underlying specialized readers can parse this file.
     *
     * @param file the file to check
     * @return true if the file format is supported
     */
    public static boolean canRead(File file) {
        return DocReaderPDF.canRead(file)
                || DocReaderMSO.canRead(file)
                || DocReaderTXT.canRead(file);
    }

    /** The underlying document file. */
    private final File file;

    private Format format() {
        if (DocReaderPDF.canRead(file)) {
            return Format.PDF;
        }
        if (DocReaderMSO.canRead(file)) {
            return Format.OFFICE;
        }
        if (DocReaderTXT.canRead(file)) {
            return Format.TEXT;
        }
        return Format.UNSUPPORTED;
    }

    /**
     * Constructs a unified document reader for the given file.
     *
     * @param file the document to read
     */
    public DocReader(File file) {
        this.file = file;
    }

    /**
     * Extracts the raw text content from the document.
     *
     * @return the extracted text, or an empty string if the format is not supported
     * @throws Exception if an error occurs during extraction
     */
    public String read() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).read();
            case OFFICE -> new DocReaderMSO(file).read();
            case TEXT -> new DocReaderTXT(file).read();
            case UNSUPPORTED -> "";
        };
    }

    /**
     * Counts the pages, sheets, or slides reported for the document format.
     * Plain-text documents report one page; unsupported formats report zero.
     *
     * @return the page, sheet, or slide count, or 0 if the format is unsupported
     * @throws Exception if an error occurs reading the document metadata
     */
    public Integer countPages() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).countPages();
            case OFFICE -> new DocReaderMSO(file).countPages();
            case TEXT -> new DocReaderTXT(file).countPages();
            case UNSUPPORTED -> 0;
        };
    }

    /**
     * Renders a specific page of the document into a high-quality image.
     * Note: Currently, only PDF files support image rendering.
     *
     * @param pageNumber the zero-based index of the page to render
     * @param dpi        the dots-per-inch resolution for the rendered image
     * @return a BufferedImage of the page, or null if the format is unsupported
     * @throws Exception if an error occurs during rendering
     */
    public BufferedImage getPageAsImage(int pageNumber, int dpi) throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getPageAsImage(pageNumber, dpi);
            case OFFICE, TEXT, UNSUPPORTED -> null;
        };
    }

    /**
     * Retrieves the underlying file object.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Retrieves the file name.
     *
     * @return the file name
     */
    public String getFileName() {
        return file.getName();
    }

    /**
     * Retrieves the file extension.
     *
     * @return the file extension
     */
    public String getExtension() {
        return FilenameUtils.getExtension(file.getName());
    }

    /**
     * Retrieves the file size in bytes.
     *
     * @return the file size
     */
    public long getFileSize() {
        return file.length();
    }

    /**
     * Retrieves the last modified timestamp of the file.
     *
     * @return the last modified time in milliseconds
     */
    public long getLastModified() {
        return file.lastModified();
    }

    /**
     * Reads all lines from the document text.
     *
     * @return a list containing all lines, or an empty list if the format is unsupported
     * @throws Exception if an error occurs
     */
    public List<String> readLines() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).readLines();
            case OFFICE -> new DocReaderMSO(file).readLines();
            case TEXT -> new DocReaderTXT(file).readLines();
            case UNSUPPORTED -> new ArrayList<>();
        };
    }

    /**
     * Reads a preview of the document contents up to the given length.
     *
     * @param length the maximum number of characters to return; must not be negative
     * @return up to {@code length} characters, or an empty string if the format is unsupported
     * @throws Exception if an error occurs reading the document
     * @throws IndexOutOfBoundsException if {@code length} is negative and the format is supported
     */
    public String readPreview(int length) throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).readPreview(length);
            case OFFICE -> new DocReaderMSO(file).readPreview(length);
            case TEXT -> new DocReaderTXT(file).readPreview(length);
            case UNSUPPORTED -> "";
        };
    }

    /**
     * Calculates the total number of words in the extracted text.
     *
     * @return the word count
     * @throws Exception if an error occurs
     */
    public int getWordCount() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getWordCount();
            case OFFICE -> new DocReaderMSO(file).getWordCount();
            case TEXT -> new DocReaderTXT(file).getWordCount();
            case UNSUPPORTED -> 0;
        };
    }

    /**
     * Calculates the total number of characters in the extracted text.
     *
     * @return the character count
     * @throws Exception if an error occurs
     */
    public int getCharCount() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getCharCount();
            case OFFICE -> new DocReaderMSO(file).getCharCount();
            case TEXT -> new DocReaderTXT(file).getCharCount();
            case UNSUPPORTED -> 0;
        };
    }

    /**
     * Retrieves the author of the document.
     *
     * @return the author, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getAuthor() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getAuthor();
            case OFFICE -> new DocReaderMSO(file).getAuthor();
            case TEXT, UNSUPPORTED -> null;
        };
    }

    /**
     * Retrieves the title of the document.
     *
     * @return the title, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getTitle() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getTitle();
            case OFFICE -> new DocReaderMSO(file).getTitle();
            case TEXT, UNSUPPORTED -> null;
        };
    }

    /**
     * Retrieves the document creation date.
     *
     * @return the creation date from document metadata or file attributes, or {@code null}
     *         if the format is unsupported
     * @throws Exception if an error occurs
     */
    public Date getCreationDate() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getCreationDate();
            case OFFICE -> new DocReaderMSO(file).getCreationDate();
            case TEXT -> new DocReaderTXT(file).getCreationDate();
            case UNSUPPORTED -> null;
        };
    }

}
