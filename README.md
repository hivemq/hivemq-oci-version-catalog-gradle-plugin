# HiveMQ OCI Version Catalog Gradle Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.hivemq.tools.oci-version-catalog?color=brightgreen&style=for-the-badge)](https://plugins.gradle.org/plugin/com.hivemq.tools.oci-version-catalog)
[![GitHub](https://img.shields.io/github/license/hivemq/hivemq-oci-version-catalog-gradle-plugin?color=brightgreen&style=for-the-badge)](LICENSE)

A Gradle **settings plugin** that reads OCI/Docker image definitions from `gradle/oci.versions.toml` and provides
version-catalog-like accessors for the [gradle-oci](https://github.com/sgtsilvio/gradle-oci) plugin.

## TOML Format

Create a `gradle/oci.versions.toml` file alongside your `libs.versions.toml`:

```toml
[[oci]]
name = "eclipse-temurin"
image = "library/eclipse-temurin"
tag = "25-jre-noble"

[[oci]]
name = "k3s-minimum"
image = "rachner/k3s"
digest = "sha256:9e034931999854c6210b86a0708fde66b91370459fa077a4f9d008e7f51fc51d"
tag = "v1.24.17-k3s1"
update = false

[[oci]]
name = "k3s-latest"
image = "rachner/k3s"
tag = "latest"
```

### Fields

| Field    | Required | Description                                                        |
|----------|----------|--------------------------------------------------------------------|
| `name`   | Yes      | Accessor key. Hyphens become nested accessors (e.g. `k3s-latest`). |
| `image`  | Yes      | Docker image path (e.g. `library/eclipse-temurin`, `rancher/k3s`). |
| `digest` | No       | Image digest in `sha256:<hash>` format. Recommended for pinning.   |
| `tag`    | No       | Image tag. Used as fallback version if `digest` is not set.        |
| `update` | No       | Set to `false` to skip Renovate updates. Defaults to `true`.       |

Either `digest` or `tag` (or both) must be specified. Entries with a `digest` will use the digest for the gradle-oci
notation; entries without will fall back to the `tag`.

## Gradle Plugin Setup

### settings.gradle.kts

```kotlin
plugins {
    id("com.hivemq.tools.oci-version-catalog") version "0.1.0"
}
```

The plugin is applied once in settings and automatically injects the `ociImages` extension into all projects.
No changes needed in `build.gradle.kts`.

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

## Usage in Build Scripts

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
| `tag`    | `String?` | Image tag (e.g. `21-jre-noble`)                                     |
| `digest` | `String?` | Image digest in `sha256:<hash>` format                              |

### Example

```kotlin
oci {
    imageDefinitions {
        register("main") {
            allPlatforms {
                dependencies {
                    // digest-pinned parent image
                    runtime(ociImages.eclipse.temurin.oci)
                }
            }
        }
    }
}

// test image dependencies
oci.of(integrationTest) {
    imageDependencies {
        runtime(ociImages.k3s.latest.oci).tag(ociImages.k3s.latest.tag)
    }
}
```

### OCI Notation Conversion

The `oci` property converts the TOML format to gradle-oci dependency notation:

- Image path `/` becomes `:` (e.g. `rancher/k3s` becomes `rancher:k3s`)
- Digest `sha256:` becomes `sha256!` (e.g. `sha256:a1234...` becomes `sha256!a1234...`)
- Result: `rancher:k3s:sha256!a1234...`

If no digest is set, the tag is used as the version: `rancher:k3s:v1.35.1-k3s1`

## Renovate Integration

To enable automatic Docker image updates via [Renovate](https://docs.renovatebot.com/), add a
[JSONata custom manager](https://docs.renovatebot.com/modules/manager/jsonata/) to your `renovate.json5`:

```json5
{
    enabledManagers: [
        // ... your existing managers ...
        'jsonata',
    ],
    customManagers: [
        {
            customType: 'jsonata',
            datasourceTemplate: 'docker',
            description: 'OCI images in oci.versions.toml',
            fileFormat: 'toml',
            managerFilePatterns: ['**/oci.versions.toml'],
            matchStrings: [
                'oci[$not(update = false)].{"depName": image, "currentValue": tag, "currentDigest": digest}',
            ],
            versioningTemplate: 'docker',
        },
    ],
}
```

This will:

- Detect all OCI image entries in `oci.versions.toml`
- Skip entries with `update = false`
- Propose tag updates with semantic versioning via the Docker datasource
- Update digests when tags change (if `digest` is present)

### Important Notes

- `managerFilePatterns` uses **glob** syntax by default (regex requires `/slash/` delimiters)
- The JSONata filter uses `$not(update = false)` instead of `update != false` because
  `undefined != false` evaluates to `undefined` (falsy) in JSONata
- All entries should have a `digest` field for Renovate digest pinning to work — entries without
  a `digest` won't get digest-pinned by Renovate

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
