# API 設計書（IF 仕様書）

| 項目 | 内容 |
|---|---|
| プロジェクト名 | Task Manager REST API |
| ドキュメント種別 | API 設計書（インタフェース仕様書） |
| バージョン | 1.0 |
| 作成日 | 2026-06-29 |
| 最終更新日 | 2026-06-29 |

---

## 1. 共通仕様

### 1.1 ベース URL

```
http://localhost:8080
```

### 1.2 リクエストヘッダー

| ヘッダー | 値 | 必須 | 備考 |
|---|---|---|---|
| `Content-Type` | `application/json` | POST/PUT 時必須 | |
| `Accept-Language` | `ja` / `en` | 任意 | エラーメッセージの言語切替 |

### 1.3 共通レスポンス形式

全エンドポイントは以下の `ApiResponse<T>` 形式で返却する。

```json
{
  "success": true,
  "data": { ... },
  "message": "任意のメッセージ",
  "errors": ["エラー1", "エラー2"],
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `success` | boolean | ○ | 処理成功: `true`, 失敗: `false` |
| `data` | T (任意) | - | レスポンスデータ。エラー時は `null` |
| `message` | string | - | 補足メッセージ |
| `errors` | string[] | - | バリデーションエラーの一覧 |
| `timestamp` | string (ISO 8601) | ○ | レスポンス生成日時 |

### 1.4 共通エラーコード

| HTTP ステータス | 意味 | 発生条件 |
|---|---|---|
| 400 Bad Request | 入力不正 | バリデーションエラー, 無効なステータス遷移 |
| 404 Not Found | リソース未存在 | 指定 ID のタスクが存在しない |
| 409 Conflict | 競合 | 楽観的ロック競合（他ユーザによる先行更新） |
| 500 Internal Server Error | サーバーエラー | 予期しない例外 |

---

## 2. タスク API

### 2.1 タスク作成

タスクを新規作成する。タスク番号は自動採番される。

| 項目 | 内容 |
|---|---|
| メソッド | `POST` |
| パス | `/api/tasks` |
| 認証 | 不要 |

#### リクエストボディ

| フィールド | 型 | 必須 | バリデーション | 説明 |
|---|---|---|---|---|
| `title` | string | ○ | 1〜200文字 | タスクタイトル |
| `description` | string | - | 最大2000文字 | タスク説明 |
| `priority` | string | - | `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` | 優先度（未指定時 `MEDIUM`） |
| `dueDate` | string (ISO 8601) | - | 今日以降の日付 | 期限日時 |

```json
{
  "title": "プロジェクト計画書の作成",
  "description": "Q1のプロジェクト計画書を作成する",
  "priority": "HIGH",
  "dueDate": "2026-12-31T17:00:00"
}
```

#### レスポンス（201 Created）

```json
{
  "success": true,
  "data": {
    "id": 9,
    "taskNumber": "TASK-20260629-0001",
    "title": "プロジェクト計画書の作成",
    "description": "Q1のプロジェクト計画書を作成する",
    "status": "TODO",
    "statusDisplayName": "未着手",
    "priority": "HIGH",
    "priorityDisplayName": "高",
    "dueDate": "2026-12-31T17:00:00",
    "createdAt": "2026-06-29T14:00:00.000000",
    "updatedAt": "2026-06-29T14:00:00.000000",
    "version": 0
  },
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

#### エラーレスポンス（400 Bad Request - バリデーションエラー）

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    "タイトルは必須です",
    "期日は今日以降の日付を指定してください"
  ],
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

---

### 2.2 タスク取得（ID 指定）

指定 ID のタスクを取得する。

| 項目 | 内容 |
|---|---|
| メソッド | `GET` |
| パス | `/api/tasks/{id}` |
| 認証 | 不要 |

#### パスパラメータ

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| `id` | long | ○ | タスク ID |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "data": {
    "id": 1,
    "taskNumber": "TASK-20260101-0001",
    "title": "プロジェクト計画書の作成",
    "description": "Q1のプロジェクト計画書を作成する",
    "status": "IN_PROGRESS",
    "statusDisplayName": "進行中",
    "priority": "HIGH",
    "priorityDisplayName": "高",
    "dueDate": "2026-07-15T17:00:00",
    "createdAt": "2026-01-01T09:00:00",
    "updatedAt": "2026-01-05T10:00:00",
    "version": 0
  },
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

#### エラーレスポンス（404 Not Found）

```json
{
  "success": false,
  "message": "Task not found with id: 999",
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

---

### 2.3 タスク一覧取得

全タスクを取得する。

| 項目 | 内容 |
|---|---|
| メソッド | `GET` |
| パス | `/api/tasks` |
| 認証 | 不要 |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "taskNumber": "TASK-20260101-0001",
      "title": "プロジェクト計画書の作成",
      "status": "IN_PROGRESS",
      "statusDisplayName": "進行中",
      "priority": "HIGH",
      "priorityDisplayName": "高",
      "dueDate": "2026-07-15T17:00:00",
      "createdAt": "2026-01-01T09:00:00",
      "updatedAt": "2026-01-05T10:00:00",
      "version": 0
    }
  ],
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

---

### 2.4 タスク更新

既存タスクを更新する。楽観的ロック（`version` フィールド）により同時更新を検知する。

| 項目 | 内容 |
|---|---|
| メソッド | `PUT` |
| パス | `/api/tasks/{id}` |
| 認証 | 不要 |

#### パスパラメータ

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| `id` | long | ○ | タスク ID |

#### リクエストボディ

| フィールド | 型 | 必須 | バリデーション | 説明 |
|---|---|---|---|---|
| `title` | string | ○ | 1〜200文字 | タスクタイトル |
| `description` | string | - | 最大2000文字 | タスク説明 |
| `status` | string | ○ | `TODO` / `IN_PROGRESS` / `DONE` / `CANCELLED` | ステータス |
| `priority` | string | - | `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` | 優先度 |
| `dueDate` | string (ISO 8601) | - | 今日以降の日付 | 期限日時 |
| `version` | long | ○ | - | 楽観的ロック用バージョン |

```json
{
  "title": "プロジェクト計画書の作成（改訂版）",
  "description": "Q1のプロジェクト計画書を改訂する",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "dueDate": "2026-07-20T17:00:00",
  "version": 0
}
```

#### レスポンス（200 OK）

タスク取得と同一形式（`version` がインクリメントされる）。

#### エラーレスポンス

| ステータス | 条件 | レスポンス例 |
|---|---|---|
| 400 | 無効なステータス遷移 | `"message": "Cannot transition from DONE to IN_PROGRESS"` |
| 404 | ID が存在しない | `"message": "Task not found with id: 999"` |
| 409 | 楽観的ロック競合 | `"message": "他のユーザーが先に更新しました..."` |

---

### 2.5 タスク削除

指定 ID のタスクを削除する。

| 項目 | 内容 |
|---|---|
| メソッド | `DELETE` |
| パス | `/api/tasks/{id}` |
| 認証 | 不要 |

#### パスパラメータ

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| `id` | long | ○ | タスク ID |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "message": "Task deleted successfully",
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

---

### 2.6 タスク検索

クエリパラメータで条件を指定してタスクを検索する。
結果は優先度（降順）→ 作成日（降順）でソートされる。

| 項目 | 内容 |
|---|---|
| メソッド | `GET` |
| パス | `/api/tasks/search` |
| 認証 | 不要 |

#### クエリパラメータ

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| `status` | string | - | ステータスでフィルタ |
| `priority` | string | - | 優先度でフィルタ |
| `keyword` | string | - | タイトル or 説明に含まれるキーワード |
| `overdue` | boolean | - | `true`: 期限切れのみ |

#### リクエスト例

```
GET /api/tasks/search?status=TODO&priority=HIGH
GET /api/tasks/search?keyword=設計&overdue=true
```

#### レスポンス（200 OK）

タスク一覧と同一形式。

---

## 3. ダッシュボード API

### 3.1 ダッシュボード集計

ステータス別・優先度別の集計、期限切れ数、完了率を返す。
各集計は `CompletableFuture.allOf` により並列実行される。

| 項目 | 内容 |
|---|---|
| メソッド | `GET` |
| パス | `/api/dashboard` |
| 認証 | 不要 |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "data": {
    "statusCounts": {
      "TODO": 4,
      "IN_PROGRESS": 2,
      "DONE": 1,
      "CANCELLED": 1
    },
    "priorityCounts": {
      "LOW": 2,
      "MEDIUM": 3,
      "HIGH": 2,
      "CRITICAL": 1
    },
    "totalTasks": 8,
    "overdueTasks": 1,
    "completionRate": 12,
    "processingTimeMs": 15
  },
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

| フィールド | 型 | 説明 |
|---|---|---|
| `statusCounts` | Map<string, long> | ステータスごとのタスク数 |
| `priorityCounts` | Map<string, long> | 優先度ごとのタスク数 |
| `totalTasks` | long | 総タスク数 |
| `overdueTasks` | long | 期限切れタスク数 |
| `completionRate` | long | 完了率（%） |
| `processingTimeMs` | long | 集計処理時間（ミリ秒） |

---

## 4. エクスポート API

### 4.1 CSV ダウンロード

タスク一覧を CSV ファイルとしてダウンロードする。
BOM 付き UTF-8 で出力し、Excel での文字化けを防止する。

| 項目 | 内容 |
|---|---|
| メソッド | `GET` |
| パス | `/api/tasks/export/csv` |
| 認証 | 不要 |

#### レスポンスヘッダー

| ヘッダー | 値 |
|---|---|
| `Content-Type` | `text/csv; charset=UTF-8` |
| `Content-Disposition` | `attachment; filename="tasks_yyyyMMdd_HHmmss.csv"` |

#### CSV 形式

```csv
TaskNumber,Title,Status,Priority,DueDate,CreatedAt
TASK-20260101-0001,プロジェクト計画書の作成,IN_PROGRESS,HIGH,2026/07/15 17:00,2026/01/01 09:00
TASK-20260101-0002,データベース設計,TODO,HIGH,2026/07/20 17:00,2026/01/01 09:30
```

---

## 5. バッチ処理 API

### 5.1 期限切れタスク一括処理

期限切れ（`dueDate < 現在日時` かつ未完了）のタスクを `CANCELLED` に更新する。
`ReentrantLock` による排他制御と `ExecutorService` による並行処理を使用。

| 項目 | 内容 |
|---|---|
| メソッド | `POST` |
| パス | `/api/tasks/batch/process-overdue` |
| 認証 | 不要 |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "data": "1 overdue tasks processed",
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

---

### 5.2 CountDownLatch デモ

複数の前処理が全て完了するまで待機してからバッチ処理を開始するデモ。

| 項目 | 内容 |
|---|---|
| メソッド | `POST` |
| パス | `/api/tasks/batch/demo/countdown-latch` |
| 認証 | 不要 |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "data": "All preparation steps completed. Batch processing can begin.",
  "timestamp": "2026-06-29T14:00:00.000000"
}
```

---

### 5.3 CyclicBarrier デモ

全ワーカースレッドがバリアに到達してから一斉に次のフェーズへ進むデモ。

| 項目 | 内容 |
|---|---|
| メソッド | `POST` |
| パス | `/api/tasks/batch/demo/cyclic-barrier` |
| 認証 | 不要 |

#### レスポンス（200 OK）

```json
{
  "success": true,
  "data": "Worker 1 completed; Worker 2 completed; Worker 3 completed;",
  "timestamp": "2026-06-29T14:00:00.000000"
}
```
