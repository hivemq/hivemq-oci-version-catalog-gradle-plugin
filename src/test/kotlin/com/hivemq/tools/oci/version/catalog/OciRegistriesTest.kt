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

private const val GHCR_TOML = """
    [[oci]]
    name = "toxiproxy"
    image = "ghcr.io/shopify/toxiproxy"
    reference = "2.12.0@sha256:8a1f5b31ff7c60a9ec4a3d0e4b8f0c1d2e3a4b5c6d7e8f90112233445566778899"
"""

private const val DOCKER_HUB_TOML = """
    [[oci]]
    name = "ubuntu-noble"
    image = "library/ubuntu"
    reference = "noble@sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90"
"""

private const val PRINT_REGISTRIES_TASK = """
    tasks.register("printRegistries") {
        val registries = oci.registries.list
        val repositoryNames = repositories.names
        doLast {
            registries.forEach { println("REGISTRY=" + it.name + " " + it.url.get()) }
            repositoryNames.forEach { println("REPOSITORY=" + it) }
        }
    }
"""

class OciRegistriesTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `registry-qualified entry of the own catalog is declared`() {
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML)

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).contains("REGISTRY=ghcrIo https://ghcr.io")
        assertThat(result.output).contains("REPOSITORY=ghcrIoOciRegistry")
    }

    @Test
    fun `registry-qualified entry of an included build is declared in the consuming build`() {
        val producerDir = projectDir.resolve("producer")
        writeBuild(producerDir, name = "producer", toml = GHCR_TOML)
        writeBuild(projectDir, name = "consumer", toml = DOCKER_HUB_TOML, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).contains("REGISTRY=ghcrIo https://ghcr.io")
    }

    @Test
    fun `registry-qualified entry of an included build is declared without an own catalog`() {
        val producerDir = projectDir.resolve("producer")
        writeBuild(producerDir, name = "producer", toml = GHCR_TOML)
        writeBuild(projectDir, name = "consumer", toml = null, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).contains("REGISTRY=ghcrIo https://ghcr.io")
    }

    @Test
    fun `registry-qualified entry of a build included by the parent build is declared`() {
        // the composite build of the platform: the task of the consuming build is invoked from the root build
        val producerDir = projectDir.resolve("producer")
        writeBuild(producerDir, name = "producer", toml = GHCR_TOML)
        val consumerDir = projectDir.resolve("consumer")
        writeBuild(consumerDir, name = "consumer", toml = DOCKER_HUB_TOML)
        writeBuild(projectDir, name = "root", toml = null, includedBuilds = listOf("producer", "consumer"))

        val result = runner(projectDir, ":consumer:printRegistries").build()

        assertThat(result.output).contains("REGISTRY=ghcrIo https://ghcr.io")
    }

    @Test
    fun `an entry declared by the own and by an included build is declared once`() {
        val producerDir = projectDir.resolve("producer")
        writeBuild(producerDir, name = "producer", toml = GHCR_TOML)
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output.lines().filter { it.startsWith("REGISTRY=") })
            .containsExactly("REGISTRY=ghcrIo https://ghcr.io")
    }

    @Test
    fun `entries of two registries are declared as two registries`() {
        val producerDir = projectDir.resolve("producer")
        writeBuild(
            producerDir,
            name = "producer",
            toml = """
                [[oci]]
                name = "postgres"
                image = "quay.io/enterprisedb/postgresql"
                reference = "17@sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90"
            """.trimIndent(),
        )
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).contains("REGISTRY=ghcrIo https://ghcr.io")
        assertThat(result.output).contains("REGISTRY=quayIo https://quay.io")
    }

    @Test
    fun `entries without a registry declare no registry`() {
        writeBuild(projectDir, name = "consumer", toml = DOCKER_HUB_TOML)

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).doesNotContain("REGISTRY=")
    }

    @Test
    fun `entry of a build included by an included build is not discovered`() {
        // Gradle exposes the directly included builds only, so such a producer has to be included directly as well
        val producerDir = projectDir.resolve("producer")
        writeBuild(producerDir, name = "producer", toml = GHCR_TOML)
        val intermediateDir = projectDir.resolve("intermediate")
        writeBuild(intermediateDir, name = "intermediate", toml = null, includedBuilds = listOf("../producer"))
        writeBuild(projectDir, name = "consumer", toml = null, includedBuilds = listOf("intermediate"))

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).doesNotContain("REGISTRY=")
    }

    @Test
    fun `the same image of two registries fails`() {
        val producerDir = projectDir.resolve("producer")
        writeBuild(
            producerDir,
            name = "producer",
            toml = """
                [[oci]]
                name = "toxiproxy"
                image = "quay.io/shopify/toxiproxy"
                reference = "2.12.0@sha256:8a1f5b31ff7c60a9ec4a3d0e4b8f0c1d2e3a4b5c6d7e8f90112233445566778899"
            """.trimIndent(),
        )
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").buildAndFail()

        assertThat(result.output).contains("The images 'ghcr.io/shopify/toxiproxy' (")
        assertThat(result.output).contains("and 'quay.io/shopify/toxiproxy' (")
        assertThat(result.output).contains("share the coordinates 'shopify:toxiproxy'")
        assertThat(result.output).contains("producer/gradle/oci.versions.toml")
    }

    @Test
    fun `an incompatible gradle-oci version fails with both plugin versions named`() {
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML)

        val result = runner(projectDir, "printRegistries", gradleOci = incompatibleGradleOciClasspath).buildAndFail()

        assertThat(result.output).contains("The applied 'io.github.sgtsilvio.gradle.oci' plugin (0.13.0)")
        assertThat(result.output).contains("is not compatible with 'com.hivemq.tools.oci-version-catalog'")
        assertThat(result.output).contains("which is built against gradle-oci")
        assertThat(result.output).contains("NoSuchMethodError")
    }

    @Test
    fun `the same image of a registry and of docker hub fails`() {
        // the image moved from Docker Hub to another registry, the old entry is left behind in another build
        val producerDir = projectDir.resolve("producer")
        writeBuild(
            producerDir,
            name = "producer",
            toml = """
                [[oci]]
                name = "toxiproxy"
                image = "shopify/toxiproxy"
                pinnedReference = "2.1.4@sha256:8a1f5b31ff7c60a9ec4a3d0e4b8f0c1d2e3a4b5c6d7e8f90112233445566778899"
            """.trimIndent(),
        )
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").buildAndFail()

        assertThat(result.output).contains("The images 'ghcr.io/shopify/toxiproxy' (")
        assertThat(result.output).contains("and 'shopify/toxiproxy' (")
        assertThat(result.output).contains("share the coordinates 'shopify:toxiproxy'")
        assertThat(result.output).contains("producer/gradle/oci.versions.toml")
    }

    @Test
    fun `another image of the same namespace stays with docker hub`() {
        val producerDir = projectDir.resolve("producer")
        writeBuild(
            producerDir,
            name = "producer",
            toml = """
                [[oci]]
                name = "shopify-other"
                image = "shopify/other"
                reference = "1.0.0@sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90"
            """.trimIndent(),
        )
        writeBuild(projectDir, name = "consumer", toml = GHCR_TOML, includedBuilds = listOf("producer"))

        val result = runner(projectDir, "printRegistries").build()

        assertThat(result.output).contains("REGISTRY=ghcrIo https://ghcr.io")
    }

    private fun writeBuild(
        dir: File,
        name: String,
        toml: String?,
        includedBuilds: List<String> = emptyList(),
    ) {
        dir.mkdirs()
        dir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "$name"
            ${includedBuilds.joinToString("\n") { "includeBuild(\"$it\")" }}
            """.trimIndent()
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.hivemq.tools.oci-version-catalog")
                id("io.github.sgtsilvio.gradle.oci")
            }
            ${PRINT_REGISTRIES_TASK.trimIndent()}
            """.trimIndent()
        )
        if (toml != null) {
            dir.resolve("gradle").also { it.mkdirs() }.resolve("oci.versions.toml").writeText(toml.trimIndent())
        }
    }

    private fun runner(dir: File, vararg args: String, gradleOci: List<File> = gradleOciClasspath) =
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(*args)
            .withPluginClasspath(pluginClasspath + gradleOci)

    private val pluginClasspath get() = classpathOf("pluginClasspath")
    private val gradleOciClasspath get() = classpathOf("gradleOciClasspath")
    private val incompatibleGradleOciClasspath get() = classpathOf("gradleOciIncompatibleClasspath")

    private fun classpathOf(systemProperty: String): List<File> {
        val value =
            requireNotNull(System.getProperty(systemProperty)) { "system property '$systemProperty' is missing" }
        return value.split(File.pathSeparator).map(::File)
    }
}
