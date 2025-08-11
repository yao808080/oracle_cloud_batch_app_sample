# Docker Desktop不要のテスト実行ガイド

Docker Desktopが利用できない環境でのテスト実行方法を説明します。

## 前提条件

- Java 21+
- Maven 3.9+
- Git

## 1. 単体テストとコンポーネントテスト

Docker環境なしで実行可能なテスト群：

```bash
# 全体テスト実行
mvn clean test

# 機能別テスト実行
mvn test -Dtest="*ResilienceTest"      # Resilience4j機能テスト
mvn test -Dtest="*Helidon*Test"        # Helidon Framework テスト  
mvn test -Dtest="CsvProcessorServiceTest" # CSV処理テスト

# カバレッジレポート付きテスト
mvn clean test jacoco:report
```

## 2. 軽量Docker Composeテスト（推奨）

軽量版のDocker Composeを使用したテスト：

```bash
# 軽量テスト環境起動
docker-compose -f docker-compose.test.minimal.yml up -d

# 統合テスト実行
set RUN_INTEGRATION_TESTS=true
mvn test -Dtest="*Integration*Test" -DfailIfNoTests=false

# テスト環境停止とクリーンアップ  
docker-compose -f docker-compose.test.minimal.yml down --volumes
```

### 軽量版に含まれるサービス

- **WireMock** - SOAP APIスタブ
- **PostgreSQL** - 軽量テストデータベース（Oracle DBの代替）

## 3. モックサーバーを使ったテスト

### WireMockスタンドアロン実行

```bash
# WireMock JARをダウンロード（初回のみ）
curl -o wiremock-standalone.jar https://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/2.35.0/wiremock-standalone-2.35.0.jar

# WireMockサーバー起動
java -jar wiremock-standalone.jar --port 8081 --root-dir test-resources/wiremock --verbose

# 別ターミナルでテスト実行
mvn test -Dtest="SoapClientTest" -DRUN_INTEGRATION_TESTS=true
```

### H2 In-Memoryデータベーステスト

`application-test.yml`を以下のように修正してH2データベースを使用：

```yaml
datasource:
  url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  username: sa
  password: 
  driver-class-name: org.h2.Driver
```

## 4. テスト結果確認

### コードカバレッジレポート

```bash
# カバレッジレポート生成
mvn clean test jacoco:report

# レポート確認
start target/site/jacoco/index.html  # Windows
open target/site/jacoco/index.html   # Mac
```

### テストレポート

```bash
# Surefireテストレポート確認
start target/surefire-reports/index.html  # Windows
```

## 5. 継続的インテグレーション設定

### GitHub Actions設定例

`.github/workflows/test.yml`:

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      # 単体テスト実行
      - name: Run Unit Tests
        run: mvn clean test
      
      # 軽量統合テスト実行
      - name: Run Integration Tests
        run: |
          docker-compose -f docker-compose.test.minimal.yml up -d
          export RUN_INTEGRATION_TESTS=true
          mvn test -Dtest="*Integration*Test" -DfailIfNoTests=false
          docker-compose -f docker-compose.test.minimal.yml down
```

## 6. トラブルシューティング

### よくある問題と解決策

1. **ポート競合エラー**
   ```bash
   # 使用中のポートを確認
   netstat -an | findstr ":8081"  # Windows
   lsof -i :8081                  # Mac/Linux
   ```

2. **メモリ不足エラー**
   ```bash
   # Maven実行時のメモリ設定
   export MAVEN_OPTS="-Xmx1024m"
   mvn clean test
   ```

3. **テストデータベース接続エラー**
   ```bash
   # H2コンソールでデータベース内容確認
   java -cp ~/.m2/repository/com/h2database/h2/*/h2-*.jar org.h2.tools.Console
   ```

## 7. 開発時のベストプラクティス

1. **TDDアプローチ**
   - テストを先に作成
   - 失敗を確認してから実装
   - リファクタリング

2. **テスト実行の習慣化**
   ```bash
   # 開発中の継続実行
   mvn test -Dtest="*Test" --watch  # ファイル変更監視
   ```

3. **カバレッジ目標**
   - 単体テスト: 80%以上
   - 統合テスト: 主要パス実行

このガイドにより、Docker Desktop不要でも包括的なテストを実行できます。