import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.kasakaid.omoidememory"
    compileSdk = 34
    packaging {
        resources {
            // 重複したメタファイルを除外または最初の一つを選択するように設定
            // INDEX.LIST のほか、Google系でよく出る重複もついでにケアしておくと安全です
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/LICENSE*"
            pickFirsts += "META-INF/NOTICE*"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
    }

    defaultConfig {
        applicationId = "com.kasakaid.omoidememory"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 1. local.properties を読み込む準備
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        // 1. フォルダIDの設定
        localProperties.getProperty("omoide.folder.id")?.let { id ->
            buildConfigField("String", "OMOIDE_FOLDER_ID", "\"$id\"")
        } ?: throw IllegalStateException("local.properties: 'omoide.folder.id' が未定義です")

        // buildConfigField: アプリが実行時に「どのファイル名」を開けばいいかを知るために必要。
        buildFeatures {
            buildConfig = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // メタデータの不整合エラーを無視させる魔法の引数
        // 以下のメタ情報不整合のエラーが出るために
        // どうも、Android Studio が androidx.compose.remote:remote-creation-core:1.0.0-alpha04 をつれてきてしまうためみたい、Live Edit 機能のためとkで
        // file:///~/.gradle/caches/modules-2/files-2.1/androidx.compose.remote/remote-creation-core/1.0.0-alpha04/b7a08b52fb581d744610b023544e7372a6a41cd9/remote-creation-core-1.0.0-alpha04.jar!/META-INF/remote-creation-core.kotlin_moduleModule was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.1.0, expected version is 1.9.0.
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Compatible with Kotlin 1.9.24
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

// Android Studio で K2 モードは無効にすること
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("1.9.24") // Android 側の Kotlin バージョンに合わせる
            }
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.04.00")) // Stable BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Google Play Services / Drive
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:2.8.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    // Upload 時のクレデンシャルセット
    implementation("com.google.auth:google-auth-library-oauth2-http:1.43.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0") // バージョンは本体と合わせる
    implementation("io.coil-kt:coil-gif:2.7.0")

    // ArrowKt
    implementation("io.arrow-kt:arrow-core:1.2.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
