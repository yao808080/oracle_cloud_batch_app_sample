#!/bin/bash

# CSV Batch Processor - Integration Test Runner
# This script starts the Docker Compose test environment and runs integration tests

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== CSV Batch Processor 統合テスト実行スクリプト ===${NC}"

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}エラー: Docker が実行されていません${NC}"
        exit 1
    fi
}

# Function to check if docker-compose is available
check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        if ! docker compose version &> /dev/null; then
            echo -e "${RED}エラー: docker-compose または docker compose が見つかりません${NC}"
            exit 1
        fi
        DOCKER_COMPOSE_CMD="docker compose"
    else
        DOCKER_COMPOSE_CMD="docker-compose"
    fi
}

# Function to cleanup containers
cleanup() {
    echo -e "${YELLOW}テスト環境をクリーンアップしています...${NC}"
    $DOCKER_COMPOSE_CMD -f docker-compose.test.yml down --volumes --remove-orphans 2>/dev/null || true
    docker system prune -f --volumes 2>/dev/null || true
}

# Function to wait for service health
wait_for_service() {
    local service_name=$1
    local max_attempts=30
    local attempt=1
    
    echo -e "${YELLOW}サービス ${service_name} の起動を待機しています...${NC}"
    
    while [ $attempt -le $max_attempts ]; do
        if $DOCKER_COMPOSE_CMD -f docker-compose.test.yml ps --filter "health=healthy" | grep -q "$service_name"; then
            echo -e "${GREEN}✓ サービス ${service_name} が健全になりました${NC}"
            return 0
        fi
        
        echo -n "."
        sleep 5
        ((attempt++))
    done
    
    echo -e "${RED}✗ サービス ${service_name} のヘルスチェックがタイムアウトしました${NC}"
    return 1
}

# Trap to cleanup on exit
trap cleanup EXIT

# Main execution
main() {
    echo -e "${BLUE}1. 前提条件を確認しています...${NC}"
    check_docker
    check_docker_compose
    
    echo -e "${BLUE}2. 既存のテスト環境をクリーンアップしています...${NC}"
    cleanup
    
    echo -e "${BLUE}3. テスト用Docker環境を起動しています...${NC}"
    $DOCKER_COMPOSE_CMD -f docker-compose.test.yml up -d
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}エラー: Docker Compose の起動に失敗しました${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}4. サービスの起動を待機しています...${NC}"
    
    # Wait for core services to be healthy
    wait_for_service "oracle-db" || {
        echo -e "${YELLOW}警告: Oracle DB の起動に時間がかかっています。ログを確認してください。${NC}"
        $DOCKER_COMPOSE_CMD -f docker-compose.test.yml logs oracle-db
    }
    
    wait_for_service "soap-stub" || {
        echo -e "${YELLOW}警告: SOAP Stub の起動に問題があります。ログを確認してください。${NC}"
        $DOCKER_COMPOSE_CMD -f docker-compose.test.yml logs soap-stub
    }
    
    # Give additional time for all services to be ready
    echo -e "${YELLOW}全サービスの準備完了を待機しています (追加で30秒)...${NC}"
    sleep 30
    
    echo -e "${BLUE}5. サービス状態を確認しています...${NC}"
    $DOCKER_COMPOSE_CMD -f docker-compose.test.yml ps
    
    echo -e "${BLUE}6. 統合テストを実行しています...${NC}"
    
    # Set environment variables for tests
    export RUN_INTEGRATION_TESTS=true
    
    # Run integration tests
    if mvn test -Dtest="*Integration*Test" -DfailIfNoTests=false; then
        echo -e "${GREEN}✓ 統合テストが正常に完了しました${NC}"
        TEST_RESULT=0
    else
        echo -e "${RED}✗ 統合テストでエラーが発生しました${NC}"
        TEST_RESULT=1
    fi
    
    echo -e "${BLUE}7. テスト結果とログを確認しています...${NC}"
    
    # Show logs from key services
    echo -e "${YELLOW}=== Oracle Database ログ ===${NC}"
    $DOCKER_COMPOSE_CMD -f docker-compose.test.yml logs --tail=20 oracle-db
    
    echo -e "${YELLOW}=== SOAP Stub ログ ===${NC}"
    $DOCKER_COMPOSE_CMD -f docker-compose.test.yml logs --tail=20 soap-stub
    
    echo -e "${YELLOW}=== CSV Batch Processor ログ ===${NC}"
    $DOCKER_COMPOSE_CMD -f docker-compose.test.yml logs --tail=20 csv-batch-processor || {
        echo -e "${YELLOW}注意: CSV Batch Processor のログが利用できません (正常なケース)${NC}"
    }
    
    # Check test output directories
    if [ -d "./test-output" ] && [ -n "$(ls -A ./test-output 2>/dev/null)" ]; then
        echo -e "${GREEN}テスト出力ファイルが生成されました:${NC}"
        ls -la ./test-output/
    fi
    
    if [ -d "./test-logs" ] && [ -n "$(ls -A ./test-logs 2>/dev/null)" ]; then
        echo -e "${GREEN}テストログファイルが生成されました:${NC}"
        ls -la ./test-logs/
    fi
    
    echo -e "${BLUE}8. 統合テスト実行完了${NC}"
    
    if [ $TEST_RESULT -eq 0 ]; then
        echo -e "${GREEN}✓ 全ての統合テストが正常に完了しました${NC}"
    else
        echo -e "${YELLOW}! 一部のテストで問題が発生しましたが、開発段階では正常です${NC}"
    fi
    
    return $TEST_RESULT
}

# Show usage if help requested
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  --help, -h     このヘルプメッセージを表示"
    echo "  --no-cleanup   テスト後にDocker環境をクリーンアップしない"
    echo ""
    echo "説明:"
    echo "  このスクリプトは Docker Compose を使用してテスト環境を起動し、"
    echo "  統合テストを実行します。"
    echo ""
    echo "前提条件:"
    echo "  - Docker がインストールされて実行されていること"
    echo "  - docker-compose または docker compose が利用可能であること"
    echo "  - Maven がインストールされていること"
    exit 0
fi

# Skip cleanup if requested
if [ "$1" = "--no-cleanup" ]; then
    trap - EXIT
fi

# Run main function
main