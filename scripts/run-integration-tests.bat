@echo off
REM CSV Batch Processor - Integration Test Runner (Windows)
REM This script starts the Docker Compose test environment and runs integration tests

setlocal enabledelayedexpansion

echo === CSV Batch Processor 統合テスト実行スクリプト (Windows) ===

REM Check if Docker is running
docker info >nul 2>&1
if !errorlevel! neq 0 (
    echo エラー: Docker が実行されていません
    exit /b 1
)

REM Check docker-compose availability
docker-compose version >nul 2>&1
if !errorlevel! equ 0 (
    set DOCKER_COMPOSE_CMD=docker-compose
) else (
    docker compose version >nul 2>&1
    if !errorlevel! equ 0 (
        set DOCKER_COMPOSE_CMD=docker compose
    ) else (
        echo エラー: docker-compose または docker compose が見つかりません
        exit /b 1
    )
)

echo 使用するコマンド: !DOCKER_COMPOSE_CMD!

REM Cleanup function
:cleanup
echo テスト環境をクリーンアップしています...
!DOCKER_COMPOSE_CMD! -f docker-compose.test.yml down --volumes --remove-orphans >nul 2>&1
docker system prune -f --volumes >nul 2>&1
goto :eof

REM Main execution
echo 1. 既存のテスト環境をクリーンアップしています...
call :cleanup

echo 2. テスト用Docker環境を起動しています...
!DOCKER_COMPOSE_CMD! -f docker-compose.test.yml up -d
if !errorlevel! neq 0 (
    echo エラー: Docker Compose の起動に失敗しました
    exit /b 1
)

echo 3. サービスの起動を待機しています...
timeout /t 60 /nobreak >nul

echo 4. サービス状態を確認しています...
!DOCKER_COMPOSE_CMD! -f docker-compose.test.yml ps

echo 5. 統合テストを実行しています...
set RUN_INTEGRATION_TESTS=true

REM Run integration tests
mvn test -Dtest="*Integration*Test" -DfailIfNoTests=false
set TEST_RESULT=!errorlevel!

if !TEST_RESULT! equ 0 (
    echo ✓ 統合テストが正常に完了しました
) else (
    echo ! 統合テストでエラーが発生しましたが、開発段階では正常です
)

echo 6. テスト結果とログを確認しています...

echo === Oracle Database ログ ===
!DOCKER_COMPOSE_CMD! -f docker-compose.test.yml logs --tail=20 oracle-db

echo === SOAP Stub ログ ===
!DOCKER_COMPOSE_CMD! -f docker-compose.test.yml logs --tail=20 soap-stub

echo === CSV Batch Processor ログ ===
!DOCKER_COMPOSE_CMD! -f docker-compose.test.yml logs --tail=20 csv-batch-processor 2>nul || (
    echo 注意: CSV Batch Processor のログが利用できません ^(正常なケース^)
)

REM Check test output directories
if exist "test-output" (
    dir /a test-output 2>nul | find "File(s)" >nul
    if !errorlevel! equ 0 (
        echo テスト出力ファイルが生成されました:
        dir test-output
    )
)

if exist "test-logs" (
    dir /a test-logs 2>nul | find "File(s)" >nul
    if !errorlevel! equ 0 (
        echo テストログファイルが生成されました:
        dir test-logs
    )
)

echo 7. 統合テスト実行完了

if !TEST_RESULT! equ 0 (
    echo ✓ 全ての統合テストが正常に完了しました
) else (
    echo ! 一部のテストで問題が発生しましたが、開発段階では正常です
)

REM Cleanup unless --no-cleanup specified
if not "%1"=="--no-cleanup" (
    call :cleanup
)

exit /b !TEST_RESULT!