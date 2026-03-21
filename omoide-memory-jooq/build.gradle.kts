import org.jooq.meta.jaxb.Property

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("io.spring.dependency-management") version "1.1.7"
    id("nu.studer.jooq") version "9.0"
    `java-library`
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.2")
    }
}

group = "com.kasakaid"
version = ""
description = "Omoide Memory の JOOQ (DB アクセスリソース)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// 日本語が入ってしまうと上手くビルドができないのでプロジェクト名を明示的にセット
tasks.jar {
    archiveBaseName.set("omoide-memory-jooq")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.2.0")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    jooqGenerator("org.jooq:jooq-meta-extensions")
    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    implementation("com.github.spotbugs:spotbugs-annotations:4.8.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // kotlin-logging 本体
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    // ログの実装体 (例: Logback) も合わせて必要です
    implementation("ch.qos.logback:logback-classic")

    // R2DBC
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    api("io.arrow-kt:arrow-core:2.2.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
    implementation("org.jooq:jooq-kotlin-coroutines")

    // Jackson
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jooq {
    version = dependencyManagement.importedProperties["jooq.version"] // Spring Boot管理バージョンに追従
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation = false
            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator" // Kotlin コード生成
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase" // DDLからコード生成（DB不要）
                        properties.add(
                            Property().apply {
                                key = "scripts"
                                // Flyway マイグレーションファイルをすべて読み込む
                                value = "src/main/resources/db/migration/*.sql"
                            },
                        )
                        properties.add(
                            Property().apply {
                                key = "sort"
                                value = "flyway" // Vxx__yyy.sql 順でソート
                            },
                        )
                        properties.add(
                            Property().apply {
                                key = "defaultNameCase"
                                value = "lower"
                            },
                        )
                    }
                    target.apply {
                        packageName = "com.kasakaid.omoidememory.jooq" // 生成先パッケージ
                        // プラグイン標準の構成（build/generated-sources/jooq/main）に合わせる
                        directory =
                            project.layout.buildDirectory
                                .dir("generated-sources/jooq/main")
                                .get()
                                .asFile.path
                    }
                    generate.apply {
                        isKotlinNotNullRecordAttributes = true
                        isKotlinNotNullPojoAttributes = true
                        isDaos = true
                        isPojos = true // POJOクラスも生成
                        isImmutablePojos = true // イミュータブルPOJO
                        isFluentSetters = true
                        isRecords = true // Kotlin data classライクなRecord
                        isDeprecated = false
                        isNullableAnnotation = true // @Nullable アノテーション付与
                        isNonnullAnnotation = true // @NonNull アノテーション付与
                        isComments = true
                        isCommentsOnColumns = true
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(project.layout.buildDirectory.dir("generated-sources/jooq/main"))
        }
    }
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(project.layout.buildDirectory.dir("generated-sources/jooq/main"))
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateJooq")
}

val deleteBin by tasks.registering(Delete::class) {
    delete("bin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(deleteBin)
}
