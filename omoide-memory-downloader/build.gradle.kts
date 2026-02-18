import org.jooq.meta.jaxb.Property
plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
    id("nu.studer.jooq") version "9.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply true
}

group = "com.kasakaid"
version = "0.0.1-SNAPSHOT"
description = "Omoide Memory を GDrive からダウンロードする"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("org.postgresql:postgresql")
    jooqGenerator("org.jooq:jooq-meta-extensions")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// kotlin-logging 本体
	implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
	// ログの実装体 (例: Logback) も合わせて必要です
    implementation("ch.qos.logback:logback-classic")
    implementation(project(":omoide-memory-jooq"))
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// jOOQ 生成コードを kotlin コンパイルのソースセットに追加
kotlin {
    sourceSets {
        main {
            kotlin.srcDir("build/generated-sources/jooq")
        }
    }
}