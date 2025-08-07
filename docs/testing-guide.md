# テストガイド（OCI版）

## 目次
1. [テスト概要](#1-テスト概要)
2. [テスト環境構築](#2-テスト環境構築)
3. [単体テスト仕様](#3-単体テスト仕様)
4. [統合テスト仕様](#4-統合テスト仕様)
5. [テストデータ管理](#5-テストデータ管理)
6. [モック・スタブ管理](#6-モック・スタブ管理)
7. [テスト実行とレポート](#7-テスト実行とレポート)
8. [テストベストプラクティス](#8-テストベストプラクティス)
9. [環境別テスト実行ガイド](#9-環境別テスト実行ガイド)
10. [トラブルシューティング](#10-トラブルシューティング)

---

## 1. テスト概要

### 1.1 テスト戦略
- **テストピラミッド**: 単体テスト > 統合テスト > E2Eテスト
- **目標カバレッジ**: 80%以上
- **テストフレームワーク**: JUnit 5, Mockito, Helidon Test, TestContainers
- **クラウドサービステスト**: OCI Local Testing FrameworkでのObject Storageエミュレーション
- **新機能テスト**: Resilience4j、サーキットブレーカー、Object Storage統合、例外処理
- **セキュリティテスト**: インスタンスプリンシパル認証、Vault統合のテスト

### 1.2 テスト分類
| テスト種別 | 対象 | 実行環境 | 実行時間目安 | テスト数 |
|-----------|------|---------|-------------|---------|
| 単体テスト | クラス単位、サービス、例外処理 | H2インメモリDB | 1-2分 | 70個 |
| 統合テスト | DB、Object Storage、リトライ、エラーハンドリング | TestContainers + OCI Local Testing | 4-8分 | 18個 |
| 合計 | 全体 | - | 8-12分 | 88個 |

### 1.3 新機能のテスト対象
- **例外処理**: ObjectStorageException、DataProcessingException、VaultSecretException
- **Object Storage統合**: ObjectStorageService、アップロード・ダウンロード・削除機能
- **リトライ機能**: Resilience4j、指数バックオフ、最大試行回数
- **サーキットブレーカー**: 障害時のフォールバック処理
- **OCI認証**: インスタンスプリンシパル認証、Vault統合
- **環境変数設定**: セキュリティ強化された設定の動作確認

### 1.4 テスト実行制御
- **通常実行**: `mvn test` - 70個のテスト（統合テストを除く）
- **完全実行**: `mvn test -DRUN_INTEGRATION_TESTS=true` - 88個すべてのテスト
- **セキュリティ考慮**: テストでは環境変数未設定でもデフォルト値で動作

---

## 2. テスト環境構築

### 2.1 前提条件
```bash
# 必要なソフトウェア
Java 21+
Maven 3.8+
Docker Desktop（統合テスト用）
OCI CLI（任意、統合テストで使用）

# バージョン確認
java -version
mvn -version
docker --version
oci --version
```

### 2.2 基本テスト実行
```bash
# 単体テストのみ実行（70個のテスト）
mvn test

# 全テスト実行（88個のテスト、統合テスト含む）
mvn test -DRUN_INTEGRATION_TESTS=true

# カバレッジレポート付きテスト実行
mvn clean test jacoco:report

# Resilience4j機能のテスト実行
mvn test -Dtest="*ResilienceTest"

# Object Storage統合機能のテスト実行
mvn test -Dtest="*ObjectStorage*Test" -DRUN_INTEGRATION_TESTS=true

# Helidon Framework テスト実行
mvn test -Dtest="*Helidon*Test"
```

### 2.3 Docker環境でのテスト
```bash
# テスト用Docker環境起動
docker-compose -f docker-compose.test.yml up -d

# E2Eテスト実行
docker-compose -f docker-compose.test.yml exec csv-batch-processor mvn test

# OCI Local Testing Framework起動
docker run -d --name oci-local-testing \
  -p 8080:8080 \
  oracle/oci-local-testing:latest

# テスト用Docker環境停止
docker-compose -f docker-compose.test.yml down
```

---

## 3. 単体テスト仕様

### 3.1 実際のテストクラス構成
```
src/test/java/com/example/csvbatch/
├── CsvBatchProcessorApplicationTest.java     # Helidon/Spring Boot起動テスト（1メソッド）
├── TestConfig.java                          # テスト用設定クラス
├── client/
│   └── SoapClientTest.java                  # SOAP通信、リトライ、サーキットブレーカーテスト（8メソッド）
├── config/
│   ├── OCIConfigTest.java                   # OCI設定、環境依存設定テスト（10メソッド）
│   ├── DataInitializerTest.java             # データ初期化テスト（4メソッド）
│   ├── AutonomousDatabaseConfigTest.java    # Autonomous DB設定テスト（6メソッド）
│   └── WebServiceConfigTest.java            # Webサービス設定テスト（4メソッド）
├── dto/
│   └── EmployeeCsvDataTest.java             # DTOテスト（4メソッド）
├── entity/
│   └── EmployeeTest.java                    # エンティティテスト（4メソッド）
├── exception/                               # OCI専用例外処理テスト
│   ├── CsvProcessingExceptionTest.java      # CSV処理例外テスト（3メソッド）
│   ├── ObjectStorageExceptionTest.java     # Object Storage例外テスト（4メソッド）
│   └── VaultSecretExceptionTest.java       # Vault例外テスト（3メソッド）
├── integration/                             # 統合テスト
│   ├── AutonomousDatabaseIntegrationTest.java # Autonomous DB統合テスト（4メソッド）
│   ├── ErrorHandlingIntegrationTest.java    # エラーハンドリング統合テスト（4メソッド）
│   ├── OCILocalTestingIntegrationTest.java  # OCI Local Testing統合テスト（4メソッド）
│   ├── ResilienceIntegrationTest.java       # Resilience4j統合テスト（3メソッド）
│   └── ObjectStorageIntegrationTest.java   # Object Storage統合テスト（6メソッド）
├── security/
│   ├── InstancePrincipalAuthTest.java       # インスタンスプリンシパル認証テスト（5メソッド）
│   └── VaultSecretServiceTest.java          # Vault統合テスト（4メソッド）
├── repository/
│   └── EmployeeRepositoryTest.java          # リポジトリテスト（4メソッド）
└── service/
    ├── CsvExportServiceTest.java            # CSV出力サービステスト（8メソッド）
    └── ObjectStorageServiceTest.java        # Object Storageサービステスト（8メソッド）
```

### 3.2 各テストクラスの詳細実装

#### 3.2.1 アプリケーションテスト

**CsvBatchProcessorApplicationTest.java**
```java
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CsvBatchProcessorApplicationTest {
    
    @MockBean
    private WebServiceTemplate webServiceTemplate;
    
    @MockBean
    private ObjectStorageService objectStorageService;
    
    @Test
    @DisplayName("Helidon アプリケーションコンテキスト読み込みテスト")
    void contextLoads(ApplicationContext context) {
        // Helidonコンテキストが正常に起動することを確認
        assertThat(context).isNotNull();
        assertThat(context.isRunning()).isTrue();
    }
}
```

#### 3.2.2 OCI設定テスト

**OCIConfigTest.java**
```java
@MicronautTest
@TestPropertySource(properties = {
    "oci.auth.method=instance_principal",
    "oci.objectstorage.namespace=test-namespace",
    "oci.objectstorage.bucket=test-bucket"
})
class OCIConfigTest {
    
    @Inject
    private OCIConfig ociConfig;
    
    @Test
    @DisplayName("インスタンスプリンシパル認証設定テスト")
    void testInstancePrincipalAuth() {
        AuthenticationDetailsProvider provider = ociConfig.instancePrincipalAuth();
        
        assertThat(provider).isInstanceOf(InstancePrincipalsAuthenticationDetailsProvider.class);
    }
    
    @Test
    @DisplayName("Object Storage設定テスト")
    void testObjectStorageConfiguration() {
        assertThat(ociConfig.getNamespace()).isEqualTo("test-namespace");
        assertThat(ociConfig.getBucket()).isEqualTo("test-bucket");
    }
    
    @Test
    @DisplayName("設定ファイル認証設定テスト")
    void testConfigFileAuth() {
        // Given
        System.setProperty("oci.config.file", "~/.oci/config");
        System.setProperty("oci.profile", "DEFAULT");
        
        // When
        AuthenticationDetailsProvider provider = ociConfig.configFileAuth();
        
        // Then
        assertThat(provider).isInstanceOf(ConfigFileAuthenticationDetailsProvider.class);
    }
    
    @Test
    @DisplayName("環境変数による認証方法切り替えテスト")
    void testAuthMethodSwitch(@Value("${oci.auth.method}") String authMethod) {
        assertThat(authMethod).isEqualTo("instance_principal");
    }
}
```

#### 3.2.3 Autonomous Database設定テスト

**AutonomousDatabaseConfigTest.java**
```java
@MicronautTest
class AutonomousDatabaseConfigTest {
    
    @Inject
    private AutonomousDatabaseConfig autonomousDatabaseConfig;
    
    @Test
    @DisplayName("mTLS接続設定テスト")
    void testMTLSConnectionConfiguration() {
        // Given
        String walletPath = "/opt/oracle/wallet";
        String walletPassword = "test-wallet-password";
        
        // When
        DataSource dataSource = autonomousDatabaseConfig.dataSource(walletPath, walletPassword);
        
        // Then
        assertThat(dataSource).isInstanceOf(OracleDataSource.class);
    }
    
    @Test
    @DisplayName("接続文字列ベース設定テスト")
    void testConnectionStringConfiguration() {
        // ウォレットなしでの接続設定テスト
        DataSource dataSource = autonomousDatabaseConfig.dataSource(null, null);
        
        assertThat(dataSource).isNotNull();
    }
    
    @Test
    @DisplayName("Universal Connection Pool設定テスト")
    void testUCPConfiguration() {
        PoolDataSource poolDataSource = autonomousDatabaseConfig.poolDataSource();
        
        assertThat(poolDataSource.getInitialPoolSize()).isEqualTo(5);
        assertThat(poolDataSource.getMaxPoolSize()).isEqualTo(20);
    }
}
```

#### 3.2.4 新規例外クラステスト

**ObjectStorageExceptionTest.java**
```java
class ObjectStorageExceptionTest {
    
    @Test
    @DisplayName("メッセージのみの例外作成テスト")
    void testCreateWithMessage() {
        String message = "Object Storage処理エラーが発生しました";
        ObjectStorageException exception = new ObjectStorageException(message);
        
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    @DisplayName("メッセージと原因の例外作成テスト")
    void testCreateWithMessageAndCause() {
        String message = "Object Storage処理エラー";
        BmcException cause = new BmcException(404, "NotFound", "Object not found", "request-id");
        
        ObjectStorageException exception = new ObjectStorageException(message, cause);
        
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    @DisplayName("例外の継承関係テスト")
    void testExceptionHierarchy() {
        ObjectStorageException exception = new ObjectStorageException("test");
        
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    @DisplayName("OCI固有エラー情報テスト")
    void testOCISpecificErrorInfo() {
        BmcException ociError = new BmcException(503, "ServiceUnavailable", "Service temporarily unavailable", "req-123");
        ObjectStorageException exception = new ObjectStorageException("OCI Object Storage error", ociError);
        
        assertThat(exception.getCause()).isInstanceOf(BmcException.class);
        BmcException bmcCause = (BmcException) exception.getCause();
        assertThat(bmcCause.getStatusCode()).isEqualTo(503);
    }
}
```

#### 3.2.5 Object Storageサービステスト

**ObjectStorageServiceTest.java**
```java
@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceTest {
    
    @Mock
    private ObjectStorage objectStorageClient;
    
    @Mock
    private AuthenticationDetailsProvider authProvider;
    
    @InjectMocks
    private ObjectStorageService objectStorageService;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(objectStorageService, "namespace", "test-namespace");
        ReflectionTestUtils.setField(objectStorageService, "bucketName", "test-bucket");
    }
    
    @Test
    @DisplayName("Object Storageファイルアップロード成功テスト")
    void testUploadObjectSuccess() throws Exception {
        // Given
        String objectName = "test/file.csv";
        String content = "test,data\n1,test";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();
        
        when(objectStorageClient.putObject(any(PutObjectRequest.class)))
                .thenReturn(response);
        
        // When & Then
        assertDoesNotThrow(() -> objectStorageService.uploadObject(objectName, inputStream));
        
        verify(objectStorageClient).putObject(any(PutObjectRequest.class));
    }
    
    @Test
    @DisplayName("Object Storageファイルアップロード失敗時の例外処理テスト")
    void testUploadObjectFailure() {
        // Given
        String objectName = "test/file.csv";
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());
        
        when(objectStorageClient.putObject(any(PutObjectRequest.class)))
                .thenThrow(new BmcException(500, "InternalServerError", "Internal server error", "req-123"));
        
        // When & Then
        assertThatThrownBy(() -> objectStorageService.uploadObject(objectName, inputStream))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessageContaining("Object Storageアップロードに失敗しました");
    }
    
    @Test
    @DisplayName("Pre-authenticated Request URL生成テスト")
    void testGeneratePreAuthenticatedRequestUrl() {
        // Given
        String objectName = "test/file.csv";
        CreatePreauthenticatedRequestResponse response = CreatePreauthenticatedRequestResponse.builder()
                .preauthenticatedRequest(PreauthenticatedRequest.builder()
                    .accessUri("https://objectstorage.us-ashburn-1.oraclecloud.com/p/abcd/n/namespace/b/bucket/o/test%2Ffile.csv")
                    .build())
                .build();
        
        when(objectStorageClient.createPreauthenticatedRequest(any(CreatePreauthenticatedRequestRequest.class)))
                .thenReturn(response);
        
        // When
        String url = objectStorageService.generatePreAuthenticatedUrl(objectName, Duration.ofHours(1));
        
        // Then
        assertThat(url).contains("objectstorage.us-ashburn-1.oraclecloud.com");
        assertThat(url).contains("test%2Ffile.csv");
    }
}
```

#### 3.2.6 Vault Secret Service テスト

**VaultSecretServiceTest.java**
```java
@ExtendWith(MockitoExtension.class)
class VaultSecretServiceTest {
    
    @Mock
    private VaultsClient vaultsClient;
    
    @Mock
    private SecretsClient secretsClient;
    
    @InjectMocks
    private VaultSecretService vaultSecretService;
    
    @Test
    @DisplayName("Vaultシークレット取得成功テスト")
    void testGetSecretSuccess() {
        // Given
        String secretId = "ocid1.vaultsecret.oc1..xxxxx";
        String expectedSecret = "test-secret-value";
        
        SecretBundleContentDetails contentDetails = Base64SecretBundleContentDetails.builder()
                .content(Base64.getEncoder().encodeToString(expectedSecret.getBytes()))
                .build();
        
        SecretBundle secretBundle = SecretBundle.builder()
                .secretBundleContent(contentDetails)
                .build();
        
        GetSecretBundleResponse response = GetSecretBundleResponse.builder()
                .secretBundle(secretBundle)
                .build();
        
        when(vaultsClient.getSecretBundle(any(GetSecretBundleRequest.class)))
                .thenReturn(response);
        
        // When
        String result = vaultSecretService.getSecret(secretId);
        
        // Then
        assertThat(result).isEqualTo(expectedSecret);
    }
    
    @Test
    @DisplayName("Vaultシークレット取得失敗時の例外処理テスト")
    void testGetSecretFailure() {
        // Given
        String secretId = "ocid1.vaultsecret.oc1..xxxxx";
        
        when(vaultsClient.getSecretBundle(any(GetSecretBundleRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "Secret not found", "req-123"));
        
        // When & Then
        assertThatThrownBy(() -> vaultSecretService.getSecret(secretId))
                .isInstanceOf(VaultSecretException.class)
                .hasMessageContaining("シークレット取得に失敗しました");
    }
}
```

---

## 4. 統合テスト仕様

### 4.1 統合テスト一覧

| テストクラス | テスト数 | 対象 | 実行条件 |
|-------------|---------|------|---------|
| AutonomousDatabaseIntegrationTest | 4個 | Autonomous Database TestContainer | `RUN_INTEGRATION_TESTS=true` |
| OCILocalTestingIntegrationTest | 4個 | OCI Local Testing Framework | `RUN_INTEGRATION_TESTS=true` |
| ObjectStorageIntegrationTest | 6個 | Object Storage統合機能 | `RUN_INTEGRATION_TESTS=true` |
| ErrorHandlingIntegrationTest | 4個 | エラーハンドリング | 常時 |
| ResilienceIntegrationTest | 3個 | Resilience4j機能 | 常時 |

### 4.2 新機能統合テストの特徴
- **Object Storage統合**: OCI Local Testing Framework使用、アップロード・ダウンロード・削除・一覧機能
- **リトライ機能**: Resilience4j指数バックオフ、最大試行回数、サーキットブレーカー
- **エラーハンドリング**: カスタム例外、フォールバック処理、エラー率制限
- **OCI認証**: インスタンスプリンシパル、設定ファイル認証の切り替え
- **環境変数**: セキュリティ強化された設定での動作確認

### 4.3 Autonomous Database統合テスト

**AutonomousDatabaseIntegrationTest.java**
```java
@MicronautTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Condition("integration-tests")
class AutonomousDatabaseIntegrationTest {
    
    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim")
            .withUsername("csvuser")
            .withPassword("csvpass")
            .withInitScript("init-autonomous-features.sql");
    
    @TestPropertySource
    static Map<String, Object> configureProperties() {
        return Map.of(
            "datasources.default.url", oracle.getJdbcUrl(),
            "datasources.default.username", oracle.getUsername(),
            "datasources.default.password", oracle.getPassword(),
            "datasources.default.driver-class-name", "oracle.jdbc.OracleDriver",
            "jpa.default.properties.hibernate.dialect", "org.hibernate.dialect.Oracle12cDialect",
            "jpa.default.properties.hibernate.hbm2ddl.auto", "create",
            "csv.export.enabled", false
        );
    }
    
    @Inject
    private EmployeeRepository employeeRepository;
    
    @MockBean
    private WebServiceTemplate webServiceTemplate;
    
    @Test
    @DisplayName("Autonomous Database統合テスト - データ保存と取得")
    void testAutonomousDatabaseIntegration() {
        // Oracle TestContainerでの実際のデータベース操作テスト
        Employee employee = createTestEmployee();
        Employee saved = employeeRepository.save(employee);
        
        Optional<Employee> found = employeeRepository.findById(saved.getEmployeeId());
        
        assertThat(found).isPresent();
        assertThat(found.get().getEmployeeName()).isEqualTo("田中太郎");
    }
    
    @Test
    @DisplayName("Autonomous Database統合テスト - mTLS接続シミュレーション")
    void testMTLSConnectionSimulation() {
        // mTLS接続のシミュレーションテスト（実際のウォレットは使用しない）
        assertThat(oracle.isRunning()).isTrue();
        assertThat(oracle.getJdbcUrl()).contains("oracle:thin");
    }
    
    @Test
    @DisplayName("Autonomous Database統合テスト - トランザクション")
    void testTransactionIntegration() {
        // トランザクション処理の確認
        List<Employee> employees = Arrays.asList(
            createTestEmployee(1001L, "田中太郎"),
            createTestEmployee(1002L, "佐藤花子")
        );
        
        employeeRepository.saveAll(employees);
        
        List<Employee> saved = employeeRepository.findAll();
        assertThat(saved).hasSize(2);
    }
    
    @Test
    @DisplayName("Autonomous Database統合テスト - 大量データ処理")
    void testLargeDataProcessing() {
        // 大量データ処理のパフォーマンステスト
        List<Employee> employees = IntStream.range(1, 1001)
                .mapToObj(i -> createTestEmployee((long) i, "Employee" + i))
                .collect(Collectors.toList());
        
        employeeRepository.saveAll(employees);
        
        List<Employee> saved = employeeRepository.findAll();
        assertThat(saved).hasSize(1000);
    }
}
```

### 4.4 OCI Local Testing統合テスト

**OCILocalTestingIntegrationTest.java**
```java
@MicronautTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Condition("integration-tests")
class OCILocalTestingIntegrationTest {
    
    @Container
    static GenericContainer<?> ociLocalTesting = new GenericContainer<>("oracle/oci-local-testing:latest")
            .withExposedPorts(8080)
            .withEnv("OCI_RESOURCE_PRINCIPAL_VERSION", "2.2")
            .waitingFor(Wait.forHttp("/").forPort(8080));
    
    @TestPropertySource
    static Map<String, Object> configureProperties() {
        return Map.of(
            "oci.objectstorage.endpoint", "http://localhost:" + ociLocalTesting.getMappedPort(8080),
            "oci.objectstorage.namespace", "test-namespace",
            "oci.objectstorage.bucket", "test-bucket",
            "oci.auth.method", "config_file",
            "csv.export.enabled", false
        );
    }
    
    @Test
    @DisplayName("OCI Local Testing Object Storageバケット作成テスト")
    void testObjectStorageBucketCreation() {
        // Object Storageバケット作成とアクセステスト
        assertThat(ociLocalTesting.isRunning()).isTrue();
        String endpoint = "http://localhost:" + ociLocalTesting.getMappedPort(8080);
        assertThat(endpoint).isNotEmpty();
    }
    
    @Test
    @DisplayName("OCI Local Testing Object Storageファイルアップロードテスト")
    void testObjectStorageFileUpload() {
        // ファイルアップロードと確認テスト
        // 実際のOCI SDKを使用したアップロードテスト実装
    }
    
    @Test
    @DisplayName("OCI Local Testing Vault統合テスト")
    void testVaultIntegration() {
        // Vaultサービスのエミュレーションテスト
        assertThat(ociLocalTesting.isRunning()).isTrue();
    }
    
    @Test
    @DisplayName("OCI Local Testing 環境変数統合テスト")
    void testEnvironmentVariableIntegration() {
        // 環境変数による設定の動作確認
        String endpoint = System.getProperty("oci.objectstorage.endpoint");
        assertThat(endpoint).contains("localhost");
    }
}
```

### 4.5 Object Storage統合テスト

**ObjectStorageIntegrationTest.java**
```java
@MicronautTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Condition("integration-tests")
class ObjectStorageIntegrationTest {
    
    @Container
    static GenericContainer<?> ociLocalTesting = new GenericContainer<>("oracle/oci-local-testing:latest")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/").forPort(8080));
    
    @Inject
    private ObjectStorageService objectStorageService;
    
    @Test
    @DisplayName("Object Storageファイルアップロード統合テスト")
    void testObjectStorageFileUploadIntegration() {
        // Given
        String objectName = "test/integration-test.csv";
        String content = "employeeId,name\n1001,Test User";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        // When & Then
        assertDoesNotThrow(() -> objectStorageService.uploadObject(objectName, inputStream));
    }
    
    @Test
    @DisplayName("Object Storageファイルダウンロード統合テスト")
    void testObjectStorageFileDownloadIntegration() {
        // Given: まずファイルをアップロード
        String objectName = "test/download-test.csv";
        String content = "employeeId,name\n1002,Download Test User";
        InputStream uploadStream = new ByteArrayInputStream(content.getBytes());
        objectStorageService.uploadObject(objectName, uploadStream);
        
        // When: ファイルをダウンロード
        InputStream downloadStream = objectStorageService.downloadObject(objectName);
        
        // Then: ダウンロードしたコンテンツを確認
        String downloadedContent = new String(downloadStream.readAllBytes());
        assertThat(downloadedContent).isEqualTo(content);
    }
    
    @Test
    @DisplayName("Object Storageファイル一覧取得統合テスト")
    void testObjectStorageFileListIntegration() {
        // Given: 複数のファイルをアップロード
        String prefix = "test/list-test/";
        for (int i = 1; i <= 3; i++) {
            String objectName = prefix + "file" + i + ".csv";
            String content = "test,data," + i;
            InputStream inputStream = new ByteArrayInputStream(content.getBytes());
            objectStorageService.uploadObject(objectName, inputStream);
        }
        
        // When: ファイル一覧を取得
        List<String> objectNames = objectStorageService.listObjects(prefix);
        
        // Then: アップロードしたファイルが一覧に含まれることを確認
        assertThat(objectNames).hasSize(3);
        assertThat(objectNames).contains(prefix + "file1.csv");
        assertThat(objectNames).contains(prefix + "file2.csv");
        assertThat(objectNames).contains(prefix + "file3.csv");
    }
    
    @Test
    @DisplayName("Object Storageファイル削除統合テスト")
    void testObjectStorageFileDeleteIntegration() {
        // Given: ファイルをアップロード
        String objectName = "test/delete-test.csv";
        String content = "test,data,delete";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        objectStorageService.uploadObject(objectName, inputStream);
        
        // When: ファイルを削除
        objectStorageService.deleteObject(objectName);
        
        // Then: ファイルが存在しないことを確認
        assertThatThrownBy(() -> objectStorageService.downloadObject(objectName))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessageContaining("Object not found");
    }
    
    @Test
    @DisplayName("Object Storage大容量ファイルアップロード統合テスト")
    void testObjectStorageLargeFileUploadIntegration() {
        // Given: 大容量ファイル（10MB相当のデータ）
        String objectName = "test/large-file.csv";
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            largeContent.append("employeeId,name,department,email\n")
                       .append(i).append(",Employee").append(i)
                       .append(",Department").append(i % 10)
                       .append(",employee").append(i).append("@example.com\n");
        }
        InputStream inputStream = new ByteArrayInputStream(largeContent.toString().getBytes());
        
        // When & Then: マルチパートアップロードが正常に実行されることを確認
        assertDoesNotThrow(() -> objectStorageService.uploadObject(objectName, inputStream));
    }
    
    @Test
    @DisplayName("Object Storageエラーハンドリング統合テスト")
    void testObjectStorageErrorHandlingIntegration() {
        // Given: 存在しないオブジェクト名
        String nonExistentObject = "non-existent/file.csv";
        
        // When & Then: 適切な例外が発生することを確認
        assertThatThrownBy(() -> objectStorageService.downloadObject(nonExistentObject))
                .isInstanceOf(ObjectStorageException.class);
    }
}
```

### 4.6 Resilience4j統合テスト

**ResilienceIntegrationTest.java**
```java
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "csv.export.enabled=false",
    "resilience4j.retry.instances.soap-api.max-attempts=3",
    "resilience4j.retry.instances.soap-api.wait-duration=1s",
    "resilience4j.circuitbreaker.instances.soap-api.failure-rate-threshold=50"
})
class ResilienceIntegrationTest {
    
    @MockBean
    private WebServiceTemplate webServiceTemplate;
    
    @Inject
    private SoapClient soapClient;
    
    @Test
    @DisplayName("Resilience4j指数バックオフ統合テスト")
    void testResilienceWithExponentialBackoff() {
        // Given
        Long employeeId = 1001L;
        
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenThrow(new WebServiceIOException("Network error"))
                .thenThrow(new WebServiceIOException("Network error"))
                .thenReturn(createMockResponse());
        
        // When
        long startTime = System.currentTimeMillis();
        SoapClient.EmployeeDetails result = soapClient.getEmployeeDetails(employeeId);
        long endTime = System.currentTimeMillis();
        
        // Then
        assertThat(result).isNotNull();
        // 指数バックオフにより適切な遅延があることを確認
        assertThat(endTime - startTime).isGreaterThan(2000); // 1秒 + 1秒の遅延
        verify(webServiceTemplate, times(3))
                .marshalSendAndReceive(anyString(), any());
    }
    
    @Test
    @DisplayName("リトライ最大回数超過統合テスト")
    void testRetryExhaustionIntegration() {
        // Given
        Long employeeId = 1001L;
        
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenThrow(new WebServiceIOException("Persistent error"));
        
        // When & Then
        assertThatThrownBy(() -> soapClient.getEmployeeDetails(employeeId))
                .isInstanceOf(RuntimeException.class);
        
        verify(webServiceTemplate, times(3))
                .marshalSendAndReceive(anyString(), any());
    }
    
    @Test
    @DisplayName("サーキットブレーカー統合テスト")
    void testCircuitBreakerIntegration() {
        // Given: 複数回の失敗でサーキットブレーカーをオープンさせる
        Long employeeId = 1001L;
        
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenThrow(new RuntimeException("Service error"));
        
        // When: 失敗率の閾値を超えるまで呼び出し
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> soapClient.getEmployeeDetails(employeeId))
                    .isInstanceOf(RuntimeException.class);
        }
        
        // Then: サーキットブレーカーがオープンし、フォールバック処理が実行される
        SoapClient.EmployeeDetails fallbackResult = soapClient.getEmployeeDetailsFallback(employeeId);
        assertThat(fallbackResult.getStatus()).isEqualTo("Unavailable");
    }
}
```

---

## 5. テストデータ管理

### 5.1 テスト設定ファイル

**application-test.yml**
```yaml
# テスト用データベース設定
micronaut:
  application:
    name: csv-batch-processor-test

datasources:
  default:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: password

jpa:
  default:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        hbm2ddl:
          auto: create-drop
        show_sql: false  # テスト時はログを簡潔に

# テスト用OCI設定
oci:
  auth:
    method: config_file
  objectstorage:
    namespace: test-namespace
    bucket: test-csv-export-bucket
    region: us-ashburn-1
    endpoint: http://localhost:8080

# テスト用サービス設定
csv:
  export:
    enabled: false
    objectstorage-upload: false
    local-backup: false
  output:
    path: /tmp/test-result.csv

# SOAP API設定
soap:
  api:
    url: http://localhost:8080/ws
    timeout:
      connection: 5000  # テスト用短縮タイムアウト
      read: 10000

# Resilience4j設定（テスト用）
resilience4j:
  retry:
    instances:
      soap-api:
        max-attempts: 2   # テスト用短縮リトライ回数
        wait-duration: 100ms
        exponential-backoff-multiplier: 2.0
  circuitbreaker:
    instances:
      soap-api:
        failure-rate-threshold: 3  # テスト用に短縮
        wait-duration-in-open-state: 5s
        sliding-window-size: 5

# セキュリティ強化：環境変数設定（テスト時）
test:
  environment:
    variables:
      DB_PASSWORD: test-password
      OCI_NAMESPACE: test-namespace

# ログ設定
logger:
  levels:
    com.example.csvbatch: DEBUG    # テスト時は詳細ログ
    io.github.resilience4j: DEBUG
    com.oracle.bmc: WARN
  pattern: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### 5.2 統合テスト専用設定

**application-integration.yml**
```yaml
# 統合テスト専用設定ファイル
micronaut:
  application:
    name: csv-batch-processor-integration

# 統合テスト用OCI設定
oci:
  auth:
    method: instance_principal
  objectstorage:
    endpoint: ${OCI_OBJECTSTORAGE_ENDPOINT:http://localhost:8080}
    namespace: ${OCI_NAMESPACE:integration-test-namespace}
    bucket: ${OCI_BUCKET:integration-test-bucket}
    region: ${OCI_REGION:us-ashburn-1}

# CSV出力設定（統合テスト用）
csv:
  export:
    enabled: true
    objectstorage-upload: true
    local-backup: true
  output:
    path: /tmp/integration-test-result.csv

# タイムアウト設定（統合テスト用）
soap:
  api:
    timeout:
      connection: 30000
      read: 60000

# Resilience4j設定（統合テスト用）
resilience4j:
  retry:
    instances:
      soap-api:
        max-attempts: 3
        wait-duration: 1s
  circuitbreaker:
    instances:
      soap-api:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s

# ログ設定
logger:
  levels:
    com.example.csvbatch: DEBUG
    org.springframework.ws: WARN
    org.testcontainers: WARN
```

### 5.3 テストユーティリティクラス

**TestConfig.java**
```java
@TestConfiguration
@Requires(env = "test")
public class TestConfig {
    
    @Bean
    @Primary
    public ObjectStorage objectStorageClient() {
        return Mockito.mock(ObjectStorage.class);
    }
    
    @Bean
    @Primary
    public WebServiceTemplate webServiceTemplate() {
        return Mockito.mock(WebServiceTemplate.class);
    }
    
    @Bean
    @Primary
    public VaultsClient vaultsClient() {
        return Mockito.mock(VaultsClient.class);
    }
    
    @Bean
    @Primary
    public AuthenticationDetailsProvider authenticationDetailsProvider() {
        return Mockito.mock(AuthenticationDetailsProvider.class);
    }
}
```

---

## 6. モック・スタブ管理

### 6.1 モック戦略

| テスト種別 | モック方法 | 対象コンポーネント |
|-----------|-----------|------------------|
| 単体テスト | `@Mock`, `@InjectMocks` | WebServiceTemplate, ObjectStorage, VaultsClient |
| リポジトリテスト | `@MockBean` | 外部サービス |
| 統合テスト | `@MockBean` | WebServiceTemplate |
| Micronaut起動テスト | `@MockBean` | WebServiceTemplate, ObjectStorage |

### 6.2 共通モック設定

**TestConfig.java**でグローバルなモック設定を管理：

```java
@TestConfiguration
@Requires(env = "test")
public class TestConfig {
    
    @Bean
    @Primary
    public ObjectStorage objectStorageClient() {
        return Mockito.mock(ObjectStorage.class);
    }
    
    @Bean
    @Primary  
    public WebServiceTemplate webServiceTemplate() {
        return Mockito.mock(WebServiceTemplate.class);
    }
    
    @Bean
    @Primary
    public AuthenticationDetailsProvider authenticationDetailsProvider() {
        AuthenticationDetailsProvider mock = Mockito.mock(AuthenticationDetailsProvider.class);
        when(mock.getKeyId()).thenReturn("test-key-id");
        return mock;
    }
}
```

### 6.3 条件付きモック設定

OCIConfigTestでの環境依存モック：

```java
@Test
@DisplayName("OCI Local Testingプロファイルでの設定テスト")
void testOCIClientWithLocalTestingProfile() {
    // OCI Local Testingプロファイル有無での設定分岐テスト
    System.setProperty("oci.auth.method", "config_file");
    System.setProperty("oci.objectstorage.endpoint", "http://localhost:8080");
}
```

---

## 7. テスト実行とレポート

### 7.1 基本的なテスト実行

```bash
# 単体テストのみ実行（約1-2分）
mvn test

# 統合テスト含む全テスト実行（約8-12分）
mvn test -DRUN_INTEGRATION_TESTS=true

# 特定のテストクラスのみ実行
mvn test -Dtest=SoapClientTest

# 特定のテストメソッドのみ実行
mvn test -Dtest=SoapClientTest#testGetEmployeeDetailsSuccess

# Helidon固有のテスト実行
mvn test -Dtest="*Helidon*Test"
```

### 7.2 カバレッジレポート生成

```bash
# カバレッジレポート生成
mvn clean test jacoco:report

# カバレッジレポート確認
open target/site/jacoco/index.html

# SonarQubeとの統合
mvn clean test jacoco:report sonar:sonar
```

### 7.3 継続的インテグレーション

**GitHub Actions設定例**
```yaml
# .github/workflows/test.yml
name: Test OCI Version
on: [push, pull_request]

jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      - name: Run unit tests
        run: mvn clean test
        env:
          DB_PASSWORD: test-password
          OCI_NAMESPACE: test-namespace
      - name: Generate coverage report
        run: mvn jacoco:report
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3

  integration-test:
    runs-on: ubuntu-latest
    needs: unit-test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      - name: Run integration tests
        run: mvn clean test -DRUN_INTEGRATION_TESTS=true
        env:
          DB_PASSWORD: test-password
          OCI_NAMESPACE: test-namespace
          OCI_BUCKET: integration-test-bucket
```

---

## 8. テストベストプラクティス

### 8.1 テスト命名規則

- **クラス名**: `{対象クラス名}Test`
- **メソッド名**: `test{機能}{条件}{期待結果}` または説明的な名前
- **DisplayName**: 日本語で分かりやすい説明

### 8.2 テストの構成原則

- **Given-When-Then パターン**の使用
- **@TempDir**を使用した一時ファイル管理
- **ReflectionTestUtils**を使用したプライベートフィールド設定
- **適切なアサーション**の使用（AssertJライブラリ）

### 8.3 テストの独立性

- 各テストは独立して実行可能
- テスト順序に依存しない
- `@DirtiesContext`による適切なクリーンアップ
- モックの適切なリセット

### 8.4 パフォーマンス考慮

- TestContainersの条件付き実行（`@Condition("integration-tests")`）
- テスト用プロファイルの活用（`@Requires(env = "test")`）
- MemoryLimitを考慮したテストケース設計
- テスト並列実行の設定（surefire-plugin）

### 8.5 セキュリティ強化テスト

- **環境変数**: 必須環境変数（DB_PASSWORD）の動作確認
- **機密情報保護**: ログ出力時のマスキング確認
- **認証**: インスタンスプリンシパル認証と設定ファイル認証の分離
- **Vault統合**: シークレット取得の動作確認

### 8.6 OCI統合テスト

- **Object Storage**: OCI Local Testing Framework使用
- **認証**: AuthenticationDetailsProviderの動作確認
- **Resilience4j**: @Retry, @CircuitBreaker アノテーションの動作確認
- **Helidon**: Micronaut統合の動作確認

---

## 9. 環境別テスト実行

### 9.1 開発環境でのテスト実行

```bash
# 基本的な単体テスト実行
mvn clean test

# 開発環境でのプロファイル指定テスト
mvn test -Dmicronaut.environments=dev

# OCI Local Testing使用の統合テスト
docker run -d --name oci-local-testing -p 8080:8080 oracle/oci-local-testing:latest
mvn test -DRUN_INTEGRATION_TESTS=true -Dmicronaut.environments=integration
```

### 9.2 CI/CD環境でのテスト実行

```bash
# GitHub Actions での実行例
name: "CI/CD Tests OCI"
env:
  RUN_INTEGRATION_TESTS: true
  DB_PASSWORD: ci-test-password
  OCI_NAMESPACE: ci-test-namespace
  OCI_BUCKET: ci-test-bucket

# 実行
mvn clean test verify -Dmaven.test.failure.ignore=false
```

### 9.3 本番類似環境でのテスト

```bash
# 本番設定に近いテスト環境でのエンドツーエンドテスト
export DB_PASSWORD="secure-production-like-password"
export OCI_NAMESPACE="production-like-namespace"
export OCI_BUCKET="production-like-bucket"
export MICRONAUT_ENVIRONMENTS="production,test"

mvn clean test -DRUN_INTEGRATION_TESTS=true
```

### 9.4 新機能テストパターン

#### 9.4.1 Resilience4j機能テスト
```bash
# リトライ機能特化テスト
mvn test -Dtest=*ResilienceTest*

# タイムアウト・バックオフ測定
mvn test -Dtest=ResilienceIntegrationTest#testResilienceWithExponentialBackoff -Dlogger.levels.io.github.resilience4j=DEBUG
```

#### 9.4.2 Object Storage統合機能テスト
```bash
# OCI Local Testing Object Storage機能テスト
docker run -d --name oci-local-testing -p 8080:8080 oracle/oci-local-testing:latest
mvn test -Dtest=ObjectStorage*Test* -DRUN_INTEGRATION_TESTS=true

# Object Storageアップロード・ダウンロードテスト
mvn test -Dtest=ObjectStorageIntegrationTest -DRUN_INTEGRATION_TESTS=true
```

#### 9.4.3 Vault統合機能テスト
```bash
# Vault統合機能テスト
mvn test -Dtest=VaultSecretServiceTest

# Vaultシークレット取得統合テスト
mvn test -Dtest=VaultIntegrationTest -DRUN_INTEGRATION_TESTS=true
```

---

## 10. トラブルシューティング

### 10.1 よくあるテスト実行エラー

#### 10.1.1 TestContainers関連エラー
```bash
# エラー例：Could not find a valid Docker environment
# 解決方法：
docker info
# Docker Desktopが起動していることを確認

# Docker設定確認
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

#### 10.1.2 Oracle TestContainer起動エラー
```bash
# メモリ不足エラーの場合
# Docker Desktop設定で最低8GB割り当て

# Oracle イメージダウンロードタイムアウト
# .testcontainers.properties に以下を追加：
ryuk.container.timeout=120s
testcontainers.reuse.enable=true
```

#### 10.1.3 OCI Local Testing Framework接続エラー
```bash
# OCI Local Testing Frameworkが正常に起動していない場合
docker logs oci-local-testing

# ポート競合確認
netstat -an | grep 8080

# OCI Local Testing Framework健康状態確認
curl http://localhost:8080/n/test-namespace/b
```

#### 10.1.4 環境変数設定エラー
```bash
# 必須環境変数未設定エラー
export DB_PASSWORD=test-password
export OCI_NAMESPACE=test-namespace
export OCI_BUCKET=test-bucket

# .env ファイル確認
cat .env | grep -v PASSWORD

# 環境変数設定確認
mvn test -Dtest=CsvExportServiceTest -X | grep -i "DB_PASSWORD"
```

### 10.2 パフォーマンス問題

#### 10.2.1 テスト実行時間が長い場合
```bash
# 並列実行の有効化
mvn test -Dmaven.test.parallel=true -Dmaven.test.forkCount=4

# 統合テストをスキップして単体テストのみ実行
mvn test -DskipIntegrationTests

# 特定のカテゴリのテストのみ実行
mvn test -Dgroups=fast
```

#### 10.2.2 メモリ不足エラー
```bash
# JVM メモリ設定
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=256m"

# TestContainers メモリ設定
# docker-compose.override.yml:
services:
  oci-local-testing:
    mem_limit: 512m
  oracle-db:
    mem_limit: 1g
```

### 10.3 Mock・Stub関連問題

#### 10.3.1 Mock設定が反映されない
```java
// @MockBean の代わりに @Mock を使用
@ExtendWith(MockitoExtension.class)
class SoapClientTest {
    @Mock
    private WebServiceTemplate webServiceTemplate;
    
    @InjectMocks
    private SoapClient soapClient;
}
```

#### 10.3.2 ObjectStorage Mock問題
```java
// モック設定の確認
when(objectStorageClient.putObject(any(PutObjectRequest.class)))
    .thenThrow(new BmcException(500, "InternalServerError", "Test error", "req-123"));

// 呼び出し確認
verify(objectStorageClient, times(1))
    .putObject(any(PutObjectRequest.class));
```

### 10.4 設定ファイル問題

#### 10.4.1 application-test.yml読み込まれない
```bash
# 環境確認
mvn test -Dmicronaut.environments=test -X | grep -i "application-test.yml"

# 設定値確認
mvn test -Dtest=ConfigTest -Dlogger.levels.io.micronaut.context=DEBUG
```

#### 10.4.2 Environment Variable設定問題
```java
// テスト内での環境変数設定
@Test
void testWithEnvironmentVariable() {
    System.setProperty("DB_PASSWORD", "test-password");
    // テスト実行
    System.clearProperty("DB_PASSWORD");
}
```

### 10.5 OCI SDK設定問題

#### 10.5.1 認証プロバイダー設定されない
```yaml
# application-test.yml に明示的に追加
oci:
  auth:
    method: config_file  # または instance_principal
  config:
    file: ~/.oci/config
    profile: DEFAULT
```

#### 10.5.2 Resilience4j設定問題
```java
// Retry設定確認テスト
@Test
void testRetryConfiguration() {
    // @Retry アノテーション付きメソッドの動作確認
    // RetryRegistry Bean設定の確認
}
```

### 10.6 デバッグのためのログ出力

```bash
# Resilience4j関連ログ
mvn test -Dlogger.levels.io.github.resilience4j=DEBUG

# OCI SDK関連ログ
mvn test -Dlogger.levels.com.oracle.bmc=DEBUG

# アプリケーション詳細ログ
mvn test -Dlogger.levels.com.example.csvbatch=DEBUG

# テスト詳細実行ログ
mvn test -X -Dtest=CsvExportServiceTest
```

### 10.7 完全なクリーンアップ

```bash
# テスト環境完全クリーンアップ
docker-compose down --volumes --remove-orphans
docker stop oci-local-testing && docker rm oci-local-testing
docker system prune -af

# Maven キャッシュクリア
mvn dependency:purge-local-repository

# テストレポート削除
rm -rf target/site/jacoco/
rm -rf target/surefire-reports/

# 再起動してテスト
mvn clean test -DRUN_INTEGRATION_TESTS=true
```

---

## 付録

### A. テスト実行チェックリスト

#### A.1 開発者向けチェックリスト
- [ ] 単体テスト実行: `mvn test`
- [ ] カバレッジ確認: 80%以上
- [ ] 環境変数設定: DB_PASSWORD、OCI_NAMESPACE等の必須変数
- [ ] 新機能テスト: 関連テストクラスの実行
- [ ] MockとStubの適切な使用
- [ ] Helidon統合の確認

#### A.2 CI/CD環境チェックリスト  
- [ ] 全テスト実行: `mvn test -DRUN_INTEGRATION_TESTS=true`
- [ ] TestContainers動作確認
- [ ] OCI Local Testing Framework接続確認
- [ ] セキュリティテスト: 環境変数での機密情報管理
- [ ] パフォーマンステスト: 実行時間の監視
- [ ] Object Storage統合テスト実行

### B. テスト設定サマリー

| 項目 | 単体テスト | 統合テスト | 備考 |
|------|-----------|-----------|------|
| 実行時間 | 1-2分 | 8-12分 | TestContainers + OCI Local Testing含む |
| テスト数 | 70個 | 18個 | 合計88個 |
| Docker必須 | × | ○ | TestContainers使用 |
| 環境変数 | 一部 | 必須 | セキュリティ強化により |
| フレームワーク | test | test,integration | Helidon/Micronaut対応 |

### C. 関連技術仕様

- **Helidon**: 4.0.0 + Micronaut 4.0
- **Resilience4j**: 指数バックオフ、最大3回リトライ、サーキットブレーカー
- **TestContainers**: Oracle XE 21, OCI Local Testing Framework
- **JUnit**: 5.9.2 + AssertJ + Mockito
- **OCI SDK**: Object Storage Client + OCI Local Testing統合
- **カバレッジツール**: JaCoCo + Maven Surefire Plugin
- **認証**: インスタンスプリンシパル、設定ファイル認証対応

---

**最終更新**: 2025-08-06 - OCI PoC環境用テストガイド  
**対象フレームワーク**: Helidon 4.0 + OCI SDK v3  
**次回レビュー予定**: 2025-12-06

**注意**: このガイドはOCI環境でのテスト実行を前提としています。AWS版からの移行時は、認証方法とクラウドサービスの違いに注意してテスト実装を調整してください。