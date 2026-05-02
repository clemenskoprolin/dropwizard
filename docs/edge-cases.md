# J2K Edge-Case Dataset

Eight focused Java files stress-test known J2K pain points. Each has an explicit
hypothesis and is evaluated pass/fail by the `run-edge-cases` subcommand of the
`j2k-evaluator` tool.

**Source files:** [`benchmarks/edge-cases/cases/`](../benchmarks/edge-cases/cases/)
**Hypothesis table:** [`benchmarks/edge-cases/hypotheses.json`](../benchmarks/edge-cases/hypotheses.json)

## Pass/fail criteria

A case **passes** if all of the following hold after conversion:

1. The j2k binary exits with code 0.
2. The output `.kt` file is non-empty and does not start with `// CONVERSION FAILED`.
3. The output contains zero `!!` operators.

A case **fails** if any criterion is not met; the evaluator records the specific reason.

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

> _Populated by CI on first successful run. Re-run the workflow and download
> `artifacts/reports/edge-cases-result.json` for up-to-date per-case results._

| ID | Result | Failure note |
|---|---|---|
| `ec-01` | _pending_ | — |
| `ec-02` | _pending_ | — |
| `ec-03` | _pending_ | — |
| `ec-04` | _pending_ | — |
| `ec-05` | _pending_ | — |
| `ec-06` | _pending_ | — |
| `ec-07` | _pending_ | — |
| `ec-08` | _pending_ | — |

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
