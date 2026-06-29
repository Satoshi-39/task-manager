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
└── キーワード
    └── volatile                ← メモリ可視性の保証
```

## スレッドプール

### ExecutorService — タスクの並列実行基盤

スレッドの生成・管理をフレームワークに委ねる。直接 `new Thread()` するのは現場ではアンチパターン。

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
  └── DB の並行更新制御？
        ├── 競合が少ない → @Version（楽観的ロック）
        └── 競合が多い → SELECT FOR UPDATE（悲観的ロック）
```
