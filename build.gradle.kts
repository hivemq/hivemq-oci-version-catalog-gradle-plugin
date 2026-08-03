import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `kotlin-dsl`
    signing
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.defaults)
    alias(libs.plugins.metadata)
    alias(libs.plugins.spotless)
}

group = "com.hivemq.tools"

metadata {
    readableName = "HiveMQ OCI Version Catalog Gradle Plugin"
    description = "A Gradle plugin to read oci.versions.toml and provide typed accessors for gradle-oci"
    organization {
        name = "HiveMQ"
        url = "https://www.hivemq.com/"
    }
    license {
        apache2()
    }
    github {
        org = "hivemq"
        issues()
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.compileJava {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

tasks.compileKotlin {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    })
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.tomlj)
    compileOnly(libs.gradleOci)
    testImplementation(libs.assertj)
}

tasks.processResources {
    val pluginVersion = version.toString()
    val gradleOciVersion = libs.versions.gradleOci.get()
    inputs.property("pluginVersion", pluginVersion)
    inputs.property("gradleOciVersion", gradleOciVersion)
    filesMatching("com/hivemq/tools/oci/version/catalog/versions.properties") {
        filter<ReplaceTokens>("tokens" to mapOf("pluginVersion" to pluginVersion, "gradleOciVersion" to gradleOciVersion))
    }
}

gradlePlugin {
    plugins {
        create("oci-version-catalog") {
            id = "$group.oci-version-catalog"
            implementationClass = "$group.oci.version.catalog.OciVersionCatalogPlugin"
            tags = listOf("oci", "docker", "versions", "catalog")
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    isRequired = signingKey != null && signingPassword != null
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        "test"(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
        }
    }
}

// the gradle-oci plugin is injected into the test builds instead of resolved from the plugin portal, so that the tests
// pick the version to test against and both plugins end up in the same class loader scope, as in a real build
val gradleOciUnderTest: Configuration by configurations.creating
val gradleOciIncompatibleUnderTest: Configuration by configurations.creating

dependencies {
    gradleOciUnderTest(libs.gradleOci)
    // last version without OciRegistries.exclusiveContent, kept out of the version catalog so that it is not updated
    gradleOciIncompatibleUnderTest("io.github.sgtsilvio.gradle:gradle-oci:0.13.0")
}

tasks.test {
    val pluginClasspath = sourceSets.main.get().runtimeClasspath
    inputs.files(pluginClasspath, gradleOciUnderTest, gradleOciIncompatibleUnderTest)
    doFirst {
        systemProperty("pluginClasspath", pluginClasspath.asPath)
        systemProperty("gradleOciClasspath", gradleOciUnderTest.asPath)
        systemProperty("gradleOciIncompatibleClasspath", gradleOciIncompatibleUnderTest.asPath)
    }
}

spotless {
    kotlin {
        licenseHeaderFile(rootDir.resolve("HEADER"), "(package |@file:)")
    }
}
