# OCI デプロイメントガイド

## 1. OCI デプロイメント概要

### 1.1 エンタープライズMulti-AZアーキテクチャ

**⚠️ 重要**: 本番環境では必ずSingle-AZではなくMulti-AZ構成を使用してください。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              OCI Region                                      │
│                         (Enterprise Production)                           │
│                                                                             │
│  ┌──────────── AD-1 ────────────┐  ┌──────────── AD-2 ────────────┐         │
│  │ ┌─── OKE Cluster ───┐      │  │ ┌─── OKE Cluster ───┐      │         │
│  │ │ Node Pool (2 nodes)  │      │  │ │ Node Pool (2 nodes)  │      │         │
│  │ │ - csv-batch-pod-1    │      │  │ │ - csv-batch-pod-2    │      │         │
│  │ │ - Load Balancer      │      │  │ │ - Load Balancer      │      │         │
│  │ └─────────────────────┘      │  │ └─────────────────────┘      │         │
│  └──────────────────────────────┘  └──────────────────────────────┘         │
│                                   │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                       Regional Services                               │    │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌──────────────────┐ │    │
│  │  │  Autonomous DB    │  │   Object Storage  │  │   OCI Vault      │ │    │
│  │  │  Multi-AZ Primary │  │   Multi-Region    │  │   HSM-backed     │ │    │
│  │  │  Auto-Scaling     │  │   99.95% SLA      │  │   Auto-rotation  │ │    │
│  │  │  TDE + mTLS       │  │   Private Endpoint│  │   Audit Logging  │ │    │
│  │  └───────────────────┘  └───────────────────┘  └──────────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌────────────────────────────── Security & Compliance ─────────────────────┐    │
│  │ Security Zones | Cloud Guard | WAF + DDoS | Vulnerability Scanner    │    │
│  │ Audit Logs    | Operations  | APM + Logs | Cost Management           │    │
│  └───────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 エンタープライズOCIサービスポートフォリオ

#### コンピュート & コンテナ
- **OKE (Oracle Kubernetes Engine)**: マネージドKubernetes (Multi-AZ構成)
- **OCI Container Instances**: サーバーレスコンテナ実行 (高機能需要時)
- **OCIR (Container Registry)**: Privateコンテナイメージレジストリ

#### データベース & ストレージ
- **Autonomous Database**: 自律型データベース (Always FreeからExadataまで)
- **Object Storage**: 11 9sの耐久性、Regionalレプリケーション
- **Block Storage**: 高性能SSDストレージ (必要時)

#### セキュリティ & アイデンティティ
- **OCI Vault**: HSM-backedシークレット管理 (FIPS 140-2 Level 3)
- **Identity and Access Management (IAM)**: Instance Principal認証
- **Security Zones**: CIS Benchmarks自動適用
- **Cloud Guard**: MLベース脅威検知システム

#### ネットワーク & 接続
- **Virtual Cloud Network (VCN)**: プライベートネットワーク (Multi-AZ)
- **Service Gateway**: OCIサービスへのプライベート接続
- **Load Balancer**: アプリケーション負荷分散 (WAF統合)
- **Web Application Firewall (WAF)**: DDoS保護 + アプリケーションセキュリティ

#### 監視 & 運用
- **OCI APM (Application Performance Monitoring)**: 分散トレーシング
- **OCI Logging**: 一元化ログ管理
- **OCI Monitoring**: カスタムメトリクス & アラート
- **Operations Insights**: AIベースパフォーマンス分析

#### CI/CD & DevOps
- **OCI DevOps**: ネイティブCI/CDパイプライン
- **OCI Resource Manager**: Terraform-as-a-Service (IaC)
- **Vulnerability Scanning Service**: コンテナセキュリティスキャン

## 2. 事前準備

### 2.1 OCI CLI設定
```bash
# OCI CLIインストール
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"

# OCI設定
oci setup config

# 設定内容例
# User OCID: ocid1.user.oc1..xxxxx
# Tenancy OCID: ocid1.tenancy.oc1..xxxxx
# Region: us-ashburn-1
# Generate API Key: Y

# 認証確認
oci iam region list
```

### 2.2 必要な権限
```
# 必要なIAMポリシー
Allow group <group-name> to manage virtual-network-family in compartment <compartment-name>
Allow group <group-name> to manage cluster-family in compartment <compartment-name>
Allow group <group-name> to manage repos in compartment <compartment-name>
Allow group <group-name> to manage autonomous-databases in compartment <compartment-name>
Allow group <group-name> to manage object-family in compartment <compartment-name>
Allow group <group-name> to manage vaults in compartment <compartment-name>
Allow group <group-name> to manage keys in compartment <compartment-name>
```

### 2.3 環境変数設定
```bash
# 基本設定
export OCI_COMPARTMENT_ID="ocid1.compartment.oc1..xxxxx"
export OCI_REGION="us-ashburn-1"
export OCI_NAMESPACE=$(oci os ns get --query 'data' --raw-output)

# プロジェクト固有設定
export PROJECT_NAME="csv-batch-processor"
export BUCKET_NAME="csv-export-bucket"
```

## 3. ネットワーク構築

### 3.1 VCN作成
```bash
# VCN作成
VCN_ID=$(oci network vcn create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --display-name "${PROJECT_NAME}-vcn" \
    --cidr-blocks '["10.0.0.0/16"]' \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# インターネットゲートウェイ作成
IGW_ID=$(oci network internet-gateway create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --is-enabled true \
    --display-name "${PROJECT_NAME}-igw" \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# ルートテーブル更新
RT_ID=$(oci network route-table list \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --query 'data[0].id' --raw-output)

oci network route-table update \
    --rt-id $RT_ID \
    --route-rules '[{
        "destination": "0.0.0.0/0",
        "destinationType": "CIDR_BLOCK",
        "networkEntityId": "'$IGW_ID'"
    }]' --force
```

### 3.2 サブネット作成
```bash
# パブリックサブネット（Load Balancer用）
PUBLIC_SUBNET_ID=$(oci network subnet create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --display-name "${PROJECT_NAME}-public-subnet" \
    --cidr-block "10.0.1.0/24" \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# プライベートサブネット（ワークロード用）
PRIVATE_SUBNET_ID=$(oci network subnet create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --display-name "${PROJECT_NAME}-private-subnet" \
    --cidr-block "10.0.2.0/24" \
    --prohibit-public-ip-on-vnic true \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# NATゲートウェイ作成（プライベートサブネット用）
NAT_ID=$(oci network nat-gateway create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --display-name "${PROJECT_NAME}-nat" \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# プライベートサブネット用ルートテーブル
PRIVATE_RT_ID=$(oci network route-table create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --display-name "${PROJECT_NAME}-private-rt" \
    --route-rules '[{
        "destination": "0.0.0.0/0",
        "destinationType": "CIDR_BLOCK",
        "networkEntityId": "'$NAT_ID'"
    }]' \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# サービスゲートウェイ作成（OCIサービス用）
SG_ID=$(oci network service-gateway create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --display-name "${PROJECT_NAME}-sg" \
    --services '[{
        "serviceId": "'$(oci network service list --query "data[?contains(\"name\",'All')].id | [0]" --raw-output)'"
    }]' \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)
```

### 3.3 セキュリティリスト設定
```bash
# デフォルトセキュリティリスト取得
DEFAULT_SL_ID=$(oci network security-list list \
    --compartment-id $OCI_COMPARTMENT_ID \
    --vcn-id $VCN_ID \
    --query 'data[0].id' --raw-output)

# セキュリティルール更新
oci network security-list update \
    --security-list-id $DEFAULT_SL_ID \
    --egress-security-rules '[
        {
            "destination": "0.0.0.0/0",
            "protocol": "all",
            "isStateless": false
        }
    ]' \
    --ingress-security-rules '[
        {
            "source": "0.0.0.0/0",
            "protocol": "6",
            "tcpOptions": {
                "destinationPortRange": {
                    "min": 80,
                    "max": 80
                }
            },
            "isStateless": false
        },
        {
            "source": "0.0.0.0/0",
            "protocol": "6",
            "tcpOptions": {
                "destinationPortRange": {
                    "min": 443,
                    "max": 443
                }
            },
            "isStateless": false
        },
        {
            "source": "10.0.0.0/16",
            "protocol": "all",
            "isStateless": false
        }
    ]' --force
```

## 4. Autonomous Database構築

### 4.1 Autonomous Database作成
```bash
# ADB作成
ADB_ID=$(oci db autonomous-database create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --db-name csvbatchdb \
    --display-name "${PROJECT_NAME}-adb" \
    --db-workload OLTP \
    --is-free-tier false \
    --cpu-core-count 1 \
    --data-storage-size-in-gbs 20 \
    --admin-password "YourSecurePassword#123" \
    --wait-for-state AVAILABLE \
    --query 'data.id' --raw-output)

# 接続情報取得
ADB_CONNECTION=$(oci db autonomous-database get \
    --autonomous-database-id $ADB_ID \
    --query 'data."connection-strings"."profiles"[?contains("consumer-group",'HIGH')]."value" | [0]' \
    --raw-output)

echo "ADB Connection String: $ADB_CONNECTION"
```

### 4.2 ウォレット設定（オプション）
```bash
# ウォレットダウンロード
oci db autonomous-database generate-wallet \
    --autonomous-database-id $ADB_ID \
    --file wallet.zip \
    --password "WalletPassword#123"

# ウォレット展開
mkdir -p ~/wallet
unzip wallet.zip -d ~/wallet/
```

### 4.3 データベーススキーマ作成
```sql
-- SQLPlusまたはSQL Developer で実行
CREATE USER csvuser IDENTIFIED BY "CsvUserPassword#123";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO csvuser;
ALTER USER csvuser QUOTA UNLIMITED ON DATA;

-- テーブル作成
CREATE TABLE csvuser.employees (
    employee_id    NUMBER(10) PRIMARY KEY,
    employee_name  VARCHAR2(100) NOT NULL,
    department     VARCHAR2(50),
    email          VARCHAR2(100),
    hire_date      DATE,
    salary         NUMBER(10,2)
);

-- サンプルデータ投入
INSERT INTO csvuser.employees VALUES (1001, '田中太郎', '開発部', 'tanaka@example.com', DATE '2020-04-01', 500000);
INSERT INTO csvuser.employees VALUES (1002, '佐藤花子', '営業部', 'sato@example.com', DATE '2019-01-15', 450000);
COMMIT;
```

## 5. OCI Container Registry (OCIR) セットアップ

### 5.1 OCIRログイン
```bash
# Auth Token生成（OCI Console → User Settings → Auth Tokens）

# OCIRログイン
docker login ${OCI_REGION}.ocir.io
# Username: ${OCI_NAMESPACE}/${OCI_USERNAME}
# Password: <Auth Token>
```

### 5.2 Dockerイメージビルド・プッシュ
```bash
# アプリケーションビルド
cd /path/to/project
mvn clean package

# Dockerイメージビルド
docker build -t csv-batch-processor:latest .
docker build -t soap-stub:latest ./soap-stub

# タグ付け
docker tag csv-batch-processor:latest ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/csv-batch-processor:latest
docker tag soap-stub:latest ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/soap-stub:latest

# プッシュ
docker push ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/csv-batch-processor:latest
docker push ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/soap-stub:latest
```

## 6. Object Storage セットアップ

### 6.1 バケット作成
```bash
# バケット作成（アプリケーションでも自動作成可能）
oci os bucket create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --name $BUCKET_NAME \
    --public-access-type NoPublicAccess \
    --object-events-enabled true \
    --versioning Enabled

# ライフサイクルポリシー設定
cat > lifecycle-policy.json << EOF
{
    "items": [
        {
            "name": "archive-old-csv",
            "action": "ARCHIVE",
            "time-amount": 30,
            "time-unit": "DAYS",
            "is-enabled": true,
            "object-name-filter": {
                "inclusion-patterns": ["exports/*.csv"]
            }
        },
        {
            "name": "delete-old-csv",
            "action": "DELETE",
            "time-amount": 365,
            "time-unit": "DAYS",
            "is-enabled": true,
            "object-name-filter": {
                "inclusion-patterns": ["exports/*.csv"]
            }
        }
    ]
}
EOF

oci os object-lifecycle-policy put-object-lifecycle-policy \
    --bucket-name $BUCKET_NAME \
    --items file://lifecycle-policy.json
```

## 7. OCI Vault設定

### 7.1 Vault作成
```bash
# Vault作成
VAULT_ID=$(oci kms vault create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --display-name "${PROJECT_NAME}-vault" \
    --vault-type DEFAULT \
    --wait-for-state ACTIVE \
    --query 'data.id' --raw-output)

# マスター暗号鍵作成
KEY_ID=$(oci kms management key create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --display-name "${PROJECT_NAME}-master-key" \
    --key-shape '{"algorithm": "AES", "length": 32}' \
    --endpoint $(oci kms vault get --vault-id $VAULT_ID --query 'data."management-endpoint"' --raw-output) \
    --wait-for-state ENABLED \
    --query 'data.id' --raw-output)
```

### 7.2 シークレット作成
```bash
# データベースパスワード
oci vault secret create-base64 \
    --compartment-id $OCI_COMPARTMENT_ID \
    --secret-name "${PROJECT_NAME}-db-password" \
    --vault-id $VAULT_ID \
    --key-id $KEY_ID \
    --secret-content-content $(echo -n "CsvUserPassword#123" | base64)

# SOAP API認証情報（必要に応じて）
oci vault secret create-base64 \
    --compartment-id $OCI_COMPARTMENT_ID \
    --secret-name "${PROJECT_NAME}-soap-credentials" \
    --vault-id $VAULT_ID \
    --key-id $KEY_ID \
    --secret-content-content $(echo -n '{"username":"api-user","password":"api-pass"}' | base64)
```

## 8. OKE クラスター構築

### 8.1 OKEクラスター作成
```bash
# クラスター作成
CLUSTER_ID=$(oci ce cluster create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --name "${PROJECT_NAME}-cluster" \
    --kubernetes-version "v1.28.2" \
    --vcn-id $VCN_ID \
    --service-lb-subnet-ids "[\"$PUBLIC_SUBNET_ID\"]" \
    --wait-for-state ACTIVE \
    --query 'data.id' --raw-output)

# ノードプール作成
NODE_POOL_ID=$(oci ce node-pool create \
    --cluster-id $CLUSTER_ID \
    --compartment-id $OCI_COMPARTMENT_ID \
    --name "${PROJECT_NAME}-nodepool" \
    --kubernetes-version "v1.28.2" \
    --node-shape "VM.Standard.E4.Flex" \
    --node-shape-config '{"memory_in_gbs": 16, "ocpus": 2}' \
    --node-image-id $(oci compute image list --compartment-id $OCI_COMPARTMENT_ID --operating-system "Oracle Linux" --operating-system-version "8.8" --shape "VM.Standard.E4.Flex" --query 'data[0].id' --raw-output) \
    --size 2 \
    --placement-configs "[{\"availability-domain\": \"$(oci iam availability-domain list --query 'data[0].name' --raw-output)\", \"subnet-id\": \"$PRIVATE_SUBNET_ID\"}]" \
    --wait-for-state ACTIVE \
    --query 'data.id' --raw-output)

# kubeconfig取得
oci ce cluster create-kubeconfig \
    --cluster-id $CLUSTER_ID \
    --file $HOME/.kube/config \
    --region $OCI_REGION \
    --token-version 2.0.0 \
    --kube-endpoint PUBLIC_ENDPOINT

# 接続確認
kubectl get nodes
```

### 8.2 Kubernetes設定

#### 8.2.1 Namespace作成
```bash
kubectl create namespace csv-batch
kubectl config set-context --current --namespace=csv-batch
```

#### 8.2.2 シークレット作成
```bash
# OCIRシークレット
kubectl create secret docker-registry ocir-secret \
    --docker-server=${OCI_REGION}.ocir.io \
    --docker-username="${OCI_NAMESPACE}/${OCI_USERNAME}" \
    --docker-password="<Auth Token>" \
    --docker-email="user@example.com"

# データベース認証情報
kubectl create secret generic db-credentials \
    --from-literal=username=csvuser \
    --from-literal=password="CsvUserPassword#123" \
    --from-literal=url="$ADB_CONNECTION"

# OCI設定（インスタンスプリンシパル使用の場合は不要）
kubectl create secret generic oci-config \
    --from-literal=namespace=$OCI_NAMESPACE \
    --from-literal=bucket=$BUCKET_NAME \
    --from-literal=region=$OCI_REGION
```

#### 8.2.3 ConfigMap作成
```bash
cat > app-config.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: csv-batch-config
data:
  application.yaml: |
    server:
      port: 8080
    
    db:
      connection:
        url: \${DB_URL}
        username: \${DB_USERNAME}
        password: \${DB_PASSWORD}
    
    soap:
      endpoint: http://soap-stub-service:8080/ws
      timeout:
        connection: 30000
        read: 60000
    
    csv:
      output:
        path: /tmp/result.csv
      export:
        storage-upload: true
        local-backup: false
    
    oci:
      objectstorage:
        namespace: \${OCI_NAMESPACE}
        bucket: \${OCI_BUCKET}
        region: \${OCI_REGION}
        prefix: exports/
EOF

kubectl apply -f app-config.yaml
```

#### 8.2.4 Deployment作成
```bash
cat > csv-batch-deployment.yaml << EOF
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
      serviceAccountName: csv-batch-sa
      imagePullSecrets:
      - name: ocir-secret
      containers:
      - name: csv-batch-processor
        image: ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/csv-batch-processor:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        - name: OCI_NAMESPACE
          valueFrom:
            secretKeyRef:
              name: oci-config
              key: namespace
        - name: OCI_BUCKET
          valueFrom:
            secretKeyRef:
              name: oci-config
              key: bucket
        - name: OCI_REGION
          valueFrom:
            secretKeyRef:
              name: oci-config
              key: region
        volumeMounts:
        - name: config
          mountPath: /app/config
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
      volumes:
      - name: config
        configMap:
          name: csv-batch-config
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: soap-stub
spec:
  replicas: 1
  selector:
    matchLabels:
      app: soap-stub
  template:
    metadata:
      labels:
        app: soap-stub
    spec:
      imagePullSecrets:
      - name: ocir-secret
      containers:
      - name: soap-stub
        image: ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/soap-stub:latest
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
EOF

kubectl apply -f csv-batch-deployment.yaml
```

#### 8.2.5 Service作成
```bash
cat > csv-batch-service.yaml << EOF
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
---
apiVersion: v1
kind: Service
metadata:
  name: soap-stub-service
spec:
  selector:
    app: soap-stub
  ports:
  - port: 8080
    targetPort: 8080
  type: ClusterIP
EOF

kubectl apply -f csv-batch-service.yaml
```

#### 8.2.6 ServiceAccount（インスタンスプリンシパル用）
```bash
cat > service-account.yaml << EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: csv-batch-sa
  annotations:
    oci.oraclecloud.com/workload-identity: "ocid1.workloadidentity.oc1..xxxxx"
EOF

kubectl apply -f service-account.yaml
```

### 8.3 動的グループとポリシー設定
```bash
# 動的グループ作成（ワークロードアイデンティティ用）
oci iam dynamic-group create \
    --name "${PROJECT_NAME}-workload-identity" \
    --description "Workload identity for CSV Batch Processor" \
    --matching-rule "ALL {resource.type = 'workloadidentity', resource.compartment.id = '$OCI_COMPARTMENT_ID'}"

# IAMポリシー作成
cat > iam-policy.json << EOF
[
  "Allow dynamic-group ${PROJECT_NAME}-workload-identity to manage objects in compartment id $OCI_COMPARTMENT_ID where target.bucket.name='$BUCKET_NAME'",
  "Allow dynamic-group ${PROJECT_NAME}-workload-identity to read autonomous-databases in compartment id $OCI_COMPARTMENT_ID",
  "Allow dynamic-group ${PROJECT_NAME}-workload-identity to read secrets in compartment id $OCI_COMPARTMENT_ID"
]
EOF

oci iam policy create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --name "${PROJECT_NAME}-policy" \
    --description "Policy for CSV Batch Processor" \
    --statements file://iam-policy.json
```

## 9. 監視・ログ設定

### 9.1 OCI Monitoring設定
```bash
# メトリクス名前空間作成
oci monitoring metric create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --namespace "${PROJECT_NAME}_metrics" \
    --name "records_processed" \
    --dimensions '{"deployment": "production"}'

# アラーム作成
oci monitoring alarm create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --display-name "${PROJECT_NAME}-health-alarm" \
    --metric-compartment-id $OCI_COMPARTMENT_ID \
    --namespace "oci_kubernetesengine" \
    --query "PodStatus[5m].mean() < 1" \
    --severity "CRITICAL" \
    --body "CSV Batch Processor pods are unhealthy" \
    --pending-duration "PT5M" \
    --destinations "[]"
```

### 9.2 OCI Logging設定
```bash
# ログループ作成
LOG_GROUP_ID=$(oci logging log-group create \
    --compartment-id $OCI_COMPARTMENT_ID \
    --display-name "${PROJECT_NAME}-logs" \
    --description "Logs for CSV Batch Processor" \
    --wait-for-state ACTIVE \
    --query 'data.id' --raw-output)

# アプリケーションログ
oci logging log create \
    --log-group-id $LOG_GROUP_ID \
    --display-name "${PROJECT_NAME}-app-logs" \
    --log-type CUSTOM \
    --is-enabled true \
    --retention-duration 30

# 監査ログ
oci logging log create \
    --log-group-id $LOG_GROUP_ID \
    --display-name "${PROJECT_NAME}-audit-logs" \
    --log-type SERVICE \
    --configuration '{
        "source": {
            "source-type": "OCISERVICE",
            "service": "objectstorage",
            "resource": "'$BUCKET_NAME'",
            "category": "write"
        }
    }' \
    --is-enabled true
```

## 10. CronJob設定（定期実行）

```bash
cat > csv-batch-cronjob.yaml << EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: csv-batch-cronjob
spec:
  schedule: "0 2 * * *"  # 毎日午前2時
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: csv-batch-sa
          imagePullSecrets:
          - name: ocir-secret
          containers:
          - name: csv-batch-processor
            image: ${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${PROJECT_NAME}/csv-batch-processor:latest
            env:
            - name: BATCH_MODE
              value: "true"
            envFrom:
            - secretRef:
                name: db-credentials
            - secretRef:
                name: oci-config
          restartPolicy: OnFailure
EOF

kubectl apply -f csv-batch-cronjob.yaml
```

## 11. トラブルシューティング

### 11.1 ポッド状態確認
```bash
# ポッド一覧
kubectl get pods

# ポッド詳細
kubectl describe pod <pod-name>

# ログ確認
kubectl logs -f deployment/csv-batch-processor

# シェルアクセス
kubectl exec -it deployment/csv-batch-processor -- /bin/bash
```

### 11.2 Autonomous Database接続テスト
```bash
# ポッド内から接続テスト
kubectl exec deployment/csv-batch-processor -- java -cp /app/app.jar \
    -Ddb.url="$ADB_CONNECTION" \
    -Ddb.username=csvuser \
    -Ddb.password="CsvUserPassword#123" \
    com.example.TestConnection
```

### 11.3 Object Storage権限確認
```bash
# ServiceAccountの確認
kubectl get serviceaccount csv-batch-sa -o yaml

# ワークロードアイデンティティ確認
oci iam workload-identity get --workload-identity-id <workload-identity-ocid>

# Object Storage アクセステスト
kubectl exec deployment/csv-batch-processor -- \
    oci os object list --bucket-name $BUCKET_NAME --auth instance_principal
```

## 12. クリーンアップ

```bash
# Kubernetesリソース削除
kubectl delete namespace csv-batch

# OKEクラスター削除
oci ce cluster delete --cluster-id $CLUSTER_ID --force

# Autonomous Database削除
oci db autonomous-database delete --autonomous-database-id $ADB_ID --force

# Object Storageバケット削除
oci os object bulk-delete --bucket-name $BUCKET_NAME --force
oci os bucket delete --bucket-name $BUCKET_NAME --force

# VCN削除
oci network vcn delete --vcn-id $VCN_ID --force

# Vault削除
oci kms vault schedule-deletion --vault-id $VAULT_ID --time-of-deletion $(date -u -d "+7 days" +"%Y-%m-%dT%H:%M:%S.%3NZ")
```

## 13. ベストプラクティス

### 13.1 セキュリティ
- インスタンスプリンシパルまたはワークロードアイデンティティを使用
- シークレットはOCI Vaultで管理
- ネットワークは最小権限で設定
- 監査ログを有効化

### 13.2 可用性
- マルチADにノードを配置
- Autonomous Databaseの自動バックアップを活用
- ヘルスチェックとreadiness/livenessプローブを設定
- 適切なリソースリミットを設定

### 13.3 パフォーマンス
- HPA (Horizontal Pod Autoscaler) を設定
- Object Storageのマルチパートアップロードを活用
- 接続プーリングを適切に設定
- メトリクスベースの最適化

### 13.4 運用
- GitOpsワークフローの実装
- CI/CDパイプラインの構築
- 定期的なセキュリティパッチ適用
- ディザスタリカバリ計画

---

**最終更新**: 2025-08-06 - OCI本番環境デプロイメントガイド