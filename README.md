# CSV Batch Processor - OCI Edition

エンタープライズグレードのCSVバッチプロセッサーアプリケーション。Oracle Cloud Infrastructure (OCI) とHelidon MPフレームワークを使用して構築された、高可用性・スケーラブルなクラウドネイティブアプリケーション。

## 🚀 主要機能

- **Helidon MP 4.0**: MicroProfile準拠のクラウドネイティブフレームワーク
- **Universal Connection Pool (UCP)**: Oracle Autonomous Database最適化接続
- **OCI Object Storage**: 自動バケット作成とファイル管理
- **OCI Vault統合**: セキュアなシークレット管理
- **Resilience4j**: サーキットブレーカーとリトライ機構
- **MicroProfile Health**: 包括的ヘルスチェック
- **MicroProfile Metrics**: リアルタイムメトリクス収集

## 📋 システム要件

- Java 21 (OpenJDK推奨)
- Maven 3.8+
- Docker 24.0+
- Oracle Database 21c+ または Autonomous Database

## 🛠️ ローカル開発セットアップ

### 1. プロジェクトクローン
```bash
git clone <repository-url>
cd csv-batch-processor
```

### 2. 環境変数設定
```bash
export DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
export DB_USERNAME="csvuser"
export DB_PASSWORD="your_password"
export OCI_NAMESPACE="your_namespace"
export OCI_BUCKET="csv-export-bucket"
```

### 3. ビルド
```bash
mvn clean compile
```

### 4. 実行
```bash
mvn exec:exec
```

## 🐋 Docker実行

### 単体実行
```bash
docker build -t csv-batch-processor:latest .
docker run -p 8080:8080 \
  -e DB_PASSWORD=password \
  -e OCI_NAMESPACE=namespace \
  csv-batch-processor:latest
```

### Docker Compose実行
```bash
docker-compose up -d
```

## ☸️ Kubernetes デプロイメント

### 1. ネームスペース作成
```bash
kubectl create namespace csv-batch
```

### 2. シークレット作成
```bash
kubectl apply -f k8s/secrets.yaml
```

### 3. ConfigMap作成
```bash
kubectl apply -f k8s/configmap.yaml
```

### 4. アプリケーションデプロイ
```bash
kubectl apply -f k8s/deployment.yaml
```

## 📊 API エンドポイント

### REST API
- `POST /api/csv/export` - CSV出力実行
- `GET /api/csv/status` - サービス状態確認
- `GET /api/csv/files` - CSV ファイル一覧
- `GET /api/csv/download/{fileName}` - CSVファイルダウンロード
- `DELETE /api/csv/files/{fileName}` - CSVファイル削除

### メトリクス API
- `GET /api/metrics/application` - アプリケーションメトリクス
- `GET /api/metrics/database` - データベースメトリクス
- `GET /api/metrics/summary` - 総合メトリクス

### MicroProfile エンドポイント
- `GET /health` - ヘルスチェック
- `GET /health/ready` - レディネスチェック
- `GET /health/live` - ライブネスチェック
- `GET /metrics` - Prometheusメトリクス

## 🔧 設定オプション

### アプリケーション設定 (microprofile-config.properties)

```properties
# データベース設定
datasource.url=${DB_URL}
datasource.username=${DB_USERNAME}
datasource.password=${DB_PASSWORD}

# OCI設定
oci.region=${OCI_REGION:us-ashburn-1}
oci.objectstorage.namespace=${OCI_NAMESPACE}
oci.objectstorage.bucket=${OCI_BUCKET:csv-export-bucket}

# CSV出力設定
csv.export.enabled=true
csv.export.storage-upload=true
csv.batch.size=1000

# リトライ設定
resilience4j.retry.instances.soapService.max-attempts=3
resilience4j.circuitbreaker.instances.soapService.failure-rate-threshold=50
```

## 🧪 テスト実行

### 単体テスト
```bash
mvn test
```

### 統合テスト (データベース必須)
```bash
mvn test -Dtest.database.enabled=true
```

### 全テスト (外部サービス必須)
```bash
mvn test -Dtest.database.enabled=true \
         -Dtest.soap.enabled=true \
         -Dtest.integration.enabled=true
```

## 📈 監視・運用

### ヘルスチェック
```bash
curl http://localhost:8080/health
```

### メトリクス確認
```bash
curl http://localhost:8080/metrics/application
```

### CSV出力実行
```bash
curl -X POST http://localhost:8080/api/csv/export
```

## 🔒 セキュリティ

### 認証方式
- **Instance Principal** (推奨): OCI上で自動認証
- **Resource Principal**: OCI Functions用
- **Config File**: 開発環境用

### シークレット管理
- OCI Vault統合による自動シークレット取得
- 定期的なシークレットローテーション対応
- 環境変数フォールバック機能

## 🚀 本番デプロイメント

### OCI Container Instances
```bash
# OCIRへのイメージプッシュ
docker tag csv-batch-processor:latest \
  oci-region.ocir.io/namespace/csv-batch-processor:1.0.0

docker push oci-region.ocir.io/namespace/csv-batch-processor:1.0.0
```

### OKE (Oracle Kubernetes Engine)
```bash
# OKEクラスター接続
oci ce cluster create-kubeconfig --cluster-id <cluster-ocid>

# デプロイメント
kubectl apply -f k8s/
```

## 📋 トラブルシューティング

### よくある問題

1. **データベース接続エラー**
   ```bash
   # 接続テスト
   curl http://localhost:8080/health/ready
   ```

2. **OCI認証エラー**
   ```bash
   # Instance Principal確認
   curl -H "Authorization: Bearer Oracle" \
     http://169.254.169.254/opc/v2/instance/
   ```

3. **メモリ不足エラー**
   ```bash
   # JVM設定調整
   export JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC"
   ```

### ログ確認
```bash
# アプリケーションログ
kubectl logs -f deployment/csv-batch-processor -n csv-batch

# デバッグモード
export JAVA_OPTS="$JAVA_OPTS -Dlogging.level.com.example.csvbatch=DEBUG"
```

## 🤝 コントリビューション

1. フォーク作成
2. フィーチャーブランチ作成 (`git checkout -b feature/AmazingFeature`)
3. 変更コミット (`git commit -m 'Add some AmazingFeature'`)
4. ブランチプッシュ (`git push origin feature/AmazingFeature`)
5. プルリクエスト作成

## 📝 ライセンス

このプロジェクトはMITライセンスの下で公開されています。詳細は[LICENSE](LICENSE)ファイルを参照してください。

## 📞 サポート

- 📧 Email: csv-batch-support@example.com
- 🐛 Issues: [GitHub Issues](https://github.com/example/csv-batch-processor/issues)
- 📖 Documentation: [Wiki](https://github.com/example/csv-batch-processor/wiki)
