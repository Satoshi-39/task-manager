# データアクセス層ナレッジ

Java のデータベースアクセス技術の階層構造と使い分けに関する知識整理。
このプロジェクトの実装コードを例に、JDBC・JPA・MyBatis の関係を解説する。

## 全体マップ — 技術の階層構造

```
アプリケーションコード
    │
    ├── Spring Data JPA          ← リポジトリの自動実装（メソッド名 → クエリ）
    │       │
    │       └── JPA (Hibernate)  ← ORM（エンティティ ↔ テーブル のマッピング）
    │               │
    ├── MyBatis                  ← SQLマッパー（SQL は自分で書く、マッピングは自動）
    │       │
    └── JdbcTemplate             ← Spring の JDBC ラッパー（接続管理・例外変換）
            │
            └── JDBC (java.sql)  ← Java 標準の DB 接続 API（最下層）
                    │
                    └── JDBC ドライバ  ← DB 固有の通信処理（H2, PostgreSQL 等）
                            │
                            └── データベース
```

**重要**: MyBatis も JPA（Hibernate）も、裏では JDBC を使っている。
JDBC は Java における**データベースアクセスの最下層 API** であり、別物ではなく土台。

## JPA と Hibernate の関係

JPA は**仕様（インタフェース）**、Hibernate は**その実装**。
Java の `List` と `ArrayList` の関係に似ている。

```
JPA (jakarta.persistence)    ← 仕様（アノテーション、API の定義だけ）
  ├── Hibernate               ← 実装 A（Spring Boot のデフォルト）
  ├── EclipseLink              ← 実装 B
  └── OpenJPA                  ← 実装 C
```

コード上は JPA のアノテーション（`@Entity`, `@Column` 等）を使い、
実行時に Hibernate が実際の処理を行う。
Spring Boot では `spring-boot-starter-data-jpa` に Hibernate が含まれている。

### なぜ仕様と実装を分離するのか

- コードが JPA 仕様に依存するため、Hibernate → EclipseLink への切り替えが理論上可能
- 実務では Hibernate 固有機能を使うことも多く、完全な切り替えは稀
- ただし JPA 標準のアノテーションを使うことで、コードの移植性が高まる

## 各技術の位置づけと比較

| 技術 | 分類 | SQL | マッピング | 特徴 |
|---|---|---|---|---|
| **JDBC** | 標準 API | 手動 | 手動（`ResultSet` を自分で処理） | 最も低レベル。全てを自分で制御 |
| **JdbcTemplate** | Spring ラッパー | 手動 | 半自動（`RowMapper`） | 接続管理・例外変換を自動化 |
| **JPA (Hibernate)** | ORM | 自動生成 | 自動（`@Entity` ↔ テーブル） | エンティティ中心。SQL を書かない |
| **MyBatis** | SQL マッパー | 手動 | 自動（XML / アノテーション） | SQL を書くが、結果のマッピングは自動 |

### 生の JDBC と JdbcTemplate の違い

```java
// 生の JDBC（java.sql）— 冗長で例外処理が煩雑
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
try {
    conn = dataSource.getConnection();
    ps = conn.prepareStatement("SELECT * FROM tasks WHERE status = ?");
    ps.setString(1, "TODO");
    rs = ps.executeQuery();
    while (rs.next()) {
        // 手動でマッピング
    }
} catch (SQLException e) {
    // チェック例外の処理
} finally {
    // 3つのリソースを個別にクローズ（nullチェック付き）
    if (rs != null) rs.close();
    if (ps != null) ps.close();
    if (conn != null) conn.close();
}

// JdbcTemplate — 接続管理・例外変換・リソース解放を自動化
jdbcTemplate.query(
    "SELECT * FROM tasks WHERE status = ?",
    (rs, rowNum) -> mapToTask(rs),  // RowMapper（ラムダで簡潔に）
    "TODO"
);
```

JdbcTemplate が自動化するもの:
- `Connection` の取得と解放
- `PreparedStatement` の生成と解放
- `ResultSet` のクローズ
- `SQLException`（チェック例外）→ `DataAccessException`（非チェック例外）への変換

## JPA の SQL 自動生成の仕組み

JPA は主に2つの仕組みで SQL を自動生成する。

### 1. エンティティのメタデータ — テーブルとカラムの対応

`@Entity`, `@Table`, `@Column` などのアノテーションから
テーブル名・カラム名を把握し、SQL を組み立てる。

```java
@Entity
@Table(name = "tasks")    // ← テーブル名
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;       // ← tasks.id にマッピング

    @Column(nullable = false)
    private String title;  // ← tasks.title にマッピング

    @Enumerated(EnumType.STRING)
    private TaskStatus status;  // ← tasks.status にマッピング（文字列で保存）
}
```

### 2. メソッド名の解析（Query Method）— Spring Data JPA

Spring Data JPA がリポジトリインタフェースのメソッド名を解析し、
JPQL → SQL を自動生成する。

```java
// メソッド名 → JPQL → SQL の変換例
List<Task> findByStatus(TaskStatus status);
// → SELECT t FROM Task t WHERE t.status = ?1
// → SELECT * FROM tasks WHERE status = ?

List<Task> findByStatusAndPriority(TaskStatus status, TaskPriority priority);
// → SELECT t FROM Task t WHERE t.status = ?1 AND t.priority = ?2
// → SELECT * FROM tasks WHERE status = ? AND priority = ?

long countByStatus(TaskStatus status);
// → SELECT COUNT(t) FROM Task t WHERE t.status = ?1
// → SELECT COUNT(*) FROM tasks WHERE status = ?
```

使えるキーワード:

| キーワード | 例 | 生成される WHERE 句 |
|---|---|---|
| `And` | `findByStatusAndPriority` | `WHERE status = ? AND priority = ?` |
| `Or` | `findByStatusOrPriority` | `WHERE status = ? OR priority = ?` |
| `Between` | `findByDueDateBetween` | `WHERE due_date BETWEEN ? AND ?` |
| `LessThan` | `findByDueDateLessThan` | `WHERE due_date < ?` |
| `OrderBy` | `findByStatusOrderByDueDateAsc` | `WHERE status = ? ORDER BY due_date ASC` |
| `IsNull` | `findByDueDateIsNull` | `WHERE due_date IS NULL` |

### 3. @Query による明示的な JPQL

メソッド名の解析では表現しきれない複雑なクエリは `@Query` で JPQL を直接記述する。

```java
@Query("SELECT t FROM Task t WHERE t.dueDate < :now AND t.status NOT IN ('DONE', 'CANCELLED')")
List<Task> findOverdueTasks(@Param("now") LocalDateTime now);
```

JPQL はエンティティのフィールド名（`t.dueDate`）で書き、
Hibernate がテーブルのカラム名（`due_date`）に変換して SQL を生成する。

## このプロジェクトでの使い分け

| リポジトリ | 技術 | 用途 | ファイル |
|---|---|---|---|
| `TaskRepository` | Spring Data JPA | CRUD・JPQL クエリ・悲観的ロック | `repository/TaskRepository.java` |
| `TaskJdbcRepository` | `JdbcTemplate` | 集計クエリ（`GROUP BY`）・サマリー取得 | `repository/TaskJdbcRepository.java` |

### なぜ JPA と JdbcTemplate を併用するのか

JPA はエンティティ単位の操作に強いが、集計クエリの結果は
エンティティに対応しないことが多い。

```java
// JPA で表現しにくい — 結果が Task エンティティではなく Map<String, Long>
public Map<String, Long> countGroupByStatus() {
    String sql = "SELECT status, COUNT(*) AS cnt FROM tasks GROUP BY status ORDER BY status";

    return jdbcTemplate.query(sql, (ResultSet rs) -> {
        Map<String, Long> result = new LinkedHashMap<>();
        while (rs.next()) {
            result.put(rs.getString("status"), rs.getLong("cnt"));
        }
        return result;
    });
}
```

JPA でも `@Query` + `Object[]` や DTO プロジェクションで書けるが、
`JdbcTemplate` の方が素直で読みやすい場合がある。

## JPA + JdbcTemplate vs JPA + MyBatis

| 組み合わせ | メリット | デメリット |
|---|---|---|
| **JPA + JdbcTemplate** | 追加依存なし。Spring Boot 標準サポート | SQL を文字列で書く。型安全でない |
| **JPA + MyBatis** | XML/アノテーションで SQL 管理。型安全なマッピング | 設定が複雑。DataSource の共有に注意が必要 |
| **MyBatis 単体** | SQL を完全制御。複雑なクエリに強い | CRUD も全て SQL を書く必要がある |
| **JPA 単体** | CRUD は自動。エンティティ中心の開発 | 複雑な集計クエリが書きにくい |

### 実務での選択指針

```
プロジェクトの性質
  │
  ├── CRUD 中心 + 一部集計 → JPA + JdbcTemplate（このプロジェクト）
  │
  ├── 複雑な SQL が多い → MyBatis 単体 or JPA + MyBatis
  │
  ├── 既存 DB のテーブルが複雑 → MyBatis（テーブル構造に合わせた SQL を書ける）
  │
  └── DDD（ドメイン駆動設計） → JPA（エンティティ = ドメインオブジェクト）
```

## JDBC が「土台」であることの確認

### JPA 経由の場合

`TaskRepository.findByStatus(TaskStatus.TODO)` を呼ぶと内部では:

```
1. Spring Data JPA: メソッド名 findByStatus を解析
       ↓
2. Hibernate: JPQL を生成 → SQL に変換
       ↓
3. JDBC: PreparedStatement で SQL を実行
       ↓
4. JDBC: ResultSet から行データを取得
       ↓
5. Hibernate: ResultSet → Task エンティティにマッピング
       ↓
6. アプリケーション: List<Task> を受け取る
```

### JdbcTemplate 経由の場合

`TaskJdbcRepository.countGroupByStatus()` を呼ぶと内部では:

```
1. JdbcTemplate: Connection を取得
       ↓
2. JDBC: PreparedStatement で SQL を実行
       ↓
3. JDBC: ResultSet から行データを取得
       ↓
4. ラムダ（ResultSetExtractor）: ResultSet → Map にマッピング
       ↓
5. JdbcTemplate: Connection を解放
       ↓
6. アプリケーション: Map<String, Long> を受け取る
```

どちらも最終的には JDBC の `PreparedStatement` と `ResultSet` を通じて
DB とやり取りしている。JPA や JdbcTemplate はその上の便利な抽象層。

## Spring Boot での設定

このプロジェクトでは `spring-boot-starter-data-jpa` を依存に含めるだけで、
JPA（Hibernate）と JdbcTemplate の**両方**が使える。

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // ↑ Hibernate + JdbcTemplate + HikariCP（コネクションプール）が全て含まれる
    runtimeOnly("com.h2database:h2")  // JDBC ドライバ
}
```

`spring-boot-starter-data-jpa` に含まれるもの:

```
spring-boot-starter-data-jpa
├── spring-boot-starter-jdbc     ← JdbcTemplate + HikariCP
│   ├── spring-jdbc              ← JdbcTemplate 本体
│   └── HikariCP                 ← コネクションプール
├── hibernate-core               ← JPA 実装（Hibernate）
└── spring-data-jpa              ← Spring Data JPA（リポジトリ自動実装）
```

`JdbcTemplate` は `spring-boot-starter-jdbc` に含まれており、
`spring-boot-starter-data-jpa` がこれを内包しているため、
追加の依存なしで `JdbcTemplate` を `@Autowired` / コンストラクタインジェクションで使える。
