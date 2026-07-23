# CLAUDE.md

## Project Overview

Gradle project plugin that reads `gradle/oci.versions.toml` and provides version-catalog-like accessors (`ociImages.*`) for the [gradle-oci](https://github.com/sgtsilvio/gradle-oci) plugin. Converts `sha256:` (OCI standard) to `sha256!` (gradle-oci format).

## Tech Stack

- Kotlin, Gradle project plugin development (`Plugin<Project>`)
- tomlj for TOML parsing
- JUnit 5 + AssertJ + GradleTestKit for testing

## Key Files

- `src/main/kotlin/.../OciVersionCatalogPlugin.kt` — Project plugin entry point, reads TOML, registers `ociImages` extension on the applying project
- `src/main/kotlin/.../OciVersionsToml.kt` — Finds and parses `gradle/oci.versions.toml` files
- `src/main/kotlin/.../OciRegistries.kt` — Declares gradle-oci registries and image mappings for the registry-qualified entries of the build tree
- `src/main/kotlin/.../OciPluginCompatibility.kt` — Turns a gradle-oci API incompatibility into an error naming both plugin versions
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
- Declares a gradle-oci registry plus image mapping per registry-qualified entry, taken from the own TOML and from the TOML of the other builds of the build tree (directly included builds and their parents)
- gradle-oci is a `compileOnly` dependency, all its API usage runs inside `withOciPluginCompatibility`

## Build & Test

```shell
./gradlew check       # Full build + tests + spotless
./gradlew test        # Run all tests
```
