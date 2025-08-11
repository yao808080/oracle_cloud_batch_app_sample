# Kubernetes デプロイメントガイド

## 概要

CSV Batch ProcessorのKubernetesデプロイメント設定ファイル群です。Oracle Container Engine for Kubernetes (OKE) または他のKubernetesクラスターでの本番運用を想定した設定になっています。

## 📂 ファイル構成

| ファイル | 内容 | 説明 |
|---------|------|------|
| `deployment.yaml` | Deployment, Service, Ingress | メインアプリケーションの定義 |
| `configmap.yaml` | ConfigMap群 | アプリケーション設定 |
| `secrets.yaml` | Secret, ServiceAccount, RBAC | セキュリティ設定 |

## 🚀 現在の状況

### ✅ 利用可能
- **Kubernetes設定ファイル**: 完全に準備済み
- **kubectl CLI**: インストール済み (v1.32.2)
- **Docker Desktop**: 利用可能

### ❌ 未設定
- **Kubernetesクラスター接続**: 設定されていません
- **kubeconfig**: コンテキストが空

## 📋 デプロイメント前提条件

### 1. Kubernetesクラスターの準備

#### Option A: Docker Desktop Kubernetes (ローカル開発)
```bash
# Docker Desktopで Kubernetes を有効化
# Settings → Kubernetes → Enable Kubernetes

# コンテキスト確認
kubectl config get-contexts
kubectl config use-context docker-desktop
```

#### Option B: Oracle Container Engine for Kubernetes (OKE)
```bash
# OCI CLIでkubeconfigを設定
oci ce cluster create-kubeconfig --cluster-id <cluster-ocid> --file $HOME/.kube/config --region us-ashburn-1

# クラスター接続確認
kubectl cluster-info
```

#### Option C: 他のクラウドプロバイダー
```bash
# AWS EKS
aws eks update-kubeconfig --region us-west-2 --name csv-batch-cluster

# Azure AKS
az aks get-credentials --resource-group myResourceGroup --name csv-batch-cluster

# Google GKE
gcloud container clusters get-credentials csv-batch-cluster --zone us-central1-a
```

### 2. 必要な設定値の更新

#### secrets.yaml の更新
```yaml
stringData:
  url: "jdbc:oracle:thin:@your-actual-db:1521/service_name"
  username: "your-actual-username"
  password: "your-actual-password"
```

#### configmap.yaml の OCI設定更新
```yaml
data:
  namespace: "your-actual-oci-namespace"
  bucket: "your-actual-bucket-name"
```

#### deployment.yaml のイメージ更新
```yaml
image: your-region.ocir.io/your-namespace/csv-batch-processor:1.0.0
```

## 🛠️ デプロイメント手順

### 1. Namespace作成
```bash
kubectl create namespace csv-batch
```

### 2. Secrets適用
```bash
kubectl apply -f k8s/secrets.yaml
```

### 3. ConfigMap適用
```bash
kubectl apply -f k8s/configmap.yaml
```

### 4. アプリケーションデプロイ
```bash
kubectl apply -f k8s/deployment.yaml
```

### 5. デプロイメント確認
```bash
# Pod状態確認
kubectl get pods -n csv-batch

# Service確認
kubectl get svc -n csv-batch

# Ingress確認
kubectl get ingress -n csv-batch

# ログ確認
kubectl logs -f deployment/csv-batch-processor -n csv-batch
```

## 🔧 主要機能

### セキュリティ機能
- **非root実行**: UID 1000で実行
- **読み取り専用ルートファイルシステム**
- **権限エスカレーション無効化**
- **最小権限RBAC**

### 可観測性
- **Prometheus メトリクス**: `/metrics`エンドポイント
- **ヘルスチェック**: Liveness/Readiness Probe
- **ログ出力**: 構造化ログ

### 高可用性
- **レプリカ数**: 2個のPod
- **ローリングアップデート**: ダウンタイムなし更新
- **リソース制限**: CPU/メモリ制限設定

### OCI統合
- **Workload Identity**: OCI IAMとの統合
- **OCI Vault**: シークレット管理
- **Oracle Autonomous Database**: 接続設定済み

## 🧪 動作確認

### ローカル確認（Port Forward）
```bash
# アプリケーションへのアクセス
kubectl port-forward svc/csv-batch-processor-service 8080:80 -n csv-batch

# ヘルスチェック
curl http://localhost:8080/health

# メトリクス確認  
curl http://localhost:8080/metrics
```

### クラスター内確認
```bash
# Pod内でのテスト実行
kubectl exec -it deployment/csv-batch-processor -n csv-batch -- curl http://localhost:8080/health
```

## 🚨 トラブルシューティング

### よくある問題

#### 1. Pod起動失敗
```bash
kubectl describe pod <pod-name> -n csv-batch
kubectl logs <pod-name> -n csv-batch
```

#### 2. データベース接続エラー
```bash
# Secret確認
kubectl get secret database-secret -n csv-batch -o yaml

# 接続テスト
kubectl exec -it deployment/csv-batch-processor -n csv-batch -- env | grep DB_
```

#### 3. OCI認証エラー
```bash
# ServiceAccount確認
kubectl get sa csv-batch-sa -n csv-batch -o yaml

# Workload Identity設定確認
kubectl describe sa csv-batch-sa -n csv-batch
```

## 📊 リソース要件

### 最小要件
- **CPU**: 250m (0.25コア)
- **メモリ**: 512Mi

### 推奨設定
- **CPU**: 1000m (1コア) 
- **メモリ**: 2Gi
- **ストレージ**: 10Gi (PV使用時)

## 🔄 CI/CD統合

### GitHub Actions例
```yaml
- name: Deploy to Kubernetes
  run: |
    kubectl apply -f k8s/ --recursive
    kubectl rollout status deployment/csv-batch-processor -n csv-batch
```

### OCI DevOps統合
```yaml
- name: OKE Deploy
  uses: oracle-actions/run-oci-cli-command@v1.1
  with:
    command: 'kubectl apply -f k8s/'
```

---

## 📞 次のステップ

現在の状況でKubernetesを利用するには：

1. **Kubernetesクラスターの準備** (Docker Desktop推奨)
2. **kubeconfig設定**
3. **設定ファイルの値更新**
4. **デプロイメント実行**

Kubernetesクラスターが利用可能になり次第、本番グレードのデプロイメントが可能です。