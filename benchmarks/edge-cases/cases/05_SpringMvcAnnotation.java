package benchmarks.edgecases;

import java.lang.annotation.*;

/**
 * Edge case: Spring MVC composed annotations.
 *
 * Hypothesis: J2K will copy @interface declarations and their meta-annotations
 * verbatim as Kotlin annotation classes, but the composed-annotation pattern
 * (an @interface annotated with another @interface) may lose the AliasFor
 * semantics or produce a Kotlin annotation class without the @JvmRepeatable
 * counterpart. Also, @Target with multiple ElementType values converts to a
 * vararg AnnotationTarget array, and J2K may miss the Kotlin-side @Target mapping.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpringMvcAnnotation {

    /** HTTP method string, e.g. "GET". */
    String method() default "GET";

    /** URL path(s) this handler maps to. */
    String[] path() default {};

    /** Content-type this handler produces. */
    String produces() default "application/json";
}

// Separate file would normally hold these — inlined here for self-containment

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringMvcAnnotation(method = "GET")
@interface GetEndpoint {

    String[] value() default {};

    String produces() default "application/json";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringMvcAnnotation(method = "POST")
@interface PostEndpoint {

    String[] value() default {};

    String consumes() default "application/json";
}

@Repeatable(SpringMvcAnnotation.Container.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface RepeatableController {

    String name();

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        RepeatableController[] value();
    }
}
