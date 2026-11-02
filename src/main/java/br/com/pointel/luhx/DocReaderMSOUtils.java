package br.com.pointel.luhx;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Supported Microsoft Office extensions and extension-based recognition helpers.
 */
public class DocReaderMSOUtils {

    /**
     * Supported PowerPoint extensions, lowercase and without leading periods.
     * This public array is mutable; changing it changes recognition results.
     */
    public static String[] MSPOWERPOINT_EXTENSIONS = new String[]{ "ppt", "pptx" };

    /**
     * Supported Excel extensions, lowercase and without leading periods.
     * This public array is mutable; changing it changes recognition results.
     */
    public static String[] MSEXCEL_EXTENSIONS = new String[]{ "xls", "xlsx" };

    /**
     * Supported Word extensions, lowercase and without leading periods.
     * This public array is mutable; changing it changes recognition results.
     */
    public static String[] MSWORD_EXTENSIONS = new String[]{ "doc", "docx" };

    /**
     * Checks whether the file name has a supported Word extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isMSWordFile(File file) {
        return isMSWordFile(file.getName());
    }

    /**
     * Checks whether the file name has a supported Word extension.
     *
     * @param fileName the name of the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isMSWordFile(String fileName) {
        return UtilsArrays.has(FilenameUtils.getExtension(fileName).toLowerCase(), MSWORD_EXTENSIONS);
    }

    /**
     * Checks whether the file name has a supported PowerPoint extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isMSPowerPointFile(File file) {
        return isMSPowerPointFile(file.getName());
    }

    /**
     * Checks whether the file name has a supported PowerPoint extension.
     *
     * @param fileName the name of the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isMSPowerPointFile(String fileName) {
        return UtilsArrays.has(FilenameUtils.getExtension(fileName).toLowerCase(), MSPOWERPOINT_EXTENSIONS);
    }

    /**
     * Checks whether the file name has a supported Excel extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isMSExcelFile(File file) {
        return isMSExcelFile(file.getName());
    }

    /**
     * Checks whether the file name has a supported Excel extension.
     *
     * @param fileName the name of the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean isMSExcelFile(String fileName) {
        return UtilsArrays.has(FilenameUtils.getExtension(fileName).toLowerCase(), MSEXCEL_EXTENSIONS);
    }
    /**
     * Returns a new list containing the currently supported Word, Excel, and
     * PowerPoint extensions in that order.
     *
     * @return a mutable list of supported extensions
     */
    public static List<String> getAllExtensions() {
        var list = new ArrayList<String>();
        list.addAll(Arrays.asList(MSWORD_EXTENSIONS));
        list.addAll(Arrays.asList(MSEXCEL_EXTENSIONS));
        list.addAll(Arrays.asList(MSPOWERPOINT_EXTENSIONS));
        return list;
    }
    
}
