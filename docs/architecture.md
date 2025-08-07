# CSV バッチプロセッサー - システム設計書（OCI版）

## 1. システム概要

### 1.1 システム名
CSV バッチプロセッサー（csv-batch-processor） - OCI対応版

### 1.2 システム目的
Oracle Database から従業員データを取得し、SOAP API から追加の従業員詳細情報を取得して、両方のデータを結合したCSVファイルを出力するバッチ処理システム。Oracle Cloud Infrastructure (OCI) のマネージドサービスを活用した、高可用性・低運用負荷のクラウドネイティブアプリケーション。

### 1.3 主要機能
- Oracle Database（Autonomous Database推奨）からの従業員基本情報の取得
- SOAP API からの従業員詳細情報（level、bonus、status）の取得
- データの結合とCSVファイルへの出力
- OCI Object Storageへの自動アップロード機能
- OCI Local Testing Frameworkでの開発・テストサポート
- Docker環境での実行サポート
- Helidonフレームワークによるクラウドネイティブ実装（オプション）

## 2. システム構成

### 2.1 アーキテクチャ概要

#### 2.1.1 OCIネイティブアーキテクチャ（推奨）
```
┌─────────────────────────────────────────────────────────────────┐
│                        OCI Cloud                                │
│                                                                 │
│  ┌──────────────────┐    ┌───────────────────┐                │
│  │ Container        │    │   Autonomous      │                │
│  │ Instances/OKE    │    │   Database        │                │
│  │                  │    │                   │                │
│  │ ┌──────────────┐ │    │ ┌───────────────┐ │                │
│  │ │csv-batch-    │ │◄───┤ │ Oracle DB     │ │                │
│  │ │processor     │ │    │ │ (Auto-managed)│ │                │
│  │ └──────────────┘ │    │ └───────────────┘ │                │
│  │ ┌──────────────┐ │    │                   │                │
│  │ │soap-stub     │ │    │                   │                │
│  │ └──────────────┘ │    │                   │                │
│  └──────────────────┘    └───────────────────┘                │
│           │                                                    │
│           ▼                                                    │
│  ┌──────────────────┐    ┌───────────────────┐                │
│  │   Object Storage │    │   OCI Monitoring  │                │
│  │   (CSV Output)   │    │   & Logging       │                │
│  └──────────────────┘    └───────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

#### 2.1.2 ハイブリッドアーキテクチャ（移行期）
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Oracle DB     │    │  Helidon/Spring │    │   SOAP Stub     │
│   (On-Premise)  │◄───┤  CSV Processor  ├───►│   (External)    │
│                 │    │  (Container)    │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────┐      ┌─────────────────┐
                       │ OCI Object  │      │  OCI Local      │
                       │   Storage   │◄─────│  Testing        │
                       │result.csv   │      │   Framework     │
                       └─────────────┘      └─────────────────┘
```

### 2.2 エンタープライズ技術スタック

#### 2.2.1 フレームワーク選択肢

| オプション | フレームワーク | 特徴 | 推奨用途 | セキュリティレベル |
|-----------|--------------|------|---------|------------------|
| **推奨** | Helidon 4.0 | OCIネイティブ、軽量、高速起動、MicroProfile準拠 | 新規開発、マイクロサービス | Enterprise |
| 代替案 | Spring Boot 3.2.0 | 既存コード活用、豊富なエコシステム | 移行プロジェクト | Enterprise |

#### 2.2.2 OCIネイティブサービス統合

| サービス | 用途 | 高可用性対応 | セキュリティ機能 |
|---------|------|-------------|---------------|
| **OKE (Oracle Kubernetes Engine)** | コンテナオーケストレーション | Multi-AZ, Node Pool Autoscaling | Pod Security Standards, Network Policies |
| **Autonomous Database** | データストレージ | Cross-AZ自動バックアップ | mTLS, TDE, Database Vault |
| **Object Storage** | ファイル保存 | Regional Replication | Customer-Managed Keys, Private Endpoints |
| **OCI Vault** | シークレット管理 | Multi-AZ HSM | Hardware Security Module, Auto-rotation |
| **Instance Principal** | 認証 | Region-level Failover | Passwordless, Dynamic Principal |
| **Service Gateway** | プライベート接続 | Redundant Gateways | Zero Internet Exposure |
| **Security Zones** | セキュリティ強制 | Multi-AZ Policy Enforcement | CIS Benchmarks, Compliance |
| **Cloud Guard** | 脅威検知 | Real-time Monitoring | ML-based Anomaly Detection |

#### 2.2.3 エンタープライズ技術スタック

**コアテクノロジー**:
- **開発言語**: Java 21 (LTS)
- **ビルドツール**: Maven 3.8+ / Gradle 8.0+
- **コンテナ化**: Docker 24.0+, Kubernetes 1.28+

**データ層**:
- **Primary DB**: Oracle Autonomous Database (Always FreeからExadataまでスケール対応)
- **接続プール**: Universal Connection Pool (UCP) 21c
- **ファイルストレージ**: OCI Object Storage (11 9s durability)

**セキュリティ**:
- **認証**: Instance Principal + OCI IAM
- **シークレット管理**: OCI Vault (FIPS 140-2 Level 3 HSM)
- **ネットワーク**: VCN + Security Lists + NACLs
- **暗号化**: Customer-Managed Keys (CMK)

**監視・運用**:
- **APM**: OCI Application Performance Monitoring
- **ログ**: OCI Logging + Logging Analytics
- **メトリクス**: OCI Monitoring + Custom Metrics
- **アラート**: OCI Notifications + PagerDuty/Slack連携

**CI/CD・自動化**:
- **Infrastructure as Code**: Terraform / OpenTofu
- **CI/CD**: OCI DevOps / GitHub Actions
- **コンテナレジストリ**: OCI Container Registry (OCIR)
- **セキュリティスキャン**: OCI Vulnerability Scanning Service

### 2.3 コンポーネント構成

#### 2.3.1 メインアプリケーション (csv-batch-processor)
- **ポート**: 8080
- **機能**: データ取得、変換、CSV出力の主処理
- **依存関係**: Oracle DB、SOAP Stub、OCI Object Storage

#### 2.3.2 Oracle Database
- **タイプ**: Autonomous Database (推奨) または Database Service
- **接続**: mTLS (Autonomous) または通常のJDBC
- **ユーザー**: csvuser/設定による

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
CSV File → Object Storage (result.csv)
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

### 4.1 パッケージ構成（Helidon版）
```
com.example.csvbatch
├── Main.java                            # Helidonメインクラス
├── client/
│   └── SoapClient.java                  # SOAP API クライアント
├── config/
│   ├── OciConfig.java                   # OCI設定
│   ├── DataInitializer.java            # データ初期化
│   └── WebServiceConfig.java           # Web Service 設定
├── dto/
│   └── EmployeeCsvData.java            # CSV出力用DTO
├── entity/
│   └── Employee.java                   # JPA エンティティ
├── exception/
│   ├── CsvProcessingException.java      # CSV処理例外
│   ├── ObjectStorageException.java      # Object Storage例外
│   └── DataProcessingException.java    # データ処理例外
├── repository/
│   └── EmployeeRepository.java         # データアクセス層
├── service/
│   ├── CsvExportService.java           # CSV出力サービス
│   └── ObjectStorageService.java       # Object Storageクライアント
└── health/
    └── ReadinessCheck.java             # ヘルスチェック
```

### 4.2 主要クラス設計

#### 4.2.1 CsvExportService
- **責務**: CSV出力の主処理とObject Storageアップロード
- **メソッド**:
  - `exportEmployeesToCsv()`: メイン処理メソッド
  - `writeCsvFile()`: CSV書き込み処理
  - `uploadToObjectStorage()`: Object Storageアップロード処理
- **エラーハンドリング**: Resilience4j によるリトライ機能、フォールバック処理

#### 4.2.2 ObjectStorageService
- **責務**: OCI Object Storageとの通信処理とバケット自動管理
- **メソッド**:
  - `@PostConstruct initializeBucket()`: バケット自動作成・確認
  - `uploadObject(String objectName, InputStream content)`: オブジェクトアップロード
  - `downloadObject(String objectName)`: オブジェクトダウンロード
  - `deleteObject(String objectName)`: オブジェクト削除
  - `listObjects(String prefix)`: オブジェクト一覧取得
  - `bucketExists()`: バケット存在確認
  - `createBucket()`: バケット作成
- **エラーハンドリング**: 接続エラー、認証エラー、容量制限エラー対応
- **自動初期化機能**: アプリケーション起動時に必要なバケットを自動作成

#### 4.2.3 SoapClient
- **責務**: SOAP API との通信
- **メソッド**:
  - `getEmployeeDetails(Long employeeId)`: 従業員詳細取得
- **エラーハンドリング**: タイムアウト、接続エラー、レスポンス検証
- **リトライ機能**: Resilience4j によるリトライとサーキットブレーカー

#### 4.2.4 Employee Entity
- **責務**: データベーステーブルとのマッピング
- **アノテーション**: `@Entity`, `@Table`, `@Id`, `@Column`

#### 4.2.5 EmployeeCsvData DTO
- **責務**: CSV出力用のデータ転送オブジェクト
- **特徴**: Immutableデータクラス

#### 4.2.6 例外処理クラス群
- **CsvProcessingException**: CSV処理関連エラー
- **ObjectStorageException**: Object Storage関連エラー
- **DataProcessingException**: データ処理関連エラー

## 5. 設定管理

### 5.1 application.yaml (Helidon版)
```yaml
server:
  port: 8080
  host: 0.0.0.0

# データベース設定
db:
  connection:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    poolName: "CsvBatchPool"
    
# Autonomous Database使用時
autonomous:
  wallet:
    path: ${TNS_ADMIN:/opt/oracle/wallet}
    password: ${WALLET_PASSWORD}
    
# SOAP API設定
soap:
  endpoint: ${SOAP_API_URL:http://soap-stub:8080/ws}
  timeout: 30000
  
# CSV出力設定
csv:
  output:
    path: /app/output/result.csv
  export:
    storage-upload: true
    local-backup: true
    
# OCI設定
oci:
  config:
    profile: ${OCI_PROFILE:DEFAULT}
    region: ${OCI_REGION:us-ashburn-1}
  objectstorage:
    namespace: ${OCI_NAMESPACE}
    bucket: ${OCI_BUCKET:csv-export-bucket}
    prefix: exports/
    
# Resilience4j設定
resilience4j:
  retry:
    instances:
      soapService:
        max-attempts: 3
        wait-duration: 1000
        retry-exceptions:
          - java.net.ConnectException
          - java.io.IOException
  circuitbreaker:
    instances:
      soapService:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10000
        sliding-window-size: 10
```

### 5.2 コンテナ環境設定
```yaml
# Container Instances / OKE 環境変数
environment:
  - name: DB_URL
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: url
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials  
        key: password
  - name: OCI_REGION
    value: us-ashburn-1
  - name: OCI_NAMESPACE
    valueFrom:
      configMapKeyRef:
        name: oci-config
        key: namespace
```

## 6. OCI統合設計

### 6.1 認証メカニズム

#### 6.1.1 インスタンスプリンシパル（推奨）
```java
// Container Instances/Compute Instance での自動認証
AuthenticationDetailsProvider provider = 
    InstancePrincipalsAuthenticationDetailsProvider.builder().build();
```

#### 6.1.2 リソースプリンシパル（OCI Functions）
```java
// Functions での自動認証
AuthenticationDetailsProvider provider = 
    ResourcePrincipalAuthenticationDetailsProvider.builder().build();
```

### 6.2 Object Storage 設計

#### 6.2.1 バケット構造
```
csv-export-bucket/
├── exports/
│   ├── 2025/
│   │   ├── 01/
│   │   │   ├── 15/
│   │   │   │   ├── result-20250115-100000.csv
│   │   │   │   └── result-20250115-140000.csv
```

#### 6.2.2 ライフサイクルポリシー
- 30日後: Infrequent Access Storage へ移行
- 90日後: Archive Storage へ移行
- 365日後: 自動削除

### 6.3 Autonomous Database 接続

#### 6.3.1 ウォレット不要接続（mTLS）
```java
// TLS接続文字列使用
String url = "jdbc:oracle:thin:@(description=" +
    "(retry_count=20)(retry_delay=3)" +
    "(address=(protocol=tcps)(port=1521)(host=xxx.oraclecloud.com))" +
    "(connect_data=(service_name=xxx_high.adb.oraclecloud.com))" +
    "(security=(ssl_server_dn_match=yes)))";
```

#### 6.3.2 接続プール（UCP）
```java
PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
pds.setURL(url);
pds.setUser(username);
pds.setPassword(password);
pds.setInitialPoolSize(2);
pds.setMinPoolSize(2);
pds.setMaxPoolSize(10);
```

## 7. セキュリティ設計

### 7.1 OCI Vault 統合
```java
// シークレット取得
VaultsClient vaultsClient = VaultsClient.builder()
    .build(provider);
    
GetSecretBundleResponse response = vaultsClient.getSecretBundle(
    GetSecretBundleRequest.builder()
        .secretId(secretOcid)
        .build());
        
String secretValue = new String(
    Base64.getDecoder().decode(
        response.getSecretBundle().getSecretBundleContent().getContent()));
```

### 7.2 ネットワークセキュリティ
- VCN内のプライベートサブネット配置
- セキュリティリストによるアクセス制御
- サービスゲートウェイ経由のOCIサービスアクセス

### 7.3 データ暗号化
- Object Storage: デフォルトで暗号化
- Autonomous Database: Transparent Data Encryption (TDE)
- 転送時: TLS 1.2以上

## 8. 監視・運用設計

### 8.1 OCI Monitoring
```java
// カスタムメトリクス送信
MonitoringClient monitoringClient = MonitoringClient.builder()
    .build(provider);
    
PostMetricDataRequest request = PostMetricDataRequest.builder()
    .postMetricDataDetails(
        PostMetricDataDetails.builder()
            .metricData(Arrays.asList(
                MetricDataDetails.builder()
                    .namespace("csv_batch_processor")
                    .name("records_processed")
                    .datapoints(Arrays.asList(
                        Datapoint.builder()
                            .value(1000.0)
                            .timestamp(new Date())
                            .build()))
                    .build()))
            .build())
    .build();
```

### 8.2 ヘルスチェック
```java
@Path("/health")
public class HealthResource {
    @GET
    @Path("/ready")
    public Response readiness() {
        // DB接続チェック
        // Object Storage接続チェック
        return Response.ok().build();
    }
    
    @GET
    @Path("/live")
    public Response liveness() {
        return Response.ok().build();
    }
}
```

### 8.3 ログ設計
- OCI Logging への統合
- 構造化ログ（JSON形式）
- ログレベル: ERROR, WARN, INFO
- 機密情報のマスキング

## 9. スケーリング設計

### 9.1 水平スケーリング
- Container Instances: 自動スケーリングポリシー
- OKE: Horizontal Pod Autoscaler (HPA)
- 処理の冪等性保証

### 9.2 バッチ分割処理
```java
// 大量データの分割処理
int batchSize = 1000;
List<Employee> allEmployees = employeeRepository.findAll();
Lists.partition(allEmployees, batchSize)
    .parallelStream()
    .forEach(batch -> processBatch(batch));
```

## 10. 移行戦略

### 10.1 段階的移行
1. **Phase 1**: Spring Boot アプリをそのままOCIで実行
2. **Phase 2**: OCI サービスへの段階的移行（Object Storage、Vault）
3. **Phase 3**: Autonomous Database への移行
4. **Phase 4**: Helidon への移行（オプション）

### 10.2 互換性維持
- S3互換API によるコード互換性
- 標準JDBC による DB接続
- Docker コンテナによる可搬性

---

**最終更新**: 2025-08-06 - OCI PoC環境用設計書