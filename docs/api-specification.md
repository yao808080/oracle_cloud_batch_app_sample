# API仕様書 - CSV Batch Processor

## 概要

Helidon MP 4.0.11で実装されたCSVバッチプロセッサーのREST API仕様書です。本システムは従業員データのCSVエクスポート、Object Storage統合、およびシステムメトリクスの提供を行います。

## Base URL

```
http://localhost:8080
```

## 認証

現在の実装では認証は実装されていません。今後JWT認証の追加を予定しています。

---

## エンドポイント一覧

### CSV処理API

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/csv/export` | CSVエクスポートの実行 |
| GET | `/api/csv/status` | サービスステータスの確認 |
| GET | `/api/csv/files` | CSVファイル一覧の取得 |
| GET | `/api/csv/download/{fileName}` | CSVファイルのダウンロード |
| DELETE | `/api/csv/files/{fileName}` | CSVファイルの削除 |

### メトリクスAPI

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/metrics/application` | アプリケーションメトリクス |
| GET | `/api/metrics/database` | データベース接続プールメトリクス |
| GET | `/api/metrics/summary` | 全メトリクスのサマリー |

### ヘルスチェックAPI

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/health` | 総合ヘルスチェック |
| GET | `/health/live` | Liveness Probe |
| GET | `/health/ready` | Readiness Probe |
| GET | `/health/started` | Startup Probe |

### MicroProfileメトリクス

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/metrics` | Prometheus形式のメトリクス |
| GET | `/metrics/application` | アプリケーション固有のメトリクス |
| GET | `/metrics/base` | JVMベースメトリクス |
| GET | `/metrics/vendor` | ベンダー固有のメトリクス |

---

## API詳細仕様

### 1. CSV エクスポート

#### POST `/api/csv/export`

従業員データをCSV形式でエクスポートし、Object Storageに保存します。

**リクエスト**
```http
POST /api/csv/export
Content-Type: application/json
```

**レスポンス - 成功 (200 OK)**
```json
{
  "status": "success",
  "message": "CSV export completed successfully",
  "timestamp": "2025-08-07T10:30:00",
  "outputLocation": "exports/employee_data_20250807_103000.csv",
  "downloadUrl": "http://localhost:4566/test-bucket/exports/employee_data_20250807_103000.csv"
}
```

**レスポンス - エラー (500 Internal Server Error)**
```json
{
  "status": "error",
  "message": "CSV export failed: Connection timeout",
  "timestamp": "2025-08-07T10:30:00"
}
```

**メトリクス**
- `csv.export.api.calls` - API呼び出し回数
- `csv.export.api.duration` - 処理時間

---

### 2. サービスステータス

#### GET `/api/csv/status`

CSVエクスポートサービスの稼働状態を確認します。

**リクエスト**
```http
GET /api/csv/status
```

**レスポンス (200 OK)**
```json
{
  "service": "CSV Export Service",
  "status": "running",
  "timestamp": "2025-08-07T10:30:00"
}
```

---

### 3. CSVファイル一覧

#### GET `/api/csv/files`

Object Storage内のCSVファイル一覧を取得します。

**リクエスト**
```http
GET /api/csv/files
```

**レスポンス - 成功 (200 OK)**
```json
{
  "status": "success",
  "count": 3,
  "files": [
    "exports/employee_data_20250807_103000.csv",
    "exports/employee_data_20250806_150000.csv",
    "exports/employee_data_20250805_120000.csv"
  ],
  "timestamp": "2025-08-07T10:30:00"
}
```

**レスポンス - エラー (500 Internal Server Error)**
```json
{
  "status": "error",
  "message": "Failed to list CSV files: Object Storage unavailable",
  "timestamp": "2025-08-07T10:30:00"
}
```

---

### 4. CSVファイルダウンロード

#### GET `/api/csv/download/{fileName}`

指定されたCSVファイルをダウンロードします。

**リクエスト**
```http
GET /api/csv/download/employee_data_20250807_103000.csv
```

**パスパラメータ**
- `fileName` (string, required) - ダウンロードするファイル名

**レスポンス - 成功 (200 OK)**
```csv
Content-Type: text/csv
Content-Disposition: attachment; filename="employee_data_20250807_103000.csv"

id,name,department,email,hire_date
1,John Doe,Engineering,john.doe@example.com,2023-01-15
2,Jane Smith,Marketing,jane.smith@example.com,2023-02-20
```

**レスポンス - エラー (404 Not Found)**
```text
File not found or download failed
```

**レスポンス - バリデーションエラー (400 Bad Request)**
```text
File must be a CSV file
```

---

### 5. CSVファイル削除

#### DELETE `/api/csv/files/{fileName}`

指定されたCSVファイルを削除します。

**リクエスト**
```http
DELETE /api/csv/files/employee_data_20250807_103000.csv
```

**パスパラメータ**
- `fileName` (string, required) - 削除するファイル名

**レスポンス - 成功 (200 OK)**
```json
{
  "status": "success",
  "message": "File deleted successfully: employee_data_20250807_103000.csv",
  "timestamp": "2025-08-07T10:30:00"
}
```

**レスポンス - エラー (500 Internal Server Error)**
```json
{
  "status": "error",
  "message": "Failed to delete file: File not found",
  "timestamp": "2025-08-07T10:30:00"
}
```

---

### 6. アプリケーションメトリクス

#### GET `/api/metrics/application`

アプリケーションの実行メトリクスを取得します。

**リクエスト**
```http
GET /api/metrics/application
```

**レスポンス (200 OK)**
```json
{
  "csv_exports_total": 42,
  "csv_exports_success": 40,
  "csv_exports_failed": 2,
  "average_processing_time_ms": 1250,
  "last_export_timestamp": "2025-08-07T10:15:00",
  "timestamp": "2025-08-07T10:30:00"
}
```

---

### 7. データベースメトリクス

#### GET `/api/metrics/database`

データベース接続プールの状態を取得します。

**リクエスト**
```http
GET /api/metrics/database
```

**レスポンス (200 OK)**
```json
{
  "active_connections": 3,
  "available_connections": 7,
  "total_connections": 10,
  "timestamp": "2025-08-07T10:30:00"
}
```

---

### 8. メトリクスサマリー

#### GET `/api/metrics/summary`

全メトリクスの統合サマリーを取得します。

**リクエスト**
```http
GET /api/metrics/summary
```

**レスポンス (200 OK)**
```json
{
  "application": {
    "csv_exports_total": 42,
    "csv_exports_success": 40,
    "csv_exports_failed": 2,
    "average_processing_time_ms": 1250,
    "last_export_timestamp": "2025-08-07T10:15:00"
  },
  "database": {
    "active_connections": 3,
    "available_connections": 7,
    "total_connections": 10
  },
  "timestamp": "2025-08-07T10:30:00"
}
```

---

## ヘルスチェック

### GET `/health`

アプリケーション全体のヘルス状態を確認します。

**レスポンス例 (200 OK)**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "database",
      "status": "UP",
      "data": {
        "url": "jdbc:postgresql://localhost:5432/testdb"
      }
    },
    {
      "name": "objectStorage",
      "status": "UP",
      "data": {
        "endpoint": "http://localhost:4566",
        "bucket": "test-bucket"
      }
    }
  ]
}
```

### GET `/health/live`

アプリケーションのLiveness状態を確認します（Kubernetes Liveness Probe用）。

**レスポンス例 (200 OK)**
```json
{
  "status": "UP",
  "checks": []
}
```

### GET `/health/ready`

アプリケーションのReadiness状態を確認します（Kubernetes Readiness Probe用）。

**レスポンス例 (200 OK)**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "database",
      "status": "UP"
    },
    {
      "name": "objectStorage",
      "status": "UP"
    }
  ]
}
```

---

## SOAP API統合

### SOAP エンドポイント情報

従業員詳細情報を取得するためのSOAP Web Service連携。

- **URL**: `http://soap-stub:8081/ws`
- **WSDL**: `http://soap-stub:8081/ws/employees.wsdl`
- **ネームスペース**: `http://example.com/employees`
- **タイムアウト設定**: 接続30秒、読み取り60秒
- **リトライ設定**: 最大3回、指数バックオフ（初期遅延1秒、倍率2.0）
- **サーキットブレーカー**: 5回失敗でオープン、10秒間オープン、30秒後リセット

### getEmployeeDetails操作

従業員IDに基づいて詳細情報を取得します。

**リクエスト例**
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

**レスポンス例**
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

---

## エラーハンドリング

### エラーレスポンス形式

すべてのエラーレスポンスは以下の形式で返されます：

```json
{
  "status": "error",
  "message": "エラーの詳細メッセージ",
  "timestamp": "2025-08-07T10:30:00",
  "error_code": "ERR_001"  // オプション
}
```

### HTTPステータスコード

| コード | 説明 | 使用例 |
|--------|------|--------|
| 200 | 成功 | 正常な処理完了 |
| 400 | Bad Request | 不正なパラメータ |
| 404 | Not Found | リソースが見つからない |
| 500 | Internal Server Error | サーバー内部エラー |
| 503 | Service Unavailable | サービス利用不可 |

### カスタム例外クラス

- `CsvProcessingException` - CSV処理関連のエラー
- `DataProcessingException` - データ処理エラー
- `ObjectStorageException` - Object Storage操作エラー

---

## 制限事項と注意点

### 現在の制限事項

1. **認証・認可**: 現在未実装（JWT認証を実装予定）
2. **レート制限**: 未実装
3. **CORS設定**: ローカル開発用の設定のみ
4. **ファイルサイズ制限**: CSVファイルは最大100MBまで
5. **同時実行制限**: CSV エクスポートは同時に1つまで

### パフォーマンス考慮事項

- CSVエクスポート処理は大量データの場合、数秒〜数分かかる可能性があります
- データベース接続プールサイズは10に設定されています
- Object Storageのアップロード/ダウンロードはネットワーク帯域に依存します

### セキュリティ考慮事項

- 本番環境ではHTTPS通信を必須とすること
- JWT認証の実装を推奨
- SQLインジェクション対策としてPreparedStatementを使用
- ファイルアップロード時のウイルススキャンを推奨

---

## 今後の拡張予定

### Phase 1 (短期)
- JWT認証の実装
- ページネーション対応
- CSVインポート機能

### Phase 2 (中期)
- WebSocket対応（リアルタイム通知）
- バッチジョブのスケジューリングAPI
- 詳細なエラーコード体系

### Phase 3 (長期)
- GraphQL API対応
- gRPC対応
- API Gateway統合

---

## 参考情報

### 関連ドキュメント
- [Helidon MP Documentation](https://helidon.io/docs/v4/)
- [MicroProfile OpenAPI Specification](https://microprofile.io/specifications/openapi/)
- [JAX-RS 3.1 Specification](https://jakarta.ee/specifications/restful-ws/3.1/)

### 開発環境でのテスト

```bash
# サービスステータス確認
curl http://localhost:8080/api/csv/status

# CSVエクスポート実行
curl -X POST http://localhost:8080/api/csv/export \
  -H "Content-Type: application/json"

# CSVファイル一覧取得
curl http://localhost:8080/api/csv/files

# ヘルスチェック
curl http://localhost:8080/health

# メトリクス取得
curl http://localhost:8080/metrics
```

---

**最終更新**: 2025-08-07  
**バージョン**: 1.0.0  
**ステータス**: 開発環境で動作確認済み