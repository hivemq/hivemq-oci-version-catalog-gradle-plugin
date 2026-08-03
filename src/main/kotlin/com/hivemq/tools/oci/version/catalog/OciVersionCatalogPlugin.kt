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

class OciVersionCatalogPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val tomlFile = findTomlFile(project.rootDir)
        val entries = if (tomlFile == null) emptyList() else parseEntries(tomlFile).map { tomlFile to it }
        if (entries.isNotEmpty()) {
            createExtension(project, entries.map { it.second })
        }
        configureOciRegistries(project, entries)
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
}
