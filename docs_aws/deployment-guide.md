# デプロイメントガイド

## 1. 環境要件

### 1.1 システム要件
- **OS**: Linux (Ubuntu 20.04+推奨) / Windows 10+ / macOS 11+
- **Docker**: 20.10.0以上
- **Docker Compose**: 2.0.0以上
- **メモリ**: 最小4GB、推奨8GB以上（LocalStack含む）
- **ディスク容量**: 最小10GB、推奨20GB以上
- **AWS LocalStack**: 開発環境でのクラウドサービスエミュレーション

### 1.2 ネットワーク要件
- **ポート**: 1521, 8080, 8081, 4566が利用可能であること
- **インターネット接続**: Dockerイメージダウンロードのため必要
- **LocalStack**: ポート4566でS3/SQS/SNS等のエミュレーション

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
# プロジェクトクローン（実際のリポジトリから）
git clone <repository-url>
cd claude_code_sample_csv_batch

# または、ファイル一式をコピー
```

### 2.3 ディレクトリ構造確認
```
claude_code_sample_csv_batch/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── src/
├── soap-stub/
├── oracle-init/
└── output/
```

## 3. 設定ファイル

### 3.1 環境変数設定（必須）
**⚠️ セキュリティ強化により環境変数設定が必須になりました**

```bash
# .envファイルを作成（.env.exampleからコピー）
cp .env.example .env

# .envファイルを編集
cat > .env << EOF
# データベース設定（必須）
ORACLE_PASSWORD=your_secure_oracle_password
DB_USERNAME=csvuser
DB_PASSWORD=your_secure_csvuser_password
DB_URL=jdbc:oracle:thin:@oracle-db:1521/XEPDB1

# AWS設定（LocalStack用）
AWS_ACCESS_KEY_ID=dummy
AWS_SECRET_ACCESS_KEY=dummy
AWS_ENDPOINT=http://localstack:4566
AWS_S3_BUCKET=csv-export-bucket
AWS_DEFAULT_REGION=us-east-1

# CSV出力設定
CSV_OUTPUT_PATH=/app/output/result.csv
CSV_EXPORT_S3_UPLOAD=true
CSV_EXPORT_LOCAL_BACKUP=true
CSV_EXPORT_ENABLED=true

# SOAP API設定
SOAP_API_URL=http://soap-stub:8080/ws
EOF

# 注意：.envファイルは絶対にGitにコミットしないでください
echo ".env" >> .gitignore
```

### 3.2 セキュリティ設定の確認
```bash
# .envファイルの権限を制限
chmod 600 .env

# 機密情報が含まれていないことを確認
grep -v "password\|secret\|key" .env || echo "機密情報が検出されました。設定を確認してください。"
```

### 3.3 出力ディレクトリ準備
```bash
# 出力ディレクトリが存在しない場合は作成
mkdir -p output
chmod 755 output

# LocalStack用データディレクトリ作成
mkdir -p localstack_data
chmod 755 localstack_data
```

## 4. デプロイメント手順

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
# 1. LocalStackとデータベースを起動
docker-compose up -d localstack oracle-db

# 2. LocalStackの健康状態確認
curl -f http://localhost:4566/_localstack/health

# 3. データベースの健康状態確認
docker-compose exec oracle-db sqlplus -L system/oracle@//localhost:1521/XE <<<'SELECT 1 FROM DUAL;'

# 4. SOAPスタブ起動
docker-compose up -d soap-stub

# 5. SOAPスタブの健康状態確認
curl -f http://localhost:8081/ws/employees.wsdl

# 6. メインアプリケーション起動（自動起動順序制御）
docker-compose up -d csv-batch-processor

# 7. S3バケット自動作成確認（アプリケーション起動時に実行）
# 注意：S3バケットは@PostConstructで自動作成されるため手動作成不要
docker-compose logs csv-batch-processor | grep -i "s3 bucket"
```

### 4.2.1 起動順序制御の仕組み
Docker Composeは依存関係とヘルスチェックにより自動的に起動順序を制御します：

```yaml
# 起動順序: Oracle DB → LocalStack → SOAP Stub → CSV Batch Processor
csv-batch-processor:
  depends_on:
    oracle-db:
      condition: service_healthy
    soap-stub:
      condition: service_healthy
    localstack:
      condition: service_healthy
  restart: on-failure        # 依存サービス準備前起動時の自動復旧
  healthcheck:
    start_period: 120s       # 十分な待機時間を確保
```

### 4.2.2 HikariCP接続プール強化による起動問題解決
アプリケーション内部でも起動順序問題に対応：

- **接続タイムアウト延長**: 30秒→60秒（Docker環境）
- **初期化失敗タイムアウト**: 60秒→120秒（依存サービス準備待ち）
- **接続プール最適化**: Docker環境で最大5接続、最小1接続
- **自動リトライ機能**: restart: on-failureによる自動復旧

### 4.3 デプロイメント確認
```bash
# 全サービスの状態確認
docker-compose ps

# 期待される出力例:
# NAME                    COMMAND                  SERVICE             STATUS              PORTS
# localstack             "docker-entrypoint.sh"   localstack          Up (healthy)        0.0.0.0:4566->4566/tcp
# oracle-db               "/container-entrypoi…"   oracle-db           Up (healthy)        0.0.0.0:1521->1521/tcp
# soap-stub               "java -jar /app/soap…"   soap-stub           Up (healthy)        0.0.0.0:8081->8080/tcp  
# csv-batch-processor     "java -jar /app/app.…"   csv-batch-processor Up                  0.0.0.0:8080->8080/tcp

# CSV出力ファイル確認（ローカル）
ls -la output/
cat output/result.csv

# S3アップロード確認（LocalStack）
aws --endpoint-url=http://localhost:4566 s3 ls s3://csv-export-bucket/

# アプリケーション健康状態確認
curl -f http://localhost:8080/actuator/health

# 処理ログ確認
docker-compose logs csv-batch-processor | grep -E "CSV|S3|Processing"
```

## 5. トラブルシューティング

### 5.1 よくある問題と解決方法

#### 5.1.1 Oracleデータベース起動失敗・接続エラー
```bash
# エラー: ORA-12514: TNS:listener does not currently know of service requested
# 解決: データベースの完全起動を待つ（HikariCP設定で自動リトライ）
docker-compose logs oracle-db | grep "DATABASE IS READY TO USE"

# 手動での健康チェック
docker-compose exec oracle-db sqlplus -L system/oracle@//localhost:1521/XE

# HikariCP接続プール状態確認
docker-compose logs csv-batch-processor | grep -i "hikari"

# 起動順序問題の場合（自動復旧機能）
# - restart: on-failure により自動的に再起動される
# - initialization-fail-timeout: 120秒で依存サービス準備を待機
# - 手動でのコンテナ再起動も可能
docker-compose restart csv-batch-processor
```

#### 5.1.2 SOAP API接続エラー
```bash
# WSDL取得確認
curl -f http://localhost:8081/ws/employees.wsdl

# サービス再起動
docker-compose restart soap-stub
```

#### 5.1.3 CSV出力ファイルが生成されない
```bash
# アプリケーションログ確認
docker-compose logs csv-batch-processor

# 出力ディレクトリの権限確認
ls -la output/
chmod 755 output/

# S3アップロードエラー確認
docker-compose logs csv-batch-processor | grep -i "s3\|upload\|error"

# LocalStack接続確認
curl -f http://localhost:4566/_localstack/health

# CSV出力設定確認
docker-compose logs csv-batch-processor | grep -i "csv.export"
```

#### 5.1.4 LocalStack S3接続エラー・バケット未作成
```bash
# LocalStackサービス状態確認
docker-compose ps localstack

# LocalStackログ確認
docker-compose logs localstack

# S3バケット自動作成ログ確認（重要）
docker-compose logs csv-batch-processor | grep -i "s3 bucket"
# 期待される出力例:
# "S3 bucket 'csv-export-bucket' created successfully"
# または "S3 bucket 'csv-export-bucket' already exists"

# S3バケット作成確認
aws --endpoint-url=http://localhost:4566 s3 ls

# 自動作成に失敗した場合の手動対応
aws --endpoint-url=http://localhost:4566 s3 mb s3://csv-export-bucket

# S3ClientService初期化失敗時の対応
# アプリケーション再起動で自動的に再実行される
docker-compose restart csv-batch-processor

# LocalStack再起動
docker-compose restart localstack
```

#### 5.1.5 環境変数設定エラー
```bash
# .envファイル存在確認
ls -la .env

# 環境変数読み込み確認
docker-compose config

# DB_PASSWORD設定確認（値は表示されません）
docker-compose exec csv-batch-processor printenv | grep -v PASSWORD

# 設定値の反映確認
docker-compose logs csv-batch-processor | grep -i "profile\|datasource"
```

#### 5.1.6 メモリ不足エラー
```bash
# Docker リソース確認
docker system df
docker stats

# 不要なコンテナ・イメージ削除
docker system prune -f

# 個別サービスのメモリ使用量確認
docker stats csv-batch-processor oracle-db soap-stub localstack
```

### 5.2 ログ確認方法
```bash
# 全サービスのログ
docker-compose logs

# 特定サービスのログ
docker-compose logs oracle-db
docker-compose logs soap-stub  
docker-compose logs csv-batch-processor
docker-compose logs localstack

# リアルタイムログ監視
docker-compose logs -f csv-batch-processor

# エラーのみを確認
docker-compose logs csv-batch-processor | grep -i error

# 特定の機能のログ確認
docker-compose logs csv-batch-processor | grep -E "S3|CSV|SOAP|Retry|Circuit"

# 最新のログのみ表示
docker-compose logs --tail=100 csv-batch-processor
```

### 5.3 デバッグ情報収集
```bash
# システム情報収集
echo "=== Docker Info ===" > debug.log
docker info >> debug.log 2>&1

echo "=== Container Status ===" >> debug.log
docker-compose ps >> debug.log 2>&1

echo "=== Environment Variables ===" >> debug.log
docker-compose config >> debug.log 2>&1

echo "=== Service Logs ===" >> debug.log
docker-compose logs >> debug.log 2>&1

echo "=== Network Info ===" >> debug.log
docker network ls >> debug.log 2>&1

echo "=== LocalStack Health ===" >> debug.log
curl -s http://localhost:4566/_localstack/health >> debug.log 2>&1

echo "=== S3 Bucket Status ===" >> debug.log
aws --endpoint-url=http://localhost:4566 s3 ls >> debug.log 2>&1

echo "=== Application Health ===" >> debug.log
curl -s http://localhost:8080/actuator/health >> debug.log 2>&1
```

## 6. 運用・保守

### 6.1 日常運用
```bash
# 定期的なヘルスチェック
docker-compose ps
curl -f http://localhost:8081/ws/employees.wsdl
curl -f http://localhost:4566/_localstack/health
curl -f http://localhost:8080/actuator/health

# HikariCP接続プール状態確認
docker-compose logs csv-batch-processor | grep -E "HikariPool|connection"

# CSV出力の確認（ローカル）
ls -la output/result.csv
wc -l output/result.csv

# S3出力の確認（LocalStack）
aws --endpoint-url=http://localhost:4566 s3 ls s3://csv-export-bucket/ --recursive

# S3バケット自動管理状態確認
docker-compose logs csv-batch-processor | grep -i "s3 bucket"

# 処理ログの確認
docker-compose logs --tail=50 csv-batch-processor | grep -E "Export|S3|Complete|Started"

# エラー監視（起動順序問題含む）
docker-compose logs csv-batch-processor | grep -i "error\|exception\|failed\|timeout" | tail -10

# 自動復旧状況確認
docker-compose logs csv-batch-processor | grep -i "restart\|retry\|recovery"
```

### 6.2 データベースメンテナンス
```bash
# データベース接続
docker-compose exec oracle-db sqlplus system/oracle@//localhost:1521/XE

# テーブル状態確認
SELECT COUNT(*) FROM csvuser.employees;

# データ再投入（必要に応じて）
docker-compose exec oracle-db sqlplus csvuser/csvpass@//localhost:1521/XEPDB1 @/container-entrypoint-initdb.d/01_create_schema.sql
```

### 6.3 ログローテーション
```bash
# Dockerログサイズ制限設定
# docker-compose.yml に追加:
logging:
  driver: "json-file"  
  options:
    max-size: "10m"
    max-file: "3"
```

### 6.4 バックアップ・リストア
```bash
# データベースバックアップ
docker-compose exec oracle-db expdp system/oracle@//localhost:1521/XE directory=DATA_PUMP_DIR dumpfile=backup.dmp

# CSV出力ファイルバックアップ（ローカル）
mkdir -p backup
cp output/result.csv backup/result_$(date +%Y%m%d_%H%M%S).csv

# S3バックアップ（LocalStack）
aws --endpoint-url=http://localhost:4566 s3 sync s3://csv-export-bucket/ backup/s3-backup/

# LocalStackデータ永続化
docker-compose exec localstack tar -czf /tmp/localstack-backup.tar.gz /var/lib/localstack
docker cp $(docker-compose ps -q localstack):/tmp/localstack-backup.tar.gz ./backup/
```

## 7. セキュリティ設定

### 7.1 本番環境でのセキュリティ強化
⚠️ **重要**: 本番環境では以下のセキュリティ設定が必須です

```bash
# 1. 環境変数によるパスワード管理（必須）
# .envファイルで強力なパスワードを設定
export DB_PASSWORD="$(openssl rand -base64 32)"
export ORACLE_PASSWORD="$(openssl rand -base64 32)"

# 2. ネットワークポート制限
# docker-compose.yml でポート公開を制限
# 本番環境では以下をコメントアウト:
# ports:
#   - "1521:1521"  # Oracle DB（内部接続のみ）
#   - "8081:8080"  # SOAP Stub（内部接続のみ）
#   - "4566:4566"  # LocalStack（開発環境のみ）

# 3. Spring プロファイル設定
export SPRING_PROFILES_ACTIVE="production"

# 4. ログレベル制限
export LOGGING_LEVEL_ROOT="WARN"
export LOGGING_LEVEL_COM_EXAMPLE="INFO"

# 5. ファイル権限設定
chmod 600 .env
chmod 700 backup/
chmod 755 output/
```

### 7.2 ファイアウォール設定例
```bash
# UFW（Ubuntu）での設定例
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 8080/tcp    # アプリケーション（必要に応じて）
sudo ufw --force enable
```

## 8. パフォーマンスチューニング

### 8.1 Docker リソース制限
```yaml
# docker-compose.yml にリソース制限追加
services:
  csv-batch-processor:
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '1.0'
        reservations:
          memory: 512M
          cpus: '0.5'
```

### 8.2 JVMパラメータ調整
```dockerfile
# Dockerfile でJVMオプション追加
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
```

## 9. 監視・アラート

### 9.1 ヘルスチェック強化
```yaml
# docker-compose.yml でヘルスチェック詳細化
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

### 9.2 監視スクリプト例
```bash
#!/bin/bash
# monitor.sh - 簡易監視スクリプト

check_services() {
    services=("oracle-db" "soap-stub" "csv-batch-processor")
    
    for service in "${services[@]}"; do
        status=$(docker-compose ps -q $service | xargs docker inspect -f '{{.State.Status}}')
        if [ "$status" != "running" ]; then
            echo "ALERT: $service is not running"
            # ここでアラート送信処理
        fi
    done
}

check_csv_output() {
    if [ ! -f "output/result.csv" ]; then
        echo "ALERT: CSV output file not found"
    elif [ ! -s "output/result.csv" ]; then
        echo "ALERT: CSV output file is empty"  
    fi
}

check_services
check_csv_output
```

## 10. アップデート・アップグレード

### 10.1 アプリケーション更新
```bash
# 1. 新しいコードの取得
git pull origin main

# 2. サービス停止
docker-compose down

# 3. イメージ再ビルド
docker-compose build --no-cache

# 4. サービス再起動
docker-compose up -d
```

### 10.2 依存関係更新
```bash
# Maven依存関係更新
docker run --rm -v "$(pwd)":/usr/src/app -w /usr/src/app maven:3.8-openjdk-21 mvn versions:display-dependency-updates

# Dockerイメージ更新
docker-compose pull
```

## 11. 緊急時対応

### 11.1 緊急停止
```bash
# 全サービス即座に停止
docker-compose kill

# 強制削除
docker-compose down --volumes --remove-orphans
```

### 11.2 復旧手順
```bash
# 1. 原因調査
docker-compose logs > emergency.log

# 2. クリーンアップ
docker system prune -af --volumes

# 3. 再デプロイ
docker-compose up -d

# 4. 動作確認
./monitor.sh
```