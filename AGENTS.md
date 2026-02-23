# AGENTS.md - Kotlin プロジェクト開発ガイドライン

このドキュメントは、Kotlinプロジェクト（サーバーサイド・モバイル・Webフロント）におけるコーディング規約、アーキテクチャ、技術選定の指針を定めたものです。

---

## 技術スタック

### サーバーサイド・Web API

| 技術 | 用途 |
|---|---|
| **Spring Boot** | アプリケーションフレームワーク |
| **jOOQ** | 型安全なSQL生成（コード生成はDDLから） |
| **R2DBC** | 非同期・リアクティブなDB接続 |
| **WebFlux** | 非同期Web API（Web APIの場合のみ） |
| **PostgreSQL** | データベース |
| **Kotlin Coroutines** | 非同期・並行処理 |
| **Arrow-kt** | 関数型プログラミング（Either, Option等） |

### モバイル（Android）

| 技術 | 用途 |
|---|---|
| **Jetpack Compose** | 宣言的UI |
| **Dagger Hilt** | 依存性注入 |
| **Room** | ローカルDB |
| **Kotlin Coroutines + Flow** | 非同期処理・リアクティブストリーム |

### ブラウザアプリ（Web フロントエンド）

| 技術 | 用途 |
|---|---|
| **TypeScript** | 型安全な開発 |
| **React 19.2** | UIライブラリ |
| **React Hooks** | 状態管理・副作用 |
| **Zod** | スキーマバリデーション |

---

## コーディング原則

### 1. Cライクな変数代入を避ける

❌ **悪い例（一時変数の濫用）**:

```kotlin
val userName = user.name
val userAge = user.age
val userEmail = user.email

createUserDto(userName, userAge, userEmail)
```

✅ **良い例（名前付き引数で宣言的に）**:

```kotlin
createUserDto(
    name = user.name,
    age = user.age,
    email = user.email,
)
```

**ルール**:
- 一度しか使わない値をDTOから変数に入れない
- 二度以上使う場合でも、単純な値参照のみなら変数に入れない
- インスタンス生成時は名前付き引数を使い、宣言的に書く

---

### 2. イミュータブル原則

❌ **悪い例（可変リスト）**:

```kotlin
val results = mutableListOf<String>()
users.forEach { user ->
    results.add(user.name)
}
```

✅ **良い例（map使用）**:

```kotlin
val results = users.map { it.name }
```

❌ **悪い例（forEachでの横持ち）**:

```kotlin
var total = 0
items.forEach { item ->
    total += item.price
}
```

✅ **良い例（fold使用）**:

```kotlin
val total = items.fold(0) { acc, item -> acc + item.price }
```

**ルール**:
- 値の再代入を禁止（`var`ではなく`val`）
- `forEach`によるリスト作成ではなく`map`を使う
- `forEach`によるListの横持ちは`fold`を使う

---

### 3. DRY原則の徹底

#### jOOQでの再利用

✅ **良い例（カラム定義の再利用）**:

```kotlin
private fun updateColumns(entity: UserEntity): Map<Field<*>, Any?> = mapOf(
    USER.NAME to entity.name,
    USER.EMAIL to entity.email,
    USER.UPDATED_AT to OffsetDateTime.now(),
)

private fun insertColumns(entity: UserEntity): Map<Field<*>, Any?> =
    updateColumns(entity) + mapOf(
        USER.CREATED_AT to OffsetDateTime.now(),
    )
```

#### DSL構築の関数化（Dao代替）

✅ **良い例（拡張関数でクエリを再利用）**:

```kotlin
fun DSLContext.getUser(userId: UserId):  SelectConditionStep<User> =
    this.selectFrom(USER)
        .where(USER.ID.eq(userId.value))

```

**ルール**:
- INSERT/UPDATEで重複するカラムは関数化して再利用
- DSL構築部分を拡張関数で関数化し、Daoの代替として使う

---

### 4. 並行処理

#### DB層とWeb API層はCoroutineで実装

```kotlin
// DB層
suspend fun findUser(userId: UserId): Either<DbError, User> = either {
    dslContext.get()
        .getUser(userId)
        ?.let { it.into(User::class.java) }
        ?.right()
        ?: UserNotFound.left()
}.bind()

// Web API層（WebFlux）
@GetMapping("/users/{id}")
suspend fun getUser(@PathVariable id: String): ResponseEntity<UserDto> {
    return userService.findUser(UserId(id))
        .fold(
            ifLeft = { ResponseEntity.notFound().build() },
            ifRight = { user -> ResponseEntity.ok(user.toDto()) }
        )
}
```

**ルール**:
- DB層: R2DBC + Coroutines
- Web API層: WebFlux + Coroutines（またはKtor）

---

### 5. ADT（代数的データ型）を好む

❌ **悪い例（if分岐とnull）**:

```kotlin
fun processFile(file: File): String {
    if (file.isValid()) {
        return "OK"
    } else {
        return "ERROR"
    }
}
```

✅ **良い例（sealed interface + パターンマッチ）**:

```kotlin
sealed interface FileProcessResult {
    object Success : FileProcessResult
    data class Error(val reason: String) : FileProcessResult
}

fun processFile(file: File): FileProcessResult {
    return if (file.isValid()) {
        FileProcessResult.Success
    } else {
        FileProcessResult.Error("Invalid file format")
    }
}

// 使用側
when (val result = processFile(file)) {
    is FileProcessResult.Success -> logger.info { "成功" }
    is FileProcessResult.Error -> logger.error { "失敗: ${result.reason}" }
}
```

**ルール**:
- 分岐・パターン・状態が発生する場合は`sealed interface`を定義
- `if`やOOPの継承は使わず、パターンマッチで分岐を表現
- 何が起きうるかを型で明示

---

### 6. Arrow-ktの活用

#### Either（エラーハンドリング）

✅ **良い例**:

```kotlin
suspend fun downloadFile(fileId: String): Either<DownloadError, File> = either {
    val metadata = fetchMetadata(fileId).bind()
    val content = fetchContent(fileId).bind()
    createFile(metadata, content).bind()
}

// 使用側
downloadFile("abc123")
    .onLeft { error ->
        logger.error { "ダウンロード失敗: ${error.message}" }
    }
    .onRight { file ->
        logger.info { "ダウンロード成功: ${file.name}" }
    }
```

#### Option（値の有無）

```kotlin
sealed interface MediaType {
    object Photo : MediaType
    object Video : MediaType

    companion object {
        fun of(fileName: String): Option<MediaType> {
            val ext = fileName.substringAfterLast(".", "")
            return when (ext.lowercase()) {
                "jpg", "png" -> Photo.some()
                "mp4", "mov" -> Video.some()
                else -> None
            }
        }
    }
}

// 使用側
MediaType.of("photo.jpg").fold(
    ifEmpty = { logger.warn { "未対応の形式" } },
    ifSome = { type -> processMedia(type) }
)
```

**ルール**:
- エラー時は`Either`を使い、Leftが来たらログを出す・スキップするなど、何が起きるかを明示
- 「ある・ない」がデータ的に存在しない場合は`null`を使う
- 変換できる・できないのような場合は`Option`を使う

---

### 7. 中間オブジェクトを作らない

❌ **悪い例（中間DTOを挟む）**:

```kotlin
// ImageMetadataReader -> DTO -> ドメインモデル
val exifData = ImageMetadataReader.readMetadata(file)
val dto = ExifDto(
    captureTime = exifData.captureTime,
    latitude = exifData.latitude,
    // ...
)
val photo = Photo.from(dto)
```

✅ **良い例（直接マッピング）**:

```kotlin
// ImageMetadataReader -> ドメインモデル
val exifData = ImageMetadataReader.readMetadata(file)
val photo = Photo(
    captureTime = exifData.captureTime,
    latitude = exifData.latitude,
    // ...
)
```

**例外**: jOOQのPojo/Recordは使用する

```kotlin
// jOOQのRecordは使う（型安全のため）
val userRecord = dslContext.selectFrom(USER).fetchOne()
val user = userRecord.into(User::class.java)
```

**ルール**:
- 入力からドメインモデルへの変換は直接行い、中間DTOを作らない
- ただし、jOOQのPojo/Recordは型安全のために使用する

---

### 8. data classを控える

❌ **悪い例（大量データ処理でdata class）**:

```kotlin
data class LogEntry(val timestamp: Long, val message: String, val level: String)

val logs = mutableListOf<LogEntry>()
for (i in 0..1_000_000) {
    logs.add(LogEntry(System.currentTimeMillis(), "message", "INFO"))
}
```

✅ **良い例（通常のclass）**:

```kotlin
class LogEntry(val timestamp: Long, val message: String, val level: String)

val logs = (0..1_000_000).map {
    LogEntry(System.currentTimeMillis(), "message", "INFO")
}
```

**ルール**:
- `data class`はequals/hashCode/toString/copyを自動生成するため、メモリを多く消費
- forループが発生するような大量データ処理では`data class`を使わない
- 小規模なDTO・値オブジェクトでは`data class`を使ってOK

---

## アーキテクチャ

### ディレクトリ構成（クリーンアーキテクチャ）

```
src/main/kotlin/com/example/project/
├── adapter/
│   ├── DownloadFromGDrive.kt          # ApplicationRunner（バッチエントリーポイント）
│   └── UserController.kt              # REST Controller（Web APIエントリーポイント）
├── service/
│   ├── DownloadFileBackupService.kt   # アプリケーションサービス（処理フロー）
│   └── UserService.kt
├── domain/
│   ├── model/
│   │   ├── User.kt                    # ドメインモデル
│   │   ├── UserId.kt                  # 値オブジェクト
│   │   └── FileProcessResult.kt       # ADT（sealed interface）
│   └── repository/
│       └── UserRepository.kt          # Repositoryインターフェース
└── infrastructure/
    └── UserRepositoryImpl.kt          # Repository実装（jOOQ + R2DBC）
```

### 各層の責務

#### adapter層
- **役割**: 外界とのI/O、エントリーポイント
- **バッチ**: `ApplicationRunner`を実装し、Spring Boot起動時のエントリーポイントとなる
- **Web API**: REST Controllerを配置

```kotlin
@Component
@ConditionalOnProperty(name = ["runner_name"], havingValue = "download-from-gdrive")
class DownloadFromGDrive(
    private val downloadService: DownloadFileBackupService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) = runBlocking {
        downloadService.execute()
    }
}
```

#### service層
- **役割**: アプリケーションサービス、処理フロー
- **重要**: ADTによるパターンマッチや`Either`の結果を書き、**処理フロー上何が起きるか**を明示

```kotlin
@Service
class DownloadFileBackupService(
    private val driveService: DriveService,
    private val repository: SyncedMemoryRepository,
) {
    suspend fun execute(file: File): Either<DownloadError, DownloadResult> = either {
        // 既存チェック
        if (repository.exists(file.name)) {
            return DownloadResult.Skip("既に存在").right()
        }

        // ダウンロード
        val localFile = driveService.download(file).bind()

        // メタデータ抽出
        val metadata = extractMetadata(localFile).bind()

        // 保存
        repository.save(metadata).bind()

        DownloadResult.Success
    }
}
```

#### domain/model層
- **役割**: ドメインモデル、値オブジェクト、ADT
- **配置**: Repositoryインターフェース、ドメインロジック

// 値オブジェクト
# 悪い例
JvmInline はデフォルト値をつかうと、意図せぬ動きをする場合があるので、利用しない

```kotlin
@JvmInline
value class UserId(val value: String)
```

# 良い例
```kotlin
class UserId(val value: String)
```

```kotlin

// ドメインモデル
class User(
    val id: UserId,
    val name: String,
    val email: String,
)

// ADT
sealed interface DownloadResult {
    object Success : DownloadResult
    data class Skip(val reason: String) : DownloadResult
}
```

#### infrastructure層
- **役割**: DB層へのSQL実装（jOOQ + R2DBC）

```kotlin
@Repository
class UserRepositoryImpl(
    private val dslContext: R2DBCDSLContext,
) : UserRepository {

    override suspend fun findById(userId: UserId): Either<UserNotFound, User> = either {
        dslContext.get()
            .getUser(userId.value)
            .awaitSingleOrNull()
            ?.into(User::class.java)
            ?.right()
            ?: UserNotFound.left()
    }.bind()
}
```

---

## モバイル・Webフロント側のルール

### コンポーネント設計原則

❌ **悪い例（大きな単一コンポーネント）**:

```tsx
// UserPage.tsx（500行）
export const UserPage = () => {
    // ヘッダー、サイドバー、メインコンテンツ、フッターが全部入っている
    return <div>...</div>
}
```

✅ **良い例（小さなコンポーネントの組み合わせ）**:

```tsx
// UserPage.tsx
export const UserPage = () => {
    return (
        <>
            <Header />
            <Sidebar />
            <UserContent />
            <Footer />
        </>
    )
}

// UserContent.tsx
export const UserContent = () => {
    return (
        <div>
            <UserProfile />
            <UserActivity />
        </div>
    )
}
```

**ルール**:
- 大きなコンポーネントを一つ作るのではなく、小さなコンポーネントをルートページが利用する設計
- 各コンポーネントは単一責任の原則に従う
- 再利用可能な粒度で分割する

---

