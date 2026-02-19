# Design Document: Spring Cloud Alibaba 微服务架构设计

## Overview

本设计文档描述将现有 ASP.NET Core 单体博客系统迁移到 Spring Cloud Alibaba 微服务架构的详细设计方案。系统将拆分为 9 个业务微服务和若干基础设施组件，采用 DDD 分层架构，实现服务解耦、独立部署和弹性伸缩。

## 技术栈版本矩阵

> **重要：版本兼容性约束**
> 
> 以下版本经过兼容性验证，请勿随意升级单个组件版本。

| 组件 | 版本 | 最低兼容版本 | 说明 |
|------|------|-------------|------|
| **JDK** | 17 | 17 | LTS 版本，支持 Records、Sealed Classes |
| **Spring Boot** | 3.2.x | 3.2.0 | 最新稳定版，支持 Virtual Threads |
| **Spring Cloud** | 2023.0.x | 2023.0.0 | 与 Spring Boot 3.2 兼容 |
| **Spring Cloud Alibaba** | 2023.0.x | 2023.0.0.0 | 与 Spring Cloud 2023.0 兼容 |
| **Nacos** | 2.3.x | 2.3.0 | 服务注册与配置中心 |
| **Sentinel** | 1.8.x | 1.8.6 | 流量控制与熔断降级 |
| **RocketMQ** | 5.1.x | 5.1.0 | 消息队列，支持事务消息 |
| **PostgreSQL** | 15.x | 15.0 | 主数据库 |
| **Redis** | 7.x | 7.0 | 缓存与分布式锁 |
| **Elasticsearch** | 8.11.x | 8.11.0 | 全文搜索引擎 |
| **SkyWalking** | 9.x | 9.5.0 | 链路追踪与 APM |
| **MyBatis-Plus** | 3.5.x | 3.5.5 | ORM 框架 |

### Maven BOM 配置

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.1</spring-boot.version>
    <spring-cloud.version>2023.0.0</spring-cloud.version>
    <spring-cloud-alibaba.version>2023.0.0.0</spring-cloud-alibaba.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 公共规范索引

> **设计说明：集中管理公共规范**
> 
> 以下规范文档定义了跨服务的统一标准，所有服务必须遵循。

| 规范 | 位置 | 说明 |
|------|------|------|
| **错误码规范** | #[[file:design/13-error-handling.md]] | 统一错误码定义（1xxx-6xxx） |
| **Redis Key 规范** | #[[file:design/11-data-models.md]] | Key 命名格式：`{service}:{entity}:{id}:{field}` |
| **事件命名规范** | #[[file:design/11-data-models.md]] | 领域事件定义与版本管理 |
| **配置管理规范** | #[[file:design/10-infrastructure.md]] | Nacos 配置分类与命名 |
| **缓存策略矩阵** | #[[file:design/10-infrastructure.md]] | 各实体缓存策略与 TTL |
| **API 版本控制** | #[[file:design/15-enhancements.md]] | URL 路径版本化 `/api/v1/*` |
| **分页策略** | #[[file:design/15-enhancements.md]] | Web/移动端分页规范 |

## 文档结构

本设计文档按模块拆分为以下子文档：

| 文档 | 描述 |
|------|------|
| #[[file:design/01-architecture.md]] | 整体架构设计、服务间通信模式、DDD 分层原则 |
| #[[file:design/02-gateway.md]] | API Gateway 设计、路由配置、JWT 认证 |
| #[[file:design/03-user-service.md]] | 用户服务设计、关注系统、签到功能 |
| #[[file:design/04-post-service.md]] | 文章服务设计、点赞系统、混合分页 |
| #[[file:design/05-comment-service.md]] | 评论服务设计、嵌套评论、热门回复 |
| #[[file:design/06-message-service.md]] | 消息服务设计、多端推送架构 |
| #[[file:design/07-notification-service.md]] | 通知服务设计、通知聚合 |
| #[[file:design/08-search-ranking-service.md]] | 搜索服务、排行榜服务设计 |
| #[[file:design/09-upload-admin-service.md]] | 上传服务、管理后台服务设计 |
| #[[file:design/10-infrastructure.md]] | 基础设施：缓存、消息队列、分布式事务 |
| #[[file:design/11-data-models.md]] | 领域事件、RabbitMQ 配置、Redis Key 规范 |
| #[[file:design/12-correctness-properties.md]] | 正确性属性定义 |
| #[[file:design/13-error-handling.md]] | 错误处理、熔断降级、缓存问题解决方案 |
| #[[file:design/14-testing-strategy.md]] | 测试策略、Property-Based Testing |
| #[[file:design/15-enhancements.md]] | 架构增强：API 版本控制、Saga 事务、限流、监控、RBAC |

## 快速导航

### 核心服务设计
- 用户服务 → #[[file:design/03-user-service.md]]
- 文章服务 → #[[file:design/04-post-service.md]]
- 评论服务 → #[[file:design/05-comment-service.md]]
- 消息服务 → #[[file:design/06-message-service.md]]

### 基础设施
- 缓存架构 → #[[file:design/10-infrastructure.md]]
- 分布式事务 → #[[file:design/15-enhancements.md]]

### 质量保障
- 正确性属性 → #[[file:design/12-correctness-properties.md]]
- 测试策略 → #[[file:design/14-testing-strategy.md]]
