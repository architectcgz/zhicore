# TASK-02: 删除文章时清理正文图片

## 背景

`PostApplicationService.java:783` 中，删除文章时未清理正文内的图片资源。
文章内容存储在 MongoDB（`post_contents` 集合），需要先拉取内容、解析图片 URL，再异步清理。

## 涉及文件

- `com.zhicore.content.application.service.PostApplicationService` — 第 783 行 TODO
- `com.zhicore.content.infrastructure.persistence.mongo.repository.PostContentRepository` — 已有 `findByPostId`
- 可能需要新增：图片 URL 解析工具类

## 实现要求

### 1. 从 MongoDB 拉取正文内容

```java
PostContentDocument contentDoc = postContentRepository.findByPostId(postId);
```

### 2. 解析正文中的图片 URL

- 正文格式为 HTML 或 Markdown，需解析 `<img src="...">` 和 `![](...)` 中的图片地址
- 仅清理项目自有存储的图片（通过 URL 前缀或域名判断），忽略外链图片
- 建议新增工具方法：`ContentImageExtractor.extractImageUrls(String content, String contentType)`

### 3. 异步清理图片

- 使用 Spring `@Async` 异步执行清理（项目需确认 `@EnableAsync` 已配置）
- 清理失败记录日志 + 告警，不阻塞删除主流程
- 单张图片清理失败不影响其他图片

### 4. 边界情况

- 正文为空或不存在：跳过，不报错
- 正文中无图片：跳过
- 图片已被其他文章引用：当前阶段不做引用计数，直接清理（后续可优化）

## 验收标准

- [ ] 删除文章时，从 MongoDB 拉取正文并解析图片 URL
- [ ] 自有存储的图片被异步删除
- [ ] 外链图片不被清理
- [ ] 正文为空/无图片时不报错
- [ ] 清理失败不阻塞删除主流程，有日志记录
- [ ] 单元测试：覆盖 HTML/Markdown 两种格式的图片解析

## 回滚策略

- 新增逻辑，revert commit 即可
- 图片清理是单向操作，已删除的图片无法恢复（但文章本身是软删除，可恢复文章后重新上传）
