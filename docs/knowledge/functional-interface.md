# 関数型インタフェース ナレッジ

Java の関数型インタフェース（Functional Interface）に関する知識整理。
このプロジェクトの実装コードを例に、各インタフェースの役割と使い方を解説する。

## 関数型インタフェースとは

**抽象メソッドを1つだけ持つインタフェース**。ラムダ式やメソッド参照で実装できる。

```java
// 従来の書き方（匿名クラス）
Predicate<String> p = new Predicate<String>() {
    @Override
    public boolean test(String s) {
        return s.isEmpty();
    }
};

// ラムダ式（関数型インタフェースだから書ける）
Predicate<String> p = s -> s.isEmpty();

// メソッド参照（さらに短く）
Predicate<String> p = String::isEmpty;
```

## 主要4インタフェース

```
java.util.function
├── Supplier<T>      () → T         値を供給する（引数なし）
├── Consumer<T>      T → void       値を消費する（戻り値なし）
├── Function<T,R>    T → R          値を変換する
└── Predicate<T>     T → boolean    値を判定する
```

### Supplier\<T\> — 引数なし、値を返す

```java
// 定義: T get()
Supplier<String> supplier = () -> "Hello";
String result = supplier.get();  // "Hello"
```

**このプロジェクトでの使用箇所**:

```java
// DashboardService — CompletableFuture.supplyAsync の引数
CompletableFuture.supplyAsync(() -> taskJdbcRepository.countGroupByStatus());
//                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                            Supplier<Map<String, Long>>

// TaskNotificationService
CompletableFuture.supplyAsync(() -> {
    simulateDelay();
    return task.getTaskNumber();  // Supplier<String>
});
```

### Consumer\<T\> — 値を受け取り、戻り値なし

```java
// 定義: void accept(T t)
Consumer<String> printer = s -> System.out.println(s);
printer.accept("Hello");  // "Hello" を出力
```

**このプロジェクトでの使用箇所**:

```java
// TaskNotificationService — thenAccept の引数
.thenAccept(result -> {
    // Consumer<String>: result を受け取り、戻り値なし
});

// TaskService — forEach の引数
.forEach(counts::put);
// Consumer<Map.Entry<TaskStatus, Long>>（メソッド参照）
```

### Function\<T, R\> — 値を変換する

```java
// 定義: R apply(T t)
Function<String, Integer> length = s -> s.length();
int result = length.apply("Hello");  // 5
```

**このプロジェクトでの使用箇所**:

```java
// TaskService — Optional.map の引数
taskRepository.findById(id)
    .map(task -> {                      // Function<Task, Task>
        log.debug("Task found: {}", task.getTitle());
        return task;
    })
    .orElseThrow(() -> new TaskNotFoundException(id));

// TaskNotificationService — thenApply の引数
.thenApply(taskNumber -> {              // Function<String, String>
    log.info("Notification sent: {}", taskNumber);
    return taskNumber;
});

// TaskService — Collectors.toMap の引数
.collect(Collectors.toMap(
    Task::getId,         // Function<Task, Long>（キー抽出）
    task -> task          // Function<Task, Task>（値抽出）
));

// TaskService — Stream.map の引数
.map(task -> task.getPriority().getLevel())  // Function<Task, Integer>
```

### Predicate\<T\> — 値を判定する

```java
// 定義: boolean test(T t)
Predicate<String> isEmpty = s -> s.isEmpty();
boolean result = isEmpty.test("");  // true
```

**このプロジェクトでの使用箇所**:

```java
// TaskFilterBuilder — Predicate の動的合成（最も本格的な使用例）
private Predicate<Task> filter = task -> true;  // 初期値: 全て通す

filter = filter.and(task -> task.getStatus() == status);      // AND 合成
filter = filter.and(titleMatch.or(descMatch));                // OR 合成

// TaskService — Stream.filter の引数
allTasks.stream()
    .filter(filter)  // Predicate<Task> を渡す
    ...

// TaskService — partitioningBy の引数
.collect(Collectors.partitioningBy(
    task -> task.getStatus() == TaskStatus.DONE  // Predicate<Task>
));
```

## このプロジェクトでの関数型インタフェース使用箇所一覧

### 明示的に型名が登場する箇所

| ファイル | 行 | 関数型インタフェース | 使い方 |
|---|---|---|---|
| `TaskFilterBuilder` | 8, 20 | `Predicate<Task>` | フィルタ条件の動的合成（and/or） |
| `TaskService` | 21, 145 | `Predicate<Task>` | TaskFilterBuilder で組み立てた条件を `stream().filter()` に渡す |
| `TaskBatchService` | 66-67 | `Callable<Boolean>` | 各タスクの処理をラムダ式で `Callable` に変換し `invokeAll` |

### ラムダ式・メソッド参照として暗黙的に使われている箇所

| ファイル | 行 | コード | 裏の型 |
|---|---|---|---|
| `DashboardService` | 45 | `supplyAsync(() -> countGroupByStatus())` | `Supplier<Map<String,Long>>` |
| `DashboardService` | 48 | `supplyAsync(() -> countGroupByPriority())` | `Supplier<Map<String,Long>>` |
| `DashboardService` | 51 | `supplyAsync(taskRepository::count)` | `Supplier<Long>` |
| `TaskNotificationService` | 28 | `supplyAsync(() -> { ... return taskNumber; })` | `Supplier<String>` |
| `TaskNotificationService` | 34 | `.thenApply(taskNumber -> { ... })` | `Function<String,String>` |
| `TaskNotificationService` | 38 | `.exceptionally(ex -> null)` | `Function<Throwable,String>` |
| `TaskNotificationService` | 42 | `.thenAccept(result -> {})` | `Consumer<String>` |
| `TaskService` | 124 | `.map(task -> { ... return task; })` | `Function<Task,Task>` |
| `TaskService` | 154 | `Comparator.comparing(Task::getPriority, ...)` | `Function<Task,TaskPriority>` + `Comparator<TaskPriority>` |
| `TaskService` | 166 | `.groupingBy(Task::getStatus)` | `Function<Task,TaskStatus>` |
| `TaskService` | 176 | `.partitioningBy(task -> ... == DONE)` | `Predicate<Task>` |
| `TaskService` | 185 | `.toMap(Task::getId, task -> task)` | `Function<Task,Long>` + `Function<Task,Task>` |
| `TaskService` | 201 | `.forEach(counts::put)` | `BiConsumer<TaskStatus,Long>` |
| `TaskService` | 213 | `.reduce(0, Integer::sum)` | `BinaryOperator<Integer>` |

### まとめ: インタフェース別の出現回数

| 関数型インタフェース | 出現数 | 主な用途 |
|---|---|---|
| `Predicate<T>` | 5+ | フィルタ条件（TaskFilterBuilder, Stream.filter, partitioningBy） |
| `Function<T,R>` | 7+ | 値の変換（map, thenApply, groupingBy, toMap, Comparator.comparing） |
| `Supplier<T>` | 4+ | 値の供給（CompletableFuture.supplyAsync） |
| `Consumer<T>` / `BiConsumer` | 3+ | 値の消費（thenAccept, forEach） |
| `Comparator<T>` | 2 | ソート条件の合成（comparing + thenComparing） |
| `Callable<V>` | 1 | 並行処理（ExecutorService.invokeAll） |
| `BinaryOperator<T>` | 1 | 集約（Stream.reduce） |

## Predicate の合成 — and() / or() / negate()

`TaskFilterBuilder` で重点的に使われているパターン。

```java
Predicate<Task> isHighPriority = task -> task.getPriority() == TaskPriority.HIGH;
Predicate<Task> isOverdue      = task -> task.getDueDate().isBefore(LocalDateTime.now());
Predicate<Task> isDone         = task -> task.getStatus() == TaskStatus.DONE;

// AND: 両方満たす
Predicate<Task> urgentAndOverdue = isHighPriority.and(isOverdue);

// OR: どちらか一方
Predicate<Task> highOrOverdue = isHighPriority.or(isOverdue);

// NEGATE: 否定
Predicate<Task> notDone = isDone.negate();

// 組み合わせ
Predicate<Task> complex = isHighPriority.and(isOverdue).and(notDone);
```

## TaskFilterBuilder 詳細解説

`common/TaskFilterBuilder.java` — **ビルダーパターン + Predicate 合成**で
検索条件を動的に組み立てるクラス。

### なぜこの設計が必要か

検索条件は API の呼び出しごとに異なる。全パターンを if 文で分岐すると組み合わせ爆発する。

```java
// ダメな例: 条件の組み合わせごとに分岐 → 4条件で 16パターン
if (status != null && priority != null && keyword != null && overdue) {
    return findByStatusAndPriorityAndKeywordAndOverdue(...);
} else if (status != null && priority != null && keyword != null) {
    return findByStatusAndPriorityAndKeyword(...);
} else if (status != null && priority != null) {
    return findByStatusAndPriority(...);
}
// ... 延々と続く
```

TaskFilterBuilder なら**指定された条件だけを合成**する。

### 仕組み: 初期値 `true` に条件を AND で積み重ねる

```java
private Predicate<Task> filter = task -> true;  // 全タスクを通す（恒真）
```

この初期値がポイント。`true AND X` は `X` と等価なので、
最初の条件が何であっても正しく機能する。

### 各メソッドの動作

**withStatus** — null なら何もしない（条件スキップ）

```java
public TaskFilterBuilder withStatus(TaskStatus status) {
    if (status != null) {
        filter = filter.and(task -> task.getStatus() == status);
    }
    return this;  // ← メソッドチェーンのため this を返す
}
```

**withKeyword** — or() で「タイトルまたは説明にマッチ」を表現

```java
public TaskFilterBuilder withKeyword(String keyword) {
    if (keyword != null && !keyword.isBlank()) {
        String lowerKeyword = keyword.toLowerCase();

        Predicate<Task> titleMatch = task ->
                task.getTitle().toLowerCase().contains(lowerKeyword);
        Predicate<Task> descMatch = task ->
                task.getDescription() != null
                        && task.getDescription().toLowerCase().contains(lowerKeyword);

        // titleMatch OR descMatch を1つの条件として AND 合成
        filter = filter.and(titleMatch.or(descMatch));
    }
    return this;
}
```

```
titleMatch.or(descMatch) の論理:
  タイトルに "api" を含む  → true   ← OR なのでこちらだけで OK
  説明に "api" を含む      → true   ← OR なのでこちらだけでも OK
  どちらにも含まない       → false
```

**withOverdue** — 3つの条件を AND で結合した複合 Predicate

```java
filter = filter.and(task ->
        task.getDueDate() != null                    // 期限が設定されている
                && task.getDueDate().isBefore(now)    // 期限を過ぎている
                && !task.getStatus().isTerminal());   // 完了/キャンセルでない
```

### フィルタ合成の具体例

```
リクエスト: status=IN_PROGRESS, priority=HIGH, keyword="API", overdue=null

new TaskFilterBuilder()
    .withStatus(IN_PROGRESS)
        filter = (true) AND (status == IN_PROGRESS)
        → (status == IN_PROGRESS)

    .withPriority(HIGH)
        filter = ↑ AND (priority == HIGH)
        → (status == IN_PROGRESS) AND (priority == HIGH)

    .withKeyword("API")
        filter = ↑ AND (title含む"api" OR desc含む"api")
        → (status == IN_PROGRESS) AND (priority == HIGH) AND (title OR desc に "api")

    .withOverdue(null)          ← null なので何もしない（条件スキップ）

    .build()
        → 完成した Predicate<Task> を返す
```

```
リクエスト: keyword="バグ" のみ（他は全て null）

new TaskFilterBuilder()
    .withStatus(null)           ← スキップ
    .withPriority(null)         ← スキップ
    .withKeyword("バグ")
        filter = (true) AND (title含む"バグ" OR desc含む"バグ")
    .withOverdue(null)          ← スキップ
    .build()
        → title か desc に "バグ" を含むタスクだけ通す Predicate
```

### 呼び出し側: TaskService.searchTasks()

```java
Predicate<Task> filter = new TaskFilterBuilder()
        .withStatus(criteria.getStatus())       // ユーザーの検索条件
        .withPriority(criteria.getPriority())
        .withKeyword(criteria.getKeyword())
        .withOverdue(criteria.getOverdue())
        .build();                               // Predicate<Task> 完成

return allTasks.stream()
        .filter(filter)                         // ← ここで使う
        .sorted(...)
        .collect(Collectors.toList());
```

### この設計のメリット

| メリット | 説明 |
|---|---|
| 条件の組み合わせ爆発を回避 | 4条件 → 16 パターンの分岐が不要 |
| 条件の追加が容易 | 新メソッド `withAssignee()` 等を足すだけ |
| null 安全 | 各メソッドが null チェックを担当 |
| テスタブル | `build()` で `Predicate` を取り出してユニットテスト可能 |
| 関心の分離 | フィルタ構築ロジックを Service から切り出し |

### 「組み立て」と「評価」は別のタイミングで起きる

TaskFilterBuilder は Predicate を**組み立てるための入れ物**であって、
評価時にはもう関係ない。`build()` した瞬間に役目は終わる。

```java
// 【組み立てフェーズ】TaskFilterBuilder がラムダ式を and() で合成する
Predicate<Task> filter = new TaskFilterBuilder()
        .withStatus(IN_PROGRESS)    // filter フィールドにラムダ式を積む
        .withKeyword("API")         // さらにラムダ式を積む
        .build();    // ← ここで Predicate が取り出される。TaskFilterBuilder はもう不要

// 【評価フェーズ】ここから先は TaskFilterBuilder の存在を知らない
allTasks.stream()
        .filter(filter)  // filter.test(task) で各条件が順に true/false 評価される
        ...
```

**組み立て時**: 各 `withXxx()` メソッドは `TaskFilterBuilder` を返す。
`filter` フィールドには `task -> ...` という**未実行のラムダ式**が `and()` で合成されて蓄積される。
この時点では true/false は一切出ない。

**評価時**: `stream().filter(filter)` の内部でリストの各要素に対して `filter.test(task)` が呼ばれる。
`and()` で繋がれた各条件が左から順に true/false で評価される。

```
filter.test(taskA) の内部:
  (task -> true)                  → true  ✓ 続行
  AND (task -> status判定)         → true  ✓ 続行
  AND (task -> keyword判定)        → false ✗ ここで確定（短絡評価）
  結果: false → taskA は除外

filter.test(taskB) の内部:
  (task -> true)                  → true  ✓
  AND (task -> status判定)         → true  ✓
  AND (task -> keyword判定)        → true  ✓
  結果: true → taskB は通過
```

AND なので途中で false が出た時点で残りは評価されない（短絡評価。Java の `&&` と同じ）。

### Predicate の3つの組み立て方と使い分け

**方式1: ラッパークラス（ビルダー）** — TaskFilterBuilder の方式

```java
Predicate<Task> filter = new TaskFilterBuilder()
        .withStatus(IN_PROGRESS)
        .withKeyword("API")
        .build();
tasks.stream().filter(filter)...
```

条件が多い・再利用する・or() の合成がある場合に向いている。

**方式2: Predicate 変数に直接組む** — ビルダーを作らない方式

```java
Predicate<Task> filter = task -> true;
if (status != null) {
    filter = filter.and(task -> task.getStatus() == status);
}
tasks.stream().filter(filter).toList();
```

やっていることは TaskFilterBuilder と同じ。条件が少なく1箇所でしか使わない場合はこれで十分。

**方式3: filter() にラムダを直接渡す** — 最もシンプル

```java
tasks.stream()
        .filter(task -> !task.getStatus().isTerminal())
        .toList();
```

条件が1つで固定の場合はこれが最も簡潔。

| | ビルダー | 直接組む | ラムダ直接渡し |
|---|---|---|---|
| 条件が少ない(1-2個) | やりすぎ | 十分 | 十分 |
| 条件が多い(4個以上) | 整理しやすい | 長くなる | 長くなる |
| 複数箇所で再利用 | 向いている | 都度書く | 都度書く |
| or() の合成 | メソッド内に隠せる | 呼び出し側が書く | 書けない |
| テスト | build() で取り出せる | 変数を公開する必要 | テストしにくい |

or() について: `filter()` を2回呼ぶと AND になるため、方式3 では OR を表現できない。

### このプロジェクトでの使い分け実例

| 箇所 | 条件 | 方式 | 理由 |
|---|---|---|---|
| `TaskService.searchTasks()` :153 | 4つ（動的、or()あり） | ビルダー | 条件が多く or() 合成もある |
| `TaskService.calculateTotalPriorityScore()` :211 | 1つ（固定） | ラムダ直接渡し | `task -> !task.getStatus().isTerminal()` だけで十分 |

```java
// searchTasks() — 条件が4つ、or()合成もある → ビルダー
Predicate<Task> filter = new TaskFilterBuilder()
        .withStatus(criteria.getStatus())
        .withPriority(criteria.getPriority())
        .withKeyword(criteria.getKeyword())
        .withOverdue(criteria.getOverdue())
        .build();
allTasks.stream().filter(filter)...

// calculateTotalPriorityScore() — 条件1つ → ラムダ直接渡し
taskRepository.findAll().stream()
        .filter(task -> !task.getStatus().isTerminal())
        .map(task -> task.getPriority().getLevel())
        .reduce(0, Integer::sum);
```

### Stream パイプラインでは各ステップで異なる関数型インタフェースが使われる

`calculateTotalPriorityScore()` を例に、パイプラインの各ステップで型が変わっていく様子:

```java
taskRepository.findAll().stream()                        // Stream<Task>
        .filter(task -> !task.getStatus().isTerminal())   // Stream<Task>     ← Predicate<Task>
        .map(task -> task.getPriority().getLevel())       // Stream<Integer>  ← Function<Task, Integer>
        .reduce(0, Integer::sum);                         // int              ← BinaryOperator<Integer>
```

| メソッド | 引数の型 | 役割 |
|---|---|---|
| `filter()` | `Predicate<Task>` | Task を判定して通す/除外する |
| `map()` | `Function<Task, Integer>` | Task を Integer に変換する |
| `reduce()` | `BinaryOperator<Integer>` | Integer を2つずつまとめて1つにする |

Predicate が活躍するのは `filter` まで。`map` で型が変換され、
`reduce` には `Stream<Integer>` が届く。各ステップで別の関数型インタフェースにバトンタッチしている。

```
reduce の動き:
  [3, 1, 2, 3] という Stream<Integer> が来たとすると
  0 + 3 = 3 → 3 + 1 = 4 → 4 + 2 = 6 → 6 + 3 = 9（最終結果）
```

### ラムダ式の型はどうやって決まるか

ラムダ式自体には型がない。**受け取る側のメソッドのシグネチャが型を決める**。

```java
// Stream クラスの定義（Java 標準ライブラリ内部）
public interface Stream<T> {
    Stream<T>  filter(Predicate<? super T> predicate);          // ← Predicate を要求
    <R> Stream<R> map(Function<? super T, ? extends R> mapper); // ← Function を要求
    T reduce(T identity, BinaryOperator<T> accumulator);        // ← BinaryOperator を要求
}
```

```java
.filter(task -> !task.getStatus().isTerminal())
//      ↑ filter() の引数が Predicate<T> と定義されている
//        → このラムダは Predicate<Task> として解釈される

.map(task -> task.getPriority().getLevel())
//   ↑ map() の引数が Function<T,R> と定義されている
//     → このラムダは Function<Task, Integer> として解釈される

.reduce(0, Integer::sum)
//         ↑ reduce() の第2引数が BinaryOperator<T> と定義されている
//           → Integer::sum は BinaryOperator<Integer> として解釈される
```

同じ `task -> ...` という形のラムダでも、`filter()` に渡せば Predicate、
`map()` に渡せば Function になる。ラムダの形ではなく**渡す先で型が確定する**。

### ラムダ式は必ず関数型インタフェースの実装

Java ではラムダ式は**必ず何かの関数型インタフェースを実装**している。
「ほぼ」ではなく「必ず」。これは Java の言語仕様。

```java
Predicate<String> p = s -> s.isEmpty();          // Predicate の実装
Function<String, Integer> f = s -> s.length();   // Function の実装
Runnable r = () -> System.out.println("hello");  // Runnable の実装
```

コード中にラムダ式やメソッド参照を見つけたら、
「これは何かの関数型インタフェースを実装している」と判断してよい。
どの関数型インタフェースかは、渡される先のメソッドシグネチャを見ればわかる。

## 派生インタフェース

### Bi 系 — 引数が2つ

| 基本 | Bi 版 | シグネチャ |
|---|---|---|
| `Function<T,R>` | `BiFunction<T,U,R>` | `(T, U) → R` |
| `Consumer<T>` | `BiConsumer<T,U>` | `(T, U) → void` |
| `Predicate<T>` | `BiPredicate<T,U>` | `(T, U) → boolean` |

```java
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
add.apply(3, 5);  // 8
```

### Operator 系 — 入出力が同じ型

| インタフェース | シグネチャ | 用途 |
|---|---|---|
| `UnaryOperator<T>` | `T → T` | `Function<T,T>` の特殊化 |
| `BinaryOperator<T>` | `(T, T) → T` | `BiFunction<T,T,T>` の特殊化 |

```java
// UnaryOperator: 同じ型で変換
UnaryOperator<String> toUpper = s -> s.toUpperCase();
toUpper.apply("hello");  // "HELLO"

// BinaryOperator: reduce で使われる
BinaryOperator<Integer> sum = Integer::sum;
```

**このプロジェクトでの使用箇所**:

```java
// TaskService — reduce の第2引数
.reduce(0, Integer::sum);  // BinaryOperator<Integer>
```

### プリミティブ特殊化 — ボクシング回避

| インタフェース | シグネチャ | 回避するもの |
|---|---|---|
| `IntPredicate` | `int → boolean` | `Predicate<Integer>` のボクシング |
| `LongSupplier` | `() → long` | `Supplier<Long>` のボクシング |
| `ToIntFunction<T>` | `T → int` | `Function<T, Integer>` のボクシング |
| `IntFunction<R>` | `int → R` | `Function<Integer, R>` のボクシング |

大量データ処理でパフォーマンスが問題になる場合に使う。

## Comparator — 関数型インタフェースとしての側面

`Comparator` も抽象メソッド `compare(T, T)` を1つだけ持つ関数型インタフェース。

**このプロジェクトでの使用箇所**:

```java
// TaskService.searchTasks() — Comparator の合成
.sorted(
    Comparator.comparing(
        Task::getPriority,
        Comparator.comparingInt(TaskPriority::getLevel).reversed()  // 優先度降順
    )
    .thenComparing(Task::getCreatedAt, Comparator.reverseOrder())   // 作成日降順
)
```

### Comparator 合成の主要メソッド

| メソッド | 説明 |
|---|---|
| `comparing(keyExtractor)` | キーで比較 |
| `comparingInt/Long/Double` | プリミティブキーで比較（ボクシング回避） |
| `reversed()` | 逆順 |
| `thenComparing(...)` | 第1条件が同じ場合の第2条件 |
| `naturalOrder()` | 自然順序 |
| `reverseOrder()` | 自然順序の逆 |
| `nullsFirst/nullsLast` | null の扱い |

## Callable\<V\> — 並行処理の関数型インタフェース

`java.util.function` パッケージには属さないが、関数型インタフェースの一つ。

```java
// 定義: V call() throws Exception
// Supplier<V> と似ているが、チェック例外をスローできる
```

**このプロジェクトでの使用箇所**:

```java
// TaskBatchService — ラムダ式で Callable<Boolean> を生成
List<Callable<Boolean>> callables = overdueTasks.stream()
    .<Callable<Boolean>>map(task -> () -> processOneTask(task))
    .toList();
```

### Callable vs Supplier

| | Supplier\<T\> | Callable\<T\> |
|---|---|---|
| パッケージ | `java.util.function` | `java.util.concurrent` |
| メソッド | `get()` | `call()` |
| チェック例外 | スローできない | スローできる |
| 用途 | Stream / Optional / CompletableFuture | ExecutorService |

## メソッド参照の4パターン

ラムダ式をさらに簡潔に書ける場合に使う。

| パターン | 例 | ラムダ式の等価 |
|---|---|---|
| スタティックメソッド | `Integer::sum` | `(a, b) -> Integer.sum(a, b)` |
| インスタンスメソッド（特定オブジェクト） | `counts::put` | `(k, v) -> counts.put(k, v)` |
| インスタンスメソッド（任意オブジェクト） | `Task::getId` | `task -> task.getId()` |
| コンストラクタ | `ArrayList::new` | `() -> new ArrayList<>()` |

**このプロジェクトでの使用箇所**:

```java
// スタティックメソッド参照
.reduce(0, Integer::sum);

// インスタンスメソッド参照（特定オブジェクト: counts）
.forEach(counts::put);

// インスタンスメソッド参照（任意オブジェクト）
Collectors.toMap(Task::getId, task -> task);
Collectors.groupingBy(Task::getStatus);
Comparator.comparing(Task::getPriority);

// スタティックメソッド参照（ファクトリ）
CompletableFuture.supplyAsync(taskRepository::count);
```

## @FunctionalInterface アノテーション

```java
@FunctionalInterface
public interface MyConverter<T, R> {
    R convert(T input);
    // 抽象メソッドは1つだけ。2つ以上あるとコンパイルエラー
}

MyConverter<String, Integer> toInt = Integer::parseInt;
int result = toInt.convert("42");  // 42
```

- `@FunctionalInterface` は任意だが、付けるとコンパイラが検証してくれる
- `default` メソッドや `static` メソッドは何個あってもOK（抽象メソッドではないため）

## 使い分けフローチャート

```
ラムダ式を書きたい
  │
  ├── 引数なし、値を返す？
  │     └── Supplier<T>       例: () -> new Task()
  │
  ├── 引数あり、戻り値なし？
  │     └── Consumer<T>       例: task -> log(task)
  │
  ├── 引数を変換して返す？
  │     ├── 型が変わる → Function<T,R>  例: task -> task.getId()
  │     └── 型が同じ → UnaryOperator<T> 例: s -> s.toUpperCase()
  │
  ├── 引数を判定する？
  │     └── Predicate<T>      例: task -> task.isDone()
  │
  ├── 引数が2つ？
  │     └── Bi 系を使う       例: BiFunction, BiConsumer
  │
  └── チェック例外をスローしたい？
        └── Callable<T>       例: () -> riskyWork()
```
