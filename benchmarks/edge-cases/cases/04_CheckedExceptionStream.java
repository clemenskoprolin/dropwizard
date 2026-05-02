package benchmarks.edgecases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Edge case: checked exceptions inside stream/lambda flows.
 *
 * Hypothesis: Java stream lambdas that throw checked exceptions require wrapper
 * helpers (sneaky-throw or UncheckedIOException wrapping). J2K converts the lambda
 * body but may not lift the try-catch out of the lambda, producing a try-catch
 * inside a Kotlin lambda where a plain IOException propagation would be idiomatic.
 * The output is expected to be syntactically valid but not idiomatic Kotlin.
 */
public class CheckedExceptionStream {

    @FunctionalInterface
    interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public static <T, R> java.util.function.Function<T, R> wrap(ThrowingFunction<T, R> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public List<String> readAllLines(List<Path> files) {
        return files.stream()
                .flatMap(path -> {
                    try {
                        return Files.lines(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    public List<Long> getFileSizes(List<Path> files) {
        return files.stream()
                .map(wrap(path -> Files.size(path)))
                .collect(Collectors.toList());
    }

    public void processFiles(Stream<Path> paths) throws IOException {
        try (Stream<Path> s = paths) {
            s.filter(p -> {
                try {
                    return Files.isReadable(p) && Files.size(p) > 0;
                } catch (IOException e) {
                    return false;
                }
            }).forEach(p -> {
                try {
                    System.out.println(Files.readString(p));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
