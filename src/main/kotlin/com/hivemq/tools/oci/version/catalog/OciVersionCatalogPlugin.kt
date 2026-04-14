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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.tomlj.Toml
import java.io.File

class OciVersionCatalogPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val tomlFile = findTomlFile(project.rootDir) ?: return

        val result = Toml.parse(tomlFile.toPath())
        val ociArray = result.getArrayOrEmpty("oci")
        if (ociArray.isEmpty) return

        val entries = mutableListOf<OciImageEntry>()
        for (i in 0 until ociArray.size()) {
            val table = ociArray.getTable(i)
            val name = table.getString("name") ?: error("Missing 'name' in [[oci]] entry $i")
            val image = table.getString("image") ?: error("Missing 'image' for '$name'")
            val ref = table.getString("reference") ?: table.getString("pinnedReference")
                ?: error("Missing 'reference' or 'pinnedReference' for '$name'")
            val atIndex = ref.indexOf('@')
            val tag = if (atIndex >= 0) ref.substring(0, atIndex) else ref
            val digest = if (atIndex >= 0) ref.substring(atIndex + 1) else null
            entries.add(OciImageEntry(name = name, image = image, tag = tag, digest = digest))
        }

        createExtension(project, entries)
    }

    private fun createExtension(project: Project, entries: List<OciImageEntry>) {
        val extension = project.extensions.create("ociImages", OciVersionCatalogExtension::class.java)

        for (entry in entries) {
            val segments = entry.name.split("-")
            var parent: ExtensionAware = extension as ExtensionAware
            for (j in 0 until segments.size - 1) {
                parent = (parent.extensions.findByName(segments[j]) as? ExtensionAware)
                    ?: (parent.extensions.create(
                        segments[j],
                        OciVersionCatalogGroupExtension::class.java
                    ) as ExtensionAware)
            }
            parent.extensions.create(segments.last(), OciVersionCatalogEntryExtension::class.java, entry)
        }
    }

    private fun findTomlFile(startDir: File): File? {
        var dir: File? = startDir
        while (dir != null) {
            val candidate = dir.resolve("gradle/oci.versions.toml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
