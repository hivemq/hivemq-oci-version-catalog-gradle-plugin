# CLAUDE.md

## Project Overview

Gradle project plugin that reads `gradle/oci.versions.toml` and provides version-catalog-like accessors (`ociImages.*`) for the [gradle-oci](https://github.com/sgtsilvio/gradle-oci) plugin. Converts `sha256:` (OCI standard) to `sha256!` (gradle-oci format).

## Tech Stack

- Kotlin, Gradle project plugin development (`Plugin<Project>`)
- tomlj for TOML parsing
- JUnit 5 + AssertJ + GradleTestKit for testing

## Key Files

- `src/main/kotlin/.../OciVersionCatalogPlugin.kt` — Project plugin entry point, reads TOML, registers `ociImages` extension on the applying project
- `src/main/kotlin/.../OciImageEntry.kt` — Data class with `toOciNotation()` conversion
- `src/main/kotlin/.../OciVersionCatalogEntryExtension.kt` — Leaf accessor with `image`, `tag`, `digest`, `oci` properties
- `src/main/kotlin/.../OciVersionCatalogGroupExtension.kt` — Intermediate node for hyphen-separated names
- `src/main/kotlin/.../OciVersionCatalogExtension.kt` — Top-level `ociImages` extension

## TOML Format

Each `[[oci]]` entry has `name`, `image`, and either `reference` or `pinnedReference`:

- `reference = "tag@sha256:hash"` — updated by Renovate
- `pinnedReference = "tag@sha256:hash"` — invisible to Renovate (not updated)
- The `@sha256:hash` part is optional (tag-only entries are supported)

## Plugin Details

- **Plugin ID:** `com.hivemq.tools.oci-version-catalog`
- **Group:** `com.hivemq.tools`
- **Package:** `com.hivemq.tools.oci.version.catalog`
- **Extension name:** `ociImages`
- Hyphens in TOML `name` become nested accessors: `eclipse-temurin` → `ociImages.eclipse.temurin`
- Walks up directories from `project.rootDir` to find `gradle/oci.versions.toml` (supports composite/included builds)

## Build & Test

```shell
./gradlew check       # Full build + tests + spotless
./gradlew test        # Run all tests
```
