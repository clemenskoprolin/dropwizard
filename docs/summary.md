# J2K Evaluation Summary

This document describes the Java-to-Kotlin evaluation pipeline added to this
`dropwizard/dropwizard` fork and records the findings from the first CI run.

## Repositories used

| Role | Repository | Status |
|---|---|---|
| Primary conversion target | `dropwizard/dropwizard` — `dropwizard-example` module | This fork |
| Secondary benchmark (Java) | `spring-projects/spring-petclinic` | Cloned at runtime |
| Secondary benchmark (Kotlin reference) | `spring-petclinic/spring-petclinic-kotlin` | Cloned at runtime |

The upstream `dropwizard/dropwizard` commit pinned for the first evaluation run:
**`d2dea7c29017a254a46b45b190027f20cad0d24d`** (branch `release/5.0.x`, 2026-05-02).

## Converter

**kotlinc 2.1.20** — downloaded by the CI workflow from the JetBrains GitHub releases
at `https://github.com/JetBrains/kotlin/releases`. The `j2k` binary is extracted from
the distribution zip and invoked directly on each Java source file.

## What the pipeline does

1. **Baseline validation** — the upstream `dropwizard-example` module is built and tested
   with `./mvnw -pl dropwizard-example -am test` to confirm it is unmodified and green.

2. **Conversion** (`convert-primary`) — the `j2k` binary from the downloaded kotlinc
   distribution converts every `.java` file under `dropwizard-example/src`. Output `.kt`
   files are written to `artifacts/converted/dropwizard-example/` (gitignored).

3. **Evaluation** (`evaluate-primary`) — the Kotlin evaluator scores the converted sources
   against the original Java sources using structural heuristics (class/interface/enum parity,
   public method count, annotation retention) and quality heuristics (`!!` density, unsafe
   casts, TODO markers, data-class generation).

4. **Secondary benchmark** (`compare-petclinic`) — `spring-petclinic` Java sources are
   converted with the same j2k binary and the result is compared against the official
   `spring-petclinic-kotlin` repository using structural signature matching (packages,
   class names, annotations).

5. **Edge-case dataset** (`run-edge-cases`) — 8 focused Java files in
   `benchmarks/edge-cases/cases/` are converted individually. Each file has an explicit
   hypothesis in `benchmarks/edge-cases/hypotheses.json`; the evaluator marks each case
   pass or fail and records which unsafe patterns appeared.

## Primary benchmark findings

> _Results below are from the first successful CI run. Re-run the workflow and download
> the `primary-result.json` artifact for up-to-date numbers._

**`dropwizard-example` module: 33 Java files (23 main, 10 test)**

| Metric | Value |
|---|---|
| Converter variant | `new` (kotlinc 2.1.20) |
| Converted successfully | _pending first run_ |
| Conversion failures | _pending first run_ |
| Java LOC (non-blank) | _pending first run_ |
| Kotlin LOC (non-blank) | _pending first run_ |
| Unsafe-call operators (`!!`) | _pending first run_ |
| Unsafe casts (`as T`) | _pending first run_ |
| Data classes generated | _pending first run_ |

### Key observations (expected based on source analysis)

- **`@Entity` / `@Column` annotations** on `Person.java` and `User.java` are expected to
  be retained verbatim as Kotlin annotations.
- **`Template.java`** contains a format-string method with Optional return; the Optional
  is unlikely to be simplified to `String?` by J2K.
- **`HelloWorldResource.java`** uses JAX-RS annotations that J2K retains correctly.
- **`PersonDAO.java`** (Hibernate DAO) is a prime candidate for `!!` emission since
  HQL result types are untyped at compile time.
- **Test classes** referencing `DropwizardAppRule` (generic type parameter) may produce
  unsafe casts for the rule's `.getApplication()` call.

## Secondary benchmark findings (Petclinic)

> _Populated by CI on first successful run. See `artifacts/reports/petclinic-result.json`._

| Metric | Converted (J2K) | Official Kotlin | Match % |
|---|---|---|---|
| Packages | _pending_ | _pending_ | _pending_ |
| Classes / interfaces | _pending_ | _pending_ | _pending_ |
| Annotations | _pending_ | _pending_ | _pending_ |

## Edge-case dataset findings

See [docs/edge-cases.md](edge-cases.md) for per-case hypothesis and pass/fail analysis.

## Reproducing locally

See the project [README](../README.md) — section **"J2K Evaluation — Local Reproduction"**.

# Usage of AI
I used Claude for debugging and generating some test/edge cases. The main implemenation was done by myself.
