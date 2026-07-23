# HiveMQ OCI Version Catalog Gradle Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.hivemq.tools.oci-version-catalog?color=brightgreen&style=for-the-badge)](https://plugins.gradle.org/plugin/com.hivemq.tools.oci-version-catalog)
[![GitHub](https://img.shields.io/github/license/hivemq/hivemq-oci-version-catalog-gradle-plugin?color=brightgreen&style=for-the-badge)](LICENSE)

A Gradle project plugin that reads OCI/Docker image definitions from `gradle/oci.versions.toml` and provides
version-catalog-like accessors for the [gradle-oci](https://github.com/sgtsilvio/gradle-oci) plugin.

For images of a registry other than Docker Hub, the plugin also declares the gradle-oci registry and the image
mapping, so that a registry is configured in one place, the `image` value of the entry. This covers the entries of
the applying build as well as the entries of the other builds of the build tree, see
[Registry-Qualified Images](#registry-qualified-images) and [Registries of Other Builds](#registries-of-other-builds).

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

Contents of the `build.gradle.kts` file:
```kotlin
plugins {
    id("com.hivemq.tools.oci-version-catalog") version "0.4.0"
}

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

All images of this example live on Docker Hub, which gradle-oci uses by default, so no registry is declared. An
`image` that names a registry host makes the plugin declare that registry, see the next section.

## Configuration

### TOML Fields

| Field             | Required | Description                                                          |
|-------------------|----------|----------------------------------------------------------------------|
| `name`            | Yes      | Accessor key. Hyphens become nested accessors (e.g. `k3s-latest`).   |
| `image`           | Yes      | Image path, optionally registry-qualified (see below).               |
| `reference`       | *        | Image tag and digest as `tag@sha256:hash`. Updated by Renovate.      |
| `pinnedReference` | *        | Same format as `reference`, but invisible to Renovate (not updated). |

Exactly one of `reference` or `pinnedReference` must be specified.
The reference format is `tag@sha256:hash` (with digest) or just `tag` (without digest).
Entries with a digest use it for the gradle-oci notation; entries without fall back to the tag.

### Registry-Qualified Images

An unqualified `image` (e.g. `library/eclipse-temurin`) is resolved against Docker Hub. Prefixing it
with a registry host selects a different registry:

```toml
[[oci]]
name = "eclipse-temurin"
image = "public.ecr.aws/y7j2u9c5/base-images/eclipse-temurin"
reference = "21-jre-noble@sha256:1a407124990ecf35af8e80fabcf311218b590d6f3a7df61ce8a294efcb704dd4"
```

The first segment is treated as a registry host if it contains a `.` or a `:`, or is `localhost` —
the same convention the OCI distribution spec uses. Qualifying the image keeps the whole reference in
this file, which is what lets Renovate resolve it against the right registry: it uses `image`
verbatim as the dependency name.

gradle-oci addresses registries separately, so the host is not part of the `oci` notation. The plugin
declares the registry and the matching image mapping itself, for every registry-qualified entry, so
no build script has to repeat the image path:

```kotlin
// declared by the plugin for the image "ghcr.io/acme/base-images/eclipse-temurin",
// shown here only to describe what it does
registry("ghcrIo") {
    url = uri("https://ghcr.io")
    exclusiveContent { includeGroup("acme.base-images") } // else Docker Hub is searched too
}
imageMapping {
    mapGroup("acme.base-images") { toImage(nameSpec("acme/base-images/") + name) }
}
```

The image mapping is needed whenever `namespace` has more than one segment, because gradle-oci
derives the namespace back from the coordinate group by dropping everything up to the first dot.

### Registries of Other Builds

A build that consumes an image built by another build resolves that image's parent images against its
own repositories, because Gradle repositories are not part of a published component. Such a build
therefore needs the registry of a parent image although it never declares that image itself.

To keep the image declared in exactly one `oci.versions.toml`, the plugin also reads the
`oci.versions.toml` of the other builds of the build tree and declares their registries as well. Only
the registry declaration is shared, the `ociImages` accessors always come from the own file.

Gradle exposes the directly included builds of a build, so a build that provides an image has to be
included directly by the consuming build:

```kotlin
// settings.gradle.kts of the consuming build
includeBuild("../app") // builds the image
includeBuild("../base") // declares the parent image, included by ../app as well
```

A group can only resolve to a single registry and namespace. If two builds of the tree claim the same
group with different `image` values, the build fails with both values named.

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
| `oci`        | `String`  | gradle-oci notation (e.g. `library:eclipse-temurin:sha256!0186...`)    |
| `image`      | `String`  | Original image path (e.g. `library/eclipse-temurin`)                   |
| `tag`        | `String`  | Image tag (e.g. `21-jre-noble`)                                        |
| `digest`     | `String?` | Image digest in `sha256:<hash>` format                                 |
| `registry`   | `String?` | Registry host, or `null` if unqualified (e.g. `public.ecr.aws`)        |
| `repository` | `String`  | Image path within the registry (e.g. `y7j2u9c5/base-images/eclipse-temurin`) |
| `namespace`  | `String`  | `repository` without the image name (e.g. `y7j2u9c5/base-images`)      |
| `group`      | `String`  | Coordinate group of `oci` (e.g. `y7j2u9c5.base-images`)                |

### OCI Notation Conversion

The `oci` property converts the TOML format to gradle-oci dependency notation:

- The registry host, if any, is dropped (gradle-oci addresses registries separately)
- The namespace becomes the coordinate group, `/` becoming `.` (e.g. `rancher/k3s` becomes `rancher:k3s`)
- Digest `sha256:` becomes `sha256!` (e.g. `sha256:a1234...` becomes `sha256!a1234...`)
- Result: `rancher:k3s:sha256!a1234...`

If no digest is set, the tag is used as the version: `rancher:k3s:v1.35.1-k3s1`

### Composite Builds

For included builds that need access to `ociImages`, apply the plugin in each included build's
`build.gradle.kts`. The plugin walks up the directory tree to find `gradle/oci.versions.toml`, so it
automatically picks up the parent project's TOML file.

```kotlin
// hivemq-platform-monitoring/build.gradle.kts
plugins {
    id("com.hivemq.tools.oci-version-catalog") version "0.4.0"
}
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
- The gradle-oci plugin is optional. When it is applied, its version has to be compatible with the
  version this plugin is built against, currently `0.28.0`. An incompatible version fails the build
  at plugin apply, naming both versions.

## Build

Execute the `check` task to run tests and validation:
```shell
./gradlew check
```
