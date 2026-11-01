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
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).read();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).read();
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).read();
        } else {
            return "";
        }
    }

    /**
     * Counts the total number of pages in the document.
     * Note: Currently, only PDF files support physical page counting.
     *
     * @return the number of pages, or 0 if unsupported
     * @throws Exception if an error occurs reading the document metadata
     */
    public Integer countPages() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).countPages();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).countPages();
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).countPages();
        } else {
            return 0;
        }
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
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).getPageAsImage(pageNumber, dpi);
        } else if (DocReaderMSO.canRead(file)) {
            return null;
        } else if (DocReaderTXT.canRead(file)) {
            return null;
        } else {
            return null;
        }
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
     * @return a list containing all lines
     * @throws Exception if an error occurs
     */
    public List<String> readLines() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).readLines();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).readLines();
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).readLines();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Reads a preview of the document contents up to the given length.
     *
     * @param length the maximum number of characters to return
     * @return a substring of the text
     * @throws Exception if an error occurs
     */
    public String readPreview(int length) throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).readPreview(length);
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).readPreview(length);
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).readPreview(length);
        } else {
            return "";
        }
    }

    /**
     * Calculates the total number of words in the extracted text.
     *
     * @return the word count
     * @throws Exception if an error occurs
     */
    public int getWordCount() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).getWordCount();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).getWordCount();
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).getWordCount();
        } else {
            return 0;
        }
    }

    /**
     * Calculates the total number of characters in the extracted text.
     *
     * @return the character count
     * @throws Exception if an error occurs
     */
    public int getCharCount() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).getCharCount();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).getCharCount();
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).getCharCount();
        } else {
            return 0;
        }
    }

    /**
     * Retrieves the author of the document.
     *
     * @return the author, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getAuthor() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).getAuthor();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).getAuthor();
        } else {
            return null;
        }
    }

    /**
     * Retrieves the title of the document.
     *
     * @return the title, or null if unavailable
     * @throws Exception if an error occurs
     */
    public String getTitle() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).getTitle();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).getTitle();
        } else {
            return null;
        }
    }

    /**
     * Retrieves the document creation date.
     *
     * @return the creation date, or null if unavailable
     * @throws Exception if an error occurs
     */
    public Date getCreationDate() throws Exception {
        if (DocReaderPDF.canRead(file)) {
            return new DocReaderPDF(file).getCreationDate();
        } else if (DocReaderMSO.canRead(file)) {
            return new DocReaderMSO(file).getCreationDate();
        } else if (DocReaderTXT.canRead(file)) {
            return new DocReaderTXT(file).getCreationDate();
        } else {
            return null;
        }
    }

}
