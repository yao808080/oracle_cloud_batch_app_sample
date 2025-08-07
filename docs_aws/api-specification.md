# API仕様書

## 1. SOAP API仕様

### 1.1 概要
従業員詳細情報を取得するためのSOAP Web Serviceの仕様。Spring Retry機能とサーキットブレーカーパターンによる堅牢な通信を実現している。

### 1.2 エンドポイント情報
- **URL**: `http://soap-stub:8080/ws`
- **WSDL**: `http://soap-stub:8080/ws/employees.wsdl`
- **ネームスペース**: `http://example.com/employees`
- **タイムアウト設定**: 接続30秒、読み取り60秒
- **リトライ設定**: 最大3回、指数バックオフ（初期遅延1秒、倍率2.0）
- **サーキットブレーカー**: 5回失敗でオープン、10秒間オープン、30秒後リセット

### 1.3 操作仕様

#### 1.3.1 getEmployeeDetails
従業員IDに基づいて詳細情報を取得する。

**リクエスト**
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:emp="http://example.com/employees">
    <soap:Header/>
    <soap:Body>
        <emp:getEmployeeDetailsRequest>
            <emp:employeeId>1001</emp:employeeId>
        </emp:getEmployeeDetailsRequest>
    </soap:Body>
</soap:Envelope>
```

**レスポンス**
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:emp="http://example.com/employees">
    <soap:Header/>
    <soap:Body>
        <emp:getEmployeeDetailsResponse>
            <emp:employeeDetails>
                <emp:employeeId>1001</emp:employeeId>
                <emp:level>Senior</emp:level>
                <emp:bonus>150000.00</emp:bonus>
                <emp:status>Active</emp:status>
            </emp:employeeDetails>
        </emp:getEmployeeDetailsResponse>
    </soap:Body>
</soap:Envelope>
```

### 1.4 データ型定義

#### 1.4.1 EmployeeDetails
| 要素名 | データ型 | 必須 | 説明 |
|--------|---------|------|------|
| employeeId | long | ○ | 従業員ID |
| level | string | ○ | 従業員レベル（Junior/Mid/Senior/Manager） |
| bonus | decimal | ○ | ボーナス金額 |
| status | string | ○ | ステータス（Active/Inactive/On Leave） |

### 1.5 エラーハンドリング

#### 1.5.1 従業員が見つからない場合
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Header/>
    <soap:Body>
        <soap:Fault>
            <faultcode>Client</faultcode>
            <faultstring>Employee not found</faultstring>
            <detail>
                <emp:employeeNotFound xmlns:emp="http://example.com/employees">
                    <emp:employeeId>9999</emp:employeeId>
                    <emp:message>Employee with ID 9999 does not exist</emp:message>
                </emp:employeeNotFound>
            </detail>
        </soap:Fault>
    </soap:Body>
</soap:Envelope>
```

#### 1.5.2 フォールバック機能
サーキットブレーカーがオープンした場合、以下のデフォルト値を返却：
- **level**: "Unknown"
- **bonus**: 0.00
- **status**: "Unavailable"

#### 1.5.3 リトライ対象例外
- `WebServiceIOException`: ネットワーク接続エラー
- `RuntimeException`: 一般的な実行時エラー
- サーバー応答タイムアウト

## 2. AWS S3 API仕様

### 2.1 概要
CSV出力ファイルのクラウドストレージ管理を行うAWS S3統合サービスの仕様。LocalStackでの開発環境サポートと自動バケット作成機能を含む。

### 2.2 S3初期化仕様

#### 2.2.1 S3バケット自動初期化
- **メソッド**: `@PostConstruct initializeS3Bucket()`
- **説明**: アプリケーション起動時に自動実行されるS3バケット初期化処理
- **動作**:
  1. `bucketExists()` でバケット存在確認
  2. バケットが存在しない場合、`createBucket()` で自動作成
  3. 成功・失敗に関わらずログ出力
  4. エラー発生時もアプリケーション起動は継続

**初期化ログ例**
```
INFO - S3 bucket 'csv-export-bucket' does not exist. Creating bucket...
INFO - S3 bucket 'csv-export-bucket' created successfully
```

#### 2.2.2 バケット存在確認
- **メソッド**: `bucketExists()`
- **説明**: HeadBucketリクエストでバケット存在を確認
- **戻り値**: `boolean` (存在する場合true)

#### 2.2.3 バケット作成
- **メソッド**: `createBucket()`
- **説明**: 指定されたバケット名でS3バケットを作成
- **例外処理**:
  - `BucketAlreadyExistsException`: 他プロセスが同時作成した場合
  - `BucketAlreadyOwnedByYouException`: 既に所有している場合
  - その他の例外: `S3UploadException` でラップして再スロー

### 2.3 S3操作仕様

#### 2.3.1 ファイルアップロード
- **メソッド**: `uploadCsvFile(String csvContent, int recordCount, long processingTime)`
- **説明**: CSVファイルをS3バケットにアップロード（メタデータ付き）
- **キー形式**: `exports/YYYY/MM/DD/result-{timestamp}.csv`
- **リトライ設定**: 最大3回、指数バックオフ（初期遅延2秒）
- **メタデータ**: レコード数、処理時間、アップロード日時

#### 2.3.2 ファイルダウンロード
- **メソッド**: `downloadFile(String key)`
- **説明**: S3バケットからファイルをダウンロード
- **戻り値**: `InputStream`

#### 2.3.3 CSVファイルダウンロード
- **メソッド**: `downloadCsvFile(String key)`
- **説明**: CSVファイルを文字列として取得
- **戻り値**: `String` (UTF-8エンコード)

#### 2.3.4 ファイル一覧取得
- **メソッド**: `listFiles(String prefix)`
- **説明**: 指定プレフィックスでファイル一覧を取得
- **戻り値**: `List<S3Object>` (S3オブジェクト一覧)

#### 2.3.5 CSVファイル一覧取得
- **メソッド**: `listCsvFiles()`
- **説明**: exports/プレフィックスの.csvファイル一覧を取得
- **戻り値**: `List<String>` (ファイルキー一覧)

#### 2.3.6 ファイル削除
- **メソッド**: `deleteFile(String key)`, `deleteCsvFile(String key)`
- **説明**: 指定キーのファイルを削除

### 2.4 S3設定パラメータ
| パラメータ | 説明 | デフォルト値 |
|-----------|------|-------------|
| `aws.s3.bucket-name` | S3バケット名（自動作成対象） | csv-export-bucket |
| `aws.endpoint` | S3エンドポイント（LocalStack用） | http://localstack:4566 |
| `aws.access-key-id` | AWSアクセスキー | test |
| `aws.secret-access-key` | AWSシークレットキー | test |
| `aws.region` | AWSリージョン | ap-northeast-1 |

### 2.5 自動バケット作成の設定要件
S3バケット自動作成機能を使用するには、以下のIAM権限が必要：

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:CreateBucket",
                "s3:HeadBucket",
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::csv-export-bucket",
                "arn:aws:s3:::csv-export-bucket/*"
            ]
        }
    ]
}
```

## 3. REST API仕様（将来拡張用）

### 3.1 概要
バッチ処理の状態確認やCSVダウンロード用のREST APIの将来仕様（現在は未実装）。

### 3.2 エンドポイント一覧

#### 3.2.1 バッチ処理状態確認
- **URL**: `GET /api/batch/status`
- **説明**: 最新のバッチ処理の実行状態を取得

**レスポンス例**
```json
{
    "status": "completed",
    "startTime": "2024-01-15T10:00:00Z",
    "endTime": "2024-01-15T10:05:30Z",
    "recordsProcessed": 1000,
    "outputFile": "/app/output/result.csv",
    "s3Location": "s3://csv-export-bucket/exports/2024/01/15/result-20240115100530.csv"
}
```

#### 3.2.2 CSVファイルダウンロード
- **URL**: `GET /api/export/download`
- **説明**: 生成されたCSVファイルをダウンロード

**レスポンス**: `Content-Type: text/csv`

#### 3.2.3 バッチ処理手動実行
- **URL**: `POST /api/batch/execute`
- **説明**: バッチ処理を手動で実行

**レスポンス例**
```json
{
    "message": "Batch process started",
    "jobId": "batch-20240115-100000"
}
```

## 4. データベースアクセス仕様

### 4.1 従業員データ取得クエリ
```sql
SELECT 
    employee_id,
    employee_name,
    department,
    email,
    hire_date,
    salary
FROM employees
ORDER BY employee_id;
```

### 4.2 接続設定
- **JDBC URL**: 環境変数 `${DB_URL}` または `jdbc:oracle:thin:@oracle-db:1521/XEPDB1`
- **ユーザー名**: 環境変数 `${DB_USERNAME}` または `csvuser`
- **パスワード**: 環境変数 `${DB_PASSWORD}` （セキュリティ強化により必須）
- **接続プール**: HikariCP（Spring Boot デフォルト）
- **データベース方言**: Oracle12cDialect（TestContainers対応）

### 4.3 セキュリティ設定
⚠️ **重要**: 本番環境では必ず環境変数を使用してください
- 開発環境でのデフォルト値は学習・テスト用途のみ
- パスワードのハードコード禁止
- 強力なパスワードポリシーの適用推奨

## 5. CSV出力仕様

### 5.1 ファイル形式
- **ファイル名**: `result.csv`
- **エンコーディング**: UTF-8
- **区切り文字**: カンマ (,)
- **改行コード**: LF (\n)
- **ヘッダー行**: あり
- **出力先**: S3アップロード（メイン）+ ローカルバックアップ（オプション）

### 5.2 S3出力設定
- **S3キー形式**: `exports/YYYY/MM/DD/result-{timestamp}.csv`
- **バケット**: 環境変数 `${AWS_S3_BUCKET}` または `csv-export-bucket`
- **メタデータ**: 処理日時、レコード数、処理時間を自動付与
- **アップロード方式**: マルチパートアップロード（大容量対応）

### 5.3 カラム仕様
| 位置 | カラム名 | データ型 | フォーマット | 例 |
|------|----------|---------|-------------|-----|
| 1 | employeeId | 数値 | そのまま | 1001 |
| 2 | employeeName | 文字列 | ダブルクォート囲み | "田中 太郎" |
| 3 | department | 文字列 | ダブルクォート囲み | "開発部" |
| 4 | email | 文字列 | ダブルクォート囲み | "tanaka@example.com" |
| 5 | hireDate | 日付 | YYYY-MM-DD | 2020-04-01 |
| 6 | salary | 数値 | 小数点以下2桁 | 500000.00 |
| 7 | level | 文字列 | ダブルクォート囲み | "Senior" |
| 8 | bonus | 数値 | 小数点以下2桁 | 150000.00 |
| 9 | status | 文字列 | ダブルクォート囲み | "Active" |

### 5.4 サンプル出力
```csv
employeeId,employeeName,department,email,hireDate,salary,level,bonus,status
1001,"田中 太郎","開発部","tanaka@example.com",2020-04-01,500000.00,"Senior",150000.00,"Active"
1002,"佐藤 花子","営業部","sato@example.com",2019-01-15,450000.00,"Mid",100000.00,"Active"
1003,"高橋 次郎","総務部","takahashi@example.com",2021-10-01,400000.00,"Junior",50000.00,"On Leave"
```

### 5.5 エラーハンドリング
- **データ不整合**: ログ出力後、スキップして処理継続
- **SOAP API障害**: フォールバック値使用（Unknown/0.00/Unavailable）
- **S3アップロード失敗**: ローカルバックアップにフォールバック
- **エラー率制限**: 全レコードの50%以上エラーで処理中断

## 6. 設定パラメータ仕様

### 6.1 アプリケーション設定 (application.yml)

#### 6.1.1 データベース設定
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:oracle:thin:@oracle-db:1521/XEPDB1}
    username: ${DB_USERNAME:csvuser}
    password: ${DB_PASSWORD}  # 環境変数必須
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    database-platform: org.hibernate.dialect.Oracle12cDialect
    hibernate:
      ddl-auto: create  # create|update|validate|none
    show-sql: false     # 本番環境ではfalse推奨
```

#### 6.1.2 SOAP API設定
```yaml
soap:
  api:
    url: http://soap-stub:8080/ws
    timeout:
      connection: 30000  # 接続タイムアウト（ミリ秒）
      read: 60000       # 読み取りタイムアウト（ミリ秒）
    retry:
      max-attempts: 3   # 最大リトライ回数
      delay: 1000      # 初期遅延（ミリ秒）
```

#### 6.1.3 CSV出力・S3設定
```yaml
csv:
  output:
    path: /app/output/result.csv
  export:
    s3-upload: true        # S3アップロード有効化
    local-backup: true     # ローカルバックアップ有効化
    enabled: true          # CSV出力全体の有効化

aws:
  endpoint: ${AWS_ENDPOINT:http://localstack:4566}  # LocalStack用
  s3:
    bucket: ${AWS_S3_BUCKET:csv-export-bucket}
  access-key-id: ${AWS_ACCESS_KEY_ID:dummy}
  secret-access-key: ${AWS_SECRET_ACCESS_KEY:dummy}
  default-region: ${AWS_DEFAULT_REGION:us-east-1}
```

### 6.2 環境変数（セキュリティ強化）
| 変数名 | 説明 | デフォルト値 | 必須 |
|--------|------|-------------|-----|
| `DB_URL` | データベース接続URL | jdbc:oracle:thin:@oracle-db:1521/XEPDB1 | △ |
| `DB_USERNAME` | データベースユーザー名 | csvuser | △ |
| `DB_PASSWORD` | データベースパスワード | - | ✅ |
| `SOAP_API_URL` | SOAP API エンドポイントURL | http://soap-stub:8080/ws | △ |
| `CSV_OUTPUT_PATH` | CSV出力ファイルパス | /app/output/result.csv | △ |
| `AWS_ENDPOINT` | AWSエンドポイント（LocalStack用） | http://localstack:4566 | △ |
| `AWS_S3_BUCKET` | S3バケット名 | csv-export-bucket | △ |
| `AWS_ACCESS_KEY_ID` | AWSアクセスキー | dummy | △ |
| `AWS_SECRET_ACCESS_KEY` | AWSシークレットキー | dummy | △ |
| `AWS_DEFAULT_REGION` | AWSリージョン | us-east-1 | △ |

**凡例**: ✅必須 △デフォルト値あり

## 7. 例外処理仕様

### 7.1 カスタム例外クラス
| 例外クラス | 説明 | 使用場面 |
|-----------|------|---------|
| `CsvProcessingException` | CSV処理関連エラー | ファイル書き込み、フォーマットエラー |
| `DataProcessingException` | データ処理関連エラー | 従業員データ処理、変換エラー |
| `S3UploadException` | S3アップロード関連エラー | クラウドストレージ操作失敗 |

### 7.2 システムエラーコード
| エラーコード | 説明 | 対応例外 | ログレベル |
|-------------|------|---------|----------|
| `DB_CONNECTION_ERROR` | データベース接続エラー | SQLException | ERROR |
| `SOAP_API_ERROR` | SOAP API呼び出しエラー | WebServiceIOException | ERROR |
| `CSV_PROCESSING_ERROR` | CSV処理エラー | CsvProcessingException | ERROR |
| `S3_UPLOAD_ERROR` | S3アップロードエラー | S3UploadException | ERROR |
| `DATA_PROCESSING_ERROR` | データ処理エラー | DataProcessingException | ERROR |
| `EMPLOYEE_NOT_FOUND` | 従業員データが見つからない | RuntimeException | WARN |
| `CIRCUIT_BREAKER_OPEN` | サーキットブレーカーオープン | 独自実装 | WARN |

### 7.3 ログ出力仕様
- **ログレベル**: ERROR, WARN, INFO, DEBUG
- **ログフォーマット**: `[TIMESTAMP] [LEVEL] [THREAD] [CLASS] - MESSAGE`
- **出力先**: 標準出力（Dockerログとして収集）
- **機密情報**: パスワード、API キー等はマスクして出力

## 8. パフォーマンス仕様

### 8.1 処理性能目標
- **処理速度**: 1,000件/分以上（エラーハンドリング含む）
- **メモリ使用量**: 最大1GB以下（JVM最適化後）
- **CSV出力時間**: 10,000件で30秒以内
- **S3アップロード時間**: 100MBで60秒以内

### 8.2 制限事項
- **最大処理件数**: 100,000件まで
- **SOAP API同時接続数**: 5接続まで（サーキットブレーカー制御）
- **CSV最大ファイルサイズ**: 500MB まで（マルチパートアップロード対応）
- **S3アップロード**: 単一ファイル5GB制限（AWS制限）

### 8.3 リトライ・タイムアウト設定
- **データベース接続**: 最大3回リトライ、指数バックオフ
- **SOAP API**: 最大3回リトライ、1秒初期遅延、倍率2.0
- **S3アップロード**: 最大3回リトライ、2秒初期遅延
- **サーキットブレーカー**: 5回失敗でオープン、30秒後リセット

## 9. セキュリティ仕様

### 9.1 認証・認可
- **データベース**: 環境変数による認証情報管理
- **AWS S3**: IAM認証（本番環境）、ダミー認証（開発環境）
- **ネットワーク**: Docker内部ネットワーク通信

### 9.2 データ保護
- **機密情報**: ログ出力時のマスキング
- **パスワード**: 平文保存禁止、環境変数必須
- **通信**: HTTPS推奨（本番環境）

### 9.3 セキュリティ制限
- **ファイルアクセス**: コンテナ内パス制限
- **ポート公開**: 必要最小限のポートのみ
- **環境分離**: 開発・テスト・本番環境の完全分離