# omoide-memory-sharing Backend

Spring Boot 3.x (WebFlux) + jOOQ による非同期・リアクティブなバックエンドアプリケーションです。

---

## Repository Structure

```
backend/
├── src/main/kotlin/com/kasakaid/omoidememory/
│   ├── adapter/         # REST Controller, VideoStreamTranslator 等
│   └── service/         # クエリサービス・Dto
├── build.gradle.kts
└── README.md
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x (WebFlux) |
| Async / Coroutines | Kotlin Coroutines, Project Reactor |
| Database Access | R2DBC + jOOQ (`:omoide-memory-jooq`) |
| DB | PostgreSQL |

---

## Features & API

- **フィード一覧**: `GET /feed`
- **コメント一覧**: `GET /content/{id}/comments`
- **動画ストリーミング**: `GET /video/{id}/stream`
- **年月メタデータ取得**: `GET /contents-captured-ym`, `GET /comment-created-ym`

---

## Getting Started (Development)

### ローカル起動

```bash
cd backend
./gradlew bootRun
```

### テスト実行

```bash
./gradlew test
```
