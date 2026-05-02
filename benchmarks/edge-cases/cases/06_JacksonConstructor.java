package benchmarks.edgecases;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edge case: Jackson @JsonCreator / @JsonProperty constructor patterns.
 *
 * Hypothesis: J2K will convert the class to a Kotlin data class or regular class
 * with primary constructor, retaining @JsonCreator and @JsonProperty. However,
 * when properties have default values or Optional fields, J2K may not produce
 * idiomatic nullable + default-parameter Kotlin; instead it may emit the Java
 * optional-checking logic in the constructor body, requiring manual cleanup.
 * The @JsonIgnore on a computed property may be placed on a backing field instead
 * of the accessor, breaking Jackson's visibility rules.
 */
public class JacksonConstructor {

    private final String name;
    private final int age;
    private final List<String> tags;
    private final String description;

    @JsonCreator
    public JacksonConstructor(
            @JsonProperty("name") String name,
            @JsonProperty("age") int age,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("description") String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.age = age;
        this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.emptyList();
        this.description = description != null ? description : "";
    }

    // Convenience constructor — J2K must choose: secondary constructor or default params
    public JacksonConstructor(String name, int age) {
        this(name, age, null, null);
    }

    @JsonProperty("name")
    public String getName() { return name; }

    @JsonProperty("age")
    public int getAge() { return age; }

    @JsonProperty("tags")
    public List<String> getTags() { return tags; }

    @JsonProperty("description")
    public Optional<String> getDescription() {
        return description.isEmpty() ? Optional.empty() : Optional.of(description);
    }

    @JsonIgnore
    public boolean isAdult() { return age >= 18; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JacksonConstructor)) return false;
        JacksonConstructor other = (JacksonConstructor) o;
        return age == other.age && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name, age); }

    @Override
    public String toString() {
        return "JacksonConstructor{name='" + name + "', age=" + age + "}";
    }
}
