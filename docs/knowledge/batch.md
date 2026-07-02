# バッチ処理ナレッジ

Java のバッチ処理フレームワークに関する知識整理。
このプロジェクトの Spring Batch 実装（CSV エクスポート・インポート）を例に、
バッチ処理の基本概念・Spring Batch のアーキテクチャ・Java Gold 関連トピックを解説する。

## 全体マップ — Java バッチ処理の選択肢

```
Java バッチ処理の技術スタック
├── Spring Batch              ← Spring エコシステムのバッチフレームワーク ★ 最も主流
│   ├── Chunk 指向処理         ← Reader → Processor → Writer の定型パイプライン
│   ├── Tasklet 処理           ← 自由形式の1ステップ処理
│   ├── JobRepository          ← ジョブ実行履歴の永続管理
│   └── スケジューリング連携    ← @Scheduled / Quartz / クラウドスケジューラ
├── JSR 352 (JBatch)          ← Java EE 標準仕様（Spring Batch を参考に策定）
│   └── Jakarta EE サーバー前提。Spring Boot 環境ではほぼ使わない
├── Quartz Scheduler          ← ジョブスケジューリング特化（バッチ処理機能は持たない）
│   └── Spring Batch の起動トリガーとして併用されることがある
└── 独自実装                   ← @Scheduled + サービス層で単純な定期処理
    └── このプロジェクトの TaskBatchService が該当
```

## Spring Batch vs JSR 352 vs 独自実装

| 観点 | Spring Batch | JSR 352 (JBatch) | 独自実装 (@Scheduled) |
|---|---|---|---|
| **実行環境** | Spring Boot 単体 | Jakarta EE サーバー | Spring Boot 単体 |
| **設定方式** | Java Config / アノテーション | XML (job.xml) | アノテーション |
| **チャンク処理** | ✅ 充実 | ✅ 基本的 | ❌ 自前実装 |
| **リトライ / スキップ** | ✅ 宣言的に設定 | ✅ 基本的 | ❌ 自前実装 |
| **ジョブ実行履歴** | ✅ JobRepository | ✅ JobRepository | ❌ なし |
| **コミュニティ** | 非常に活発 | 小規模 | — |
| **採用率** | 高い | 低い | 単純処理なら多い |

**結論**: Spring Boot 環境では **Spring Batch** 一択。
JSR 352 は API 設計が Spring Batch を参考にしているが、実行環境が限定的で採用率が低い。
単純な定期処理（メール送信、期限チェック等）なら `@Scheduled` + サービス層で十分。

## Spring Batch のアーキテクチャ

### 主要コンポーネントの関係

```
JobLauncher                          ← ジョブの起動（REST API / Scheduler から呼ぶ）
    │
    └── Job                          ← 1つのバッチジョブ（複数 Step を持てる）
         │
         ├── Step 1                  ← 処理の1ステップ
         │    │
         │    └── Chunk 処理         ← Reader → Processor → Writer を chunk 単位で繰り返す
         │         ├── ItemReader    ← データソースからの読み取り
         │         ├── ItemProcessor ← 変換・フィルタリング
         │         └── ItemWriter    ← データの書き出し
         │
         ├── Step 2 ...
         │
         └── JobExecutionListener    ← ジョブの前後で実行されるコールバック
              ├── beforeJob()
              └── afterJob()

JobRepository                        ← ジョブ・ステップの実行履歴を DB に保存
  └── BATCH_JOB_INSTANCE / BATCH_JOB_EXECUTION / BATCH_STEP_EXECUTION テーブル
```

### Chunk 指向処理の動作フロー

```
chunk size = 10 の場合:

  ItemReader         ItemProcessor      ItemWriter
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │ read()   │─→    │process() │─→    │ write()  │
  │ read()   │─→    │process() │─→    │          │
  │ read()   │─→    │process() │─→    │ 10件まとめて│
  │  ...     │      │  ...     │      │ 一括書き込み│
  │ read()   │─→    │process() │─→    │          │
  └──────────┘      └──────────┘      └──────────┘
       ↑ 10件読んだら               ↑ 10件分を1トランザクションで書き込み
       │                            │
       └──── 次の chunk へ ─────────┘

  全件読み終わるまで chunk 単位で繰り返す。
  → メモリに全件載せる必要がないので大量データに対応できる。
```

### Chunk vs Tasklet — Step ごとに処理の「形」で選ぶ

Chunk と Tasklet は **Job 全体ではなく、Step（1工程）ごとに** 選ぶ処理方式。
「複雑さ」で使い分けるのではなく、処理の **形** で選ぶ。
Tasklet のほうがむしろシンプル。

```
Job と Step と処理方式の関係:

  Job（ジョブ = バッチ処理全体）
    │
    ├── Step 1 → 処理方式: Tasklet（前処理）
    ├── Step 2 → 処理方式: Chunk  （メイン処理）  ← Chunk はこの Step の中だけ
    └── Step 3 → 処理方式: Tasklet（後処理）

  「Job 全体が Chunk」ではなく「Step 2 だけが Chunk 方式」。
  各 Step が独立して Chunk か Tasklet かを選ぶ。
```

```
Chunk:   「大量のモノを流れ作業で処理する」
          ┌──────────────────────────────────────────┐
          │  read → process → read → process → ...  │ ← 1件ずつ変換して
          │                                          │
          │  → write(まとめて書き込み)                 │ ← N件ごとに一括保存
          └──────────────────────────────────────────┘
          例: CSV → DB、DB → CSV、テーブル A → テーブル B

Tasklet:  「1つの用事を済ませる」
          ┌──────────────────────────────────────────┐
          │  execute() { やること; return FINISHED; } │ ← 1回で終わり
          └──────────────────────────────────────────┘
          例: ファイル削除、テーブル TRUNCATE、通知送信
```

| 判断基準 | Chunk | Tasklet |
|---|---|---|
| **データの流れ** | 入力 → 変換 → 出力（パイプライン型） | なし、または1回の操作 |
| **処理件数** | 大量（数百〜数百万件） | 1回 |
| **トランザクション** | chunk 単位で自動コミット | 1トランザクション |
| **リトライ / スキップ** | フレームワークが提供 | 自前で実装 |

Chunk の Reader → Processor → Writer という **型に当てはめると不自然になる処理** に Tasklet を使う。
「ファイルを消す」処理に Reader（何を読む？）や Writer（何に書く？）は不要。

### Tasklet のコード例

```java
// 古い CSV ファイルを削除する Tasklet
@Bean
public Step cleanupStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager) {
    return new StepBuilder("cleanupStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                Path exportDir = Paths.get("export");
                if (Files.exists(exportDir)) {
                    try (Stream<Path> files = Files.list(exportDir)) {
                        files.filter(p -> p.toString().endsWith(".csv"))
                             .forEach(p -> {
                                 try { Files.delete(p); } catch (IOException e) { /* log */ }
                             });
                    }
                }
                return RepeatStatus.FINISHED;  // ← FINISHED: 1回で終了
                                               //    CONTINUABLE: もう1回 execute() を呼ぶ
            }, transactionManager)
            .build();
}
```

### Chunk と Tasklet を1つの Job に混在させる

実務では Tasklet を**前処理・後処理**、Chunk を**メイン処理**として組み合わせるのが一般的。

```
典型的なジョブ構成:

  Job: taskExportJob
    ├── Step 1: cleanupStep    (Tasklet)  ← 前処理: 古い CSV を削除
    ├── Step 2: exportStep     (Chunk)    ← メイン: DB → CSV 出力
    └── Step 3: notifyStep     (Tasklet)  ← 後処理: 完了通知を送信

  Job: taskImportJob
    ├── Step 1: validateStep   (Tasklet)  ← 前処理: CSV の形式チェック
    ├── Step 2: importStep     (Chunk)    ← メイン: CSV → DB 登録
    └── Step 3: summaryStep    (Tasklet)  ← 後処理: 処理結果サマリーの出力
```

```java
// Job に複数 Step を連結する
@Bean
public Job taskExportJob(JobRepository jobRepository,
                         Step cleanupStep,
                         Step taskExportStep,
                         TaskJobExecutionListener listener) {
    return new JobBuilder("taskExportJob", jobRepository)
            .listener(listener)
            .start(cleanupStep)         // ← Step 1: Tasklet（前処理）
            .next(taskExportStep)       // ← Step 2: Chunk（メイン処理）
            .build();
}
```

このプロジェクトでは現在 Chunk のみ使用しているが、
上記のように Tasklet を追加することで自然に拡張できる。

## このプロジェクトの実装

### パッケージ構成

```
batch/
├── config/
│   ├── TaskExportJobConfig.java     ← エクスポートジョブ定義
│   └── TaskImportJobConfig.java     ← インポートジョブ定義
├── controller/
│   └── BatchJobController.java      ← REST API（手動起動・ステータス確認）
├── dto/
│   └── TaskCsvRow.java              ← CSV 1行分の POJO
├── listener/
│   └── TaskJobExecutionListener.java ← ジョブ実行の監視・ログ出力
└── scheduler/
    └── TaskBatchScheduler.java      ← @Scheduled による定期実行
```

### エクスポートジョブの流れ

```
TaskExportJobConfig:

  JpaPagingItemReader<Task>          ← DB から Task エンティティを読み取り（ページング）
         │
         ↓
  ItemProcessor<Task, TaskCsvRow>    ← Entity → CSV 行に変換（ラムダ式で実装）
         │ ・LocalDateTime → String フォーマット変換
         │ ・null フィールドの空文字変換
         │ ・Enum.name() で文字列化
         ↓
  FlatFileItemWriter<TaskCsvRow>     ← CSV ファイルに書き出し
         │ ・ヘッダー行の出力（headerCallback）
         │ ・NIO.2 で出力ディレクトリ作成
         ↓
  export/tasks_yyyyMMdd_HHmmss.csv   ← 出力ファイル
```

### インポートジョブの流れ

```
TaskImportJobConfig:

  FlatFileItemReader<TaskCsvRow>     ← CSV ファイルを読み取り
         │ ・ヘッダー行スキップ（linesToSkip = 1）
         │ ・DelimitedLineTokenizer でカンマ区切り
         │ ・BeanWrapperFieldSetMapper で TaskCsvRow にマッピング
         ↓
  ItemProcessor<TaskCsvRow, Task>    ← CSV 行 → Entity に変換（ラムダ式で実装）
         │ ・タイトル必須チェック（null → スキップ）
         │ ・Enum.valueOf() でステータス・優先度を変換
         │ ・DateTimeFormatter で日時パース
         │ ・TaskNumberGenerator で新規タスク番号を発番
         ↓
  RepositoryItemWriter<Task>         ← TaskRepository.save() で DB に保存
         │
         ↓  Skip ポリシー: FlatFileParseException を最大 10 件まで許容
  DB に新規タスクが登録される
```

## Spring Batch の主要 API

### ItemReader の種類

| Reader | データソース | 特徴 |
|---|---|---|
| **JpaPagingItemReader** | JPA (DB) | JPQL でページング読み取り ★ このプロジェクトで使用 |
| **JdbcPagingItemReader** | JDBC (DB) | SQL でページング読み取り |
| **JdbcCursorItemReader** | JDBC (DB) | カーソルで1件ずつ読み取り |
| **FlatFileItemReader** | CSV / テキスト | ファイルを行単位で読み取り ★ このプロジェクトで使用 |
| **JsonItemReader** | JSON | JSON 配列の要素を1件ずつ読み取り |
| **StaxEventItemReader** | XML | SAX パーサーで XML を読み取り |

### ItemWriter の種類

| Writer | データソース | 特徴 |
|---|---|---|
| **RepositoryItemWriter** | Spring Data | Repository.save() で保存 ★ このプロジェクトで使用 |
| **JpaItemWriter** | JPA | EntityManager.merge() で保存 |
| **JdbcBatchItemWriter** | JDBC | バッチ INSERT で高速書き込み |
| **FlatFileItemWriter** | CSV / テキスト | ファイルに行単位で書き出し ★ このプロジェクトで使用 |
| **JsonFileItemWriter** | JSON | JSON ファイルに書き出し |

### ItemProcessor

```java
// ItemProcessor は関数型インタフェース（SAM: Single Abstract Method）
@FunctionalInterface
public interface ItemProcessor<I, O> {
    O process(I item) throws Exception;  // ← I を受け取って O を返す
}

// ラムダ式で簡潔に記述できる
@Bean
public ItemProcessor<Task, TaskCsvRow> taskExportProcessor() {
    return task -> {                         // ← ラムダ式で実装
        return new TaskCsvRow(
            task.getTaskNumber(),
            task.getTitle(),
            // ...
        );
    };
}

// null を返すとその項目はスキップされる（フィルタリング）
@Bean
public ItemProcessor<TaskCsvRow, Task> taskImportProcessor() {
    return csvRow -> {
        if (csvRow.getTitle() == null || csvRow.getTitle().isBlank()) {
            return null;  // ← この行はスキップ（Writer に渡されない）
        }
        // ...
    };
}
```

## ジョブの起動方法

### 3つの起動パターン

```
                    ┌─────────────────────────┐
                    │      JobLauncher         │ ← 全ての起動はここを通る
                    └─────────┬───────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
  ① REST API             ② @Scheduled        ③ 起動時自動実行
  BatchJobController      TaskBatchScheduler    spring.batch.job.enabled=true
  POST /api/batch/export  cron = "0 0 2 * * *"  （このプロジェクトでは false）
```

### ① REST API による手動起動

```java
// BatchJobController.java
@PostMapping("/export")
public ResponseEntity<ApiResponse<Map<String, Object>>> startExport() {
    JobParameters params = new JobParametersBuilder()
            .addString("executionTime", LocalDateTime.now().toString())  // ← ユニークパラメータ
            .toJobParameters();

    JobExecution execution = jobLauncher.run(taskExportJob, params);
    // ...
}
```

### ② @Scheduled による定期実行

```java
// TaskBatchScheduler.java
@Scheduled(cron = "${task.batch.export.cron:0 0 2 * * *}")  // ← デフォルト毎日2時
public void runScheduledExport() {
    JobParameters params = new JobParametersBuilder()
            .addString("executionTime", LocalDateTime.now().toString())
            .toJobParameters();

    jobLauncher.run(taskExportJob, params);
}
```

### JobParameters とは何か

**ジョブを実行するときに外から渡す設定値**。プログラムの引数（`main(String[] args)`）に相当する。

```java
// ジョブパラメータの作成
JobParameters params = new JobParametersBuilder()
        .addString("inputFile", "/tmp/tasks_a.csv")        // ← 文字列パラメータ
        .addString("executionTime", "2026-07-02T10:30:00") // ← 文字列パラメータ
        .toJobParameters();

// ジョブの実行（パラメータ付き）
jobLauncher.run(taskImportJob, params);
```

このプロジェクトで使用しているパラメータ:

| パラメータ名 | 値の例 | 用途 |
|---|---|---|
| `inputFile` | `/tmp/task-import-xxx.csv` | インポートする CSV ファイルのパス |
| `executionTime` | `2026-07-02T10:30:00.123` | 実行のユニーク化（下記参照） |

### なぜ JobParameters が必要か

**理由①: 実行時の設定を渡すため**

```
エクスポートジョブ:
  出力先ディレクトリは application.yml で固定 → パラメータ不要
  （executionTime はユニーク化目的のみ）

インポートジョブ:
  読み込む CSV ファイルは毎回違う → パラメータで渡す必要がある
  Reader が #{jobParameters['inputFile']} でパスを受け取る
```

**理由②: 同じジョブの重複実行を防ぐため**

Spring Batch は `ジョブ名 + パラメータ` の組み合わせで **JobInstance** を識別する。
同じ JobInstance が成功済みなら再実行しない（二重処理の防止）。

```
JobInstance の一意性 = ジョブ名 + パラメータ

  実行1: taskExportJob + {executionTime: "10:00:00"} → JobInstance A → 成功 ✅
  実行2: taskExportJob + {executionTime: "14:00:00"} → JobInstance B → 成功 ✅
  実行3: taskExportJob + {executionTime: "10:00:00"} → JobInstance A が既に成功済み
                                                       → 再実行しない → 例外 ❌

  これにより「日次バッチを誤って2回実行してデータが重複した」という事故を防げる。
```

```java
// ❌ パラメータが毎回同じ → 2回目以降エラー
jobLauncher.run(job, new JobParameters());
// → JobInstanceAlreadyCompleteException

// ✅ 実行時刻をパラメータに含める → 毎回異なる JobInstance になる
JobParameters params = new JobParametersBuilder()
        .addString("executionTime", LocalDateTime.now().toString())
        .toJobParameters();
jobLauncher.run(job, params);
```

**理由③: 失敗したジョブの再実行**

同じパラメータでジョブが失敗した場合、同じパラメータで再実行すると
**失敗した箇所から再開**できる（リスタート機能）。

```
  実行1: taskImportJob + {inputFile: "a.csv"} → 50件目で失敗 FAILED
  実行2: taskImportJob + {inputFile: "a.csv"} → 50件目から再開（リスタート）
```

### @StepScope との関係

JobParameters は @StepScope と組み合わせて使う。

```
  JobParameters                 @StepScope の Bean
  ┌─────────────────┐          ┌──────────────────────────────┐
  │ inputFile:      │──注入──→ │ FlatFileItemReader           │
  │ "/tmp/a.csv"    │          │  .resource(inputFile を使用)  │
  └─────────────────┘          └──────────────────────────────┘
         ↑                              ↑
  ジョブ実行時に渡す              Step 実行時に Bean が生成される
                               → この時点で inputFile が利用可能
```

## エラーハンドリング — Skip ポリシー

### スキップの仕組み

```
チャンク処理中にエラーが発生した場合:

  通常モード:
    read → read → read(❌例外) → ジョブ FAILED（全体停止）

  faultTolerant + skip:
    read → read → read(❌例外 → スキップ) → read → ... → write
    ↑ 指定した例外を skipLimit 回まで許容し、処理を継続する
```

### このプロジェクトでの設定

```java
// TaskImportJobConfig.java
return new StepBuilder("taskImportStep", jobRepository)
        .<TaskCsvRow, Task>chunk(10, transactionManager)
        .reader(taskImportReader)
        .processor(taskImportProcessor)
        .writer(taskImportWriter)
        .faultTolerant()                         // ← フォールトトレラントモードを有効化
        .skip(FlatFileParseException.class)      // ← この例外をスキップ対象に
        .skipLimit(10)                           // ← 最大 10 件までスキップ許容
        .build();
```

### Processor でのフィルタリング vs Skip

| 方式 | 手法 | ユースケース |
|---|---|---|
| **Processor で null 返却** | `return null;` | ビジネスルールによるフィルタ（タイトル空欄など） |
| **Skip ポリシー** | `faultTolerant().skip()` | パース失敗・型変換エラーなどの技術的例外 |

## JobExecutionListener — ジョブ監視

```java
// TaskJobExecutionListener.java
@Component
public class TaskJobExecutionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // ジョブ開始時のログ出力
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // Duration で所要時間を計算（Date/Time API）
        Duration duration = Duration.between(
                jobExecution.getStartTime(),
                jobExecution.getEndTime());

        // ステップの統計情報を集計
        for (StepExecution step : jobExecution.getStepExecutions()) {
            step.getReadCount();    // 読み取り件数
            step.getWriteCount();   // 書き込み件数
            step.getSkipCount();    // スキップ件数
        }

        // FAILED 時のエラー情報
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    log.error("失敗原因: {}", ex.getMessage(), ex));
        }
    }
}
```

## @StepScope — 「設定は後で決める」Bean

### 問題: Bean の生成タイミングとジョブパラメータのずれ

インポートジョブの Reader は「どの CSV ファイルを読むか」を知る必要がある。
しかしファイルパスはジョブ実行時にユーザーが指定するもので、アプリ起動時には決まっていない。

```
時系列で見ると:

  アプリ起動時                     ジョブ実行時
  ─────────                     ─────────
  Spring が全ての @Bean を生成      ユーザーが curl で CSV をアップロード
  ↓                               ↓
  taskImportReader を作ろうとする   jobParameters['inputFile'] = "/tmp/tasks_a.csv"
  ↓
  「inputFile の値は？」
  → まだジョブが実行されていない
  → jobParameters 自体が存在しない
  → エラー ❌
```

### 解決: @StepScope で Bean の生成を遅らせる

```java
// ❌ @StepScope なし — アプリ起動時に Bean を作ろうとしてエラー
@Bean
public FlatFileItemReader<TaskCsvRow> taskImportReader(
        @Value("#{jobParameters['inputFile']}") String inputFile) {
    // アプリ起動時に inputFile を解決しようとする → jobParameters がない → エラー
}

// ✅ @StepScope あり — Step 実行時まで Bean の生成を遅らせる
@Bean
@StepScope
public FlatFileItemReader<TaskCsvRow> taskImportReader(
        @Value("#{jobParameters['inputFile']}") String inputFile) {
    // Step 実行時に作られるので jobParameters['inputFile'] が解決できる ✅
}
```

```
@StepScope の動作:

  アプリ起動時:
    taskImportReader → 「プロキシ（中身は空の箱）」だけ作る
                        実際の FlatFileItemReader はまだ作らない

  ユーザーA がインポート実行:
    jobParameters = { inputFile: "/tmp/tasks_a.csv" }
    → Step 開始
    → プロキシが呼ばれる
    → この時点で inputFile = "/tmp/tasks_a.csv" が注入される
    → 実際の FlatFileItemReader("/tmp/tasks_a.csv") が作られる

  ユーザーB がインポート実行:
    jobParameters = { inputFile: "/tmp/tasks_b.csv" }
    → Step 開始
    → 別の FlatFileItemReader("/tmp/tasks_b.csv") が作られる
```

### いつ @StepScope が必要か

```
@StepScope が必要:
  - #{jobParameters['...']} でジョブパラメータを参照する Bean
  - #{stepExecutionContext['...']} でステップコンテキストを参照する Bean
  → 実行時にしか決まらない値を使う場合

@StepScope が不要:
  - 設定値が固定の Bean
  → エクスポートの Reader（JPQL クエリは固定）
  → Processor（変換ロジックは固定）
  → Writer の出力先が application.yml で固定
```

このプロジェクトでは `taskImportReader` だけに `@StepScope` を付けている。
入力ファイルのパスがジョブパラメータで渡されるため。

## Spring Boot の設定

### application.yml

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: embedded    # ← H2 に Batch メタデータテーブルを自動作成
    job:
      enabled: false                 # ← アプリ起動時にジョブを自動実行しない
```

### initialize-schema の選択肢

| 値 | 動作 | ユースケース |
|---|---|---|
| `always` | 毎回テーブル作成を試みる | 本番 DB（PostgreSQL 等） |
| `embedded` | 組み込み DB の場合のみ作成 | 開発環境（H2, HSQLDB） |
| `never` | 作成しない | テーブルを手動管理する場合 |

### Batch メタデータテーブル

Spring Batch が自動作成するテーブル:

```
BATCH_JOB_INSTANCE       ← ジョブの論理的な定義（名前 + パラメータの一意性）
  └── BATCH_JOB_EXECUTION       ← ジョブの実行履歴（開始/終了時刻、ステータス）
        └── BATCH_STEP_EXECUTION       ← ステップの実行履歴（読取/書込/スキップ件数）
              └── BATCH_JOB_EXECUTION_PARAMS  ← ジョブパラメータ
```

## Spring Batch 5 (Spring Boot 3.x) の変更点

Spring Boot 3.x で Spring Batch 5 にメジャーバージョンアップされた。
主な変更点:

### ① JobBuilderFactory / StepBuilderFactory の廃止

```java
// ❌ Spring Batch 4（旧）— Factory 経由
@Autowired
private JobBuilderFactory jobBuilderFactory;
@Autowired
private StepBuilderFactory stepBuilderFactory;

Job job = jobBuilderFactory.get("myJob").start(step).build();
Step step = stepBuilderFactory.get("myStep").<I, O>chunk(10).build();

// ✅ Spring Batch 5（現行）— JobRepository を直接渡す
Job job = new JobBuilder("myJob", jobRepository).start(step).build();
Step step = new StepBuilder("myStep", jobRepository)
        .<I, O>chunk(10, transactionManager)  // ← transactionManager も必須に
        .build();
```

### ② @EnableBatchProcessing が不要に

Spring Boot 3.x では Spring Batch の自動構成が組み込まれているため、
`@EnableBatchProcessing` を付ける必要がない（付けると逆に自動構成が無効化される場合がある）。

### ③ 複数 Job 定義時の注意

`spring.batch.job.enabled=true`（起動時自動実行）を使う場合、
Job が複数あると `Job name must be specified` エラーになる。
`spring.batch.job.name` で実行する Job を指定するか、`enabled=false` にして手動起動する。

## Java Gold トピックとの対応

| Java Gold トピック | Spring Batch での使用箇所 |
|---|---|
| **Generics（型パラメータ）** | `ItemProcessor<Task, TaskCsvRow>`, `JpaPagingItemReader<Task>`, `FlatFileItemWriter<TaskCsvRow>` |
| **関数型インタフェース / ラムダ** | `ItemProcessor` をラムダ式で実装、`headerCallback` のラムダ |
| **例外処理** | Skip ポリシー（`FlatFileParseException`）、`DateTimeParseException` のキャッチ |
| **Date/Time API** | `DateTimeFormatter` による CSV 日時変換、`Duration` による所要時間計算 |
| **NIO.2** | `Files.createDirectories()` で出力ディレクトリ作成、`Path` / `Paths` 操作 |
| **Enum** | `TaskStatus.valueOf()`, `TaskPriority.valueOf()` による文字列 → Enum 変換 |
| **アノテーション** | `@Configuration`, `@Bean`, `@StepScope`, `@Scheduled`, `@ConditionalOnProperty` |
| **並行処理** | `@Scheduled` のスケジューラスレッドプール |

## テスト

### @SpringBatchTest

```java
@SpringBatchTest     // ← JobLauncherTestUtils 等を自動登録
@SpringBootTest      // ← Spring Boot アプリケーション全体をロード
class TaskExportJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("taskExportJob")
    private Job taskExportJob;

    @Test
    void エクスポートジョブが正常に完了する() throws Exception {
        // テスト対象のジョブを設定（複数 Job 定義時に必要）
        jobLauncherTestUtils.setJob(taskExportJob);

        JobParameters params = new JobParametersBuilder()
                .addString("executionTime", LocalDateTime.now().toString())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
    }
}
```

### テスト時の注意点

- **JobLauncherTestUtils.setJob()**: 複数 Job がある場合、テスト対象の Job を明示的に設定する必要がある
- **JobParameters のユニーク性**: テストメソッドごとに異なる JobParameters を使う
- **H2 共有問題**: `@SpringBatchTest` は異なるコンテキストキーを生成するため、同じ H2 に `data.sql` が重複実行される。`continue-on-error: true` で回避

## バッチ処理の並列化

### 基本方針: まずシングルスレッド、遅ければ並列化

Spring Batch はデフォルトでシングルスレッド。このプロジェクトもシングルスレッドで動作する。
実務でも **大半のバッチ処理はシングルスレッドで十分**。

並列化を検討するのは以下の場合:
- 処理対象が数万件〜数百万件
- 処理時間が SLA に収まらない（例: 夜間バッチを朝6時までに終わらせたい）

むやみに並列化すると DB のロック競合やデータ順序の問題が発生するため、
「まずシングルスレッドで作り、パフォーマンス問題が出たら並列化」が鉄則。

### Spring Batch の並列化手法

```
簡単 ──────────────────────────────────────── 難しい

  マルチスレッド Step    パラレル Step    パーティショニング    リモートチャンキング
  (TaskExecutor 設定)   (Flow で並列)    (データ分割)          (サーバー分散)
       ↑                                                        ↑
  最もよく使う                                             ほぼ使わない
```

| 手法 | 仕組み | 実務での使用頻度 |
|---|---|---|
| **マルチスレッド Step** | Step に `TaskExecutor` を設定し chunk を複数スレッドで並列処理 | ★★★ 最も多い |
| **パラレル Step** | `Flow` で独立した複数 Step を同時実行 | ★★ たまに |
| **パーティショニング** | データを範囲分割し各パーティションを別スレッドで処理 | ★ 大規模案件 |
| **リモートチャンキング** | Reader はマスター、Processor/Writer をワーカーサーバーに分散 | ほぼ使わない |

### マルチスレッド Step の設定例

最も手軽で実務でもよく使われる並列化手法。
Step に `TaskExecutor` を設定するだけで chunk が複数スレッドで処理される。

```java
@Bean
public Step taskExportStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           JpaPagingItemReader<Task> reader,
                           ItemProcessor<Task, TaskCsvRow> processor,
                           FlatFileItemWriter<TaskCsvRow> writer,
                           TaskExecutor taskExecutor) {
    return new StepBuilder("taskExportStep", jobRepository)
            .<Task, TaskCsvRow>chunk(10, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .taskExecutor(taskExecutor)   // ← これを追加するだけ
            .throttleLimit(4)             // ← 同時実行スレッド数の上限
            .build();
}
```

```
シングルスレッド:
  chunk1 → chunk2 → chunk3 → chunk4 → 完了
  ─────────────────────────────────→ 時間

マルチスレッド（4スレッド）:
  Thread-1: chunk1 → chunk5 → ...
  Thread-2: chunk2 → chunk6 → ...
  Thread-3: chunk3 → chunk7 → ...
  Thread-4: chunk4 → chunk8 → ...
  ──────────────────→ 時間（短縮）
```

### マルチスレッド化の注意点

| 注意点 | 説明 |
|---|---|
| **Reader のスレッドセーフ性** | `JpaPagingItemReader` はスレッドセーフ。`JdbcCursorItemReader` は非スレッドセーフ（`SynchronizedItemStreamReader` でラップが必要） |
| **Writer の競合** | ファイル出力（`FlatFileItemWriter`）は書き込み順序が保証されない。DB 書き込みは通常問題なし |
| **処理順序の非保証** | chunk の処理順序が保証されないため、順序依存の処理には不向き |
| **リスタートの制約** | マルチスレッド Step はリスタート時に「どこまで処理したか」の特定が難しい |

### Java Gold との関連

バッチの並列化では Java Gold で学ぶ並行処理 API がそのまま活きる:

- **`ThreadPoolTaskExecutor`** — `ExecutorService` の Spring ラッパー。スレッドプールの設定
- **`AtomicLong`** — スレッドセーフなカウンタ（進捗管理等）
- **`ConcurrentHashMap`** — スレッドセーフな中間結果の集約
- **`synchronized` / `ReentrantLock`** — 共有リソースへの排他制御

## 本番環境でのバッチ起動方式

### このプロジェクトと実務の違い

このプロジェクトでは curl + Basic 認証で手動起動しているが、
これは学習用の動作確認手段であり、本番環境では使わない。

```
このプロジェクト（学習用）:
  curl -u admin:admin123 -X POST /api/batch/export
  → Basic 認証で REST API 経由で起動

本番環境:
  スケジューラ → java -jar app.jar --spring.batch.job.name=...
  → 人の手を介さず自動実行（認証自体が不要）
```

### 実務での起動方式

```
よくある方式（頻度順）:

  ① スケジューラから直接実行         ★★★ 最も多い
  ② 内部ネットワーク + 認証なし      ★★
  ③ API キー / トークン認証          ★
  ④ Basic 認証                      ほぼない
```

### ① スケジューラから直接実行（最も一般的）

そもそも REST API 経由でジョブを起動しないパターン。
アプリ起動時にジョブを実行するか、`@Scheduled` で定期実行する。

```
OS の cron:
  0 2 * * * java -jar app.jar --spring.batch.job.name=taskExportJob

クラウドサービス:
  AWS: CloudWatch Events → ECS Task / Lambda
  GCP: Cloud Scheduler → Cloud Run Jobs
  Azure: Azure Functions Timer Trigger

アプリ内:
  @Scheduled(cron = "0 0 2 * * *")  ← このプロジェクトの TaskBatchScheduler

いずれも人の手を介さないので認証自体が不要。
```

### ② 内部ネットワーク + 認証なし

管理用 API を社内ネットワーク内に限定し、認証を省略するパターン。
運用担当者が手動でリカバリ実行する場合などに使う。

```
┌─────────────────────────────────────┐
│  VPC / 社内ネットワーク              │
│                                     │
│  管理サーバー → POST /internal/batch/export  │
│                     ↓                        │
│               バッチアプリ（認証なし）          │
└─────────────────────────────────────┘
                    │
─── インターネットからはアクセス不可 ───
```

ネットワークレベルで保護するため、アプリ側の認証は不要。

### ③ API キー / トークン認証

REST API でジョブを起動する必要がある場合（外部連携、CI/CD パイプライン等）。

```bash
# API キー方式
curl -H "X-API-Key: <secret>" -X POST https://app.example.com/api/batch/export

# Bearer トークン方式
curl -H "Authorization: Bearer <token>" -X POST https://app.example.com/api/batch/export
```

### なぜ Basic 認証は使われないか

| 理由 | 説明 |
|---|---|
| **パスワードが平文で送信される** | Base64 エンコードは暗号化ではない。HTTPS 必須だが、ログに残るリスクもある |
| **認証情報の管理** | ユーザー名/パスワードをスクリプトにハードコードしがち |
| **権限の粒度** | ユーザー単位の認証であり、API 単位の細かい権限制御が難しい |
| **トークンの失効** | Basic 認証にはトークン期限の概念がなく、パスワード変更でしか無効化できない |
