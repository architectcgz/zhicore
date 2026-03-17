#!/bin/bash

# ===========================================
# ZhiCore 性能测试脚本
# ===========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
USER_SERVICE_URL="http://localhost:8081"
CONTENT_SERVICE_URL="http://localhost:8082"
COMMENT_SERVICE_URL="http://localhost:8083"

# 测试配置
CONCURRENT_USERS=50
TEST_DURATION=30  # 秒

# 结果存储
declare -a RESPONSE_TIMES

# 打印标题
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# 计算统计数据
calculate_stats() {
    local sorted=($(printf '%s\n' "${RESPONSE_TIMES[@]}" | sort -n))
    local count=${#sorted[@]}

    if [ $count -eq 0 ]; then
        echo "0"
        return
    fi

    # 计算平均值
    local sum=0
    for time in "${sorted[@]}"; do
        sum=$((sum + time))
    done
    local avg=$((sum / count))

    # 计算 P50, P95, P99
    local p50_index=$((count * 50 / 100))
    local p95_index=$((count * 95 / 100))
    local p99_index=$((count * 99 / 100))

    local p50=${sorted[$p50_index]}
    local p95=${sorted[$p95_index]}
    local p99=${sorted[$p99_index]}
    local min=${sorted[0]}
    local max=${sorted[$((count-1))]}

    echo "平均: ${avg}ms, P50: ${p50}ms, P95: ${p95}ms, P99: ${p99}ms, Min: ${min}ms, Max: ${max}ms"
}

# 测试单个接口响应时间
test_endpoint_performance() {
    local name=$1
    local url=$2
    local method=${3:-GET}
    local headers=${4:-""}
    local data=${5:-""}
    local iterations=${6:-100}

    echo "测试 $name (${iterations} 次请求)..."

    RESPONSE_TIMES=()
    local success=0
    local failed=0

    for ((i=1; i<=iterations; i++)); do
        local start=$(date +%s%3N)

        if [ "$method" == "GET" ]; then
            response=$(curl -s -w "%{http_code}" -o /dev/null $headers "$url" 2>&1)
        else
            response=$(curl -s -w "%{http_code}" -o /dev/null -X $method $headers -d "$data" "$url" 2>&1)
        fi

        local end=$(date +%s%3N)
        local duration=$((end - start))

        if [ "$response" == "200" ] || [ "$response" == "201" ]; then
            success=$((success + 1))
            RESPONSE_TIMES+=($duration)
        else
            failed=$((failed + 1))
        fi

        # 显示进度
        if [ $((i % 10)) -eq 0 ]; then
            echo -n "."
        fi
    done

    echo ""
    echo -e "${GREEN}✓${NC} $name"
    echo "  成功: $success, 失败: $failed"
    echo "  $(calculate_stats)"
    echo ""
}

# 并发测试
test_concurrent_requests() {
    local name=$1
    local url=$2
    local concurrent=$3
    local total_requests=$4

    echo "测试 $name (${concurrent} 并发, ${total_requests} 总请求)..."

    local start_time=$(date +%s)
    local pids=()
    local success=0
    local failed=0

    # 启动并发请求
    for ((i=1; i<=concurrent; i++)); do
        (
            local requests_per_worker=$((total_requests / concurrent))
            for ((j=1; j<=requests_per_worker; j++)); do
                response=$(curl -s -w "%{http_code}" -o /dev/null "$url" 2>&1)
                if [ "$response" == "200" ]; then
                    echo "success" >> /tmp/perf_test_$$_success.txt
                else
                    echo "failed" >> /tmp/perf_test_$$_failed.txt
                fi
            done
        ) &
        pids+=($!)
    done

    # 等待所有请求完成
    for pid in "${pids[@]}"; do
        wait $pid
    done

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    # 统计结果
    if [ -f /tmp/perf_test_$$_success.txt ]; then
        success=$(wc -l < /tmp/perf_test_$$_success.txt)
        rm /tmp/perf_test_$$_success.txt
    fi

    if [ -f /tmp/perf_test_$$_failed.txt ]; then
        failed=$(wc -l < /tmp/perf_test_$$_failed.txt)
        rm /tmp/perf_test_$$_failed.txt
    fi

    local total=$((success + failed))
    local qps=$((total / duration))
    local success_rate=$((success * 100 / total))

    echo -e "${GREEN}✓${NC} $name"
    echo "  总请求: $total, 成功: $success, 失败: $failed"
    echo "  耗时: ${duration}s, QPS: ${qps}, 成功率: ${success_rate}%"
    echo ""
}

# 主测试流程
main() {
    echo ""
    print_header "ZhiCore 性能测试"
    echo ""
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""

    # ========================================
    # 1. 单接口响应时间测试
    # ========================================
    print_header "1. 单接口响应时间测试"
    echo ""

    test_endpoint_performance "用户服务健康检查" "$USER_SERVICE_URL/actuator/health" "GET" "" "" 100
    test_endpoint_performance "内容服务健康检查" "$CONTENT_SERVICE_URL/actuator/health" "GET" "" "" 100
    test_endpoint_performance "评论服务健康检查" "$COMMENT_SERVICE_URL/actuator/health" "GET" "" "" 100

    # ========================================
    # 2. 并发读测试
    # ========================================
    print_header "2. 并发读测试"
    echo ""

    test_concurrent_requests "用户服务并发读" "$USER_SERVICE_URL/actuator/health" 50 500
    test_concurrent_requests "内容服务并发读" "$CONTENT_SERVICE_URL/actuator/health" 50 500
    test_concurrent_requests "评论服务并发读" "$COMMENT_SERVICE_URL/actuator/health" 50 500

    # ========================================
    # 测试总结
    # ========================================
    print_header "测试总结"
    echo ""
    echo "测试完成时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    echo -e "${GREEN}性能测试完成！${NC}"
    echo ""
    echo "建议："
    echo "1. P95 响应时间应 < 1000ms"
    echo "2. 成功率应 > 99%"
    echo "3. QPS 应根据业务需求评估"
}

# 执行主函数
main
