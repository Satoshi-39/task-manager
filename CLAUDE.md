# Task Manager REST API

Java Gold 学習用の Spring Boot タスク管理 API。

## 技術スタック

- Java 21 (Amazon Corretto) / Spring Boot 3.5.0 / Gradle (Kotlin DSL)
- Spring Data JPA + H2 Database (インメモリ)
- JUnit 5 + Mockito + Database Rider (DBUnit) + JaCoCo

## よく使うコマンド

```bash
./gradlew bootRun                                    # アプリ起動
./gradlew bootRun --args='--spring.profiles.active=dev'  # dev プロファイル（H2 Console 有効）
./gradlew test                                       # 全テスト実行
./gradlew clean test jacocoTestReport                # テスト + カバレッジレポート
./gradlew compileJava                                # コンパイルのみ
```

## プロジェクト構成

レイヤードアーキテクチャ（Controller → Service → Repository → DB）。

```
src/main/java/com/example/taskmanager/
├── controller/    REST エンドポイント
├── service/       ビジネスロジック
├── repository/    JPA / JDBC データアクセス
├── domain/        エンティティ・Enum
├── dto/           リクエスト / レスポンス DTO
├── exception/     カスタム例外・GlobalExceptionHandler
├── validation/    カスタムバリデーション (@FutureOrToday)
├── config/        AsyncConfig 等
└── common/        TaskNumberGenerator, TaskFilterBuilder
```

## コーディング規約

- パッケージ構成は上記のレイヤード構造を維持する
- 日本語コメントは Javadoc に Java Gold トピック（学習ポイント）を記載する形式
- テストは `src/test/java` に本体と同じパッケージ構成で配置
- SQL 初期化は `schema.sql` + `data.sql`（JPA の ddl-auto は none）
