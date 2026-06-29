# Task Manager REST API

Java Gold 資格の学習範囲を実務的なコードで体験するための Spring Boot アプリケーション。
教科書的な書き方ではなく、現場で実際に使われるパターン・設計を中心に実装している。

## 技術スタック

| 項目 | 技術 |
|---|---|
| 言語 | Java 21 (Amazon Corretto) |
| フレームワーク | Spring Boot 3.5.0 |
| ORM | Spring Data JPA (Hibernate 6) |
| DB | H2 Database (インメモリ) |
| ビルド | Gradle (Kotlin DSL) |
| テスト | JUnit 5 + Mockito + MockMvc + Database Rider (DBUnit) |
| カバレッジ | JaCoCo |

## クイックスタート

```bash
# プロジェクトディレクトリへ移動
cd task-manager

# アプリケーション起動
./gradlew bootRun

# テスト実行
./gradlew test

# テスト + カバレッジレポート生成
./gradlew clean test jacocoTestReport

# カバレッジレポートをブラウザで確認
open build/reports/jacoco/test/html/index.html

# コンパイルのみ
./gradlew compileJava
```

起動後、`http://localhost:8080` でアクセス可能。

### 開発プロファイル（H2 Console 有効化）

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

H2 Console: http://localhost:8080/h2-console
JDBC URL: `jdbc:h2:mem:taskdb` / User: `sa` / Password: (空)

## API エンドポイント一覧

### タスク CRUD

| メソッド | パス | 説明 |
|---|---|---|
| `GET` | `/api/tasks` | 全タスク取得 |
| `GET` | `/api/tasks/{id}` | ID指定取得 |
| `POST` | `/api/tasks` | タスク作成 |
| `PUT` | `/api/tasks/{id}` | タスク更新 |
| `DELETE` | `/api/tasks/{id}` | タスク削除 |
| `GET` | `/api/tasks/search` | 条件検索 |

### ダッシュボード・エクスポート

| メソッド | パス | 説明 |
|---|---|---|
| `GET` | `/api/dashboard` | ダッシュボード集計 |
| `GET` | `/api/tasks/export/csv` | CSV ダウンロード |

### バッチ処理・並行処理デモ

| メソッド | パス | 説明 |
|---|---|---|
| `POST` | `/api/tasks/batch/process-overdue` | 期限切れタスク一括処理 |
| `POST` | `/api/tasks/batch/demo/countdown-latch` | CountDownLatch デモ |
| `POST` | `/api/tasks/batch/demo/cyclic-barrier` | CyclicBarrier デモ |

## curl サンプル

### タスク作成

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "新しいタスク",
    "description": "タスクの説明",
    "priority": "HIGH",
    "dueDate": "2026-12-31T17:00:00"
  }'
```

### タスク更新

```bash
curl -X PUT http://localhost:8080/api/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "更新後のタイトル",
    "description": "更新後の説明",
    "status": "IN_PROGRESS",
    "priority": "HIGH",
    "version": 0
  }'
```

### 条件検索

```bash
# ステータスで検索
curl "http://localhost:8080/api/tasks/search?status=TODO"

# 優先度で検索
curl "http://localhost:8080/api/tasks/search?priority=HIGH"

# キーワード検索
curl "http://localhost:8080/api/tasks/search?keyword=設計"

# 期限切れのみ
curl "http://localhost:8080/api/tasks/search?overdue=true"
```

### ダッシュボード取得

```bash
curl http://localhost:8080/api/dashboard
```

### CSV エクスポート

```bash
curl -O http://localhost:8080/api/tasks/export/csv
```

## プロジェクト構成

```
src/main/java/com/example/taskmanager/
├── TaskManagerApplication.java        # エントリーポイント
├── config/
│   └── AsyncConfig.java               # 非同期スレッドプール設定
├── controller/
│   ├── TaskController.java            # タスク CRUD API
│   ├── DashboardController.java       # ダッシュボード API
│   └── TaskExportController.java      # CSV エクスポート API
├── service/
│   ├── TaskService.java               # コアビジネスロジック
│   ├── TaskNotificationService.java   # 非同期通知
│   ├── TaskBatchService.java          # バッチ・並行処理
│   ├── TaskExportService.java         # ファイル出力
│   └── DashboardService.java          # ダッシュボード集計
├── repository/
│   ├── TaskRepository.java            # Spring Data JPA
│   └── TaskJdbcRepository.java        # JdbcTemplate
├── domain/
│   ├── entity/
│   │   └── Task.java                  # JPA エンティティ
│   └── enums/
│       ├── TaskStatus.java            # ステータス Enum
│       └── TaskPriority.java          # 優先度 Enum
├── dto/
│   ├── request/
│   │   ├── TaskCreateRequest.java     # 作成リクエスト
│   │   ├── TaskUpdateRequest.java     # 更新リクエスト
│   │   └── TaskSearchCriteria.java    # 検索条件
│   └── response/
│       ├── ApiResponse.java           # 汎用レスポンスラッパー
│       ├── TaskResponse.java          # タスクレスポンス
│       └── DashboardResponse.java     # ダッシュボードレスポンス
├── exception/
│   ├── BusinessException.java         # ビジネス例外基底
│   ├── TaskNotFoundException.java     # 404 例外
│   ├── InvalidStatusTransitionException.java  # 遷移エラー
│   ├── TaskExportException.java       # エクスポートエラー
│   └── GlobalExceptionHandler.java    # 集約例外ハンドラ
├── validation/
│   ├── FutureOrToday.java             # カスタムアノテーション
│   └── FutureOrTodayValidator.java    # バリデータ実装
└── common/
    ├── TaskNumberGenerator.java       # スレッドセーフ採番
    └── TaskFilterBuilder.java         # Predicate ビルダー
```

## Java Gold トピック対応表

| トピック | ファイル | 学習ポイント |
|---|---|---|
| Generics | `ApiResponse<T>` | 型パラメータ, 境界型, ファクトリメソッド |
| Collections 高度 | `TaskService` | `EnumMap`, `Comparator` 合成 |
| Stream API | `TaskService` | `groupingBy`, `partitioningBy`, `toMap`, `reduce` |
| Lambda / 関数型 IF | `TaskFilterBuilder` | `Predicate` の `and()` / `or()` 合成 |
| Optional | `TaskService`, `TaskRepository` | `map` → `orElseThrow` チェーン |
| CompletableFuture | `TaskNotificationService`, `DashboardService` | `supplyAsync`, `thenApply`, `exceptionally`, `allOf` |
| ExecutorService | `TaskBatchService` | `Callable`, `invokeAll`, `Future` |
| 排他制御 | `TaskNumberGenerator`, `Task`, `TaskBatchService` | `ReentrantLock`, `AtomicLong`, `@Version`, `@Lock`, `CountDownLatch`, `CyclicBarrier` |
| Date/Time API | `Task`, `TaskExportService` | `LocalDateTime`, `Duration`, `DateTimeFormatter` |
| NIO.2 | `TaskExportService` | `Path`, `Files`, `BufferedWriter` |
| JDBC | `TaskJdbcRepository` | `JdbcTemplate`, `RowMapper` |
| 例外処理 | `GlobalExceptionHandler` | カスタム例外階層, `@ExceptionHandler`, try-with-resources |
| Annotations | `@FutureOrToday` | カスタムアノテーション + `ConstraintValidator` |
| Localization | `messages*.properties` | `MessageSource`, `Locale` |

## テスト

### テスト種別

| 種別 | ファイル | 説明 |
|---|---|---|
| 単体テスト | `TaskServiceTest.java` | Mockito でリポジトリをモック化 |
| MVC テスト | `TaskControllerTest.java` | `@WebMvcTest` + MockMvc で API 検証 |
| DB テスト | `TaskRepositoryDbUnitTest.java` | Database Rider (DBUnit) で DB 状態を検証 |

### Database Rider (DBUnit)

YAML データセットでテストデータを管理し、DB の入出力を検証する。

```
src/test/resources/datasets/
├── tasks.yml                 # 標準テストデータ（3件）
├── tasks-empty.yml           # 空データセット
└── expected-after-delete.yml # 削除後の期待値
```

主要アノテーション:

| アノテーション | 説明 |
|---|---|
| `@DBRider` | JUnit 5 拡張を有効化 |
| `@DataSet` | テスト前に YAML データをDB に投入 |
| `@ExpectedDataSet` | テスト後のDB状態を期待値と比較 |

### JaCoCo カバレッジ

```bash
# レポート生成
./gradlew clean test jacocoTestReport

# ブラウザで確認
open build/reports/jacoco/test/html/index.html
```

レポートは `build/reports/jacoco/test/html/index.html` に出力される。

## ドキュメント

```
docs/
├── design/                   # 設計書
│   ├── 01_basic-design.md
│   ├── 02_api-design.md
│   └── 03_database-design.md
└── knowledge/                # 学習ナレッジ
    └── testing.md
```

### 設計書

| ドキュメント | 内容 |
|---|---|
| [基本設計書](docs/design/01_basic-design.md) | アーキテクチャ, レイヤー構成, 処理方式 |
| [API 設計書](docs/design/02_api-design.md) | 全エンドポイントの IF 仕様 |
| [DB 設計書](docs/design/03_database-design.md) | テーブル定義, インデックス, ER 図 |

### 学習ナレッジ

| ドキュメント | 内容 |
|---|---|
| [テスト手法](docs/knowledge/testing.md) | Mockito, MockMvc, DBUnit, MSW の違いと使い分け |
| [並行処理](docs/knowledge/concurrency.md) | ExecutorService, CompletableFuture, Lock, Latch, Barrier, @Version |
