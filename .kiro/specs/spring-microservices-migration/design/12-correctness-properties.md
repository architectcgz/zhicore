# 正确性属性定义

## 概述

本文档定义系统必须满足的正确性属性，用于指导测试策略和验证系统行为。

---

## 用户服务正确性属性

### CP-USER-01: 邮箱唯一性
```
∀ user1, user2 ∈ Users:
  user1.id ≠ user2.id → user1.email ≠ user2.email
```
**描述**: 系统中不存在两个不同用户拥有相同邮箱。

### CP-USER-02: 关注关系对称统计
```
∀ user ∈ Users:
  count(user.followers) = user.followersCount ∧
  count(user.following) = user.followingCount
```
**描述**: 用户的粉丝数等于实际关注该用户的人数，关注数等于该用户实际关注的人数。

### CP-USER-03: 关注关系非自反
```
∀ follow ∈ UserFollows:
  follow.followerId ≠ follow.followingId
```
**描述**: 用户不能关注自己。

### CP-USER-04: 签到幂等性
```
∀ user ∈ Users, date ∈ Dates:
  checkIn(user, date) 执行多次 → user.checkInRecords 中 date 只出现一次
```
**描述**: 同一用户同一天多次签到，只记录一次。

### CP-USER-05: 连续签到计算正确性
```
∀ user ∈ Users:
  user.continuousDays = maxConsecutiveDays(user.checkInRecords, today)
```
**描述**: 连续签到天数等于从今天往前数的最大连续签到天数。


---

## 文章服务正确性属性

### CP-POST-01: 点赞计数一致性
```
∀ post ∈ Posts:
  post.likeCount = count(PostLikes where postId = post.id)
```
**描述**: 文章点赞数等于实际点赞记录数。

### CP-POST-02: 点赞幂等性
```
∀ user ∈ Users, post ∈ Posts:
  like(user, post) 执行多次 → PostLikes 中 (user, post) 只有一条记录
```
**描述**: 同一用户对同一文章多次点赞，只记录一次。

### CP-POST-03: 评论计数一致性
```
∀ post ∈ Posts:
  post.commentCount = count(Comments where postId = post.id ∧ status = NORMAL)
```
**描述**: 文章评论数等于该文章下未删除的评论数。

### CP-POST-04: 收藏计数一致性
```
∀ post ∈ Posts:
  post.favoriteCount = count(PostFavorites where postId = post.id)
```
**描述**: 文章收藏数等于实际收藏记录数。

### CP-POST-05: 状态转换合法性
```
∀ post ∈ Posts:
  post.status 只能按以下路径转换:
  DRAFT → PUBLISHED
  DRAFT → SCHEDULED → PUBLISHED
  PUBLISHED → DRAFT
  * → DELETED
```
**描述**: 文章状态转换必须遵循预定义的状态机。

### CP-POST-06: 定时发布时间约束
```
∀ post ∈ Posts:
  post.scheduledAt ≠ null → post.scheduledAt > now()
```
**描述**: 定时发布时间必须是未来时间。

---

## 评论服务正确性属性

### CP-COMMENT-01: 评论点赞计数一致性
```
∀ comment ∈ Comments:
  comment.likeCount = count(CommentLikes where commentId = comment.id)
```
**描述**: 评论点赞数等于实际点赞记录数。

### CP-COMMENT-02: 回复计数一致性
```
∀ comment ∈ Comments where comment.parentId = null:
  comment.replyCount = count(Comments where rootId = comment.id ∧ id ≠ comment.id)
```
**描述**: 顶级评论的回复数等于以该评论为根的所有子评论数。

### CP-COMMENT-03: 嵌套评论结构完整性
```
∀ comment ∈ Comments where comment.parentId ≠ null:
  ∃ parent ∈ Comments: parent.id = comment.parentId ∧
  comment.rootId = (parent.rootId if parent.parentId ≠ null else parent.id)
```
**描述**: 子评论的 rootId 指向正确的根评论。

### CP-COMMENT-04: 评论点赞幂等性
```
∀ user ∈ Users, comment ∈ Comments:
  likeComment(user, comment) 执行多次 → CommentLikes 中 (user, comment) 只有一条记录
```
**描述**: 同一用户对同一评论多次点赞，只记录一次。


---

## 消息服务正确性属性

### CP-MSG-01: 消息顺序性
```
∀ conversation ∈ Conversations:
  messages(conversation) 按 createdAt 排序后，顺序与发送顺序一致
```
**描述**: 同一会话中的消息按发送时间排序。

### CP-MSG-02: 会话唯一性
```
∀ conv1, conv2 ∈ Conversations:
  {conv1.participant1, conv1.participant2} = {conv2.participant1, conv2.participant2}
  → conv1.id = conv2.id
```
**描述**: 两个用户之间只有一个会话。

### CP-MSG-03: 未读计数一致性
```
∀ conversation ∈ Conversations, user ∈ {participant1, participant2}:
  conversation.unreadCount(user) = count(Messages where 
    conversationId = conversation.id ∧ 
    receiverId = user ∧ 
    isRead = false)
```
**描述**: 会话未读数等于该用户在该会话中未读消息数。

### CP-MSG-04: 消息撤回时间约束
```
∀ message ∈ Messages:
  recall(message) 成功 → now() - message.createdAt ≤ 2 minutes
```
**描述**: 只能撤回 2 分钟内发送的消息。

---

## 通知服务正确性属性

### CP-NOTIF-01: 未读计数一致性
```
∀ user ∈ Users:
  user.unreadNotificationCount = count(Notifications where 
    recipientId = user.id ∧ isRead = false)
```
**描述**: 用户未读通知数等于实际未读通知记录数。

### CP-NOTIF-02: 通知不重复
```
∀ event ∈ DomainEvents:
  processEvent(event) 执行多次 → 只创建一条通知
```
**描述**: 同一事件不会创建重复通知（幂等性）。

---

## 排行榜服务正确性属性

### CP-RANK-01: 排行榜有序性
```
∀ ranking ∈ Rankings:
  ∀ i, j where i < j:
    ranking[i].score ≥ ranking[j].score
```
**描述**: 排行榜按分数降序排列。

### CP-RANK-02: 热度分数非负
```
∀ post ∈ Posts:
  hotScore(post) ≥ 0
```
**描述**: 文章热度分数不能为负数。

---

## 跨服务正确性属性

### CP-CROSS-01: 事件最终一致性
```
∀ event ∈ DomainEvents:
  eventually(∀ consumer ∈ event.consumers: consumer.processed(event))
```
**描述**: 所有领域事件最终会被所有订阅者处理。

### CP-CROSS-02: 缓存最终一致性
```
∀ entity ∈ Entities:
  eventually(cache(entity) = database(entity) ∨ cache(entity) = null)
```
**描述**: 缓存数据最终与数据库一致，或缓存失效。

### CP-CROSS-03: 分布式 ID 唯一性
```
∀ id1, id2 ∈ GeneratedIds:
  id1 生成时间 ≠ id2 生成时间 → id1 ≠ id2
```
**描述**: Leaf 生成的分布式 ID 全局唯一。
