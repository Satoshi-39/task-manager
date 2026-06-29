# DB 設計書（テーブル定義書）

| 項目 | 内容 |
|---|---|
| プロジェクト名 | Task Manager REST API |
| ドキュメント種別 | DB 設計書（テーブル定義書） |
| バージョン | 1.0 |
| 作成日 | 2026-06-29 |
| 最終更新日 | 2026-06-29 |

---

## 1. データベース概要

| 項目 | 内容 |
|---|---|
| DBMS | H2 Database（開発環境：インメモリ） |
| JDBC URL | `jdbc:h2:mem:taskdb` |
| スキーマ | デフォルト (PUBLIC) |
| 文字コード | UTF-8 |
| DDL 管理方式 | `schema.sql` による初期化（`spring.jpa.hibernate.ddl-auto=none`） |

---

## 2. ER 図

```
  ┌───────────────────────────────────────────┐
  │                  tasks                     │
  ├───────────────┬───────────┬───────────────┤
  │ PK │ id       │ BIGINT    │ AUTO_INCREMENT│
  ├────┼──────────┼───────────┼───────────────┤
  │ UK │ task_number│VARCHAR(30)│ NOT NULL     │
  │    │ title     │VARCHAR(200)│ NOT NULL     │
  │    │ description│VARCHAR(2000)│            │
  │    │ status    │VARCHAR(20)│ NOT NULL      │
  │    │ priority  │VARCHAR(20)│ NOT NULL      │
  │    │ due_date  │ TIMESTAMP │               │
  │    │ created_at│ TIMESTAMP │ NOT NULL      │
  │    │ updated_at│ TIMESTAMP │ NOT NULL      │
  │    │ version   │ BIGINT    │ NOT NULL      │
  └────┴──────────┴───────────┴───────────────┘
```

> 本システムはタスクテーブル 1 つの単純構成。
> 本番化する場合は、ユーザテーブル・カテゴリテーブル等の追加を検討すること。

---

## 3. テーブル定義

### 3.1 tasks テーブル

タスク情報を管理するテーブル。

| No | 論理名 | 物理名 | データ型 | PK | UK | Not Null | Default | 説明 |
|---|---|---|---|---|---|---|---|---|
| 1 | タスク ID | `id` | BIGINT | ○ | | ○ | AUTO_INCREMENT | サロゲートキー |
| 2 | タスク番号 | `task_number` | VARCHAR(30) | | ○ | ○ | | 業務キー（TASK-yyyyMMdd-NNNN） |
| 3 | タイトル | `title` | VARCHAR(200) | | | ○ | | タスクタイトル |
| 4 | 説明 | `description` | VARCHAR(2000) | | | | | タスク詳細説明 |
| 5 | ステータス | `status` | VARCHAR(20) | | | ○ | 'TODO' | タスク状態（後述の区分値参照） |
| 6 | 優先度 | `priority` | VARCHAR(20) | | | ○ | 'MEDIUM' | タスク優先度（後述の区分値参照） |
| 7 | 期限日時 | `due_date` | TIMESTAMP | | | | | タスク期限。NULL 許可（期限なし） |
| 8 | 作成日時 | `created_at` | TIMESTAMP | | | ○ | | レコード作成日時 |
| 9 | 更新日時 | `updated_at` | TIMESTAMP | | | ○ | | レコード最終更新日時 |
| 10 | バージョン | `version` | BIGINT | | | ○ | 0 | 楽観的ロック用バージョン番号 |

---

## 4. インデックス定義

| No | インデックス名 | テーブル | カラム | 種別 | 説明 |
|---|---|---|---|---|---|
| 1 | PRIMARY (自動) | tasks | `id` | PRIMARY KEY | 主キー |
| 2 | (自動) | tasks | `task_number` | UNIQUE | 業務キーの一意制約 |
| 3 | `idx_tasks_status` | tasks | `status` | INDEX | ステータス検索の高速化 |
| 4 | `idx_tasks_priority` | tasks | `priority` | INDEX | 優先度検索の高速化 |
| 5 | `idx_tasks_due_date` | tasks | `due_date` | INDEX | 期限切れ検索の高速化 |

---

## 5. 区分値定義

### 5.1 ステータス（status）

| 区分値 | 論理名 | 表示名 | 説明 |
|---|---|---|---|
| `TODO` | 未着手 | 未着手 | 初期状態。作業未開始 |
| `IN_PROGRESS` | 進行中 | 進行中 | 作業中 |
| `DONE` | 完了 | 完了 | 作業完了（終了状態） |
| `CANCELLED` | 中止 | 中止 | 作業中止（終了状態） |

#### 遷移ルール

| 遷移元 ＼ 遷移先 | TODO | IN_PROGRESS | DONE | CANCELLED |
|---|---|---|---|---|
| **TODO** | - | ○ | ✕ | ○ |
| **IN_PROGRESS** | ✕ | - | ○ | ○ |
| **DONE** | ✕ | ✕ | - | ✕ |
| **CANCELLED** | ✕ | ✕ | ✕ | - |

- ○: 遷移可能
- ✕: 遷移不可（`InvalidStatusTransitionException` がスローされる）

### 5.2 優先度（priority）

| 区分値 | 論理名 | 表示名 | レベル | 説明 |
|---|---|---|---|---|
| `LOW` | 低 | 低 | 1 | 低優先度 |
| `MEDIUM` | 中 | 中 | 2 | 通常優先度（デフォルト） |
| `HIGH` | 高 | 高 | 3 | 高優先度 |
| `CRITICAL` | 緊急 | 緊急 | 4 | 最優先で対応 |

---

## 6. DDL

```sql
CREATE TABLE IF NOT EXISTS tasks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_number VARCHAR(30)   NOT NULL UNIQUE,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    priority    VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    due_date    TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tasks_status   ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date);
```

---

## 7. 初期データ

開発・動作確認用に 8 件のサンプルデータを投入する。

| id | task_number | title | status | priority | due_date |
|---|---|---|---|---|---|
| 1 | TASK-20260101-0001 | プロジェクト計画書の作成 | IN_PROGRESS | HIGH | 2026-07-15 |
| 2 | TASK-20260101-0002 | データベース設計 | TODO | HIGH | 2026-07-20 |
| 3 | TASK-20260101-0003 | API仕様書レビュー | DONE | MEDIUM | 2026-06-30 |
| 4 | TASK-20260101-0004 | セキュリティ監査対応 | TODO | CRITICAL | 2026-08-01 |
| 5 | TASK-20260101-0005 | テストケース作成 | IN_PROGRESS | MEDIUM | 2026-07-10 |
| 6 | TASK-20260101-0006 | 古いドキュメント整理 | TODO | LOW | (なし) |
| 7 | TASK-20260101-0007 | 期限切れタスク（テスト用） | TODO | LOW | 2026-01-01 |
| 8 | TASK-20260101-0008 | CI/CDパイプライン構築 | CANCELLED | MEDIUM | 2026-06-15 |

---

## 8. エンティティマッピング

JPA エンティティ `Task.java` とテーブルカラムの対応。

| Java フィールド | DB カラム | 型マッピング | アノテーション |
|---|---|---|---|
| `id` | `id` | Long ↔ BIGINT | `@Id`, `@GeneratedValue(IDENTITY)` |
| `taskNumber` | `task_number` | String ↔ VARCHAR(30) | `@Column(nullable=false)` |
| `title` | `title` | String ↔ VARCHAR(200) | `@Column(nullable=false)` |
| `description` | `description` | String ↔ VARCHAR(2000) | `@Column(length=2000)` |
| `status` | `status` | TaskStatus ↔ VARCHAR(20) | `@Enumerated(EnumType.STRING)` |
| `priority` | `priority` | TaskPriority ↔ VARCHAR(20) | `@Enumerated(EnumType.STRING)` |
| `dueDate` | `due_date` | LocalDateTime ↔ TIMESTAMP | (自動マッピング) |
| `createdAt` | `created_at` | LocalDateTime ↔ TIMESTAMP | `@Column(updatable=false)`, `@PrePersist` |
| `updatedAt` | `updated_at` | LocalDateTime ↔ TIMESTAMP | `@PrePersist`, `@PreUpdate` |
| `version` | `version` | Long ↔ BIGINT | `@Version`（楽観的ロック） |

---

## 9. 排他制御設計

### 9.1 楽観的ロック

| 項目 | 内容 |
|---|---|
| 方式 | JPA `@Version` アノテーション |
| 対象カラム | `version` |
| 動作 | UPDATE 時に `WHERE version = ?` を自動付与。不一致なら例外 |
| 例外クラス | `ObjectOptimisticLockingFailureException` |
| HTTP レスポンス | 409 Conflict |
| クライアント対応 | 最新データを再取得して再送信 |

### 9.2 悲観的ロック

| 項目 | 内容 |
|---|---|
| 方式 | JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` |
| SQL | `SELECT ... FOR UPDATE` |
| 使用箇所 | `TaskRepository.findByIdWithLock()` |
| 用途 | バッチ処理など競合が頻発する処理 |

---

## 10. 本番化に向けた考慮事項

| 項目 | 現状 | 本番化対応 |
|---|---|---|
| DBMS | H2（インメモリ） | PostgreSQL / MySQL に変更 |
| DDL 管理 | `schema.sql` | Flyway / Liquibase によるマイグレーション |
| コネクションプール | HikariCP（デフォルト設定） | 最大接続数等をチューニング |
| テーブル追加 | tasks のみ | users, categories, comments 等を追加 |
| 監査カラム | created_at, updated_at | created_by, updated_by を追加（Spring Data Auditing） |
| ソフトデリート | 物理削除 | `deleted_at` カラムによる論理削除に変更 |
