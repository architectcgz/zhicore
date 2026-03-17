#!/bin/bash

# ===========================================
# ZhiCore 核心业务流程 E2E 测试
# ===========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
GATEWAY_URL="http://localhost:8000"
USER_SERVICE_URL="http://localhost:8081"
CONTENT_SERVICE_URL="http://localhost:8082"
COMMENT_SERVICE_URL="http://localhost:8083"

# 测试数据
TEST_EMAIL="test_$(date +%s)@example.com"
TEST_PASSWORD="Test123456!"
TEST_USERNAME="testuser_$(date +%s)"
JWT_TOKEN=""
USER_ID=""
POST_ID=""
COMMENT_ID=""

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

# 测试用户注册
test_user_register() {
    echo "测试数据: Email=$TEST_EMAIL, Username=$TEST_USERNAME"

    response=$(curl -s -w "\n%{http_code}" -X POST "$USER_SERVICE_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$TEST_EMAIL\",
            \"password\": \"$TEST_PASSWORD\",
            \"userName\": \"$TEST_USERNAME\"
        }")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ] || [ "$http_code" == "201" ]; then
        USER_ID=$(echo "$body" | grep -o '"data":[0-9]*' | head -1 | cut -d':' -f2)
        if [ -z "$USER_ID" ]; then
            USER_ID=$(echo "$body" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        fi
        print_result "用户注册" "PASS" "HTTP $http_code, User ID: $USER_ID"
        return 0
    else
        print_result "用户注册" "FAIL" "HTTP $http_code, Response: $body"
        return 1
    fi
}

# 测试用户登录
test_user_login() {
    response=$(curl -s -w "\n%{http_code}" -X POST "$USER_SERVICE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$TEST_EMAIL\",
            \"password\": \"$TEST_PASSWORD\"
        }")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        JWT_TOKEN=$(echo "$body" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
        if [ -z "$JWT_TOKEN" ]; then
            JWT_TOKEN=$(echo "$body" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        fi
        if [ -n "$JWT_TOKEN" ]; then
            print_result "用户登录" "PASS" "HTTP $http_code, Token obtained"
            return 0
        else
            print_result "用户登录" "FAIL" "Token not found in response"
            return 1
        fi
    else
        print_result "用户登录" "FAIL" "HTTP $http_code, Response: $body"
        return 1
    fi
}

# 测试获取用户信息
test_get_user_profile() {
    if [ -z "$USER_ID" ]; then
        print_result "获取用户信息" "FAIL" "User ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X GET "$USER_SERVICE_URL/api/v1/users/$USER_ID" \
        -H "Authorization: Bearer $JWT_TOKEN")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        username=$(echo "$body" | grep -o '"userName":"[^"]*"' | cut -d'"' -f4)
        if [ -z "$username" ]; then
            username=$(echo "$body" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
        fi
        print_result "获取用户信息" "PASS" "HTTP $http_code, Username: $username"
        return 0
    else
        print_result "获取用户信息" "FAIL" "HTTP $http_code"
        return 1
    fi
}

# 测试创建文章
test_create_post() {
    response=$(curl -s -w "\n%{http_code}" -X POST "$CONTENT_SERVICE_URL/api/v1/posts" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"title\": \"测试文章标题\",
            \"content\": \"这是一篇测试文章的内容，用于集成测试。\",
            \"contentType\": \"markdown\",
            \"tags\": [\"测试\", \"集成测试\"]
        }")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ] || [ "$http_code" == "201" ]; then
        POST_ID=$(echo "$body" | grep -o '"data":[0-9]*' | head -1 | cut -d':' -f2)
        if [ -z "$POST_ID" ]; then
            POST_ID=$(echo "$body" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        fi
        print_result "创建文章" "PASS" "HTTP $http_code, Post ID: $POST_ID"
        return 0
    else
        print_result "创建文章" "FAIL" "HTTP $http_code, Response: $body"
        return 1
    fi
}

# 测试发布文章
test_publish_post() {
    if [ -z "$POST_ID" ]; then
        print_result "发布文章" "FAIL" "Post ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID/publish" \
        -H "Authorization: Bearer $JWT_TOKEN")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" == "200" ]; then
        print_result "发布文章" "PASS" "HTTP $http_code"
        return 0
    else
        print_result "发布文章" "FAIL" "HTTP $http_code"
        return 1
    fi
}

# 测试查询文章详情
test_get_post_detail() {
    if [ -z "$POST_ID" ]; then
        print_result "查询文章详情" "FAIL" "Post ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X GET "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID" \
        -H "Authorization: Bearer $JWT_TOKEN")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        title=$(echo "$body" | grep -o '"title":"[^"]*"' | cut -d'"' -f4)
        print_result "查询文章详情" "PASS" "HTTP $http_code, Title: $title"
        return 0
    else
        print_result "查询文章详情" "FAIL" "HTTP $http_code"
        return 1
    fi
}

# 测试点赞文章
test_like_post() {
    if [ -z "$POST_ID" ]; then
        print_result "点赞文章" "FAIL" "Post ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID/like" \
        -H "Authorization: Bearer $JWT_TOKEN")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" == "200" ]; then
        print_result "点赞文章" "PASS" "HTTP $http_code"
        return 0
    else
        print_result "点赞文章" "FAIL" "HTTP $http_code"
        return 1
    fi
}

# 测试收藏文章
test_favorite_post() {
    if [ -z "$POST_ID" ]; then
        print_result "收藏文章" "FAIL" "Post ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$CONTENT_SERVICE_URL/api/v1/posts/$POST_ID/favorite" \
        -H "Authorization: Bearer $JWT_TOKEN")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" == "200" ]; then
        print_result "收藏文章" "PASS" "HTTP $http_code"
        return 0
    else
        print_result "收藏文章" "FAIL" "HTTP $http_code"
        return 1
    fi
}

# 测试创建评论
test_create_comment() {
    if [ -z "$POST_ID" ]; then
        print_result "创建评论" "FAIL" "Post ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$COMMENT_SERVICE_URL/api/v1/comments" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"postId\": $POST_ID,
            \"content\": \"这是一条测试评论\"
        }")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ] || [ "$http_code" == "201" ]; then
        COMMENT_ID=$(echo "$body" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        print_result "创建评论" "PASS" "HTTP $http_code, Comment ID: $COMMENT_ID"
        return 0
    else
        print_result "创建评论" "FAIL" "HTTP $http_code, Response: $body"
        return 1
    fi
}

# 测试查询评论列表
test_get_comments() {
    if [ -z "$POST_ID" ]; then
        print_result "查询评论列表" "FAIL" "Post ID not available"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X GET "$COMMENT_SERVICE_URL/api/v1/comments/post/$POST_ID/page?page=0&size=20" \
        -H "Authorization: Bearer $JWT_TOKEN")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" == "200" ]; then
        print_result "查询评论列表" "PASS" "HTTP $http_code"
        return 0
    else
        print_result "查询评论列表" "FAIL" "HTTP $http_code"
        return 1
    fi
}

# 主测试流程
main() {
    echo ""
    print_header "ZhiCore 核心业务流程 E2E 测试"
    echo ""
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""

    # ========================================
    # 1. 用户注册和登录流程
    # ========================================
    print_header "1. 用户注册和登录流程"
    echo ""

    test_user_register || exit 1
    sleep 1
    test_user_login || exit 1
    sleep 1
    test_get_user_profile || exit 1

    echo ""

    # ========================================
    # 2. 文章发布和互动流程
    # ========================================
    print_header "2. 文章发布和互动流程"
    echo ""

    test_create_post || exit 1
    sleep 1
    test_publish_post || exit 1
    sleep 1
    test_get_post_detail || exit 1
    sleep 1
    test_like_post || exit 1
    sleep 1
    test_favorite_post || exit 1

    echo ""

    # ========================================
    # 3. 评论互动流程
    # ========================================
    print_header "3. 评论互动流程"
    echo ""

    test_create_comment || exit 1
    sleep 1
    test_get_comments || exit 1

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
