# デプロイメントガイド（OCI版）

## 1. 環境要件

### 1.1 システム要件
- **OS**: Linux (Ubuntu 20.04+推奨) / Windows 10+ / macOS 11+
- **Java**: OpenJDK 21 (Helidon MP 4.0.11対応)
- **Maven**: 3.9+
- **Docker**: 20.10.0以上
- **Docker Compose**: 3.8+
- **メモリ**: 最小2GB、推奨4GB以上
- **ディスク容量**: 最小5GB、推奨10GB以上
- **OCI CLI**: 3.0以上（本番デプロイ用）
- **kubectl**: 1.25以上（OKEデプロイ用）

### 1.2 ネットワーク要件
- **ポート**: 1521(Oracle DB), 8080(Application), 8081(SOAP Stub), 8082(OCI Local Testing), 3000(Grafana), 9090(Prometheus)
- **インターネット接続**: Dockerイメージダウンロードのため必要
- **OCI接続**: Object Storage、Container Registry、Autonomous Database等へのアクセス

## 2. 事前準備

### 2.1 Dockerインストール確認
```bash
# Dockerバージョン確認
docker --version
docker-compose --version

# Docker サービス起動確認
docker info
```

### 2.2 プロジェクト取得
```bash
# プロジェクトクローン
git clone <repository-url>
cd oracle_cloud_batch_app_sample

# または、ファイル一式をコピー
```

### 2.3 ディレクトリ構造確認
```
oracle_cloud_batch_app_sample/
├── docker-compose.yml                    # メイン構成
├── docker-compose.test.yml              # テスト環境用
├── docker-compose.test.minimal.yml      # 軽量テスト環境用
├── Dockerfile                          # アプリケーション用
├── Dockerfile.test                     # テスト用
├── pom.xml                             # Mavenプロジェクト
├── src/                                # ソースコード
├── k8s/                                # Kubernetesマニフェスト
│   ├── deployment.yaml
│   ├── configmap.yaml
│   └── secrets.yaml
├── scripts/                            # 初期化スクリプト
│   ├── init-postgresql.sql
│   └── init-test-db.sql
├── test-resources/                     # テストリソース
│   └── wiremock/
├── output/                             # CSV出力先
└── logs/                               # ログ出力先
```

## 3. ローカル開発環境設定

### 3.1 環境変数設定（必須）
```bash
# .envファイルを作成
cat > .env << EOF
# Oracle Database設定（必須）
ORACLE_PWD=oracle
DB_USERNAME=csvuser
DB_PASSWORD=password

# OCI設定（ローカル開発用）
OCI_NAMESPACE=namespace
OCI_BUCKET=csv-export-bucket
OCI_REGION=us-ashburn-1
OCI_AUTH_METHOD=config_file

# Monitoring設定
GRAFANA_PASSWORD=admin

# Helidon設定
MP_CONFIG_PROFILE=docker
HELIDON_MP_LOG_LEVEL=INFO
EOF

# ファイル権限を制限
chmod 600 .env
```

### 3.2 OCI CLI設定（オプション）
```bash
# OCI CLIインストール
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"

# OCI設定
oci setup config

# 設定確認
oci iam region list
```

### 3.3 出力ディレクトリ準備
```bash
# 必要なディレクトリ作成
mkdir -p output logs
chmod 755 output logs

# テスト環境用ディレクトリ
mkdir -p test-output test-logs
chmod 755 test-output test-logs

# WireMockスタブ用ディレクトリ確認
ls -la test-resources/wiremock/
```

## 4. ローカル環境デプロイメント

### 4.1 基本デプロイメント
```bash
# 1. アプリケーションビルド（初回のみ）
mvn clean package -DskipTests

# 2. 全サービス起動（モニタリング含む）
docker-compose up -d

# 3. 起動状況確認
docker-compose ps

# 4. メインアプリケーションログ確認
docker-compose logs -f csv-batch-processor
```

### 4.2 段階的起動（推奨）
```bash
# 1. Oracle Database起動
docker-compose up -d oracle-db

# 2. データベースの健康状態確認
# Oracle XEの起動完了を待つ（数分かかる場合あり）
docker-compose logs oracle-db | grep "DATABASE IS READY TO USE"

# 3. SOAPスタブモック起動
docker-compose up -d soap-stub

# 4. OCI Local Testing Framework起動（利用可能な場合）
docker-compose up -d oci-local-testing

# 5. SOAPスタブの健康状態確認
curl -f http://localhost:8081/health || echo "WireMock is starting..."

# 6. メインアプリケーション起動
docker-compose up -d csv-batch-processor

# 7. Helidonアプリケーションの健康状態確認
curl -f http://localhost:8080/health/ready

# 8. モニタリングサービス起動
docker-compose up -d prometheus grafana
```

### 4.3 デプロイメント確認
```bash
# 全サービスの状態確認
docker-compose ps

# Helidon MPアプリケーションのヘルスチェック
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready

# CSV出力ファイル確認（ローカル）
ls -la output/
# 出力ファイルがある場合
cat output/result.csv 2>/dev/null || echo "CSV file not yet generated"

# CSVエクスポートAPIのテスト
curl -X POST http://localhost:8080/api/csv/export

# メトリクス確認
curl http://localhost:8080/metrics

# モニタリングダッシュボードアクセス
echo "Grafana: http://localhost:3000 (admin/admin)"
echo "Prometheus: http://localhost:9090"

# アプリケーションログ確認
docker-compose logs csv-batch-processor | grep -E "CSV|Processing|Helidon|Starting"
```

## 5. OCI環境へのデプロイメント

### 5.1 Container Registry設定
```bash
# OCIRログイン
docker login <region>.ocir.io
# Username: <namespace>/<username>
# Password: Auth Token

# イメージタグ付け
docker tag csv-batch-processor:latest <region>.ocir.io/<namespace>/csv-batch-processor:latest
docker tag soap-stub:latest <region>.ocir.io/<namespace>/soap-stub:latest

# イメージプッシュ
docker push <region>.ocir.io/<namespace>/csv-batch-processor:latest
docker push <region>.ocir.io/<namespace>/soap-stub:latest
```

### 5.2 Container Instancesデプロイ（シンプル構成）
```bash
# Container Instances作成
oci container-instances container-instance create \
    --availability-domain <AD> \
    --compartment-id <compartment-ocid> \
    --display-name csv-batch-processor \
    --container-restart-policy ALWAYS \
    --shape CI.Standard.E4.Flex \
    --shape-config '{"memory_in_gbs": 8, "ocpus": 2}' \
    --containers '[
        {
            "image_url": "<region>.ocir.io/<namespace>/csv-batch-processor:latest",
            "display_name": "csv-batch-processor",
            "environment_variables": {
                "DB_URL": "jdbc:oracle:thin:@<adb-connection-string>",
                "OCI_BUCKET": "csv-export-bucket"
            }
        }
    ]'
```

### 5.3 OKE (Kubernetes) デプロイ（推奨）

#### 5.3.1 OKEクラスター作成
```bash
# OKEクラスター作成
oci ce cluster create \
    --compartment-id <compartment-ocid> \
    --name csv-batch-cluster \
    --kubernetes-version v1.28.2 \
    --vcn-id <vcn-ocid> \
    --service-lb-subnet-ids '["<subnet-ocid>"]'

# kubeconfigダウンロード
oci ce cluster create-kubeconfig \
    --cluster-id <cluster-ocid> \
    --file $HOME/.kube/config \
    --region <region>
```

#### 5.3.2 Kubernetes マニフェスト

実際のプロジェクトに含まれる `k8s/` ディレクトリのマニフェストファイルを使用：

```bash
# マニフェストファイルの構成確認
ls -la k8s/
# configmap.yaml    - 設定マップ
# deployment.yaml   - デプロイメント、サービス、Ingress
# secrets.yaml      - シークレット

# マニフェストの主な特徴：
# - Helidon MP 4.0.11対応
# - セキュリティ強化設定（non-root user, read-only filesystem）
# - OCI Workload Identity対応
# - Prometheusメトリクス収集
# - ヘルスチェック設定
# - IngressでのSSL/TLS終端
```

主な設定ポイント：
- **レプリカ数**: 2個（可用性向上）
- **リソース制限**: メモリ 512Mi-2Gi, CPU 250m-1000m
- **ヘルスチェック**: `/health/live` と `/health/ready`
- **セキュリティ**: 非non-rootユーザー、読み取り専用ファイルシステム

#### 5.3.3 デプロイ実行
```bash
# 1. ネームスペース作成
kubectl create namespace csv-batch

# 2. サービスアカウント作成（OCI Workload Identity用）
kubectl create serviceaccount csv-batch-sa -n csv-batch

# 3. ConfigMap適用
kubectl apply -f k8s/configmap.yaml

# 4. Secrets適用（実際の値で置き換え）
# 事前にsecrets.yamlを編集し、base64エンコードされた値を設定
kubectl apply -f k8s/secrets.yaml

# 5. デプロイメント適用
kubectl apply -f k8s/deployment.yaml

# 6. 状態確認
kubectl get all -n csv-batch
kubectl get pods -n csv-batch -w

# 7. ログ確認
kubectl logs -f deployment/csv-batch-processor -n csv-batch

# 8. ヘルスチェック確認
kubectl exec -it deployment/csv-batch-processor -n csv-batch -- curl http://localhost:8080/health/ready

# 9. サービスアクセス確認
kubectl port-forward svc/csv-batch-processor-service 8080:80 -n csv-batch
# 別ターミナルで: curl http://localhost:8080/health/ready
```

## 6. Autonomous Database接続設定

### 6.1 ウォレット不要接続（mTLS）
```bash
# 環境変数設定
export DB_URL="jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1521)(host=<adb-host>))(connect_data=(service_name=<service>))(security=(ssl_server_dn_match=yes)))"
```

### 6.2 ウォレット使用接続
```bash
# ウォレットダウンロード
oci db autonomous-database generate-wallet \
    --autonomous-database-id <adb-ocid> \
    --file wallet.zip \
    --password <wallet-password>

# ウォレット展開
unzip wallet.zip -d config/wallet/

# 環境変数設定
export TNS_ADMIN=/app/config/wallet
export DB_URL="jdbc:oracle:thin:@dbname_high"
```

## 7. Object Storage設定

### 7.1 バケット作成（自動作成も可能）
```bash
# バケット作成
oci os bucket create \
    --compartment-id <compartment-ocid> \
    --name csv-export-bucket \
    --public-access-type NoPublicAccess

# バケット確認
oci os bucket list \
    --compartment-id <compartment-ocid> \
    --query "data[?name=='csv-export-bucket']"
```

### 7.2 IAMポリシー設定
```bash
# 動的グループ作成（インスタンスプリンシパル用）
oci iam dynamic-group create \
    --name csv-batch-instances \
    --description "CSV Batch Processor instances" \
    --matching-rule "instance.compartment.id = '<compartment-ocid>'"

# ポリシー作成
cat > policy.json << EOF
{
  "statements": [
    "Allow dynamic-group csv-batch-instances to manage objects in compartment <compartment-name> where target.bucket.name='csv-export-bucket'",
    "Allow dynamic-group csv-batch-instances to read autonomous-databases in compartment <compartment-name>"
  ]
}
EOF

oci iam policy create \
    --compartment-id <compartment-ocid> \
    --name csv-batch-policy \
    --description "Policy for CSV Batch Processor" \
    --statements file://policy.json
```

## 8. 監視設定

### 8.1 OCI Monitoring設定
```bash
# アラーム作成
oci monitoring alarm create \
    --compartment-id <compartment-ocid> \
    --display-name "CSV Batch Processor Health" \
    --metric-compartment-id <compartment-ocid> \
    --namespace "oci_computecontainerinstance" \
    --query "ContainerInstanceHealthStatus[1m].mean() < 1" \
    --severity "CRITICAL" \
    --body "CSV Batch Processor is unhealthy" \
    --destinations '["<topic-ocid>"]'
```

### 8.2 ログ設定
```bash
# ログ収集設定
oci logging log-group create \
    --compartment-id <compartment-ocid> \
    --display-name csv-batch-logs

oci logging log create \
    --log-group-id <log-group-ocid> \
    --display-name csv-batch-app-logs \
    --log-type SERVICE \
    --configuration '{
        "source": {
            "source-type": "OCISERVICE",
            "service": "containerinstances",
            "resource": "<container-instance-ocid>",
            "category": "all"
        }
    }'
```

## 9. トラブルシューティング

### 9.1 よくある問題と解決方法

#### 9.1.1 Helidon MPアプリケーション起動エラー
```bash
# Helidonアプリケーションのスタータス確認
docker-compose logs csv-batch-processor | grep -E "Helidon|Starting|Ready"

# ヘルスチェックエンドポイント確認
curl -v http://localhost:8080/health/live
curl -v http://localhost:8080/health/ready

# MicroProfile Config設定の確認
docker exec csv-batch-processor env | grep -E "MP_|HELIDON_"

# ポート競合の確認
netstat -tulpn | grep 8080
```

#### 9.1.2 Oracle Database接続エラー
```bash
# Oracle XEコンテナの状態確認
docker-compose logs oracle-db | grep -E "DATABASE IS READY|ERROR"

# データベース接続テスト
docker exec csv-batch-processor curl -f http://localhost:8080/health/ready

# データソース設定の確認
docker exec csv-batch-processor env | grep -E "DB_|datasource"

# Oracle接続テスト（コンテナ内から）
docker exec oracle-db sqlplus -s csvuser/password@//localhost:1521/XEPDB1 <<< "SELECT 1 FROM DUAL;"
```

#### 9.1.3 Object Storageエラー
```bash
# OCI Local Testing Frameworkの状態確認
docker-compose ps oci-local-testing
docker-compose logs oci-local-testing

# LocalStackへのフォールバック（テスト環境）
# docker-compose.test.minimal.ymlを使用した場合
docker-compose -f docker-compose.test.minimal.yml logs localstack
curl http://localhost:4566/_localstack/health

# Object Storageヘルスチェック確認
docker exec csv-batch-processor curl -f http://localhost:8080/health/ready

# Object Storage設定の確認
docker exec csv-batch-processor env | grep -E "OCI_|oci\."
```

#### 9.1.4 Docker Composeエラー
```bash
# 全サービスの状態確認
docker-compose ps
docker-compose logs --tail=50

# ネットワークの確認
docker network ls | grep csv-batch
docker network inspect csv-batch-network

# ボリュームの確認
docker volume ls | grep csv-batch
ls -la output/ logs/

# リソース使用状況
docker stats
```

#### 9.1.5 メトリクス・モニタリングエラー
```bash
# Prometheusメトリクスの確認
curl http://localhost:8080/metrics

# Prometheusサーバーの状態
curl http://localhost:9090/-/ready

# Grafanaアクセス確認
curl -I http://localhost:3000

# モニタリングサービスのログ
docker-compose logs prometheus
docker-compose logs grafana
```

### 9.2 デバッグ情報収集
```bash
# システム情報収集スクリプト
cat > collect-debug.sh << 'EOF'
#!/bin/bash
echo "=== System Information ==="
date
docker --version
docker-compose --version

echo "=== Container Status ==="
docker-compose ps
docker stats --no-stream

echo "=== Helidon Application Logs ==="
docker-compose logs --tail=100 csv-batch-processor

echo "=== Health Checks ==="
curl -s http://localhost:8080/health/live || echo "Live check failed"
curl -s http://localhost:8080/health/ready || echo "Ready check failed"

echo "=== Network Configuration ==="
docker network ls
docker-compose port csv-batch-processor 8080

echo "=== Environment Variables ==="
docker exec csv-batch-processor env | grep -E "HELIDON|MP_|DB_|OCI_" | sort

echo "=== File System ==="
ls -la output/
ls -la logs/

echo "=== Metrics (if available) ==="
curl -s http://localhost:8080/metrics | head -20
EOF

chmod +x collect-debug.sh
./collect-debug.sh > debug-report-$(date +%Y%m%d-%H%M%S).txt
```

## 10. 本番環境チェックリスト

### 10.1 アプリケーション
- [ ] Helidon MP 4.0.11の本番用設定完了
- [ ] MicroProfile Configプロファイル設定 (`production`)
- [ ] JVMパラメーター最適化
- [ ] ヘルスチェックエンドポイント動作確認
- [ ] Resilience4j設定確認（リトライ、サーキットブレーカー）

### 10.2 セキュリティ
- [ ] OCI Vault統合完了（シークレット管理）
- [ ] インスタンスプリンシパル認証設定
- [ ] IAMポリシー最小権限化
- [ ] コンテナセキュリティ（non-root user, read-only filesystem）
- [ ] ネットワークセキュリティルール設定

### 10.3 データベース
- [ ] Autonomous Database接続設定
- [ ] UCPコネクションプール設定
- [ ] mTLS接続設定（ウォレットまたはウォレットレス）
- [ ] データベースバックアップ設定

### 10.4 可用性・スケーラビリティ
- [ ] マルチインスタンス構成（OKEレプリカ: 2+）
- [ ] ロードバランサー設定
- [ ] 自動スケーリング設定（HPA/VPA）
- [ ] Rolling Update戦略設定

### 10.5 監視・メトリクス
- [ ] Prometheusメトリクス収集設定
- [ ] Grafanaダッシュボード設定
- [ ] OCI Monitoring統合
- [ ] アラーヨ通知設定
- [ ] MicroProfile Metricsエンドポイント動作確認

### 10.6 ログ管理
- [ ] OCI Loggingサービス統合
- [ ] ログローテーション設定
- [ ] アプリケーションログレベル設定
- [ ] セキュリティログ収集

### 10.7 運用
- [ ] CI/CDパイプライン構築
- [ ] バックアップ・リストア手順
- [ ] 障害対応手順書（Helidon固有のトラブルシューティング含む）
- [ ] 定期メンテナンス計画
- [ ] 容量計画（メモリ、CPU、ストレージ）

---

## 参考情報

### 関連ファイル
- `docker-compose.yml` - メインのサービス構成
- `docker-compose.test.minimal.yml` - 軽量テスト環境
- `Dockerfile` - Helidon MPアプリケーションのビルド設定
- `k8s/deployment.yaml` - OKEデプロイメント用マニフェスト
- `src/main/resources/META-INF/microprofile-config.properties` - MicroProfile設定

### 技術仕様
- **Helidon MP**: 4.0.11
- **Java**: OpenJDK 21
- **Maven**: 3.9+
- **Oracle Database**: XE 21.3.0
- **Docker**: 20.10.0+
- **Kubernetes**: 1.25+

---

**最終更新**: 2025-08-07 - Helidon MP 4.0.11実装版デプロイメントガイド  
**対応環境**: ローカル開発、Docker Compose、OKE (Oracle Kubernetes Engine)