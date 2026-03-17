#!/bin/bash

# ===========================================
# ZhiCore 压力测试脚本
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

# 压力测试配置
CONCURRENT_LEVELS=(100 200 500)
REQUESTS_PER_LEVEL=1000

# 打印标题
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# 压力测试
stress_test() {
    local name=$1
    local url=$2
    local concurrent=$3
    local total_requests=$4

    echo "压力测试 $name (${concurrent} 并发, ${total_requests} 总请求)..."

    local start_time=$(date +%s)
    local pids=()

    # 启动并发请求
    for ((i=1; i<=concurrent; i++)); do
        (
            local requests_per_worker=$((total_requests / concurrent))
            for ((j=1; j<=requests_per_worker; j++)); do
                response=$(curl -s -w "%{http_code}" -o /dev/null "$url" 2>&1)
                if [ "$response" == "200" ]; then
                    echo "1" >> /tmp/stress_test_$$_success.txt
                else
                    echo "1" >> /tmp/stress_test_$$_failed.txt
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
    local success=0
    local failed=0

    if [ -f /tmp/stress_test_$$_success.txt ]; then
        success=$(wc -l < /tmp/stress_test_$$_success.txt)
        rm /tmp/stress_test_$$_success.txt
    fi

    if [ -f /tmp/stress_test_$$_failed.txt ]; then
        failed=$(wc -l < /tmp/stress_test_$$_failed.txt)
        rm /tmp/stress_test_$$_failed.txt
    fi

    local total=$((success + failed))
    local qps=0
    if [ $duration -gt 0 ]; then
        qps=$((total / duration))
    fi
    local success_rate=0
    if [ $total -gt 0 ]; then
        success_rate=$((success * 100 / total))
    fi

    echo -e "${GREEN}✓${NC} 并发 $concurrent"
    echo "  总请求: $total, 成功: $success, 失败: $failed"
    echo "  耗时: ${duration}s, QPS: ${qps}, 成功率: ${success_rate}%"
    echo ""

    # 检查系统资源
    echo "  系统资源使用情况:"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | grep zhicore | head -5
    echo ""
}

# 主测试流程
main() {
    echo ""
    print_header "ZhiCore 压力测试"
    echo ""
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    echo "测试配置:"
    echo "  并发级别: ${CONCURRENT_LEVELS[@]}"
    echo "  每级别请求数: $REQUESTS_PER_LEVEL"
    echo ""

    # ========================================
    # 1. 用户服务压力测试
    # ========================================
    print_header "1. 用户服务压力测试"
    echo ""

    for concurrent in "${CONCURRENT_LEVELS[@]}"; do
        stress_test "用户服务" "$USER_SERVICE_URL/actuator/health" $concurrent $REQUESTS_PER_LEVEL
        sleep 5  # 让系统恢复
    done

    # ========================================
    # 2. 内容服务压力测试
    # ========================================
    print_header "2. 内容服务压力测试"
    echo ""

    for concurrent in "${CONCURRENT_LEVELS[@]}"; do
        stress_test "内容服务" "$CONTENT_SERVICE_URL/actuator/health" $concurrent $REQUESTS_PER_LEVEL
        sleep 5  # 让系统恢复
    done

    # ========================================
    # 3. 评论服务压力测试
    # ========================================
    print_header "3. 评论服务压力测试"
    echo ""

    for concurrent in "${CONCURRENT_LEVELS[@]}"; do
        stress_test "评论服务" "$COMMENT_SERVICE_URL/actuator/health" $concurrent $REQUESTS_PER_LEVEL
        sleep 5  # 让系统恢复
    done

    # ========================================
    # 测试总结
    # ========================================
    print_header "测试总结"
    echo ""
    echo "测试完成时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    echo -e "${GREEN}压力测试完成！${NC}"
    echo ""
    echo "观察要点："
    echo "1. 随着并发增加，QPS 是否线性增长"
    echo "2. 成功率是否保持在 99% 以上"
    echo "3. 系统资源使用是否在合理范围"
    echo "4. 是否出现服务降级或熔断"
}

# 执行主函数
main
