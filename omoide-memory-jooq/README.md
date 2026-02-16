# omoide-memory-jooq

おもいでメモリアプリの **jOOQ クラス生成** を担うプロジェクトです。

DDL（テーブル定義SQL）を管理し、そこから型安全なjOOQクラスを生成します。
他プロジェクトはこのプロジェクトにのみ依存することで、Flywayや起動処理の副作用なしにjOOQクラスを利用できます。

---

## モノレポ内での位置づけ

```
omoide-memory/
    ├── omoide-memory-jooq/        ← このプロジェクト
    │       └── src/main/resources/db/migration/  ← DDLの正本
    └── omoide-memory-migration/
```

**DDLの正本はこのプロジェクトが持ちます。**
`omoide-memory-migration` はこのプロジェクトのDDLをコピーして使用します。
DDLを変更する場合は必ずこのプロジェクトのファイルを編集してください。

---

## テーブル構成

| テーブル名 | 概要 |
|---|---|
| `synced_omoide_photo` | 同期済み写真メタ情報（EXIF情報含む） |
| `synced_omoide_video` | 同期済み動画メタ情報（コーデック・サムネイル含む） |
| `comment_omoide_photo` | 写真に対するコメント |
| `comment_omoide_video` | 動画に対するコメント |

写真と動画でメタ情報が異なるためテーブルを分割しています（写真はEXIFカメラ設定、動画はコーデック・fps等）。
コメントも同様に分割することでクエリ時の不要なJOINを排除しています。

### DDL ファイル構成

```
src/main/resources/db/migration/
    ├── V1__create_synced_omoide_photo.sql
    ├── V2__create_synced_omoide_video.sql
    ├── V3__create_comment_omoide_photo.sql
    └── V4__create_comment_omoide_video.sql
```

---

## 必要な環境

| ツール | バージョン                 |
|---|-----------------------|
| JDK | 17                    |
| Kotlin | 2.2.x                 |
| Spring Boot | 4.0.2                 |
| jOOQ | 3.19.x（Spring Boot管理） |

---

## jOOQ コード生成

```bash
./gradlew generateJooq
```

`src/main/resources/db/migration/` 以下のSQLファイルを読み込み、以下にソースコードを生成します。

```
build/generated-sources/jooq/
    └── com/example/omoide/jooq/
            ├── tables/          # テーブルクラス
            ├── tables/records/  # Record クラス
            └── tables/pojos/    # POJO クラス
```

> **DBへの接続は不要です。** `DDLDatabase` を使用しているためFlywayのSQLファイルから直接生成します。

### IntelliJ でのビルド設定について

IntelliJ のビルドを Gradle に設定すると **ビルドが遅くなります**。
以下の設定を推奨します。

```
Settings > Build, Execution, Deployment
    > Build Tools > Gradle
        > Build and run using : IntelliJ IDEA
        > Run tests using     : IntelliJ IDEA
```

jOOQ の生成だけ `./gradlew generateJooq` で行い、通常のビルド・テストは IntelliJ に任せるのがベストプラクティスです。

---

## 他プロジェクトからの利用方法

### 1. 依存の追加

```kotlin
dependencies {
    implementation(project(":omoide-memory-jooq"))
}
```

### 2. Repository の実装例

```kotlin
@Repository
class PhotoRepository(private val dsl: DSLContext) {

    fun findByFileName(fileName: String) =
        dsl.selectFrom(SYNCED_OMOIDE_PHOTO)
            .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName))
            .fetchOneInto(SyncedOmoidePhoto::class.java)

    fun findAll() =
        dsl.selectFrom(SYNCED_OMOIDE_PHOTO)
            .fetchInto(SyncedOmoidePhoto::class.java)
}
```

`SYNCED_OMOIDE_PHOTO` などのクラスはすべてこのプロジェクトが生成したjOOQクラスです。
SQLを文字列で書く必要がなく、**型安全なクエリ**を実装できます。

---

## DDL を変更したいとき

1. このプロジェクトの `src/main/resources/db/migration/` に新しいSQLファイルを追加する
    - ファイル名は `V{次の番号}__{変更内容}.sql` の形式
    - 例: `V5__add_column_synced_omoide_photo.sql`
2. `./gradlew generateJooq` を実行してjOOQクラスを再生成する
3. `omoide-memory-migration` を起動してマイグレーションを適用する
4. 依存している他プロジェクトを再ビルドする

> **既存のDDLファイルは絶対に編集しないでください。**
> Flywayはチェックサムで整合性を検証するため、編集すると `omoide-memory-migration` 起動時にエラーになります。