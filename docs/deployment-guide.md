# デプロイメントガイド（OCI版）

## 1. 環境要件

### 1.1 システム要件
- **OS**: Linux (Ubuntu 20.04+推奨) / Windows 10+ / macOS 11+
- **Docker**: 20.10.0以上
- **Docker Compose**: 2.0.0以上
- **メモリ**: 最小4GB、推奨8GB以上
- **ディスク容量**: 最小10GB、推奨20GB以上
- **OCI CLI**: 3.0以上（本番デプロイ用）
- **kubectl**: 1.25以上（OKEデプロイ用）

### 1.2 ネットワーク要件
- **ポート**: 1521, 8080, 8081が利用可能であること
- **インターネット接続**: Dockerイメージダウンロードのため必要
- **OCI接続**: Object Storage、Container Registry等へのアクセス

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
├── docker-compose.yml
├── docker-compose.oci.yml  # OCI環境用
├── Dockerfile
├── pom.xml
├── src/
├── soap-stub/
├── oracle-init/
├── output/
└── config/
    └── wallet/  # Autonomous Database用（オプション）
```

## 3. ローカル開発環境設定

### 3.1 環境変数設定（必須）
```bash
# .envファイルを作成
cat > .env << EOF
# データベース設定（必須）
ORACLE_PASSWORD=your_secure_oracle_password
DB_USERNAME=csvuser
DB_PASSWORD=your_secure_csvuser_password
DB_URL=jdbc:oracle:thin:@oracle-db:1521/XEPDB1

# OCI設定（ローカル開発用）
OCI_NAMESPACE=your_namespace
OCI_BUCKET=csv-export-bucket
OCI_REGION=us-ashburn-1

# OCI認証（ローカルテスト用）
# 実際のOCIアクセスには適切な認証設定が必要
OCI_CONFIG_FILE=~/.oci/config
OCI_PROFILE=DEFAULT

# CSV出力設定
CSV_OUTPUT_PATH=/app/output/result.csv
CSV_EXPORT_STORAGE_UPLOAD=true
CSV_EXPORT_LOCAL_BACKUP=true
CSV_EXPORT_ENABLED=true

# SOAP API設定
SOAP_API_URL=http://soap-stub:8080/ws
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
# 出力ディレクトリ作成
mkdir -p output
chmod 755 output

# Walletディレクトリ作成（Autonomous Database用）
mkdir -p config/wallet
chmod 700 config/wallet
```

## 4. ローカル環境デプロイメント

### 4.1 基本デプロイメント
```bash
# 1. 全サービス起動（初回はイメージビルドも実行）
docker-compose up -d

# 2. 起動状況確認
docker-compose ps

# 3. ログ確認
docker-compose logs -f csv-batch-processor
```

### 4.2 段階的起動（推奨）
```bash
# 1. データベースを起動
docker-compose up -d oracle-db

# 2. データベースの健康状態確認
docker-compose exec oracle-db sqlplus -L system/oracle@//localhost:1521/XE <<<'SELECT 1 FROM DUAL;'

# 3. SOAPスタブ起動
docker-compose up -d soap-stub

# 4. SOAPスタブの健康状態確認
curl -f http://localhost:8081/ws/employees.wsdl

# 5. メインアプリケーション起動
docker-compose up -d csv-batch-processor

# 6. アプリケーション健康状態確認
curl -f http://localhost:8080/actuator/health
```

### 4.3 デプロイメント確認
```bash
# 全サービスの状態確認
docker-compose ps

# CSV出力ファイル確認（ローカル）
ls -la output/
cat output/result.csv

# アプリケーション健康状態確認
curl -f http://localhost:8080/actuator/health

# 処理ログ確認
docker-compose logs csv-batch-processor | grep -E "CSV|Processing"
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
```yaml
# csv-batch-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: csv-batch-processor
spec:
  replicas: 1
  selector:
    matchLabels:
      app: csv-batch-processor
  template:
    metadata:
      labels:
        app: csv-batch-processor
    spec:
      containers:
      - name: csv-batch-processor
        image: <region>.ocir.io/<namespace>/csv-batch-processor:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        - name: OCI_BUCKET
          value: csv-export-bucket
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: csv-batch-service
spec:
  selector:
    app: csv-batch-processor
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

#### 5.3.3 デプロイ実行
```bash
# シークレット作成
kubectl create secret generic db-credentials \
    --from-literal=password=$DB_PASSWORD

# デプロイメント適用
kubectl apply -f csv-batch-deployment.yaml

# 状態確認
kubectl get pods
kubectl get services

# ログ確認
kubectl logs -f deployment/csv-batch-processor
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

#### 9.1.1 Autonomous Database接続エラー
```bash
# mTLS接続文字列の確認
echo $DB_URL

# ウォレット使用時の確認
ls -la $TNS_ADMIN
cat $TNS_ADMIN/tnsnames.ora

# 接続テスト
docker exec csv-batch-processor java -cp /app/app.jar TestConnection
```

#### 9.1.2 Object Storage権限エラー
```bash
# インスタンスプリンシパル確認
curl -H "Authorization: Bearer Oracle" http://169.254.169.254/opc/v2/instance/

# IAMポリシー確認
oci iam policy list \
    --compartment-id <compartment-ocid> \
    --query "data[?name=='csv-batch-policy']"

# バケット権限テスト
oci os object list \
    --bucket-name csv-export-bucket \
    --auth instance_principal
```

#### 9.1.3 Container Instancesエラー
```bash
# コンテナログ確認
oci container-instances container-instance get \
    --container-instance-id <instance-ocid>

# イベント確認
oci events event list \
    --compartment-id <compartment-ocid> \
    --start-time 2025-01-01T00:00:00Z \
    --end-time 2025-12-31T23:59:59Z
```

### 9.2 デバッグ情報収集
```bash
# システム情報収集スクリプト
cat > collect-debug.sh << 'EOF'
#!/bin/bash
echo "=== OCI Configuration ==="
oci iam availability-domain list

echo "=== Container Status ==="
kubectl get pods -o wide

echo "=== Application Logs ==="
kubectl logs deployment/csv-batch-processor --tail=100

echo "=== Object Storage ==="
oci os bucket list --compartment-id $COMPARTMENT_ID

echo "=== Network Configuration ==="
kubectl get services
EOF

chmod +x collect-debug.sh
./collect-debug.sh > debug-report.txt
```

## 10. 本番環境チェックリスト

### 10.1 セキュリティ
- [ ] OCI Vault設定完了
- [ ] IAMポリシー最小権限化
- [ ] ネットワークセキュリティリスト設定
- [ ] 暗号化有効（TDE、Object Storage）

### 10.2 可用性
- [ ] マルチAD構成
- [ ] 自動バックアップ設定
- [ ] ヘルスチェック設定
- [ ] 自動スケーリング設定

### 10.3 監視
- [ ] OCI Monitoring設定
- [ ] アラーム設定
- [ ] ログ収集設定
- [ ] パフォーマンスメトリクス

### 10.4 運用
- [ ] バックアップ・リストア手順
- [ ] 障害対応手順書
- [ ] 定期メンテナンス計画
- [ ] 容量計画

---

**最終更新**: 2025-08-06 - OCI PoC環境用デプロイメントガイド