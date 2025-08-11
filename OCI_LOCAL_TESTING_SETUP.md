# OCI Local Testing Framework セットアップガイド

## 概要

Oracle Cloud Infrastructure (OCI)サービスをローカル環境でテストするための方法を説明します。公式の`oracle/oci-local-testing`イメージは**存在しません**が、代替ソリューションとして**OCI Emulator**を使用できます。

## 1. Oracle Container Registry認証設定

### 1.1 認証トークンの生成

1. **Oracle Cloudコンソールにログイン**
2. **プロフィールアイコン** → **ユーザー設定** を選択
3. **認証トークン** セクションで **トークンの生成** をクリック
4. トークンの説明を入力（例: "Docker Registry Access"）
5. **生成されたトークンをコピー** （一度しか表示されません！）

### 1.2 テナンシー情報の確認

Oracle Cloudコンソールで以下を確認：
- **テナンシーネームスペース**: 管理 → テナンシー詳細
- **ユーザー名**: プロフィール → ユーザー設定
- **リージョンキー**: 使用しているリージョン（例: nrt = 東京、iad = Ashburn）

### 1.3 Docker Registryへのログイン

```bash
# Oracle Container Registry（グローバル）
docker login container-registry.oracle.com

# または地域固有のOCIR
docker login <region-key>.ocir.io

# 例: 東京リージョン
docker login nrt.ocir.io

# 認証情報
Username: <テナンシーネームスペース>/<ユーザー名>
Password: <認証トークン>
```

## 2. OCI Emulator の使用方法（推奨）

### 2.1 OCI Emulator Dockerイメージの取得と起動

```bash
# OCI Emulatorイメージのプル（認証不要）
docker pull cameritelabs/oci-emulator:latest

# コンテナの起動
docker run -d \
  --name oci-emulator \
  -p 12000:12000 \
  -e OCI_NAMESPACE=test-namespace \
  cameritelabs/oci-emulator:latest
```

**特徴:**
- 認証不要で使用可能
- OCIサービスのモック実装
- ポート12000で動作
- 軽量でセットアップが簡単

### 2.2 Docker Composeでの設定

```yaml
# docker-compose.yml
services:
  # OCI Emulator
  oci-emulator:
    image: cameritelabs/oci-emulator:latest
    ports:
      - "12000:12000"
    environment:
      - OCI_NAMESPACE=test-namespace
    networks:
      - app-network

  # LocalStack (S3互換)
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3
      - AWS_ACCESS_KEY_ID=test
      - AWS_SECRET_ACCESS_KEY=test
      - AWS_DEFAULT_REGION=us-east-1
    networks:
      - app-network
```

### 2.3 環境変数の設定

`.env`ファイルを作成：

```bash
# .env
OCI_NAMESPACE=your-namespace
OCI_COMPARTMENT_ID=ocid1.compartment.oc1..xxxxx
OCI_AUTH_METHOD=local_testing
OCI_OBJECTSTORAGE_ENDPOINT=http://localhost:8080
```

## 3. 代替ソリューション（認証不要）

Oracle Container Registryへのアクセスがない場合の代替案：

### 3.1 LocalStack（推奨）

```yaml
# docker-compose.yml
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3
      - AWS_ACCESS_KEY_ID=test
      - AWS_SECRET_ACCESS_KEY=test
      - AWS_DEFAULT_REGION=us-east-1
    volumes:
      - localstack-data:/var/lib/localstack
```

**アプリケーション設定：**
```properties
# application.properties
object.storage.endpoint=http://localhost:4566
object.storage.bucket=test-bucket
aws.access.key.id=test
aws.secret.access.key=test
aws.region=us-east-1
```

### 3.2 MinIO

```yaml
# docker-compose.yml
services:
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      - MINIO_ROOT_USER=minioadmin
      - MINIO_ROOT_PASSWORD=minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio-data:/data
```

### 3.3 WireMock（OCI REST APIモック）

```yaml
# docker-compose.yml
services:
  oci-api-mock:
    image: wiremock/wiremock:latest
    ports:
      - "8082:8080"
    volumes:
      - ./wiremock/oci-mappings:/home/wiremock/mappings
```

## 4. テスト実装の切り替え

### 4.1 プロファイルベースの設定

```java
// TestConfig.java
@Configuration
public class TestConfig {
    
    @Profile("oci-local")
    @Bean
    public ObjectStorageClient ociLocalTestingClient() {
        // OCI Local Testing Framework用設定
        return ObjectStorageClient.builder()
            .endpoint("http://localhost:8080")
            .build();
    }
    
    @Profile("localstack")
    @Bean
    public S3Client localStackClient() {
        // LocalStack用設定
        return S3Client.builder()
            .endpointOverride(URI.create("http://localhost:4566"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .region(Region.US_EAST_1)
            .build();
    }
}
```

### 4.2 テスト実行

```bash
# OCI Local Testing Frameworkを使用
mvn test -Dspring.profiles.active=oci-local

# LocalStackを使用（デフォルト）
mvn test -Dspring.profiles.active=localstack
```

## 5. トラブルシューティング

### 5.1 認証エラー

```bash
# エラー: pull access denied for oracle/oci-local-testing
# 解決方法:
docker logout container-registry.oracle.com
docker login container-registry.oracle.com
```

### 5.2 ネットワーク接続エラー

```bash
# コンテナ間通信の確認
docker network ls
docker network inspect <network-name>

# ネットワークの再作成
docker-compose down
docker-compose up -d
```

### 5.3 ポート競合

```bash
# 使用中のポート確認（Windows）
netstat -ano | findstr :8080

# 使用中のポート確認（Linux/Mac）
lsof -i :8080

# 別のポートを使用
docker run -p 8888:8080 ...
```

## 6. ベストプラクティス

### 6.1 開発環境

- **LocalStack**を使用して軽量なテスト環境を構築
- CI/CDパイプラインでの自動テストに最適

### 6.2 統合テスト環境

- 可能であれば**OCI Local Testing Framework**を使用
- より本番環境に近いテストが可能

### 6.3 本番前検証

- 実際のOCI環境でのE2Eテスト
- パフォーマンステストとセキュリティ検証

## 7. 参考リンク

- [Oracle Container Registry Documentation](https://docs.oracle.com/en-us/iaas/Content/Registry/home.htm)
- [OCI CLI Configuration](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/cliconcepts.htm)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [MinIO Documentation](https://min.io/docs/)

---

**注意事項**: 
- Oracle Container Registryへのアクセスには有効なOracleアカウントとライセンスが必要です
- 認証トークンは安全に管理し、定期的に更新してください
- 本番環境では実際のOCIサービスを使用することを推奨します