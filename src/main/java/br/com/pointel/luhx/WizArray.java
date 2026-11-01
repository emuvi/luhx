package br.com.pointel.luhx;

public class WizArray {

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

