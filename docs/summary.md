# J2K Evaluation Summary

This document describes the Java-to-Kotlin evaluation pipeline added to this
`dropwizard/dropwizard` fork and records the findings from the first successful
headless IntelliJ J2K run.

## Repositories used

| Role | Repository | Status |
|---|---|---|
| Primary conversion target | `dropwizard/dropwizard` — `dropwizard-example` module | This fork |
| Secondary benchmark (Java) | `spring-projects/spring-petclinic` | Cloned at runtime |
| Secondary benchmark (Kotlin reference) | `spring-petclinic/spring-petclinic-kotlin` | Cloned at runtime |

Reference run:
[GitHub Actions run `25262774657`](https://github.com/clemenskoprolin/dropwizard/actions/runs/25262774657)
on commit **`8465a0ff5ef8e57c714d14ff0af733ce0b4aba54`**, completed on
2026-05-02.

## Converter

**Headless IntelliJ J2K runner** — implemented in
[`tools/headless-j2k-runner/`](../tools/headless-j2k-runner/).

Kotlin 2.1.20 does not provide a standalone `j2k` executable in the compiler
distribution, so CI builds a small IntelliJ Platform plugin instead. The plugin
registers the `j2k-headless` `ApplicationStarter`, opens a lightweight headless
IDEA project, attaches the source root and Maven classpath, and invokes the
bundled Kotlin J2K API (`J2kConverterExtension.Kind.K1_NEW`) directly.

Current runner pins:

| Component | Version / setting |
|---|---|
| IntelliJ IDEA Community | `2024.3.5` |
| Kotlin Gradle plugin | `2.1.20` |
| IntelliJ Platform Gradle plugin | `2.3.0` |
| CI Java runtime | Temurin JDK 21 |
| CI Gradle runtime | Gradle 8.13 |
| Conversion report variant | `headless-j2k` |

The evaluator still labels primary structural reports with `2.1.20`; that value
identifies the Kotlin/J2K evaluation target, while `conversion-result.json`
records the actual runner variant as `headless-j2k`.

## What the pipeline does

1. **Baseline validation** — the upstream `dropwizard-example` module is built and tested
   with `./mvnw -pl dropwizard-example -am test` to confirm it is unmodified and green.

2. **Runner build** — CI builds the IntelliJ headless plugin with
   `gradle -p tools/headless-j2k-runner buildPlugin --no-daemon`.

3. **Conversion** (`convert-primary`) — the evaluator invokes the runner's
   `runJ2kHeadless` Gradle task against every `.java` file under
   `dropwizard-example/src`. Output `.kt` files are written to
   `artifacts/converted/dropwizard-example/` (gitignored).

4. **Evaluation** (`evaluate-primary`) — the Kotlin evaluator scores the converted sources
   against the original Java sources using structural heuristics (class/interface/enum parity,
   public method count, annotation retention) and quality heuristics (`!!` density, unsafe
   casts, TODO markers, data-class generation).

5. **Secondary benchmark** (`compare-petclinic`) — `spring-petclinic` Java sources are
   converted with the same headless runner and the result is compared against the official
   `spring-petclinic-kotlin` repository using structural signature matching (packages,
   class names, annotations).

6. **Edge-case dataset** (`run-edge-cases`) — 8 focused Java files in
   `benchmarks/edge-cases/cases/` are converted individually. Each file has an explicit
   hypothesis in `benchmarks/edge-cases/hypotheses.json`; the evaluator marks each case
   pass or fail and records which unsafe patterns appeared.

## Primary benchmark findings

> Results are from the successful full workflow run linked above. Re-run the
> workflow and download the `primary-result.json` artifact for fresh numbers.

**`dropwizard-example` module: 33 Java files (23 main, 10 test)**

| Metric | Value |
|---|---|
| Converter variant | `headless-j2k` |
| Converted successfully | 33 / 33 |
| Conversion failures | 0 |
| Java LOC (non-blank) | 1,424 |
| Kotlin LOC (non-blank) | 1,379 |
| Kotlin parse successes | 33 / 33 |
| Unsafe-call operators (`!!`) | 14 |
| Unsafe casts (`as T`) | 1 |
| TODO comments | 0 |
| Data classes generated | 0 |
| Kotlin `object` declarations | 13 |

Structural counts:

| Metric | Java | Kotlin |
|---|---:|---:|
| Classes | 34 | 23 |
| Interfaces | 0 | 0 |
| Enums | 1 | 1 |
| Annotations | 190 | 177 |
| Public methods | 60 | 85 |

Primary hotspots:

| Converted file | `!!` | Unsafe casts |
|---|---:|---:|
| `test/java/com/example/helloworld/db/PersonDAOTest.kt` | 7 | 0 |
| `test/java/com/example/helloworld/db/PersonDAOIntegrationTest.kt` | 7 | 0 |
| `main/java/com/example/helloworld/core/Person.kt` | 0 | 1 |

Key observations:

- The conversion is real output, not stub placeholders: CI verified zero files
  beginning with `// STUB`.
- The primary quality issue is concentrated in DAO tests, where generated Kotlin
  uses `!!` around values obtained from Dropwizard/JDBI test infrastructure.
- J2K preserved enough syntax for all 33 converted files to parse, but it did
  not infer Dropwizard model classes as Kotlin `data class` declarations.

## Secondary benchmark findings (Petclinic)

The secondary benchmark converts the Java Spring Petclinic application and
compares structural signatures against the official Kotlin Petclinic reference.

| Metric | Converted (J2K) | Official Kotlin | Match % |
|---|---|---|---|
| Packages | 5 | 6 | 83.3% |
| Classes / interfaces | 33 | 32 | 90.6% |
| Annotations | 122 | 106 | 100.0% |

## Edge-case dataset findings

The corrected edge-case parser now loads all eight hypotheses, including
`ec-03`, whose hypothesis string contains braces inside a string literal.
The current headless J2K result is **5 / 8 cases passed**:

| ID | Result | Note |
|---|---|---|
| `ec-01` | fail | 1 unsafe-call operator |
| `ec-02` | fail | 1 unsafe-call operator |
| `ec-03` | pass | 3 unsafe casts recorded |
| `ec-04` | pass | no recorded unsafe pattern |
| `ec-05` | pass | 1 unsafe cast recorded |
| `ec-06` | pass | 1 unsafe cast recorded |
| `ec-07` | fail | 1 unsafe-call operator |
| `ec-08` | pass | no recorded unsafe pattern |

See [docs/edge-cases.md](edge-cases.md) for per-case hypothesis and pass/fail
analysis.

## Reproducing locally

See the project [README](../README.md) — section **"J2K Evaluation — Local Reproduction"**.

# Usage of AI
I used Claude for debugging and generating some test/edge cases. The main
implementation was done by myself.
