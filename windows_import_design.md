# おもいでメモリ ダウンローダー実装仕様書

## 概要
家庭内で利用する Windows アプリケーション。Google Drive にアップロードされた写真・動画を取得し、ローカル PC にバックアップとして保存する。保存後、クラウド上のファイルは削除する。

---

## 技術スタック
- **言語**: Kotlin
- **フレームワーク**: Spring Boot 4.0.2
- **データベースアクセス**: jOOQ + R2DBC（非同期・リアクティブ）
- **データベース**: PostgreSQL 16
- **実行環境**: Windows 11
- **モノレポ構成**: Android アプリ（omoide-memory-android）と共存

---

## プロジェクト構成

```
omoide-memory/
├── omoide-memory-jooq/           # DDL管理・jOOQコード生成
├── omoide-memory-migration/      # Flywayマイグレーション実行
├── omoide-memory-android/        # Androidアプリ（既存）
└── omoide-memory-downloader/     # ← 今回実装するプロジェクト
    ├── src/main/kotlin/
    │   └── com/kasakaid/omoidememory/downloader/
    │       ├── OmoideMemoryDownloader.kt        # エントリーポイント（main関数）
    │       ├── DownloadFromGDrive.kt            # ApplicationRunner実装（ここを肉付け）
    │       ├── service/
    │       │   ├── GoogleDriveService.kt        # Google Drive API操作
    │       │   ├── FileOrganizeService.kt       # ファイル配置・振り分け
    │       │   ├── MetadataService.kt           # 画像/動画メタデータ取得
    │       │   ├── LocationService.kt           # Nominatim API経由で地名取得
    │       │   └── SyncedMemoryRepository.kt    # DB永続化（jOOQ + R2DBC）
    │       ├── config/
    │       │   ├── R2DBCConfiguration.kt        # R2DBC DSLContext設定
    │       │   └── GoogleDriveConfiguration.kt  # Google Drive API認証設定
    │       └── domain/
    │           ├── DownloadedFile.kt            # ダウンロード済みファイルのドメインモデル
    │           └── FileMetadata.kt              # メタデータのドメインモデル
    └── build.gradle.kts
```

---

## エントリーポイント: OmoideMemoryDownloader.kt

### 現在の実装（修正版）

```kotlin
package com.kasakaid.omoidememory.downloader

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class OmoideMemoryDownloader

const val APPLICATION_RUNNER_KEY = "omoide_application_runner"

fun main(args: Array<String>) {
    // 必要な環境変数チェック
    val destination = System.getenv("OMOIDE_BACKUP_DESTINATION")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_BACKUP_DESTINATION が設定されていません。ダウンロード先ディレクトリを指定してください。例: G:\\いつきくん")

    val postgresqlUrl = System.getenv("OMOIDE_POSTGRESQL_URL")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_POSTGRESQL_URL が設定されていません。例: r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory")

    val postgresqlUsername = System.getenv("OMOIDE_POSTGRESQL_USERNAME")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_POSTGRESQL_USERNAME が設定されていません。")

    val postgresqlPassword = System.getenv("OMOIDE_POSTGRESQL_PASSWORD")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_POSTGRESQL_PASSWORD が設定されていません。")

    // Google Drive API認証情報（OAuth2のcredentials.jsonのパス、またはサービスアカウントJSONのパス）
    val gdriveCredentialsPath = System.getenv("OMOIDE_GDRIVE_CREDENTIALS_PATH")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_GDRIVE_CREDENTIALS_PATH が設定されていません。Google Drive API認証情報のパスを指定してください。")

    val runnerName = System.getenv(APPLICATION_RUNNER_KEY)
        ?: throw IllegalArgumentException("環境変数 ${APPLICATION_RUNNER_KEY} が設定されていません。実行するApplicationRunnerの名前を指定してください。例: downloadFromGDrive")

    println("=== おもいでメモリ ダウンローダー 起動 ===")
    println("ダウンロード先: $destination")
    println("DB接続先: $postgresqlUrl")
    println("実行Runner: $runnerName")

    SpringApplicationBuilder(OmoideMemoryDownloader::class.java)
        .web(WebApplicationType.NONE) // Web不要（バッチ実行）
        .bannerMode(Banner.Mode.OFF)
        .run(*args)
}
```

### 環境変数一覧

| 変数名 | 説明 | 例 |
|---|---|---|
| `OMOIDE_BACKUP_DESTINATION` | ダウンロード先ディレクトリ | `G:\いつきくん` |
| `OMOIDE_POSTGRESQL_URL` | PostgreSQL接続URL（R2DBC形式） | `r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory` |
| `OMOIDE_POSTGRESQL_USERNAME` | PostgreSQLユーザー名 | `root` |
| `OMOIDE_POSTGRESQL_PASSWORD` | PostgreSQLパスワード | `root` |
| `OMOIDE_GDRIVE_CREDENTIALS_PATH` | Google Drive API認証情報のパス | `C:\credentials\gdrive-credentials.json` |
| `omoide_application_runner` | 実行するApplicationRunner名 | `downloadFromGDrive` |

---

## ApplicationRunner: DownloadFromGDrive.kt（肉付け対象）

### 実装すべき処理フロー

```kotlin
package com.kasakaid.omoidememory.downloader

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import com.kasakaid.omoidememory.downloader.service.*

private val logger = KotlinLogging.logger {}

@Component("downloadFromGDrive")
class DownloadFromGDrive(
    private val googleDriveService: GoogleDriveService,
    private val fileOrganizeService: FileOrganizeService,
    private val metadataService: MetadataService,
    private val locationService: LocationService,
    private val syncedMemoryRepository: SyncedMemoryRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) = runBlocking {
        logger.info { "Google Driveからのダウンロード処理を開始します" }

        try {
            // 1. Google Driveから対象フォルダ配下の全ファイルを取得
            val files = googleDriveService.listFiles()
            logger.info { "取得対象ファイル: ${files.size}件" }

            files.forEach { driveFile ->
                try {
                    // 2. ファイルをダウンロード
                    val downloadedFile = googleDriveService.downloadFile(driveFile)

                    // 3. メタデータを取得
                    val metadata = metadataService.extractMetadata(downloadedFile)

                    // 4. 位置情報から地名を取得（緯度経度がある場合のみ）
                    val locationName = if (metadata.latitude != null && metadata.longitude != null) {
                        locationService.getLocationName(metadata.latitude, metadata.longitude)
                    } else {
                        null
                    }

                    // 5. ファイルを年/月/picture or video の構造に配置
                    val finalPath = fileOrganizeService.organizeFile(downloadedFile, metadata)

                    // 6. DBに保存（jOOQ + R2DBC）
                    syncedMemoryRepository.save(
                        fileName = downloadedFile.name,
                        serverPath = finalPath.toString(),
                        metadata = metadata,
                        locationName = locationName
                    )

                    // 7. Google Drive上のファイルを削除
                    googleDriveService.deleteFile(driveFile.id)

                    logger.info { "処理完了: ${downloadedFile.name} -> $finalPath" }

                } catch (e: Exception) {
                    logger.error(e) { "ファイル処理中にエラーが発生: ${driveFile.name}" }
                    // 個別ファイルの失敗は継続（全体は止めない）
                }
            }

            logger.info { "全ファイルの処理が完了しました" }

        } catch (e: Exception) {
            logger.error(e) { "ダウンロード処理中に致命的なエラーが発生しました" }
            throw e
        }
    }
}
```

---

## サービス層の実装仕様

### 1. GoogleDriveService.kt

```kotlin
/**
 * Google Drive APIを使ってファイルの取得・ダウンロード・削除を行う
 */
interface GoogleDriveService {
    /**
     * 対象フォルダ配下の全ファイルをリスト取得
     * フォルダパスは環境変数または設定ファイルから取得
     */
    suspend fun listFiles(): List<DriveFile>

    /**
     * ファイルをダウンロードして一時ディレクトリに保存
     * 戻り値: ダウンロードしたファイルのローカルパスと元のファイル情報
     */
    suspend fun downloadFile(driveFile: DriveFile): DownloadedFile

    /**
     * Google Drive上のファイルを削除
     */
    suspend fun deleteFile(fileId: String)
}

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val createdTime: String?, // ISO 8601形式
)

data class DownloadedFile(
    val localPath: Path,
    val name: String,
    val mimeType: String,
    val driveFileId: String,
)
```

**実装メモ:**
- Google Drive API v3を使用
- 認証は `OMOIDE_GDRIVE_CREDENTIALS_PATH` から読み込んだ認証情報を使用
- OAuth2またはサービスアカウントどちらでも対応できるように実装
- ダウンロード先は `System.getProperty("java.io.tmpdir")` 配下の一時ディレクトリ

---

### 2. MetadataService.kt

```kotlin
/**
 * 画像・動画ファイルからメタデータを抽出する
 */
interface MetadataService {
    suspend fun extractMetadata(file: DownloadedFile): FileMetadata
}

data class FileMetadata(
    val captureTime: OffsetDateTime?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val deviceMake: String?,
    val deviceModel: String?,
    // 画像専用
    val aperture: Double?,
    val shutterSpeed: String?,
    val isoSpeed: Int?,
    val focalLength: Double?,
    val focalLength35mm: Int?,
    val whiteBalance: String?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val orientation: Int?,
    // 動画専用
    val durationSeconds: Double?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val frameRate: Double?,
    val videoCodec: String?,
    val videoBitrateKbps: Int?,
    val audioCodec: String?,
    val audioBitrateKbps: Int?,
    val audioChannels: Int?,
    val audioSampleRate: Int?,
    val thumbnailImage: ByteArray?,   // 動画の1秒目のサムネイル
    val thumbnailMimeType: String?,   // "image/jpeg"
    val fileSizeBytes: Long,
)
```

**実装メモ:**
- 画像の場合: `metadata-extractor` ライブラリを使用してEXIF情報を取得
- 動画の場合: `ffmpeg-cli-wrapper` または `jave2` を使用してメタデータ取得
- 動画サムネイル生成: ffmpegで1秒目のフレームをJPEGで抽出
  ```
  ffmpeg -i input.mp4 -ss 00:00:01 -vframes 1 thumbnail.jpg
  ```
- 撮影日時は EXIF の `DateTimeOriginal` または動画の `creation_time` から取得
- 取得できない項目は `null` で返す

---

### 3. LocationService.kt

```kotlin
/**
 * 緯度経度から地名を取得する（Nominatim API使用）
 */
interface LocationService {
    suspend fun getLocationName(latitude: Double, longitude: Double): String?
}
```

**実装メモ:**
- Nominatim APIエンドポイント: `https://nominatim.openstreetmap.org/reverse`
- リクエスト例:
  ```
  GET https://nominatim.openstreetmap.org/reverse?format=json&lat=35.6894&lon=139.6917&accept-language=ja
  ```
- レスポンスから `display_name` を取得
- レート制限: 1秒に1リクエストまで（必ず1秒の遅延を入れる）
- User-Agentヘッダーを設定: `OmoideMemoryDownloader/1.0`
- タイムアウト: 10秒
- エラー時は `null` を返して処理を継続

---

### 4. FileOrganizeService.kt

```kotlin
/**
 * ファイルを年/月/picture or video の構造に配置する
 */
interface FileOrganizeService {
    /**
     * ファイルを適切なディレクトリに移動・リネームして配置
     * 戻り値: 最終的な配置先のフルパス
     */
    suspend fun organizeFile(file: DownloadedFile, metadata: FileMetadata): Path
}
```

**実装メモ:**
- ベースディレクトリ: 環境変数 `OMOIDE_BACKUP_DESTINATION` から取得（例: `G:\いつきくん`）
- 撮影日時の取得優先順位:
  1. `metadata.captureTime`
  2. ファイル名から日付を抽出（例: `PXL_20230521_095441068.jpg` → 2023-05-21）
  3. ファイルの最終更新日時
- ディレクトリ構造:
  ```
  <baseDir>/
    └─ <year>/          # 例: 2023
        └─ <month>/     # 例: 05（ゼロ埋め2桁）
            ├─ picture/ # 画像ファイル
            └─ video/   # 動画ファイル
  ```
- picture / video の判定:
  - 画像: `.jpg`, `.jpeg`, `.png`, `.heic`, `.heif`, `.gif`, `.webp`
  - 動画: `.mp4`, `.mov`, `.avi`, `.mkv`, `.3gp`, `.webm`
- 既に同名ファイルが存在する場合:
  - ファイルハッシュ（SHA-256）を比較
  - 同一なら上書きしない（DBにも保存しない）
  - 異なる場合は末尾に `_1`, `_2` などの連番を付与
- ディレクトリが存在しない場合は自動作成

---

### 5. SyncedMemoryRepository.kt

```kotlin
/**
 * DBへの永続化（jOOQ + R2DBC）
 */
interface SyncedMemoryRepository {
    suspend fun save(
        fileName: String,
        serverPath: String,
        metadata: FileMetadata,
        locationName: String?
    )

    /**
     * ファイル名が既にDBに存在するか確認
     */
    suspend fun existsByFileName(fileName: String): Boolean
}
```

**実装メモ:**
- jOOQで生成された `SyncedOmoidePhoto` / `SyncedOmoideVideo` テーブルを使用
- 画像か動画かは `metadata` の内容で判定（`metadata.videoCodec` が null なら画像）
- R2DBC + jOOQ の非同期実行
- トランザクション管理は文書末尾の参照実装を参考に実装
- `created_by` は固定で `"downloader"` をセット

---

## R2DBC + jOOQ 設定

### R2DBCConfiguration.kt

```kotlin
package com.kasakaid.omoidememory.downloader.config

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.*
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class R2DBCConfiguration {

    @Bean
    fun connectionFactory(): ConnectionFactory {
        val url = System.getenv("OMOIDE_POSTGRESQL_URL")
            ?: throw IllegalArgumentException("OMOIDE_POSTGRESQL_URL not set")
        val username = System.getenv("OMOIDE_POSTGRESQL_USERNAME")
            ?: throw IllegalArgumentException("OMOIDE_POSTGRESQL_USERNAME not set")
        val password = System.getenv("OMOIDE_POSTGRESQL_PASSWORD")
            ?: throw IllegalArgumentException("OMOIDE_POSTGRESQL_PASSWORD not set")

        // r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory
        // からホスト・ポート・データベース名を抽出
        val regex = """r2dbc:postgresql://([^:]+):(\d+)/([^?]+)""".toRegex()
        val matchResult = regex.find(url) ?: throw IllegalArgumentException("Invalid R2DBC URL format")
        val (host, port, database) = matchResult.destructured

        return ConnectionFactories.get(
            builder()
                .option(DRIVER, "postgresql")
                .option(HOST, host)
                .option(PORT, port.toInt())
                .option(DATABASE, database)
                .option(USER, username)
                .option(PASSWORD, password)
                .option(CONNECT_TIMEOUT, Duration.ofSeconds(20))
                .build()
        ).let { R2DBCLoggingConnectionFactory(it) }
    }

    @Bean
    fun dslContext(connectionFactory: ConnectionFactory): DSLContext {
        return DSL.using(
            connectionFactory,
            SQLDialect.POSTGRES,
            DefaultConfiguration().apply {
                setSQLDialect(SQLDialect.POSTGRES)
            }.settings()
        )
    }
}
```

---

## 依存関係 (build.gradle.kts)

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    
    // jOOQ（モノレポの omoide-memory-jooq プロジェクトに依存）
    implementation(project(":omoide-memory-jooq"))
    
    // R2DBC PostgreSQL
    implementation("org.postgresql:r2dbc-postgresql")
    
    // Google Drive API
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    
    // メタデータ抽出
    implementation("com.drewnoakes:metadata-extractor:2.18.0") // 画像EXIF
    implementation("ws.schild:jave-core:3.3.1") // 動画メタデータ
    implementation("ws.schild:jave-nativebin-win64:3.3.1") // Windows用ffmpegバイナリ
    
    // HTTP Client（Nominatim API用）
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic")
}
```

---

## ファイル配置ルールの詳細

### ディレクトリ構造の例

```
G:\いつきくん\
├── 2023\
│   ├── 05\
│   │   ├── picture\
│   │   │   ├── PXL_20230521_095441068.jpg
│   │   │   └── SNOW_20230527_155202_347.jpg
│   │   └── video\
│   │       └── VID_20230515_183022.mp4
│   └── 12\
│       └── picture\
│           └── IMG_20231225_120000.jpg
└── 2024\
    └── 03\
        ├── picture\
        │   └── 20240315_143022.jpg
        └── video\
            └── VID_20240320_090000.mp4
```

---

## 重複防止の仕組み

1. **ファイル名での判定**
   - `SyncedMemoryRepository.existsByFileName(fileName)` でDB確認
   - 既に存在する場合はダウンロード・処理をスキップ

2. **ハッシュ値での判定（将来拡張）**
   - 現時点ではファイル名での判定のみ
   - 将来的に `file_hash` カラムを追加してハッシュ値（SHA-256）でも判定可能にする

---

## 実行方法

### 1. 環境変数の設定（Windows）

```cmd
set OMOIDE_BACKUP_DESTINATION=G:\いつきくん
set OMOIDE_POSTGRESQL_URL=r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory
set OMOIDE_POSTGRESQL_USERNAME=root
set OMOIDE_POSTGRESQL_PASSWORD=root
set OMOIDE_GDRIVE_CREDENTIALS_PATH=C:\credentials\gdrive-credentials.json
set omoide_application_runner=downloadFromGDrive
```

### 2. ビルド

```bash
./gradlew :omoide-memory-downloader:build
```

### 3. 実行

```bash
java -jar omoide-memory-downloader/build/libs/omoide-memory-downloader-0.0.1-SNAPSHOT.jar
```

### 4. Windowsタスクスケジューラで定期実行

- アクション: プログラムの開始
- プログラム: `java`
- 引数: `-jar C:\path\to\omoide-memory-downloader-0.0.1-SNAPSHOT.jar`
- 開始: バッチファイル経由で環境変数をセットしてから実行

---

## R2DBC トランザクション管理（参照実装）

以下は文書末尾に記載されているトランザクション管理の実装例です。

### CoroutineContextへのDSLContext追加

```kotlin
private const val KEY = "org.jooq.DSLContext"

fun CoroutineContext.addDSLContext(dslContext: DSLContext): CoroutineContext {
    val reactorContext = this[ReactorContext]
    return if (reactorContext == null) {
        this + ReactorContext(Context.of(KEY, dslContext))
    } else {
        this + reactorContext.context.put(KEY, dslContext).asCoroutineContext()
    }
}

fun CoroutineContext.getR2DBCContext(): DSLContext? = 
    this[ReactorContext]?.context?.getOrEmpty<DSLContext>(KEY)?.getOrNull()
```

### トランザクション実行

```kotlin
suspend fun <LEFT : TransactionRollbackException, RIGHT> executeWithPerLineLeftRollback(
    requestId: String,
    block: suspend CoroutineScope.() -> Either<LEFT, RIGHT>,
): Either<TransactionAttemptFailure, RIGHT> {
    val propagatedDSLContext = coroutineContext.getR2DBCContext()
    val outer = propagatedDSLContext?.dsl() ?: r2DBCDSLContext.get()
    return outer.transactionCoroutine { inner ->
        try {
            inner.dsl().transactionCoroutine { config ->
                withContext(MDCContext(mapOf("requestId" to requestId))) {
                    withContext(
                        context = coroutineContext.addDSLContext(dslContext = config.dsl()),
                        block = block,
                    ).fold(
                        ifLeft = {
                            throw it // ロールバックさせる
                        },
                        ifRight = { it.right() },
                    )
                }
            }
        } catch (e: CancellationException) {
            logger.error { "[requestId=$requestId] ${OneLineLogFormatter.toOneLine(e)}" }
            throw e
        } catch (e: TransactionRollbackException) {
            logger.warn { "明示的なロールバック [requestId=$requestId] ${OneLineLogFormatter.toOneLine(e)}" }
            e.left()
        } catch (e: Throwable) {
            logger.error { "[requestId=$requestId] ${OneLineLogFormatter.toOneLine(e)}" }
            TransactionAttemptFailure.Unmanaged(e).left()
        }
    }
}
```

### ロギング実装

```kotlin
class R2DBCLoggingConnectionFactory(
    private val delegate: ConnectionFactory,
) : ConnectionFactory by delegate {
    override fun create(): Publisher<out Connection> = 
        Mono.from(delegate.create()).map { conn -> R2DBCLoggingConnection(conn) }
}

class R2DBCLoggingConnection(
    private val delegate: Connection,
) : LoggingConnection(delegate) {
    private val log = KotlinLogging.logger {}

    override fun createStatement(sql: String): Statement {
        val original = delegate.createStatement(sql)
        return R2DBCLoggingStatement(
            delegate = original,
            sql = sql,
            log = log,
        )
    }
}

class R2DBCLoggingStatement(
    private val delegate: Statement,
    private val sql: String,
    private val log: KLogger,
) : Statement by delegate {
    private val bindsByIndex = LinkedHashMap<Int, Any?>()

    override fun bind(index: Int, value: Any): Statement {
        bindsByIndex[index] = value
        delegate.bind(index, value)
        return this
    }

    override fun execute(): Publisher<out Result> {
        val binds = bindsByIndex
        log.info { "$sql, ${binds.entries.joinToString(prefix = "[", postfix = "]") { (k, v) -> "$k=$v" }}" }
        return delegate.execute()
    }
}
```

---

## エラーハンドリング方針

1. **個別ファイルの処理エラー**
   - ログに記録して次のファイルに進む（全体の処理は継続）
   - Google Drive上のファイルは削除しない（次回の実行時に再試行）

2. **致命的なエラー**
   - DB接続エラー、Google Drive API認証エラーなど
   - 処理を即座に停止して例外をスロー
   - Windowsタスクスケジューラでエラーを検知できるようにする

3. **リトライ対象**
   - Nominatim APIのレート制限エラー → 2秒待ってリトライ（最大3回）
   - 一時的なネットワークエラー → 指数バックオフで最大3回リトライ

---

## テスト方針

1. **単体テスト**
   - 各Serviceの動作確認
   - モックを使用してGoogle Drive APIやDB接続を代替

2. **統合テスト**
   - TestcontainersでPostgreSQLを起動
   - Google Drive APIはモックサーバー（WireMock）で代替

3. **手動テスト**
   - 小規模なGoogle Driveフォルダで実際にダウンロード→配置→DB保存→削除の一連の流れを確認

---

## 今後の拡張予定

1. **定期実行のスケジューラ組み込み**
   - Spring Schedulerで毎日深夜2時に自動実行

2. **進捗通知**
   - Slack / Lineへの通知機能

3. **差分同期**
   - 変更されたファイルのみを再ダウンロード

4. **複数のクラウドストレージ対応**
   - Dropbox、OneDriveなどへの対応

---

## 実装時の注意事項

- Google Drive APIの認証は初回実行時にブラウザで認証フローが発生する可能性があります。サービスアカウントを使用する場合はこの問題を回避できます。
- ffmpegはJAVE2ライブラリにバンドルされていますが、システムにインストールされているffmpegを優先的に使用することもできます。
- Nominatim APIは無料ですがレート制限が厳しいため、必ず1秒以上の間隔を空けてリクエストしてください。
- PostgreSQLの接続情報は環境変数から読み込みますが、将来的には `application.yml` に移行することも検討してください。

---

以上が実装仕様書です。この仕様をもとにコードを生成してください。
