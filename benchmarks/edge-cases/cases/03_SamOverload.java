package benchmarks.edgecases;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Edge case: overloaded SAM call sites.
 *
 * Hypothesis: Kotlin's SAM conversion for Java functional interfaces works at
 * call sites, but when multiple overloads accept different SAM types with compatible
 * lambda signatures, J2K may lose the explicit overload disambiguation and produce
 * ambiguous or wrong call sites that need manual @JvmOverloads or explicit casts.
 */
public class SamOverload {

    // Two overloads with the same arity but different SAM types
    public static void execute(Runnable task) {
        task.run();
    }

    public static void execute(Supplier<String> task) {
        System.out.println(task.get());
    }

    public static <T> void execute(Consumer<T> consumer, T value) {
        consumer.accept(value);
    }

    public static <T> void execute(Function<T, T> transform, T value) {
        System.out.println(transform.apply(value));
    }

    // Predicate overloads — J2K may not distinguish between negate() return type
    public static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }

    public static <T> Predicate<T> not(java.util.function.BiPredicate<T, T> biPredicate, T fixed) {
        return t -> !biPredicate.test(t, fixed);
    }

    // Call site where the overload resolution is ambiguous after SAM folding
    public static void demo() {
        execute((Runnable) () -> System.out.println("runnable"));     // explicit cast needed in Java too
        execute((Supplier<String>) () -> "supplier result");
        execute(System.out::println, "hello");
        execute((Function<String, String>) String::toUpperCase, "world");
    }
}
