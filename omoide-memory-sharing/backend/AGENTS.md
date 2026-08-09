# AGENTS.md - サーバーサイド (Kotlin / Spring Boot) 開発ガイドライン

本ドキュメントは、サーバーサイド・Web API（Kotlin / Spring Boot）開発におけるコーディング規約、アーキテクチャ、技術選定の指針を定めたものです。

---

## 技術スタック

| 技術 | 用途 |
|---|---|
| **Spring Boot** | アプリケーションフレームワーク |
| **jOOQ** | 型安全なSQL生成（コード生成はDDLから） |
| **R2DBC** | 非同期・リアクティブなDB接続 |
| **WebFlux** | 非同期Web API（Web APIの場合のみ） |
| **PostgreSQL** | データベース |
| **Kotlin Coroutines** | 非同期・並行処理 |
| **Arrow-kt** | 関数型プログラミング（Either, Option等） |

---

## コーディング原則

### 1. Cライクな一時変数の代入・濫用を避ける

❌ **悪い例（一度しか使わない一時変数の宣言・代入）**:

```kotlin
val userName = user.name
val userAge = user.age
val userEmail = user.email

createUserDto(userName, userAge, userEmail)
```

✅ **良い例（一時変数を作らず名前付き引数を代替として使い宣言的に書く）**:

```kotlin
createUserDto(
    name = user.name,
    age = user.age,
    email = user.email,
)
```

**ルール**:
- **一度しか使わない値をローカル変数（一時変数）に代入しない**（関数呼び出しやコンストラクタ引数へ直接渡す）
- **名前付き引数を一時変数の代替として活用する**: 可読性を上げるために一時変数を作るのではなく、呼び出し側で名前付き引数（`name = user.name` など）を使うことで意図を明確にしつつ中間変数を排除する
- 二度以上使う場合でも、単純なプロパティ参照のみなら中間変数を作らず直接参照する

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
- `forEach`による副作用・繰り返し処理を避け、`map` / `mapNotNull` を優先して使用する
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
fun DSLContext.getUser(userId: UserId): SelectConditionStep<User> =
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
- DB層: R2DBC + Coroutines (または Reactive/Reactor)
- Web API層: WebFlux + Coroutines（またはKtor）
- **同期クエリの禁止**: R2DBC 環境であるため、jOOQ の `fetchOne()`, `fetch()`, `execute()` などの同期・ブロッキングメソッドの呼び出しは禁止（`DetachedException` が発生する）。クエリ実行時は Coroutines の非同期ストリームや Kotlin コレクション操作を使用すること。

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
- 分岐・パターン・状態が発生する場合は `sealed interface` を定義
- `if` や OOP の継承は使わず、パターンマッチで分岐を表現
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
- エラー時は `Either` を使い、Leftが来たらログを出す・スキップするなど、何が起きるかを明示
- 「ある・ない」がデータ的に存在しない場合は `null` を使う
- 変換できる・できないのような場合は `Option` を使う

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

### 8. KDoc ドキュメントコメントの徹底

一見して意図や背景が分かりにくい処理・マジックナンバー・変換ロジックには、必ず KDoc（`/** ... */`）による説明と名前付き定数化を行ってください。

❌ **悪い例（意図が不明な数値や直書きロジック）**:

```kotlin
val count = minOf(1024 * 1024L, contentLength)
val region = ResourceRegion(resource, 0, count)
```

✅ **良い例（定数化 + KDoc による意図の明記）**:

```kotlin
/**
 * 1回のレスポンスでクライアントへ返す動画ストリームの最大チャンクサイズ（1 MB = 1024 * 1024 バイト）。
 * 動画全体を一括でメモリに読み込まず、1MB ごとの部分領域（ResourceRegion）に分割して配信することで
 * メモリ消費を抑え、スムーズな初期再生開始を実現します。
 */
private const val CHUNK_SIZE_1MB: Long = 1024 * 1024L

val count = minOf(CHUNK_SIZE_1MB, contentLength)
val region = ResourceRegion(resource, 0, count)
```

**ルール**:
- パッと見で役割がわかりづらい数値・条件式・計算式（ビット演算、バイト数計算、特殊な閾値など）は、意味が伝わる定数名で定義する
- クラス・コンポーネント・変換クラス（Translator 等）や非直感的な処理には、KDoc で背景や意図（「なぜそうしているか」）を明記する

---

### 9. 制御結合（Control Coupling）の回避

フラグ変数や状態判定用パラメータを渡してメソッド内部の挙動を分岐させる「制御結合」を禁止します。

❌ **悪い例（フラグや状態を渡して内部で分岐・判定させる）**:

```kotlin
fun processFile(file: File, isVideo: Boolean) {
    if (isVideo) {
        processVideo(file)
    } else {
        processImage(file)
    }
}
```

✅ **良い例（単一責任のメソッドに分け、呼び出し元が適切なメソッドを選択する）**:

```kotlin
fun processVideo(file: File) { ... }
fun processImage(file: File) { ... }
```

**ルール**:
- メソッドに Boolean フラグや nullable な制御パラメータを渡して内部動作を分岐させない
- 呼び出し元で分かっている状態・判定結果は呼び出し先へ持ち込まず、専用の単一責任メソッドを分けて呼び出す

---

### 10. data class を控える

❌ **悪い例（大量データ処理でdata class）**:

```kotlin
data class LogEntry(val timestamp: Long, val message: String, val level: String)
```

✅ **良い例（通常のclass）**:

```kotlin
class LogEntry(val timestamp: Long, val message: String, val level: String)
```

**ルール**:
- `data class` は equals/hashCode/toString/copy を自動生成するためメモリを多く消費する
- forループや大容量データ処理では `data class` を使わない（小規模DTOでは使用OK）

---

## アーキテクチャ

### ディレクトリ構成（クリーンアーキテクチャ）

```
src/main/kotlin/com/example/project/
├── adapter/                           # Controller, ApplicationRunner
├── service/
│   ├── command                        # アプリケーションサービス（コマンド）
│   └── query                          # アプリケーションサービス（クエリ）
├── domain/
│   ├── model/                         # ドメインモデル, 値オブジェクト (コマンド用)
│   └── repository/                    # Repository インターフェース
└── infrastructure/                    # Repository 実装 (jOOQ + R2DBC)
```

### クエリの原則
- **SQL関数（`MAX`, `MIN`, `COUNT`, `COALESCE` 等）をクエリ内で直接多用しない**
- 集計・計算・データ結合などの加工は Kotlin 側で行う
- CQS を遵守し、Query サービスはドメインモデルに依存せず jOOQ の POJO/Record を直接扱う
