# CSV バッチプロセッサー - Helidon MP 実装ドキュメント

## 概要

AWS版CSVバッチプロセッサーをOracle Cloud Infrastructure (OCI)に移植した**Helidon MicroProfile 4.0.11**実装プロジェクトです。実用的なCSVデータ処理、Object Storage統合、およびSOAPクライアント連携を含む、本番環境対応のバッチ処理システムです。

### 🚀 実装技術スタック
- **Helidon MP 4.0.11**: MicroProfile 6.0準拠のマイクロサービスフレームワーク
- **Java 21**: 最新のLTS版Java実行環境
- **PostgreSQL 15**: 開発・テスト用データベース
- **LocalStack**: S3互換Object Storageのローカルエミュレーション
- **WireMock**: SOAP APIモックサーバー
- **Docker Compose**: 開発環境の統合オーケストレーション

---

## 📁 ドキュメント一覧

### 🔧 実装・開発ドキュメント
| ドキュメント | 概要 | ステータス |
|------------|------|----------|
| [**testing-guide.md**](./testing-guide.md) | **統合テストガイド** | ✅ 実装完了 |
| [**deployment-guide.md**](./deployment-guide.md) | **デプロイメントガイド** | ✅ 実装完了 |
| [**api-specification.md**](./api-specification.md) | **REST API仕様書** | 📝 更新中 |

### 🏗️ アーキテクチャ・設計ドキュメント
| ドキュメント | 概要 | ステータス |
|------------|------|----------|
| [architecture.md](./architecture.md) | システムアーキテクチャ設計 | 📝 更新中 |
| [SECURITY.md](./SECURITY.md) | セキュリティ実装ガイド | 📝 更新中 |
| [infrastructure-as-code.md](./infrastructure-as-code.md) | Infrastructure as Code | 📝 更新中 |
| [oci-deployment-guide.md](./oci-deployment-guide.md) | OCI本番環境デプロイメント | 📝 更新中 |

### 📚 参考ドキュメント
| ドキュメント | 概要 | ステータス |
|------------|------|----------|
| [technical-comparison.md](./technical-comparison.md) | AWS/OCI技術比較 | ✅ 完成 |
| [helidon-oci-technical-dialogue.md](./helidon-oci-technical-dialogue.md) | Helidon+OCI技術対話 | ✅ 完成 |

---

## 🚀 クイックスタート

### 1. 開発環境のセットアップ
```bash
# リポジトリのクローン
git clone https://github.com/your-org/oracle_cloud_batch_app_sample.git
cd oracle_cloud_batch_app_sample

# Docker Composeでテスト環境を起動
docker-compose -f docker-compose.test.minimal.yml up -d

# Mavenビルド
mvn clean package
```

### 2. アプリケーションの起動
```bash
# Helidonアプリケーションの起動
java -jar target/csv-batch-processor.jar
```

### 3. テストの実行
```bash
# 単体テストの実行
mvn test

# 統合テストの実行（Docker環境が必要）
mvn test -DRUN_INTEGRATION_TESTS=true
```

---

## 📂 プロジェクト構造

```
oracle_cloud_batch_app_sample/
├── src/
│   ├── main/
│   │   ├── java/com/example/csvbatch/
│   │   │   ├── client/           # SOAPクライアント
│   │   │   ├── config/           # 設定クラス
│   │   │   ├── dto/              # データ転送オブジェクト
│   │   │   ├── entity/           # JPA エンティティ
│   │   │   ├── exception/        # カスタム例外
│   │   │   ├── health/           # ヘルスチェック
│   │   │   ├── repository/       # データアクセス層
│   │   │   ├── resource/         # REST APIエンドポイント
│   │   │   ├── service/          # ビジネスロジック
│   │   │   └── Main.java         # エントリーポイント
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── microprofile-config.properties
│   │       │   └── persistence.xml
│   │       └── application.yaml
│   └── test/                     # テストコード
├── docker-compose.test.minimal.yml  # 軽量テスト環境
├── Dockerfile                    # コンテナビルド定義
└── pom.xml                      # Maven設定
```

---

## 🔌 実装済み機能

### REST API エンドポイント
- **POST /api/csv/export** - CSVエクスポート実行
- **GET /api/csv/status** - サービスステータス確認
- **GET /api/csv/files** - CSVファイル一覧取得
- **GET /api/csv/download/{fileName}** - CSVファイルダウンロード
- **DELETE /api/csv/files/{fileName}** - CSVファイル削除

### ヘルスチェック
- **/health** - アプリケーション全体のヘルス状態
- **/health/live** - Liveness Probe
- **/health/ready** - Readiness Probe
- **/health/started** - Startup Probe

### メトリクス
- **/metrics** - Prometheus形式のメトリクス
- カスタムメトリクス: API呼び出し回数、処理時間など

---

## 🧪 テスト環境

### Docker Composeサービス構成
```yaml
services:
  soap-stub:        # WireMock (SOAP APIモック)
    port: 8081
  localstack:       # S3互換Object Storage
    port: 4566
  test-db:          # PostgreSQL 15
    port: 5432
```

### テスト実行統計
- **単体テスト**: 30件
- **統合テスト**: 7件
- **カバレッジ**: 約70%

---

## 🔧 設定・環境変数

### 必須環境変数
```bash
# データベース接続
DB_URL=jdbc:postgresql://localhost:5432/testdb
DB_USER=testuser
DB_PASSWORD=testpass

# Object Storage (LocalStack/OCI)
OBJECT_STORAGE_ENDPOINT=http://localhost:4566
OBJECT_STORAGE_BUCKET=test-bucket

# SOAP API エンドポイント
SOAP_API_URL=http://localhost:8081/soap
```

### MicroProfile設定
`src/main/resources/META-INF/microprofile-config.properties`で詳細設定が可能

---

## 📊 実装の特徴

### Helidon MP 4.0.11の活用
- **MicroProfile 6.0準拠**: 標準仕様に基づく実装
- **リアクティブストリーム**: 非同期処理のサポート
- **CDI 4.0**: 依存性注入の活用
- **JAX-RS 3.1**: RESTful APIの実装

### エラーハンドリング
- カスタム例外クラスによる体系的なエラー管理
- Resilience4jによるリトライ・サーキットブレーカー実装
- 詳細なエラーログとメトリクス記録

### セキュリティ対策
- JWT認証（実装準備済み）
- HTTPS対応
- セキュリティヘッダーの設定

---

## 🚧 今後の開発予定

### Phase 1: 基本機能強化
- [ ] バッチ処理のスケジューリング機能
- [ ] より詳細なエラーレポート機能
- [ ] CSVファイルの検証機能強化

### Phase 2: OCI統合
- [ ] OCI Object Storage Native SDK統合
- [ ] OCI Autonomous Database接続
- [ ] OCI Vault によるシークレット管理

### Phase 3: 本番環境対応
- [ ] Kubernetes (OKE) デプロイメント設定
- [ ] 包括的な監視・アラート設定
- [ ] パフォーマンスチューニング

---

## 📚 参考リンク

### 公式ドキュメント
- **Helidon**: https://helidon.io/docs/v4/
- **MicroProfile**: https://microprofile.io/
- **OCI Documentation**: https://docs.oracle.com/iaas/

### 開発ツール
- **Docker**: https://docs.docker.com/
- **LocalStack**: https://docs.localstack.cloud/
- **WireMock**: https://wiremock.org/docs/

---

**最終更新**: 2025-08-07  
**バージョン**: 1.0.0-SNAPSHOT  
**ステータス**: 開発環境で動作確認済み

**📝 このドキュメントは実装の現状を反映しています。**