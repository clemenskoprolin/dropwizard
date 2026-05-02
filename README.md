Dropwizard — J2K Evaluation Fork
=================================

> **This is a research fork.** The upstream Dropwizard source is unchanged. A
> JetBrains J2K evaluation toolchain has been added to measure the quality of
> automated Java→Kotlin conversion on `dropwizard-example`.
>
> - Workflow: [`.github/workflows/j2k-evaluate.yml`](.github/workflows/j2k-evaluate.yml)
> - Evaluator tool: [`tools/j2k-evaluator/`](tools/j2k-evaluator/)
> - Headless J2K runner: [`tools/headless-j2k-runner/`](tools/headless-j2k-runner/)
> - Edge-case dataset: [`benchmarks/edge-cases/`](benchmarks/edge-cases/)
> - Findings: [`docs/summary.md`](docs/summary.md) · [`docs/edge-cases.md`](docs/edge-cases.md)

## J2K Evaluation — Local Reproduction

The workflow does not use a standalone `j2k` binary. Conversion is run through a
small IntelliJ Platform plugin that starts IDEA Community headlessly and calls
the bundled Kotlin J2K APIs.

Prerequisites: JDK 21, Maven 3.9+ (or use `./mvnw`), Gradle 8.13+, `git`, and
internet access for Maven, Gradle, IntelliJ Platform, and Petclinic benchmark
downloads.

```bash
# 1. Confirm the upstream example module still builds
./mvnw -pl dropwizard-example -am test

# 2. Build the headless IntelliJ J2K runner plugin
gradle -p tools/headless-j2k-runner buildPlugin --no-daemon

# 3. Build the evaluator (standalone — not part of the upstream reactor)
./mvnw -f tools/j2k-evaluator/pom.xml package

# 4. Prepare the example module's dependency classpath
./mvnw -pl dropwizard-example -am dependency:build-classpath \
  -Dmdep.outputFile=dropwizard-example/target/classpath.txt -q

# 5. Convert dropwizard-example sources
java -jar tools/j2k-evaluator/target/j2k-evaluator.jar convert-primary \
  --source dropwizard-example/src \
  --output artifacts/converted/dropwizard-example \
  --headless-runner-dir tools/headless-j2k-runner \
  --classpath-file dropwizard-example/target/classpath.txt

# 6. Evaluate the converted sources
java -jar tools/j2k-evaluator/target/j2k-evaluator.jar evaluate-primary \
  --source dropwizard-example/src \
  --converted artifacts/converted/dropwizard-example \
  --output artifacts/reports

# 7. (Optional) Run the Petclinic secondary benchmark
java -jar tools/j2k-evaluator/target/j2k-evaluator.jar compare-petclinic \
  --headless-runner-dir tools/headless-j2k-runner \
  --output artifacts/reports

# 8. (Optional) Run the edge-case dataset
java -jar tools/j2k-evaluator/target/j2k-evaluator.jar run-edge-cases \
  --dataset benchmarks/edge-cases \
  --headless-runner-dir tools/headless-j2k-runner \
  --output artifacts/reports

# Reports land in artifacts/reports/
#   primary-result.json    primary-summary.md
#   petclinic-result.json  petclinic-summary.md
#   edge-cases-result.json edge-cases-summary.md
```

All evaluation logic is in Kotlin. The evaluator shells out only to Gradle for
the headless IntelliJ runner and to `git clone` for secondary benchmark inputs.

---

## Upstream Dropwizard

[![Build](https://github.com/dropwizard/dropwizard/workflows/Java%20CI/badge.svg)](https://github.com/dropwizard/dropwizard/actions?query=workflow%3A%22Java+CI%22)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dropwizard_dropwizard&metric=alert_status)](https://sonarcloud.io/dashboard?id=dropwizard_dropwizard)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.dropwizard/dropwizard-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.dropwizard/dropwizard-core/)
[![Javadocs](https://javadoc.io/badge/io.dropwizard/dropwizard-project.svg?color=brightgreen)](https://javadoc.io/doc/io.dropwizard/dropwizard-project)
[![Documentation Status](https://readthedocs.org/projects/dropwizard/badge/?version=stable)](https://www.dropwizard.io/en/stable/?badge=stable)
[![Maintainability](https://api.codeclimate.com/v1/badges/11a16ea08c8b5499e2b9/maintainability)](https://codeclimate.com/github/dropwizard/dropwizard/maintainability)
[![Reproducible Builds](https://img.shields.io/badge/Reproducible_Builds-ok-green?labelColor=blue)](https://github.com/jvm-repo-rebuild/reproducible-central#io.dropwizard:dropwizard-core)
[![Contribute with Gitpod](https://img.shields.io/badge/Contribute%20with-Gitpod-908a85?logo=gitpod)](https://gitpod.io/#https://github.com/dropwizard/dropwizard)

*Dropwizard is a sneaky way of making fast Java web applications.*

It's a little bit of opinionated glue code which bangs together a set of libraries which have
historically not sucked:

* [Jetty](http://www.eclipse.org/jetty/) for HTTP servin'.
* [Jersey](https://jersey.github.io/) for REST modelin'.
* [Jackson](https://github.com/FasterXML/jackson) for JSON parsin' and generatin'.
* [Logback](http://logback.qos.ch/) for loggin'.
* [Hibernate Validator](http://hibernate.org/validator/) for validatin'.
* [Metrics](http://metrics.dropwizard.io) for figurin' out what your application is doin' in production.
* [JDBI](http://www.jdbi.org) and [Hibernate](http://www.hibernate.org/orm/) for databasin'.
* [Liquibase](http://www.liquibase.org/) for migratin'.

Read more at [dropwizard.io](http://www.dropwizard.io).

Want to contribute to Dropwizard?
---
Before working on the code, if you plan to contribute changes, please read the following [CONTRIBUTING](CONTRIBUTING.md) document.

Need help or found an issue?
---
When reporting an issue through the [issue tracker](https://github.com/dropwizard/dropwizard/issues?state=open)
on GitHub or sending an email to the
[Dropwizard User Google Group](https://groups.google.com/forum/#!forum/dropwizard-user)
mailing list, please use the following guidelines:

* Check existing issues to see if it has been addressed already
* The version of Dropwizard you are using
* A short description of the issue you are experiencing and the expected outcome
* Description of how someone else can reproduce the problem
* Paste error output or logs in your issue or in a Gist. If pasting them in the GitHub
issue, wrap it in three backticks: ```  so that it renders nicely
* Write a unit test to show the issue!
