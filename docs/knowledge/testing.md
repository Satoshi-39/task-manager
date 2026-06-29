# テスト手法ナレッジ

Java / Spring Boot プロジェクトにおけるテスト手法の整理。

## テストピラミッド

```
        ╱  E2E  ╲           少ない・遅い・高コスト
       ╱─────────╲
      ╱ 統合テスト ╲
     ╱─────────────╲
    ╱   単体テスト    ╲      多い・速い・低コスト
```

下層ほどテスト数を多く、上層ほど少なくするのが基本方針。

## ライブラリの位置付け

```
【フロントエンド（React/Next.js 等）】
  └── MSW ─── fetch/axios のレスポンスをネットワーク層で横取り

【バックエンド（Spring Boot）← このプロジェクト】
  ├── MockMvc ──── HTTPリクエストをサーバー起動なしで模擬
  ├── Mockito ──── クラスの依存をモック（偽物）に差し替え
  └── Database Rider (DBUnit) ── テストデータのDB投入・検証
```

## 各ライブラリ詳細

### Mockito — クラス単位のモック化

テスト対象の依存クラスを偽物に差し替えるライブラリ。
DB やネットワークにアクセスせずにロジックだけをテストできる。

```java
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;  // 偽物のリポジトリ

    @InjectMocks
    private TaskService taskService;        // テスト対象

    @Test
    void shouldReturnTask() {
        // Arrange: findById が呼ばれたら固定の Task を返す
        Task task = new Task("TASK-001", "Test", ...);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // Act
        Task result = taskService.getTask(1L);

        // Assert
        assertEquals("Test", result.getTitle());
        verify(taskRepository).findById(1L);  // 呼ばれたことを検証
    }
}
```

**使い所**: Service 層のビジネスロジック。Repository を実際に叩かない。

### MockMvc — HTTPリクエストの模擬

実際のサーバーを起動せずに、コントローラの HTTP リクエスト/レスポンスをテストする Spring の仕組み。

```java
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;  // Service はモック

    @Test
    void shouldGetAllTasks() throws Exception {
        when(taskService.getAllTasks()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Test"));
    }
}
```

**使い所**: Controller 層のHTTP処理（ルーティング、ステータスコード、レスポンス形式）。

### Database Rider (DBUnit) — DBテストデータ管理

DBUnit のモダンなラッパー。YAML でテストデータを定義し、DB の入出力を検証する。

```
DBUnit（テストデータ投入・検証エンジン）← 元のライブラリ
  └── Database Rider（アノテーション対応、YAML/JSON対応、JUnit 5 統合）
```

```java
@DataJpaTest
@DBRider
class TaskRepositoryDbUnitTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)  // テスト前にデータ投入
    void shouldFindByStatus() {
        List<Task> todos = taskRepository.findByStatus(TaskStatus.TODO);
        assertEquals(1, todos.size());
    }

    @Test
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    @ExpectedDataSet(value = "datasets/expected-after-delete.yml")  // テスト後のDB検証
    void shouldDeleteTask() {
        taskRepository.deleteById(1L);
        taskRepository.flush();
    }
}
```

YAML データセット例:
```yaml
tasks:
  - id: 1
    task_number: "TASK-001"
    title: "テスト用タスク"
    status: "TODO"
    priority: "HIGH"
```

**DBUnit と Database Rider の違い**:

| | DBUnit | Database Rider |
|---|---|---|
| 関係 | 元のライブラリ | DBUnit のラッパー |
| データ形式 | XML（冗長） | YAML / JSON / XML |
| JUnit 5 | 非対応 | `@DBRider` で対応 |
| Spring Boot 3.x | 自力統合が必要 | `rider-spring` で対応 |

**使い所**: Repository 層のDB操作（クエリの正しさ、データ整合性）。

### MSW (Mock Service Worker) — フロントエンド用APIモック

JavaScript/TypeScript のフロントエンドで、fetch/axios のレスポンスをネットワーク層で横取りするライブラリ。

```javascript
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

const server = setupServer(
  http.get('/api/tasks', () => {
    return HttpResponse.json([{ title: 'テスト' }])
  })
)
```

**使い所**: React 等のフロントエンドテスト。バックエンドを起動せずに画面の動作を検証。

## テスト手法の使い分け

| テスト対象 | 層 | 使うもの | DB必要 | 速度 |
|---|---|---|---|---|
| ビジネスロジック | Service | Mockito | 不要 | 速い |
| HTTP 入出力 | Controller | MockMvc + Mockito | 不要 | 速い |
| DBクエリ | Repository | Database Rider + 実DB | 必要 | やや遅い |
| 画面の動作 | フロントエンド | MSW | 不要 | 速い |
| 全結合 | 全層 | @SpringBootTest | 必要 | 遅い |

## JaCoCo — カバレッジ計測

テストの網羅率を計測するツール。行カバレッジ・分岐カバレッジ等をHTMLレポートで可視化する。

```bash
# レポート生成
./gradlew clean test jacocoTestReport

# ブラウザで確認
open build/reports/jacoco/test/html/index.html
```

現場でのカバレッジ目安:

| レベル | 基準 | 備考 |
|---|---|---|
| 最低限 | 50%以上 | ビジネスロジックのメインパスをカバー |
| 一般的 | 70〜80% | 多くの現場で求められる水準 |
| 厳格 | 90%以上 | 金融・医療等のミッションクリティカル |

※ カバレッジが高い＝品質が高いではない。意味のあるテストを書くことが重要。
