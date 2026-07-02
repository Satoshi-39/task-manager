# AOP（アスペクト指向プログラミング）ナレッジ

Spring AOP に関する知識整理。
このプロジェクトの `LoggingAspect` を例に、AOP の主要概念と Spring での動作原理を解説する。

## 全体マップ

```
AOP の主要概念
├── Aspect（アスペクト）      ← 横断的関心事をまとめたモジュール（クラス）
├── Join Point（結合点）      ← AOP が差し込まれるポイント（メソッド実行）
├── Pointcut（ポイントカット） ← Join Point を選別する条件式
├── Advice（アドバイス）       ← Join Point で実行する処理
│   ├── @Before              ← メソッド実行前
│   ├── @After               ← メソッド実行後（成功・失敗問わず）
│   ├── @AfterReturning      ← メソッド正常終了後
│   ├── @AfterThrowing       ← メソッド例外スロー後
│   └── @Around              ← メソッド実行前後を包括的に制御
└── Weaving（ウィービング）    ← アスペクトを対象コードに適用する処理
    ├── コンパイル時           ← AspectJ
    ├── ロード時              ← AspectJ LTW
    └── 実行時（プロキシ）     ← Spring AOP ★
```

## 主要概念の関係

```
                    Pointcut（条件式）
                    「どこに差し込むか」
                         │ 選別
                         ▼
Aspect ──── Advice ──→ Join Point ──→ 対象メソッド実行
（クラス）  （処理）    （結合点）

例: LoggingAspect
  │
  ├── Pointcut: controllerMethods()  ← controller パッケージの全メソッド
  ├── Pointcut: serviceMethods()     ← service パッケージの全メソッド
  │         │
  │         └── 条件に合致する Join Point を選別
  │                   │
  └── Advice: logAround()            ← 選ばれた Join Point で実行するログ処理
```

### このプロジェクトでの具体例

```
HTTP リクエスト: POST /api/tasks

  ① TaskController.createTask() が呼ばれる
     → Join Point（メソッド実行という結合点）
     → controllerMethods() Pointcut に合致
     → logAround() Advice が実行される

  ② TaskService.createTask() が呼ばれる
     → Join Point
     → serviceMethods() Pointcut に合致
     → logAround() Advice が実行される

ログ出力:
  [AOP] START: TaskController.createTask(TaskCreateRequest{...})
  [AOP] START: TaskService.createTask(TaskCreateRequest{...})
  [AOP] END: TaskService.createTask → 12ms
  [AOP] END: TaskController.createTask → 15ms
```

## Join Point・Pointcut・Advice の詳細

### Join Point（結合点）

AOP が差し込まれうる個々のポイント。Spring AOP では**メソッドの実行**のみがJoin Point となる（フィールドアクセスやコンストラクタ呼び出しは対象外）。

```
Spring AOP の Join Point:
  ✅ メソッド実行 — execution(* com.example..*.*(..))
  ❌ フィールドアクセス — Spring AOP では対象外（AspectJ のみ）
  ❌ コンストラクタ呼び出し — Spring AOP では対象外（AspectJ のみ）
```

### Pointcut（ポイントカット）

Join Point を選別する条件式。`@Pointcut` アノテーションで宣言する。

```java
// controller パッケージ配下の全メソッドを対象
@Pointcut("execution(* com.example.taskmanager.controller..*.*(..))")
public void controllerMethods() {}

// service パッケージ配下の全メソッドを対象
@Pointcut("execution(* com.example.taskmanager.service..*.*(..))")
public void serviceMethods() {}
```

#### Pointcut 式の読み方

```
execution(* com.example.taskmanager.controller..*.*(..))
           │  │                                │ │ │
           │  │                                │ │ └── 引数: 任意
           │  │                                │ └──── メソッド名: 任意
           │  │                                └───── クラス名: 任意
           │  └────────────────────────────────────── パッケージ: .. はサブパッケージ含む
           └───────────────────────────────────────── 戻り値: 任意
```

#### よく使う Pointcut 式

| 式 | 意味 |
|---|---|
| `execution(* com.example..*.*(..))` | com.example 配下の全メソッド |
| `execution(public * *(..))` | 全 public メソッド |
| `execution(* set*(..))` | set で始まるメソッド |
| `@annotation(org.springframework.transaction.annotation.Transactional)` | @Transactional が付いたメソッド |
| `within(com.example.taskmanager.service..*)` | service パッケージ内のクラスの全メソッド |

#### Pointcut の組み合わせ

```java
// || で OR 結合
@Around("controllerMethods() || serviceMethods()")

// && で AND 結合
@Around("serviceMethods() && @annotation(Transactional)")

// ! で否定
@Around("serviceMethods() && !execution(* get*(..))")
```

### Advice（アドバイス）

選ばれた Join Point で実行する処理。5 種類ある。

| Advice | 実行タイミング | 用途 |
|---|---|---|
| `@Before` | メソッド実行前 | 権限チェック、パラメータ検証 |
| `@After` | メソッド実行後（成功・失敗問わず） | リソース解放 |
| `@AfterReturning` | メソッド正常終了後 | 戻り値のログ、監査 |
| `@AfterThrowing` | メソッド例外スロー後 | エラーログ、通知 |
| `@Around` | メソッド実行前後を包括制御 | 実行時間計測、キャッシュ、トランザクション |

```
@Before        → [メソッド実行] → @AfterReturning → @After
                         │
                    例外発生時
                         │
                         └──→ @AfterThrowing → @After

@Around: 上記すべてを1つのメソッドで制御可能
```

#### @Around と ProceedingJoinPoint

`@Around` は最も強力な Advice。`ProceedingJoinPoint.proceed()` を呼ぶことで対象メソッドの実行を制御する。

```java
@Around("controllerMethods() || serviceMethods()")
public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
    // ← メソッド実行前の処理
    String className = joinPoint.getTarget().getClass().getSimpleName();
    String methodName = joinPoint.getSignature().getName();

    long startTime = System.currentTimeMillis();
    try {
        Object result = joinPoint.proceed();  // ← 対象メソッドを実行
        // ← メソッド正常終了後の処理
        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("[AOP] END: {}.{} → {}ms", className, methodName, elapsed);
        return result;
    } catch (Throwable ex) {
        // ← メソッド例外スロー後の処理
        logger.error("[AOP] ERROR: {}.{} — {}", className, methodName, ex.getMessage());
        throw ex;  // 例外はそのまま再スロー
    }
}
```

**重要**: `proceed()` を呼ばないとメソッドが実行されない。これにより、条件付き実行やキャッシュ（結果があれば proceed しない）が実現できる。

## Spring AOP の動作原理 — プロキシベース

### プロキシとは

Spring AOP は**プロキシパターン**で実装されている。Bean の呼び出しをプロキシオブジェクトが仲介し、Advice を差し込む。

```
プロキシなし（通常の呼び出し）:
  Controller ──→ TaskService.createTask()

プロキシあり（Spring AOP）:
  Controller ──→ [Proxy] ──→ TaskService.createTask()
                    │
                    ├── logAround() 開始ログ
                    ├── proceed() → 本物の createTask() 実行
                    └── logAround() 終了ログ
```

### 自己呼び出し（self-invocation）の制約

Spring AOP はプロキシベースのため、**同一クラス内の `this.method()` 呼び出しではプロキシを経由しない**。そのためアスペクトが適用されない。

```java
// TaskService 内
public Task createTask(TaskCreateRequest request) {
    // ↓ this 経由なのでプロキシを通らず、AOP が効かない
    Task existing = this.getTask(id);
    // ...
}
```

```
外部からの呼び出し（AOP が効く）:
  Controller ──→ [Proxy] ──→ TaskService.createTask()  ✅ AOP 適用

自己呼び出し（AOP が効かない）:
  TaskService.createTask() ──→ this.getTask()  ❌ プロキシを通らない
```

これは `@Async` や `@Transactional` でも同じ制約がある。

### AspectJ との違い

| | Spring AOP | AspectJ |
|---|---|---|
| ウィービング | 実行時（プロキシ） | コンパイル時 or ロード時 |
| 対象 | Spring Bean のメソッド実行のみ | フィールド、コンストラクタも対象 |
| 自己呼び出し | AOP が効かない | AOP が効く |
| 設定 | `spring-boot-starter-aop` で自動設定 | AspectJ コンパイラが必要 |
| 用途 | エンタープライズアプリの大半 | 細かい制御が必要な場合 |

Spring AOP で十分なケースが大半であり、AspectJ が必要になるのは稀。

## このプロジェクトでの AOP 使用箇所

### 明示的な AOP — LoggingAspect

`@Aspect` を使った明示的なアスペクト定義。

| 要素 | 値 |
|---|---|
| Aspect | `LoggingAspect` |
| Pointcut | `controllerMethods()` / `serviceMethods()` |
| Advice | `logAround()`（`@Around`） |
| 対象 | Controller 6 クラス・Service 7 クラスの全 public メソッド |

### 暗黙的な AOP — Spring が内部で使っている

Spring はフレームワーク内部で AOP を多用している。以下はこのプロジェクトで暗黙的に AOP が使われている箇所。

| アノテーション | 内部動作 | 使用箇所 |
|---|---|---|
| `@Transactional` | トランザクション開始/コミット/ロールバックをプロキシが制御 | `TaskService` |
| `@Async` | メソッド呼び出しを別スレッドに委譲するプロキシ | `TaskNotificationService` |
| `@Validated` / `@Valid` | バリデーションをプロキシが実行 | Controller の引数 |

これらも Spring AOP（プロキシ）で動いているため、自己呼び出しでは効かないという同じ制約を持つ。

## ログ出力例

```
# 正常系
[AOP] START: TaskController.createTask(TaskCreateRequest{title=...})
[AOP] START: TaskService.createTask(TaskCreateRequest{title=...})
[AOP] END: TaskService.createTask → 12ms
[AOP] END: TaskController.createTask → 15ms

# 例外発生時
[AOP] START: TaskService.updateTask(99, TaskUpdateRequest{...})
[AOP] ERROR: TaskService.updateTask → 3ms — TaskNotFoundException: Task not found: 99

# 実行時間が閾値（500ms）を超えた場合
[AOP] START: DashboardService.getDashboard()
[AOP] SLOW: DashboardService.getDashboard → 523ms (threshold: 500ms)
```

## AOP を使うべき場面

```
横断的関心事（複数のクラスにまたがる共通処理）
  │
  ├── ログ出力          ← LoggingAspect（このプロジェクト）
  ├── トランザクション管理 ← @Transactional（Spring 標準）
  ├── セキュリティ       ← @PreAuthorize（Spring Security）
  ├── キャッシュ         ← @Cacheable（Spring Cache）
  ├── 例外ハンドリング    ← @ControllerAdvice も AOP 的
  ├── パフォーマンス監視  ← 実行時間計測
  └── 監査ログ          ← 誰が何をしたか記録
```

AOP の利点は、対象クラスのコードを一切変更せずに横断的処理を差し込めること。
このプロジェクトでも `LoggingAspect` の導入時に Controller・Service のコードは変更していない。
