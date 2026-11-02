package br.com.pointel.luhx;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Supported plain-text and source-code file extensions and extension-based
 * recognition helpers.
 */
public class DocReaderTXTUtils {

    /**
     * Supported plain-text extensions, stored in lowercase without leading
     * periods. This public array is mutable; changing it changes recognition
     * results.
     */
    public static String[] TXT_EXTENSIONS = new String[]{ "txt", "md", "htm", "html", "php", "phtml", "java", "py", "log" };

    /**
     * Checks whether the file name has a supported plain-text extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isTXTFile(File file) {
        return isTXTFile(file.getName());
    }

    /**
     * Checks whether the file name has a supported plain-text extension.
     *
     * @param fileName the name of the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isTXTFile(String fileName) {
        return UtilsArrays.has(FilenameUtils.getExtension(fileName).toLowerCase(), TXT_EXTENSIONS);
    }
    /**
     * Returns a list view of the currently supported extensions.
     *
     * @return a fixed-size list backed by {@link #TXT_EXTENSIONS}
     */
    public static List<String> getAllExtensions() {
        return Arrays.asList(TXT_EXTENSIONS);
    }
    
}
