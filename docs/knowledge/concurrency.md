# 並行処理ナレッジ

Java の並行処理（Concurrency）に関する知識整理。
このプロジェクトの実装コードを例に、各 API の役割と使い分けを解説する。

## 全体マップ

```
java.util.concurrent
├── スレッドプール
│   ├── ExecutorService         ← タスクの並列実行基盤
│   ├── Callable / Future       ← 戻り値ありの非同期タスク
│   └── CompletableFuture       ← 非同期パイプライン（モダン）
├── 同期プリミティブ
│   ├── ReentrantLock           ← 明示的ロック（synchronized の高機能版）
│   ├── CountDownLatch          ← 「全員完了」を待つ
│   └── CyclicBarrier           ← 「全員集合」してから一斉に進む
├── アトミック変数
│   ├── AtomicInteger           ← ロック不要のスレッドセーフ整数
│   └── AtomicLong              ← ロック不要のスレッドセーフ長整数
├── スレッドセーフなコレクション
│   ├── ConcurrentHashMap       ← セグメントロックで高並行性の Map
│   ├── CopyOnWriteArrayList    ← 書き込み時コピーで読み取り高速な List
│   └── LinkedBlockingQueue     ← Producer-Consumer パターン用キュー
└── キーワード
    └── volatile                ← メモリ可視性の保証
```

## Executor フレームワークの全体像

### なぜ `new Thread()` はアンチパターンか

```java
// アンチパターン: 直接 Thread を生成
for (Task task : tasks) {
    new Thread(() -> process(task)).start();
}
```

| 問題 | 説明 |
|---|---|
| **生成コスト** | スレッド生成にはメモリ確保（スタック領域）と OS レベルの処理が必要。毎回払う |
| **スレッド数の爆発** | 上限管理がないため、大量リクエストで無制限にスレッドが生成される → `OutOfMemoryError` |
| **ライフサイクル管理** | 例外処理、キャンセル、完了待ちを全て自分で実装する必要がある |
| **リソースの無駄** | 処理完了後にスレッドが破棄される。再利用できない |

Executor フレームワークはこれらを**スレッドプール**で解決する。

```
new Thread() の世界:
  リクエスト1 → new Thread() → 処理 → 破棄
  リクエスト2 → new Thread() → 処理 → 破棄
  リクエスト3 → new Thread() → 処理 → 破棄
  ... 10000リクエスト → 10000スレッド → OutOfMemoryError

Executor フレームワークの世界:
  スレッドプール [Thread-1] [Thread-2] [Thread-3] [Thread-4]  ← 固定数
  リクエスト1 → Thread-1 が処理 → Thread-1 がプールに戻る
  リクエスト2 → Thread-2 が処理 → Thread-2 がプールに戻る
  ...
  リクエスト5 → Thread-1 が空いたので再利用
```

### インタフェース階層

```
Executor                         ← 最上位（execute(Runnable) だけ）
  │
  └── ExecutorService            ← 主要インタフェース（submit, invokeAll, shutdown）
        │
        ├── ThreadPoolExecutor   ← 汎用スレッドプール実装
        │     ↑ Executors.newFixedThreadPool() が内部で返す
        │
        └── ForkJoinPool         ← ワークスティーリング型プール
              ↑ CompletableFuture.supplyAsync() がデフォルトで使う

Executors                        ← ファクトリクラス（プール生成のショートカット）
  ├── newFixedThreadPool(n)      → 固定 n スレッドの ThreadPoolExecutor
  ├── newCachedThreadPool()      → 必要に応じて増減するプール
  ├── newSingleThreadExecutor()  → スレッド1本のプール（順序保証）
  └── newScheduledThreadPool(n)  → 定期実行対応プール

Spring ThreadPoolTaskExecutor    ← Spring の ExecutorService ラッパー
  ↑ @Async メソッドの実行基盤（AsyncConfig で Bean 定義）
```

### Executors ファクトリの使い分け

| メソッド | プールサイズ | ユースケース |
|---|---|---|
| `newFixedThreadPool(n)` | 固定 n 本 | 並列数を制御したいバッチ処理 |
| `newCachedThreadPool()` | 0〜無制限 | 短い非同期タスクが大量に来る場合 |
| `newSingleThreadExecutor()` | 固定 1 本 | 順序保証が必要な場合 |
| `newScheduledThreadPool(n)` | 固定 n 本 | 定期実行（cron 的な用途） |

**注意**: `newCachedThreadPool()` はスレッド数に上限がなく、`new Thread()` と同じ問題を起こしうる。
実務では `ThreadPoolExecutor` を直接生成するか、Spring の `ThreadPoolTaskExecutor` で上限を設定する。

### ForkJoinPool — CompletableFuture のデフォルト実行基盤

`CompletableFuture.supplyAsync(() -> ...)` に Executor を渡さないと、
**`ForkJoinPool.commonPool()`** が使われる。

```java
// Executor 指定なし → ForkJoinPool.commonPool() で実行
CompletableFuture.supplyAsync(() -> taskJdbcRepository.countGroupByStatus());

// Executor 指定あり → 指定したプールで実行
CompletableFuture.supplyAsync(() -> taskJdbcRepository.countGroupByStatus(), myExecutor);
```

ForkJoinPool の特徴:

| 項目 | ThreadPoolExecutor | ForkJoinPool |
|---|---|---|
| スレッド数 | 固定 or 範囲指定 | デフォルトは CPU コア数 - 1 |
| タスク分配 | キューから1つずつ取得 | **ワークスティーリング**（暇なスレッドが他から奪う） |
| 向いている処理 | I/O バウンド（DB, HTTP） | CPU バウンド（計算、ソート） |
| 共有 | 自分で作成・管理 | JVM 全体で 1 つ（commonPool） |

**ワークスティーリング**:
```
通常のプール:
  Thread-1: [タスクA][タスクB][タスクC]  ← 忙しい
  Thread-2: [タスクD]                   ← 暇だが他のタスクは取れない

ForkJoinPool:
  Thread-1: [タスクA][タスクB][タスクC]  ← 忙しい
  Thread-2: [タスクD][ C を奪って実行 ]  ← 暇なので Thread-1 から奪う
```

**このプロジェクトでの使用箇所**: `DashboardService.getDashboard()` の `supplyAsync()`

### このプロジェクトでの 3 パターン

```
Executor フレームワークの利用パターン
  │
  ├── パターン1: ExecutorService 直接利用
  │     TaskBatchService.processOverdueTasks()
  │     自分でプール作成 → invokeAll → Future.get() → shutdown
  │     用途: 全結果を待つバッチ処理
  │
  ├── パターン2: CompletableFuture（ForkJoinPool 暗黙利用）
  │     DashboardService.getDashboard()
  │     supplyAsync × 5 → allOf().join()
  │     用途: 複数の独立した処理を合流
  │
  └── パターン3: Spring @Async（ThreadPoolTaskExecutor）
        TaskNotificationService.notifyTaskCreated()
        @Async("taskExecutor") → CompletableFuture チェーン
        用途: Fire-and-forget（投げっぱなし非同期）
```

#### パターン1: ExecutorService + invokeAll — 全結果を待つ

```java
// TaskBatchService.processOverdueTasks()
ExecutorService executor = Executors.newFixedThreadPool(Math.min(size, 4));
try {
    List<Callable<Boolean>> callables = overdueTasks.stream()
            .<Callable<Boolean>>map(task -> () -> processOneTask(task))
            .toList();
    List<Future<Boolean>> futures = executor.invokeAll(callables);  // 全実行・全完了待ち
    for (Future<Boolean> future : futures) {
        future.get();  // 結果を1つずつ取得
    }
} finally {
    executor.shutdown();  // 必ず shutdown（リソースリーク防止）
}
```

**ライフサイクル**: 作成 → 使用 → shutdown がメソッド内で完結。

#### パターン2: CompletableFuture.allOf — 合流パターン

```java
// DashboardService.getDashboard()
CompletableFuture<Map<String, Long>> statusFuture =
        CompletableFuture.supplyAsync(() -> jdbcRepo.countGroupByStatus());
CompletableFuture<Long> totalFuture =
        CompletableFuture.supplyAsync(taskRepo::count);

CompletableFuture.allOf(statusFuture, totalFuture).join();  // 全完了を待つ

Map<String, Long> status = statusFuture.join();  // 既に完了しているので即取得
```

**ライフサイクル**: ForkJoinPool.commonPool() が管理。自分で shutdown 不要。

#### パターン3: @Async — Spring 管理プール

```java
// AsyncConfig: プール定義
@Bean(name = "taskExecutor")
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);     // 常時 2 スレッド
    executor.setMaxPoolSize(4);      // 最大 4 スレッド
    executor.setQueueCapacity(50);   // キュー上限 50
    return executor;
}

// TaskNotificationService: @Async で非同期実行
@Async("taskExecutor")
public CompletableFuture<Void> notifyTaskCreated(Task task) {
    return CompletableFuture.supplyAsync(() -> { ... })
            .thenApply(...)
            .exceptionally(...)
            .thenAccept(...);
}
```

**ライフサイクル**: Spring コンテナが管理。アプリ終了時に自動 shutdown。

### 3 パターンの選び方

```
並列処理が必要
  │
  ├── 全タスクの結果を待ちたい？
  │     └── YES → ExecutorService + invokeAll（パターン1）
  │
  ├── 複数の独立処理を合流させたい？
  │     └── YES → CompletableFuture.allOf（パターン2）
  │
  └── 呼び出し元は結果を待たなくていい？
        └── YES → @Async（パターン3）
```

## スレッドプール

### ExecutorService — タスクの並列実行基盤

スレッドの生成・管理をフレームワークに委ねる。直接 `new Thread()` するのは現場ではアンチパターン（上記参照）。

```java
// スレッドプール作成（最大4スレッド）
ExecutorService executor = Executors.newFixedThreadPool(4);

try {
    // Callable（戻り値あり）のリストを一括実行
    List<Callable<Boolean>> callables = tasks.stream()
            .<Callable<Boolean>>map(task -> () -> processOneTask(task))
            .toList();

    List<Future<Boolean>> futures = executor.invokeAll(callables);

    // 結果を取得（ブロッキング）
    for (Future<Boolean> future : futures) {
        boolean success = future.get();  // 完了まで待つ
    }
} finally {
    executor.shutdown();  // 必ずシャットダウン（リソースリーク防止）
}
```

**このプロジェクトでの使用箇所**: `TaskBatchService.processOverdueTasks()`

### Callable vs Runnable

| | Runnable | Callable\<V\> |
|---|---|---|
| 戻り値 | なし (`void`) | あり (`V`) |
| 例外 | チェック例外をスローできない | スローできる |
| 用途 | 結果不要の非同期処理 | 結果を返す非同期処理 |

### Future — 非同期結果の受け取り

```java
Future<String> future = executor.submit(() -> "結果");

future.isDone();     // 完了したか？
future.get();        // 結果を取得（ブロッキング）
future.get(5, TimeUnit.SECONDS);  // タイムアウト付き
future.cancel(true); // キャンセル
```

**制約**: `get()` はブロッキング。チェーンやコールバックができない → CompletableFuture で解決。

## CompletableFuture — 非同期パイプライン

`Future` の進化版。非同期処理をメソッドチェーンで組み立てられる。

### 基本パターン

```java
CompletableFuture.supplyAsync(() -> {
            // 非同期で値を生成
            return "データ";
        })
        .thenApply(data -> {
            // 変換（map に相当）
            return data.toUpperCase();
        })
        .exceptionally(ex -> {
            // エラーハンドリング（catch に相当）
            return "fallback";
        })
        .thenAccept(result -> {
            // 最終消費（戻り値なし）
            log.info("Result: {}", result);
        });
```

**このプロジェクトでの使用箇所**: `TaskNotificationService`

### 複数の非同期処理を合流

```java
CompletableFuture<Map<String, Long>> statusFuture =
        CompletableFuture.supplyAsync(() -> countByStatus());

CompletableFuture<Long> totalFuture =
        CompletableFuture.supplyAsync(() -> countAll());

// 全部の完了を待つ
CompletableFuture.allOf(statusFuture, totalFuture).join();

// 結果を取り出す
Map<String, Long> status = statusFuture.join();
long total = totalFuture.join();
```

**このプロジェクトでの使用箇所**: `DashboardService.getDashboard()`

### CompletableFuture 主要メソッド

| メソッド | 説明 | Stream での類似 |
|---|---|---|
| `supplyAsync(() -> ...)` | 非同期で値を生成 | ソース |
| `thenApply(x -> ...)` | 値を変換 | `map` |
| `thenCompose(x -> ...)` | 別の CompletableFuture に繋ぐ | `flatMap` |
| `thenAccept(x -> ...)` | 値を消費（戻り値なし） | `forEach` |
| `exceptionally(ex -> ...)` | エラー時のフォールバック | catch |
| `allOf(cf1, cf2, ...)` | 全ての完了を待つ | — |
| `anyOf(cf1, cf2, ...)` | 最初の完了を待つ | — |
| `join()` | 結果を取得（非チェック例外） | — |
| `get()` | 結果を取得（チェック例外） | — |

## 同期プリミティブ

### ReentrantLock — 明示的ロック

`synchronized` の高機能版。`try/finally` で必ずアンロックする。

```java
private final ReentrantLock lock = new ReentrantLock();

public String generate() {
    lock.lock();          // ロック取得
    try {
        // ここはスレッドセーフ（1スレッドのみ実行可能）
        return doWork();
    } finally {
        lock.unlock();    // 必ずアンロック
    }
}
```

**このプロジェクトでの使用箇所**: `TaskNumberGenerator`, `TaskBatchService`

### synchronized vs ReentrantLock

| | synchronized | ReentrantLock |
|---|---|---|
| 記法 | ブロック / メソッド修飾子 | `lock()` / `unlock()` |
| tryLock | 不可 | `tryLock(timeout)` 可能 |
| 条件変数 | `wait/notify` | `Condition` オブジェクト |
| 公平性 | 制御不可 | `new ReentrantLock(true)` で公平ロック |
| 推奨 | 単純な排他制御 | タイムアウトや高度な制御が必要な場合 |

### CountDownLatch — 「全員完了」を待つ

カウンタが 0 になるまで `await()` でブロックする。**1回限り**。

```
初期値 3
            Thread A: countDown() → カウンタ 2
            Thread B: countDown() → カウンタ 1
            Thread C: countDown() → カウンタ 0
Main Thread: await() ──────────────────→ ブロック解除！
```

```java
CountDownLatch latch = new CountDownLatch(3);

// 各ワーカーが完了時に countDown
executor.submit(() -> {
    doPreparation();
    latch.countDown();  // カウンタ -1
});

// メインスレッドで全完了を待つ
latch.await(5, TimeUnit.SECONDS);
log.info("全前処理が完了。バッチ開始。");
```

**このプロジェクトでの使用箇所**: `TaskBatchService.demonstrateCountDownLatch()`

### CyclicBarrier — 「全員集合」して一斉に進む

全スレッドがバリアに到達するまで待ち、揃ったら一斉に次のフェーズへ。**再利用可能**。

```
            Thread A: barrier.await() ──→ 待機中...
            Thread B: barrier.await() ──→ 待機中...
            Thread C: barrier.await() ──→ 全員到達！→ バリアアクション実行 → 全員解放
```

```java
CyclicBarrier barrier = new CyclicBarrier(3, () ->
        log.info("全ワーカーがバリアに到達 — 次のフェーズへ"));

executor.submit(() -> {
    doPhase1();
    barrier.await();  // 他のスレッドを待つ
    doPhase2();       // 全員揃ったら実行
});
```

**このプロジェクトでの使用箇所**: `TaskBatchService.demonstrateCyclicBarrier()`

### CountDownLatch vs CyclicBarrier

| | CountDownLatch | CyclicBarrier |
|---|---|---|
| 目的 | 「全員の完了」を待つ | 「全員の合流」を待つ |
| 誰が待つ | 別のスレッド（await する側） | 参加スレッド自身 |
| 再利用 | 不可（1回限り） | 可能（`reset()` で再利用） |
| バリアアクション | なし | 全員到達時に実行されるコールバック |
| ユースケース | 初期化完了待ち、テストの準備待ち | フェーズ同期、並列計算の区切り |

## アトミック変数

### AtomicInteger / AtomicLong

`synchronized` や `Lock` なしでスレッドセーフに整数を操作できる。内部的には CAS（Compare-And-Swap）命令を使用。

```java
private final AtomicInteger processedCount = new AtomicInteger(0);

// スレッドセーフなインクリメント
processedCount.incrementAndGet();  // ++i 相当
processedCount.getAndIncrement();  // i++ 相当

// スレッドセーフなリセット
processedCount.set(0);

// 現在値の取得
int count = processedCount.get();
```

**このプロジェクトでの使用箇所**: `TaskBatchService`（処理件数カウント）, `TaskNumberGenerator`（シーケンス番号）

### CAS (Compare-And-Swap) — Atomic 変数の内部動作

CAS は CPU レベルで提供されるアトミック操作。「比較して入れ替え」を CPU 命令1つで行う。

```
CAS(メモリ位置, 期待する値, 新しい値)

1. メモリの現在値を読む
2. 期待する値と一致するか比較する
3. 一致 → 新しい値に書き換え（成功）
   不一致 → 何もしない（失敗 → 読み直してリトライ）

この 1〜3 が CPU 命令1つで行われるので途中で割り込まれない
```

`incrementAndGet()` の内部動作:

```
Thread-1: count を読む (5) → CAS(count, 5, 6) → 現在値 5? YES → 6 に更新。成功！
Thread-2: count を読む (5) → CAS(count, 5, 6) → 現在値 5? NO（もう6）→ 失敗
          count を読み直す (6) → CAS(count, 6, 7) → YES → 7 に更新。成功！
```

### CAS vs synchronized

| | synchronized | CAS (Atomic) |
|---|---|---|
| 競合時の動作 | 他スレッドをブロック | 失敗したらリトライ |
| オーバーヘッド | ロック取得/解放 | CPU 命令1つ |
| 競合が少ない時 | CAS より遅い | 高速 |
| 競合が激しい時 | 安定 | リトライが増えて遅くなりうる |
| 向いている用途 | 複雑な処理の排他制御 | 単純なカウンタ・フラグ |

このプロジェクトでは採番やバッチカウントという軽い操作なので CAS が適している。

### いつ使うか

| 状況 | 使うもの |
|---|---|
| 単純なカウンタ・フラグ | AtomicInteger / AtomicLong |
| 複数の変数を同時に更新 | synchronized / ReentrantLock |
| 読み書き両方が頻繁 | ReentrantLock |
| 読みが多く書きが少ない | ReentrantReadWriteLock |

## volatile

変数の**メモリ可視性**を保証するキーワード。あるスレッドの書き込みが、別のスレッドから即座に見える。

```java
private volatile String currentDate = "";
```

**注意**: volatile は可視性のみ保証。`i++` のような複合操作はアトミックにならない → `AtomicInteger` を使う。

```
volatile で OK  : boolean フラグ、参照の切り替え
volatile では不足: カウンタのインクリメント（読み→加算→書き込みの3操作）
```

**このプロジェクトでの使用箇所**: `TaskNumberGenerator.currentDate`

## 楽観的ロック（@Version）

DB レベルの並行制御。`java.util.concurrent` とは別のアプローチ。

```java
@Entity
public class Task {
    @Version
    private Long version;  // JPA が自動管理
}
```

```
User A: version=0 で読み取り → 編集 → version=0 で保存 → OK（version=1 に更新）
User B: version=0 で読み取り → 編集 → version=0 で保存 → 失敗！（既に version=1）
         → OptimisticLockException スロー
```

| | 悲観的ロック | 楽観的ロック |
|---|---|---|
| 方式 | SELECT ... FOR UPDATE | @Version カラム比較 |
| ロックタイミング | 読み取り時 | 書き込み時 |
| 競合が多い場合 | 待機（デッドロックリスク） | 例外で検知 |
| 競合が少ない場合 | 不要なロックのオーバーヘッド | 効率的 |
| 一般的な用途 | 在庫管理、座席予約 | Web アプリのフォーム更新 |

**このプロジェクトでの使用箇所**: `Task.version` + `TaskUpdateRequest.version`

## Spring @Async — フレームワーク統合

Spring の `@Async` を使うと、メソッド呼び出しが自動的に別スレッドで実行される。

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);    // 常時稼働スレッド数
        executor.setMaxPoolSize(4);     // 最大スレッド数
        executor.setQueueCapacity(50);  // キュー容量
        executor.setThreadNamePrefix("task-async-");
        executor.initialize();
        return executor;
    }
}
```

```java
@Async("taskExecutor")
public CompletableFuture<Void> notifyTaskCreated(Task task) {
    // このメソッドは別スレッドで実行される
    return CompletableFuture.supplyAsync(() -> { ... });
}
```

**注意点**:
- 同一クラス内のメソッド呼び出しでは `@Async` は効かない（プロキシ経由でないため）
- 戻り値は `void` か `Future` / `CompletableFuture`

**このプロジェクトでの使用箇所**: `TaskNotificationService`, `AsyncConfig`

## 使い分けフローチャート

```
並行処理が必要
  │
  ├── 単純なカウンタ/フラグ？
  │     └── YES → AtomicInteger / AtomicLong
  │
  ├── 非同期パイプライン（チェーン）が必要？
  │     └── YES → CompletableFuture
  │
  ├── 複数タスクを並列実行して結果を集めたい？
  │     └── YES → ExecutorService + Callable/Future
  │
  ├── 全ワーカーの完了を待ちたい？
  │     ├── 待つ側と作業する側が別 → CountDownLatch
  │     └── 全員が合流してから進む → CyclicBarrier
  │
  ├── 共有リソースの排他制御？
  │     ├── 単純 → synchronized
  │     └── タイムアウト/条件変数が必要 → ReentrantLock
  │
  ├── スレッドセーフなコレクションが必要？
  │     ├── Map → ConcurrentHashMap
  │     ├── List（読み多・書き少） → CopyOnWriteArrayList
  │     └── Producer-Consumer キュー → BlockingQueue
  │
  └── DB の並行更新制御？
        ├── 競合が少ない → @Version（楽観的ロック）
        └── 競合が多い → SELECT FOR UPDATE（悲観的ロック）
```

---

## 並行 (Concurrency) vs 並列 (Parallelism)

### 定義

- **並行 (Concurrency)**: 複数のタスクを**論理的に同時に**扱う設計。シングルコアでも成立する
- **並列 (Parallelism)**: 複数のタスクを**物理的に同時に**実行すること。マルチコアが必要

```
マルチコア (4コア) — 真の並列実行:
  Core 1: ──[statusCounts]──
  Core 2: ──[priorityCounts]──
  Core 3: ──[total]──
  Core 4: ──[overdue]──
  → 最も遅い1クエリ分の時間で完了

シングルコア — 並行（交互実行）:
  Core 1: ──[status]──[priority]──[total]──[overdue]──
  → CPU バウンドな処理では逐次実行と変わらない（切り替えオーバーヘッド分むしろ遅い）
```

### シングルコアでも効果がある場合: I/O バウンド処理

DB クエリ・HTTP 通信・ファイル読み書きなど **I/O 待ちが発生する処理** では、
1スレッドが応答を待っている間に別スレッドが次の処理を開始できる。

```
シングルコアでの I/O バウンド処理（DashboardService の例）:
  Thread 1: [クエリ発行]──[I/O待ち]──────[結果受信]
  Thread 2:    [クエリ発行]──[I/O待ち]──────[結果受信]
  → I/O 待ちの間に CPU を有効活用。逐次より速い
```

このプロジェクトの `DashboardService` は DB クエリ（I/O バウンド）なので、
シングルコアでも並行実行による恩恵がある。

## Future vs CompletableFuture — 本質的な違い

### Future: 「結果の引換券」— ブロッキングで待つしかない

```java
Future<String> future = executor.submit(() -> "結果");
String result = future.get();  // ← ここでブロック。完了まで動けない
```

- `get()` を呼ぶとスレッドが止まる
- 「終わったら次にこれをやって」という宣言ができない
- エラー処理は `try-catch` で `ExecutionException` を捕まえるしかない

### CompletableFuture: 「処理パイプライン」— ノンブロッキングで繋げられる

```java
CompletableFuture.supplyAsync(() -> "データ")    // 別スレッドで実行
    .thenApply(data -> data.toUpperCase())        // 終わったら変換（map 相当）
    .exceptionally(ex -> "fallback")              // エラー時（catch 相当）
    .thenAccept(result -> log.info(result));       // 終わったら消費（forEach 相当）
```

- **ブロックせずに「次にやること」を宣言的に繋げられる** のが本質
- Stream API の `map` / `flatMap` / `forEach` と同じ感覚で非同期処理を組み立てられる

### 比較表

| | Future | CompletableFuture |
|---|---|---|
| 結果の取得 | `get()` でブロッキングのみ | `join()` もコールバックも可 |
| チェーン | 不可 | `thenApply` → `thenAccept` → ... |
| エラー処理 | try-catch で `ExecutionException` | `exceptionally()` でチェーン内に組み込み |
| 複数の合流 | 自分でループして `get()` | `allOf()` / `anyOf()` |
| 手動完了 | 不可 | `complete(value)` で外から完了させられる |
| 導入バージョン | Java 5 | Java 8 |

### このプロジェクトでの使い分け

- `TaskBatchService`: `ExecutorService.invokeAll()` → `Future.get()` で結果取得（単純な並列バッチ向き）
- `DashboardService`: `CompletableFuture.allOf()` で複数クエリを合流（パイプライン向き）
- `TaskNotificationService`: `CompletableFuture` チェーンで非同期通知（コールバック向き）

## 並列処理と排他制御 (synchronized) の関係

### 「アクセルとブレーキ」の関係

```
並列化:   処理を速くするために複数スレッドで同時実行（アクセル）
排他制御: 壊れる箇所だけ1スレッドずつ通す（ブレーキ）
```

並列処理をすると「同時に実行されたら困るもの」が出てくる。
それを守るのが `synchronized` や `ReentrantLock` の役割。

### なぜ困るのか — 競合状態 (Race Condition)

```java
// 危険な例: 2スレッドが同時に実行すると値がおかしくなる
private int count = 0;

public void increment() {
    count++;  // 読み取り → 加算 → 書き込み の3操作
}
```

```
Thread A: count を読む (0) → +1 を計算 → 書き込み (1)
Thread B:     count を読む (0) → +1 を計算 → 書き込み (1)
                                               ↑ 2 になるべきが 1 に！
```

### このプロジェクトでの実例

**TaskNumberGenerator** — 日付チェックと採番の間に割り込まれると壊れる:

```java
lock.lock();
try {
    if (!today.equals(currentDate)) {  // ← ここと
        currentDate = today;
        sequence.set(0);               // ← ここの間に別スレッドが入ると
    }                                  //   古い日付で採番してしまう
    long seq = sequence.incrementAndGet();
    return String.format("TASK-%s-%04d", today, seq);
} finally {
    lock.unlock();
}
```

**TaskBatchService** — `batchLock` でバッチ全体を排他制御し、二重実行を防止:

```java
batchLock.lock();
try {
    // バッチ処理（2つのリクエストが同時に来ても1つずつ実行）
} finally {
    batchLock.unlock();
}
```

### 排他制御は「必要最小限」に

ロック範囲が広いほど並列性が下がり、パフォーマンスが落ちる。

```
悪い例（ロック範囲が広すぎる）:
  synchronized(this) {
    DBクエリ();     // ← ロック不要なのに待たされる
    共有変数更新();  // ← ここだけロックすればいい
    ログ出力();     // ← ロック不要
  }

良い例（必要最小限のロック）:
  DBクエリ();       // 並列OK
  synchronized(this) { 共有変数更新(); }  // ここだけ排他
  ログ出力();       // 並列OK
```

### synchronized を使うべき場面の判断基準

| 共有リソースの状況 | 排他制御 |
|---|---|
| 複数スレッドから **読み書き** される変数 | 必要 |
| 複数スレッドから **読みのみ** の変数（不変） | 不要 |
| 各スレッドが **独立したデータ** を処理 | 不要 |
| 複数の変数を **一貫した状態** で更新する必要がある | 必要（Lock が適切） |
| 単純なカウンタの加減算 | `AtomicInteger` で十分（Lock 不要） |

## このプロジェクトの実装別解説

### AsyncConfig — スレッドプール定義

`@Async` メソッド用のスレッドプールを Bean 定義する。

| 設定 | 値 | 意味 |
|---|---|---|
| `corePoolSize` | 2 | 常時稼働するスレッド数 |
| `maxPoolSize` | 4 | キューが一杯になった時の最大スレッド数 |
| `queueCapacity` | 50 | コアスレッドが埋まった時の待ちキュー上限 |
| `threadNamePrefix` | `task-async-` | ログで識別するための接頭辞 |

### TaskNumberGenerator — 3つの並行処理プリミティブの組み合わせ

- **`ReentrantLock`**: `generate()` 全体をロックし、日付チェック〜採番をアトミックに実行
- **`AtomicLong`**: シーケンス番号の CAS ベースのスレッドセーフなインクリメント
- **`volatile String currentDate`**: 日付文字列のメモリ可視性を保証

### DashboardService — CompletableFuture.allOf で並列集計

5つの DB クエリを同時実行し、全完了を待ってレスポンスを組み立てる:

```
supplyAsync → statusCounts    ─┐
supplyAsync → priorityCounts  ─┤
supplyAsync → total           ─┼→ allOf().join() → 全完了を待つ → レスポンス組立
supplyAsync → overdue         ─┤
supplyAsync → doneCount       ─┘
```

### TaskBatchService — ExecutorService + 同期プリミティブ

- `processOverdueTasks()`: `invokeAll()` で全 Callable を並列実行 + `AtomicInteger` で成功件数カウント
- `demonstrateCountDownLatch()`: 待つ側(Main)と作業側(Worker)が別。1回きり
- `demonstrateCyclicBarrier()`: 参加スレッド自身が互いを待つ。再利用可能

### TaskNotificationService — @Async + CompletableFuture チェーン

Spring `@Async` でメソッド自体を別スレッド実行しつつ、内部で CompletableFuture パイプラインを構築。
**注意**: `@Async` は Spring プロキシ経由でないと効かない（同一クラス内呼び出しでは同期実行になる）。

## ブロッキング vs ノンブロッキング

### ブロッキング — 結果が返るまでスレッドが止まる

```java
Future<String> future = executor.submit(() -> heavyWork());
String result = future.get();  // ← heavyWork() が終わるまでこのスレッドは停止
doNext(result);                // ← get() が返るまで到達しない
```

電話で保留にされている状態。受話器を持ったまま何もできない。

### ノンブロッキング — 指示だけ出してすぐ次に進める

```java
CompletableFuture.supplyAsync(() -> heavyWork())
    .thenAccept(result -> doNext(result));  // ← 終わったら勝手に実行される
doSomethingElse();  // ← 待たずに別の仕事ができる
```

メッセージを送って返事を待たずに別の作業をする状態。

### スレッドの動きの比較

```
ブロッキング:
  Thread: ──[submit]──[... 待機中 ...  何もできない ...]──[get()で結果取得]──[次の処理]──

ノンブロッキング:
  Thread: ──[submit + コールバック登録]──[別の仕事]──[別の仕事]──
  Pool  :   ──[heavyWork]──[完了 → コールバック自動実行]──
```

### 注意

`CompletableFuture` でも `join()` や `get()` を呼べばブロッキングになる。
`DashboardService` の `allOf().join()` がまさにその例で、
「5つ全部終わるまではブロックして待つ」という意図的な使い方。

## synchronized vs ReentrantLock 詳細比較

### synchronized — 暗黙的ロック

```java
// メソッド全体をロック（this がロック対象）
public synchronized void update() {
    sharedData++;
}

// ブロック単位でロック
public void update() {
    synchronized (this) {
        sharedData++;
    }
}
```

- ブロックを抜けると**自動的にアンロック**される（例外時も安全）
- 構文がシンプル
- 細かい制御はできない

### ReentrantLock — 明示的ロック

```java
private final ReentrantLock lock = new ReentrantLock();

public void update() {
    lock.lock();
    try {
        sharedData++;
    } finally {
        lock.unlock();  // 自分で解放しないとデッドロック
    }
}
```

- `lock()` / `unlock()` を**自分で管理**する
- `finally` で `unlock()` を忘れるとロックが永久に解放されない

### ReentrantLock にしかできないこと

**1. tryLock — ロック取得の試行とタイムアウト**

```java
// synchronized ではできない: ロック取得を「諦める」
if (lock.tryLock(3, TimeUnit.SECONDS)) {
    try {
        doWork();
    } finally {
        lock.unlock();
    }
} else {
    // 3秒待ってもロック取得できなかった → 別の処理
    log.warn("ロック取得失敗、スキップ");
}
```

`synchronized` はロックが取れるまで**無限に待ち続ける**。タイムアウトの概念がない。

**2. 公平性の制御**

```java
// 待ちが長いスレッドから順にロックを取得（FIFO 順）
ReentrantLock fairLock = new ReentrantLock(true);

// synchronized は公平性を制御できない（どのスレッドが次に取得するかは不定）
```

**3. Condition — 複数の待ち条件を分離**

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition notEmpty = lock.newCondition();  // 「空でない」条件
private final Condition notFull  = lock.newCondition();  // 「満杯でない」条件

// Producer
lock.lock();
try {
    while (queue.isFull()) notFull.await();   // 満杯なら待つ
    queue.add(item);
    notEmpty.signal();                         // 「空でない」を通知
} finally {
    lock.unlock();
}

// synchronized の wait/notify では条件を分離できない
```

### 比較表

| | synchronized | ReentrantLock |
|---|---|---|
| ロック解放 | 自動（ブロック終了時） | 手動（`unlock()` 必須） |
| タイムアウト | 不可（無限に待つ） | `tryLock(timeout)` 可 |
| 公平性 | 制御不可 | `new ReentrantLock(true)` |
| 条件分岐 | `wait/notify`（1条件） | `Condition`（複数条件） |
| デッドロック回避 | 難しい | `tryLock` で回避しやすい |
| 記述量 | 少ない | 多い（try/finally 必須） |
| 解放忘れリスク | なし（自動） | あり（finally 必須） |

### 選び方の目安

```
排他制御が必要
  │
  ├── 単純にブロックを保護するだけ？
  │     └── YES → synchronized（シンプルで安全）
  │
  └── 以下のどれかが必要？
        ├── タイムアウト付きロック取得  → ReentrantLock + tryLock
        ├── 公平性の保証              → ReentrantLock(true)
        ├── 複数の待ち条件            → ReentrantLock + Condition
        └── ロック取得失敗時の代替処理  → ReentrantLock + tryLock
```

基本は `synchronized` で十分。
「`synchronized` では足りない」と判断した時に `ReentrantLock` に切り替えるのが実務的。

### このプロジェクトでの使い分け

- **`TaskNumberGenerator`**: `ReentrantLock` を使用。将来 `tryLock` でタイムアウト制御に発展できる設計
- **`TaskBatchService`**: `ReentrantLock` を使用。`tryLock` に切り替えれば「既に実行中ならスキップ」パターンに発展可能

## wait() / notify() — スレッド間の待ち合わせ

`synchronized` ブロック内で使う**スレッド間通信**の仕組み。
`wait()` には典型的に **2つのパターン** がある。

### パターン1: 暇なとき — 「仕事が来るまで」眠る wait()

**働く側（Consumer / Worker）** がキューを見て、空なら眠る。

```java
// Consumer: 仕事を取り出す側
synchronized (queue) {
    while (queue.isEmpty()) {   // 仕事がない
        queue.wait();           // 「仕事が来るまで」眠る 💤
    }
    Task task = queue.remove(); // 起こされた → 仕事を取り出す
}
```

```
Queue: [  空  ]
Consumer: isEmpty()? → YES → wait() で眠る 💤
          ...
Producer: queue.add(task) → notify() で起こす
Consumer: 起床 → isEmpty()? → NO → remove() で仕事を取る
```

### パターン2: 忙しすぎるとき — 「席が空くまで」眠る wait()

**頼む側（Producer / Client）** がキューを見て、満杯なら眠る。

```java
// Producer: 仕事を追加する側
synchronized (queue) {
    while (queue.size() >= MAX_SIZE) {  // 席が満杯
        queue.wait();                    // 「席が空くまで」眠る 💤
    }
    queue.add(newTask);                  // 起こされた → 仕事を投入
    queue.notify();                      // Consumer に「仕事あるよ」と通知
}
```

```
Queue: [task][task][task] ← 満杯
Producer: isFull()? → YES → wait() で眠る 💤
          ...
Consumer: queue.remove() → notify() で起こす
Producer: 起床 → isFull()? → NO → add() で仕事を追加
```

### 2つの wait() を組み合わせた全体像 — Producer-Consumer パターン

```
Producer                    Queue (上限あり)              Consumer
────────                    ────────────────              ────────
仕事を作る                                                仕事を待つ
  │                                                         │
  ├─ 満杯？─YES→ wait()💤   [task][task][task]   空？─YES→ wait()💤
  │                                                         │
  └─ NO → add() ──────────→ [task][task][task][new] ←───── remove()
          notify() ─────────────────────────────────→ 起床！
```

両方の wait() が **同じキューオブジェクト** で行われるのがポイント。

### wait/notify のルール

| ルール | 理由 |
|---|---|
| `synchronized` ブロック内でのみ使える | ロックを持っていないと wait/notify できない |
| `while` ループで条件チェック | `if` だと **spurious wakeup**（偽の起床）で壊れる |
| `notify()` は1スレッドだけ起こす | 誰が起きるかは不定 |
| `notifyAll()` は全スレッドを起こす | 安全だが、不要な起床が発生しうる |

### なぜ while であって if ではないのか

```java
// ダメな例
synchronized (queue) {
    if (queue.isEmpty()) {     // ← if だと1回しかチェックしない
        queue.wait();
    }
    queue.remove();            // ← 別のConsumerが先に取っていたら空で例外！
}

// 正しい例
synchronized (queue) {
    while (queue.isEmpty()) {  // ← 起床後にもう一度チェック
        queue.wait();
    }
    queue.remove();            // ← 確実にデータがある
}
```

```
Consumer A: isEmpty()→YES → wait()
Consumer B: isEmpty()→YES → wait()
Producer:   add() → notifyAll()
Consumer A: 起床 → while: isEmpty()→NO → remove() 成功
Consumer B: 起床 → while: isEmpty()→YES → また wait()  ← if だとここで壊れる
```

### wait/notify vs java.util.concurrent の対応

| synchronized + wait/notify | java.util.concurrent 相当 |
|---|---|
| `wait()` | `Condition.await()` |
| `notify()` | `Condition.signal()` |
| `notifyAll()` | `Condition.signalAll()` |
| 手動で Producer-Consumer を書く | `BlockingQueue`（全部やってくれる） |

実務では `BlockingQueue` や `Condition` を使う方が安全。
`wait/notify` は仕組みの理解と試験対策として重要。

## デッドロックの回避

### デッドロックの4条件（全て揃うと発生）

```
1. 排他制御       — ロックを使っている
2. 保持して待機   — ロックAを持ったままロックBを待つ
3. 横取り不可     — 他スレッドのロックを強制解放できない
4. 循環待ち       — A→B→A のようにロック待ちが循環する
```

4つのうち1つでも崩せばデッドロックは起きない。

### このプロジェクトでの回避策

**1. ロックは常に1つだけ取得する（条件2・4を崩す）**

```
デッドロックの典型例:
  Thread A: lock1 取得 → lock2 を待つ
  Thread B: lock2 取得 → lock1 を待つ
  → 永久に互いを待ち続ける
```

このプロジェクトでは各メソッドが1つのロックしか取らないため、構造的に起きない。

- `TaskNumberGenerator.generate()` → `lock` のみ
- `TaskBatchService.processOverdueTasks()` → `batchLock` のみ

**2. finally で必ず unlock / shutdown**

ロック解放漏れは他スレッドが永久ブロックされる原因になる。全箇所で `try/finally` を徹底。

```java
lock.lock();
try { ... } finally { lock.unlock(); }         // TaskNumberGenerator
try { ... } finally { executor.shutdown(); }    // TaskBatchService
```

**3. タイムアウト付き待機**

無期限待機は相手のデッドロックに巻き込まれるリスクがある。

```java
latch.await(5, TimeUnit.SECONDS);       // TaskBatchService:137
barrier.await(5, TimeUnit.SECONDS);     // TaskBatchService:167
future.get(10, TimeUnit.SECONDS);       // TaskBatchService:175
```

**4. InterruptedException の適切な処理**

```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // 割り込みフラグを復元
}
```

フラグを復元しないと、上位の呼び出し元がスレッドの中断を検知できなくなる。

### やっていないこと

| 手法 | 状況 |
|---|---|
| `tryLock(timeout)` | 未使用。`lock()` で無期限待機 |
| ロック取得順序の明文化 | ロックが1つなので不要 |

ロックが1つなので `tryLock` は不要だが、将来ロックが複数になるなら切り替えが必要。

## ライブロック (Livelock)

デッドロックと違い**スレッドは動いている**が、互いに譲り合い続けて**処理が進まない**状態。

```
デッドロック: 2人が廊下で向き合って、どちらも動かない（固まる）
ライブロック: 2人が廊下で向き合って、同時に同じ方向に避け続ける（動いているが進まない）
```

### なぜ tryLock でライブロックが起きるか

`tryLock` 自体が悪いのではなく、**tryLock + 即リトライ**の組み合わせが原因。

```java
// Thread A と Thread B が lock1, lock2 の両方を取りたい
while (true) {
    if (lock1.tryLock()) {
        if (lock2.tryLock()) {
            return;  // 両方取れた → 処理
        }
        lock1.unlock();  // lock2 が取れなかったので lock1 を手放す
    }
    // すぐリトライ ← ここが問題
}
```

```
Thread A: lock1 取得 → lock2 失敗 → lock1 解放 → すぐリトライ
Thread B: lock2 取得 → lock1 失敗 → lock2 解放 → すぐリトライ
Thread A: lock1 取得 → lock2 失敗 → lock1 解放 → すぐリトライ
... 永遠に繰り返し
```

「取れなかったら手放す」という**譲る動作**を両者が**同じタイミング**で繰り返す。

`lock()` は譲らない（ブロックする）ので、この問題が起きない。

### 回避策: リトライのタイミングをずらす

```java
while (true) {
    if (lock1.tryLock()) {
        if (lock2.tryLock()) {
            return;  // 成功
        }
        lock1.unlock();
    }
    Thread.sleep(random.nextInt(100));  // ← ランダムに待つことでタイミングがずれる
}
```

```
Thread A: lock1 取得 → lock2 失敗 → 解放 → 73ms 待ち
Thread B: lock2 取得 → lock1 失敗 → 解放 → 12ms 待ち ← 先にリトライ → 成功！
```

`tryLock` を使うこと自体は問題なく、**失敗時にすぐリトライしない**ことがポイント。
リトライ回数に上限を設けるのも有効。

### このプロジェクトでの状況

`tryLock` を使っていないため、ライブロックは構造的に起きない。
`lock()` は取得できるまでブロックするので「譲り合い」が発生しない。

## スタベーション (Starvation)

特定のスレッドが**いつまでもロックを取得できず、処理が進まない**状態。
デッドロックやライブロックと違い、他のスレッドは正常に動いている。

```
例: 3つのスレッドがロックを奪い合う
  Thread A: ロック取得 → 処理 → 解放 → ロック取得 → 処理 → ...（何度も取れる）
  Thread B: ロック取得 → 処理 → 解放 → ロック取得 → ...（何度も取れる）
  Thread C: 待ち → 待ち → 待ち → 待ち → ...（永遠に取れない）
```

### 原因

- **不公平なロック** — `synchronized` や `new ReentrantLock()` はロック取得順が不定。運の悪いスレッドが永遠に後回しにされうる
- **優先度の偏り** — 高優先度スレッドが常にロックを先取りする

### 回避策

```java
// 公平ロック: 待ちが長いスレッドから順に取得（FIFO）
ReentrantLock fairLock = new ReentrantLock(true);
```

ただし公平ロックはオーバーヘッドがあり、スループットが下がる。

### このプロジェクトでの状況

`new ReentrantLock()` を使っており公平性の保証はないが、
ロック保持時間が短い（採番やバッチ処理の制御のみ）ため、
実質的にスタベーションが問題になる可能性は低い。

## デッドロック・ライブロック・スタベーション比較

| | デッドロック | ライブロック | スタベーション |
|---|---|---|---|
| スレッドの状態 | 停止（ブロック） | 動いている | 動いている（待機中） |
| 原因 | 互いのロックを待つ | 互いに譲り合う | 特定スレッドが後回し |
| 進行 | 全員停止 | 全員進まない | 他は進む。特定だけ進まない |
| 検知 | しやすい（スレッドダンプ） | 難しい | 難しい |
| 回避策 | ロック順序の統一 / tryLock | ランダム待機 | 公平ロック |

## そもそもロックや同期が必要になる条件

以下の3つが**全て揃った時だけ**問題になる。1つでも欠ければロック不要。

```
複数のスレッドが → 共有リソースに → 同時にアクセスする
```

| 条件 | main のみ（シングルスレッド） | 並行/並列処理 |
|---|---|---|
| 複数スレッド | いない | いる |
| 共有リソース | 自分しか使わない | 複数スレッドから見える |
| 同時アクセス | ありえない | ありうる |

`main` メソッドだけのプログラムなら、同時に同じ変数を触る相手がいないので、
競合状態・デッドロック・ライブロック・スタベーションの全てが構造的に起きない。

### Web アプリケーションは暗黙的にマルチスレッド

Spring Boot は HTTP リクエストごとにスレッドを割り当てるため、
自分で `new Thread()` を書いていなくてもマルチスレッドになる。

```
ブラウザA → POST /tasks ──→ Thread-1 が createTask() を実行
ブラウザB → POST /tasks ──→ Thread-2 が createTask() を同時に実行
```

このプロジェクトの `TaskNumberGenerator` にロックがあるのはこのため。

```
ロック不要: main だけのプログラム
ロック必要: Spring Boot 等の Web アプリ（フレームワークがマルチスレッド化する）
ロック必要: 自分で ExecutorService や @Async を使うコード
```

## java.util.concurrent パッケージの全体像

`synchronized` だけでも並行処理は書けるが、`java.util.concurrent` を使うと
より細かい制御・より高速・より安全に書ける。

```
java.util.concurrent
├── ロック・同期（synchronized の高機能版）
│   ├── locks.ReentrantLock         ← このプロジェクトで使用
│   ├── locks.ReadWriteLock
│   ├── locks.Condition
│   ├── CountDownLatch              ← このプロジェクトで使用
│   └── CyclicBarrier               ← このプロジェクトで使用
│
├── アトミック操作（ロック不要のスレッドセーフ）
│   ├── atomic.AtomicInteger        ← このプロジェクトで使用
│   └── atomic.AtomicLong           ← このプロジェクトで使用
│
├── スレッドプール（スレッド管理の自動化）
│   ├── ExecutorService             ← このプロジェクトで使用
│   └── Executors
│
├── 非同期パイプライン
│   └── CompletableFuture           ← このプロジェクトで使用
│
└── スレッドセーフなコレクション
    ├── ConcurrentHashMap            ← このプロジェクトで使用
    ├── CopyOnWriteArrayList         ← このプロジェクトで使用
    └── BlockingQueue (LinkedBlockingQueue) ← このプロジェクトで使用
```

### synchronized だけの世界 vs java.util.concurrent

| やりたいこと | synchronized だけ | java.util.concurrent |
|---|---|---|
| 排他制御 | `synchronized` ブロック | `ReentrantLock`（タイムアウト・公平性あり） |
| スレッドセーフなカウンタ | `synchronized` + `int` | `AtomicInteger`（ロック不要で高速） |
| スレッド間通信 | `wait()` / `notify()` | `Condition`、`CountDownLatch`、`BlockingQueue` |
| 並列タスク実行 | `new Thread()` を手動管理 | `ExecutorService`（プール管理） |
| 非同期チェーン | 書けない | `CompletableFuture` |
| スレッドセーフな Map | `synchronized(map) { ... }` | `ConcurrentHashMap`（部分ロックで高速） |

### このプロジェクトでの使用箇所

| クラス | 使用箇所 |
|---|---|
| `CompletableFuture` | DashboardService, TaskNotificationService |
| `ExecutorService` / `Executors` | TaskBatchService |
| `Callable` / `Future` | TaskBatchService |
| `CountDownLatch` | TaskBatchService |
| `CyclicBarrier` | TaskBatchService |
| `Executor` | AsyncConfig |
| `AtomicLong` | TaskNumberGenerator |
| `AtomicInteger` | TaskBatchService |
| `ReentrantLock` | TaskNumberGenerator, TaskBatchService |
| `ConcurrentHashMap` | TaskCacheService |
| `CopyOnWriteArrayList` | TaskEventPublisher |
| `LinkedBlockingQueue` | NotificationQueueService |

### 具体例: synchronized だけ vs concurrent パッケージ

```java
// synchronized だけで書いた場合
private int sequence = 0;
public synchronized String generate() {  // メソッド全体をロック
    sequence++;
    return String.format("TASK-%s-%04d", today, sequence);
}

// java.util.concurrent で書いた場合（実際のコード）
private final AtomicLong sequence = new AtomicLong(0);     // ロック不要で高速
private final ReentrantLock lock = new ReentrantLock();     // 必要な範囲だけロック
public String generate() {
    lock.lock();
    try { ... } finally { lock.unlock(); }
}
```

`synchronized` でも動くが、`concurrent` を使うことで
「ロックが必要な部分」と「AtomicLong で十分な部分」を分離できる。

### 現場での使い分け

「synchronized は使わない」ではなく、**場面で使い分ける**のが一般的。

| 場面 | よく使われるもの |
|---|---|
| 単純な排他制御（1メソッド内） | `synchronized` で十分 |
| スレッドセーフなカウンタ | `AtomicInteger` / `AtomicLong` |
| スレッドセーフな Map | `ConcurrentHashMap` |
| 並列タスク実行 | `ExecutorService` |
| 非同期処理のチェーン | `CompletableFuture` |
| タイムアウト付きロック | `ReentrantLock` + `tryLock` |

実務で最も多いのは `ConcurrentHashMap`、`ExecutorService`、`CompletableFuture`。
これらは `synchronized` では代替できない（またはコードが複雑になる）。

一方で「このメソッドを同時に呼ばれたくない」程度なら `synchronized` で書くことも普通にある。

```
判断基準:
  synchronized で足りる → synchronized（シンプルで安全）
  synchronized では足りない → concurrent パッケージ
```

## 並列処理コレクション

`java.util.concurrent` にはスレッドセーフなコレクション実装が用意されている。
`synchronized(list) { ... }` や `Collections.synchronizedMap()` より高い並行性能を提供する。

```
スレッドセーフなコレクション
├── ConcurrentHashMap        ← セグメントロックで高い並行性能の Map
├── CopyOnWriteArrayList     ← 書き込み時コピーで読み取り高速な List
└── BlockingQueue            ← Producer-Consumer パターン用キュー
    ├── LinkedBlockingQueue  ← リンクリストベース（容量任意）
    └── ArrayBlockingQueue   ← 配列ベース（容量固定）
```

### ConcurrentHashMap — セグメントロックによる高並行性 Map

`HashMap` のスレッドセーフ版。`Collections.synchronizedMap()` と異なり、
Map 全体ではなく**セグメント（バケット）単位でロック**するため、
異なるキーへの同時アクセスが可能。

```
synchronized(HashMap):
  Map 全体を1つのロックで保護
  Thread A: synchronized(map) { map.get("key1") }  ← ロック中
  Thread B: synchronized(map) { map.get("key2") }  ← Thread A を待つ

ConcurrentHashMap:
  セグメントごとに独立したロック
  Thread A: map.get("key1")  ← セグメント1のロック
  Thread B: map.get("key2")  ← セグメント2のロック（並行OK）
```

#### computeIfAbsent — アトミックな「なければ入れる」

```java
// 非スレッドセーフな実装（チェック〜格納の間に割り込まれる可能性）
if (!map.containsKey(key)) {
    map.put(key, loadFromDB(key));
}

// ConcurrentHashMap の computeIfAbsent ならアトミック
map.computeIfAbsent(key, k -> loadFromDB(k));
```

#### 主要メソッド

| メソッド | 説明 |
|---|---|
| `put(K, V)` | エントリを追加/更新 |
| `get(K)` | 値を取得 |
| `remove(K)` | エントリを削除 |
| `computeIfAbsent(K, Function)` | キーがなければ計算して格納（アトミック） |
| `computeIfPresent(K, BiFunction)` | キーがあれば再計算（アトミック） |
| `merge(K, V, BiFunction)` | 既存値と新値をマージ |
| `forEach(BiConsumer)` | 並行安全な走査 |

**このプロジェクトでの使用箇所**: `TaskCacheService`（タスクのインメモリキャッシュ）

### CopyOnWriteArrayList — 読み取り特化のスレッドセーフ List

**書き込み時に内部配列のコピーを作成**することでスレッドセーフを実現。
読み取りはロック不要で高速だが、書き込みのたびにコピーが発生するため書き込みが多い場合は遅い。

```
読み取り（イテレーション）:
  内部配列をそのまま走査 → ロック不要で高速

書き込み（add / remove）:
  1. 内部配列のコピーを作成
  2. コピーに対して変更を適用
  3. 参照を新しい配列に切り替え（volatile）
  → 走査中のスレッドは古い配列を参照し続けるため例外が起きない
```

#### ConcurrentModificationException の回避

通常の `ArrayList` をイテレーション中に変更すると `ConcurrentModificationException` が発生する。
`CopyOnWriteArrayList` ではイテレータが作成時のスナップショットを参照するため、この問題が構造的に起きない。

```java
// ArrayList: イテレーション中の変更で例外
for (Listener l : arrayList) {
    if (condition) arrayList.remove(l);  // ConcurrentModificationException!
}

// CopyOnWriteArrayList: イテレーション中の変更も安全
for (Listener l : copyOnWriteList) {
    if (condition) copyOnWriteList.remove(l);  // OK（スナップショット参照）
}
```

#### いつ使うか

| 状況 | 適切なコレクション |
|---|---|
| 読みが多く書きが少ない（リスナーリスト等） | CopyOnWriteArrayList |
| 読み書き同程度 | `Collections.synchronizedList()` |
| 高頻度の読み書き | `ConcurrentLinkedQueue` 等 |

**このプロジェクトでの使用箇所**: `TaskEventPublisher`（リスナーリストの管理）

### BlockingQueue — Producer-Consumer パターン

スレッド間で安全にデータを受け渡すためのキュー。
**キューが空なら take() でブロック、満杯なら put() でブロック**する。

```
Producer-Consumer パターン:

Producer              BlockingQueue             Consumer
────────              ─────────────             ────────
データを生成          [item][item][item]          データを処理
  │                                                │
  ├─ 満杯？→ put() でブロック                      │
  │   （空きが出るまで待つ）                        │
  │                                                │
  └─ queue.put(item) ──────────→ queue.take() ───→ 処理
                                    ↑
                               空なら待つ
```

#### LinkedBlockingQueue vs ArrayBlockingQueue

| | LinkedBlockingQueue | ArrayBlockingQueue |
|---|---|---|
| 内部構造 | リンクリスト | 配列 |
| 容量 | 任意（デフォルト無制限） | 必須（固定） |
| ロック | Producer/Consumer で別ロック | 1つのロック共有 |
| 同時 put/take | 可能 | 排他的 |
| メモリ | ノード生成のオーバーヘッド | 効率的 |

#### 主要メソッド

| メソッド | ブロック | 説明 |
|---|---|---|
| `put(E)` | する | 満杯なら空くまで待つ |
| `take()` | する | 空なら要素が入るまで待つ |
| `offer(E)` | しない | 満杯なら false を返す |
| `poll(timeout)` | タイムアウト | 指定時間だけ待つ |
| `size()` | - | 現在の要素数 |

**このプロジェクトでの使用箇所**: `NotificationQueueService`（通知キュー）

### BlockingQueue と wait/notify の関係

`BlockingQueue` は内部的に `ReentrantLock` + `Condition`（≒ `wait/notify`）で実装されている。
手動で Producer-Consumer パターンを `wait/notify` で書く代わりに、`BlockingQueue` を使えば
同じことを安全かつ簡潔に実現できる。

```
手動実装（wait/notify）:
  synchronized (queue) {
      while (queue.isEmpty()) queue.wait();
      return queue.remove();
  }

BlockingQueue:
  return queue.take();  // 上と同じ動作を1行で
```

### インプロセスキュー (BlockingQueue) とメッセージブローカーの関係

`BlockingQueue` は JVM 内のキューだが、同じ Producer-Consumer パターンが
分散システムのメッセージブローカー（RabbitMQ, Kafka 等）に発展する。

```
成長の流れ:
  BlockingQueue（JVM 内）
    → Spring Events（アプリ内のイベント駆動）
    → RabbitMQ / Kafka（分散システムのメッセージキュー）

共通する概念:
  Producer → Queue/Topic → Consumer
  非同期処理、バッファリング、負荷の平準化
```

| | BlockingQueue | RabbitMQ / Kafka |
|---|---|---|
| スコープ | 1つの JVM 内 | ネットワーク越し（分散） |
| 永続化 | なし（JVM 停止で消失） | あり（ディスクに保存） |
| スケール | 1プロセス | 複数サーバーにスケールアウト |
| 用途 | アプリ内の非同期処理 | マイクロサービス間通信 |
| 学習の位置付け | 基礎概念の理解 | 実務での本番利用 |

`BlockingQueue` で Producer-Consumer パターンの概念を理解しておくと、
RabbitMQ や Kafka の仕組みがスムーズに理解できる。

### このプロジェクトでの並列処理コレクション使用箇所

| コレクション | 使用箇所 | ユースケース |
|---|---|---|
| `ConcurrentHashMap` | TaskCacheService | タスクのインメモリキャッシュ |
| `CopyOnWriteArrayList` | TaskEventPublisher | イベントリスナーリストの管理 |
| `LinkedBlockingQueue` | NotificationQueueService | 通知の非同期キュー処理 |

### 連携フロー

```
TaskService
  │ createTask() / updateTask() / deleteTask()
  ↓
TaskEventPublisher (CopyOnWriteArrayList でリスナー管理)
  │ publish(event)  ← リスナーリストを走査
  ├──→ TaskNotificationService (CompletableFuture で即座に非同期処理)
  └──→ NotificationQueueService (BlockingQueue に入れて後で処理)

TaskService
  │ getTask()
  ↓
TaskCacheService (ConcurrentHashMap でキャッシュ)
  │ getOrLoad(id)  ← computeIfAbsent でキャッシュヒットなら DB をスキップ
  ↓
TaskRepository (DB)
```

### なぜこのプロジェクトでこれらを使うのか — 設計判断

#### ConcurrentHashMap（TaskCacheService）— HashMap ではダメな理由

Spring Boot は HTTP リクエストごとにスレッドを割り当てるため、
キャッシュ用の Map には**必ず複数スレッドから同時アクセスされる可能性がある**。

```
ユーザーA: GET /api/tasks/1 → Thread-1 → cache.get(1)
ユーザーB: GET /api/tasks/1 → Thread-2 → cache.get(1)  ← ほぼ同時
```

通常の `HashMap` はスレッドセーフではないため、同時に `put` すると
内部構造（ハッシュテーブル）が壊れる可能性がある（無限ループやデータ消失）。
これは学習目的ではなく、**Web アプリのキャッシュは構造的に並行アクセスされるので必須**。

#### CopyOnWriteArrayList（TaskEventPublisher）— 通知先の分離

主な目的は**スレッドセーフ性ではなく、Service 層と通知処理の分離**。

```
EventPublisher なし（直接呼び出し）:
  TaskService → notificationService.notifyTaskCreated()
  TaskService → queueService.enqueue()         ← 通知先が増えるたびに修正
  TaskService → auditService.log()             ← さらに増えたらまた修正

EventPublisher あり:
  TaskService → eventPublisher.publish(event)   ← これだけ。通知先を知らなくていい
      ├→ TaskNotificationService（リスナー1）
      └→ NotificationQueueService（リスナー2）
```

Service 層は `publish()` するだけで、**具体的にどの通知処理が走るかを知らなくていい**。
CopyOnWriteArrayList はこのリスナーリストの走査をスレッドセーフにするために使っている。

#### BlockingQueue（NotificationQueueService）— キューに入れるのは通知処理

キューに入るのは「タスク作成の処理」ではなく、**タスク作成に伴う通知処理**。
TaskService のレスポンスは通知の完了を待たずに即座に返る。

```
タスク作成が大量に来た場合:
  TaskService: タスクを DB に保存 → eventPublisher.publish() → レスポンス返却
                                         │
                                         ↓
  NotificationQueueService: キューに入れるだけ（一瞬）
                                         │
                                         ↓
  Consumer スレッド: 自分のペースで順次処理 ← 負荷のスパイクを吸収
```

呼び出し元（TaskService）は通知完了を待たないので、レスポンス速度に影響しない。

### いつこれらのパターンは不要か

アクセスが少ないアプリでは、これらのパターンの**実益はほとんどない**。

| パターン | 不要な場合 | 必要になる場合 |
|---|---|---|
| ConcurrentHashMap キャッシュ | アクセスが少なく DB が十分速い | 同じデータへの大量アクセスがある |
| EventPublisher（Observer） | 通知先が1つだけ（直接呼び出しで十分） | 通知先が増える可能性がある |
| BlockingQueue | 通知処理が軽い / 負荷スパイクがない | 通知処理が重い / 一時的な負荷集中がある |

```
判断基準:
  アクセスが少ない → 直接呼び出し + HashMap で十分
  アクセスが増えてきた → 段階的にこれらのパターンを導入
```

ただし1点注意: **HashMap は少アクセスでもスレッドセーフではない**。
同時アクセスの確率が低いだけで、Web アプリでキャッシュを置くなら
ConcurrentHashMap を使うか、キャッシュ自体を置かないかの二択。
「HashMap でキャッシュ」はアクセス量に関わらず避けるべき。

このプロジェクトでは Java Gold の学習用として全パターンを組み込んでいるが、
実務では必要になった時点で段階的に導入するのが一般的。

## Flow API（Reactive Streams）— Java 9+（このプロジェクトでは未使用）

このプロジェクトでは Flow API を**使っていない**。
イベント駆動は独自の Observer パターン（`TaskEventPublisher` / `TaskEventListener`）で実装している。
ここでは Flow API の概念と、このプロジェクトの Observer パターンとの対比を整理する。

### Flow API とは

`java.util.concurrent.Flow` は Java 9 で追加された **Reactive Streams** の標準インタフェース。
非同期ストリーム処理で、**バックプレッシャー**（消費者が処理しきれない場合に生産者を制御する仕組み）を標準化したもの。

```
Flow API の4つのインタフェース（java.util.concurrent.Flow 内に定義）:

Flow.Publisher<T>      ← データを発行する側
Flow.Subscriber<T>     ← データを受け取る側
Flow.Subscription      ← Publisher-Subscriber 間の接続（バックプレッシャー制御）
Flow.Processor<T,R>    ← Publisher + Subscriber の両方（中間処理）
```

### バックプレッシャーの仕組み

```
バックプレッシャーなし（このプロジェクトの Observer パターン）:
  Publisher: [event1][event2][event3][event4][event5] → 全部一気に送る
  Subscriber: [event1]...[event2]...                 ← 処理が追いつかない → あふれる

バックプレッシャーあり（Flow API）:
  Subscriber: subscription.request(2)                ← 「2個だけちょうだい」
  Publisher:  [event1][event2] → 2個だけ送る → 待つ
  Subscriber: 処理完了 → subscription.request(3)     ← 「次は3個」
  Publisher:  [event3][event4][event5] → 3個送る
```

### Flow API の基本コード

```java
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

// Publisher（SubmissionPublisher は Flow.Publisher の標準実装）
SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

// Subscriber
Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);  // 最初の1個を要求（バックプレッシャー）
    }

    @Override
    public void onNext(String item) {
        System.out.println("受信: " + item);
        subscription.request(1);  // 次の1個を要求
    }

    @Override
    public void onError(Throwable throwable) {
        System.err.println("エラー: " + throwable.getMessage());
    }

    @Override
    public void onComplete() {
        System.out.println("完了");
    }
};

// 購読開始
publisher.subscribe(subscriber);

// データ発行
publisher.submit("event1");
publisher.submit("event2");
publisher.submit("event3");

// 完了
publisher.close();  // → onComplete() が呼ばれる
```

### このプロジェクトの Observer パターンとの対比

```
このプロジェクトの実装:
  TaskEventPublisher              ← Publisher 相当
  TaskEventListener               ← Subscriber 相当
  TaskEvent                       ← データ（イベント）
  CopyOnWriteArrayList            ← リスナー管理

Flow API の場合:
  Flow.Publisher<TaskEvent>       ← Publisher
  Flow.Subscriber<TaskEvent>      ← Subscriber
  Flow.Subscription               ← バックプレッシャー制御
  SubmissionPublisher<TaskEvent>  ← Publisher の標準実装
```

#### 対応関係

| このプロジェクト | Flow API | 役割 |
|---|---|---|
| `TaskEventPublisher.publish(event)` | `SubmissionPublisher.submit(event)` | イベント発行 |
| `TaskEventPublisher.addListener(l)` | `publisher.subscribe(subscriber)` | 購読登録 |
| `TaskEventListener.onTaskCreated()` | `Subscriber.onNext()` | イベント受信 |
| なし | `Subscription.request(n)` | **バックプレッシャー** |
| なし | `Subscriber.onError()` | エラー通知 |
| なし | `Subscriber.onComplete()` | 完了通知 |

#### なぜ Flow API を使っていないか

| 観点 | Observer パターン（現在の実装） | Flow API |
|---|---|---|
| バックプレッシャー | なし（全イベントを即座に配信） | あり（`request(n)` で制御） |
| 複雑度 | シンプル（インタフェース1つ） | 複雑（4つのインタフェース） |
| Spring 統合 | 容易（`@Component` + DI） | 自前でライフサイクル管理 |
| 適したケース | イベント量が少ない（タスク CRUD） | 大量データストリーム |
| 実装コスト | 低い | 高い |

このプロジェクトのイベント量（タスクの CRUD 操作）では Observer パターンで十分。
Flow API はデータストリームが大量で消費者が処理しきれない可能性がある場合に有効。

### Flow API が有効なケース

```
Flow API が向いている:
  ├── IoT センサーデータ（毎秒数千件）
  ├── ログストリーム処理（大量ログを集約）
  ├── 株価のリアルタイム配信（高頻度データ）
  └── ファイルの行単位ストリーム処理（巨大ファイル）

Observer パターンで十分:
  ├── UI イベント（ボタンクリック等）
  ├── ビジネスイベント（注文作成、タスク更新等）  ← このプロジェクト
  └── 設定変更通知
```

### Flow API vs 他のリアクティブライブラリ

Flow API は**インタフェースのみ**を定義しており、実装は最低限（`SubmissionPublisher` のみ）。
実務でリアクティブプログラミングをするなら、以下のライブラリを使うのが一般的。

| | Flow API | Project Reactor | RxJava |
|---|---|---|---|
| 位置づけ | Java 標準（インタフェース） | Spring 公式の実装 | Netflix 発のリアクティブ |
| 実装の豊富さ | 最低限 | 豊富（`Mono`, `Flux`） | 豊富（`Observable`, `Flowable`） |
| Spring 統合 | なし | WebFlux で標準採用 | 可能 |
| オペレータ | なし | `map`, `filter`, `flatMap` 等多数 | 同上 |
| 用途 | 学習・標準準拠 | Spring WebFlux アプリ | Android / 非 Spring |

```
学習の流れ:
  Observer パターン（基本概念）
    → Flow API（標準インタフェース・バックプレッシャーの理解）
    → Project Reactor / RxJava（実務のリアクティブプログラミング）
```

### NotificationQueueService の BlockingQueue によるバックプレッシャー

このプロジェクトでは Flow API は使っていないが、`NotificationQueueService` が
**BlockingQueue でバックプレッシャーに近いことを実現**している。

```
NotificationQueueService の動作:
  Producer（TaskEventListener.onTaskCreated 等）
    │
    └─ queue.offer(event)  ← 満杯なら false を返す（ドロップ）
         │
    LinkedBlockingQueue（容量100）
         │
    Consumer（バックグラウンドスレッド）
    └─ queue.poll(1, SECONDS)  ← 自分のペースで取り出す
```

| 手法 | バックプレッシャーの実現方法 |
|---|---|
| Flow API | `Subscription.request(n)` で明示的に個数を要求 |
| BlockingQueue | キュー容量の上限で暗黙的に制御（`put()` でブロック / `offer()` でドロップ） |
| Observer（このプロジェクト） | なし（全イベントを同期的に配信） |
