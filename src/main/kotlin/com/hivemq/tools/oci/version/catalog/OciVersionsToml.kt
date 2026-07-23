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

import org.tomlj.Toml
import java.io.File

/** Finds the closest `gradle/oci.versions.toml` at or above [startDir], or `null` if there is none. */
internal fun findTomlFile(startDir: File): File? {
    var dir: File? = startDir
    while (dir != null) {
        val candidate = dir.resolve("gradle/oci.versions.toml")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}

internal fun parseEntries(tomlFile: File): List<OciImageEntry> {
    val result = Toml.parse(tomlFile.toPath())
    val ociArray = result.getArrayOrEmpty("oci")

    val entries = mutableListOf<OciImageEntry>()
    for (i in 0 until ociArray.size()) {
        val table = ociArray.getTable(i)
        val name = table.getString("name") ?: error("Missing 'name' in [[oci]] entry $i of $tomlFile")
        val image = table.getString("image") ?: error("Missing 'image' for '$name' in $tomlFile")
        val ref = table.getString("reference") ?: table.getString("pinnedReference")
        ?: error("Missing 'reference' or 'pinnedReference' for '$name' in $tomlFile")
        val atIndex = ref.indexOf('@')
        val tag = if (atIndex >= 0) ref.substring(0, atIndex) else ref
        val digest = if (atIndex >= 0) ref.substring(atIndex + 1) else null
        entries.add(OciImageEntry(name = name, image = image, tag = tag, digest = digest))
    }
    return entries
}
