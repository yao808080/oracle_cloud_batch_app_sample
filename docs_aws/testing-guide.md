# テストガイド

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
- **テストフレームワーク**: JUnit 5, Mockito, Spring Boot Test, Testcontainers
- **クラウドサービステスト**: AWS LocalStackでのS3エミュレーション
- **新機能テスト**: Spring Retry、サーキットブレーカー、S3統合、例外処理
- **セキュリティテスト**: 環境変数化された設定のテスト

### 1.2 テスト分類
| テスト種別 | 対象 | 実行環境 | 実行時間目安 | テスト数 |
|-----------|------|---------|-------------|---------|
| 単体テスト | クラス単位、サービス、例外処理 | H2インメモリDB | 1-2分 | 65個 |
| 統合テスト | DB、S3、リトライ、エラーハンドリング | TestContainers + LocalStack | 3-5分 | 15個 |
| 合計 | 全体 | - | 5-10分 | 80個 |

### 1.3 新機能のテスト対象
- **例外処理**: CsvProcessingException、DataProcessingException、S3UploadException
- **S3統合**: S3ClientService、アップロード・ダウンロード・削除機能
- **リトライ機能**: Spring Retry、指数バックオフ、最大試行回数
- **サーキットブレーカー**: 障害時のフォールバック処理
- **環境変数設定**: セキュリティ強化された設定の動作確認

### 1.4 テスト実行制御
- **通常実行**: `mvn test` - 65個のテスト（統合テストを除く）
- **完全実行**: `mvn test -DRUN_INTEGRATION_TESTS=true` - 80個すべてのテスト
- **セキュリティ考慮**: テストでは環境変数未設定でもデフォルト値で動作

---

## 2. テスト環境構築

### 2.1 前提条件
```bash
# 必要なソフトウェア
Java 21+
Maven 3.8+
Docker Desktop（統合テスト用）

# バージョン確認
java -version
mvn -version
docker --version
```

### 2.2 基本テスト実行
```bash
# 単体テストのみ実行（65個のテスト）
mvn test

# 全テスト実行（80個のテスト、統合テスト含む）
mvn test -DRUN_INTEGRATION_TESTS=true

# カバレッジレポート付きテスト実行
mvn clean test jacoco:report

# Spring Retry機能のテスト実行
mvn test -Dtest="*RetryIntegrationTest"

# S3統合機能のテスト実行
mvn test -Dtest="*S3*Test" -DRUN_INTEGRATION_TESTS=true
```

### 2.3 Docker環境でのテスト
```bash
# テスト用Docker環境起動
docker-compose -f docker-compose.test.yml up -d

# E2Eテスト実行
docker-compose -f docker-compose.test.yml exec csv-batch-processor mvn test

# テスト用Docker環境停止
docker-compose -f docker-compose.test.yml down
```

---

## 3. 単体テスト仕様

### 3.1 実際のテストクラス構成
```
src/test/java/com/example/csvbatch/
├── CsvBatchProcessorApplicationTest.java     # Spring Boot起動テスト（1メソッド）
├── TestConfig.java                          # テスト用設定クラス
├── client/
│   └── SoapClientTest.java                  # SOAP通信、リトライ、サーキットブレーカーテスト（8メソッド）
├── config/
│   ├── AwsConfigTest.java                   # AWS設定、環境依存設定テスト（8メソッド）
│   ├── DataInitializerTest.java             # データ初期化テスト（4メソッド）
│   └── WebServiceConfigTest.java            # Webサービス設定テスト（4メソッド）
├── dto/
│   └── EmployeeCsvDataTest.java             # DTOテスト（4メソッド）
├── entity/
│   └── EmployeeTest.java                    # エンティティテスト（4メソッド）
├── exception/                               # 新規追加：例外処理テスト
│   ├── CsvProcessingExceptionTest.java      # CSV処理例外テスト（3メソッド）
│   ├── DataProcessingExceptionTest.java     # データ処理例外テスト（3メソッド）
│   └── S3UploadExceptionTest.java           # S3アップロード例外テスト（3メソッド）
├── integration/                             # 統合テスト
│   ├── DatabaseIntegrationTest.java         # Oracle DB統合テスト（3メソッド）
│   ├── ErrorHandlingIntegrationTest.java    # エラーハンドリング統合テスト（4メソッド）
│   ├── LocalStackIntegrationTest.java       # LocalStack統合テスト（4メソッド）
│   ├── RetryIntegrationTest.java            # リトライ統合テスト（3メソッド）
│   └── S3IntegrationTest.java               # S3統合テスト（6メソッド）
├── repository/
│   └── EmployeeRepositoryTest.java          # リポジトリテスト（4メソッド）
└── service/
    ├── CsvExportServiceTest.java            # CSV出力サービステスト（8メソッド）
    └── S3ClientServiceTest.java             # S3クライアントサービステスト（6メソッド）
```

### 3.2 各テストクラスの詳細実装

#### 3.2.1 アプリケーションテスト

**CsvBatchProcessorApplicationTest.java**
```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "csv.export.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
class CsvBatchProcessorApplicationTest {
    
    @MockBean
    private WebServiceTemplate webServiceTemplate;
    
    @MockBean
    private S3ClientService s3ClientService;
    
    @Test
    @DisplayName("Spring Boot アプリケーションコンテキスト読み込みテスト")
    void contextLoads() {
        // Spring Bootコンテキストが正常に起動することを確認
        // Spring Retry、サーキットブレーカー、新しい例外クラスも含む
    }
}
```

#### 3.2.2 エンティティテスト

**EmployeeTest.java**
```java
class EmployeeTest {
    
    @Test
    @DisplayName("Employeeエンティティのビルダーパターンテスト")
    void testEmployeeBuilder() {
        Employee employee = Employee.builder()
                .employeeId(1001L)
                .employeeName("田中太郎")
                .department("開発部")
                .email("tanaka@example.com")
                .hireDate(LocalDate.of(2020, 4, 1))
                .salary(new BigDecimal("500000"))
                .build();
                
        assertThat(employee.getEmployeeId()).isEqualTo(1001L);
        assertThat(employee.getEmployeeName()).isEqualTo("田中太郎");
        assertThat(employee.getDepartment()).isEqualTo("開発部");
    }
    
    @Test
    @DisplayName("Employee等価性テスト")
    void testEmployeeEquality() {
        Employee employee1 = createTestEmployee();
        Employee employee2 = createTestEmployee();
        
        assertThat(employee1).isEqualTo(employee2);
        assertThat(employee1.hashCode()).isEqualTo(employee2.hashCode());
    }
}
```

#### 3.2.3 リポジトリテスト

**EmployeeRepositoryTest.java**
```java
@DataJpaTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class EmployeeRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @Test
    @DisplayName("全従業員データ取得テスト")
    void testFindAllEmployees() {
        // Given
        Employee employee = createTestEmployee(1001L, "田中太郎");
        entityManager.persistAndFlush(employee);
        
        // When
        List<Employee> employees = employeeRepository.findAll();
        
        // Then
        assertThat(employees).hasSize(1);
        assertThat(employees.get(0).getEmployeeName()).isEqualTo("田中太郎");
    }
    
    @Test
    @DisplayName("ID順ソート取得テスト")
    void testFindAllOrderByEmployeeId() {
        // 複数の従業員データでソート順を確認するテスト実装
    }
}
```

#### 3.2.4 新規例外クラステスト

**CsvProcessingExceptionTest.java**
```java
class CsvProcessingExceptionTest {
    
    @Test
    @DisplayName("メッセージのみの例外作成テスト")
    void testCreateWithMessage() {
        String message = "CSV処理エラーが発生しました";
        CsvProcessingException exception = new CsvProcessingException(message);
        
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    @DisplayName("メッセージと原因の例外作成テスト")
    void testCreateWithMessageAndCause() {
        String message = "CSV処理エラー";
        IOException cause = new IOException("ファイル書き込みエラー");
        
        CsvProcessingException exception = new CsvProcessingException(message, cause);
        
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    @DisplayName("例外の継承関係テスト")
    void testExceptionHierarchy() {
        CsvProcessingException exception = new CsvProcessingException("test");
        
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
```

#### 3.2.5 S3クライアントサービステスト

**S3ClientServiceTest.java**
```java
@ExtendWith(MockitoExtension.class)
class S3ClientServiceTest {
    
    @Mock
    private S3Client s3Client;
    
    @InjectMocks
    private S3ClientService s3ClientService;
    
    @Test
    @DisplayName("S3ファイルアップロード成功テスト")
    void testUploadFileSuccess() throws Exception {
        // Given
        String key = "test/file.csv";
        String content = "test,data\n1,test";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);
        
        // When & Then
        assertDoesNotThrow(() -> s3ClientService.uploadFile(key, inputStream));
        
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    @DisplayName("S3ファイルアップロード失敗時の例外処理テスト")
    void testUploadFileFailure() {
        // Given
        String key = "test/file.csv";
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.builder().message("S3エラー").build());
        
        // When & Then
        assertThatThrownBy(() -> s3ClientService.uploadFile(key, inputStream))
                .isInstanceOf(S3UploadException.class)
                .hasMessageContaining("S3アップロードに失敗しました");
    }
}
```

#### 3.2.6 強化されたCSVサービステスト

**CsvExportServiceTest.java**
```java
@ExtendWith(MockitoExtension.class)
class CsvExportServiceTest {
    
    @Mock
    private EmployeeRepository employeeRepository;
    
    @Mock
    private SoapClient soapClient;
    
    @Mock
    private S3ClientService s3ClientService;
    
    @InjectMocks
    private CsvExportService csvExportService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(csvExportService, "csvOutputPath", 
                                   tempDir.resolve("test.csv").toString());
        ReflectionTestUtils.setField(csvExportService, "s3UploadEnabled", false);
        ReflectionTestUtils.setField(csvExportService, "localBackupEnabled", true);
    }
    
    @Test
    @DisplayName("CSV出力処理正常系テスト")
    void testExportEmployeesToCsvSuccess() throws Exception {
        // CSV出力の基本機能テスト
    }
    
    @Test
    @DisplayName("S3アップロード付きCSV出力テスト")
    void testExportEmployeesToCsvWithS3Upload() throws Exception {
        // Given
        ReflectionTestUtils.setField(csvExportService, "s3UploadEnabled", true);
        List<Employee> employees = Arrays.asList(createTestEmployee());
        SoapClient.EmployeeDetails details = createTestEmployeeDetails();
        
        when(employeeRepository.findAll()).thenReturn(employees);
        when(soapClient.getEmployeeDetails(anyLong())).thenReturn(details);
        
        // When
        csvExportService.exportEmployeesToCsv();
        
        // Then
        verify(s3ClientService).uploadFile(anyString(), any(InputStream.class));
    }
    
    @Test
    @DisplayName("SOAP APIエラー時のフォールバック処理テスト")
    void testSoapApiErrorFallback() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(createTestEmployee());
        
        when(employeeRepository.findAll()).thenReturn(employees);
        when(soapClient.getEmployeeDetails(anyLong()))
                .thenThrow(new RuntimeException("SOAP API エラー"));
        
        // When & Then
        assertDoesNotThrow(() -> csvExportService.exportEmployeesToCsv());
        
        // フォールバック値でCSVが生成されることを確認
        Path csvFile = tempDir.resolve("test.csv");
        assertThat(csvFile).exists();
    }
    
    @Test
    @DisplayName("大量データ処理時のエラー率制限テスト")
    void testErrorRateLimit() throws Exception {
        // エラー率が50%を超えた場合の処理中断テスト
    }
}
```

#### 3.2.7 強化されたSOAPクライアントテスト

**SoapClientTest.java**
```java
@ExtendWith(MockitoExtension.class)
class SoapClientTest {
    
    @Mock
    private WebServiceTemplate webServiceTemplate;
    
    @InjectMocks
    private SoapClient soapClient;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(soapClient, "soapApiUrl", 
                                   "http://localhost:8080/ws");
    }
    
    @Test
    @DisplayName("SOAP API呼び出し成功テスト")
    void testGetEmployeeDetailsSuccess() {
        // Given
        Long employeeId = 1001L;
        GetEmployeeDetailsResponse mockResponse = createMockResponse();
        
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenReturn(mockResponse);
        
        // When
        SoapClient.EmployeeDetails result = soapClient.getEmployeeDetails(employeeId);
        
        // Then
        assertThat(result.getLevel()).isEqualTo("Senior");
        assertThat(result.getBonus()).isEqualTo(new BigDecimal("150000"));
        assertThat(result.getStatus()).isEqualTo("Active");
    }
    
    @Test
    @DisplayName("SOAP API WebServiceIOException時のリトライテスト")
    void testGetEmployeeDetailsRetryOnWebServiceIOException() {
        // Given
        Long employeeId = 1001L;
        
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenThrow(new WebServiceIOException("接続エラー"))
                .thenThrow(new WebServiceIOException("接続エラー"))
                .thenReturn(createMockResponse());
        
        // When
        SoapClient.EmployeeDetails result = soapClient.getEmployeeDetails(employeeId);
        
        // Then
        assertThat(result).isNotNull();
        verify(webServiceTemplate, times(3))
                .marshalSendAndReceive(anyString(), any());
    }
    
    @Test
    @DisplayName("サーキットブレーカーのフォールバック処理テスト")
    void testCircuitBreakerFallback() {
        // Given
        Long employeeId = 1001L;
        
        // サーキットブレーカーがオープンした状態をシミュレート
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenThrow(new RuntimeException("サーキットブレーカーオープン"));
        
        // When
        SoapClient.EmployeeDetails result = soapClient.getEmployeeDetailsFallback(employeeId);
        
        // Then
        assertThat(result.getLevel()).isEqualTo("Unknown");
        assertThat(result.getBonus()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.getStatus()).isEqualTo("Unavailable");
    }
    
    @Test
    @DisplayName("リトライ最大回数超過時の例外処理テスト")
    void testRetryExhaustion() {
        // Given
        Long employeeId = 1001L;
        
        when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
                .thenThrow(new WebServiceIOException("継続的な接続エラー"));
        
        // When & Then
        assertThatThrownBy(() -> soapClient.getEmployeeDetails(employeeId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get employee details");
        
        // 最大3回リトライされることを確認
        verify(webServiceTemplate, times(3))
                .marshalSendAndReceive(anyString(), any());
    }
}
```

---

## 4. 統合テスト仕様

### 4.1 統合テスト一覧

| テストクラス | テスト数 | 対象 | 実行条件 |
|-------------|---------|------|---------|
| DatabaseIntegrationTest | 3個 | Oracle TestContainer | `RUN_INTEGRATION_TESTS=true` |
| LocalStackIntegrationTest | 4個 | AWS LocalStack | `RUN_INTEGRATION_TESTS=true` |
| S3IntegrationTest | 6個 | S3統合機能 | `RUN_INTEGRATION_TESTS=true` |
| ErrorHandlingIntegrationTest | 4個 | エラーハンドリング | 常時 |
| RetryIntegrationTest | 3個 | Spring Retry機能 | 常時 |

### 4.2 新機能統合テストの特徴
- **S3統合**: LocalStack使用、アップロード・ダウンロード・削除・一覧機能
- **リトライ機能**: 指数バックオフ、最大試行回数、サーキットブレーカー
- **エラーハンドリング**: カスタム例外、フォールバック処理、エラー率制限
- **環境変数**: セキュリティ強化された設定での動作確認

### 4.3 Oracle Database統合テスト

**DatabaseIntegrationTest.java**
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "RUN_INTEGRATION_TESTS", matches = "true")
class DatabaseIntegrationTest {
    
    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim")
            .withUsername("csvuser")
            .withPassword("csvpass");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.Oracle12cDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("csv.export.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @MockBean
    private WebServiceTemplate webServiceTemplate;
    
    @Test
    @DisplayName("データベース統合テスト - データ保存と取得")
    void testDatabaseIntegration() {
        // Oracle TestContainerでの実際のデータベース操作テスト
    }
    
    @Test
    @DisplayName("データベース統合テスト - 複数データの処理")
    void testMultipleEmployeesIntegration() {
        // 複数レコードの操作テスト
    }
    
    @Test
    @DisplayName("データベース統合テスト - トランザクション")
    void testTransactionIntegration() {
        // トランザクション処理の確認
    }
}
```

### 4.4 AWS LocalStack統合テスト

**LocalStackIntegrationTest.java**
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "RUN_INTEGRATION_TESTS", matches = "true")
class LocalStackIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.S3);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.default-region", () -> "us-east-1");
        registry.add("aws.access-key-id", () -> "dummy");
        registry.add("aws.secret-access-key", () -> "dummy");
        registry.add("csv.export.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }
    
    @Test
    @DisplayName("LocalStack S3バケット作成テスト")
    void testS3BucketCreation() {
        // S3バケット作成とアクセステスト
    }
    
    @Test
    @DisplayName("LocalStack S3ファイルアップロードテスト")
    void testS3FileUpload() {
        // ファイルアップロードと確認テスト
    }
    
    @Test
    @DisplayName("LocalStack S3設定確認テスト")
    void testS3Configuration() {
        // LocalStack環境での設定動作確認
    }
    
    @Test
    @DisplayName("LocalStack環境変数統合テスト")
    void testEnvironmentVariableIntegration() {
        // 環境変数による設定の動作確認
    }
}
```

### 4.5 S3統合テスト

**S3IntegrationTest.java**
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "RUN_INTEGRATION_TESTS", matches = "true")
class S3IntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.S3);
    
    @Autowired
    private S3ClientService s3ClientService;
    
    @Test
    @DisplayName("S3ファイルアップロード統合テスト")
    void testS3FileUploadIntegration() {
        // Given
        String key = "test/integration-test.csv";
        String content = "employeeId,name\n1001,Test User";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        // When & Then
        assertDoesNotThrow(() -> s3ClientService.uploadFile(key, inputStream));
    }
    
    @Test
    @DisplayName("S3ファイルダウンロード統合テスト")
    void testS3FileDownloadIntegration() {
        // ファイルアップロード後のダウンロードテスト
    }
    
    @Test
    @DisplayName("S3ファイル一覧取得統合テスト")
    void testS3FileListIntegration() {
        // ファイル一覧取得テスト
    }
    
    @Test
    @DisplayName("S3ファイル削除統合テスト")
    void testS3FileDeleteIntegration() {
        // ファイル削除テスト
    }
    
    @Test
    @DisplayName("S3大容量ファイルアップロード統合テスト")
    void testS3LargeFileUploadIntegration() {
        // マルチパートアップロードテスト
    }
    
    @Test
    @DisplayName("S3エラーハンドリング統合テスト")
    void testS3ErrorHandlingIntegration() {
        // S3エラー時の例外処理テスト
    }
}
```

### 4.6 リトライ機能統合テスト

**RetryIntegrationTest.java**
```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "csv.export.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
class RetryIntegrationTest {
    
    @MockBean
    private WebServiceTemplate webServiceTemplate;
    
    @Autowired
    private SoapClient soapClient;
    
    @Test
    @DisplayName("Spring Retry指数バックオフ統合テスト")
    void testRetryWithExponentialBackoff() {
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
        assertThat(endTime - startTime).isGreaterThan(3000); // 1秒 + 2秒の遅延
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
        // サーキットブレーカーの動作確認テスト
    }
}
```

---

## 5. テストデータ管理

### 5.1 テスト設定ファイル

**application-test.yml**
```yaml
# テスト用データベース設定
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    show-sql: false  # テスト時はログを簡潔に
  cloud:
    discovery:
      enabled: false  # テスト時はサービスディスカバリーを無効化

# テスト用サービス設定
csv:
  export:
    enabled: false
    s3-upload: false
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
    retry:
      max-attempts: 2   # テスト用短縮リトライ回数
      delay: 100

# AWS設定（テスト用デフォルト値）
aws:
  endpoint: http://localhost:4566
  s3:
    bucket: test-csv-export-bucket
  access-key-id: dummy
  secret-access-key: dummy
  default-region: us-east-1

# セキュリティ強化：環境変数設定（テスト時）
# 実際の環境変数設定は .env ファイルまたはCI/CDで管理
test:
  environment:
    variables:
      DB_PASSWORD: test-password
      AWS_ACCESS_KEY_ID: dummy
      AWS_SECRET_ACCESS_KEY: dummy

# ログ設定
logging:
  level:
    com.example.csvbatch: DEBUG    # テスト時は詳細ログ
    org.springframework.retry: DEBUG
    software.amazon.awssdk: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    
# Spring Retry設定（テスト用）
spring:
  retry:
    circuit-breaker:
      failure-threshold: 3  # テスト用に短縮
      timeout: 5000        # 5秒
      reset-timeout: 10000 # 10秒
```

### 5.2 統合テスト専用設定

**application-integration.yml**
```yaml
# 統合テスト専用設定ファイル
spring:
  profiles:
    active: integration
  cloud:
    discovery:
      enabled: false

# 統合テスト用AWS設定
aws:
  endpoint: ${AWS_ENDPOINT:http://localhost:4566}
  s3:
    bucket: ${AWS_S3_BUCKET:integration-test-bucket}
  access-key-id: ${AWS_ACCESS_KEY_ID:dummy}
  secret-access-key: ${AWS_SECRET_ACCESS_KEY:dummy}
  default-region: ${AWS_DEFAULT_REGION:us-east-1}

# CSV出力設定（統合テスト用）
csv:
  export:
    enabled: true
    s3-upload: true
    local-backup: true
  output:
    path: /tmp/integration-test-result.csv

# タイムアウト設定（統合テスト用）
soap:
  api:
    timeout:
      connection: 30000
      read: 60000
    retry:
      max-attempts: 3
      delay: 1000
  secret-access-key: dummy
  default-region: us-east-1

# ログ設定
logging:
  level:
    com.example.csvbatch: DEBUG
    org.springframework.ws: WARN
    org.testcontainers: WARN
```

### 5.2 テストユーティリティクラス

**TestConfig.java**
```java
@TestConfiguration
@Profile("test")
public class TestConfig {
    
    @Bean
    @Primary
    public S3Client s3Client() {
        return Mockito.mock(S3Client.class);
    }
    
    @Bean
    @Primary
    public WebServiceTemplate webServiceTemplate() {
        return Mockito.mock(WebServiceTemplate.class);
    }
}
```

### 5.3 テストデータビルダー

各テストクラス内でテストデータ作成メソッドを実装：

```java
// EmployeeTest.java内
private Employee createTestEmployee() {
    return Employee.builder()
            .employeeId(1001L)
            .employeeName("田中太郎")
            .department("開発部")
            .email("tanaka@example.com")
            .hireDate(LocalDate.of(2020, 4, 1))
            .salary(new BigDecimal("500000"))
            .build();
}

// SoapClientTest.java内  
private SoapClient.EmployeeDetails createTestEmployeeDetails() {
    return SoapClient.EmployeeDetails.builder()
            .level("Senior")
            .bonus(new BigDecimal("150000"))
            .status("Active")
            .build();
}
```

---

## 6. モック・スタブ管理

### 6.1 モック戦略

| テスト種別 | モック方法 | 対象コンポーネント |
|-----------|-----------|------------------|
| 単体テスト | `@Mock`, `@InjectMocks` | WebServiceTemplate, S3Client, Repository |
| リポジトリテスト | `@MockBean` | 外部サービス |
| 統合テスト | `@MockBean` | WebServiceTemplate |
| Spring Boot起動テスト | `@MockBean` | WebServiceTemplate |

### 6.2 共通モック設定

**TestConfig.java**でグローバルなモック設定を管理：

```java
@TestConfiguration
@Profile("test")
public class TestConfig {
    
    @Bean
    @Primary
    public S3Client s3Client() {
        return Mockito.mock(S3Client.class);
    }
    
    @Bean
    @Primary  
    public WebServiceTemplate webServiceTemplate() {
        return Mockito.mock(WebServiceTemplate.class);
    }
}
```

### 6.3 条件付きモック設定

AwsConfigTestでの環境依存モック：

```java
@Test
@DisplayName("LocalStackプロファイルでのS3Client設定テスト")
void testS3ClientWithLocalStackProfile() {
    // LocalStackプロファイル有無での設定分岐テスト
    ReflectionTestUtils.setField(awsConfig, "activeProfiles", new String[]{"localstack"});
}
```

---

## 7. テスト実行とレポート

### 7.1 基本的なテスト実行

```bash
# 単体テストのみ実行（約1-2分）
mvn test

# 統合テスト含む全テスト実行（約5-10分）
mvn test -DRUN_INTEGRATION_TESTS=true

# 特定のテストクラスのみ実行
mvn test -Dtest=SoapClientTest

# 特定のテストメソッドのみ実行
mvn test -Dtest=SoapClientTest#testGetEmployeeDetailsSuccess
```

### 7.2 カバレッジレポート生成

```bash
# カバレッジレポート生成
mvn clean test jacoco:report

# カバレッジレポート確認
open target/site/jacoco/index.html
```

### 7.3 継続的インテグレーション

**GitHub Actions設定例**
```yaml
# .github/workflows/test.yml
name: Test
on: [push, pull_request]

jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run unit tests
        run: mvn clean test
      - name: Generate coverage report
        run: mvn jacoco:report

  integration-test:
    runs-on: ubuntu-latest
    needs: unit-test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run integration tests
        run: mvn clean test -DRUN_INTEGRATION_TESTS=true
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

- TestContainersの条件付き実行（`@EnabledIfSystemProperty`）
- テスト用プロファイルの活用（`@ActiveProfiles("test")`）
- MemoryLimitを考慮したテストケース設計
- テスト並列実行の設定（surefire-plugin）

### 8.5 セキュリティ強化テスト

- **環境変数**: 必須環境変数（DB_PASSWORD）の動作確認
- **機密情報保護**: ログ出力時のマスキング確認
- **認証**: AWS IAM認証と LocalStack ダミー認証の分離

### 8.6 Spring Cloud統合テスト

- **サービスディスカバリー**: テスト時の無効化設定
- **設定外部化**: Config Server非依存のテスト設計
- **Spring Retry**: @Retryable アノテーションの動作確認
- **サーキットブレーカー**: Resilience4j統合の動作確認

---

## 9. 環境別テスト実行

### 9.1 開発環境でのテスト実行

```bash
# 基本的な単体テスト実行
mvn clean test

# 開発環境でのプロファイル指定テスト
mvn test -Dspring.profiles.active=dev

# LocalStack使用の統合テスト
docker-compose up -d localstack
mvn test -DRUN_INTEGRATION_TESTS=true -Dspring.profiles.active=localstack
```

### 9.2 CI/CD環境でのテスト実行

```bash
# GitHub Actions での実行例
name: "CI/CD Tests"
env:
  RUN_INTEGRATION_TESTS: true
  DB_PASSWORD: ci-test-password
  AWS_ACCESS_KEY_ID: dummy
  AWS_SECRET_ACCESS_KEY: dummy

# 実行
mvn clean test verify -Dmaven.test.failure.ignore=false
```

### 9.3 本番類似環境でのテスト

```bash
# 本番設定に近いテスト環境でのエンドツーエンドテスト
export DB_PASSWORD="secure-production-like-password"
export AWS_ENDPOINT="http://localstack:4566"
export SPRING_PROFILES_ACTIVE="production,test"

mvn clean test -DRUN_INTEGRATION_TESTS=true
```

### 9.4 新機能テストパターン

#### 9.4.1 Spring Retry機能テスト
```bash
# リトライ機能特化テスト
mvn test -Dtest=*RetryTest*

# タイムアウト・バックオフ測定
mvn test -Dtest=RetryIntegrationTest#testRetryWithExponentialBackoff -Dlogging.level.org.springframework.retry=DEBUG
```

#### 9.4.2 S3統合機能テスト
```bash
# LocalStack S3機能テスト
docker-compose up -d localstack
mvn test -Dtest=S3*Test* -DRUN_INTEGRATION_TESTS=true

# S3アップロード・ダウンロードテスト
mvn test -Dtest=S3IntegrationTest -DRUN_INTEGRATION_TESTS=true
```

#### 9.4.3 例外処理強化テスト
```bash
# 新規例外クラステスト
mvn test -Dtest=*Exception*Test

# エラーハンドリング統合テスト
mvn test -Dtest=ErrorHandlingIntegrationTest
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

#### 10.1.3 LocalStack接続エラー
```bash
# LocalStackが正常に起動していない場合
docker-compose logs localstack

# ポート競合確認
netstat -an | grep 4566

# LocalStack健康状態確認
curl http://localhost:4566/_localstack/health
```

#### 10.1.4 環境変数設定エラー
```bash
# 必須環境変数未設定エラー
export DB_PASSWORD=test-password
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy

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
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=256m"

# TestContainers メモリ設定
# docker-compose.override.yml:
services:
  localstack:
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

#### 10.3.2 WebServiceTemplate Mock問題
```java
// モック設定の確認
when(webServiceTemplate.marshalSendAndReceive(anyString(), any()))
    .thenThrow(new WebServiceIOException("Test error"));

// 呼び出し確認
verify(webServiceTemplate, times(1))
    .marshalSendAndReceive(anyString(), any());
```

### 10.4 設定ファイル問題

#### 10.4.1 application-test.yml読み込まれない
```bash
# プロファイル確認
mvn test -Dspring.profiles.active=test -X | grep -i "application-test.yml"

# 設定値確認
mvn test -Dtest=ConfigTest -Dlogging.level.org.springframework.context=DEBUG
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

### 10.5 Spring Cloud設定問題

#### 10.5.1 サービスディスカバリー無効化されない
```yaml
# application-test.yml に明示的に追加
spring:
  cloud:
    discovery:
      enabled: false
    config:
      enabled: false
```

#### 10.5.2 Spring Retry設定問題
```java
// Retry設定確認テスト
@Test
void testRetryConfiguration() {
    // @Retryable アノテーション付きメソッドの動作確認
    // RetryTemplate Bean設定の確認
}
```

### 10.6 デバッグのためのログ出力

```bash
# Spring Retry関連ログ
mvn test -Dlogging.level.org.springframework.retry=DEBUG

# AWS SDK関連ログ
mvn test -Dlogging.level.software.amazon.awssdk=DEBUG

# アプリケーション詳細ログ
mvn test -Dlogging.level.com.example.csvbatch=DEBUG

# テスト詳細実行ログ
mvn test -X -Dtest=CsvExportServiceTest
```

### 10.7 完全なクリーンアップ

```bash
# テスト環境完全クリーンアップ
docker-compose down --volumes --remove-orphans
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
- [ ] 環境変数設定: DB_PASSWORD等の必須変数
- [ ] 新機能テスト: 関連テストクラスの実行
- [ ] MockとStubの適切な使用

#### A.2 CI/CD環境チェックリスト  
- [ ] 全テスト実行: `mvn test -DRUN_INTEGRATION_TESTS=true`
- [ ] TestContainers動作確認
- [ ] LocalStack接続確認
- [ ] セキュリティテスト: 環境変数での機密情報管理
- [ ] パフォーマンステスト: 実行時間の監視

### B. テスト設定サマリー

| 項目 | 単体テスト | 統合テスト | 備考 |
|------|-----------|-----------|------|
| 実行時間 | 1-2分 | 5-10分 | TestContainers含む |
| テスト数 | 61個 | 19個 | 合計80個 |
| Docker必須 | × | ○ | TestContainers使用 |
| 環境変数 | 一部 | 必須 | セキュリティ強化により |
| プロファイル | test | test,integration | Spring Cloud対応 |

### C. 関連技術仕様

- **Spring Cloud**: 2023.0.0 + Spring Boot 3.2.0
- **Spring Retry**: 指数バックオフ、最大3回リトライ
- **TestContainers**: Oracle XE 21, LocalStack 3.0
- **JUnit**: 5.9.2 + AssertJ + Mockito
- **AWS SDK**: S3 Client v2 + LocalStack統合
- **カバレッジツール**: JaCoCo + Maven Surefire Plugin

**最終更新**: ソースコード修正内容に合わせてセキュリティ強化と新機能対応を実施