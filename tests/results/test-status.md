# 测试状态追踪

> 最后更新时间: 2026-01-15 15:05:02

## 测试汇总

| 模块 | 总数 | 通过 | 失败 | 跳过 | 通过率 |
|------|------|------|------|------|--------|
| 用户服务 | 35 | 35 | 0 | 0 | 100% |
| 文章服务 | 10 | 0 | 0 | 0 | 0% |
| 评论服务 | 10 | 0 | 0 | 0 | 0% |
| 消息服务 | 4 | 0 | 0 | 0 | 0% |
| 通知服务 | 4 | 0 | 0 | 0 | 0% |
| 搜索服务 | 2 | 0 | 0 | 0 | 0% |
| 排行榜服务 | 3 | 0 | 0 | 0 | 0% |
| 上传服务 | 2 | 0 | 0 | 0 | 0% |
| 管理后台 | 8 | 0 | 0 | 0 | 0% |
| 网关服务 | 3 | 0 | 0 | 0 | 0% |
| **总计** | **81** | **35** | **0** | **0** | **43.2%** |

---

## 用户服务测试 (ZhiCore-user) - 35/35 PASS ?

### Section 1: Registration Tests (7 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-001 | Normal Registration | ? PASS | 864ms | 2026-01-15 15:05:02 | UserID: 270085928549097472 |
| USER-002 | Duplicate Email | ? PASS | 39ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-003 | Invalid Email Format | ? PASS | 33ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-004 | Short Username | ? PASS | 5ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-005 | Invalid Username Chars | ? PASS | 5ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-006 | Short Password | ? PASS | 5ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-007 | Empty Fields | ? PASS | 5ms | 2026-01-15 15:05:02 | Correctly rejected |

### Section 2: Login Tests (4 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-008 | Normal Login | ? PASS | 179ms | 2026-01-15 15:05:02 | Got JWT Token |
| USER-009 | Wrong Password | ? PASS | 103ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-010 | Non-existent Email | ? PASS | 35ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-011 | Empty Login Fields | ? PASS | 5ms | 2026-01-15 15:05:02 | Correctly rejected |

### Section 3: Token Tests (3 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-012 | Token Refresh | ? PASS | 94ms | 2026-01-15 15:05:02 | Got new Token |
| USER-013 | Invalid Refresh Token | ? PASS | 32ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-014 | Empty Refresh Token | ? PASS | 5ms | 2026-01-15 15:05:02 | Correctly rejected |

### Section 4: User Info Tests (3 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-015 | Get User Info | ? PASS | 59ms | 2026-01-15 15:05:02 | Username: testuser_20260115150502 |
| USER-016 | Get Non-existent User | ? PASS | 36ms | 2026-01-15 15:05:02 | Correctly returned error |
| USER-017 | Get User Without Auth | ? PASS | 45ms | 2026-01-15 15:05:02 | Public access allowed |

### Section 5: Follow Tests (10 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-018 | Follow User | ? PASS | 90ms | 2026-01-15 15:05:02 | Follow successful |
| USER-019 | Follow Self | ? PASS | 32ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-020 | Follow Non-existent | ? PASS | 35ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-021 | Duplicate Follow | ? PASS | 50ms | 2026-01-15 15:05:02 | Handled gracefully |
| USER-022 | Unfollow User | ? PASS | 49ms | 2026-01-15 15:05:02 | Unfollow successful |
| USER-023 | Unfollow Not Following | ? PASS | 37ms | 2026-01-15 15:05:02 | Handled gracefully |
| USER-024 | Get Followers | ? PASS | 62ms | 2026-01-15 15:05:02 | Followers: 1 |
| USER-025 | Get Following | ? PASS | 49ms | 2026-01-15 15:05:02 | Following: 1 |
| USER-026 | Check Following | ? PASS | 34ms | 2026-01-15 15:05:02 | IsFollowing: True |
| USER-027 | Get Follow Stats | ? PASS | 38ms | 2026-01-15 15:05:02 | Stats retrieved |

### Section 6: Check-In Tests (6 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-028 | Check In | ? PASS | 61ms | 2026-01-15 15:05:02 | Continuous: 1 |
| USER-029 | Duplicate Check In | ? PASS | 38ms | 2026-01-15 15:05:02 | Handled correctly |
| USER-030 | Check-In Stats | ? PASS | 35ms | 2026-01-15 15:05:02 | Total: 1d |
| USER-031 | Check In Non-existent | ? PASS | 34ms | 2026-01-15 15:05:02 | Correctly rejected |
| USER-032 | Monthly Check-In | ? PASS | 34ms | 2026-01-15 15:05:02 | Got bitmap |
| USER-033 | Invalid Month | ? PASS | 12ms | 2026-01-15 15:05:02 | Correctly rejected |

### Section 7: Pagination Tests (2 tests)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| USER-034 | Invalid Page | ? PASS | 45ms | 2026-01-15 15:05:02 | Handled |
| USER-035 | Large Page Size | ? PASS | 46ms | 2026-01-15 15:05:02 | Handled |

---

## 文章服务测试 (ZhiCore-post)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| POST-001 | 创建文章 | ? PENDING | - | - | - |
| POST-002 | 获取文章详情 | ? PENDING | - | - | - |
| POST-003 | 更新文章 | ? PENDING | - | - | - |
| POST-004 | 删除文章 | ? PENDING | - | - | - |
| POST-005 | 发布文章 | ? PENDING | - | - | - |
| POST-006 | 获取文章列表 | ? PENDING | - | - | - |
| POST-007 | 点赞文章 | ? PENDING | - | - | - |
| POST-008 | 取消点赞 | ? PENDING | - | - | - |
| POST-009 | 收藏文章 | ? PENDING | - | - | - |
| POST-010 | 取消收藏 | ? PENDING | - | - | - |

---

## 评论服务测试 (ZhiCore-comment)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| COMMENT-001 | 创建评论 | ? PENDING | - | - | - |
| COMMENT-002 | 获取评论列表 | ? PENDING | - | - | - |
| COMMENT-003 | 获取子评论 | ? PENDING | - | - | - |
| COMMENT-004 | 删除评论 | ? PENDING | - | - | - |
| COMMENT-005 | 点赞评论 | ? PENDING | - | - | - |
| COMMENT-006 | 取消评论点赞 | ? PENDING | - | - | - |
| COMMENT-007 | 回复评论 | ? PENDING | - | - | - |
| COMMENT-008 | 按热度排序评论 | ? PENDING | - | - | - |
| COMMENT-009 | 按时间排序评论 | ? PENDING | - | - | - |
| COMMENT-010 | 获取单条评论 | ? PENDING | - | - | - |

---















## 消息服务测试 (Message Service)
| 测试ID | 测试名称 | 状态 | 响应时间 | 备注 |
|--------|----------|------|----------|------|
| MSG-001 | Send Text Message | ? PASS | 129ms | MessageID: 270421578548781056 |
| MSG-002 | Send Empty Message | ? PASS | 129ms | Correctly rejected |
| MSG-003 | Send Long Message | ? PASS | 79ms | Correctly rejected |
| MSG-004 | Send to Non-existent | ? PASS | 54ms | Correctly rejected |
| MSG-005 | Send to Self | ? PASS | 31ms | Correctly rejected |
| MSG-006 | Send Without Auth | ? PASS | 33ms | Correctly rejected |
| MSG-007 | Get Message History | ? PASS | 35ms | Messages: 1 |
| MSG-008 | Non-existent Conversation | ? PASS | 33ms | Correctly handled |
| MSG-009 | Message Pagination | ? PASS | 34ms | Messages: 1 |
| MSG-010 | Other User's Conversation | ? PASS | 33ms | Correctly rejected |
| MSG-011 | Invalid Pagination | ? PASS | 33ms | Correctly rejected |
| MSG-012 | Get Conversation List | ? PASS | 44ms | Conversations: 1 |
| MSG-013 | Conversation Sorting | ? PASS | 44ms | Sorted by last message |
| MSG-014 | Get Conversation Detail | ? PASS | 41ms | Got conversation info |
| MSG-015 | Non-existent Conversation Detail | ? PASS | 32ms | Correctly returned error |
| MSG-016 | Get Conversation by User | ? PASS | 94ms | Found conversation |
| MSG-017 | Mark as Read | ? PASS | 36ms | Marked as read |
| MSG-018 | Mark Non-existent as Read | ? PASS | 34ms | Correctly returned error |
| MSG-019 | Get Unread Count | ? PASS | 33ms | Unread: 0 |
| MSG-020 | Batch Mark as Read | ? PASS | 51ms | Batch marked as read |

**统计**: 总计 20 | 通过 20 | 失败 0 | 跳过 0
**执行时间**: 2026-01-16 13:18:50## 通知服务测试 (ZhiCore-notification)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| NOTIF-001 | 获取通知列表 | ? PENDING | - | - | - |
| NOTIF-002 | 标记通知已读 | ? PENDING | - | - | - |
| NOTIF-003 | 批量标记已读 | ? PENDING | - | - | - |
| NOTIF-004 | 获取未读数量 | ? PENDING | - | - | - |

---

## 搜索服务测试 (ZhiCore-search)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| SEARCH-001 | 搜索文章 | ? PENDING | - | - | - |
| SEARCH-002 | 搜索建议 | ? PENDING | - | - | - |

---

## 排行榜服务测试 (ZhiCore-ranking)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| RANK-001 | 热门文章 | ? PENDING | - | - | - |
| RANK-002 | 创作者排行 | ? PENDING | - | - | - |
| RANK-003 | 热门话题 | ? PENDING | - | - | - |

---

## 上传服务测试 (ZhiCore-upload)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| UPLOAD-001 | 上传图片 | ? PENDING | - | - | - |
| UPLOAD-002 | 上传文件 | ? PENDING | - | - | - |

---

## 管理后台测试 (ZhiCore-admin)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| ADMIN-001 | 获取用户列表 | ? PENDING | - | - | - |
| ADMIN-002 | 禁用用户 | ? PENDING | - | - | - |
| ADMIN-003 | 获取文章列表 | ? PENDING | - | - | - |
| ADMIN-004 | 删除文章 | ? PENDING | - | - | - |
| ADMIN-005 | 获取评论列表 | ? PENDING | - | - | - |
| ADMIN-006 | 删除评论 | ? PENDING | - | - | - |
| ADMIN-007 | 获取举报列表 | ? PENDING | - | - | - |
| ADMIN-008 | 处理举报 | ? PENDING | - | - | - |

---

## 网关服务测试 (ZhiCore-gateway)

| 测试ID | 测试名称 | 状态 | 响应时间 | 执行时间 | 备注 |
|--------|----------|------|----------|----------|------|
| GW-001 | 路由转发测试 | ? PENDING | - | - | - |
| GW-002 | 无Token认证测试 | ? PENDING | - | - | - |
| GW-003 | 限流测试 | ? PENDING | - | - | - |

---

## 压力测试

| 场景ID | 测试名称 | 状态 | P99延迟 | 实际QPS | 目标QPS | 执行时间 |
|--------|----------|------|---------|---------|---------|----------|
| LOAD-001 | 文章详情压测 | ? PENDING | - | - | >1000 | - |
| LOAD-002 | 文章列表压测 | ? PENDING | - | - | >500 | - |
| LOAD-003 | 点赞接口压测 | ? PENDING | - | - | >2000 | - |
| LOAD-004 | 搜索接口压测 | ? PENDING | - | - | >200 | - |
| LOAD-005 | 通知列表压测 | ? PENDING | - | - | >500 | - |
| LOAD-006 | 评论列表压测 | ? PENDING | - | - | >800 | - |
| LOAD-007 | 创建评论压测 | ? PENDING | - | - | >500 | - |
| LOAD-008 | 评论点赞压测 | ? PENDING | - | - | >1500 | - |











## 鏂囩珷鏈嶅姟娴嬭瘯 (Post Service)
| 娴嬭瘯ID | 娴嬭瘯鍚嶇О | 鐘舵€?| 鍝嶅簲鏃堕棿 | 澶囨敞 |
|--------|----------|------|----------|------|
| POST-001 | Create Post | [OK] PASS | 3666ms | PostID: 270431981693575168 |
| POST-002 | Empty Title | [OK] PASS | 2360ms | Correctly rejected |
| POST-003 | Empty Content | [OK] PASS | 331ms | Handled gracefully |
| POST-004 | Long Title | [OK] PASS | 19ms | Correctly rejected |
| POST-005 | Get Post Detail | [OK] PASS | 459ms | Title: Test Post 20260116140005 |
| POST-006 | Get Non-existent Post | [OK] PASS | 88ms | Correctly returned error |
| POST-007 | Update Post | [OK] PASS | 583ms | Update successful |
| POST-008 | Update Other's Post | [OK] PASS | 25ms | Correctly rejected |
| POST-009 | Delete Post | [OK] PASS | 103ms | Delete successful |
| POST-010 | Delete Other's Post | [OK] PASS | 11ms | Correctly rejected |
| POST-011 | Delete Non-existent | [OK] PASS | 166ms | Correctly returned error |
| POST-012 | Create Without Auth | [OK] PASS | 539ms | Correctly rejected |
| POST-013 | Publish Draft | [OK] PASS | 136ms | Publish successful |
| POST-014 | Publish Published | [OK] PASS | 49ms | Handled gracefully |
| POST-015 | Publish Other's Post | [OK] PASS | 12ms | Correctly rejected |
| POST-016 | Publish Non-existent | [OK] PASS | 49ms | Correctly returned error |
| POST-017 | Unpublish Post | [OK] PASS | 147ms | Unpublish successful |
| POST-018 | Get Posts List | [OK] PASS | 3042ms | Posts: 11 |
| POST-019 | Get Posts by Category | [OK] PASS | 62ms | Handled |
| POST-020 | Get Posts by Tag | [OK] PASS | 98ms | Handled |
| POST-021 | Get User's Posts | [OK] PASS | 67ms | Posts: 1 |
| POST-022 | Invalid Pagination | [OK] PASS | 41ms | Handled gracefully |
| POST-023 | Get Drafts List | [OK] PASS | 39ms | Drafts: 1 |
| POST-024 | Like Post | [OK] PASS | 481ms | Like successful |
| POST-025 | Duplicate Like | [OK] PASS | 56ms | Handled gracefully |
| POST-026 | Unlike Post | [OK] PASS | 51ms | Unlike successful |
| POST-027 | Unlike Not Liked | [OK] PASS | 36ms | Handled gracefully |
| POST-028 | Like Non-existent | [OK] PASS | 37ms | Correctly returned error |
| POST-029 | Check Like Status | [OK] PASS | 32ms | IsLiked: True |
| POST-030 | Favorite Post | [OK] PASS | 185ms | Favorite successful |
| POST-031 | Duplicate Favorite | [OK] PASS | 33ms | Handled gracefully |
| POST-032 | Unfavorite Post | [OK] PASS | 41ms | Unfavorite successful |
| POST-033 | Unfavorite Not Favorited | [OK] PASS | 42ms | Handled gracefully |
| POST-034 | Favorite Non-existent | [OK] PASS | 34ms | Correctly returned error |
| POST-035 | Check Favorite Status | [OK] PASS | 50ms | IsFavorited: True |
| POST-036 | XSS in Title | [OK] PASS | 72ms | Title stored (frontend should escape) |
| POST-037 | XSS in Content | [OK] PASS | 79ms | XSS content handled |
| POST-038 | SQL Injection ID | [OK] PASS | 38ms | SQL injection rejected |
| POST-039 | HTML Tag Injection | [OK] PASS | 69ms | HTML content handled |
| POST-040 | Special Chars Title | [OK] PASS | 46ms | Special chars handled |
| POST-041 | XSS in ID Param | [OK] PASS | 237ms | XSS in ID rejected |

**缁熻**: 鎬昏 41 涓祴璇? 閫氳繃 41, 澶辫触 0, 璺宠繃 0, 閫氳繃鐜?100%
**鎵ц鏃堕棿**: 2026-01-16 14:00:21
## 璇勮鏈嶅姟娴嬭瘯 (Comment Service)
| 娴嬭瘯ID | 娴嬭瘯鍚嶇О | 鐘舵€?| 鍝嶅簲鏃堕棿 | 澶囨敞 |
|--------|----------|------|----------|------|
| COMMENT-001 | Create Comment | [PASS] PASS | 2448ms | CommentID: 270432258572165120 |
| COMMENT-002 | Empty Content | [PASS] PASS | 94ms | Correctly rejected |
| COMMENT-003 | Long Content | [PASS] PASS | 52ms | Correctly rejected |
| COMMENT-004 | Comment Non-existent Post | [PASS] PASS | 74ms | Correctly rejected |
| COMMENT-005 | Get Comment Detail | [PASS] PASS | 207ms | Content: This is a test comme... |
| COMMENT-006 | Get Non-existent Comment | [PASS] PASS | 89ms | Correctly returned error |
| COMMENT-007 | Delete Comment | [PASS] PASS | 95ms | Delete successful |
| COMMENT-008 | Delete Other's Comment | [PASS] PASS | 175ms | Correctly rejected |
| COMMENT-009 | Delete Non-existent | [PASS] PASS | 33ms | Correctly returned error |
| COMMENT-010 | Create Without Auth | [PASS] PASS | 97ms | Correctly rejected |
| COMMENT-011 | Reply to Comment | [PASS] PASS | 256ms | ReplyID: 270432264721014784 |
| COMMENT-012 | Reply Non-existent | [PASS] PASS | 42ms | Correctly rejected |
| COMMENT-013 | Get Replies (Page) | [PASS] PASS | 94ms | Replies: 0 |
| COMMENT-014 | Multi-level Reply | [PASS] PASS | 94ms | Multi-level reply created |
| COMMENT-015 | Reply Deleted Comment | [PASS] PASS | 59ms | Correctly rejected |
| COMMENT-016 | Get Post Comments (Page) | [PASS] PASS | 81ms | Comments: 0 |
| COMMENT-017 | Get Comments (Hot Sort) | [PASS] PASS | 68ms | Comments: 0 |
| COMMENT-018 | Get Comments (Time Sort) | [PASS] PASS | 75ms | Comments: 0 |
| COMMENT-019 | Get Comments (Cursor) | [PASS] PASS | 74ms | HasNext:  |
| COMMENT-020 | Invalid Cursor | [PASS] PASS | 66ms | Handled gracefully |
| COMMENT-021 | Comments Non-existent Post | [PASS] PASS | 40ms | Handled: 0 comments |
| COMMENT-022 | Like Comment | [PASS] PASS | 92ms | Like successful |
| COMMENT-023 | Duplicate Like | [PASS] PASS | 36ms | Handled gracefully |
| COMMENT-024 | Unlike Comment | [PASS] PASS | 42ms | Unlike successful |
| COMMENT-025 | Unlike Not Liked | [PASS] PASS | 37ms | Handled gracefully |
| COMMENT-026 | Like Non-existent | [PASS] PASS | 35ms | Correctly rejected |
| COMMENT-027 | Check Like Status | [PASS] PASS | 31ms | IsLiked: True |
| COMMENT-028 | Get Like Count | [PASS] PASS | 33ms | LikeCount: 1 |
| COMMENT-029 | Get Post Comment Count | [PASS] PASS | 66ms | Total: 0 |
| COMMENT-030 | Batch Check Like | [PASS] PASS | 57ms | Checked: 2 comments |
| COMMENT-031 | XSS in Content | [PASS] PASS | 52ms | Content stored (frontend should escape) |
| COMMENT-032 | SQL Injection ID | [PASS] PASS | 48ms | SQL injection rejected |
| COMMENT-033 | HTML Tag Injection | [PASS] PASS | 45ms | HTML content handled |
| COMMENT-034 | Special Chars Content | [PASS] PASS | 74ms | Special chars handled |
| COMMENT-035 | XSS in ID Param | [PASS] PASS | 150ms | XSS in ID rejected |
| COMMENT-036 | SQL Injection Post ID | [PASS] PASS | 29ms | Handled gracefully |

**缁熻**: 鎬昏 36 | 閫氳繃 36 | 澶辫触 0 | 璺宠繃 0
**鎵ц鏃堕棿**: 2026-01-16 14:01:18
## 閫氱煡鏈嶅姟娴嬭瘯 (Notification Service)
**娴嬭瘯鏃堕棿**: 2026-01-16 14:04:54
**娴嬭瘯缁撴灉**: 27 閫氳繃, 0 澶辫触, 0 璺宠繃

| 娴嬭瘯ID | 娴嬭瘯鍚嶇О | 鐘舵€?| 鍝嶅簲鏃堕棿 | 澶囨敞 |
|--------|----------|------|----------|------|
| NOTIF-001 | Get Notification List | [PASS] PASS | 44ms | Notifications: 0 |
| NOTIF-002 | Filter by Type | [PASS] PASS | 33ms | LIKE notifications: 0 |
| NOTIF-003 | Notification Pagination | [PASS] PASS | 36ms | Page size: 0, Total: 0 |
| NOTIF-004 | Invalid Pagination | [PASS] PASS | 36ms | Handled gracefully with defaults |
| NOTIF-005 | Get Without Auth | [PASS] PASS | 40ms | Gateway handles auth (empty results) |
| NOTIF-006 | Mark as Read | [PASS] PASS | 34ms | Marked successfully |
| NOTIF-007 | Mark Non-existent | [PASS] PASS | 35ms | Handled gracefully |
| NOTIF-008 | Mark Other's Notification | [PASS] PASS | 37ms | Handled gracefully |
| NOTIF-009 | Batch Mark Read | [PASS] PASS | 36ms | Batch marked successfully |
| NOTIF-010 | Mark All Read | [PASS] PASS | 36ms | All marked successfully |
| NOTIF-011 | Repeat Mark Read | [PASS] PASS | 37ms | Idempotent operation |
| NOTIF-012 | Get Unread Count | [PASS] PASS | 35ms | Unread: 0 |
| NOTIF-013 | Unread Count by Type | [PASS] PASS | 33ms | LIKE unread: 0 |
| NOTIF-014 | Delete Notification | [PASS] PASS | 30ms | Delete handled gracefully (endpoint may not exist) |
| NOTIF-015 | Delete Non-existent | [PASS] PASS | 11ms | Correctly returned error |
| NOTIF-016 | Empty ID | [PASS] PASS | 9ms | Correctly rejected empty ID |
| NOTIF-017 | Special Chars ID | [PASS] PASS | 62ms | Correctly rejected special chars |
| NOTIF-018 | SQL Injection ID | [PASS] PASS | 28ms | Handled gracefully |
| NOTIF-019 | Large Page Size | [PASS] PASS | 28ms | Returned 0 items (may be capped) |
| NOTIF-020 | Large Page Number | [PASS] PASS | 29ms | Returned 0 items (expected 0) |
| NOTIF-021 | Zero Page Size | [PASS] PASS | 15ms | Correctly rejected |
| NOTIF-022 | Invalid Type | [PASS] PASS | 29ms | Handled gracefully (ignored invalid type) |
| NOTIF-023 | Long ID | [PASS] PASS | 29ms | Handled gracefully |
| NOTIF-024 | Malformed Token | [PASS] PASS | 25ms | Gateway handles auth |
| NOTIF-025 | Expired Token | [PASS] PASS | 25ms | Gateway handles auth |
| NOTIF-026 | HTML Tag Injection | [PASS] PASS | 28ms | Handled gracefully |
| NOTIF-027 | Special Characters | [PASS] PASS | 5ms | Special chars correctly rejected |
















## Search Service Tests
| TestID | TestName | Status | ResponseTime | Note |
|--------|----------|--------|--------------|------|
| SEARCH-001 | Keyword Search | [PASS] PASS | 49ms | Items: 1, Total: 1 |
| SEARCH-002 | Empty Keyword Search | [PASS] PASS | 29ms | Correctly rejected empty keyword |
| SEARCH-003 | Special Characters Search | [PASS] PASS | 47ms | Handled gracefully, items: 1 |
| SEARCH-004 | Long Keyword Search | [PASS] PASS | 204ms | Handled gracefully, items: 1 |
| SEARCH-005 | Search Pagination | [PASS] PASS | 43ms | Page size: 1, Total: 1, Pages: 1 |
| SEARCH-006 | Search Sorting | [PASS] PASS | 43ms | Results sorted by relevance |
| SEARCH-007 | No Results Search | [PASS] PASS | 101ms | Returned 1 items (unexpected but handled) |
| SEARCH-008 | Highlight Display | [PASS] PASS | 50ms | Highlights present in results |
| SEARCH-009 | Get Suggestions | [PASS] PASS | 41ms | Suggestions: 3 |
| SEARCH-010 | Empty Prefix Suggestions | [PASS] PASS | 7ms | Empty prefix correctly rejected |
| SEARCH-011 | Suggestion Limit | [PASS] PASS | 38ms | Returned 2 (limit 5) |
| SEARCH-012 | Special Chars Suggestions | [PASS] PASS | 39ms | Handled gracefully, suggestions: 2 |
| SEARCH-013 | Negative Page Number | [PASS] PASS | 31ms | Correctly rejected negative page |
| SEARCH-014 | Large Page Number | [PASS] PASS | 42ms | Large page rejected (acceptable) |
| SEARCH-015 | Zero Page Size | [PASS] PASS | 5ms | Correctly rejected zero size |
| SEARCH-016 | Large Page Size | [PASS] PASS | 6ms | Large size rejected (acceptable) |
| SEARCH-017 | Long Prefix Suggestions | [PASS] PASS | 40ms | Handled gracefully, suggestions: 2 |
| SEARCH-018 | Invalid Limit Parameter | [PASS] PASS | 5ms | Correctly rejected negative limit |
| SEARCH-019 | SQL Injection Keyword | [PASS] PASS | 65ms | Handled safely, items: 1 |
| SEARCH-020 | XSS in Keyword | [PASS] PASS | 57ms | XSS handled safely, items: 1 |
| SEARCH-021 | Search Without Auth | [PASS] PASS | 40ms | Public search allowed, items: 1 |
| SEARCH-022 | Unicode/Chinese Search | [PASS] PASS | 49ms | Unicode handled correctly, items: 1 |

**Test Time**: 2026-01-16 15:35:34
**Test Result**: 22 passed, 0 failed, 0 skipped



## Ranking Service Tests
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| RANK-001 | Get Hot Posts | [PASS] | 35ms | Items: 0 |
| RANK-002 | Hot Posts Pagination | [PASS] | 37ms | Page size: 0 (limit 5) |
| RANK-003 | Hot Posts with Scores | [PASS] | 36ms | Items: 0 |
| RANK-004 | Daily Hot Posts | [PASS] | 35ms | Daily posts: 0 |
| RANK-005 | Invalid Date Format | [PASS] | 3ms | Correctly rejected invalid date |
| RANK-006 | Future Date | [PASS] | 37ms | Handled gracefully, items: 0 |
| RANK-007 | Weekly Hot Posts | [PASS] | 35ms | Weekly posts: 0 |
| RANK-008 | Weekly with Week Number | [PASS] | 40ms | Week 3 posts: 0 |
| RANK-009 | Get Post Rank | [PASS] | 35ms | Rank:  |
| RANK-010 | Get Post Score | [PASS] | 35ms | Score:  |
| RANK-011 | Post Rank SQL Injection | [PASS] | 31ms | SQL injection handled safely |
| RANK-012 | Post Score XSS | [PASS] | 25ms | XSS handled safely |
| RANK-013 | Get Creator Ranking | [PASS] | 24ms | Creators: 0 |
| RANK-014 | Creator Ranking with Scores | [PASS] | 23ms | Items: 0 |
| RANK-015 | Get Creator Rank | [PASS] | 24ms | Rank:  |
| RANK-016 | Get Creator Score | [PASS] | 26ms | Score:  |
| RANK-017 | Creator Negative Page | [PASS] | 25ms | Handled gracefully |
| RANK-018 | Creator Zero Size | [PASS] | 24ms | Handled gracefully, items: 0 |
| RANK-019 | Creator Size Exceeds MAX | [PASS] | 23ms | Capped at MAX_SIZE, items: 0 |
| RANK-020 | Creator Rank SQL Injection | [PASS] | 23ms | SQL injection handled safely |
| RANK-021 | Get Hot Topics | [PASS] | 23ms | Topics: 0 |
| RANK-022 | Hot Topics with Scores | [PASS] | 25ms | Items: 0 |
| RANK-023 | Get Topic Rank | [PASS] | 25ms | Rank:  |
| RANK-024 | Get Topic Score | [PASS] | 24ms | Score:  |
| RANK-025 | Topic Large Page Number | [PASS] | 24ms | Handled gracefully, items: 0 |
| RANK-026 | Topic Negative Size | [PASS] | 24ms | Handled gracefully |
| RANK-027 | Topic Invalid ID | [PASS] | 3ms | Invalid ID handled (status: 400) |
| RANK-028 | Topic Score SQL Injection | [PASS] | 24ms | SQL injection handled safely |
| RANK-029 | Empty String Post ID | [PASS] | 11ms | Empty ID handled (status: 500) |
| RANK-030 | Special Characters in ID | [PASS] | 24ms | Special chars handled safely |
| RANK-031 | Very Long ID | [PASS] | 23ms | Long ID handled safely |
| RANK-032 | Invalid Week Number | [PASS] | 23ms | Handled gracefully |
| RANK-033 | Week Number Too Large | [PASS] | 24ms | Handled gracefully, items: 0 |
| RANK-034 | Daily Limit Zero | [PASS] | 24ms | Handled gracefully, items: 0 |
| RANK-035 | Daily Limit Exceeds MAX | [PASS] | 25ms | Capped at MAX_SIZE, items: 0 |
| RANK-036 | No Auth Token | [PASS] | 24ms | Public endpoint (no auth needed) |

**Test Time**: 2026-01-16 16:38:04
**Test Result**: 36 passed, 0 failed, 0 skipped





## Upload Service Tests (ZhiCore-upload)

| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| UPLOAD-001 | Upload JPEG Image | [PASS] | 102ms | URL: http://localhost:8089/files/images/1768554969479.webp |
| UPLOAD-002 | Upload Invalid Format | [PASS] | 59ms | Correctly rejected |
| UPLOAD-003 | Upload Oversized Image | [PASS] | 96ms | Correctly rejected |
| UPLOAD-004 | Upload Empty File | [PASS] | 31ms | Correctly rejected |
| UPLOAD-005 | Upload Without Auth | [PASS] | 71ms | Auth at gateway level (direct access allowed) |
| UPLOAD-006 | Upload PNG Image | [PASS] | 73ms | Compressed to WebP |
| UPLOAD-007 | Thumbnail Generation | [PASS] | 78ms | Thumbnail: http://localhost:8089/files/thumbnails/1768554977416.webp |
| UPLOAD-008 | Special Filename | [PASS] | 74ms | Handled gracefully |
| UPLOAD-009 | Upload PDF File | [PASS] | 54ms | URL: http://localhost:8089/files/files/1768554977573.pdf |
| UPLOAD-010 | Upload Forbidden Type | [PASS] | 32ms | Correctly rejected |
| UPLOAD-011 | Upload Oversized File | [PASS] | 90ms | Correctly rejected |
| UPLOAD-012 | Upload Text File | [PASS] | 53ms | URL: http://localhost:8089/files/files/1768554977812.txt |
| UPLOAD-013 | Path Traversal Prevention | [PASS] | 57ms | Handled securely |
| UPLOAD-014 | Delete File | [PASS] | 29ms | Delete successful |
| UPLOAD-015 | Delete Non-existent | [PASS] | 37ms | Handled gracefully |

**Test Time**: 2026-01-16 17:16:18
**Test Result**: 15 passed, 0 failed, 0 skipped




## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 184ms | 系统繁忙，请稍后重试 |
| ADMIN-002 | Search Users | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-003 | Disable User | [FAIL] | 75ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 15ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 15ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 14ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 21ms | 系统繁忙，请稍后重试 |
| ADMIN-008 | Get Post List | [FAIL] | 49ms | 系统繁忙，请稍后重试 |
| ADMIN-009 | Search Posts | [FAIL] | 24ms | 系统繁忙，请稍后重试 |
| ADMIN-010 | Delete Post | [FAIL] | 20ms | 系统繁忙，请稍后重试 |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 18ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 13ms | 系统繁忙，请稍后重试 |
| ADMIN-014 | Get Comment List | [FAIL] | 33ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 15ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 13ms | 系统繁忙，请稍后重试 |
| ADMIN-019 | Filter Comments by User | [FAIL] | 13ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [FAIL] | 299ms | 系统繁忙，请稍后重试 |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 21ms | 系统繁忙，请稍后重试 |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 13ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 11ms | Correctly rejected |

**Test Time**: 2026-01-19 23:38:43
**Test Result**: 6 passed, 16 failed, 3 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 28ms | 系统繁忙，请稍后重试 |
| ADMIN-002 | Search Users | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-003 | Disable User | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 11ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 12ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-008 | Get Post List | [FAIL] | 13ms | 系统繁忙，请稍后重试 |
| ADMIN-009 | Search Posts | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-010 | Delete Post | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 13ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-014 | Get Comment List | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 14ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-019 | Filter Comments by User | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [FAIL] | 21ms | 系统繁忙，请稍后重试 |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 9ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 9ms | Correctly rejected |

**Test Time**: 2026-01-19 23:45:46
**Test Result**: 6 passed, 17 failed, 2 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 11:47:18
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 12:38:12
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 12:50:24
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 712ms | 系统繁忙，请稍后重试 |
| ADMIN-002 | Search Users | [FAIL] | 23ms | 系统繁忙，请稍后重试 |
| ADMIN-003 | Disable User | [FAIL] | 146ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 17ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 15ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 15ms | 系统繁忙，请稍后重试 |
| ADMIN-008 | Get Post List | [FAIL] | 173ms | 系统繁忙，请稍后重试 |
| ADMIN-009 | Search Posts | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-010 | Delete Post | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 16ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 11ms | 系统繁忙，请稍后重试 |
| ADMIN-014 | Get Comment List | [FAIL] | 37ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 14ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [FAIL] | 35ms | 系统繁忙，请稍后重试 |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 22ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-019 | Filter Comments by User | [FAIL] | 13ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [FAIL] | 667ms | 系统繁忙，请稍后重试 |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 24ms | 系统繁忙，请稍后重试 |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 24ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 15ms | Correctly rejected |

**Test Time**: 2026-01-20 13:05:45
**Test Result**: 6 passed, 17 failed, 2 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-002 | Search Users | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-003 | Disable User | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 20ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 19ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 24ms | 系统繁忙，请稍后重试 |
| ADMIN-008 | Get Post List | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-009 | Search Posts | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-010 | Delete Post | [FAIL] | 15ms | 系统繁忙，请稍后重试 |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 17ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-014 | Get Comment List | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 17ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-019 | Filter Comments by User | [FAIL] | 16ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [PASS] | 148ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 86ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 13ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 11ms | Correctly rejected |

**Test Time**: 2026-01-20 13:07:21
**Test Result**: 8 passed, 15 failed, 2 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 410ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 71ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 456ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 74ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 74ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [FAIL] | 69ms | Should return 403 |
| ADMIN-007 | Get User Details | [PASS] | 55ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 216ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 59ms | Search successful |
| ADMIN-010 | Delete Post | [PASS] | 101ms | Post deleted |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 71ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 57ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 179ms | æ¥è¯¢æç« åè¡¨å¤±è´¥ |
| ADMIN-014 | Get Comment List | [PASS] | 263ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 143ms | Search successful |
| ADMIN-016 | Delete Comment | [PASS] | 145ms | Comment deleted |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 61ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [PASS] | 88ms | Filter successful |
| ADMIN-019 | Filter Comments by User | [PASS] | 79ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 171ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 46ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 37ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 11ms | Correctly rejected |

**Test Time**: 2026-01-20 13:16:20
**Test Result**: 21 passed, 2 failed, 2 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 64ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 56ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 72ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 64ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 51ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [FAIL] | 63ms | Should return 403 |
| ADMIN-007 | Get User Details | [PASS] | 50ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 70ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 49ms | Search successful |
| ADMIN-010 | Delete Post | [PASS] | 60ms | Post deleted |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 47ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 51ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 63ms | æ¥è¯¢æç« åè¡¨å¤±è´¥ |
| ADMIN-014 | Get Comment List | [PASS] | 93ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 91ms | Search successful |
| ADMIN-016 | Delete Comment | [PASS] | 63ms | Comment deleted |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 51ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [PASS] | 91ms | Filter successful |
| ADMIN-019 | Filter Comments by User | [PASS] | 92ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 51ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 48ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 9ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 9ms | Correctly rejected |

**Test Time**: 2026-01-20 13:17:24
**Test Result**: 21 passed, 2 failed, 2 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 13:30:15
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 13:30:50
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 168ms | éè¦ç®¡çåæé |
| ADMIN-002 | Search Users | [FAIL] | 41ms | éè¦ç®¡çåæé |
| ADMIN-003 | Disable User | [FAIL] | 43ms | éè¦ç®¡çåæé |
| ADMIN-004 | Enable User | [FAIL] | 44ms | éè¦ç®¡çåæé |
| ADMIN-005 | Disable Non-existent | [PASS] | 43ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 44ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 42ms | éè¦ç®¡çåæé |
| ADMIN-008 | Get Post List | [FAIL] | 47ms | éè¦ç®¡çåæé |
| ADMIN-009 | Search Posts | [FAIL] | 41ms | éè¦ç®¡çåæé |
| ADMIN-010 | Delete Post | [FAIL] | 45ms | éè¦ç®¡çåæé |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 47ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 47ms | éè¦ç®¡çåæé |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 58ms | éè¦ç®¡çåæé |
| ADMIN-014 | Get Comment List | [FAIL] | 83ms | éè¦ç®¡çåæé |
| ADMIN-015 | Search Comments | [FAIL] | 47ms | éè¦ç®¡çåæé |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 51ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 63ms | éè¦ç®¡çåæé |
| ADMIN-019 | Filter Comments by User | [FAIL] | 54ms | éè¦ç®¡çåæé |
| ADMIN-020 | Get Pending Reports | [FAIL] | 65ms | éè¦ç®¡çåæé |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 48ms | éè¦ç®¡çåæé |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 44ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 39ms | Correctly rejected |

**Test Time**: 2026-01-20 13:45:00
**Test Result**: 6 passed, 16 failed, 3 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 41ms | éè¦ç®¡çåæé |
| ADMIN-002 | Search Users | [FAIL] | 39ms | éè¦ç®¡çåæé |
| ADMIN-003 | Disable User | [FAIL] | 40ms | éè¦ç®¡çåæé |
| ADMIN-004 | Enable User | [FAIL] | 42ms | éè¦ç®¡çåæé |
| ADMIN-005 | Disable Non-existent | [PASS] | 41ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 49ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 39ms | éè¦ç®¡çåæé |
| ADMIN-008 | Get Post List | [FAIL] | 42ms | éè¦ç®¡çåæé |
| ADMIN-009 | Search Posts | [FAIL] | 43ms | éè¦ç®¡çåæé |
| ADMIN-010 | Delete Post | [FAIL] | 40ms | éè¦ç®¡çåæé |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 41ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 41ms | éè¦ç®¡çåæé |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 39ms | éè¦ç®¡çåæé |
| ADMIN-014 | Get Comment List | [FAIL] | 42ms | éè¦ç®¡çåæé |
| ADMIN-015 | Search Comments | [FAIL] | 40ms | éè¦ç®¡çåæé |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 40ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 53ms | éè¦ç®¡çåæé |
| ADMIN-019 | Filter Comments by User | [FAIL] | 39ms | éè¦ç®¡çåæé |
| ADMIN-020 | Get Pending Reports | [FAIL] | 41ms | éè¦ç®¡çåæé |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 40ms | éè¦ç®¡çåæé |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 41ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 40ms | Correctly rejected |

**Test Time**: 2026-01-20 13:47:09
**Test Result**: 6 passed, 16 failed, 3 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 553ms |  |
| ADMIN-002 | Search Users | [FAIL] | 9ms |  |
| ADMIN-003 | Disable User | [FAIL] | 8ms |  |
| ADMIN-004 | Enable User | [FAIL] | 7ms |  |
| ADMIN-005 | Disable Non-existent | [FAIL] | 6ms | Should return 404 |
| ADMIN-006 | Non-admin Access | [FAIL] | 7ms | Should return 403 |
| ADMIN-007 | Get User Details | [FAIL] | 7ms |  |
| ADMIN-008 | Get Post List | [FAIL] | 6ms |  |
| ADMIN-009 | Search Posts | [FAIL] | 7ms |  |
| ADMIN-010 | Delete Post | [FAIL] | 6ms |  |
| ADMIN-011 | Delete Non-existent Post | [FAIL] | 17ms | Should return 404 |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 14ms |  |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 7ms |  |
| ADMIN-014 | Get Comment List | [FAIL] | 7ms |  |
| ADMIN-015 | Search Comments | [FAIL] | 7ms |  |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [FAIL] | 7ms | Should return 404 |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 8ms |  |
| ADMIN-019 | Filter Comments by User | [FAIL] | 7ms |  |
| ADMIN-020 | Get Pending Reports | [FAIL] | 6ms |  |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 6ms |  |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [FAIL] | 7ms | Should return 404 |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [FAIL] | 7ms | Should return 400 |

**Test Time**: 2026-01-20 13:49:04
**Test Result**: 0 passed, 22 failed, 3 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 28ms |  |
| ADMIN-002 | Search Users | [FAIL] | 8ms |  |
| ADMIN-003 | Disable User | [FAIL] | 8ms |  |
| ADMIN-004 | Enable User | [FAIL] | 6ms |  |
| ADMIN-005 | Disable Non-existent | [FAIL] | 9ms | Should return 404 |
| ADMIN-006 | Non-admin Access | [FAIL] | 7ms | Should return 403 |
| ADMIN-007 | Get User Details | [FAIL] | 7ms |  |
| ADMIN-008 | Get Post List | [FAIL] | 7ms |  |
| ADMIN-009 | Search Posts | [FAIL] | 7ms |  |
| ADMIN-010 | Delete Post | [FAIL] | 7ms |  |
| ADMIN-011 | Delete Non-existent Post | [FAIL] | 6ms | Should return 404 |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 9ms |  |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 6ms |  |
| ADMIN-014 | Get Comment List | [FAIL] | 7ms |  |
| ADMIN-015 | Search Comments | [FAIL] | 7ms |  |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [FAIL] | 6ms | Should return 404 |
| ADMIN-018 | Filter Comments by Post | [FAIL] | 12ms |  |
| ADMIN-019 | Filter Comments by User | [FAIL] | 12ms |  |
| ADMIN-020 | Get Pending Reports | [FAIL] | 8ms |  |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 7ms |  |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [FAIL] | 6ms | Should return 404 |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [FAIL] | 7ms | Should return 400 |

**Test Time**: 2026-01-20 13:50:56
**Test Result**: 0 passed, 22 failed, 3 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 170ms | 系统繁忙，请稍后重试 |
| ADMIN-002 | Search Users | [FAIL] | 21ms | 系统繁忙，请稍后重试 |
| ADMIN-003 | Disable User | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 20ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 19ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 18ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-008 | Get Post List | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-009 | Search Posts | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 22ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 26ms | 系统繁忙，请稍后重试 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-014 | Get Comment List | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 16ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 15ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 15ms | 系统繁忙，请稍后重试 |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 15ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 15ms | Correctly rejected |

**Test Time**: 2026-01-20 14:01:50
**Test Result**: 6 passed, 14 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 55ms | 系统繁忙，请稍后重试 |
| ADMIN-002 | Search Users | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-003 | Disable User | [FAIL] | 29ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 17ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 20ms | Correctly rejected |
| ADMIN-007 | Get User Details | [FAIL] | 16ms | 系统繁忙，请稍后重试 |
| ADMIN-008 | Get Post List | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-009 | Search Posts | [FAIL] | 69ms | 系统繁忙，请稍后重试 |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 19ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-014 | Get Comment List | [FAIL] | 17ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 16ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 22ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [FAIL] | 20ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [FAIL] | 35ms | 系统繁忙，请稍后重试 |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 50ms | 系统繁忙，请稍后重试 |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 20ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 19ms | Correctly rejected |

**Test Time**: 2026-01-20 14:11:25
**Test Result**: 6 passed, 14 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 834ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 93ms | Search successful |
| ADMIN-003 | Disable User | [FAIL] | 26ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 18ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 16ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 51ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 73ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 234ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 62ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 19ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 66ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 171ms | æ¥è¯¢æç« åè¡¨å¤±è´¥ |
| ADMIN-014 | Get Comment List | [FAIL] | 43ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 24ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 17ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [FAIL] | 22ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [PASS] | 453ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 96ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 43ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 19ms | Correctly rejected |

**Test Time**: 2026-01-20 14:16:11
**Test Result**: 14 passed, 6 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 114ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 113ms | Search successful |
| ADMIN-003 | Disable User | [FAIL] | 19ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 15ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 14ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 48ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 57ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 76ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 58ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 18ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 69ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 81ms | æ¥è¯¢æç« åè¡¨å¤±è´¥ |
| ADMIN-014 | Get Comment List | [FAIL] | 20ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 42ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 17ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [FAIL] | 21ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [PASS] | 77ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 57ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 18ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 24ms | Correctly rejected |

**Test Time**: 2026-01-20 14:17:26
**Test Result**: 14 passed, 6 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 99ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 83ms | Search successful |
| ADMIN-003 | Disable User | [FAIL] | 12ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 13ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 13ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 44ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 56ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 70ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 54ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 12ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 54ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 67ms | æ¥è¯¢æç« åè¡¨å¤±è´¥ |
| ADMIN-014 | Get Comment List | [PASS] | 935ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 136ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 29ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [PASS] | 129ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 52ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 52ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 16ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 30ms | Correctly rejected |

**Test Time**: 2026-01-20 14:22:48
**Test Result**: 17 passed, 3 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 15:47:12
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 15:48:26
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 16:35:48
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 5555ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 173ms | Search successful |
| ADMIN-003 | Disable User | [FAIL] | 80ms | 系统繁忙，请稍后重试 |
| ADMIN-004 | Enable User | [FAIL] | 63ms | 系统繁忙，请稍后重试 |
| ADMIN-005 | Disable Non-existent | [PASS] | 18ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 49ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 86ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 946ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 162ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 17ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 68ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [PASS] | 66ms | Filter successful |
| ADMIN-014 | Get Comment List | [FAIL] | 91ms | 系统繁忙，请稍后重试 |
| ADMIN-015 | Search Comments | [FAIL] | 85ms | 系统繁忙，请稍后重试 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 38ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [FAIL] | 53ms | 系统繁忙，请稍后重试 |
| ADMIN-020 | Get Pending Reports | [PASS] | 1858ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 54ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 37ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 20ms | Correctly rejected |

**Test Time**: 2026-01-20 16:56:52
**Test Result**: 15 passed, 5 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [FAIL] | 4438ms | 无法连接到远程服务器 |
| ADMIN-002 | Search Users | [FAIL] | 4245ms | 无法连接到远程服务器 |
| ADMIN-003 | Disable User | [FAIL] | 4082ms | 无法连接到远程服务器 |
| ADMIN-004 | Enable User | [FAIL] | 4077ms | 无法连接到远程服务器 |
| ADMIN-005 | Disable Non-existent | [FAIL] | 4063ms | Should return 404 |
| ADMIN-006 | Non-admin Access | [FAIL] | 4087ms | Should return 403 |
| ADMIN-007 | Get User Details | [FAIL] | 4073ms | 无法连接到远程服务器 |
| ADMIN-008 | Get Post List | [FAIL] | 4078ms | 无法连接到远程服务器 |
| ADMIN-009 | Search Posts | [FAIL] | 4073ms | 无法连接到远程服务器 |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [FAIL] | 4080ms | Should return 404 |
| ADMIN-012 | Filter Posts by Author | [FAIL] | 4083ms | 无法连接到远程服务器 |
| ADMIN-013 | Filter Posts by Status | [FAIL] | 4083ms | 无法连接到远程服务器 |
| ADMIN-014 | Get Comment List | [FAIL] | 4082ms | 无法连接到远程服务器 |
| ADMIN-015 | Search Comments | [FAIL] | 4078ms | 无法连接到远程服务器 |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [FAIL] | 4071ms | Should return 404 |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [FAIL] | 4076ms | 无法连接到远程服务器 |
| ADMIN-020 | Get Pending Reports | [FAIL] | 4087ms | 无法连接到远程服务器 |
| ADMIN-021 | Filter Reports by Status | [FAIL] | 4070ms | 无法连接到远程服务器 |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [FAIL] | 4087ms | Should return 404 |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [FAIL] | 4080ms | Should return 400 |

**Test Time**: 2026-01-20 17:36:06
**Test Result**: 0 passed, 20 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 20:33:54
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 20:36:48
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 790ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 105ms | Search successful |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [PASS] | 23ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [PASS] | 557ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 90ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 17ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [PASS] | 97ms | Filter successful |
| ADMIN-014 | Get Comment List | [PASS] | 871ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 140ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 19ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [PASS] | 511ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 56ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 18ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 22ms | Correctly rejected |

**Test Time**: 2026-01-20 20:43:00
**Test Result**: 14 passed, 0 failed, 11 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 20:46:18
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 150ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 87ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 388ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 72ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 56ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 45ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 54ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 70ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 70ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 97ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 85ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [PASS] | 54ms | Filter successful |
| ADMIN-014 | Get Comment List | [PASS] | 115ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 94ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 97ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [PASS] | 67ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 56ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 50ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 18ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 14ms | Correctly rejected |

**Test Time**: 2026-01-20 20:53:26
**Test Result**: 20 passed, 0 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 21:22:54
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [SKIP] | - | No admin token |
| ADMIN-002 | Search Users | [SKIP] | - | No admin token |
| ADMIN-003 | Disable User | [SKIP] | - | Missing params |
| ADMIN-004 | Enable User | [SKIP] | - | Missing params |
| ADMIN-005 | Disable Non-existent | [SKIP] | - | No admin token |
| ADMIN-006 | Non-admin Access | [SKIP] | - | Missing params |
| ADMIN-007 | Get User Details | [SKIP] | - | Missing params |
| ADMIN-008 | Get Post List | [SKIP] | - | No admin token |
| ADMIN-009 | Search Posts | [SKIP] | - | No admin token |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [SKIP] | - | No admin token |
| ADMIN-012 | Filter Posts by Author | [SKIP] | - | Missing params |
| ADMIN-013 | Filter Posts by Status | [SKIP] | - | No admin token |
| ADMIN-014 | Get Comment List | [SKIP] | - | No admin token |
| ADMIN-015 | Search Comments | [SKIP] | - | No admin token |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [SKIP] | - | No admin token |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [SKIP] | - | Missing params |
| ADMIN-020 | Get Pending Reports | [SKIP] | - | No admin token |
| ADMIN-021 | Filter Reports by Status | [SKIP] | - | No admin token |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No admin token |
| ADMIN-023 | Handle Non-existent Report | [SKIP] | - | No admin token |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No admin token |
| ADMIN-025 | Invalid Report Action | [SKIP] | - | No admin token |

**Test Time**: 2026-01-20 21:34:00
**Test Result**: 0 passed, 0 failed, 25 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 817ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 108ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 296ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 109ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 63ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 43ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 56ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 238ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 60ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 80ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 64ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [PASS] | 59ms | Filter successful |
| ADMIN-014 | Get Comment List | [PASS] | 529ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 138ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 77ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [PASS] | 84ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 175ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 54ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 18ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 16ms | Correctly rejected |

**Test Time**: 2026-01-20 21:37:50
**Test Result**: 20 passed, 0 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 356ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 94ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 363ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 71ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 79ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 42ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 54ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 190ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 68ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 60ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 53ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [PASS] | 53ms | Filter successful |
| ADMIN-014 | Get Comment List | [PASS] | 123ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 94ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 56ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [PASS] | 76ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 166ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 48ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 22ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 13ms | Correctly rejected |

**Test Time**: 2026-01-20 21:42:34
**Test Result**: 20 passed, 0 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 271ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 240ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 375ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 64ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 54ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 43ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 53ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 85ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 52ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Missing params |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 56ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 50ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [PASS] | 54ms | Filter successful |
| ADMIN-014 | Get Comment List | [PASS] | 115ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 91ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Missing params |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 62ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Missing params |
| ADMIN-019 | Filter Comments by User | [PASS] | 82ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 163ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 51ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 10ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 8ms | Correctly rejected |

**Test Time**: 2026-01-20 21:47:24
**Test Result**: 20 passed, 0 failed, 5 skipped


## Admin Service Tests (Admin Service)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| ADMIN-001 | Get User List | [PASS] | 105ms | Users: 0 |
| ADMIN-002 | Search Users | [PASS] | 82ms | Search successful |
| ADMIN-003 | Disable User | [PASS] | 83ms | User disabled |
| ADMIN-004 | Enable User | [PASS] | 60ms | User enabled |
| ADMIN-005 | Disable Non-existent | [PASS] | 50ms | Correctly rejected |
| ADMIN-006 | Non-admin Access | [PASS] | 41ms | Correctly rejected |
| ADMIN-007 | Get User Details | [PASS] | 51ms | Details retrieved |
| ADMIN-008 | Get Post List | [PASS] | 64ms | Posts: 0 |
| ADMIN-009 | Search Posts | [PASS] | 67ms | Search successful |
| ADMIN-010 | Delete Post | [SKIP] | - | Could not create test post |
| ADMIN-011 | Delete Non-existent Post | [PASS] | 77ms | Correctly rejected |
| ADMIN-012 | Filter Posts by Author | [PASS] | 51ms | Filter successful |
| ADMIN-013 | Filter Posts by Status | [PASS] | 52ms | Filter successful |
| ADMIN-014 | Get Comment List | [PASS] | 91ms | Comments: 0 |
| ADMIN-015 | Search Comments | [PASS] | 86ms | Search successful |
| ADMIN-016 | Delete Comment | [SKIP] | - | Could not create test comment |
| ADMIN-017 | Delete Non-existent Comment | [PASS] | 53ms | Correctly rejected |
| ADMIN-018 | Filter Comments by Post | [SKIP] | - | Could not create test post |
| ADMIN-019 | Filter Comments by User | [PASS] | 85ms | Filter successful |
| ADMIN-020 | Get Pending Reports | [PASS] | 47ms | Reports: 0 |
| ADMIN-021 | Filter Reports by Status | [PASS] | 47ms | Filter successful |
| ADMIN-022 | Handle Report (Approve) | [SKIP] | - | No reports available |
| ADMIN-023 | Handle Non-existent Report | [PASS] | 8ms | Correctly rejected |
| ADMIN-024 | Handle Report (Reject) | [SKIP] | - | No reports available |
| ADMIN-025 | Invalid Report Action | [PASS] | 8ms | Correctly rejected |

**Test Time**: 2026-01-20 21:53:22
**Test Result**: 20 passed, 0 failed, 5 skipped










## Gateway Service Tests
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
| GW-001 | Route Forwarding | [PASS] | 777ms | Routed to user service |
| GW-002 | Non-existent Route | [PASS] | 52ms | Correctly returned 404 |
| GW-003 | Service Unavailable | [PASS] | 8ms | Handled unavailable service |
| GW-004 | Request Timeout | [SKIP] | - | Requires timeout configuration |
| GW-005 | Load Balancing | [PASS] | - | All requests successful |
| GW-006 | Valid Token | [PASS] | 51ms | Token accepted |
| GW-007 | Invalid Token | [PASS] | 11ms | Correctly rejected |
| GW-008 | Expired Token | [PASS] | 10ms | Correctly rejected |
| GW-009 | No Token Public Endpoint | [PASS] | 87ms | Public endpoint accessible |
| GW-010 | No Token Private Endpoint | [PASS] | 6ms | Correctly rejected |
| GW-011 | Normal Request Rate | [PASS] | - | All requests passed |
| GW-012 | Exceed Rate Limit | [SKIP] | - | Rate limiting not configured or threshold too high |
| GW-013 | Rate Limit Recovery | [PASS] | 69ms | Rate limit recovered |
| GW-014 | IP Rate Limiting | [SKIP] | - | Requires multiple IPs |
| GW-015 | User Rate Limiting | [SKIP] | - | User rate limiting not configured |

**Test Time**: 2026-01-21 12:58:33
**Test Result**: 11 passed, 0 failed, 4 skipped

