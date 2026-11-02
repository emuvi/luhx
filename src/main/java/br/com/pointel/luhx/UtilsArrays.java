package br.com.pointel.luhx;

/**
 * Small, null-tolerant utility operations for arrays.
 */
public class UtilsArrays {

    /**
     * Checks whether an array contains a value equal to the target.
     *
     * @param target value to find
     * @param array values to search
     * @param <T> type of the target and array values
     * @return {@code true} if an equal value is present; {@code false} if the
     *         target or array is null or no value matches
     */
    @SafeVarargs
    public static <T> boolean has(T target, T... array) {
        if (target == null || array == null) {
            return false;
        }
        for (T item : array) {
            if (target.equals(item)) {
                return true;
            }
        }
        return false;
    }

}
