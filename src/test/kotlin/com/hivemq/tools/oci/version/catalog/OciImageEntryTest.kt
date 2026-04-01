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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OciImageEntryTest {

    @Test
    fun `toOciNotation with digest converts to gradle-oci format`() {
        val entry = OciImageEntry(
            name = "eclipse-temurin",
            image = "library/eclipse-temurin",
            tag = "21-jre-noble",
            digest = "sha256:01868992089327fe0871354378a499e34823e6c7439d32ca62a4876a152f6ccb",
        )
        assertThat(entry.toOciNotation())
            .isEqualTo("library:eclipse-temurin:sha256!01868992089327fe0871354378a499e34823e6c7439d32ca62a4876a152f6ccb")
    }

    @Test
    fun `toOciNotation with digest for non-library image`() {
        val entry = OciImageEntry(
            name = "k3s",
            image = "rancher/k3s",
            tag = "v1.35.3-k3s1",
            digest = "sha256:4607083d3cac07e1ccde7317297271d13ed5f60f35a78f33fcef84858a9f1d69",
        )
        assertThat(entry.toOciNotation())
            .isEqualTo("rancher:k3s:sha256!4607083d3cac07e1ccde7317297271d13ed5f60f35a78f33fcef84858a9f1d69")
    }

    @Test
    fun `toOciNotation without digest falls back to tag`() {
        val entry = OciImageEntry(
            name = "k3s",
            image = "rancher/k3s",
            tag = "v1.35.1-k3s1",
            digest = null,
        )
        assertThat(entry.toOciNotation()).isEqualTo("rancher:k3s:v1.35.1-k3s1")
    }

    @Test
    fun `toOciNotation prefers digest over tag`() {
        val entry = OciImageEntry(
            name = "busybox",
            image = "library/busybox",
            tag = "latest",
            digest = "sha256:b3255e7dfbcd10cb367af0d409747d511aeb66dfac98cf30e97e87e4207dd76f",
        )
        assertThat(entry.toOciNotation())
            .isEqualTo("library:busybox:sha256!b3255e7dfbcd10cb367af0d409747d511aeb66dfac98cf30e97e87e4207dd76f")
    }
}
