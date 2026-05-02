# Headless J2K Runner

This module is a small IntelliJ Platform plugin used by the J2K evaluation
workflow. It exists because Kotlin 2.1.20 does not ship a standalone `j2k`
binary in the compiler distribution. J2K is tightly coupled to IntelliJ IDEA, so
the CI-native path is to run IDEA Community headlessly and call the bundled
Kotlin J2K APIs.

## Design

- The plugin registers `j2k-headless` through `ApplicationStarter`.
- `HeadlessJ2kStarter` runs in headless mode and outside the EDT.
- `ConversionRunner` opens a lightweight IDEA project, creates a module for the
  requested source root, attaches the Maven classpath, and converts Java PSI
  files with `J2kConverterExtension.Kind.K1_NEW`.
- `runJ2kHeadless` disables IntelliJ split mode and writes IDEA config, system,
  plugin, and log directories under this module's Gradle build directory.
- The runner pins IDEA Community `2024.3.5`; API changes should break the build
  rather than silently changing conversion behavior.

## Build

Use JDK 21 and Gradle 8.13 or newer:

```bash
gradle -p tools/headless-j2k-runner buildPlugin --no-daemon
```

Generated IDEA and Gradle artifacts under this directory are ignored by git.

## Run Directly

The evaluator normally invokes this task for you. For debugging, run it
directly with absolute paths:

```bash
gradle -p tools/headless-j2k-runner runJ2kHeadless --no-daemon \
  -Pj2k.sourceRoot=/abs/path/to/src \
  -Pj2k.outputRoot=/abs/path/to/output \
  -Pj2k.classpathFile=/abs/path/to/classpath.txt \
  -Pj2k.files=/abs/path/to/files.txt \
  -Pj2k.report=/abs/path/to/conversion-result.json
```

`j2k.files` is optional. If omitted, the runner converts every `.java` file
under `j2k.sourceRoot`. `j2k.classpathFile` is optional for isolated edge-case
inputs but should be provided for project sources.
