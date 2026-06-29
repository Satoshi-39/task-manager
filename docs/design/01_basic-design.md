# 基本設計書

| 項目 | 内容 |
|---|---|
| プロジェクト名 | Task Manager REST API |
| ドキュメント種別 | 基本設計書 |
| バージョン | 1.0 |
| 作成日 | 2026-06-29 |
| 最終更新日 | 2026-06-29 |

---

## 1. システム概要

### 1.1 目的

タスク管理業務を支援する REST API を提供する。
タスクの作成・参照・更新・削除（CRUD）に加え、検索・集計・CSV エクスポート・バッチ処理機能を備える。

### 1.2 システム構成図

```
┌─────────────┐       HTTP/JSON        ┌──────────────────────────┐
│   クライアント  │ ◄──────────────────► │     Spring Boot App      │
│  (curl/SPA)  │                        │                          │
└─────────────┘                        │  ┌────────────────────┐  │
                                       │  │   Controller 層     │  │
                                       │  │  REST エンドポイント   │  │
                                       │  └────────┬───────────┘  │
                                       │           │              │
                                       │  ┌────────▼───────────┐  │
                                       │  │    Service 層       │  │
                                       │  │  ビジネスロジック     │  │
                                       │  └────────┬───────────┘  │
                                       │           │              │
                                       │  ┌────────▼───────────┐  │
                                       │  │   Repository 層     │  │
                                       │  │   JPA / JDBC        │  │
                                       │  └────────┬───────────┘  │
                                       │           │              │
                                       │  ┌────────▼───────────┐  │
                                       │  │    H2 Database      │  │
                                       │  │   (インメモリ)       │  │
                                       │  └────────────────────┘  │
                                       └──────────────────────────┘
```

### 1.3 技術スタック

| カテゴリ | 技術 | バージョン | 選定理由 |
|---|---|---|---|
| 言語 | Java | 21 (LTS) | Java Gold 対象バージョン |
| FW | Spring Boot | 3.5.0 | 業界標準の Web FW |
| ORM | Spring Data JPA | - | DB アクセスの定型コード削減 |
| DB | H2 Database | - | 開発環境向けインメモリ DB |
| ビルド | Gradle (Kotlin DSL) | 8.x | 型安全なビルドスクリプト |
| テスト | JUnit 5 + Mockito | - | 業界標準のテスト FW |

---

## 2. アーキテクチャ設計

### 2.1 レイヤードアーキテクチャ

本システムは 4 層のレイヤードアーキテクチャを採用する。
各層は下位層にのみ依存し、上位層への依存は禁止する。

```
  ┌──────────────────────────────────────┐
  │          Controller 層                │  ← HTTP リクエスト受付・レスポンス返却
  │  TaskController                      │
  │  DashboardController                 │
  │  TaskExportController                │
  └──────────────┬───────────────────────┘
                 │ DTO (Request / Response)
  ┌──────────────▼───────────────────────┐
  │          Service 層                   │  ← ビジネスロジック・トランザクション制御
  │  TaskService                         │
  │  TaskNotificationService             │
  │  TaskBatchService                    │
  │  TaskExportService                   │
  │  DashboardService                    │
  └──────────────┬───────────────────────┘
                 │ Entity
  ┌──────────────▼───────────────────────┐
  │          Repository 層                │  ← データアクセス
  │  TaskRepository (JPA)                │
  │  TaskJdbcRepository (JDBC)           │
  └──────────────┬───────────────────────┘
                 │ SQL
  ┌──────────────▼───────────────────────┐
  │          Database                     │
  │  H2 (インメモリ)                      │
  └──────────────────────────────────────┘
```

### 2.2 各層の責務

| 層 | 責務 | 配置クラス例 |
|---|---|---|
| Controller | HTTP リクエストの受付、入力バリデーション、レスポンス整形 | `TaskController` |
| Service | ビジネスルール適用、トランザクション境界管理、他サービス連携 | `TaskService` |
| Repository | データベースアクセス（CRUD）、クエリ発行 | `TaskRepository` |
| Domain | エンティティ定義、ドメインルール（Enum の状態遷移等） | `Task`, `TaskStatus` |

### 2.3 パッケージ構成

```
com.example.taskmanager
├── config/          設定クラス（AsyncConfig 等）
├── controller/      REST API コントローラ
├── service/         ビジネスロジック
├── repository/      データアクセス（JPA / JDBC）
├── domain/
│   ├── entity/      JPA エンティティ
│   └── enums/       列挙型
├── dto/
│   ├── request/     リクエスト DTO
│   └── response/    レスポンス DTO
├── exception/       例外クラス・グローバル例外ハンドラ
├── validation/      カスタムバリデーション
└── common/          共通ユーティリティ
```

---

## 3. 機能一覧

### 3.1 機能一覧表

| No | 機能 ID | 機能名 | 概要 |
|---|---|---|---|
| 1 | F-001 | タスク作成 | タスクを新規登録する |
| 2 | F-002 | タスク取得 | ID を指定してタスクを取得する |
| 3 | F-003 | タスク一覧取得 | 全タスクを取得する |
| 4 | F-004 | タスク更新 | 既存タスクを更新する（楽観的ロック付き） |
| 5 | F-005 | タスク削除 | 既存タスクを削除する |
| 6 | F-006 | タスク検索 | ステータス・優先度・キーワード・期限切れで検索する |
| 7 | F-007 | ダッシュボード集計 | ステータス別・優先度別の集計情報を返す |
| 8 | F-008 | CSV エクスポート | タスク一覧を CSV ファイルとしてダウンロードする |
| 9 | F-009 | 期限切れ一括処理 | 期限切れタスクをバッチで CANCELLED に更新する |
| 10 | F-010 | 並行処理デモ | CountDownLatch / CyclicBarrier の動作を確認する |

---

## 4. 処理方式設計

### 4.1 排他制御方式

本システムでは 2 種類の排他制御を実装する。

#### 4.1.1 楽観的ロック（Optimistic Locking）

```
                 ユーザ A                    ユーザ B
                   │                          │
  ① GET /tasks/1   │                          │
  version=0        │                          │
                   │          ② GET /tasks/1  │
                   │          version=0        │
  ③ PUT /tasks/1   │                          │
  version=0 → 1    │                          │
  → 成功           │                          │
                   │     ④ PUT /tasks/1        │
                   │     version=0             │
                   │     → 409 Conflict        │
```

- **実装**: `Task` エンティティの `@Version` フィールド
- **用途**: 通常の更新操作
- **動作**: 更新時に version を比較し、不一致なら `OptimisticLockException` をスロー
- **エラーハンドリング**: `GlobalExceptionHandler` で 409 Conflict を返却

#### 4.1.2 悲観的ロック（Pessimistic Locking）

- **実装**: `TaskRepository.findByIdWithLock()` に `@Lock(PESSIMISTIC_WRITE)` を付与
- **用途**: バッチ処理など、競合が頻発する処理
- **動作**: `SELECT ... FOR UPDATE` により行レベルロックを取得

### 4.2 非同期処理方式

```
  Controller               TaskService           TaskNotificationService
     │                        │                          │
     │── POST /api/tasks ───►│                          │
     │                        │── createTask() ────────►│
     │                        │                          │── @Async ──► スレッドプールで実行
     │                        │◄── Task (即時返却) ──────│              │
     │◄── 201 Created ───────│                          │              │
     │                        │                          │ ログ出力（通知シミュレート）
```

- `@Async("taskExecutor")` により非同期実行
- `ThreadPoolTaskExecutor` でスレッドプールを管理（core=2, max=4, queue=50）
- `CompletableFuture` チェーンで後処理を記述

### 4.3 バッチ処理方式

```
  TaskBatchService
     │
     │── ReentrantLock.lock() ────── クリティカルセクション開始
     │
     │── findOverdueTasks() ──────── 対象タスク取得
     │
     │── ExecutorService (固定スレッドプール, max 4)
     │     ├── Callable<Boolean> ── Task 1 処理
     │     ├── Callable<Boolean> ── Task 2 処理
     │     └── Callable<Boolean> ── Task N 処理
     │
     │── invokeAll() ─────────────── 全完了待ち
     │── AtomicInteger で結果集計
     │
     │── ReentrantLock.unlock() ──── クリティカルセクション終了
```

### 4.4 ダッシュボード並列集計方式

```
  DashboardService.getDashboard()
     │
     ├── CompletableFuture.supplyAsync() ── ステータス別集計
     ├── CompletableFuture.supplyAsync() ── 優先度別集計
     ├── CompletableFuture.supplyAsync() ── 総タスク数
     ├── CompletableFuture.supplyAsync() ── 期限切れ数
     └── CompletableFuture.supplyAsync() ── 完了数
     │
     │── CompletableFuture.allOf() ──────── 全結果合流
     │── Duration.between() ─────────────── 処理時間計測
     │
     └── DashboardResponse 返却
```

---

## 5. エラーハンドリング方式

### 5.1 例外階層

```
  RuntimeException
     └── BusinessException (errorCode, message)
            ├── TaskNotFoundException           → 404 Not Found
            ├── InvalidStatusTransitionException → 400 Bad Request
            └── TaskExportException              → 400 Bad Request

  MethodArgumentNotValidException               → 400 Bad Request (バリデーション)
  ObjectOptimisticLockingFailureException        → 409 Conflict (楽観ロック競合)
  Exception (その他)                              → 500 Internal Server Error
```

### 5.2 エラーレスポンス形式

```json
{
  "success": false,
  "message": "エラーメッセージ",
  "errors": ["フィールドエラー1", "フィールドエラー2"],
  "timestamp": "2026-06-29T14:00:00"
}
```

### 5.3 国際化（i18n）

- `messages.properties` (英語 / デフォルト)
- `messages_ja.properties` (日本語)
- `Accept-Language` ヘッダーに応じて切り替え

---

## 6. ステータス遷移図

```
                  ┌─────────────┐
                  │    TODO      │
                  │   (未着手)    │
                  └──────┬──────┘
                         │
              ┌──────────▼──────────┐
              │    IN_PROGRESS      │
              │      (進行中)        │
              └───┬─────────────┬───┘
                  │             │
        ┌─────────▼───┐   ┌───▼──────────┐
        │    DONE      │   │  CANCELLED   │
        │   (完了)      │   │   (中止)      │
        └─────────────┘   └──────────────┘

  許可される遷移:
    TODO         → IN_PROGRESS, CANCELLED
    IN_PROGRESS  → DONE, CANCELLED
    DONE         → (遷移不可)
    CANCELLED    → (遷移不可)
```

---

## 7. セキュリティ考慮事項

| 項目 | 対策 |
|---|---|
| SQL インジェクション | JPA パラメータバインド / JdbcTemplate プレースホルダ |
| 入力バリデーション | Bean Validation (`@NotBlank`, `@Size`, カスタム `@FutureOrToday`) |
| 同時更新 | 楽観的ロック (`@Version`) |
| CSV インジェクション | `escapeCsv()` による特殊文字エスケープ |

> **注記**: 本システムは学習用のため、認証・認可は実装していない。
> 本番環境では Spring Security による認証・認可を追加すること。

---

## 8. 非機能要件

| 項目 | 内容 |
|---|---|
| 可用性 | 学習用のため SLA 規定なし |
| 性能 | ダッシュボード集計を並列化し応答時間を短縮 |
| スケーラビリティ | スレッドプール設定で並行処理数を調整可能 |
| データ保持 | インメモリ DB のため再起動でリセットされる |
| ログ | SLF4J + Logback による構造化ログ出力 |
