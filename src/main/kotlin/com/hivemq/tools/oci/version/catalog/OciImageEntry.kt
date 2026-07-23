/*
 * Copyright 2025-present HiveMQ and the HiveMQ Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.tools.oci.version.catalog

data class OciImageEntry(
    val name: String,
    val image: String,
    val tag: String,
    val digest: String?,
) {
    private val segments = image.split('/')

    /**
     * The registry host of [image], or `null` if [image] is not registry-qualified (implying Docker Hub).
     *
     * Renovate uses the `image` field verbatim as the dependency name, so registry-qualifying an image is what lets it
     * resolve the image against the right registry. gradle-oci instead addresses registries separately, via
     * `oci { registries { ... } }`, so the host is not part of the coordinate returned by [toOciNotation]. Exposing it
     * here keeps `oci.versions.toml` the single place the image is declared.
     */
    val registry: String? = segments.first().takeIf { (segments.size > 1) && it.isRegistryHost() }

    /** [image] without its registry host, i.e. the path of the image within its registry. */
    val repository: String = if (registry == null) image else segments.drop(1).joinToString("/")

    /** [repository] without the image name, i.e. the namespace the image lives in within its registry. */
    val namespace: String = repository.split('/').dropLast(1).joinToString("/")

    /**
     * The coordinate group [toOciNotation] maps [namespace] to. For a registry-qualified image the group carries the
     * [registry] host, separated from the namespace by `!`, so gradle-oci resolves the image against that registry
     * without a declared registry or an `imageMapping`. For a Docker Hub image it is just the namespace.
     */
    val group: String = if (registry == null) namespace.replace('/', '.') else "$registry!${namespace.replace('/', '.')}"

    fun toOciNotation(): String {
        val version = digest?.replace("sha256:", "sha256!") ?: tag
        return "$group:${repository.substringAfterLast('/')}:$version"
    }
}

/**
 * Distinguishes a registry host from the first path segment of a Docker Hub image, following the same convention as the
 * OCI distribution spec: a host contains a `.` (domain) or a `:` (port), with `localhost` as the only exception.
 */
private fun String.isRegistryHost() = ('.' in this) || (':' in this) || (this == "localhost")
