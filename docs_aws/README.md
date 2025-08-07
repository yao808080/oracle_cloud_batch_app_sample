# ドキュメント一覧

**CSV バッチプロセッサー** プロジェクトの包括的なドキュメント集

## 📋 メインドキュメント

### 🔒 [SECURITY.md](./SECURITY.md) ⭐ **重要**
**セキュリティガイド・パブリックリリース準備**
- セキュリティ対策の実装状況
- 環境変数設定（DB_PASSWORD必須化）
- AWS S3統合セキュリティ
- Spring Retryセキュリティ
- パブリックリリース評価
- 本番環境デプロイ前チェックリスト

### 🏗️ [architecture.md](./architecture.md)
**システム設計・アーキテクチャ**
- Spring Cloud 2023.0.0 システム構成
- 技術スタックと依存関係
- データモデルとクラス設計
- 運用・保守設計

### 🔌 [api-specification.md](./api-specification.md)
**API仕様・データ形式**
- SOAP Web Service仕様（Spring Retry対応）
- AWS S3 API仕様
- CSV出力形式仕様
- カスタム例外処理仕様
- パフォーマンス仕様

## 🚀 デプロイメント

### 🚀 [deployment-guide.md](./deployment-guide.md)
**ローカル環境デプロイ**
- 環境要件（Docker、LocalStack）
- セキュリティ強化設定手順
- Docker Compose実行
- トラブルシューティング

### ☁️ [aws-deployment-guide.md](./aws-deployment-guide.md)
**AWS本番デプロイ**
- ECS Fargate + RDS + S3構成
- Secrets Manager統合
- IAM権限設定
- CloudWatch監視

## 🧪 品質保証

### 🧪 [testing-guide.md](./testing-guide.md)
**テスト戦略・実行**
- 80個のテスト（単体61個 + 統合19個）
- TestContainers統合テスト
- Spring Retryテスト
- S3統合テスト
- セキュリティテスト

## クイックナビゲーション

| 目的 | 参照ドキュメント |
|------|-----------------|
| **🚀 すぐに始める** | [メインREADME](../README.md) |
| **🔒 セキュリティ確認** | [SECURITY.md](./SECURITY.md) |
| **💻 ローカル実行** | [deployment-guide.md](./deployment-guide.md) |
| **☁️ AWS本番運用** | [aws-deployment-guide.md](./aws-deployment-guide.md) |
| **🧪 テスト実行** | [testing-guide.md](./testing-guide.md) |
| **🔧 技術詳細** | [architecture.md](./architecture.md) |

## プロジェクト概要

**Spring Cloud 2023.0.0** で構築されたエンタープライズバッチアプリケーション

### 主要機能
- **データ統合**: Oracle DB + SOAP API + AWS S3
- **レジリエンス**: Spring Retry + サーキットブレーカー
- **セキュリティ**: 環境変数管理 + 機密情報保護
- **クラウド**: LocalStack開発環境 + AWS本番対応

### 技術ハイライト
- **Java 21** + **Spring Cloud 2023.0.0** + **Spring Boot 3.2.0**
- **AWS S3統合** (LocalStack + 本番AWS)
- **カスタム例外処理** (機密情報保護)
- **包括的テスト** (TestContainers + 統合テスト)

---

**最終更新**: 2025-08-02 - ソースコード修正に合わせた最新化完了