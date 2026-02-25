# Requirements Document

## Introduction

本文档定义了博客微服务系统的完整测试方案需求，包括API功能测试、集成测试、压力测试。测试覆盖所有微服务模块，确保系统功能正确性和性能达标。

**重要原则：所有模块的测试都需要全面覆盖，包括正常场景、异常场景、边界条件、错误处理等，参照用户服务测试的35个测试用例覆盖模式。**

## Glossary

- **API_Test**: API接口功能测试，验证接口响应正确性
- **Integration_Test**: 集成测试，验证服务间交互正确性
- **Load_Test**: 压力测试，验证系统在高并发下的性能表现
- **Test_Suite**: 测试套件，按模块组织的测试集合
- **Test_Report**: 测试报告，记录测试执行结果和状态
- **Error_Scenario**: 错误场景测试，验证系统对异常输入的处理
- **Edge_Case**: 边界条件测试，验证系统对边界值的处理

## Requirements

### Requirement 1: 用户服务全面测试 (ZhiCore-user) - 35个测试用例

**User Story:** As a 测试人员, I want 全面测试用户服务所有接口的正常和异常场景, so that 确保用户服务功能完整且健壮。

#### Acceptance Criteria - 注册测试 (7个)

1.1. WHEN 测试正常注册时, THE Test_Suite SHALL 验证 POST /api/v1/auth/register 返回用户ID
1.2. WHEN 测试重复邮箱注册时, THE Test_Suite SHALL 验证返回400错误
1.3. WHEN 测试无效邮箱格式时, THE Test_Suite SHALL 验证返回400错误
1.4. WHEN 测试过短用户名时, THE Test_Suite SHALL 验证返回400错误
1.5. WHEN 测试无效用户名字符时, THE Test_Suite SHALL 验证返回400错误
1.6. WHEN 测试过短密码时, THE Test_Suite SHALL 验证返回400错误
1.7. WHEN 测试空字段注册时, THE Test_Suite SHALL 验证返回400错误

#### Acceptance Criteria - 登录测试 (4个)

1.8. WHEN 测试正常登录时, THE Test_Suite SHALL 验证返回JWT Token
1.9. WHEN 测试错误密码登录时, THE Test_Suite SHALL 验证返回401错误
1.10. WHEN 测试不存在邮箱登录时, THE Test_Suite SHALL 验证返回401错误
1.11. WHEN 测试空字段登录时, THE Test_Suite SHALL 验证返回400错误

#### Acceptance Criteria - Token测试 (3个)

1.12. WHEN 测试Token刷新时, THE Test_Suite SHALL 验证返回新Token
1.13. WHEN 测试无效RefreshToken时, THE Test_Suite SHALL 验证返回401错误
1.14. WHEN 测试空RefreshToken时, THE Test_Suite SHALL 验证返回400错误

#### Acceptance Criteria - 用户信息测试 (3个)

1.15. WHEN 测试获取用户信息时, THE Test_Suite SHALL 验证返回用户详情
1.16. WHEN 测试获取不存在用户时, THE Test_Suite SHALL 验证返回404错误
1.17. WHEN 测试无认证获取用户时, THE Test_Suite SHALL 验证返回公开信息或401

#### Acceptance Criteria - 关注测试 (10个)

1.18. WHEN 测试关注用户时, THE Test_Suite SHALL 验证关注成功
1.19. WHEN 测试关注自己时, THE Test_Suite SHALL 验证返回400错误
1.20. WHEN 测试关注不存在用户时, THE Test_Suite SHALL 验证返回404错误
1.21. WHEN 测试重复关注时, THE Test_Suite SHALL 验证优雅处理
1.22. WHEN 测试取消关注时, THE Test_Suite SHALL 验证取消成功
1.23. WHEN 测试取消未关注用户时, THE Test_Suite SHALL 验证优雅处理
1.24. WHEN 测试获取粉丝列表时, THE Test_Suite SHALL 验证返回分页数据
1.25. WHEN 测试获取关注列表时, THE Test_Suite SHALL 验证返回分页数据
1.26. WHEN 测试检查关注状态时, THE Test_Suite SHALL 验证返回布尔值
1.27. WHEN 测试获取关注统计时, THE Test_Suite SHALL 验证返回统计数据

#### Acceptance Criteria - 签到测试 (6个)

1.28. WHEN 测试签到时, THE Test_Suite SHALL 验证签到成功
1.29. WHEN 测试重复签到时, THE Test_Suite SHALL 验证优雅处理
1.30. WHEN 测试获取签到统计时, THE Test_Suite SHALL 验证返回统计数据
1.31. WHEN 测试不存在用户签到时, THE Test_Suite SHALL 验证返回错误
1.32. WHEN 测试获取月度签到时, THE Test_Suite SHALL 验证返回位图数据
1.33. WHEN 测试无效月份参数时, THE Test_Suite SHALL 验证返回400错误

#### Acceptance Criteria - 分页测试 (2个)

1.34. WHEN 测试无效页码时, THE Test_Suite SHALL 验证优雅处理
1.35. WHEN 测试超大页面大小时, THE Test_Suite SHALL 验证优雅处理

### Requirement 2: 文章服务全面测试 (ZhiCore-post) - 41个测试用例

**User Story:** As a 测试人员, I want 全面测试文章服务所有接口的正常和异常场景, so that 确保文章服务功能完整且健壮。

#### Acceptance Criteria - 文章CRUD测试 (12个)

2.1. WHEN 测试创建文章时, THE Test_Suite SHALL 验证返回文章ID
2.2. WHEN 测试创建空标题文章时, THE Test_Suite SHALL 验证返回400错误
2.3. WHEN 测试创建空内容文章时, THE Test_Suite SHALL 验证返回400错误
2.4. WHEN 测试创建超长标题文章时, THE Test_Suite SHALL 验证返回400错误
2.5. WHEN 测试获取文章详情时, THE Test_Suite SHALL 验证返回文章详情
2.6. WHEN 测试获取不存在文章时, THE Test_Suite SHALL 验证返回404错误
2.7. WHEN 测试更新文章时, THE Test_Suite SHALL 验证更新成功
2.8. WHEN 测试更新他人文章时, THE Test_Suite SHALL 验证返回403错误
2.9. WHEN 测试删除文章时, THE Test_Suite SHALL 验证软删除成功
2.10. WHEN 测试删除他人文章时, THE Test_Suite SHALL 验证返回403错误
2.11. WHEN 测试删除不存在文章时, THE Test_Suite SHALL 验证返回404错误
2.12. WHEN 测试无认证创建文章时, THE Test_Suite SHALL 验证返回401错误

#### Acceptance Criteria - 文章发布测试 (5个)

2.13. WHEN 测试发布草稿文章时, THE Test_Suite SHALL 验证发布成功
2.14. WHEN 测试发布已发布文章时, THE Test_Suite SHALL 验证优雅处理
2.15. WHEN 测试发布他人文章时, THE Test_Suite SHALL 验证返回403错误
2.16. WHEN 测试发布不存在文章时, THE Test_Suite SHALL 验证返回404错误
2.17. WHEN 测试撤回已发布文章时, THE Test_Suite SHALL 验证撤回成功

#### Acceptance Criteria - 文章列表测试 (6个)

2.18. WHEN 测试获取文章列表时, THE Test_Suite SHALL 验证返回分页数据
2.19. WHEN 测试按分类筛选文章时, THE Test_Suite SHALL 验证返回正确数据
2.20. WHEN 测试按标签筛选文章时, THE Test_Suite SHALL 验证返回正确数据
2.21. WHEN 测试获取用户文章列表时, THE Test_Suite SHALL 验证返回用户文章
2.22. WHEN 测试无效分页参数时, THE Test_Suite SHALL 验证优雅处理
2.23. WHEN 测试获取草稿列表时, THE Test_Suite SHALL 验证返回草稿数据

#### Acceptance Criteria - 点赞测试 (6个)

2.24. WHEN 测试点赞文章时, THE Test_Suite SHALL 验证点赞成功
2.25. WHEN 测试重复点赞时, THE Test_Suite SHALL 验证优雅处理
2.26. WHEN 测试取消点赞时, THE Test_Suite SHALL 验证取消成功
2.27. WHEN 测试取消未点赞时, THE Test_Suite SHALL 验证优雅处理
2.28. WHEN 测试点赞不存在文章时, THE Test_Suite SHALL 验证返回404错误
2.29. WHEN 测试检查点赞状态时, THE Test_Suite SHALL 验证返回布尔值

#### Acceptance Criteria - 收藏测试 (6个)

2.30. WHEN 测试收藏文章时, THE Test_Suite SHALL 验证收藏成功
2.31. WHEN 测试重复收藏时, THE Test_Suite SHALL 验证优雅处理
2.32. WHEN 测试取消收藏时, THE Test_Suite SHALL 验证取消成功
2.33. WHEN 测试取消未收藏时, THE Test_Suite SHALL 验证优雅处理
2.34. WHEN 测试收藏不存在文章时, THE Test_Suite SHALL 验证返回404错误
2.35. WHEN 测试获取收藏列表时, THE Test_Suite SHALL 验证返回分页数据

#### Acceptance Criteria - 安全测试 (6个)

2.36. WHEN 测试XSS注入文章标题时, THE Test_Suite SHALL 验证脚本标签被正确处理或转义
2.37. WHEN 测试XSS注入文章内容时, THE Test_Suite SHALL 验证脚本标签被正确处理或转义
2.38. WHEN 测试SQL注入文章ID时, THE Test_Suite SHALL 验证SQL注入被正确拒绝
2.39. WHEN 测试HTML标签注入文章内容时, THE Test_Suite SHALL 验证HTML标签被正确处理
2.40. WHEN 测试特殊字符文章标题时, THE Test_Suite SHALL 验证特殊字符被正确处理
2.41. WHEN 测试XSS注入文章ID参数时, THE Test_Suite SHALL 验证脚本标签被正确拒绝

### Requirement 3: 评论服务全面测试 (ZhiCore-comment) - 36个测试用例

**User Story:** As a 测试人员, I want 全面测试评论服务所有接口的正常和异常场景, so that 确保评论服务功能完整且健壮。

#### Acceptance Criteria - 评论CRUD测试 (10个)

3.1. WHEN 测试创建评论时, THE Test_Suite SHALL 验证返回评论ID
3.2. WHEN 测试创建空内容评论时, THE Test_Suite SHALL 验证返回400错误
3.3. WHEN 测试创建超长评论时, THE Test_Suite SHALL 验证返回400错误
3.4. WHEN 测试评论不存在文章时, THE Test_Suite SHALL 验证返回404错误
3.5. WHEN 测试获取评论详情时, THE Test_Suite SHALL 验证返回评论详情
3.6. WHEN 测试获取不存在评论时, THE Test_Suite SHALL 验证返回404错误
3.7. WHEN 测试删除评论时, THE Test_Suite SHALL 验证软删除成功
3.8. WHEN 测试删除他人评论时, THE Test_Suite SHALL 验证返回403错误
3.9. WHEN 测试删除不存在评论时, THE Test_Suite SHALL 验证返回404错误
3.10. WHEN 测试无认证创建评论时, THE Test_Suite SHALL 验证返回401错误

#### Acceptance Criteria - 回复评论测试 (5个)

3.11. WHEN 测试回复评论时, THE Test_Suite SHALL 验证回复成功
3.12. WHEN 测试回复不存在评论时, THE Test_Suite SHALL 验证返回404错误
3.13. WHEN 测试获取子评论列表时, THE Test_Suite SHALL 验证返回分页数据
3.14. WHEN 测试多级回复时, THE Test_Suite SHALL 验证正确处理层级
3.15. WHEN 测试回复已删除评论时, THE Test_Suite SHALL 验证返回错误

#### Acceptance Criteria - 评论列表测试 (6个)

3.16. WHEN 测试获取文章评论列表时, THE Test_Suite SHALL 验证返回分页数据
3.17. WHEN 测试按热度排序评论时, THE Test_Suite SHALL 验证返回热度排序数据
3.18. WHEN 测试按时间排序评论时, THE Test_Suite SHALL 验证返回时间排序数据
3.19. WHEN 测试游标分页时, THE Test_Suite SHALL 验证游标正确工作
3.20. WHEN 测试无效游标时, THE Test_Suite SHALL 验证优雅处理
3.21. WHEN 测试获取不存在文章评论时, THE Test_Suite SHALL 验证返回空列表或404

#### Acceptance Criteria - 评论点赞测试 (6个)

3.22. WHEN 测试点赞评论时, THE Test_Suite SHALL 验证点赞成功
3.23. WHEN 测试重复点赞评论时, THE Test_Suite SHALL 验证优雅处理
3.24. WHEN 测试取消评论点赞时, THE Test_Suite SHALL 验证取消成功
3.25. WHEN 测试取消未点赞评论时, THE Test_Suite SHALL 验证优雅处理
3.26. WHEN 测试点赞不存在评论时, THE Test_Suite SHALL 验证返回404错误
3.27. WHEN 测试检查评论点赞状态时, THE Test_Suite SHALL 验证返回布尔值

#### Acceptance Criteria - 评论统计测试 (3个)

3.28. WHEN 测试获取评论统计时, THE Test_Suite SHALL 验证返回统计数据
3.29. WHEN 测试获取文章评论数时, THE Test_Suite SHALL 验证返回正确数量
3.30. WHEN 测试获取用户评论列表时, THE Test_Suite SHALL 验证返回用户评论

#### Acceptance Criteria - 安全测试 (6个)

3.31. WHEN 测试XSS注入评论内容时, THE Test_Suite SHALL 验证脚本标签被正确处理或转义
3.32. WHEN 测试SQL注入评论ID时, THE Test_Suite SHALL 验证SQL注入被正确拒绝
3.33. WHEN 测试HTML标签注入评论内容时, THE Test_Suite SHALL 验证HTML标签被正确处理
3.34. WHEN 测试特殊字符评论内容时, THE Test_Suite SHALL 验证特殊字符被正确处理
3.35. WHEN 测试XSS注入评论ID参数时, THE Test_Suite SHALL 验证脚本标签被正确拒绝
3.36. WHEN 测试SQL注入文章ID参数时, THE Test_Suite SHALL 验证SQL注入被正确拒绝

### Requirement 4: 消息服务全面测试 (ZhiCore-message) - 20个测试用例

**User Story:** As a 测试人员, I want 全面测试消息服务所有接口的正常和异常场景, so that 确保私信功能完整且健壮。

#### Acceptance Criteria - 发送消息测试 (6个)

4.1. WHEN 测试发送消息时, THE Test_Suite SHALL 验证发送成功
4.2. WHEN 测试发送空消息时, THE Test_Suite SHALL 验证返回400错误
4.3. WHEN 测试发送超长消息时, THE Test_Suite SHALL 验证返回400错误
4.4. WHEN 测试发送给不存在用户时, THE Test_Suite SHALL 验证返回404错误
4.5. WHEN 测试发送给自己时, THE Test_Suite SHALL 验证返回400错误
4.6. WHEN 测试无认证发送消息时, THE Test_Suite SHALL 验证返回401错误

#### Acceptance Criteria - 消息历史测试 (5个)

4.7. WHEN 测试获取消息历史时, THE Test_Suite SHALL 验证返回消息列表
4.8. WHEN 测试获取不存在会话消息时, THE Test_Suite SHALL 验证返回空列表或404
4.9. WHEN 测试消息分页时, THE Test_Suite SHALL 验证分页正确工作
4.10. WHEN 测试获取他人会话消息时, THE Test_Suite SHALL 验证返回403错误
4.11. WHEN 测试无效分页参数时, THE Test_Suite SHALL 验证优雅处理

#### Acceptance Criteria - 会话管理测试 (5个)

4.12. WHEN 测试获取会话列表时, THE Test_Suite SHALL 验证返回会话列表
4.13. WHEN 测试会话排序时, THE Test_Suite SHALL 验证按最新消息排序
4.14. WHEN 测试删除会话时, THE Test_Suite SHALL 验证删除成功
4.15. WHEN 测试删除不存在会话时, THE Test_Suite SHALL 验证返回404错误
4.16. WHEN 测试获取会话详情时, THE Test_Suite SHALL 验证返回会话信息

#### Acceptance Criteria - 消息状态测试 (4个)

4.17. WHEN 测试标记消息已读时, THE Test_Suite SHALL 验证标记成功
4.18. WHEN 测试标记不存在消息时, THE Test_Suite SHALL 验证返回404错误
4.19. WHEN 测试获取未读消息数时, THE Test_Suite SHALL 验证返回正确数量
4.20. WHEN 测试批量标记已读时, THE Test_Suite SHALL 验证批量标记成功

### Requirement 5: 通知服务全面测试 (ZhiCore-notification) - 15个测试用例

**User Story:** As a 测试人员, I want 全面测试通知服务所有接口的正常和异常场景, so that 确保通知功能完整且健壮。

#### Acceptance Criteria - 通知列表测试 (5个)

5.1. WHEN 测试获取通知列表时, THE Test_Suite SHALL 验证返回通知列表
5.2. WHEN 测试按类型筛选通知时, THE Test_Suite SHALL 验证返回正确类型通知
5.3. WHEN 测试通知分页时, THE Test_Suite SHALL 验证分页正确工作
5.4. WHEN 测试无效分页参数时, THE Test_Suite SHALL 验证优雅处理
5.5. WHEN 测试无认证获取通知时, THE Test_Suite SHALL 验证返回401错误

#### Acceptance Criteria - 通知已读测试 (6个)

5.6. WHEN 测试标记通知已读时, THE Test_Suite SHALL 验证标记成功
5.7. WHEN 测试标记不存在通知时, THE Test_Suite SHALL 验证返回404错误
5.8. WHEN 测试标记他人通知时, THE Test_Suite SHALL 验证返回403错误
5.9. WHEN 测试批量标记已读时, THE Test_Suite SHALL 验证批量标记成功
5.10. WHEN 测试全部标记已读时, THE Test_Suite SHALL 验证全部标记成功
5.11. WHEN 测试重复标记已读时, THE Test_Suite SHALL 验证优雅处理

#### Acceptance Criteria - 通知统计测试 (4个)

5.12. WHEN 测试获取未读数量时, THE Test_Suite SHALL 验证返回正确数量
5.13. WHEN 测试按类型获取未读数时, THE Test_Suite SHALL 验证返回分类数量
5.14. WHEN 测试删除通知时, THE Test_Suite SHALL 验证删除成功
5.15. WHEN 测试删除不存在通知时, THE Test_Suite SHALL 验证返回404错误

### Requirement 6: 搜索服务全面测试 (ZhiCore-search) - 12个测试用例

**User Story:** As a 测试人员, I want 全面测试搜索服务所有接口的正常和异常场景, so that 确保搜索功能完整且健壮。

#### Acceptance Criteria - 搜索测试 (8个)

6.1. WHEN 测试关键词搜索时, THE Test_Suite SHALL 验证返回搜索结果
6.2. WHEN 测试空关键词搜索时, THE Test_Suite SHALL 验证返回400错误或空结果
6.3. WHEN 测试特殊字符搜索时, THE Test_Suite SHALL 验证优雅处理
6.4. WHEN 测试超长关键词搜索时, THE Test_Suite SHALL 验证优雅处理
6.5. WHEN 测试搜索结果分页时, THE Test_Suite SHALL 验证分页正确工作
6.6. WHEN 测试搜索结果排序时, THE Test_Suite SHALL 验证排序正确
6.7. WHEN 测试无结果搜索时, THE Test_Suite SHALL 验证返回空列表
6.8. WHEN 测试高亮显示时, THE Test_Suite SHALL 验证关键词高亮

#### Acceptance Criteria - 搜索建议测试 (4个)

6.9. WHEN 测试搜索建议时, THE Test_Suite SHALL 验证返回建议列表
6.10. WHEN 测试空前缀建议时, THE Test_Suite SHALL 验证返回热门搜索
6.11. WHEN 测试建议数量限制时, THE Test_Suite SHALL 验证返回限制数量
6.12. WHEN 测试特殊字符建议时, THE Test_Suite SHALL 验证优雅处理

### Requirement 7: 排行榜服务全面测试 (ZhiCore-ranking) - 12个测试用例

**User Story:** As a 测试人员, I want 全面测试排行榜服务所有接口的正常和异常场景, so that 确保排行榜功能完整且健壮。

#### Acceptance Criteria - 热门文章排行测试 (4个)

7.1. WHEN 测试获取热门文章时, THE Test_Suite SHALL 验证返回排行数据
7.2. WHEN 测试热门文章分页时, THE Test_Suite SHALL 验证分页正确工作
7.3. WHEN 测试按时间范围筛选时, THE Test_Suite SHALL 验证返回正确范围数据
7.4. WHEN 测试无效时间范围时, THE Test_Suite SHALL 验证优雅处理

#### Acceptance Criteria - 创作者排行测试 (4个)

7.5. WHEN 测试获取创作者排行时, THE Test_Suite SHALL 验证返回排行数据
7.6. WHEN 测试创作者排行分页时, THE Test_Suite SHALL 验证分页正确工作
7.7. WHEN 测试按指标排序时, THE Test_Suite SHALL 验证排序正确
7.8. WHEN 测试无效排序参数时, THE Test_Suite SHALL 验证优雅处理

#### Acceptance Criteria - 热门话题排行测试 (4个)

7.9. WHEN 测试获取热门话题时, THE Test_Suite SHALL 验证返回排行数据
7.10. WHEN 测试热门话题分页时, THE Test_Suite SHALL 验证分页正确工作
7.11. WHEN 测试话题趋势数据时, THE Test_Suite SHALL 验证返回趋势信息
7.12. WHEN 测试无效分页参数时, THE Test_Suite SHALL 验证优雅处理

### Requirement 8: 上传服务全面测试 (ZhiCore-upload) - 15个测试用例

**User Story:** As a 测试人员, I want 全面测试上传服务所有接口的正常和异常场景, so that 确保文件上传功能完整且健壮。

#### Acceptance Criteria - 图片上传测试 (8个)

8.1. WHEN 测试上传图片时, THE Test_Suite SHALL 验证上传成功并返回URL
8.2. WHEN 测试上传无效格式时, THE Test_Suite SHALL 验证返回400错误
8.3. WHEN 测试上传超大图片时, THE Test_Suite SHALL 验证返回400错误
8.4. WHEN 测试上传空文件时, THE Test_Suite SHALL 验证返回400错误
8.5. WHEN 测试无认证上传时, THE Test_Suite SHALL 验证返回401错误
8.6. WHEN 测试图片压缩时, THE Test_Suite SHALL 验证返回压缩后URL
8.7. WHEN 测试图片缩略图时, THE Test_Suite SHALL 验证返回缩略图URL
8.8. WHEN 测试批量上传图片时, THE Test_Suite SHALL 验证批量上传成功

#### Acceptance Criteria - 文件上传测试 (7个)

8.9. WHEN 测试上传文件时, THE Test_Suite SHALL 验证上传成功并返回URL
8.10. WHEN 测试上传禁止类型时, THE Test_Suite SHALL 验证返回400错误
8.11. WHEN 测试上传超大文件时, THE Test_Suite SHALL 验证返回400错误
8.12. WHEN 测试文件名特殊字符时, THE Test_Suite SHALL 验证优雅处理
8.13. WHEN 测试获取上传进度时, THE Test_Suite SHALL 验证返回进度信息
8.14. WHEN 测试取消上传时, THE Test_Suite SHALL 验证取消成功
8.15. WHEN 测试断点续传时, THE Test_Suite SHALL 验证续传成功

### Requirement 9: 管理后台服务全面测试 (ZhiCore-admin) - 25个测试用例

**User Story:** As a 测试人员, I want 全面测试管理后台服务所有接口的正常和异常场景, so that 确保后台管理功能完整且健壮。

#### Acceptance Criteria - 用户管理测试 (7个)

9.1. WHEN 测试获取用户列表时, THE Test_Suite SHALL 验证返回用户列表
9.2. WHEN 测试用户搜索时, THE Test_Suite SHALL 验证返回搜索结果
9.3. WHEN 测试禁用用户时, THE Test_Suite SHALL 验证禁用成功
9.4. WHEN 测试启用用户时, THE Test_Suite SHALL 验证启用成功
9.5. WHEN 测试禁用不存在用户时, THE Test_Suite SHALL 验证返回404错误
9.6. WHEN 测试无管理员权限时, THE Test_Suite SHALL 验证返回403错误
9.7. WHEN 测试获取用户详情时, THE Test_Suite SHALL 验证返回用户详情

#### Acceptance Criteria - 文章管理测试 (6个)

9.8. WHEN 测试获取文章列表时, THE Test_Suite SHALL 验证返回文章列表
9.9. WHEN 测试文章搜索时, THE Test_Suite SHALL 验证返回搜索结果
9.10. WHEN 测试删除文章时, THE Test_Suite SHALL 验证删除成功
9.11. WHEN 测试删除不存在文章时, THE Test_Suite SHALL 验证返回404错误
9.12. WHEN 测试恢复已删除文章时, THE Test_Suite SHALL 验证恢复成功
9.13. WHEN 测试批量删除文章时, THE Test_Suite SHALL 验证批量删除成功

#### Acceptance Criteria - 评论管理测试 (6个)

9.14. WHEN 测试获取评论列表时, THE Test_Suite SHALL 验证返回评论列表
9.15. WHEN 测试评论搜索时, THE Test_Suite SHALL 验证返回搜索结果
9.16. WHEN 测试删除评论时, THE Test_Suite SHALL 验证删除成功
9.17. WHEN 测试删除不存在评论时, THE Test_Suite SHALL 验证返回404错误
9.18. WHEN 测试恢复已删除评论时, THE Test_Suite SHALL 验证恢复成功
9.19. WHEN 测试批量删除评论时, THE Test_Suite SHALL 验证批量删除成功

#### Acceptance Criteria - 举报管理测试 (6个)

9.20. WHEN 测试获取举报列表时, THE Test_Suite SHALL 验证返回举报列表
9.21. WHEN 测试按状态筛选举报时, THE Test_Suite SHALL 验证返回正确状态举报
9.22. WHEN 测试处理举报时, THE Test_Suite SHALL 验证处理成功
9.23. WHEN 测试处理不存在举报时, THE Test_Suite SHALL 验证返回404错误
9.24. WHEN 测试驳回举报时, THE Test_Suite SHALL 验证驳回成功
9.25. WHEN 测试批量处理举报时, THE Test_Suite SHALL 验证批量处理成功

### Requirement 10: 网关服务全面测试 (ZhiCore-gateway) - 15个测试用例

**User Story:** As a 测试人员, I want 全面测试网关服务所有功能的正常和异常场景, so that 确保网关功能完整且健壮。

#### Acceptance Criteria - 路由测试 (5个)

10.1. WHEN 测试路由转发时, THE Test_Suite SHALL 验证请求正确路由到目标服务
10.2. WHEN 测试不存在路由时, THE Test_Suite SHALL 验证返回404错误
10.3. WHEN 测试服务不可用时, THE Test_Suite SHALL 验证返回503错误
10.4. WHEN 测试请求超时时, THE Test_Suite SHALL 验证返回504错误
10.5. WHEN 测试负载均衡时, THE Test_Suite SHALL 验证请求分发到多个实例

#### Acceptance Criteria - 认证测试 (5个)

10.6. WHEN 测试有效Token时, THE Test_Suite SHALL 验证请求正常通过
10.7. WHEN 测试无效Token时, THE Test_Suite SHALL 验证返回401错误
10.8. WHEN 测试过期Token时, THE Test_Suite SHALL 验证返回401错误
10.9. WHEN 测试无Token访问公开接口时, THE Test_Suite SHALL 验证请求正常通过
10.10. WHEN 测试无Token访问私有接口时, THE Test_Suite SHALL 验证返回401错误

#### Acceptance Criteria - 限流测试 (5个)

10.11. WHEN 测试正常请求频率时, THE Test_Suite SHALL 验证请求正常通过
10.12. WHEN 测试超过限流阈值时, THE Test_Suite SHALL 验证返回429错误
10.13. WHEN 测试限流恢复时, THE Test_Suite SHALL 验证限流解除后请求正常
10.14. WHEN 测试IP限流时, THE Test_Suite SHALL 验证IP级别限流生效
10.15. WHEN 测试用户限流时, THE Test_Suite SHALL 验证用户级别限流生效

### Requirement 11: 压力测试

**User Story:** As a 测试人员, I want 对核心接口进行压力测试, so that 确保系统在高并发下性能达标。

#### Acceptance Criteria

11.1. WHEN 压测文章详情接口时, THE Load_Test SHALL 验证P99响应时间小于100ms，QPS大于1000
11.2. WHEN 压测文章列表接口时, THE Load_Test SHALL 验证P99响应时间小于200ms，QPS大于500
11.3. WHEN 压测点赞接口时, THE Load_Test SHALL 验证P99响应时间小于100ms，QPS大于2000
11.4. WHEN 压测搜索接口时, THE Load_Test SHALL 验证P99响应时间小于300ms，QPS大于200
11.5. WHEN 压测通知列表接口时, THE Load_Test SHALL 验证P99响应时间小于200ms，QPS大于500
11.6. WHEN 压测评论列表接口时, THE Load_Test SHALL 验证P99响应时间小于150ms，QPS大于800
11.7. WHEN 压测创建评论接口时, THE Load_Test SHALL 验证P99响应时间小于200ms，QPS大于500
11.8. WHEN 压测评论点赞接口时, THE Load_Test SHALL 验证P99响应时间小于100ms，QPS大于1500

### Requirement 12: 测试报告与状态追踪

**User Story:** As a 测试人员, I want 测试结果有清晰的报告和状态追踪, so that 可以了解测试进度和结果。

#### Acceptance Criteria

12.1. WHEN 测试完成时, THE Test_Report SHALL 记录测试用例执行状态（通过/失败/跳过）
12.2. WHEN 测试完成时, THE Test_Report SHALL 记录响应时间和错误信息
12.3. WHEN 测试完成时, THE Test_Report SHALL 生成汇总统计（通过率、平均响应时间）

### Requirement 13: 测试驱动开发与错误修复

**User Story:** As a 开发人员, I want 测试脚本创建后立即执行测试并修复发现的错误, so that 确保代码质量和功能正确性。

#### Acceptance Criteria

13.1. WHEN 测试脚本创建完成时, THE Test_Suite SHALL 立即启动相关服务并执行测试
13.2. WHEN 测试发现错误时, THE Developer SHALL 分析错误原因并修复代码
13.3. WHEN 代码修复后, THE Test_Suite SHALL 重新执行测试验证修复效果
13.4. WHEN 所有测试通过时, THE Test_Suite SHALL 更新测试状态为通过

### Requirement 14: 全面边界测试 (Boundary Tests) - 50个测试用例

**User Story:** As a 测试人员, I want 对所有服务进行全面的边界条件测试, so that 确保系统在边界值情况下行为正确。

#### Acceptance Criteria - 数值边界测试 (15个)

14.1. WHEN 测试页码为0时, THE Test_Suite SHALL 验证返回第一页数据或400错误
14.2. WHEN 测试页码为负数时, THE Test_Suite SHALL 验证返回400错误
14.3. WHEN 测试页码为最大整数时, THE Test_Suite SHALL 验证返回空列表或400错误
14.4. WHEN 测试页面大小为0时, THE Test_Suite SHALL 验证返回400错误或使用默认值
14.5. WHEN 测试页面大小为负数时, THE Test_Suite SHALL 验证返回400错误
14.6. WHEN 测试页面大小为1000时, THE Test_Suite SHALL 验证返回限制后的数据或400错误
14.7. WHEN 测试ID为0时, THE Test_Suite SHALL 验证返回404错误
14.8. WHEN 测试ID为负数时, THE Test_Suite SHALL 验证返回400或404错误
14.9. WHEN 测试ID为最大Long值时, THE Test_Suite SHALL 验证返回404错误
14.10. WHEN 测试数量参数为0时, THE Test_Suite SHALL 验证返回空结果或400错误
14.11. WHEN 测试数量参数为负数时, THE Test_Suite SHALL 验证返回400错误
14.12. WHEN 测试偏移量为负数时, THE Test_Suite SHALL 验证返回400错误
14.13. WHEN 测试限制数为超大值时, THE Test_Suite SHALL 验证返回限制后的数据
14.14. WHEN 测试时间戳为0时, THE Test_Suite SHALL 验证返回400错误或正确处理
14.15. WHEN 测试时间戳为未来时间时, THE Test_Suite SHALL 验证返回400错误或正确处理

#### Acceptance Criteria - 字符串边界测试 (15个)

14.16. WHEN 测试空字符串输入时, THE Test_Suite SHALL 验证返回400错误
14.17. WHEN 测试纯空格字符串时, THE Test_Suite SHALL 验证返回400错误或正确处理
14.18. WHEN 测试单字符输入时, THE Test_Suite SHALL 验证正确处理或返回验证错误
14.19. WHEN 测试最大长度字符串时, THE Test_Suite SHALL 验证正确存储或返回400错误
14.20. WHEN 测试超过最大长度字符串时, THE Test_Suite SHALL 验证返回400错误
14.21. WHEN 测试包含换行符的字符串时, THE Test_Suite SHALL 验证正确处理
14.22. WHEN 测试包含制表符的字符串时, THE Test_Suite SHALL 验证正确处理
14.23. WHEN 测试包含Unicode字符的字符串时, THE Test_Suite SHALL 验证正确存储和显示
14.24. WHEN 测试包含Emoji的字符串时, THE Test_Suite SHALL 验证正确存储和显示
14.25. WHEN 测试包含中文字符的字符串时, THE Test_Suite SHALL 验证正确存储和显示
14.26. WHEN 测试包含日文字符的字符串时, THE Test_Suite SHALL 验证正确存储和显示
14.27. WHEN 测试包含韩文字符的字符串时, THE Test_Suite SHALL 验证正确存储和显示
14.28. WHEN 测试包含阿拉伯文的字符串时, THE Test_Suite SHALL 验证正确存储和显示
14.29. WHEN 测试包含零宽字符的字符串时, THE Test_Suite SHALL 验证正确处理
14.30. WHEN 测试包含控制字符的字符串时, THE Test_Suite SHALL 验证正确过滤或拒绝

#### Acceptance Criteria - 集合边界测试 (10个)

14.31. WHEN 测试空数组参数时, THE Test_Suite SHALL 验证返回400错误或空结果
14.32. WHEN 测试单元素数组时, THE Test_Suite SHALL 验证正确处理
14.33. WHEN 测试超大数组参数时, THE Test_Suite SHALL 验证返回400错误或限制处理
14.34. WHEN 测试重复元素数组时, THE Test_Suite SHALL 验证去重或正确处理
14.35. WHEN 测试包含null元素的数组时, THE Test_Suite SHALL 验证过滤或返回400错误
14.36. WHEN 测试批量操作空列表时, THE Test_Suite SHALL 验证返回400错误或空结果
14.37. WHEN 测试批量操作单个元素时, THE Test_Suite SHALL 验证正确处理
14.38. WHEN 测试批量操作超过限制数量时, THE Test_Suite SHALL 验证返回400错误或部分处理
14.39. WHEN 测试批量查询不存在的ID时, THE Test_Suite SHALL 验证返回空结果或部分结果
14.40. WHEN 测试批量删除混合存在和不存在的ID时, THE Test_Suite SHALL 验证正确处理存在的项

#### Acceptance Criteria - 时间边界测试 (10个)

14.41. WHEN 测试开始时间大于结束时间时, THE Test_Suite SHALL 验证返回400错误
14.42. WHEN 测试开始时间等于结束时间时, THE Test_Suite SHALL 验证返回空结果或正确处理
14.43. WHEN 测试时间范围跨越多年时, THE Test_Suite SHALL 验证正确处理
14.44. WHEN 测试时间范围为1毫秒时, THE Test_Suite SHALL 验证正确处理
14.45. WHEN 测试无效日期格式时, THE Test_Suite SHALL 验证返回400错误
14.46. WHEN 测试闰年日期时, THE Test_Suite SHALL 验证正确处理
14.47. WHEN 测试时区边界时, THE Test_Suite SHALL 验证正确处理
14.48. WHEN 测试夏令时切换时间时, THE Test_Suite SHALL 验证正确处理
14.49. WHEN 测试Unix时间戳最大值时, THE Test_Suite SHALL 验证正确处理或返回错误
14.50. WHEN 测试ISO8601格式时间时, THE Test_Suite SHALL 验证正确解析

### Requirement 15: 全面安全注入测试 (Security Injection Tests) - 60个测试用例

**User Story:** As a 安全测试人员, I want 对所有服务进行全面的安全注入测试, so that 确保系统能够防御各种注入攻击。

#### Acceptance Criteria - XSS注入测试 (15个)

15.1. WHEN 测试基本script标签注入时, THE Test_Suite SHALL 验证脚本被转义或过滤
15.2. WHEN 测试大小写混合script标签时, THE Test_Suite SHALL 验证脚本被转义或过滤
15.3. WHEN 测试编码后的script标签时, THE Test_Suite SHALL 验证脚本被转义或过滤
15.4. WHEN 测试事件处理器注入(onclick)时, THE Test_Suite SHALL 验证事件被过滤
15.5. WHEN 测试事件处理器注入(onerror)时, THE Test_Suite SHALL 验证事件被过滤
15.6. WHEN 测试事件处理器注入(onload)时, THE Test_Suite SHALL 验证事件被过滤
15.7. WHEN 测试img标签onerror注入时, THE Test_Suite SHALL 验证脚本被过滤
15.8. WHEN 测试svg标签onload注入时, THE Test_Suite SHALL 验证脚本被过滤
15.9. WHEN 测试iframe标签注入时, THE Test_Suite SHALL 验证标签被过滤
15.10. WHEN 测试javascript:协议注入时, THE Test_Suite SHALL 验证协议被过滤
15.11. WHEN 测试data:协议注入时, THE Test_Suite SHALL 验证协议被过滤
15.12. WHEN 测试vbscript:协议注入时, THE Test_Suite SHALL 验证协议被过滤
15.13. WHEN 测试expression()CSS注入时, THE Test_Suite SHALL 验证表达式被过滤
15.14. WHEN 测试style标签注入时, THE Test_Suite SHALL 验证样式被过滤或转义
15.15. WHEN 测试base64编码XSS时, THE Test_Suite SHALL 验证编码内容被正确处理

#### Acceptance Criteria - SQL注入测试 (15个)

15.16. WHEN 测试单引号SQL注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.17. WHEN 测试双引号SQL注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.18. WHEN 测试注释符SQL注入(--) 时, THE Test_Suite SHALL 验证注入被正确拒绝
15.19. WHEN 测试注释符SQL注入(#)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.20. WHEN 测试注释符SQL注入(/*)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.21. WHEN 测试UNION SELECT注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.22. WHEN 测试OR 1=1注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.23. WHEN 测试AND 1=1注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.24. WHEN 测试DROP TABLE注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.25. WHEN 测试INSERT INTO注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.26. WHEN 测试UPDATE SET注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.27. WHEN 测试DELETE FROM注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.28. WHEN 测试EXEC注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.29. WHEN 测试时间盲注(SLEEP)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.30. WHEN 测试布尔盲注时, THE Test_Suite SHALL 验证注入被正确拒绝

#### Acceptance Criteria - NoSQL注入测试 (10个)

15.31. WHEN 测试MongoDB $where注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.32. WHEN 测试MongoDB $gt/$lt注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.33. WHEN 测试MongoDB $regex注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.34. WHEN 测试MongoDB $ne注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.35. WHEN 测试Redis命令注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.36. WHEN 测试Elasticsearch查询注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.37. WHEN 测试JSON注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.38. WHEN 测试LDAP注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.39. WHEN 测试XPath注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.40. WHEN 测试XML注入时, THE Test_Suite SHALL 验证注入被正确拒绝

#### Acceptance Criteria - 命令注入测试 (10个)

15.41. WHEN 测试Shell命令注入(;)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.42. WHEN 测试Shell命令注入(|)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.43. WHEN 测试Shell命令注入(&)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.44. WHEN 测试Shell命令注入($())时, THE Test_Suite SHALL 验证注入被正确拒绝
15.45. WHEN 测试Shell命令注入(``)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.46. WHEN 测试路径遍历注入(../)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.47. WHEN 测试路径遍历注入(..\)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.48. WHEN 测试空字节注入(%00)时, THE Test_Suite SHALL 验证注入被正确拒绝
15.49. WHEN 测试CRLF注入时, THE Test_Suite SHALL 验证注入被正确拒绝
15.50. WHEN 测试HTTP头注入时, THE Test_Suite SHALL 验证注入被正确拒绝

#### Acceptance Criteria - 特殊字符注入测试 (10个)

15.51. WHEN 测试反斜杠注入时, THE Test_Suite SHALL 验证正确转义或过滤
15.52. WHEN 测试正斜杠注入时, THE Test_Suite SHALL 验证正确处理
15.53. WHEN 测试尖括号注入时, THE Test_Suite SHALL 验证正确转义
15.54. WHEN 测试花括号注入时, THE Test_Suite SHALL 验证正确处理
15.55. WHEN 测试方括号注入时, THE Test_Suite SHALL 验证正确处理
15.56. WHEN 测试百分号注入时, THE Test_Suite SHALL 验证正确处理
15.57. WHEN 测试井号注入时, THE Test_Suite SHALL 验证正确处理
15.58. WHEN 测试美元符号注入时, THE Test_Suite SHALL 验证正确处理
15.59. WHEN 测试管道符注入时, THE Test_Suite SHALL 验证正确处理
15.60. WHEN 测试波浪号注入时, THE Test_Suite SHALL 验证正确处理

### Requirement 16: 认证与授权边界测试 (Auth Boundary Tests) - 25个测试用例

**User Story:** As a 安全测试人员, I want 对认证和授权机制进行全面的边界测试, so that 确保系统的访问控制安全可靠。

#### Acceptance Criteria - Token边界测试 (10个)

16.1. WHEN 测试空Token时, THE Test_Suite SHALL 验证返回401错误
16.2. WHEN 测试格式错误的Token时, THE Test_Suite SHALL 验证返回401错误
16.3. WHEN 测试过期Token时, THE Test_Suite SHALL 验证返回401错误
16.4. WHEN 测试被篡改的Token时, THE Test_Suite SHALL 验证返回401错误
16.5. WHEN 测试签名错误的Token时, THE Test_Suite SHALL 验证返回401错误
16.6. WHEN 测试缺少必要声明的Token时, THE Test_Suite SHALL 验证返回401错误
16.7. WHEN 测试超长Token时, THE Test_Suite SHALL 验证返回400或401错误
16.8. WHEN 测试包含特殊字符的Token时, THE Test_Suite SHALL 验证返回401错误
16.9. WHEN 测试已注销用户的Token时, THE Test_Suite SHALL 验证返回401错误
16.10. WHEN 测试已被拉黑的Token时, THE Test_Suite SHALL 验证返回401错误

#### Acceptance Criteria - 权限边界测试 (10个)

16.11. WHEN 测试普通用户访问管理接口时, THE Test_Suite SHALL 验证返回403错误
16.12. WHEN 测试访问他人私有资源时, THE Test_Suite SHALL 验证返回403错误
16.13. WHEN 测试修改他人资源时, THE Test_Suite SHALL 验证返回403错误
16.14. WHEN 测试删除他人资源时, THE Test_Suite SHALL 验证返回403错误
16.15. WHEN 测试越权提升自己权限时, THE Test_Suite SHALL 验证返回403错误
16.16. WHEN 测试访问已删除用户的资源时, THE Test_Suite SHALL 验证返回404或403错误
16.17. WHEN 测试访问被禁用用户的资源时, THE Test_Suite SHALL 验证返回403错误
16.18. WHEN 测试批量操作包含无权限资源时, THE Test_Suite SHALL 验证返回403或部分成功
16.19. WHEN 测试通过ID猜测访问资源时, THE Test_Suite SHALL 验证返回404或403错误
16.20. WHEN 测试通过URL参数绕过权限时, THE Test_Suite SHALL 验证返回403错误

#### Acceptance Criteria - 会话边界测试 (5个)

16.21. WHEN 测试并发登录同一账号时, THE Test_Suite SHALL 验证正确处理多设备登录
16.22. WHEN 测试登出后使用旧Token时, THE Test_Suite SHALL 验证返回401错误
16.23. WHEN 测试密码修改后使用旧Token时, THE Test_Suite SHALL 验证返回401错误
16.24. WHEN 测试RefreshToken过期时, THE Test_Suite SHALL 验证返回401错误
16.25. WHEN 测试RefreshToken被撤销时, THE Test_Suite SHALL 验证返回401错误

### Requirement 17: 并发与幂等性测试 (Concurrency Tests) - 20个测试用例

**User Story:** As a 测试人员, I want 对关键操作进行并发和幂等性测试, so that 确保系统在并发场景下数据一致性。

#### Acceptance Criteria - 幂等性测试 (10个)

17.1. WHEN 重复提交相同的点赞请求时, THE Test_Suite SHALL 验证只记录一次点赞
17.2. WHEN 重复提交相同的收藏请求时, THE Test_Suite SHALL 验证只记录一次收藏
17.3. WHEN 重复提交相同的关注请求时, THE Test_Suite SHALL 验证只记录一次关注
17.4. WHEN 重复提交相同的签到请求时, THE Test_Suite SHALL 验证只记录一次签到
17.5. WHEN 重复标记同一通知已读时, THE Test_Suite SHALL 验证幂等处理
17.6. WHEN 重复标记同一消息已读时, THE Test_Suite SHALL 验证幂等处理
17.7. WHEN 重复取消点赞请求时, THE Test_Suite SHALL 验证幂等处理
17.8. WHEN 重复取消收藏请求时, THE Test_Suite SHALL 验证幂等处理
17.9. WHEN 重复取消关注请求时, THE Test_Suite SHALL 验证幂等处理
17.10. WHEN 重复删除同一资源时, THE Test_Suite SHALL 验证幂等处理

#### Acceptance Criteria - 并发测试 (10个)

17.11. WHEN 并发点赞同一文章时, THE Test_Suite SHALL 验证点赞数正确
17.12. WHEN 并发评论同一文章时, THE Test_Suite SHALL 验证评论数正确
17.13. WHEN 并发关注同一用户时, THE Test_Suite SHALL 验证粉丝数正确
17.14. WHEN 并发发送消息时, THE Test_Suite SHALL 验证消息顺序正确
17.15. WHEN 并发更新同一资源时, THE Test_Suite SHALL 验证最终状态一致
17.16. WHEN 并发删除同一资源时, THE Test_Suite SHALL 验证只删除一次
17.17. WHEN 并发创建相同唯一键资源时, THE Test_Suite SHALL 验证只创建一个
17.18. WHEN 并发刷新Token时, THE Test_Suite SHALL 验证Token正确生成
17.19. WHEN 并发注册相同邮箱时, THE Test_Suite SHALL 验证只注册一个
17.20. WHEN 并发签到时, THE Test_Suite SHALL 验证只签到一次

## 测试用例总数统计

| 模块 | 测试用例数 |
|------|-----------|
| 用户服务 | 35 |
| 文章服务 | 41 |
| 评论服务 | 36 |
| 消息服务 | 20 |
| 通知服务 | 15 |
| 搜索服务 | 12 |
| 排行榜服务 | 12 |
| 上传服务 | 15 |
| 管理后台 | 25 |
| 网关服务 | 15 |
| 边界测试 | 50 |
| 安全注入测试 | 60 |
| 认证授权边界测试 | 25 |
| 并发幂等性测试 | 20 |
| **总计** | **381** |
