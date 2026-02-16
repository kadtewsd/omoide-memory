# omoide-memory-migration

おもいでメモリアプリの **Flywayマイグレーション実行** を担うプロジェクトです。

`omoide-memory-jooq` が管理するDDLをコピーしてから Flyway でマイグレーションを実行します。
このプロジェクトは単独で起動するためのものです。他プロジェクトはこのプロジェクトに依存しないでください。

---

## モノレポ内での位置づけ

```
omoide-memory/
    ├── docker-compose.yml
    ├── omoide-memory-jooq/        ← DDLの正本・jOOQクラス生成
    └── omoide-memory-migration/   ← このプロジェクト（マイグレーション実行）
```

**DDLの正本は `omoide-memory-jooq` が持ちます。**
このプロジェクトは起動時に `omoide-memory-jooq` のDDLをコピーしてからFlywayを実行します。
DDLを変更したい場合は `omoide-memory-jooq` のファイルを編集してください。

---

## 必要な環境

| ツール | バージョン                  |
|---|------------------------|
| JDK | 17                     |
| Kotlin | 2.2.x                  |
| Spring Boot | 4.0.2                  |
| PostgreSQL | 16（docker-compose で起動） |

PostgreSQLの起動は `omoide-memory/` 直下の `docker-compose.yml` を使用します。

---

## マイグレーションの実行手順

### 1. PostgreSQL を起動する

`omoide-memory/` 直下で実行します。

```bash
cd ../
docker compose up -d
```

### 2. マイグレーションを実行する

```bash
# ローカル環境のプロファイルを指定
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

起動時に以下の順序で処理されます。

```
1. omoide-memory-jooq の DDL を src/main/resources/db/migration/ にコピー
2. Flyway マイグレーションを1ファイルずつ順次実行
3. 全マイグレーション完了後にアプリ終了
```

---

## マイグレーションの仕組み

### DDL のコピー

`main` 関数起動時に `MigrationSqlCopier` が隣の `omoide-memory-jooq` からSQLファイルをコピーします。

```
../omoide-memory-jooq/src/main/resources/db/migration/*.sql
    ↓ コピー
src/main/resources/db/migration/
```

コピー元が存在しない場合は即座にクラッシュします。モノレポ外での単独実行は想定していません。

### Flyway の実行戦略

マイグレーションは **1ファイルずつ順番に実行** します。
1ファイルでも失敗した場合はその時点で停止し、後続のマイグレーションは実行されません。
これによりトラブル時の切り分けを容易にしています。

### ロック・タイムアウトの設定

各マイグレーション実行前に以下のSQLが自動で実行されます。

```sql
SET lock_timeout = '5s';
```

テーブルロックが取得できない場合はリトライなしで即座に失敗します。
長時間ロックが残っている場合は以下で状況を確認してください。

```sql
SELECT * FROM pg_locks;
```

---

## Flyway マイグレーションファイルの構成

```
src/main/resources/db/migration/   ← omoide-memory-jooq からコピーされる
    ├── V1__create_synced_omoide_photo.sql
    ├── V2__create_synced_omoide_video.sql
    ├── V3__create_comment_omoide_photo.sql
    └── V4__create_comment_omoide_video.sql
```

> **このディレクトリのファイルを直接編集しないでください。**
> コピー元である `omoide-memory-jooq` のファイルを編集してください。
> 次回の起動時に自動で上書きコピーされます。

---

## スキーマを変更したいとき

1. `omoide-memory-jooq/src/main/resources/db/migration/` に新しいSQLファイルを追加する
   - ファイル名は `V{次の番号}__{変更内容}.sql` の形式
   - 例: `V5__add_column_synced_omoide_photo.sql`
2. `omoide-memory-jooq` で `./gradlew generateJooq` を実行してjOOQクラスを再生成する
3. このプロジェクトで `./gradlew bootRun` を実行してマイグレーションを適用する

> **既存のマイグレーションファイルの編集は要注意！**
> Flywayはチェックサムで整合性を検証するため、編集すると起動時にエラーになります。
> 編集する場合は既存の DB のテーブルを落とすか、flyway_schema_migration のチェックサムを変えるなどしないといけません。
