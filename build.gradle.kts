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
}

dependencies {
    implementation(libs.tomlj)
    testImplementation(libs.assertj)
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

spotless {
    kotlin {
        licenseHeaderFile(rootDir.resolve("HEADER"), "(package |@file:)")
    }
}
