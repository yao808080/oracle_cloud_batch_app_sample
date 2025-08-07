# AWS/OCI技術比較表 - CSVバッチプロセッサー

## 1. 概要

このドキュメントは、AWSベースのCSVバッチプロセッサーアプリケーションをOracle Cloud Infrastructure (OCI)へ移行する際の技術比較を示しています。

## 2. クラウドサービス対応表

### 2.1 コンピューティング

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| コンテナオーケストレーション | Amazon ECS (Fargate) | OCI Container Instances / OKE | OCIはKubernetes(OKE)とContainer Instancesの2つの選択肢 |
| サーバーレスコンテナ | AWS Fargate | OCI Container Instances | フルマネージドコンテナ実行環境 |
| Kubernetes | Amazon EKS | Oracle Container Engine for Kubernetes (OKE) | マネージドKubernetesサービス |
| コンテナレジストリ | Amazon ECR | OCI Container Registry (OCIR) | プライベートコンテナイメージレジストリ |

### 2.2 データベース

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| Oracle Database | Amazon RDS for Oracle | OCI Database Service | OCIではAutonomous Databaseも選択可能 |
| 自律型データベース | - | Oracle Autonomous Database | 自動管理、自動チューニング、自動パッチ適用 |
| データベース暗号化 | RDS暗号化 | Transparent Data Encryption (TDE) | OCIはOracle標準のTDEを使用 |
| バックアップ | RDS自動バックアップ | 自動バックアップ / Object Storage | OCIは30日間の自動バックアップ |

### 2.3 ストレージ

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| オブジェクトストレージ | Amazon S3 | OCI Object Storage | 互換性のあるS3 APIを提供 |
| ストレージクラス | S3 Standard/IA/Glacier | Standard/Infrequent Access/Archive | 類似の階層化ストレージ |
| 暗号化 | SSE-S3/SSE-KMS | デフォルト暗号化 | OCIは全データをデフォルトで暗号化 |
| アクセス制御 | S3バケットポリシー/IAM | IAMポリシー/事前認証リクエスト | OCIの事前認証リクエストは一時的なアクセスURL |

### 2.4 ネットワーク

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| 仮想ネットワーク | VPC | Virtual Cloud Network (VCN) | 概念はほぼ同じ |
| サブネット | Public/Private Subnet | Public/Private Subnet | 同等の機能 |
| ロードバランサー | ALB/NLB | Load Balancer | Layer 7/Layer 4対応 |
| プライベート接続 | VPC Endpoint | Service Gateway/Private Endpoint | OCIサービスへのプライベートアクセス |
| NAT | NAT Gateway | NAT Gateway | アウトバウンド接続用 |

### 2.5 セキュリティ・アイデンティティ

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| アイデンティティ管理 | AWS IAM | OCI IAM | ポリシーベースのアクセス制御 |
| シークレット管理 | AWS Secrets Manager | OCI Vault | 暗号鍵とシークレットの管理 |
| 暗号鍵管理 | AWS KMS | OCI Key Management | HSMバックエンドの鍵管理 |
| 監査ログ | AWS CloudTrail | OCI Audit | API呼び出しの記録 |
| 脅威検出 | Amazon GuardDuty | OCI Cloud Guard | セキュリティ問題の自動検出 |

### 2.6 監視・管理

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| メトリクス監視 | Amazon CloudWatch | OCI Monitoring | メトリクス収集と監視 |
| ログ管理 | CloudWatch Logs | OCI Logging | 集中ログ管理 |
| アラート | CloudWatch Alarms | OCI Alarms | しきい値ベースのアラート |
| イベント駆動 | Amazon EventBridge | OCI Events | イベントベースの自動化 |
| リソース管理 | AWS Config | OCI Resource Manager | インフラのコード化(Terraform対応) |

### 2.7 開発者ツール

| サービス機能 | AWS | OCI | 備考 |
|------------|-----|-----|------|
| CI/CD | AWS CodePipeline | OCI DevOps | パイプライン管理 |
| ビルドサービス | AWS CodeBuild | OCI DevOps Build | マネージドビルド環境 |
| デプロイメント | AWS CodeDeploy | OCI DevOps Deployment | 自動デプロイメント |
| Infrastructure as Code | CloudFormation | Resource Manager (Terraform) | OCIはTerraformネイティブ |

## 3. アプリケーションフレームワーク比較

### 3.1 バッチ処理フレームワーク

| 機能 | AWS推奨 | OCI推奨 | 備考 |
|-----|---------|---------|------|
| バッチフレームワーク | Spring Cloud + Spring Batch | Helidon + Oracle Database Job Scheduler | HelidonはOracleが開発するマイクロサービスフレームワーク |
| スケジューリング | EventBridge + ECS | OCI Functions + OCI Events | サーバーレスアプローチも可能 |
| リトライ処理 | Spring Retry | Resilience4j (Helidon内蔵) | フォールトトレランス機能 |
| サーキットブレーカー | Resilience4j | Resilience4j (Helidon内蔵) | 同じライブラリを使用可能 |

### 3.2 開発環境

| 機能 | AWS | OCI | 備考 |
|-----|-----|-----|------|
| ローカルエミュレーション | LocalStack | OCI Local Testing Framework | OCIサービスのローカルテスト |
| コンテナ開発 | Docker + Docker Compose | Docker + Docker Compose | 同じツールを使用 |
| SDK | AWS SDK for Java | OCI SDK for Java | 類似のAPI設計 |

## 4. 移行における主要な変更点

### 4.1 認証・認可

| 項目 | AWS | OCI |
|------|-----|-----|
| サービス認証 | IAMロール/アクセスキー | インスタンスプリンシパル/APIキー |
| リソースポリシー | リソースベースポリシー | コンパートメントベースポリシー |
| 一時認証 | STS | Resource Principal |

### 4.2 ストレージAPI

| 操作 | AWS S3 | OCI Object Storage |
|------|--------|-------------------|
| オブジェクトアップロード | PutObject | PutObject |
| オブジェクトダウンロード | GetObject | GetObject |
| 一覧取得 | ListObjectsV2 | ListObjects |
| マルチパートアップロード | CreateMultipartUpload | CreateMultipartUpload |
| S3互換API | ネイティブ | S3互換エンドポイント利用可能 |

### 4.3 データベース接続

| 項目 | AWS RDS | OCI Database |
|------|---------|--------------|
| 接続文字列 | JDBC Thin | JDBC Thin (同じ) |
| 自動フェイルオーバー | RDS Multi-AZ | Data Guard |
| 接続プール | HikariCP | UCP (Universal Connection Pool) |
| 暗号化接続 | SSL/TLS | Native Network Encryption + SSL/TLS |

## 5. コスト比較の観点

### 5.1 料金モデルの違い

| サービス | AWS | OCI |
|---------|-----|-----|
| コンピュート | 秒単位課金 | 秒単位課金 |
| ストレージ | GB/月 + リクエスト数 | GB/月 + リクエスト数 |
| データ転送 | アウトバウンド課金 | 月10TBまで無料 |
| データベース | インスタンス時間 | OCPU時間またはAutonomous |

### 5.2 コスト最適化機能

| 機能 | AWS | OCI |
|------|-----|-----|
| 予約インスタンス | Reserved Instances | Reserved Capacity |
| 自動スケーリング | Auto Scaling | Autoscaling |
| 無料枠 | Free Tier (12ヶ月) | Always Free + Free Tier |

## 6. 移行推奨事項

### 6.1 段階的移行アプローチ

1. **Phase 1**: 開発環境の構築
   - OCI環境のセットアップ
   - ネットワーク構成
   - IAMポリシー設定

2. **Phase 2**: データベース移行
   - Oracle Database ServiceまたはAutonomous Databaseの選択
   - データ移行（Data Pump/GoldenGate）
   - 接続設定の更新

3. **Phase 3**: アプリケーション移行
   - Helidonフレームワークへの移行評価
   - またはSpring BootアプリケーションをそのままOKEで実行
   - Container Registryへのイメージプッシュ

4. **Phase 4**: ストレージ移行
   - Object Storageバケット作成
   - S3互換APIの活用
   - データ移行

5. **Phase 5**: 本番切り替え
   - 監視・アラート設定
   - バックアップ設定
   - 性能テスト

### 6.2 技術選定の推奨

| コンポーネント | 推奨構成 | 理由 |
|--------------|---------|------|
| データベース | Autonomous Database | 自動管理、高可用性、コスト効率 |
| コンテナ実行環境 | OKE (Kubernetes) | 標準的、ポータブル、エコシステム |
| バッチフレームワーク | Spring Boot (現状維持) or Helidon | 移行コスト vs OCIネイティブ |
| ストレージアクセス | S3互換API | コード変更最小化 |

## 7. 注意事項

### 7.1 OCIの利点
- Oracle Databaseとのネイティブ統合
- 優れたコストパフォーマンス（特にデータ転送）
- Autonomous Databaseによる運用負荷軽減
- エンタープライズグレードのセキュリティ

### 7.2 移行時の課題
- AWS固有サービスの代替検討が必要
- 運用チームのOCIスキル習得
- 既存CI/CDパイプラインの更新
- 監視ツールの再構築

### 7.3 互換性の確保
- S3互換APIによるコード変更の最小化
- Kubernetesによるコンテナの可搬性
- 標準的なJDBC接続の維持
- オープンソースツールの活用

---

**更新日**: 2025-08-06  
**ステータス**: PoC環境構築用ドラフト版