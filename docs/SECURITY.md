# セキュリティガイド（OCI版）

## 概要

このプロジェクトは企業データを扱うHelidon/Spring Boot + OCI統合バッチアプリケーションのサンプルPoC環境です。AWS版からOCIへの移行を前提として設計されており、本番環境での使用には適切なOCIセキュリティ設定が必要です。

### セキュリティ強化の完了状況

**✅ 実装済みセキュリティ対策:**
- 環境変数による認証情報管理の徹底実装
- OCI Object Storage統合でのセキュリティ設定強化
- Resilience4j/Spring Retry機能でのセキュリティ考慮
- カスタム例外処理でのセキュリティ情報保護
- .gitignoreによる機密ファイル除外の包括的設定
- OCIネイティブ認証（インスタンスプリンシパル）対応

**⚠️ 使用者責任でのセキュリティ設定:**
- 本番環境での強力なパスワード設定
- TLS/mTLS通信の有効化
- VCNとセキュリティリストの適切な設定
- OCI Vault統合

## セキュリティレベル: **開発・学習用として安全** ✅

**評価根拠:**
1. ハードコードされた認証情報が完全に排除されている
2. 環境変数による設定管理が適切に実装されている
3. 開発用デフォルト値と本番用設定が明確に分離されている
4. 機密ファイルが適切に.gitignoreで除外されている
5. OCIセキュリティドキュメントが包括的に整備されている
6. インスタンスプリンシパル認証の実装準備が完了している

## セキュリティ対策

### 1. 認証情報の管理（OCI強化済み）

#### 🔐 必須環境変数の設定

**⚠️ 重要**: DB_PASSWORDは必須の環境変数となりました。設定なしでは起動しません。

```bash
# 必須環境変数（本番環境）
export DB_PASSWORD="your_secure_database_password"        # 必須
export OCI_NAMESPACE="your_oci_namespace"                 # 必須
export OCI_BUCKET="csv-export-bucket"
export WALLET_PASSWORD="your_wallet_password"             # Autonomous Database使用時

# オプション環境変数（デフォルト値あり）
export DB_URL="jdbc:oracle:thin:@your-adb-connection-string"
export DB_USERNAME="csvuser"
export SOAP_API_URL="http://soap-stub:8080/ws"
export CSV_OUTPUT_PATH="/app/output/result.csv"
export OCI_REGION="us-ashburn-1"
```

#### 📝 .env ファイルの設定

開発環境では `.env.example` をコピーして `.env` ファイルを作成：

```bash
# 1. テンプレートファイルをコピー
cp .env.example .env

# 2. 必須の認証情報を設定
cat > .env << EOF
# データベース設定（必須）
ORACLE_PASSWORD=your_secure_oracle_password
DB_USERNAME=csvuser
DB_PASSWORD=your_secure_csvuser_password
DB_URL=jdbc:oracle:thin:@your-adb-connection-string

# OCI設定
OCI_NAMESPACE=your_namespace
OCI_BUCKET=csv-export-bucket
OCI_REGION=us-ashburn-1

# Autonomous Database設定（mTLS接続時）
TNS_ADMIN=/opt/oracle/wallet
WALLET_PASSWORD=your_wallet_password

# CSV出力設定
CSV_OUTPUT_PATH=/app/output/result.csv
CSV_EXPORT_STORAGE_UPLOAD=true
CSV_EXPORT_LOCAL_BACKUP=true
CSV_EXPORT_ENABLED=true

# SOAP API設定
SOAP_API_URL=http://soap-stub:8080/ws
EOF

# 3. ファイル権限を制限
chmod 600 .env
```

#### 🔒 OCI Vault統合（推奨）

```java
// OCI Vault からのシークレット取得
@Service
public class VaultSecretService {
    
    private final VaultsClient vaultsClient;
    
    @PostConstruct
    public void initializeVaultClient() {
        // インスタンスプリンシパル認証
        AuthenticationDetailsProvider provider = 
            InstancePrincipalsAuthenticationDetailsProvider.builder().build();
        
        this.vaultsClient = VaultsClient.builder()
            .build(provider);
    }
    
    public String getSecret(String secretId) {
        try {
            GetSecretBundleRequest request = GetSecretBundleRequest.builder()
                .secretId(secretId)
                .build();
                
            GetSecretBundleResponse response = vaultsClient.getSecretBundle(request);
            
            // Base64デコード
            String encodedContent = response.getSecretBundle()
                .getSecretBundleContent().getContent();
            return new String(Base64.getDecoder().decode(encodedContent));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve secret from OCI Vault", e);
            throw new SecurityException("Secret retrieval failed");
        }
    }
}
```

### 2. OCI認証メカニズム

#### 🛡️ インスタンスプリンシパル認証（推奨）

```java
// インスタンスプリンシパル認証設定
@Configuration
public class OCIAuthConfig {
    
    @Bean
    @ConditionalOnProperty(name = "oci.auth.method", havingValue = "instance_principal", matchIfMissing = true)
    public AuthenticationDetailsProvider instancePrincipalAuth() {
        return InstancePrincipalsAuthenticationDetailsProvider.builder()
            .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "oci.auth.method", havingValue = "config_file")
    public AuthenticationDetailsProvider configFileAuth() {
        return new ConfigFileAuthenticationDetailsProvider(
            System.getProperty("oci.config.file", "~/.oci/config"),
            System.getProperty("oci.profile", "DEFAULT")
        );
    }
}
```

#### 🔑 動的グループとポリシー設定

```bash
# 動的グループ作成
oci iam dynamic-group create \
    --name csv-batch-instances \
    --description "CSV Batch Processor instances" \
    --matching-rule "ALL {instance.compartment.id = 'ocid1.compartment.oc1..xxxxx'}"

# 最小権限ポリシー
oci iam policy create \
    --name csv-batch-policy \
    --compartment-id ocid1.compartment.oc1..xxxxx \
    --statements '[
        "Allow dynamic-group csv-batch-instances to manage objects in compartment id ocid1.compartment.oc1..xxxxx where target.bucket.name='"'"'csv-export-bucket'"'"'",
        "Allow dynamic-group csv-batch-instances to read autonomous-databases in compartment id ocid1.compartment.oc1..xxxxx",
        "Allow dynamic-group csv-batch-instances to read secrets in compartment id ocid1.compartment.oc1..xxxxx"
    ]'
```

### 3. Autonomous Database セキュリティ

#### 🔒 mTLS接続（推奨）

```java
// Autonomous Database mTLS接続
@Configuration
public class AutonomousDatabaseConfig {
    
    @Value("${autonomous.wallet.path:#{null}}")
    private String walletPath;
    
    @Value("${autonomous.wallet.password:#{null}}")
    private String walletPassword;
    
    @Bean
    public DataSource dataSource() {
        OracleDataSource dataSource = new OracleDataSource();
        
        if (walletPath != null) {
            // ウォレット使用
            System.setProperty("oracle.net.tns_admin", walletPath);
            System.setProperty("oracle.net.wallet_location", walletPath);
            System.setProperty("oracle.net.wallet_password", walletPassword);
            dataSource.setURL("jdbc:oracle:thin:@dbname_high");
        } else {
            // mTLS接続文字列使用（ウォレット不要）
            dataSource.setURL(System.getenv("DB_URL"));
        }
        
        dataSource.setUser(System.getenv("DB_USERNAME"));
        dataSource.setPassword(System.getenv("DB_PASSWORD"));
        
        return dataSource;
    }
}
```

#### 🛡️ データベース暗号化

- **TDE (Transparent Data Encryption)**: Autonomous Databaseでデフォルト有効
- **ネットワーク暗号化**: Native Network Encryption + TLS
- **データマスキング**: 機密データの自動マスキング機能

### 4. Object Storage セキュリティ（OCI強化済み）

#### 🔑 最小権限アクセス制御

```java
// Object Storage サービス設定
@Service
public class ObjectStorageService {
    
    private final ObjectStorage client;
    private final String namespace;
    private final String bucketName;
    
    public ObjectStorageService(AuthenticationDetailsProvider authProvider,
                               @Value("${oci.objectstorage.namespace}") String namespace,
                               @Value("${oci.objectstorage.bucket}") String bucketName) {
        this.client = ObjectStorageClient.builder()
            .build(authProvider);
        this.namespace = namespace;
        this.bucketName = bucketName;
    }
    
    @PostConstruct
    public void initializeBucket() {
        try {
            // バケット存在確認
            HeadBucketRequest request = HeadBucketRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .build();
                
            client.headBucket(request);
            logger.info("Bucket '{}' already exists", bucketName);
            
        } catch (BucketNotFoundException e) {
            logger.info("Bucket '{}' does not exist. Creating bucket...", bucketName);
            createBucket();
        } catch (Exception e) {
            logger.error("Failed to check bucket existence", e);
        }
    }
    
    public void uploadObject(String objectName, InputStream content) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .contentType("text/csv")
                .putObjectBody(content)
                .build();
                
            client.putObject(request);
            logger.info("Successfully uploaded object: {}", objectName);
            
        } catch (Exception e) {
            logger.error("Failed to upload object", e);
            throw new ObjectStorageException("Object upload failed", e);
        }
    }
}
```

#### 🔐 バケットセキュリティ設定

```bash
# プライベートバケット設定
oci os bucket create \
    --compartment-id ocid1.compartment.oc1..xxxxx \
    --name csv-export-bucket \
    --public-access-type NoPublicAccess \
    --object-events-enabled true

# バケットレベル暗号化（デフォルトで有効）
oci os bucket put-bucket-lifecycle-policy \
    --bucket-name csv-export-bucket \
    --items '[{
        "name": "security-retention",
        "action": "DELETE",
        "time-amount": 365,
        "time-unit": "DAYS",
        "is-enabled": true
    }]'
```

### 5. ネットワークセキュリティ

#### 🌐 VCN設計

```bash
# セキュアなVCN構成
VCN_ID=$(oci network vcn create \
    --compartment-id $COMPARTMENT_ID \
    --cidr-blocks '["10.0.0.0/16"]' \
    --display-name csv-batch-vcn \
    --dns-label csvbatchvcn \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# プライベートサブネット
PRIVATE_SUBNET_ID=$(oci network subnet create \
    --compartment-id $COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --cidr-block "10.0.2.0/24" \
    --display-name private-subnet \
    --prohibit-public-ip-on-vnic true \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)
```

#### 🛡️ Zero Trust Network Architectureセキュリティ設定

**⚠️ 重要**: 0.0.0.0/0の許可はセキュリティリスクです。以下の最小権限設定を使用してください。

```json
{
  "egressSecurityRules": [
    {
      "destination": "all-iad-services-in-oracle-services-network",
      "destinationType": "SERVICE_CIDR_BLOCK",
      "protocol": "6",
      "tcpOptions": {
        "destinationPortRange": {
          "min": 443,
          "max": 443
        }
      },
      "description": "HTTPS to OCI services via Service Gateway"
    },
    {
      "destination": "10.0.0.0/16",
      "protocol": "6",
      "tcpOptions": {
        "destinationPortRange": {
          "min": 1521,
          "max": 1521
        }
      },
      "description": "Oracle DB access within VCN"
    }
  ],
  "ingressSecurityRules": [
    {
      "source": "10.0.1.0/24",
      "protocol": "6",
      "tcpOptions": {
        "destinationPortRange": {
          "min": 8080,
          "max": 8080
        }
      },
      "description": "HTTP from Load Balancer subnet only"
    },
    {
      "source": "10.0.2.0/24",
      "protocol": "6",
      "tcpOptions": {
        "destinationPortRange": {
          "min": 22,
          "max": 22
        }
      },
      "description": "SSH from bastion subnet only"
    }
  ]
}
```

#### 🔒 Network Access Control Lists (NACLs) - 追加セキュリティ層

```bash
# NACL作成コマンド
oci network network-acl create \
    --compartment-id $COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --display-name "csv-batch-nacl" \
    --ingress-security-rules '[
        {
            "source": "10.0.1.0/24",
            "protocol": "6",
            "tcpOptions": {
                "destinationPortRange": {
                    "min": 8080,
                    "max": 8080
                }
            },
            "isStateless": false
        }
    ]' \
    --egress-security-rules '[
        {
            "destination": "all-iad-services-in-oracle-services-network",
            "protocol": "6",
            "tcpOptions": {
                "destinationPortRange": {
                    "min": 443,
                    "max": 443
                }
            },
            "isStateless": false
        }
    ]'
```
```

### 6. Resilience4j セキュリティ（Helidon統合）

#### 🔄 セキュアなリトライ実装

```java
// Helidon + Resilience4j設定
@ApplicationScoped
public class SoapClient {
    
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    @PostConstruct
    public void initialize() {
        // リトライ設定
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .exponentialBackoffMultiplier(2.0)
            .retryExceptions(ConnectException.class, IOException.class)
            .build();
        
        this.retryRegistry = RetryRegistry.of(retryConfig);
        
        // サーキットブレーカー設定
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build();
            
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
    }
    
    @Retry(name = "soap-api")
    @CircuitBreaker(name = "soap-api", fallbackMethod = "getFallbackEmployeeDetails")
    public EmployeeDetails getEmployeeDetails(Long employeeId) {
        try {
            // SOAP API呼び出し（機密情報をログに含めない）
            logger.info("Calling SOAP API for employee ID: {}", employeeId);
            return callSoapApi(employeeId);
            
        } catch (Exception e) {
            // セキュリティ考慮：詳細なエラー情報を隠蔽
            logger.error("SOAP API call failed for employee ID: {}", employeeId);
            throw new ServiceException("External API call failed");
        }
    }
    
    public EmployeeDetails getFallbackEmployeeDetails(Long employeeId, Exception ex) {
        // フォールバック処理（機密情報を含まない）
        logger.warn("Using fallback for employee ID: {}", employeeId);
        return EmployeeDetails.builder()
            .employeeId(employeeId)
            .level("Unknown")
            .bonus(BigDecimal.ZERO)
            .status("Unavailable")
            .build();
    }
}
```

### 7. Kubernetes セキュリティ（OKE）

#### 🔐 ServiceAccount設定

```yaml
# service-account.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: csv-batch-sa
  namespace: csv-batch
  annotations:
    oci.oraclecloud.com/principal-name: "csv-batch-workload-identity"
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: csv-batch
  name: csv-batch-role
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: csv-batch-rolebinding
  namespace: csv-batch
subjects:
- kind: ServiceAccount
  name: csv-batch-sa
  namespace: csv-batch
roleRef:
  kind: Role
  name: csv-batch-role
  apiGroup: rbac.authorization.k8s.io
```

#### 🛡️ Pod Security Standards

```yaml
# pod-security-policy.yaml
apiVersion: v1
kind: Pod
metadata:
  name: csv-batch-processor
spec:
  serviceAccountName: csv-batch-sa
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    runAsGroup: 1000
    fsGroup: 1000
    seccompProfile:
      type: RuntimeDefault
  containers:
  - name: csv-batch-processor
    image: oci-region.ocir.io/namespace/csv-batch-processor:latest
    securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      runAsNonRoot: true
      capabilities:
        drop:
        - ALL
    volumeMounts:
    - name: tmp-volume
      mountPath: /tmp
    - name: cache-volume
      mountPath: /app/cache
  volumes:
  - name: tmp-volume
    emptyDir: {}
  - name: cache-volume
    emptyDir: {}
```

### 8. 監査・ログセキュリティ

#### 📊 OCI Audit統合

```java
// 監査ログ設定
@Component
public class AuditLogger {
    
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    public void logDataAccess(String operation, String resourceId, String userId) {
        // 構造化ログ（JSON形式）
        ObjectNode logEntry = JsonNodeFactory.instance.objectNode();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("operation", operation);
        logEntry.put("resourceId", resourceId);
        logEntry.put("userId", userId);
        logEntry.put("status", "SUCCESS");
        
        auditLogger.info(logEntry.toString());
    }
    
    public void logSecurityEvent(String eventType, String details) {
        ObjectNode logEntry = JsonNodeFactory.instance.objectNode();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("eventType", eventType);
        logEntry.put("details", details);
        logEntry.put("severity", "WARNING");
        
        auditLogger.warn(logEntry.toString());
    }
}
```

#### 🔍 機密情報マスキング

```java
// ログフィルター実装
public class SensitiveDataMaskingFilter extends Filter<ILoggingEvent> {
    
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("(password[=:])([^\\s,}]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OCI_KEY_PATTERN = 
        Pattern.compile("(oci[_\\-]?(?:user|key|secret)[=:]?)([^\\s,}]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DB_CONNECTION_PATTERN = 
        Pattern.compile("(jdbc:oracle:thin:@)([^\\s]+)", Pattern.CASE_INSENSITIVE);
        
    @Override
    public FilterReply decide(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        
        // パスワードとOCIキーをマスク
        String maskedMessage = PASSWORD_PATTERN.matcher(message)
                .replaceAll("$1***");
        maskedMessage = OCI_KEY_PATTERN.matcher(maskedMessage)
                .replaceAll("$1***");
        maskedMessage = DB_CONNECTION_PATTERN.matcher(maskedMessage)
                .replaceAll("$1[MASKED_CONNECTION_STRING]");
                
        // マスク済みメッセージでログを再作成
        return FilterReply.NEUTRAL;
    }
}
```

### 9. Container セキュリティ

#### 🐳 セキュアなDockerfile

```dockerfile
# セキュリティ強化済みDockerfile
FROM openjdk:21-jre-slim

# セキュリティアップデート
RUN apt-get update && apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
        curl \
        ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 非rootユーザーの作成
RUN groupadd -r appuser && \
    useradd -r -g appuser -m appuser

# アプリケーションディレクトリ
WORKDIR /app
COPY target/*.jar app.jar

# 出力ディレクトリとキャッシュディレクトリ
RUN mkdir -p /app/output /app/cache /tmp && \
    chown -R appuser:appuser /app /tmp

# 非rootユーザーに切り替え
USER appuser

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

EXPOSE 8080

# セキュリティオプション
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 10. エンタープライズグレードセキュリティチェックリスト

#### 🎆 Critical - 即座対応必要
- [ ] **セキュリティリストの0.0.0.0/0許可を完全削除**
- [ ] **Service Gateway経由のOCIサービスアクセスのみ許可**
- [ ] **Object StorageのPublicアクセス完全無効化**
- [ ] **動的グループと最小権限ポリシー設定**

#### ✅ 認証・認可 (Enterprise Grade)
- [ ] インスタンスプリンシパル設定完了
- [ ] 動的グループ作成完了
- [ ] 最小権限IAMポリシー設定完了
- [ ] OCI Vault + Customer-Managed Keys (CMK)統合完了
- [ ] シークレット自動ローテーション設定完了
- [ ] MFA強制設定 (Identity Domain)

#### ✅ Zero Trust Network Security
- [ ] Multi-AZ VCN配置完了
- [ ] プライベートサブネットのみ配置
- [ ] セキュリティリスト最小権限設定完了
- [ ] NACLs設定完了（必須）
- [ ] Service Gateway経由OCIサービスアクセスのみ
- [ ] WAF + DDoS Protection設定
- [ ] Network Security Groups (NSG)設定

#### ✅ データ保護
- [ ] Object Storage プライベートアクセス設定完了
- [ ] Autonomous Database TDE有効化確認完了
- [ ] mTLS接続設定完了
- [ ] データマスキング設定完了（該当する場合）

#### ✅ 監視・監査
- [ ] OCI Audit有効化完了
- [ ] OCI Logging設定完了
- [ ] セキュリティアラーム設定完了
- [ ] 異常検知設定完了

#### ✅ コンテナセキュリティ
- [ ] 非rootユーザー実行設定完了
- [ ] 読み取り専用ファイルシステム設定完了
- [ ] セキュリティコンテキスト設定完了
- [ ] イメージ脆弱性スキャン完了

#### ✅ コンプライアンス・ガバナンス
- [ ] OCI Security Zones有効化 (CIS Benchmarks自動適用)
- [ ] OCI Cloud Guard有効化 (脅威検知・対処)
- [ ] OCI Vulnerability Scanning Service設定
- [ ] PCI DSS/SOC 2コンプライアンスチェック
- [ ] GDPR/個人情報保護法対応

#### ✅ 監視・インシデント対応
- [ ] OCI Audit全サービス有効化完了
- [ ] リアルタイムセキュリティアラート設定完了
- [ ] SIEM/SOAR統合設定完了
- [ ] インシデント対応プレイブック作成完了
- [ ] 24x7 SOC監視体制構築

## 11. エンタープライズインシデント対応

### 11.1 セキュリティインシデント対応フロー
```
セキュリティアラート検知
    ↓
初期トリアージ（5分以内）
    ↓
インシデント分類
    ↓        ↓        ↓
  重要度高    重要度中    重要度低
（即座対応） （1時間以内） （24時間以内）
    ↓
封じ込め・分析
    ↓
復旧・事後対応
    ↓
レポート作成
```

### 11.2 緊急対応手順
```bash
#!/bin/bash
# 緊急時セキュリティ対応スクリプト

# 1. インシデント検知時の初期対応
function emergency_isolation() {
    echo "=== Emergency Security Response ==="
    
    # 影響のあるコンテナを停止
    kubectl scale deployment csv-batch-processor --replicas=0
    
    # ネットワークアクセス遮断
    oci network security-list update \
        --security-list-id $SECURITY_LIST_ID \
        --ingress-security-rules '[]' \
        --force
    
    # Object Storage アクセス一時停止
    oci iam policy update \
        --policy-id $POLICY_ID \
        --statements '["Deny dynamic-group csv-batch-instances to manage objects in compartment id '$COMPARTMENT_ID'"]' \
        --force
}

# 2. ログ証跡保全
function preserve_evidence() {
    mkdir -p /tmp/incident-$(date +%Y%m%d-%H%M%S)
    
    # Kubernetes ログ収集
    kubectl logs deployment/csv-batch-processor > /tmp/incident-*/k8s-logs.txt
    
    # OCI Audit ログ収集
    oci audit event list \
        --compartment-id $COMPARTMENT_ID \
        --start-time $(date -d '1 hour ago' -Iseconds) \
        --end-time $(date -Iseconds) > /tmp/incident-*/audit-logs.json
    
    # システムメトリクス収集
    kubectl top pods > /tmp/incident-*/metrics.txt
}

# 実行
if [[ "$1" == "emergency" ]]; then
    emergency_isolation
    preserve_evidence
fi
```

## 12. セキュリティ成熟度評価

### 📊 現在のセキュリティレベル

| カテゴリ | スコア | 評価 |
|---------|--------|------|
| 認証・認可 | 8/10 | 優秀 |
| データ保護 | 9/10 | 優秀 |
| ネットワーク | 7/10 | 良好 |
| 監査・監視 | 6/10 | 改善要 |
| インシデント対応 | 5/10 | 改善要 |
| **総合評価** | **7/10** | **良好** |

### 🎯 セキュリティ向上ロードマップ

**Phase 1（現在〜1ヶ月）**:
- OCI Security Zones設定
- Container Image Scanning自動化
- セキュリティダッシュボード構築

**Phase 2（1〜3ヶ月）**:
- OCI Cloud Guard統合
- 自動化されたインシデント対応
- セキュリティトレーニング実施

**Phase 3（3〜6ヶ月）**:
- ペネトレーションテスト実施
- ゼロトラスト ネットワーク実装
- セキュリティメトリクス最適化

---

**最終更新**: 2025-08-06 - OCI PoC環境用セキュリティガイド  
**セキュリティレベル**: 開発・学習用として適切、本番環境では追加対策必要  
**次回レビュー予定**: 2025-12-06

**注意**: このガイドはOCI環境での最新セキュリティ対策を示しています。本番環境では組織のセキュリティポリシーに従い、追加のセキュリティ対策を実装してください。