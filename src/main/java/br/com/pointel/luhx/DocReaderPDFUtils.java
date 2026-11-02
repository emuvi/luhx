package br.com.pointel.luhx;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Supported PDF-related extensions and extension-based recognition helpers.
 */
public class DocReaderPDFUtils {

    /**
     * Supported PDF-related extensions, stored in lowercase without leading
     * periods. This public array is mutable; changing it changes recognition
     * results.
     */
    public static String[] PDF_EXTENSIONS = new String[]{ "pdf", "fdf", "xfdf", "pdx", "ppdf" };

    /**
     * Checks whether the file name has a supported PDF-related extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isPDFFile(File file) {
        return isPDFFile(file.getName());
    }

    /**
     * Checks whether the file name has a supported PDF-related extension.
     *
     * @param fileName the name of the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isPDFFile(String fileName) {
        return UtilsArrays.has(FilenameUtils.getExtension(fileName).toLowerCase(), PDF_EXTENSIONS);
    }
    /**
     * Returns a list view of the currently supported extensions.
     *
     * @return a fixed-size list backed by {@link #PDF_EXTENSIONS}
     */
    public static List<String> getAllExtensions() {
        return Arrays.asList(PDF_EXTENSIONS);
    }
    
}
