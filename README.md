# HiveMQ OCI Version Catalog Gradle Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.hivemq.tools.oci-version-catalog?color=brightgreen&style=for-the-badge)](https://plugins.gradle.org/plugin/com.hivemq.tools.oci-version-catalog)
[![GitHub](https://img.shields.io/github/license/hivemq/hivemq-oci-version-catalog-gradle-plugin?color=brightgreen&style=for-the-badge)](LICENSE)

A Gradle settings plugin that reads OCI/Docker image definitions from `gradle/oci.versions.toml` and provides
version-catalog-like accessors for the [gradle-oci](https://github.com/sgtsilvio/gradle-oci) plugin.

## Example

Contents of the `gradle/oci.versions.toml` file:
```toml
[[oci]]
name = "eclipse-temurin"
image = "library/eclipse-temurin"
reference = "25-jre-noble@sha256:01868992089327fe0871354378a499e34823e6c7439d32ca62a4876a152f6ccb"

[[oci]]
name = "k3s-minimum"
image = "rancher/k3s"
pinnedReference = "v1.24.17-k3s1@sha256:9e034931999854c6210b86a0708fde66b91370459fa077a4f9d008e7f51fc51d"

[[oci]]
name = "k3s-latest"
image = "rancher/k3s"
reference = "v1.35.3-k3s1@sha256:4607083d3cac07e1ccde7317297271d13ed5f60f35a78f33fcef84858a9f1d69"
```

Contents of the `settings.gradle.kts` file:
```kotlin
plugins {
    id("com.hivemq.tools.oci-version-catalog") version "0.1.0"
}
```

Contents of the `build.gradle.kts` file:
```kotlin
oci {
    imageDefinitions {
        register("main") {
            allPlatforms {
                dependencies {
                    runtime(ociImages.eclipse.temurin.oci)
                }
            }
        }
    }
}

oci.of(integrationTest) {
    imageDependencies {
        runtime(ociImages.k3s.latest.oci).tag(ociImages.k3s.latest.tag)
    }
}
```

## Configuration

### TOML Fields

| Field             | Required | Description                                                          |
|-------------------|----------|----------------------------------------------------------------------|
| `name`            | Yes      | Accessor key. Hyphens become nested accessors (e.g. `k3s-latest`).   |
| `image`           | Yes      | Docker image path (e.g. `library/eclipse-temurin`, `rancher/k3s`).   |
| `reference`       | *        | Image tag and digest as `tag@sha256:hash`. Updated by Renovate.      |
| `pinnedReference` | *        | Same format as `reference`, but invisible to Renovate (not updated). |

Exactly one of `reference` or `pinnedReference` must be specified.
The reference format is `tag@sha256:hash` (with digest) or just `tag` (without digest).
Entries with a digest use it for the gradle-oci notation; entries without fall back to the tag.

### Accessor Mapping

Hyphens in `name` become nested accessors, like Gradle version catalogs:

| TOML `name`       | Accessor                    |
|-------------------|-----------------------------|
| `eclipse-temurin` | `ociImages.eclipse.temurin` |
| `busybox`         | `ociImages.busybox`         |
| `k3s-latest`      | `ociImages.k3s.latest`      |

Each accessor provides the following properties:

| Property | Type      | Description                                                         |
|----------|-----------|---------------------------------------------------------------------|
| `oci`    | `String`  | gradle-oci notation (e.g. `library:eclipse-temurin:sha256!0186...`) |
| `image`  | `String`  | Original image path (e.g. `library/eclipse-temurin`)                |
| `tag`    | `String`  | Image tag (e.g. `21-jre-noble`)                                     |
| `digest` | `String?` | Image digest in `sha256:<hash>` format                              |

### OCI Notation Conversion

The `oci` property converts the TOML format to gradle-oci dependency notation:

- Image path `/` becomes `:` (e.g. `rancher/k3s` becomes `rancher:k3s`)
- Digest `sha256:` becomes `sha256!` (e.g. `sha256:a1234...` becomes `sha256!a1234...`)
- Result: `rancher:k3s:sha256!a1234...`

If no digest is set, the tag is used as the version: `rancher:k3s:v1.35.1-k3s1`

### Composite Builds

For included builds that need access to `ociImages`, apply the plugin in each included build's
`settings.gradle.kts`. The plugin walks up the directory tree to find `gradle/oci.versions.toml`, so it
automatically picks up the parent project's TOML file.

```kotlin
// hivemq-platform-monitoring/settings.gradle.kts
plugins {
    id("com.hivemq.tools.oci-version-catalog") version "0.1.0"
}

rootProject.name = "hivemq-platform-monitoring"
```

## Renovate Integration

To enable automatic Docker image updates via [Renovate](https://docs.renovatebot.com/), add a
[regex custom manager](https://docs.renovatebot.com/modules/manager/regex/) to your `renovate.json5`:

```json5
{
    enabledManagers: [
        // ... your existing managers ...
        'regex',
    ],
    customManagers: [
        {
            customType: 'regex',
            datasourceTemplate: 'docker',
            description: 'OCI images in oci.versions.toml',
            managerFilePatterns: ['**/oci.versions.toml'],
            matchStrings: [
                'image\\s*=\\s*"(?<depName>[^"]+)"\\nreference\\s*=\\s*"(?<currentValue>[^@]+)@(?<currentDigest>[^"]+)"',
            ],
            autoReplaceStringTemplate: 'image = "{{depName}}"\\nreference = "{{newValue}}@{{#if newDigest}}{{newDigest}}{{else}}{{currentDigest}}{{/if}}"',
            versioningTemplate: 'docker',
        },
    ],
}
```

This will:

- Detect OCI image entries with a `reference` field in `oci.versions.toml`
- Skip entries with `pinnedReference` (they are invisible to the regex)
- Propose tag updates with semantic versioning via the Docker datasource
- Update digests when tags change

### Suppressing gradle-oci Platform Warnings

If you use `platform("linux", "arm64")` in your gradle-oci configuration, Renovate's Gradle manager will try to
look up `linux:arm64` as a Maven package. Suppress this with a package rule:

```json5
{
    packageRules: [
        {
            description: 'OCI platform dependencies are not real Maven packages (gradle-oci plugin)',
            matchManagers: ['gradle'],
            matchPackageNames: ['linux:amd64', 'linux:arm64'],
            enabled: false,
        },
    ],
}
```

## Requirements

- Gradle 9.0 or higher is required
- JDK 11 or higher is required

## Build

Execute the `check` task to run tests and validation:
```shell
./gradlew check
```
