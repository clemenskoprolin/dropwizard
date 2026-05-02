# J2K Edge-Case Dataset

Eight focused Java files stress-test known J2K pain points. Each has an explicit
hypothesis and is evaluated pass/fail by the `run-edge-cases` subcommand of the
`j2k-evaluator` tool.

**Source files:** [`benchmarks/edge-cases/cases/`](../benchmarks/edge-cases/cases/)
**Hypothesis table:** [`benchmarks/edge-cases/hypotheses.json`](../benchmarks/edge-cases/hypotheses.json)

## Pass/fail criteria

A case **passes** if all of the following hold after conversion:

1. The headless IntelliJ J2K runner exits with code 0.
2. The output `.kt` file is non-empty and does not start with `// CONVERSION FAILED`.
3. The output contains zero `!!` operators.

A case **fails** if any criterion is not met; the evaluator records the specific reason.
Unsafe casts are recorded as quality notes but do not currently fail a case by
themselves.

## Hypothesis table

| ID | Category | File | Hypothesis summary |
|---|---|---|---|
| `ec-01` | nested-anonymous-classes | `01_NestedAnonymousClass.java` | Anonymous classes converted to lambdas/object-expressions, but `!!` emitted on enclosing field capture; doubly-nested anonymous classes not folded to lambdas |
| `ec-02` | wildcard-generics | `02_WildcardCapture.java` | `List<? extends T>` → `List<out T>` works for one level; deep nesting collapses to `List<*>`; capture-helper method survives as internal fun |
| `ec-03` | sam-overload | `03_SamOverload.java` | Call-site explicit `(Runnable)` casts dropped, causing overload ambiguity; J2K does not re-insert SAM constructor syntax |
| `ec-04` | checked-exception-streams | `04_CheckedExceptionStream.java` | `try`-`catch` retained inside stream lambda bodies; `ThrowingFunction` becomes `fun interface`; redundant `RuntimeException` check not removed |
| `ec-05` | composed-annotations | `05_SpringMvcAnnotation.java` | `@JvmRepeatable` not added for `@Repeatable`; composed-annotation defaults dropped; `@Target` maps incorrectly to `AnnotationTarget` |
| `ec-06` | jackson-constructor | `06_JacksonConstructor.java` | Class not converted to `data class`; `Optional<String>` not simplified to `String?`; `@JsonIgnore` misplaced on field |
| `ec-07` | inner-class-shadowing | `07_InnerClassShadowing.java` | Single-level `Outer.this.field` → `this@Outer.field` works; two-level `Inner.this.field` in `InnerInner` produces `!!` or wrong reference |
| `ec-08` | builder-fluent | `08_BuilderFluent.java` | `return this` pattern recognised in flat `Builder`; covariant `SecureBuilder.tls()` override produces type mismatch after conversion |

## Results

Current headless-runner result: **5 / 8 cases passed**. All eight Java files
converted successfully; the three failing cases failed only because generated
Kotlin contained `!!`.

| ID | Result | Unsafe calls | Unsafe casts | Failure note |
|---|---|---:|---:|---|
| `ec-01` | fail | 1 | 0 | 1 unsafe-call(s) (`!!`) in output |
| `ec-02` | fail | 1 | 0 | 1 unsafe-call(s) (`!!`) in output |
| `ec-03` | pass | 0 | 3 | 3 unsafe cast(s) (`as T`) in output |
| `ec-04` | pass | 0 | 0 | — |
| `ec-05` | pass | 0 | 1 | 1 unsafe cast(s) (`as T`) in output |
| `ec-06` | pass | 0 | 1 | 1 unsafe cast(s) (`as T`) in output |
| `ec-07` | fail | 1 | 0 | 1 unsafe-call(s) (`!!`) in output |
| `ec-08` | pass | 0 | 0 | — |

The 2026-05-02 Actions artifact contained seven rows because the initial
hypothesis parser treated braces inside the `ec-03` hypothesis text as JSON
object delimiters. The parser now scans strings and escapes correctly, and the
local validation run covers all eight cases.

## Proposed remedies (v1 notes)

The following remedies are documented follow-ups, not implemented patches.

- **`ec-03` (SAM overload):** At the Kotlin call site, replace the dropped cast with an
  explicit SAM constructor: `execute(Runnable { ... })` instead of `execute { ... }`.
- **`ec-05` (composed annotations):** Add `@JvmRepeatable(ContainerAnnotation::class)`
  alongside `@Repeatable`; ensure `@Target` lists `AnnotationTarget.FUNCTION` and
  `AnnotationTarget.CLASS` separately rather than relying on Java `ElementType` mapping.
- **`ec-06` (Jackson constructor):** Manually convert to `data class` after J2K; replace
  `Optional<String>` return type with `String?`; move `@JsonIgnore` from backing field
  to the `get:` use-site target: `@get:JsonIgnore val isAdult: Boolean`.

---

*See [docs/summary.md](summary.md) for the primary and secondary benchmark findings.*
