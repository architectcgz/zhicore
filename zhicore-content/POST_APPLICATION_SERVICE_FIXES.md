# PostApplicationService 修复进度

## 修复状态

**当前状态**: 进行中 (In Progress)  
**最后更新**: 2026-02-23

## 已修复的错误

### 1. 导入缺失
- ✅ 添加了 `TopicId` 导入

### 2. 值对象转换错误 (PostApplicationService.java)
- ✅ Line 166-168: 修复了 `postTagRepository.attachBatch()` 调用，使用 `tagId.getValue()` 和 `createdPostId.getValue()`
- ✅ Line 183, 192, 443: 修复了领域事件类型声明，使用 `List<DomainEvent<?>>`
- ✅ Line 619-620, 685-686: 修复了 PostVO setters，直接使用 `getValue()` 返回的 Long
- ✅ Line 632-634, 638-639, 698-700, 704-705: 修复了统计数据类型转换，viewCount 使用 long，其他使用 (int) 强制转换
- ✅ Line 557: 修复了 `isOwnedBy()` 调用，使用 `getValue()` 获取 Long
- ✅ Line 877, 923, 978, 980, 984: 修复了 DraftManager 方法调用，使用 `PostId.of()` 和 `UserId.of()`

### 3. 方法签名修复
- ✅ `saveDraft()`: 修复了 `draftManager.saveDraft()` 调用，使用 `PostId.of(postId)`
- ✅ `getDraft()`: 已经正确使用 `PostId.of()` 和 `UserId.of()`
- ✅ `getUserDrafts()`: 已经正确使用 `UserId.of()`
- ✅ `deleteDraft()`: 已经正确使用 `PostId.of()` 和 `UserId.of()`
- ✅ `scheduleAuthorInfoCompensation()`: 已经正确使用 `PostId.of()` 和 `UserId.of()`

## 剩余错误 (约30个)

### PostApplicationService.java 中的剩余错误
1. Line 1037, 1062, 1125, 1331, 1338, 1372: PostId/UserId 转换错误
2. Line 1352: `UserId.of()` 方法调用错误

### 其他文件中的错误
1. **CreateDraftWorkflow.java** (Line 62, 106): 
   - `Post.createDraft()` 方法签名不匹配
   - `PostCreatedEvent` 类型转换错误

2. **PublishPostWorkflow.java** (Line 88):
   - `Clock` 无法转换为 `DomainEventFactory`

3. **UpdatePostMetaHandler.java** (Line 70):
   - Long 无法转换为 String

4. **UpdatePostContentHandler.java** (Line 69):
   - `post.updateTags()` 方法调用错误

5. **PurgePostHandler.java** (Lines 51, 58, 66, 73):
   - Long 无法转换为 PostId

6. **EventMapper.java**: 多个 Long 到 String 的转换错误

7. **PostStats.java**: null 比较和 long 到 int 转换错误

8. **Domain Event 类**: 缺少 `getSchemaVersion()` 实现，`getOccurredAt()` 返回类型错误

9. **其他服务类**: PostFavoriteApplicationService, PostLikeApplicationService 等

## 下一步行动

1. 修复 PostApplicationService.java 中剩余的值对象转换错误
2. 修复 CreateDraftWorkflow.java 和 PublishPostWorkflow.java
3. 修复所有 Handler 类中的类型转换错误
4. 修复 EventMapper.java 中的转换错误
5. 修复 Domain Event 类的接口实现
6. 修复其他应用服务类

## 修复模式总结

### 值对象转换规则
- `PostId.of(Long)` → 创建 PostId
- `UserId.of(Long)` → 创建 UserId
- `TopicId.of(Long)` → 创建 TopicId
- `valueObject.getValue()` → 获取 Long 值

### 统计数据类型
- `viewCount`: `long` (直接赋值)
- `likeCount`, `commentCount`: `int` (需要强制转换)

### 领域事件类型
- 使用 `List<DomainEvent<?>>` 而不是 `List<DomainEvent>`

---

**维护者**: 开发团队  
**审查频率**: 每次修复后更新
