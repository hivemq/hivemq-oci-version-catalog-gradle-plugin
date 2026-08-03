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

import org.gradle.api.GradleException
import org.gradle.api.UnknownDomainObjectException
import java.util.*

private const val OCI_PLUGIN_ID = "io.github.sgtsilvio.gradle.oci"
private const val PLUGIN_ID = "com.hivemq.tools.oci-version-catalog"
private const val VERSIONS_RESOURCE = "/com/hivemq/tools/oci/version/catalog/versions.properties"
private const val UNKNOWN_VERSION = "unknown"

/**
 * Runs [block] against the gradle-oci API and turns a version incompatibility into an actionable error.
 *
 * The gradle-oci dependency is `compileOnly`, so the applied version is whatever the consuming build declares. If that
 * version no longer provides an API used here, the JVM fails with a [LinkageError] that names the missing member but
 * neither of the two plugin versions involved, which is what makes such a failure hard to place.
 */
internal fun <T> withOciPluginCompatibility(block: () -> T): T = try {
    block()
} catch (e: LinkageError) {
    throw GradleException(incompatibilityMessage(e.toString()), e)
} catch (e: UnknownDomainObjectException) {
    throw GradleException(incompatibilityMessage(e.toString()), e)
}

private fun incompatibilityMessage(cause: String): String {
    val versions = readVersions()
    val builtAgainst = versions.getProperty("gradleOci", UNKNOWN_VERSION)
    val pluginVersion = versions.getProperty("plugin", UNKNOWN_VERSION)
    return "The applied '$OCI_PLUGIN_ID' plugin (${appliedOciPluginVersion()}) is not compatible with " +
            "'$PLUGIN_ID' $pluginVersion, which is built against gradle-oci $builtAgainst. " +
            "Align both plugin versions, then retry. Cause: $cause"
}

private fun readVersions(): Properties {
    val properties = Properties()
    OciVersionCatalogPlugin::class.java.getResourceAsStream(VERSIONS_RESOURCE)?.use { properties.load(it) }
    return properties
}

/** Derives the applied gradle-oci version from the file name of the jar the API is loaded from. */
private fun appliedOciPluginVersion(): String {
    val location = try {
        Class.forName("io.github.sgtsilvio.gradle.oci.OciPlugin").protectionDomain?.codeSource?.location
    } catch (_: Throwable) {
        null
    } ?: return UNKNOWN_VERSION
    val fileName = location.path.substringAfterLast('/')
    return Regex("""gradle-oci-(.+)\.jar""").find(fileName)?.groupValues?.get(1) ?: UNKNOWN_VERSION
}
