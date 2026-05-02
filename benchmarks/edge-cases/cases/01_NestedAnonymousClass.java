package benchmarks.edgecases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Edge case: nested anonymous classes referencing enclosing-class state.
 *
 * Hypothesis: J2K converts Runnable/Comparator anonymous classes to lambdas or
 * object expressions, but may emit !! when the enclosing field is accessed from
 * the anonymous body because nullability of the captured reference is unknown.
 */
public class NestedAnonymousClass {

    private final String prefix;
    private final List<String> items = new ArrayList<>();

    public NestedAnonymousClass(String prefix) {
        this.prefix = prefix;
    }

    public void addItem(String item) {
        items.add(item);
    }

    public Runnable buildPrinter() {
        return new Runnable() {
            @Override
            public void run() {
                // accesses enclosing field — Kotlin must capture via outer-this
                for (String item : items) {
                    System.out.println(prefix + item);
                }
            }
        };
    }

    public Comparator<String> buildComparator() {
        return new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.startsWith(prefix)
                        ? -1
                        : b.startsWith(prefix) ? 1 : a.compareTo(b);
            }
        };
    }

    // Anonymous class that itself contains an anonymous class
    public Runnable buildNestedPrinter() {
        return new Runnable() {
            @Override
            public void run() {
                Runnable inner = new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("inner: " + prefix);
                    }
                };
                inner.run();
            }
        };
    }
}
