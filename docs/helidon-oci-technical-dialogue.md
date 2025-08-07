# 🎓 CSVバッチプロセッサーの技術解説 - AさんとBさんの対話（OCI版）

## 概要
このドキュメントは、CSVバッチプロセッサープロジェクトで使用されているHelidon MP、OCI SDK、Resilience4jとその関連技術について、ジュニアエンジニア（Aさん）とシニアエンジニア（Bさん）の対話形式で解説したものです。

---

## ☕ 月曜日の朝、開発チームのミーティングルームにて

### 初めての出会い

**Aさん（ジュニアエンジニア）**: おはようございます、Bさん！先週から参画したCSVバッチプロセッサープロジェクトなんですが、OCI環境でHelidon MPを使っているんですよね？Spring Bootは使ったことあるんですが、Helidon MPは初めてで...

**Bさん（シニアエンジニア）**: おはよう、Aさん！そうだね、このプロジェクトはOCI（Oracle Cloud Infrastructure）でHelidon MP 4.0.0を使っているよ。Spring Bootとは少し異なるアプローチなんだ。Helidon MPはMicroProfile標準に準拠したマイクロサービス向けフレームワークで、特にOracleのクラウド環境と親和性が高いんだ。

**Aさん**: なるほど、でもこのプロジェクトって単一のアプリケーションですよね？なぜHelidon MPを？

**Bさん**: いい質問だね！実はOCI環境での運用最適化と将来的なマイクロサービス化を見据えているんだ。それに、Helidon MPの機能はOCI環境での単一アプリケーションでも大きなメリットがあるんだよ。具体的に見ていこうか。

---

## 📊 プロジェクトアーキテクチャの説明

### 全体構成の理解

**Bさん**: まず、全体構成を見てみよう。

```
┌─────────────────────────────────────────────────┐
│             CSV Batch Processor                 │
│           (Helidon MP Application)              │
│                                                 │
│  ┌─────────────────────────────────────────┐  │
│  │          Helidon MP 4.0.0               │  │
│  │  ┌────────────────────────────────────┐ │  │
│  │  │    MicroProfile Standards          │ │  │
│  │  │  ・Config (設定管理)                │ │  │
│  │  │  ・Fault Tolerance (障害対応)      │ │  │
│  │  │  ・Metrics (監視)                  │ │  │
│  │  │  ・Health Check (ヘルスチェック)   │ │  │
│  │  └────────────────────────────────────┘ │  │
│  └─────────────────────────────────────────┘  │
│                                                 │
│  ┌─────────────────────────────────────────┐  │
│  │          OCI SDK v3.xx                  │  │
│  │  ・Object Storage Service              │  │
│  │  ・Vault Service                       │  │
│  │  ・Instance Principal Authentication   │  │
│  │  ・Autonomous Database                 │  │
│  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
         ↓              ↓              ↓
   Autonomous DB    SOAP API    Object Storage
```

**Aさん**: Helidon MPのどんな機能を使っているんですか？

**Bさん**: 主に以下のMicroProfile機能とOCI固有の機能を活用しているよ：
1. **MicroProfile Fault Tolerance** - 自動リトライと障害対応
2. **MicroProfile Config** - 外部化設定管理
3. **MicroProfile Metrics** - メトリクス監視
4. **MicroProfile Health** - ヘルスチェック
5. **OCI Instance Principal** - ネイティブ認証
6. **Resilience4j統合** - サーキットブレーカー

それぞれ詳しく見ていこう！

---

## 🔄 MicroProfile Fault Toleranceの説明

### 自動リトライの仕組み

**Bさん**: まず最初は**MicroProfile Fault Tolerance**だね。SoapClient.javaを見てみよう。

```java
@ApplicationScoped
public class SoapClient {
    
    @Retry(
        retryOn = {WebServiceIOException.class, RuntimeException.class},
        maxRetries = 3,
        delay = 1000,
        delayUnit = ChronoUnit.MILLIS,
        maxDuration = 10000,
        durationUnit = ChronoUnit.MILLIS
    )
    @CircuitBreaker(
        requestVolumeThreshold = 5,
        failureRatio = 0.5,
        delay = 10000,
        delayUnit = ChronoUnit.MILLIS
    )
    public EmployeeDetails getEmployeeDetails(Long employeeId) {
        logger.info("Calling SOAP API for employee ID: {}", employeeId);
        
        try {
            // SOAP API呼び出し処理
            GetEmployeeDetailsRequest request = new GetEmployeeDetailsRequest();
            request.setEmployeeId(employeeId);
            
            GetEmployeeDetailsResponse response = 
                (GetEmployeeDetailsResponse) getWebServiceTemplate()
                    .marshalSendAndReceive(soapApiUrl, request);
                    
            return mapToEmployeeDetails(response);
            
        } catch (WebServiceIOException e) {
            logger.error("Network error calling SOAP API", e);
            throw e;  // MicroProfile Fault Toleranceが自動的にリトライ
        }
    }
    
    @Fallback
    public EmployeeDetails getEmployeeDetailsFallback(Long employeeId) {
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

**Aさん**: `@Retry`と`@CircuitBreaker`アノテーションですか？

**Bさん**: そう！これがMicroProfile標準の機能なんだ。Spring Cloudと似ているけど、Jakarta EEの標準に準拠している。
- **maxRetries = 3**: 最大3回まで自動リトライ
- **delay**: 1秒間隔でリトライ（指数バックオフも可能）
- **maxDuration**: 最大10秒でタイムアウト
- **retryOn**: リトライ対象の例外クラスを指定

**Aさん**: なるほど！MicroProfileは標準化されているから、他のMicroProfile実装でも同じコードが動くんですね！

**Bさん**: その通り！Helidon、Open Liberty、Payara、Quarrkusなど、どのMicroProfile実装でも互換性がある。

---

## 🔌 Resilience4j統合とCircuit Breakerパターン

### 障害の連鎖を防ぐ仕組み

**Bさん**: 次は**Resilience4j統合**。HelidonはMicroProfile Fault ToleranceとResilience4jの両方をサポートしているんだ。

```java
@ApplicationScoped
public class EnhancedSoapClient {
    
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    @PostConstruct
    public void initialize() {
        // Resilience4j Circuit Breaker設定
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
            
        this.circuitBreaker = CircuitBreaker.of("soapService", cbConfig);
        
        // Resilience4j Retry設定
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .exponentialBackoffMultiplier(2.0)
            .retryExceptions(WebServiceIOException.class, IOException.class)
            .build();
            
        this.retry = Retry.of("soapRetry", retryConfig);
        
        // Circuit BreakerとRetryを組み合わせ
        this.circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                logger.info("Circuit breaker state transition: {} -> {}", 
                           event.getStateTransition().getFromState(),
                           event.getStateTransition().getToState()));
    }
    
    public EmployeeDetails getEmployeeDetailsWithResilience(Long employeeId) {
        Supplier<EmployeeDetails> decoratedSupplier = 
            CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, () -> callSoapApi(employeeId)));
        
        return Try.ofSupplier(decoratedSupplier)
            .recover(throwable -> {
                logger.error("All retry attempts failed for employee {}", employeeId, throwable);
                return getEmployeeDetailsFallback(employeeId);
            })
            .get();
    }
}
```

**Aさん**: Circuit Breakerって何をするんですか？

**Bさん**: 電気回路のブレーカーと同じ考え方だよ。外部サービスが連続して失敗すると、自動的に「回路を遮断」して、しばらくの間はフォールバック処理を実行するんだ。

### Circuit Breakerの設定（MicroProfile Config）

```yaml
# microprofile-config.properties
resilience4j.circuitbreaker.instances.soapService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.soapService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.soapService.sliding-window-size=10
resilience4j.circuitbreaker.instances.soapService.minimum-number-of-calls=5

# MicroProfile Fault Tolerance設定
mp.faulttolerance.retry.SoapClient.getEmployeeDetails.maxRetries=3
mp.faulttolerance.retry.SoapClient.getEmployeeDetails.delay=1000
mp.faulttolerance.circuitbreaker.SoapClient.getEmployeeDetails.requestVolumeThreshold=5
mp.faulttolerance.circuitbreaker.SoapClient.getEmployeeDetails.failureRatio=0.5
```

**Aさん**: すごい！障害の連鎖を防げるんですね！

**Bさん**: そう！3つの状態があるんだ：
1. **CLOSED（閉）**: 正常状態、通常通り処理
2. **OPEN（開）**: 障害状態、フォールバック実行
3. **HALF_OPEN（半開）**: 回復確認中、一部リクエストを通す

---

## ⚙️ MicroProfile Configによる外部化設定

### 環境別設定管理

**Bさん**: Helidonの強力な機能の一つが**MicroProfile Config**だ。環境ごとの設定管理が非常に簡単になるんだよ。

```java
@ApplicationScoped
public class OCIConfigurationService {
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.namespace")
    private String namespace;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.bucket")
    private String bucket;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.region", defaultValue = "us-ashburn-1")
    private String region;
    
    @Inject
    @ConfigProperty(name = "oci.auth.method", defaultValue = "instance_principal")
    private String authMethod;
    
    @Inject
    @ConfigProperty(name = "datasource.url")
    private String databaseUrl;
    
    @Inject
    @ConfigProperty(name = "datasource.password")
    private String databasePassword;
    
    @PostConstruct
    public void logConfiguration() {
        logger.info("OCI Configuration loaded:");
        logger.info("  Namespace: {}", namespace);
        logger.info("  Bucket: {}", bucket);
        logger.info("  Region: {}", region);
        logger.info("  Auth Method: {}", authMethod);
        // パスワードはログに出力しない
    }
}
```

### 設定ファイルの階層

```properties
# microprofile-config.properties（デフォルト設定）
oci.objectstorage.namespace=${OCI_NAMESPACE}
oci.objectstorage.bucket=${OCI_BUCKET:csv-export-bucket}
oci.objectstorage.region=${OCI_REGION:us-ashburn-1}
oci.auth.method=${OCI_AUTH_METHOD:instance_principal}

# データベース設定
datasource.url=${DB_URL:jdbc:oracle:thin:@localhost:1521/XEPDB1}
datasource.username=${DB_USERNAME:csvuser}
datasource.password=${DB_PASSWORD}

# SOAP API設定
soap.api.url=${SOAP_API_URL:http://localhost:8080/ws}
soap.api.timeout.connection=${SOAP_API_CONNECTION_TIMEOUT:30000}
soap.api.timeout.read=${SOAP_API_READ_TIMEOUT:60000}
```

```properties
# microprofile-config-docker.properties（Docker環境専用）
# MicroProfile Configが自動的に読み込む
datasource.url=jdbc:oracle:thin:@oracle-db:1521/XEPDB1
soap.api.url=http://soap-stub:8080/ws
oci.objectstorage.endpoint=http://oci-local-testing:8080

# Universal Connection Pool設定
datasource.connectionPooling.initialPoolSize=5
datasource.connectionPooling.maxPoolSize=20
datasource.connectionPooling.minPoolSize=2
datasource.connectionPooling.connectionTimeout=60000
```

**Aさん**: プロファイルで設定を切り替えられるんですね！

**Bさん**: その通り！しかも優先順位があるんだ：
1. システムプロパティ
2. 環境変数
3. microprofile-config.properties
4. @ConfigPropertyのdefaultValue

---

## 🔗 Universal Connection Pool (UCP)管理

### 高性能なOracleネイティブ接続プール

**Aさん**: ところで、Universal Connection Poolという単語が出てきますが...

**Bさん**: UCPはOracleが開発した高性能な接続プールライブラリで、特にAutonomous Databaseとの接続で威力を発揮するんだ。HikariCPの代替として、OCI環境では推奨されているよ。

```java
@ApplicationScoped
public class AutonomousDatabaseConfig {
    
    @Inject
    @ConfigProperty(name = "datasource.url")
    private String jdbcUrl;
    
    @Inject
    @ConfigProperty(name = "datasource.username")
    private String username;
    
    @Inject
    @ConfigProperty(name = "datasource.password")
    private String password;
    
    @Inject
    @ConfigProperty(name = "autonomous.wallet.path", defaultValue = "")
    private Optional<String> walletPath;
    
    @Inject
    @ConfigProperty(name = "autonomous.wallet.password", defaultValue = "")
    private Optional<String> walletPassword;
    
    @Produces
    @ApplicationScoped
    public PoolDataSource poolDataSource() throws SQLException {
        PoolDataSource poolDataSource = PoolDataSourceFactory.getPoolDataSource();
        
        // 基本接続設定
        poolDataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        poolDataSource.setURL(jdbcUrl);
        poolDataSource.setUser(username);
        poolDataSource.setPassword(password);
        
        // mTLS設定（Autonomous Database用）
        if (walletPath.isPresent() && walletPassword.isPresent()) {
            System.setProperty("oracle.net.tns_admin", walletPath.get());
            System.setProperty("oracle.net.wallet_location", walletPath.get());
            System.setProperty("oracle.net.wallet_password", walletPassword.get());
            logger.info("mTLS wallet configured for Autonomous Database");
        }
        
        // プール設定
        poolDataSource.setInitialPoolSize(5);
        poolDataSource.setMaxPoolSize(20);
        poolDataSource.setMinPoolSize(2);
        poolDataSource.setConnectionTimeout(60); // 60秒
        poolDataSource.setInactiveConnectionTimeout(300); // 5分
        poolDataSource.setTimeoutCheckInterval(30); // 30秒
        poolDataSource.setMaxStatements(100); // ステートメントキャッシュ
        
        // 接続検証設定
        poolDataSource.setValidateConnectionOnBorrow(true);
        poolDataSource.setSQLForValidateConnection("SELECT 1 FROM DUAL");
        
        logger.info("Universal Connection Pool configured successfully");
        return poolDataSource;
    }
}
```

**Aさん**: Autonomous Databaseとの親和性が高いんですね？

**Bさん**: その通り！UCPはAutonomous Databaseの機能をフル活用できる：
- **Connection Labeling**: 接続の用途別ラベリング
- **Failover**: 自動フェイルオーバー機能
- **Load Balancing**: 複数インスタンス間での負荷分散
- **mTLS Support**: Mutual TLS接続の完全サポート

### UCPのメトリクス監視

```java
@ApplicationScoped
public class UCPMetricsService {
    
    @Inject
    private PoolDataSource poolDataSource;
    
    @Produces
    @ApplicationScoped
    public Gauge<Integer> activeConnectionsGauge() {
        return Gauge.<Integer>builder("ucp.connections.active")
            .description("Active UCP connections")
            .unit(MetricUnits.NONE)
            .register(() -> {
                try {
                    return poolDataSource.getStatistics().getActiveConnectionsCount();
                } catch (SQLException e) {
                    logger.error("Failed to get UCP statistics", e);
                    return 0;
                }
            });
    }
}
```

---

## 🏭 Helidonの自動設定とOCI SDK統合

### CDIベースの依存性注入

**Bさん**: HelidonはCDI（Contexts and Dependency Injection）を使った依存性注入が特徴なんだ。

```java
@ApplicationScoped
public class OCIServiceConfiguration {
    
    @Inject
    @ConfigProperty(name = "oci.auth.method")
    private String authMethod;
    
    @Produces
    @ApplicationScoped
    public AuthenticationDetailsProvider authenticationDetailsProvider() {
        switch (authMethod) {
            case "instance_principal":
                logger.info("Using Instance Principal authentication");
                return InstancePrincipalsAuthenticationDetailsProvider.builder()
                    .build();
                    
            case "config_file":
                logger.info("Using Config File authentication");
                return new ConfigFileAuthenticationDetailsProvider(
                    System.getProperty("oci.config.file", "~/.oci/config"),
                    System.getProperty("oci.profile", "DEFAULT")
                );
                
            default:
                throw new IllegalArgumentException("Unsupported auth method: " + authMethod);
        }
    }
    
    @Produces
    @ApplicationScoped
    public ObjectStorage objectStorageClient(AuthenticationDetailsProvider authProvider) {
        ObjectStorageClient.Builder builder = ObjectStorageClient.builder();
        
        // エンドポイント設定（開発環境用）
        Optional<String> endpoint = ConfigProvider.getConfig()
            .getOptionalValue("oci.objectstorage.endpoint", String.class);
        if (endpoint.isPresent()) {
            builder.endpoint(endpoint.get());
            logger.info("Using custom Object Storage endpoint: {}", endpoint.get());
        }
        
        return builder.build(authProvider);
    }
    
    @Produces
    @ApplicationScoped
    public VaultsClient vaultsClient(AuthenticationDetailsProvider authProvider) {
        return VaultsClient.builder()
            .build(authProvider);
    }
}
```

**Aさん**: `@Produces`アノテーションですか？

**Bさん**: そう！CDIの機能で、複雑な初期化処理が必要なBeanを作成する時に使うんだ。Springの`@Bean`と似ているけど、Jakarta EEの標準だからより強力で柔軟なんだよ。

### Maven依存関係

```xml
<!-- pom.xml -->
<parent>
    <groupId>io.helidon.applications</groupId>
    <artifactId>helidon-mp</artifactId>
    <version>4.0.0</version>
</parent>

<properties>
    <oci.java.sdk.version>3.25.0</oci.java.sdk.version>
    <resilience4j.version>2.1.0</resilience4j.version>
</properties>

<dependencies>
    <!-- Helidon MP -->
    <dependency>
        <groupId>io.helidon.microprofile.bundles</groupId>
        <artifactId>helidon-microprofile</artifactId>
    </dependency>
    
    <!-- OCI SDK -->
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-objectstorage</artifactId>
        <version>${oci.java.sdk.version}</version>
    </dependency>
    
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-vault</artifactId>
        <version>${oci.java.sdk.version}</version>
    </dependency>
    
    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-circuitbreaker</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <!-- Oracle UCP -->
    <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ucp</artifactId>
    </dependency>
</dependencies>
```

---

## 🔍 MicroProfile Metricsによる監視

### アプリケーション状態の可視化

**Bさん**: MicroProfile Metricsも重要な機能だよ。アプリケーションのメトリクスを標準的な方法で収集できる。

```java
@ApplicationScoped
@Counted(name = "csv.export.total", description = "Total CSV exports")
@Timed(name = "csv.export.duration", description = "CSV export duration")
public class CsvExportService {
    
    @Inject
    @Metric(name = "csv.export.errors", description = "CSV export errors")
    private Counter errorCounter;
    
    @Inject
    @Metric(name = "csv.processing.histogram")
    private Histogram processingTimeHistogram;
    
    public void exportEmployeesToCsv() {
        Timer.Sample sample = Timer.start();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // CSV出力処理
            performCsvExport();
            
            long duration = System.currentTimeMillis() - startTime;
            processingTimeHistogram.update(duration);
            
        } catch (Exception e) {
            errorCounter.inc();
            throw e;
        } finally {
            sample.stop();
        }
    }
}
```

**Aさん**: どんな情報が見れるんですか？

**Bさん**: 実際にアクセスしてみよう。

### メトリクスエンドポイント

```bash
# MicroProfile Metricsエンドポイント
curl http://localhost:8080/metrics

# アプリケーション固有のメトリクス
curl http://localhost:8080/metrics/application

{
  "csv.export.total": 42,
  "csv.export.duration": {
    "count": 42,
    "max": 2.531,
    "mean": 1.234,
    "min": 0.987,
    "p50": 1.123,
    "p75": 1.456,
    "p95": 2.123,
    "p98": 2.345,
    "p99": 2.456,
    "p999": 2.531
  },
  "csv.export.errors": 3
}
```

### MicroProfile Healthによるヘルスチェック

```java
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {
    
    @Inject
    private DataSource dataSource;
    
    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection()) {
            connection.prepareStatement("SELECT 1 FROM DUAL").execute();
            
            return HealthCheckResponse.named("database")
                .status(true)
                .withData("database", "Oracle")
                .withData("status", "UP")
                .build();
                
        } catch (SQLException e) {
            return HealthCheckResponse.named("database")
                .status(false)
                .withData("error", e.getMessage())
                .build();
        }
    }
}

@ApplicationScoped  
public class ObjectStorageHealthCheck implements HealthCheck {
    
    @Inject
    private ObjectStorage objectStorage;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.namespace")
    private String namespace;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.bucket")
    private String bucket;
    
    @Override
    public HealthCheckResponse call() {
        try {
            GetNamespaceRequest request = GetNamespaceRequest.builder()
                .build();
            objectStorage.getNamespace(request);
            
            return HealthCheckResponse.named("object-storage")
                .status(true)
                .withData("namespace", namespace)
                .withData("bucket", bucket)
                .build();
                
        } catch (Exception e) {
            return HealthCheckResponse.named("object-storage")
                .status(false)
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

---

## 🚀 @PostConstructによる初期化処理とOCI Vault統合

### アプリケーション起動時の自動処理

**Aさん**: VaultSecretServiceで`@PostConstruct`を使っていますよね？

**Bさん**: これはJakarta EEの機能で、HelidonのCDIコンテナと完璧に統合されているんだ。OCI Vaultからシークレットを取得する処理を見てみよう。

```java
@ApplicationScoped
public class VaultSecretService {
    
    private static final Logger logger = LoggerFactory.getLogger(VaultSecretService.class);
    
    @Inject
    private VaultsClient vaultsClient;
    
    @Inject
    @ConfigProperty(name = "oci.vault.secret.database-password.id")
    private Optional<String> databasePasswordSecretId;
    
    @Inject
    @ConfigProperty(name = "oci.vault.secret.soap-api-key.id")
    private Optional<String> soapApiKeySecretId;
    
    private String cachedDatabasePassword;
    private String cachedSoapApiKey;
    
    @PostConstruct
    public void initializeSecrets() {
        logger.info("Initializing secrets from OCI Vault...");
        
        try {
            // データベースパスワードの取得
            if (databasePasswordSecretId.isPresent()) {
                cachedDatabasePassword = retrieveSecret(databasePasswordSecretId.get());
                logger.info("Database password secret retrieved successfully");
            }
            
            // SOAP API キーの取得
            if (soapApiKeySecretId.isPresent()) {
                cachedSoapApiKey = retrieveSecret(soapApiKeySecretId.get());
                logger.info("SOAP API key secret retrieved successfully");
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize secrets from OCI Vault. " +
                       "This may cause authentication issues. Error: {}", e.getMessage());
            // エラーが発生してもアプリケーションの起動は続行
        }
    }
    
    public String getDatabasePassword() {
        if (cachedDatabasePassword != null) {
            return cachedDatabasePassword;
        }
        
        // フォールバック: 環境変数から取得
        return System.getenv("DB_PASSWORD");
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    private String retrieveSecret(String secretId) {
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
            logger.error("Failed to retrieve secret from OCI Vault: {}", secretId, e);
            throw new VaultSecretException("Secret retrieval failed", e);
        }
    }
    
    @Scheduled(fixedRate = "PT1H") // 1時間毎にリフレッシュ
    public void refreshSecrets() {
        logger.info("Refreshing secrets from OCI Vault...");
        initializeSecrets();
    }
}
```

**Aさん**: OCI Vaultと完全に統合されているんですね！

**Bさん**: そう！`@PostConstruct`で初期化、`@Retry`でリトライ、`@Scheduled`で定期更新と、複数のアノテーションを組み合わせてセキュアで堅牢なサービスを作れるんだ。

---

## 🐋 Container InstancesとOKEでのHelidon統合

### コンテナオーケストレーション

**Bさん**: 最後に、OCIのContainer InstancesとOKE（Oracle Kubernetes Engine）でのHelidon統合について説明しよう。

```yaml
# Dockerfile（Helidon最適化版）
FROM openjdk:21-jre-slim

# Helidon用の最適化
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 非rootユーザー作成
RUN groupadd -r helidon && useradd -r -g helidon helidon

# アプリケーション配置
WORKDIR /app
COPY target/csv-batch-processor.jar app.jar
COPY target/libs/ libs/

# ディレクトリ権限設定
RUN mkdir -p /app/output /app/cache && \
    chown -R helidon:helidon /app

# 非rootユーザーに切り替え
USER helidon

# Helidon用ヘルスチェック
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health/ready || exit 1

EXPOSE 8080

# Helidon最適化JVMオプション
ENV JAVA_OPTS="-server \
               -Xms256m -Xmx1g \
               -XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### OKE Kubernetes マニフェスト

```yaml
# kubernetes/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: csv-batch-processor
  namespace: csv-batch
spec:
  replicas: 2
  selector:
    matchLabels:
      app: csv-batch-processor
  template:
    metadata:
      labels:
        app: csv-batch-processor
    spec:
      serviceAccountName: csv-batch-sa
      containers:
      - name: csv-batch-processor
        image: oci-region.ocir.io/namespace/csv-batch-processor:latest
        ports:
        - containerPort: 8080
          protocol: TCP
        env:
        - name: MICRONAUT_ENVIRONMENTS
          value: "kubernetes,production"
        - name: OCI_NAMESPACE
          valueFrom:
            configMapKeyRef:
              name: oci-config
              key: namespace
        - name: OCI_BUCKET
          valueFrom:
            configMapKeyRef:
              name: oci-config
              key: bucket
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: database-secret
              key: password
        
        # OCI Workload Identity設定
        - name: OCI_RESOURCE_PRINCIPAL_VERSION
          value: "2.2"
        - name: OCI_RESOURCE_PRINCIPAL_REGION
          value: "us-ashburn-1"
        
        # Helidon ヘルスチェック統合
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
        
        # リソース制限
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
            
        # セキュリティコンテキスト
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          runAsNonRoot: true
          capabilities:
            drop:
            - ALL
            
        # ボリュームマウント
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

**Aさん**: HelidonアプリケーションがOKEと完全に統合されているんですね！

**Bさん**: その通り！特に重要なのは：
1. **MicroProfile Healthとの統合**: `/health/live`と`/health/ready`エンドポイント
2. **OCI Workload Identity**: Instance Principalの自動認証
3. **設定外部化**: ConfigMapとSecretによる設定管理
4. **自動スケーリング**: HPA（Horizontal Pod Autoscaler）対応

---

## 📝 まとめ

### Helidon MP + OCI機能の総括

**Bさん**: このプロジェクトで使っているHelidon MPとOCIの主要機能をまとめると：

| 機能 | 目的 | 実装例 |
|------|------|--------|
| **MicroProfile Fault Tolerance** | 一時的な障害への自動対応 | `@Retry`、`@CircuitBreaker`でSOAP API呼び出しをリトライ |
| **Resilience4j統合** | 高度な障害対策 | プログラマティックなCircuit Breaker制御 |
| **MicroProfile Config** | 環境別設定管理 | `@ConfigProperty`による設定注入 |
| **MicroProfile Metrics** | 監視・メトリクス | `@Counted`、`@Timed`による自動メトリクス収集 |
| **MicroProfile Health** | ヘルスチェック | カスタムヘルスチェックによる状態監視 |
| **OCI Instance Principal** | ネイティブ認証 | パスワードレス認証による セキュリティ向上 |
| **OCI Vault統合** | シークレット管理 | 機密情報の安全な取得と定期更新 |
| **Universal Connection Pool** | 高性能DB接続プール | Autonomous Database最適化 |

### 実装のベストプラクティス

**Aさん**: Helidon MPって、Spring Bootとは違った魅力があるんですね！標準準拠で、OCIとの親和性も高くて...

**Bさん**: その通り！そして覚えておいてほしいベストプラクティスがいくつかある：

1. **MicroProfile標準の活用**
   - 標準準拠により他の実装への移行が容易
   - Jakarta EEエコシステムとの完全互換
   - 長期的な保守性とスキルの汎用性

2. **OCI固有の機能活用**
   - Instance Principal認証でパスワードレス運用
   - Vault統合で機密情報の安全管理
   - OKE Workload Identityでセキュア認証

3. **段階的な障害対策**
   - MicroProfile Fault Toleranceで基本対策
   - Resilience4jで高度なカスタマイズ
   - フォールバック処理で最低限のサービス継続

4. **監視・運用の最適化**
   - MicroProfile Metricsで標準メトリクス
   - カスタムヘルスチェックで詳細監視
   - OCI Monitoring統合で包括的監視

5. **初期化とライフサイクル管理**
   - `@PostConstruct`で起動時の準備
   - `@PreDestroy`でクリーンアップ
   - `@Scheduled`で定期的なメンテナンス

**Aさん**: よく分かりました！Helidon MPの威力とOCIとの統合の素晴らしさを実感できました。早速コードを詳しく見てみます！

**Bさん**: 頑張って！分からないことがあったらいつでも聞いてね。Helidon MPは学習コストは少し高いけど、一度理解するとクラウドネイティブな開発が本当に楽になるよ。そして、このプロジェクトが将来的にマイクロサービス化される時も、MicroProfile標準のおかげでスムーズに移行できるんだ。

**Aさん**: はい！今日教えていただいたことを基に、まずはローカル環境で動かしてみて、実際のOCI連携動作を確認してみます！

---

## 🚀 次のステップ

### Aさんの学習計画

1. **ローカル環境での実行**
   ```bash
   # OCI Local Testing Framework起動
   docker run -d --name oci-local-testing -p 8080:8080 oracle/oci-local-testing:latest
   
   # アプリケーション起動
   mvn compile exec:exec
   ```

2. **MicroProfileエンドポイントの確認**
   ```bash
   curl http://localhost:8080/health
   curl http://localhost:8080/metrics
   curl http://localhost:8080/openapi
   ```

3. **障害シミュレーション**
   - SOAP APIを停止してCircuit Breakerの動作確認
   - Oracle DBを停止してUCPのフェイルオーバー確認
   - OCI Local Testing Frameworkを停止してフォールバック確認

4. **設定のカスタマイズ**
   - MicroProfile Configの環境変数上書き実験
   - 認証方法の切り替え（Instance Principal ↔ Config File）

### 推奨学習リソース

- [Helidon公式ドキュメント](https://helidon.io/docs/v4/)
- [MicroProfile公式サイト](https://microprofile.io/)
- [OCI SDK for Java](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm)
- [Resilience4j公式ドキュメント](https://resilience4j.readme.io/)
- [Oracle Universal Connection Pool](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjucp/)

---

*こうして、AさんはHelidon MPの基本概念と、OCI環境での実装を理解し、次のステップへと進んでいくのでした。この対話が、Helidon MPとOCIを学ぶすべてのエンジニアの助けになることを願っています。*

---

**最終更新**: 2025-08-06 - OCI PoC環境用技術対話書  
**対象フレームワーク**: Helidon MP 4.0 + OCI SDK v3  
**次回レビュー予定**: 2025-12-06

**注意**: このドキュメントはOCI環境でのHelidon MP実装を前提としています。AWS版との技術比較を行う際は、認証方式、クラウドサービス、フレームワークの違いを考慮してください。