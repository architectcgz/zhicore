#!/bin/bash

# ===========================================
# ZhiCore 跨服务通信集成测试
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
SEARCH_SERVICE_URL="http://localhost:8086"
RANKING_SERVICE_URL="http://localhost:8087"

# 测试数据
TEST_EMAIL="crosstest_$(date +%s)@example.com"
TEST_PASSWORD="Test123456!"
TEST_USERNAME="crosstest_$(date +%s)"
JWT_TOKEN=""
USER_ID=""
POST_ID=""

# 计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 打印标题
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# 打印测试结果
print_result() {
    local test_name=$1
    local status=$2
    local message=$3

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    if [ "$status" == "PASS" ]; then
        echo -e "${GREEN}✓${NC} $test_name: $message"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗${NC} $test_name: $message"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# 准备测试数据
prepare_test_data() {
    echo "准备测试数据..."

    # 注册用户
    response=$(curl -s -X POST "$USER_SERVICE_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\": \"$TEST_EMAIL\", \"password\": \"$TEST_PASSWORD\", \"userName\": \"$TEST_USERNAME\"}")

    USER_ID=$(echo "$response" | grep -o '"data":[0-9]*' | cut -d':' -f2)

    # 登录获取 Token
    response=$(curl -s -X POST "$USER_SERVICE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\": \"$TEST_EMAIL\", \"password\": \"$TEST_PASSWORD\"}")

    JWT_TOKEN=$(echo "$response" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

    # 创建并发布文章
    response=$(curl -s -X POST "$CONTENT_SERVICE_URL/api/v1/posts" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"title\": \"跨服务测试文章\", \"content\": \"测试内容\", \"contentType\": \"markdown\", \"tags\": [\"测试\"]}")

    POST_ID=$(echo "$response" | grep -o '"data":[0-9]*' | cut -d':' -f2)

    curl -s -X POST "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID/publish" \
        -H "Authorization: Bearer $JWT_TOKEN" > /dev/null

    echo "测试数据准备完成: User ID=$USER_ID, Post ID=$POST_ID"
}

# 测试 Feign 调用：内容服务调用用户服务
test_feign_content_to_user() {
    if [ -z "$POST_ID" ]; then
        print_result "Feign调用-内容到用户" "FAIL" "Post ID not available"
        return 1
    fi

    # 查询文章详情，应该包含作者信息（通过 Feign 调用用户服务获取）
    response=$(curl -s -X GET "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID" \
        -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$response" | grep -q "\"userName\":\"$TEST_USERNAME\""; then
        print_result "Feign调用-内容到用户" "PASS" "成功获取作者信息"
        return 0
    else
        print_result "Feign调用-内容到用户" "FAIL" "未获取到作者信息"
        return 1
    fi
}

# 测试 MQ 消息：文章发布事件
test_mq_post_published() {
    if [ -z "$POST_ID" ]; then
        print_result "MQ消息-文章发布" "FAIL" "Post ID not available"
        return 1
    fi

    echo "等待 MQ 消息处理（5秒）..."
    sleep 5

    # 检查搜索服务是否收到文章发布事件并创建索引
    response=$(curl -s -X GET "$SEARCH_SERVICE_URL/api/v1/search?keyword=跨服务测试" \
        -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$response" | grep -q "\"title\":\"跨服务测试文章\""; then
        print_result "MQ消息-文章发布" "PASS" "搜索服务已同步文章索引"
        return 0
    else
        print_result "MQ消息-文章发布" "WARN" "搜索索引可能未同步（需要更长时间）"
        return 0
    fi
}

# 测试 MQ 消息：文章点赞事件
test_mq_post_liked() {
    if [ -z "$POST_ID" ]; then
        print_result "MQ消息-文章点赞" "FAIL" "Post ID not available"
        return 1
    fi

    # 点赞文章
    curl -s -X POST "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID/like" \
        -H "Authorization: Bearer $JWT_TOKEN" > /dev/null

    echo "等待 MQ 消息处理（5秒）..."
    sleep 5

    # 检查排行榜服务是否收到点赞事件并更新热度
    response=$(curl -s -X GET "$RANKING_SERVICE_URL/api/v1/ranking/hot?page=0&size=20" \
        -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$response" | grep -q "\"postId\":$POST_ID"; then
        print_result "MQ消息-文章点赞" "PASS" "排行榜服务已更新热度"
        return 0
    else
        print_result "MQ消息-文章点赞" "WARN" "排行榜可能未更新（需要更长时间）"
        return 0
    fi
}

# 测试 MQ 消息：评论创建事件
test_mq_comment_created() {
    if [ -z "$POST_ID" ]; then
        print_result "MQ消息-评论创建" "FAIL" "Post ID not available"
        return 1
    fi

    # 创建评论
    curl -s -X POST "$COMMENT_SERVICE_URL/api/v1/comments" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"postId\": $POST_ID, \"content\": \"测试评论\"}" > /dev/null

    echo "等待 MQ 消息处理（3秒）..."
    sleep 3

    # 检查文章的评论数是否更新
    response=$(curl -s -X GET "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID" \
        -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$response" | grep -q "\"commentCount\":[1-9]"; then
        print_result "MQ消息-评论创建" "PASS" "文章评论数已更新"
        return 0
    else
        print_result "MQ消息-评论创建" "WARN" "评论数可能未更新（需要更长时间）"
        return 0
    fi
}

# 测试服务降级：模拟用户服务不可用
test_service_fallback() {
    # 这个测试需要手动停止用户服务，这里只做说明
    print_result "服务降级测试" "SKIP" "需要手动停止服务进行测试"
    return 0
}

# 测试缓存一致性
test_cache_consistency() {
    if [ -z "$POST_ID" ]; then
        print_result "缓存一致性" "FAIL" "Post ID not available"
        return 1
    fi

    # 第一次查询（应该从数据库加载并缓存）
    response1=$(curl -s -X GET "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID" \
        -H "Authorization: Bearer $JWT_TOKEN")

    # 第二次查询（应该从缓存读取）
    response2=$(curl -s -X GET "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID" \
        -H "Authorization: Bearer $JWT_TOKEN")

    if [ "$response1" == "$response2" ]; then
        print_result "缓存一致性" "PASS" "缓存数据一致"
        return 0
    else
        print_result "缓存一致性" "FAIL" "缓存数据不一致"
        return 1
    fi
}

# 主测试流程
main() {
    echo ""
    print_header "ZhiCore 跨服务通信集成测试"
    echo ""
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""

    # 准备测试数据
    prepare_test_data
    echo ""

    # ========================================
    # 1. Feign 客户端调用测试
    # ========================================
    print_header "1. Feign 客户端调用测试"
    echo ""

    test_feign_content_to_user

    echo ""

    # ========================================
    # 2. MQ 消息传递测试
    # ========================================
    print_header "2. MQ 消息传递测试"
    echo ""

    test_mq_post_published
    test_mq_post_liked
    test_mq_comment_created

    echo ""

    # ========================================
    # 3. 服务降级测试
    # ========================================
    print_header "3. 服务降级测试"
    echo ""

    test_service_fallback

    echo ""

    # ========================================
    # 4. 缓存一致性测试
    # ========================================
    print_header "4. 缓存一致性测试"
    echo ""

    test_cache_consistency

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
        echo -e "${RED}测试失败！${NC}"
        exit 1
    else
        echo -e "${GREEN}所有测试通过！${NC}"
        exit 0
    fi
}

# 执行主函数
main
