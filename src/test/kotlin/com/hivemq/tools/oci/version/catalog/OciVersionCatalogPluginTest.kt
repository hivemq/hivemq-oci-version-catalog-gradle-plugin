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
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OciVersionCatalogPluginTest {

    @TempDir
    lateinit var projectDir: File

    private fun setup(toml: String? = null, buildExtra: String = "") {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            plugins {
                id("com.hivemq.tools.oci-version-catalog")
            }
            rootProject.name = "test-project"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(buildExtra)
        if (toml != null) {
            val gradleDir = projectDir.resolve("gradle").also { it.mkdirs() }
            gradleDir.resolve("oci.versions.toml").writeText(toml)
        }
    }

    private fun gradleRunner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*args)
        .withPluginClasspath()

    @Test
    fun `plugin applies successfully without toml file`() {
        setup()
        val result = gradleRunner("tasks").build()
        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `plugin applies with toml file and creates ociImages extension`() {
        setup(
            toml = """
                [[oci]]
                name = "eclipse-temurin"
                image = "library/eclipse-temurin"
                digest = "sha256:01868992089327fe0871354378a499e34823e6c7439d32ca62a4876a152f6ccb"
                tag = "21-jre-noble"
            """.trimIndent(),
            buildExtra = """
                tasks.register("printOci") {
                    doLast {
                        val ext = project.extensions.getByName("ociImages")
                        val entry = (ext as org.gradle.api.plugins.ExtensionAware).extensions.getByName("eclipse")
                        val temurin = (entry as org.gradle.api.plugins.ExtensionAware).extensions
                            .getByType(com.hivemq.tools.oci.version.catalog.OciVersionCatalogEntryExtension::class.java)
                        println("OCI=" + temurin.oci)
                        println("IMAGE=" + temurin.image)
                        println("TAG=" + temurin.tag)
                        println("DIGEST=" + temurin.digest)
                    }
                }
            """.trimIndent(),
        )
        val result = gradleRunner("printOci").build()
        assertThat(result.output).contains("OCI=library:eclipse-temurin:sha256!01868992089327fe0871354378a499e34823e6c7439d32ca62a4876a152f6ccb")
        assertThat(result.output).contains("IMAGE=library/eclipse-temurin")
        assertThat(result.output).contains("TAG=21-jre-noble")
        assertThat(result.output).contains("DIGEST=sha256:01868992089327fe0871354378a499e34823e6c7439d32ca62a4876a152f6ccb")
    }

    @Test
    fun `single-segment name creates direct leaf extension`() {
        setup(
            toml = """
                [[oci]]
                name = "busybox"
                image = "library/busybox"
                digest = "sha256:b3255e7dfbcd10cb367af0d409747d511aeb66dfac98cf30e97e87e4207dd76f"
                tag = "latest"
                update = false
            """.trimIndent(),
            buildExtra = """
                tasks.register("printOci") {
                    doLast {
                        val ext = project.extensions.getByName("ociImages")
                        val busybox = (ext as org.gradle.api.plugins.ExtensionAware).extensions
                            .getByType(com.hivemq.tools.oci.version.catalog.OciVersionCatalogEntryExtension::class.java)
                        println("OCI=" + busybox.oci)
                    }
                }
            """.trimIndent(),
        )
        val result = gradleRunner("printOci").build()
        assertThat(result.output).contains("OCI=library:busybox:sha256!b3255e7dfbcd10cb367af0d409747d511aeb66dfac98cf30e97e87e4207dd76f")
    }

    @Test
    fun `tag-only entry without digest uses tag as version`() {
        setup(
            toml = """
                [[oci]]
                name = "k3s"
                image = "rancher/k3s"
                tag = "v1.35.1-k3s1"
            """.trimIndent(),
            buildExtra = """
                tasks.register("printOci") {
                    doLast {
                        val ext = project.extensions.getByName("ociImages")
                        val k3s = (ext as org.gradle.api.plugins.ExtensionAware).extensions
                            .getByType(com.hivemq.tools.oci.version.catalog.OciVersionCatalogEntryExtension::class.java)
                        println("OCI=" + k3s.oci)
                        println("DIGEST=" + k3s.digest)
                    }
                }
            """.trimIndent(),
        )
        val result = gradleRunner("printOci").build()
        assertThat(result.output).contains("OCI=rancher:k3s:v1.35.1-k3s1")
        assertThat(result.output).contains("DIGEST=null")
    }

    @Test
    fun `multi-segment name with shared prefix creates nested extensions`() {
        setup(
            toml = """
                [[oci]]
                name = "k3s-minimum"
                image = "rancher/k3s"
                digest = "sha256:aaaa"
                tag = "v1.24.17-k3s1"
                update = false

                [[oci]]
                name = "k3s-latest"
                image = "rancher/k3s"
                digest = "sha256:bbbb"
                tag = "v1.35.3-k3s1"
            """.trimIndent(),
            buildExtra = """
                tasks.register("printOci") {
                    doLast {
                        val ext = project.extensions.getByName("ociImages")
                        val k3s = (ext as org.gradle.api.plugins.ExtensionAware).extensions.getByName("k3s")
                        val minimum = (k3s as org.gradle.api.plugins.ExtensionAware).extensions.getByName("minimum")
                            as com.hivemq.tools.oci.version.catalog.OciVersionCatalogEntryExtension
                        val latest = (k3s as org.gradle.api.plugins.ExtensionAware).extensions.getByName("latest")
                            as com.hivemq.tools.oci.version.catalog.OciVersionCatalogEntryExtension
                        println("MINIMUM=" + minimum.oci)
                        println("LATEST=" + latest.oci)
                    }
                }
            """.trimIndent(),
        )
        val result = gradleRunner("printOci").build()
        assertThat(result.output).contains("MINIMUM=rancher:k3s:sha256!aaaa")
        assertThat(result.output).contains("LATEST=rancher:k3s:sha256!bbbb")
    }

    @Test
    fun `update false entries are still accessible`() {
        setup(
            toml = """
                [[oci]]
                name = "busybox"
                image = "library/busybox"
                digest = "sha256:abcd"
                tag = "latest"
                update = false
            """.trimIndent(),
            buildExtra = """
                tasks.register("printOci") {
                    doLast {
                        val ext = project.extensions.getByName("ociImages")
                        val busybox = (ext as org.gradle.api.plugins.ExtensionAware).extensions
                            .getByType(com.hivemq.tools.oci.version.catalog.OciVersionCatalogEntryExtension::class.java)
                        println("OCI=" + busybox.oci)
                    }
                }
            """.trimIndent(),
        )
        val result = gradleRunner("printOci").build()
        assertThat(result.output).contains("OCI=library:busybox:sha256!abcd")
    }
}
