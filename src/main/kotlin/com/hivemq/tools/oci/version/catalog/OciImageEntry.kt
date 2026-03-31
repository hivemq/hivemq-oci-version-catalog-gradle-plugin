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
    val digest: String?,
    val tag: String?,
    val update: Boolean,
) {
    fun toOciNotation(): String {
        val groupAndName = image.replace('/', ':')
        val version = when {
            digest != null -> digest.replace("sha256:", "sha256!")
            tag != null -> tag
            else -> error("OCI image entry '$name' must have either a digest or a tag")
        }
        return "$groupAndName:$version"
    }
}
