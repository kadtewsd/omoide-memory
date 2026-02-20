/**
 * Downloader プロジェクトの plugin
 */
plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

group = "com.kasakaid"
version = "0.0.1-SNAPSHOT"
description = "Omoide Memory を GDrive からダウンロードする"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
ktlint {
    verbose.set(true)
    // ./gradlew ktlintFormat でなんらかの失敗が出ても Format は行うようにする
    ignoreFailures.set(true)
    version.set("1.8.0")
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}
repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

    // jOOQ (depend on the monorepo project)
    implementation(project(":omoide-memory-jooq"))

    // R2DBC PostgreSQL
    implementation("org.postgresql:r2dbc-postgresql")
    runtimeOnly("org.postgresql:postgresql") // Needed for Liquibase or other JDBC tools if any, keeping just in case

    // Google Drive API
    implementation("com.google.api-client:google-api-client:2.8.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")

    // Metadata Extraction
    implementation("com.drewnoakes:metadata-extractor:2.18.0") // Image EXIF

    // HTTP Client (Nominatim API)
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // R2DBC Proxy for logging
    implementation("io.r2dbc:r2dbc-proxy:1.1.4.RELEASE")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
