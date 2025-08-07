# CSV バッチプロセッサー - システム設計書

## 1. システム概要

### 1.1 システム名
CSV バッチプロセッサー（csv-batch-processor）

### 1.2 システム目的
Oracle Database から従業員データを取得し、SOAP API から追加の従業員詳細情報を取得して、両方のデータを結合したCSVファイルを出力するバッチ処理システム。

### 1.3 主要機能
- Oracle Database からの従業員基本情報の取得
- SOAP API からの従業員詳細情報（level、bonus、status）の取得
- データの結合とCSVファイルへの出力
- AWS S3への自動アップロード機能
- LocalStack環境での開発・テストサポート
- Docker環境での実行サポート

## 2. システム構成

### 2.1 アーキテクチャ概要
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Oracle DB     │    │  Spring Cloud   │    │   SOAP Stub     │
│   (Port: 1521)  │◄───┤  CSV Processor   ├───►│   (Port: 8081)  │
│                 │    │  (Port: 8080)   │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────┐      ┌─────────────────┐
                       │   AWS S3    │      │  AWS LocalStack │
                       │ CSV Storage │◄─────│  (Port: 4566)   │
                       │result.csv   │      │   S3 Emulation  │
                       └─────────────┘      └─────────────────┘
                              │
                              ▼
                       ┌─────────────┐
                       │Local Backup │
                       │   (Option)  │
                       └─────────────┘
```

### 2.2 技術スタック
- **開発言語**: Java 21
- **フレームワーク**: Spring Cloud 2023.0.0 + Spring Boot 3.2.0
- **データベース**: Oracle Database XE 21
- **ビルドツール**: Maven
- **コンテナ化**: Docker, Docker Compose
- **クラウドサービスエミュレーション**: AWS LocalStack
- **CSV処理**: OpenCSV
- **テストフレームワーク**: JUnit 5
- **コードカバレッジ**: JaCoCo

### 2.3 コンポーネント構成

#### 2.3.1 メインアプリケーション (csv-batch-processor)
- **ポート**: 8080
- **機能**: データ取得、変換、CSV出力の主処理
- **依存関係**: Oracle DB、SOAP Stub

#### 2.3.2 Oracle Database (oracle-db)
- **イメージ**: gvenzl/oracle-xe:21-slim
- **ポート**: 1521
- **データベース**: XEPDB1
- **ユーザー**: csvuser/csvpass

#### 2.3.3 SOAP API スタブ (soap-stub)
- **ポート**: 8081 (内部8080)
- **機能**: 従業員詳細情報のモックAPI提供
- **プロトコル**: SOAP Web Services

## 3. データモデル

### 3.1 データベース設計

#### 3.1.1 employees テーブル
```sql
CREATE TABLE employees (
    employee_id    NUMBER(10) PRIMARY KEY,
    employee_name  VARCHAR2(100) NOT NULL,
    department     VARCHAR2(50),
    email          VARCHAR2(100),
    hire_date      DATE,
    salary         NUMBER(10,2)
);
```

### 3.2 データフロー
```
Oracle DB (employees)
    ↓ SELECT *
[Employee Entity]
    ↓ for each employee
SOAP API (getEmployeeDetails)
    ↓ response
[EmployeeCsvData DTO]
    ↓ combine data
CSV File (result.csv)
```

### 3.3 CSV出力形式
| カラム名 | データ型 | 説明 |
|---------|---------|------|
| employeeId | Long | 従業員ID |
| employeeName | String | 従業員名 |
| department | String | 部署 |
| email | String | メールアドレス |
| hireDate | LocalDate | 入社日 |
| salary | BigDecimal | 給与 |
| level | String | レベル（SOAP APIから取得） |
| bonus | BigDecimal | ボーナス（SOAP APIから取得） |
| status | String | ステータス（SOAP APIから取得） |

## 4. アプリケーション設計

### 4.1 パッケージ構成
```
com.example.csvbatch
├── CsvBatchProcessorApplication.java    # メインクラス
├── client/
│   └── SoapClient.java                  # SOAP API クライアント
├── config/
│   ├── AwsConfig.java                   # AWS設定
│   ├── DataInitializer.java            # データ初期化
│   └── WebServiceConfig.java           # Web Service 設定
├── dto/
│   └── EmployeeCsvData.java            # CSV出力用DTO
├── entity/
│   └── Employee.java                   # JPA エンティティ
├── exception/
│   ├── CsvProcessingException.java      # CSV処理例外
│   ├── S3UploadException.java          # S3アップロード例外
│   └── DataProcessingException.java    # データ処理例外
├── repository/
│   └── EmployeeRepository.java         # データアクセス層
└── service/
    ├── CsvExportService.java           # CSV出力サービス
    └── S3ClientService.java            # S3クライアントサービス
```

### 4.2 主要クラス設計

#### 4.2.1 CsvExportService
- **責務**: CSV出力の主処理とS3アップロード
- **メソッド**:
  - `exportEmployeesToCsv()`: メイン処理メソッド
  - `writeCsvFile()`: CSV書き込み処理
  - `uploadToS3()`: S3アップロード処理
- **エラーハンドリング**: リトライ機能、フォールバック処理

#### 4.2.2 S3ClientService
- **責務**: AWS S3との通信処理とバケット自動管理
- **メソッド**:
  - `@PostConstruct initializeS3Bucket()`: S3バケット自動作成・確認
  - `uploadFile(String key, InputStream content)`: ファイルアップロード
  - `downloadFile(String key)`: ファイルダウンロード
  - `deleteFile(String key)`: ファイル削除
  - `listFiles(String prefix)`: ファイル一覧取得
  - `bucketExists()`: バケット存在確認
  - `createBucket()`: バケット作成
- **エラーハンドリング**: 接続エラー、認証エラー、容量制限エラー対応
- **自動初期化機能**: アプリケーション起動時に必要なS3バケットを自動作成

#### 4.2.3 SoapClient
- **責務**: SOAP API との通信
- **メソッド**:
  - `getEmployeeDetails(Long employeeId)`: 従業員詳細取得
- **エラーハンドリング**: タイムアウト、接続エラー、レスポンス検証

#### 4.2.4 Employee Entity
- **責務**: データベーステーブルとのマッピング
- **アノテーション**: `@Entity`, `@Table`, `@Id`, `@Column`

#### 4.2.5 EmployeeCsvData DTO
- **責務**: CSV出力用のデータ転送オブジェクト
- **特徴**: Lombokを使用したbuilderパターン

#### 4.2.6 例外処理クラス群
- **CsvProcessingException**: CSV処理関連エラー
- **S3UploadException**: S3アップロード関連エラー
- **DataProcessingException**: データ処理関連エラー

## 5. 設定管理

### 5.1 application.yml
```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@oracle-db:1521/XEPDB1
    username: csvuser
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
    hikari:
      # 環境変数で上書き可能な設定（Docker環境で強化される）
      connection-timeout: ${SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT:30000}
      validation-timeout: ${SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT:5000}
      maximum-pool-size: ${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:10}
      minimum-idle: ${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:2}
      initialization-fail-timeout: ${SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT:60000}
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: CsvBatchHikariPool
      leak-detection-threshold: 60000
  jpa:
    hibernate:
      ddl-auto: create

soap:
  api:
    url: http://soap-stub:8080/ws
    timeout:
      connection: 30000
      read: 60000
    retry:
      max-attempts: 3
      delay: 1000

csv:
  output:
    path: /app/output/result.csv
  export:
    s3-upload: true
    local-backup: true
    
aws:
  region: ap-northeast-1
  s3:
    bucket-name: csv-export-bucket
    endpoint: http://localstack:4566  # LocalStack用
  credentials:
    access-key: ${AWS_ACCESS_KEY:test}
    secret-key: ${AWS_SECRET_KEY:test}
    
# エラーハンドリング設定
error:
  handling:
    max-retries: 3
    retry-delay: 2000
    circuit-breaker:
      failure-threshold: 5
      timeout: 10000
```

### 5.2 application-docker.yml (Docker環境専用設定)
```yaml
# Docker環境専用設定
# HikariCP接続プール設定の強化で起動順序問題を解決

spring:
  datasource:
    hikari:
      # 接続タイムアウト（60秒）
      connection-timeout: 60000
      # バリデーションタイムアウト（5秒）
      validation-timeout: 5000
      # 最大プールサイズ
      maximum-pool-size: 5
      # 最小アイドル接続数
      minimum-idle: 1
      # 初期化失敗タイムアウト（2分）
      initialization-fail-timeout: 120000
      # 接続リークの検出（デバッグ用）
      leak-detection-threshold: 60000
      # プール名（ログ識別用）
      pool-name: CsvBatchHikariPool
      # アイドル接続の最大生存時間（10分）
      max-lifetime: 600000
      # アイドル接続のタイムアウト（10分）
      idle-timeout: 600000
    
  jpa:
    hibernate:
      # DDLの自動実行を検証のみに制限（本番環境では更に安全）
      ddl-auto: validate
    # SQL実行ログの無効化（パフォーマンス向上）
    show-sql: false
    properties:
      hibernate:
        # バッチサイズの最適化
        jdbc:
          batch_size: 20
        # オーダリングの有効化（パフォーマンス向上）
        order_inserts: true
        order_updates: true

# ログ設定（接続問題のデバッグ用）
logging:
  level:
    # HikariCP関連のログレベル
    com.zaxxer.hikari: INFO
    com.zaxxer.hikari.HikariConfig: DEBUG
    com.zaxxer.hikari.HikariDataSource: DEBUG
    # JDBC接続関連のログレベル
    org.hibernate.engine.jdbc.connections: DEBUG
    org.hibernate.engine.jdbc.spi.SqlExceptionHelper: DEBUG
    # Oracle JDBC関連のログレベル
    oracle.jdbc: WARN
    # アプリケーション関連のログレベル
    com.example.csvbatch: INFO
    # Spring関連のログレベル
    org.springframework.jdbc: DEBUG
    org.springframework.transaction: DEBUG
```

### 5.3 Docker Compose 設定
- **サービス間依存関係**: health check による起動順序制御
- **ボリュームマウント**: CSV出力ファイルの永続化
- **ネットワーク**: 内部ネットワークでのサービス間通信
- **起動順序制御**: 
  - Oracle DB → LocalStack → SOAP Stub → CSV Batch Processor
  - 各サービスのヘルスチェック完了後に次サービス起動
  - csv-batch-processorのstart_period: 120秒（十分な待機時間確保）
  - restart: on-failure ポリシーで自動復旧

#### 5.3.1 Docker起動順序問題解決策
```yaml
csv-batch-processor:
  depends_on:
    oracle-db:
      condition: service_healthy
    soap-stub:
      condition: service_healthy
    localstack:
      condition: service_healthy
  restart: on-failure
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 120s  # 延長された待機時間
```

#### 5.3.2 HikariCP設定による接続タイムアウト対策
- **connection-timeout**: 60秒（依存サービス準備待ち）
- **initialization-fail-timeout**: 120秒（初期化失敗許容時間）
- **validation-timeout**: 5秒（接続検証時間）
- **leak-detection-threshold**: 60秒（デバッグ・監視用）

## 6. テスト設計

### 6.1 テスト戦略
- **目標カバレッジ**: 80%以上
- **テストツール**: JUnit 5, Mockito
- **カバレッジ測定**: JaCoCo

### 6.2 テストクラス構成
```
src/test/java/com/example/csvbatch/
├── CsvBatchProcessorApplicationTests.java
├── client/
│   └── SoapClientTest.java
├── dto/
│   └── EmployeeCsvDataTest.java
├── entity/
│   └── EmployeeTest.java
├── exception/
│   ├── CsvProcessingExceptionTest.java
│   ├── S3UploadExceptionTest.java
│   └── DataProcessingExceptionTest.java
├── integration/
│   ├── DatabaseIntegrationTest.java
│   ├── LocalStackIntegrationTest.java
│   └── S3IntegrationTest.java
├── repository/
│   └── EmployeeRepositoryTest.java
└── service/
    ├── CsvExportServiceTest.java
    └── S3ClientServiceTest.java
```

## 7. 実行環境

### 7.1 Docker環境構築

#### 7.1.1 本番環境
```bash
# 環境起動
docker-compose up -d

# ログ確認
docker-compose logs csv-batch-processor

# 環境停止
docker-compose down
```

#### 7.1.2 テスト環境
```bash
# テスト環境起動
docker-compose -f docker-compose.test.yml up -d

# ログ確認
docker-compose -f docker-compose.test.yml logs csv-batch-processor-test

# テスト環境停止
docker-compose -f docker-compose.test.yml down

# 強制停止（応答しない場合）
docker-compose -f docker-compose.test.yml down --remove-orphans --volumes

# 個別コンテナ強制停止
docker stop $(docker ps -q)

# システム全体クリーンアップ
docker system prune -a
```

### 7.2 出力ファイル

#### 7.2.1 S3ストレージ（主要）
- **バケット**: `csv-export-bucket`
- **キー形式**: `exports/YYYY/MM/DD/result-{timestamp}.csv`
- **形式**: CSV（ヘッダー付き）
- **エンコーディング**: UTF-8
- **メタデータ**: 処理日時、レコード数、処理時間

#### 7.2.2 ローカルバックアップ（オプション）
- **場所**: `./output/result.csv`
- **用途**: S3アップロード失敗時のフォールバック
- **保持期間**: 7日間（自動削除）

## 8. 運用・保守

### 8.1 ログ設計
- **ログレベル**: INFO, ERROR
- **出力内容**: 処理開始/終了、エラー詳細、処理件数

### 8.2 エラーハンドリング

#### 8.2.1 データベース接続エラー
- **戦略**: HikariCP接続プール強化による起動順序問題解決
- **対応**: 
  - 接続タイムアウト延長（30秒→60秒）
  - 初期化失敗タイムアウト延長（60秒→120秒）
  - プールサイズ最適化（Docker環境: 最大5, 最小1）
  - 自動リトライ機能（restart: on-failure）
- **監視**: リーク検出、接続プール状態の詳細ログ
- **フォールバック**: キャッシュデータによる部分処理

#### 8.2.2 SOAP API エラー
- **戦略**: サーキットブレーカーパターン
- **リトライ**: 最大3回、遅延1秒
- **タイムアウト**: 接続30秒、読み取り60秒
- **フォールバック**: デフォルト値での処理継続

#### 8.2.3 S3アップロードエラー
- **戦略**: マルチパートアップロード、リトライ機能、自動バケット管理
- **対応**: 
  - バケット未存在エラー: 自動バケット作成（@PostConstruct）
  - ネットワークエラー: 自動リトライ（最大3回）
  - 認証エラー: アラート送信、処理停止
  - 容量制限エラー: 古いファイル削除後再試行
- **自動初期化**: アプリケーション起動時にS3バケットの存在確認・作成
- **フォールバック**: ローカルファイル保存

#### 8.2.4 CSV処理エラー
- **戦略**: 行レベルエラー処理
- **対応**: 
  - データ不整合: ログ出力、スキップして継続
  - フォーマットエラー: デフォルト値設定
  - メモリ不足: ストリーミング処理への切り替え

#### 8.2.5 監視・アラート
- **メトリクス**: 処理時間、エラー率、成功率
- **アラート**: 連続失敗、処理時間超過
- **通知**: SNS、CloudWatch Logs

### 8.3 監視ポイント
- **処理時間**: 大量データ処理時のパフォーマンス
- **メモリ使用量**: JVMヒープサイズの監視
- **ディスク容量**: CSV出力先の容量監視

## 9. セキュリティ考慮事項

### 9.1 認証・認可
- **データベース**: 専用ユーザー（csvuser）による接続
- **SOAP API**: 認証なし（開発・テスト用スタブ）

### 9.2 データ保護
- **機密情報**: パスワードは環境変数で管理
- **データ暗号化**: 現時点では未実装
- **アクセス制御**: Dockerネットワーク内での通信制限

## 10. 今後の拡張予定

### 10.1 機能拡張
- **バッチスケジューリング**: Cron式による定期実行
- **データ増分処理**: 更新データのみの処理対応
- **複数フォーマット対応**: Excel、JSON出力対応
- **S3ライフサイクル管理**: 自動アーカイブ、削除ポリシー
- **データ圧縮**: ZIP、GZIP圧縮対応
- **暗号化**: S3サーバーサイド暗号化（SSE-S3、SSE-KMS）

### 10.2 非機能要件
- **パフォーマンス向上**: 
  - 並列処理、ストリーミング処理
  - S3マルチパートアップロード
  - 接続プール最適化
- **可用性向上**: 
  - サーキットブレーカー実装
  - ヘルスチェック強化
  - 自動フェイルオーバー
- **セキュリティ強化**: 
  - HTTPS通信、データ暗号化
  - IAMロールベース認証
  - VPC Endpoint使用