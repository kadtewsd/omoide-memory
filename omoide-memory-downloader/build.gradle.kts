

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.kasakaid"
version = "0.0.1-SNAPSHOT"
description = "Omoide Memory を GDrive からダウンロードする"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")

    // Metadata Extraction
    implementation("com.drewnoakes:metadata-extractor:2.18.0") // Image EXIF
    implementation("ws.schild:jave-core:3.3.1") // Video metadata
    implementation("ws.schild:jave-nativebin-win64:3.3.1") // Windows ffmpeg binary

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
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}