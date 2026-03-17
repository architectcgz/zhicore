#!/bin/bash

# ===========================================
# ZhiCore 微服务健康检查测试脚本
# ===========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试结果数组
declare -a FAILED_SERVICES

# 打印标题
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# 打印测试结果
print_result() {
    local service=$1
    local status=$2
    local message=$3

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    if [ "$status" == "PASS" ]; then
        echo -e "${GREEN}✓${NC} $service: $message"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗${NC} $service: $message"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_SERVICES+=("$service: $message")
    fi
}

# 测试 HTTP 端点
test_http_endpoint() {
    local service_name=$1
    local url=$2
    local expected_status=${3:-200}

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>&1)

    if [ "$response" == "$expected_status" ]; then
        print_result "$service_name" "PASS" "HTTP $response"
        return 0
    else
        print_result "$service_name" "FAIL" "Expected HTTP $expected_status, got $response"
        return 1
    fi
}

# 测试健康检查端点
test_health_endpoint() {
    local service_name=$1
    local url=$2

    response=$(curl -s "$url" 2>&1)

    if echo "$response" | grep -q '"status":"UP"'; then
        print_result "$service_name" "PASS" "Status UP"
        return 0
    else
        print_result "$service_name" "FAIL" "Status not UP or unreachable"
        return 1
    fi
}

# 测试 TCP 端口
test_tcp_port() {
    local service_name=$1
    local host=$2
    local port=$3

    if timeout 5 bash -c "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
        print_result "$service_name" "PASS" "Port $port is open"
        return 0
    else
        print_result "$service_name" "FAIL" "Port $port is not accessible"
        return 1
    fi
}

# 测试 Redis
test_redis() {
    local service_name="Redis"
    local host=${1:-localhost}
    local port=${2:-6379}

    if command -v redis-cli &> /dev/null; then
        response=$(redis-cli -h "$host" -p "$port" PING 2>&1)
        if [ "$response" == "PONG" ]; then
            print_result "$service_name" "PASS" "PING successful"
            return 0
        else
            print_result "$service_name" "FAIL" "PING failed: $response"
            return 1
        fi
    else
        # 如果没有 redis-cli，使用 TCP 端口测试
        test_tcp_port "$service_name" "$host" "$port"
    fi
}

# 测试 PostgreSQL
test_postgresql() {
    local service_name="PostgreSQL"
    local host=${1:-localhost}
    local port=${2:-5432}

    test_tcp_port "$service_name" "$host" "$port"
}

# 测试 MongoDB
test_mongodb() {
    local service_name="MongoDB"
    local host=${1:-localhost}
    local port=${2:-27017}

    test_tcp_port "$service_name" "$host" "$port"
}

# 测试 Elasticsearch
test_elasticsearch() {
    local service_name="Elasticsearch"
    local url=${1:-http://localhost:9200/_cluster/health}

    response=$(curl -s "$url" 2>&1)

    if echo "$response" | grep -q '"status":"green"\|"status":"yellow"'; then
        print_result "$service_name" "PASS" "Cluster health OK"
        return 0
    else
        print_result "$service_name" "FAIL" "Cluster health check failed"
        return 1
    fi
}

# 测试 Nacos
test_nacos() {
    local service_name="Nacos"
    local url=${1:-http://localhost:8848/nacos/v1/console/health/readiness}

    test_http_endpoint "$service_name" "$url" 200
}

# 主测试流程
main() {
    echo ""
    print_header "ZhiCore 微服务健康检查测试"
    echo ""
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""

    # ========================================
    # 1. 基础设施服务测试
    # ========================================
    print_header "1. 基础设施服务"
    echo ""

    test_postgresql "localhost" 5432
    test_redis "localhost" 6379
    test_mongodb "localhost" 27017
    test_elasticsearch "http://localhost:9200/_cluster/health"
    test_tcp_port "RocketMQ NameServer" "localhost" 9876
    test_nacos "http://localhost:8848/nacos/v1/console/health/readiness"

    echo ""

    # ========================================
    # 2. 微服务健康检查
    # ========================================
    print_header "2. 微服务健康检查"
    echo ""

    test_health_endpoint "Gateway Service" "http://localhost:8000/actuator/health"
    test_health_endpoint "User Service" "http://localhost:8081/actuator/health"
    test_health_endpoint "Content Service" "http://localhost:8082/actuator/health"
    test_health_endpoint "Comment Service" "http://localhost:8083/actuator/health"
    test_health_endpoint "Message Service" "http://localhost:8084/actuator/health"
    test_health_endpoint "Notification Service" "http://localhost:8085/actuator/health"
    test_health_endpoint "Search Service" "http://localhost:8086/actuator/health"
    test_health_endpoint "Ranking Service" "http://localhost:8087/actuator/health"
    test_health_endpoint "ID Generator Service" "http://localhost:8088/actuator/health"
    test_health_endpoint "Upload Service" "http://localhost:8092/actuator/health"
    test_health_endpoint "Admin Service" "http://localhost:8093/actuator/health"
    test_health_endpoint "Ops Service" "http://localhost:8094/actuator/health"

    echo ""

    # ========================================
    # 3. 网关路由测试
    # ========================================
    print_header "3. 网关路由测试"
    echo ""

    # 测试网关是否能路由到各个服务（通过健康检查端点）
    # 注意：这需要网关配置了健康检查路由

    echo ""

    # ========================================
    # 测试总结
    # ========================================
    print_header "测试总结"
    echo ""
    echo "总测试数: $TOTAL_TESTS"
    echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
    echo -e "${RED}失败: $FAILED_TESTS${NC}"
    echo ""

    if [ $FAILED_TESTS -gt 0 ]; then
        echo -e "${RED}失败的服务:${NC}"
        for service in "${FAILED_SERVICES[@]}"; do
            echo -e "  ${RED}✗${NC} $service"
        done
        echo ""
        echo -e "${RED}测试失败！${NC}"
        exit 1
    else
        echo -e "${GREEN}所有测试通过！${NC}"
        exit 0
    fi
}

# 执行主函数
main
