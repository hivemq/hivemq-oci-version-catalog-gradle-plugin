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

import io.github.sgtsilvio.gradle.oci.dsl.OciExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import java.io.File
import java.net.URI

private const val OCI_PLUGIN_ID = "io.github.sgtsilvio.gradle.oci"
private const val DOCKER_HUB = "docker.io"

internal data class OciImageCoordinates(
    val registry: String,
    val namespace: String,
    val name: String,
    val source: File,
) {

    /** The coordinate group that gradle-oci maps [namespace] to, see [OciImageEntry.group]. */
    val group: String get() = namespace.replace('/', '.')

    val image: String get() = if (registry == DOCKER_HUB) "$namespace/$name" else "$registry/$namespace/$name"
}

/**
 * Declares the registry of every registry-qualified image of the build tree with a matching image mapping.
 *
 * Gradle repositories are not part of a published component, so a build resolves the parent images of an image it
 * consumes against its own repositories. The registry of a parent image therefore has to be declared in the consuming
 * build, although only the build that defines the image declares the image itself. Collecting the entries of the whole
 * build tree keeps the image declared in exactly one `oci.versions.toml`.
 */
internal fun configureOciRegistries(project: Project, ownEntries: List<Pair<File, OciImageEntry>>) {
    project.plugins.withId(OCI_PLUGIN_ID) {
        val images = LinkedHashSet<OciImageCoordinates>()
        for ((source, entry) in ownEntries + buildTreeEntries(project)) {
            images += OciImageCoordinates(
                entry.registry ?: DOCKER_HUB,
                entry.namespace,
                entry.repository.substringAfterLast('/'),
                source,
            )
        }
        checkImagesAreUnambiguous(images)
        val registryImages = images.filter { it.registry != DOCKER_HUB }
        if (registryImages.isEmpty()) {
            return@withId
        }
        withOciPluginCompatibility {
            declareRegistries(project, registryImages)
        }
    }
}

/**
 * gradle-oci addresses an image by its coordinates, which do not contain the registry, so the same coordinates cannot
 * be served by two registries. Images of Docker Hub take part in this, their coordinates are built the same way.
 */
private fun checkImagesAreUnambiguous(images: Set<OciImageCoordinates>) {
    for ((coordinates, claims) in images.groupBy { it.group to it.name }) {
        val registries = claims.distinctBy { it.registry }
        if (registries.size > 1) {
            throw GradleException(
                "The images ${registries.joinToString(" and ") { "'${it.image}' (${it.source})" }} share the " +
                        "coordinates '${coordinates.first}:${coordinates.second}', although they are of different " +
                        "registries. gradle-oci coordinates do not contain the registry, so such images cannot be " +
                        "resolved next to each other, align the 'image' values of the listed files."
            )
        }
    }
}

private fun declareRegistries(project: Project, images: List<OciImageCoordinates>) {
    val oci = project.extensions.getByType(OciExtension::class.java)
    for ((host, registryImages) in images.groupBy { it.registry }) {
        with(oci.registries) {
            val registry = registry(toRegistryName(host)) { url.set(URI("https://$host")) }
            // scopes the registry to its own images, so all other images resolve as before
            registry.exclusiveContent {
                for (image in registryImages) {
                    includeModule(image.group, image.name)
                }
            }
        }
        for (image in registryImages.distinctBy { it.namespace }) {
            // gradle-oci derives the namespace from the group, which drops everything up to the first dot
            oci.imageMapping.mapGroup(image.group) {
                toImage(nameSpec(image.namespace + "/") + name)
            }
        }
    }
}

/** Reads the `oci.versions.toml` of every other build of the build tree, included builds and their parents. */
private fun buildTreeEntries(project: Project): List<Pair<File, OciImageEntry>> {
    val projectDirs = LinkedHashSet<File>()
    var gradle: Gradle? = project.gradle
    while (gradle != null) {
        gradle.includedBuilds.mapTo(projectDirs) { it.projectDir }
        gradle = gradle.parent
    }
    projectDirs -= project.rootDir
    return projectDirs.mapNotNull { findTomlFile(it) }
        .distinct()
        .flatMap { file -> parseEntries(file).map { file to it } }
}

/** Turns a registry host into a name usable for a Gradle repository, e.g. `public.ecr.aws` into `publicEcrAws`. */
private fun toRegistryName(host: String): String = host.split('.', ':', '-', '/')
    .filter { it.isNotEmpty() }
    .mapIndexed { index, part -> if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() } }
    .joinToString("")
