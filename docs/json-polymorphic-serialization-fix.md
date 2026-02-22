# JSON 多态序列化问题修复指南

## 问题描述

### 现象
在 SignalR 推送通知时，前端收到的通知对象只包含基类的属性，子类的所有属性都丢失了。

**问题示例：**
```json
// 期望收到的完整数据
{
  "type": 4,
  "id": 744392717066309,
  "likerUser": {
    "id": "8f97af68-5b6e-4e61-bcf1-5ec5d1538e36",
    "nickName": "秋月白",
    "avatarUrl": "https://..."
  },
  "targetType": "文章",
  "targetId": 113430676701189,
  "targetTitle": "测试：用于测试点赞和评论3",
  "link": "/posts/113430676701189",
  "isRead": false,
  "createdAt": "2025-11-23T14:31:52.4491998+00:00"
}

// 实际收到的数据（只有基类属性）
{
  "type": 4,
  "createdAt": "2025-11-23T14:25:56.9958059+00:00"
}
```

### 根本原因

**JSON 序列化抽象类时，默认行为只序列化基类声明的属性。**

在我们的代码中：
1. `SignalRNotification` 是抽象基类，只有 `Type` 和 `CreatedAt` 两个属性
2. `LikeNotification`、`CommentNotification` 等是具体子类，包含各自的属性
3. 当序列化类型为 `SignalRNotification` 的变量时，即使实际值是 `LikeNotification` 实例，`System.Text.Json` 也只会序列化基类属性

**代码路径分析：**
```
NotificationService.CreateNotificationAsync()
  ↓
TryPushNotificationFromEntityAsync()
  ↓
SignalRNotificationConverter.CreateFromEntity()  // 创建了 LikeNotification 实例
  ↓
notificationHubService.PushNotificationAsync(userId, signalRNotification)  // 参数类型是 SignalRNotification
  ↓
RabbitMqEventPublisher.PublishSignalRNotificationAsync()
  ↓
MessageSerializationOptions.SafeSerialize(messageData)  // ❌ 这里序列化时只保留基类属性
```

## 解决方案

### .NET 7+ 推荐方案：使用 `[JsonPolymorphic]` 特性

从 .NET 7 开始，`System.Text.Json` 支持通过特性配置多态序列化。

#### 1. 为基类添加 `[JsonPolymorphic]` 特性

```csharp
using System.Text.Json.Serialization;

namespace ZhiCoreShared.SignalR.Notifications;

/// <summary>
/// SignalR 通知基类
/// </summary>
[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(LikeNotification), typeDiscriminator: (int)SignalRNotificationType.Like)]
[JsonDerivedType(typeof(CommentNotification), typeDiscriminator: (int)SignalRNotificationType.Comment)]
[JsonDerivedType(typeof(FollowNotification), typeDiscriminator: (int)SignalRNotificationType.Follow)]
[JsonDerivedType(typeof(NewMessageNotification), typeDiscriminator: (int)SignalRNotificationType.NewMessage)]
[JsonDerivedType(typeof(UnreadCountNotification), typeDiscriminator: (int)SignalRNotificationType.UnreadCount)]
public abstract class SignalRNotification
{
    /// <summary>
    /// 通知类型
    /// </summary>
    public SignalRNotificationType Type { get; set; }
    
    /// <summary>
    /// 创建时间
    /// </summary>
    public DateTimeOffset CreatedAt { get; init; } = DateTimeOffset.UtcNow;
}
```

#### 2. 配置说明

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `TypeDiscriminatorPropertyName` | 类型鉴别器字段名 | `"type"` |
| `JsonDerivedType` | 注册派生类型 | `typeof(LikeNotification)` |
| `typeDiscriminator` | 类型标识值 | `(int)SignalRNotificationType.Like` |

#### 3. 序列化和反序列化行为

**序列化时：**
- 根据实际对象类型找到对应的 `[JsonDerivedType]` 配置
- 序列化所有属性（包括基类和子类）
- 自动添加类型鉴别器字段（如 `"type": 4`）

**反序列化时：**
- 读取类型鉴别器字段值
- 根据值找到对应的派生类型
- 反序列化为正确的具体类型

### 替代方案（.NET 6 及更早版本）

如果使用 .NET 6 或更早版本，可以使用自定义 `JsonConverter`：

```csharp
public class SignalRNotificationConverter : JsonConverter<SignalRNotification>
{
    public override SignalRNotification Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        using var doc = JsonDocument.ParseValue(ref reader);
        var root = doc.RootElement;
        
        if (!root.TryGetProperty("type", out var typeElement))
            throw new JsonException("Missing type discriminator");
        
        var notificationType = (SignalRNotificationType)typeElement.GetInt32();
        
        return notificationType switch
        {
            SignalRNotificationType.Like => JsonSerializer.Deserialize<LikeNotification>(root.GetRawText(), options),
            SignalRNotificationType.Comment => JsonSerializer.Deserialize<CommentNotification>(root.GetRawText(), options),
            // ... 其他类型
            _ => throw new NotSupportedException($"Unknown notification type: {notificationType}")
        };
    }
    
    public override void Write(Utf8JsonWriter writer, SignalRNotification value, JsonSerializerOptions options)
    {
        JsonSerializer.Serialize(writer, value, value.GetType(), options);
    }
}

// 使用时注册转换器
var options = new JsonSerializerOptions
{
    Converters = { new SignalRNotificationConverter() }
};
```

## 验证修复

### 1. 后端验证
添加调试日志确认数据完整性：

```csharp
var signalRNotification = SignalRNotificationConverter.CreateFromEntity(entity);

if (signalRNotification is LikeNotification likeNotif)
{
    logger.LogInformation(
        "📋 LikeNotification详情: Id={Id}, LikerUser={LikerUser}, TargetType={TargetType}, TargetId={TargetId}, TargetTitle={TargetTitle}, Link={Link}", 
        likeNotif.Id, 
        likeNotif.LikerUser?.NickName, 
        likeNotif.TargetType, 
        likeNotif.TargetId, 
        likeNotif.TargetTitle, 
        likeNotif.Link);
}
```

### 2. 前端验证
检查 SignalR 接收到的数据：

```javascript
notificationHub.on('ReceiveNotification', (notification) => {
  console.log('收到通知:', notification);
  // 验证所有子类属性都存在
  if (notification.type === 4) { // Like
    console.assert(notification.id, 'id 应该存在');
    console.assert(notification.likerUser, 'likerUser 应该存在');
    console.assert(notification.targetType, 'targetType 应该存在');
  }
});
```

## 常见问题

### Q: 为什么不直接序列化具体类型？
A: 在很多场景下，我们需要统一处理不同类型的通知，使用基类类型可以保持代码的统一性和可维护性。多态序列化允许我们在保持类型抽象的同时，正确序列化所有数据。

### Q: `TypeDiscriminatorPropertyName` 可以省略吗？
A: 可以，默认值是 `"$type"`，但建议显式指定为业务字段（如 `"type"`），这样：
- JSON 更简洁
- 与现有的业务字段对齐
- 避免特殊字符 `$` 可能引起的问题

### Q: 如果忘记注册某个派生类会怎样？
A: 序列化会失败并抛出异常 `NotSupportedException`，提示不支持该类型。这是一个好的设计，可以在开发阶段尽早发现问题。

### Q: 性能影响如何？
A: `[JsonPolymorphic]` 是编译时特性，性能影响很小。相比自定义 `JsonConverter`，它是更优的选择。

## 相关资源

- [.NET 7+ Polymorphic JSON Serialization](https://learn.microsoft.com/en-us/dotnet/standard/serialization/system-text-json/polymorphism)
- [System.Text.Json.Serialization.JsonPolymorphicAttribute](https://learn.microsoft.com/en-us/dotnet/api/system.text.json.serialization.jsonpolymorphicattribute)
- [System.Text.Json.Serialization.JsonDerivedTypeAttribute](https://learn.microsoft.com/en-us/dotnet/api/system.text.json.serialization.jsonderivedtypeattribute)

## 修复历史

| 日期 | 问题 | 解决方案 | 修复人 |
|------|------|----------|--------|
| 2025-11-23 | SignalR 通知数据丢失 | 添加 `[JsonPolymorphic]` 特性 | AI Assistant |

## 总结

JSON 多态序列化是一个常见但容易被忽略的问题。通过使用 .NET 7+ 的 `[JsonPolymorphic]` 特性，我们可以：

1. ✅ **简洁优雅**：只需要几行特性配置
2. ✅ **类型安全**：编译时检查，避免运行时错误
3. ✅ **高性能**：无需自定义转换器
4. ✅ **易维护**：所有配置集中在基类定义处

记住：**当序列化抽象类或接口类型时，始终考虑多态序列化配置！**
