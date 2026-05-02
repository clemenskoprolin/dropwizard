package benchmarks.edgecases;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge case: wildcard capture and deep generics.
 *
 * Hypothesis: J2K will convert List<? extends T> to List<out T> and
 * List<? super T> to List<in T>, but deep nesting (e.g. List<? extends List<? super T>>)
 * may produce star projections (*) instead of preserving variance, and may
 * require unsafe casts in helper methods that write to captured wildcards.
 */
public class WildcardCapture {

    public static <T> void copy(List<? super T> dest, List<? extends T> src) {
        for (T item : src) {
            dest.add(item);
        }
    }

    public static double sumList(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) {
            total += n.doubleValue();
        }
        return total;
    }

    public static <T extends Comparable<? super T>> T findMax(List<? extends T> list) {
        if (list.isEmpty()) throw new IllegalArgumentException("empty list");
        T max = list.get(0);
        for (T item : list) {
            if (item.compareTo(max) > 0) max = item;
        }
        return max;
    }

    // Deep nesting: map from key to bounded list of bounded lists
    public static <K, V extends Number> void processNested(
            java.util.Map<K, List<? extends List<? super V>>> data) {
        for (java.util.Map.Entry<K, List<? extends List<? super V>>> entry : data.entrySet()) {
            for (List<? super V> inner : entry.getValue()) {
                // inner is effectively a write-only list — J2K variance must be preserved
                System.out.println(inner.size());
            }
        }
    }

    // Wildcard that requires capture helper pattern
    public static void reverse(List<?> list) {
        reverseHelper(list);
    }

    private static <T> void reverseHelper(List<T> list) {
        int n = list.size();
        for (int i = 0; i < n / 2; i++) {
            T tmp = list.get(i);
            list.set(i, list.get(n - 1 - i));
            list.set(n - 1 - i, tmp);
        }
    }
}
