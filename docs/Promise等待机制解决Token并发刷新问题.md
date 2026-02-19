# Promise 等待机制：优雅解决 Token 并发刷新问题

> 在 SPA 应用中，当用户打开一个需要认证的页面时，通常会同时发起多个 API 请求。如果 Token 已过期，这些请求都会返回 401 错误并尝试刷新 Token。如何避免重复刷新？本文介绍一种基于 Promise 等待机制的优雅解决方案。

## 📋 目录

- [问题场景](#问题场景)
- [传统方案的困境](#传统方案的困境)
- [Promise 等待机制](#promise-等待机制)
- [核心实现](#核心实现)
- [工作流程详解](#工作流程详解)
- [关键技术点](#关键技术点)
- [性能对比](#性能对比)
- [实战效果](#实战效果)
- [总结](#总结)

## 🎯 问题场景

### 典型场景

用户打开一个需要认证的页面（如用户中心），页面同时发起 3 个 API 请求：

```
页面加载
  ├─ GET /api/notifications/unread-count  → 401 Unauthorized
  ├─ GET /api/user/profile                → 401 Unauthorized
  └─ GET /api/messages/list               → 401 Unauthorized
```

### 问题根源

在传统的实现中，每个收到 401 响应的请求都会独立触发 Token 刷新：

```
❌ 问题：重复刷新
  ├─ POST /api/auth/refresh-token  (请求1触发)
  ├─ POST /api/auth/refresh-token  (请求2触发) ← 重复！
  └─ POST /api/auth/refresh-token  (请求3触发) ← 重复！
```

**后果**：
- 浪费网络带宽和服务器资源
- 可能导致竞态条件（Race Condition）
- 影响用户体验（响应变慢）
- 在高并发场景下，可能触发 API 限流

## 🔄 传统方案的困境

### 方案一：请求队列

许多开源项目采用"请求队列"的方式：

```typescript
// ❌ 传统队列方案
let isRefreshing = false;
let failedQueue = [];

// 401错误处理
if (error.response?.status === 401) {
  if (isRefreshing) {
    // 将请求加入队列
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    }).then(token => {
      originalRequest.headers.Authorization = `Bearer ${token}`;
      return axios(originalRequest);
    });
  }
  
  isRefreshing = true;
  
  // 刷新token
  return refreshToken().then(newToken => {
    // 处理队列中的所有请求
    failedQueue.forEach(prom => {
      prom.resolve(newToken);
    });
    failedQueue = [];
    isRefreshing = false;
  });
}
```

**缺点**：
- 代码复杂：需要维护队列、处理队列回调
- 类型不安全：Promise 回调缺少类型约束
- 容易出错：队列管理逻辑容易遗漏清理
- 调试困难：异步队列的执行流程不够直观

### 方案二：函数内状态

另一种常见的错误是将状态定义在函数内部：

```typescript
// ❌ 错误：每次调用创建新实例
export function useHttpApi() {
    let isRefreshing = false;  // 每个组件一个实例
    let refreshingPromise = null;
    
    // ... 拦截器逻辑
}
```

**问题**：
- Vue/React 组件多次调用 `useHttpApi()` 会创建多个实例
- 不同组件间无法共享 `isRefreshing` 状态
- 仍然会出现并发刷新问题

## 💡 Promise 等待机制

### 核心思路

**使用模块级别的全局状态 + 单例 Promise**

```typescript
// ✅ 模块级别（所有组件共享）
let isRefreshing = false;
let refreshingPromise: Promise<string> | null = null;

export function useHttpApi() {
    // 所有组件使用同一个 isRefreshing 和 refreshingPromise
}
```

### 工作原理

```
页面加载 → 同时发起 3 个 API 请求
  ├─ GET /api/notifications/unread-count  → 401
  ├─ GET /api/user/profile                → 401
  └─ GET /api/messages/list               → 401

✅ 新方案：单次刷新 + Promise 等待
  ├─ 请求1：401 → 检查 isRefreshing=false → 开始刷新（创建 refreshingPromise）
  ├─ 请求2：401 → 检查 isRefreshing=true  → await refreshingPromise
  └─ 请求3：401 → 检查 isRefreshing=true  → await refreshingPromise
  
  └─ POST /api/auth/refresh-token (只调用1次) ✅
  
刷新完成后：
  ├─ refreshingPromise resolve(newToken)
  ├─ 请求1：用新 Token 重试 → 成功 ✅
  ├─ 请求2：用新 Token 重试 → 成功 ✅
  └─ 请求3：用新 Token 重试 → 成功 ✅
```

**优势**：
- ✅ **简洁**：不需要管理复杂的队列
- ✅ **直观**：所有请求直接 `await` 同一个 Promise
- ✅ **类型安全**：`Promise<string>` 有明确的类型约束
- ✅ **错误传播**：Promise 自带错误处理机制
- ✅ **易调试**：执行流程清晰，日志友好

## 🛠️ 核心实现

### 1. 全局状态定义

```typescript
// ===== 模块级别全局状态（httpApi.ts 顶部） =====
// Token刷新状态标志 - 防止并发刷新
let isRefreshing = false;

// Token刷新Promise - 用于等待正在进行的刷新
// 所有并发401请求都等待这个Promise
let refreshingPromise: Promise<string> | null = null;

// 登录过期通知显示状态 - 避免重复显示提示
let authExpiredNotificationShown = false;
```

**为什么在模块级别？**

JavaScript/TypeScript 模块系统保证：
- 模块在整个应用中只会被执行一次
- 模块级别的变量是全局单例
- 所有导入该模块的组件共享同一份状态

### 2. Axios 响应拦截器

完整的 401 错误处理逻辑：

```typescript
instance.value.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const originalRequest = error.config;

    // 处理401未授权错误
    if (error.response?.status === 401 && !originalRequest._retry) {
      // 标记请求已重试，避免无限循环
      originalRequest._retry = true;
      
      // 检查用户是否已登录（是否有token）
      const tokenData = await authStore.getToken();
      if (!tokenData || !tokenData.token) {
        return Promise.reject(new BusinessError(401, '用户没有权限，请先登录'));
      }

      // 🔑 关键点1：如果正在刷新token，等待刷新完成后重试
      if (isRefreshing && refreshingPromise) {
        console.log('⏳ Token正在刷新中，当前请求加入队列等待...', originalRequest.url);
        try {
          const newToken = await refreshingPromise;  // ← 等待同一个Promise
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return instance.value(originalRequest);
        } catch (err) {
          return Promise.reject(err);
        }
      }

      // 检查refreshToken是否过期
      if (tokenData.refreshTokenExpires < new Date()) {
        console.log('❌ RefreshToken已过期，需要重新登录');
        await authStore.clearAuthState();
        
        if (!authExpiredNotificationShown) {
          authExpiredNotificationShown = true;
          toast.showAuthExpiredToast({
            title: '登录已过期',
            message: 'RefreshToken已过期，请重新登录',
            onConfirm: () => {
              authExpiredNotificationShown = false;
              router.push('/login');
            },
          });
        }
        return Promise.reject(error);
      }

      // 🔑 关键点2：开始刷新token（只有第一个401请求会执行到这里）
      console.log('🔄 开始刷新Token...', originalRequest.url);
      isRefreshing = true;
      
      // 🔑 关键点3：创建刷新Promise，供后续请求等待
      refreshingPromise = (async () => {
        try {
          // 尝试刷新token
          const newToken = await authStore.refreshToken(
            tokenData.token, 
            tokenData.refreshToken
          );

          // 保存新token并更新登录状态
          await authStore.saveToken(newToken);
          await authStore.setUserLoggedIn(true);
          
          console.log('✅ Token刷新成功');
          return newToken.token;
        } catch (refreshError) {
          console.error('❌ Token刷新失败:', refreshError);
          
          // token刷新失败，清除本地认证状态
          await authStore.clearAuthState();
          
          if (!authExpiredNotificationShown) {
            authExpiredNotificationShown = true;
            toast.showAuthExpiredToast({
              title: '登录已过期',
              message: 'Token刷新失败，请重新登录',
              onConfirm: () => {
                authExpiredNotificationShown = false;
                router.push('/login');
              },
            });
          }
          throw refreshError;
        } finally {
          // 🔑 关键点4：清理状态（无论成功失败）
          isRefreshing = false;
          refreshingPromise = null;
        }
      })();
      
      // 🔑 关键点5：第一个请求也要等待刷新完成
      try {
        const newToken = await refreshingPromise;
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return instance.value(originalRequest);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }
    
    // 其他错误处理...
    return Promise.reject(error);
  }
);
```

## 🔍 工作流程详解

### 时序图

```
时间轴 ↓

T1: 请求1发出 ────→ 401 ────→ isRefreshing=false ────→ 创建refreshingPromise ────→ 调用refresh API
                                  ↓
                            isRefreshing=true

T2: 请求2发出 ────→ 401 ────→ isRefreshing=true ────→ await refreshingPromise (等待中...)

T3: 请求3发出 ────→ 401 ────→ isRefreshing=true ────→ await refreshingPromise (等待中...)

T4: refresh API响应 ────→ newToken ────→ refreshingPromise resolve(newToken)
                                              ↓
                                        isRefreshing=false
                                        refreshingPromise=null

T5: 请求2的await返回 ────→ 用newToken重试 ────→ 成功
    请求3的await返回 ────→ 用newToken重试 ────→ 成功
    请求1用newToken重试 ────→ 成功
```

### 状态变化

| 时刻 | isRefreshing | refreshingPromise | 说明 |
|------|-------------|-------------------|------|
| 初始 | false | null | 没有401错误 |
| 请求1收到401 | true | Promise(pending) | 开始刷新 |
| 请求2收到401 | true | Promise(pending) | 等待中 |
| 请求3收到401 | true | Promise(pending) | 等待中 |
| Token刷新成功 | false | null | 完成刷新 |

## 🎓 关键技术点

### 1. 为什么使用 Promise 而不是队列？

**队列方案**需要：
```typescript
let failedQueue = [];

// 加入队列
failedQueue.push({ resolve, reject, config });

// 处理队列
failedQueue.forEach(({ resolve, config }) => {
  resolve(retryRequest(config));
});
failedQueue = [];
```

**Promise 方案**只需：
```typescript
// 等待Promise
const newToken = await refreshingPromise;
```

**对比**：

| 特性 | 队列方案 | Promise 方案 |
|------|---------|-------------|
| 代码行数 | 30+ 行 | 10+ 行 |
| 概念复杂度 | 队列管理 + Promise | Promise |
| 类型安全 | 弱类型回调 | `Promise<string>` |
| 错误处理 | 手动处理每个回调 | Promise 自动传播 |
| 调试难度 | 高（队列状态不透明） | 低（清晰的 await） |

### 2. 为什么要标记 `_retry`？

```typescript
if (error.response?.status === 401 && !originalRequest._retry) {
    originalRequest._retry = true;
    // ... 刷新逻辑
}
```

**防止无限重试循环**：

```
场景：刷新Token也失败（返回401）

没有_retry标记：
请求 → 401 → 刷新Token → 401 → 再次刷新Token → 401 → ...（无限循环）

有_retry标记：
请求 → 401 → 刷新Token → 401 → _retry=true → 不再重试 → 抛出错误 ✅
```

### 3. 为什么第一个请求也要 await？

```typescript
// 创建Promise
refreshingPromise = (async () => {
  const newToken = await authStore.refreshToken(...);
  return newToken.token;
})();

// 🤔 为什么第一个请求还要await？
const newToken = await refreshingPromise;
```

**原因**：
1. **统一处理流程**：所有401请求的重试逻辑保持一致
2. **确保token已保存**：`await` 确保 `saveToken()` 已完成
3. **错误处理统一**：如果刷新失败，所有请求统一reject

**如果不await会怎样？**

```typescript
// ❌ 错误做法
refreshingPromise = authStore.refreshToken(...);
// 不等待，直接用旧token重试
return instance.value(originalRequest);  // 还是会401！
```

### 4. finally 块的重要性

```typescript
refreshingPromise = (async () => {
  try {
    const newToken = await authStore.refreshToken(...);
    return newToken.token;
  } catch (refreshError) {
    throw refreshError;
  } finally {
    // 🔑 无论成功失败，都要清理状态
    isRefreshing = false;
    refreshingPromise = null;
  }
})();
```

**为什么必须有 finally？**

- **成功场景**：清理状态，允许下次刷新
- **失败场景**：如果不清理，`isRefreshing` 永远为 `true`，后续请求会一直等待一个已失败的Promise

**如果没有 finally：**

```
刷新失败 → isRefreshing=true → 后续401请求 → await refreshingPromise（已reject）
→ 所有请求失败，但 isRefreshing=true → 下次刷新被阻止 → 应用卡死 ❌
```

## 📊 性能对比

### 场景：3个并发请求，Token已过期

#### 旧实现（并发刷新）

```
请求流程：
├─ Request1 → 401 → POST /api/auth/refresh (150ms) → Retry → Success (50ms)
├─ Request2 → 401 → POST /api/auth/refresh (150ms) → Retry → Success (50ms)
└─ Request3 → 401 → POST /api/auth/refresh (150ms) → Retry → Success (50ms)

总耗时：401检测(10ms) + 刷新(150ms) + 重试(50ms) = 210ms
网络请求：6次（3次刷新 + 3次重试）
服务器压力：3次刷新操作（重复）
```

#### 新实现（单次刷新 + Promise等待）

```
请求流程：
Request1 → 401 → POST /api/auth/refresh (150ms) → Retry → Success (50ms)
Request2 → 401 → await Promise ↓
Request3 → 401 → await Promise ↓
                                ↓
                        Promise resolved (newToken)
                                ↓
                        Request2 Retry → Success (50ms)
                        Request3 Retry → Success (50ms)

总耗时：401检测(10ms) + 刷新(150ms) + 重试(50ms) = 210ms
网络请求：4次（1次刷新 + 3次重试）
服务器压力：1次刷新操作
```

### 性能提升

| 指标 | 旧实现 | 新实现 | 提升 |
|------|-------|-------|------|
| 总耗时 | 210ms | 210ms | 相同 |
| 网络请求数 | 6次 | 4次 | **-33%** |
| 刷新API调用 | 3次 | 1次 | **-66%** |
| 服务器CPU | 高 | 低 | **-66%** |
| 数据库查询 | 3倍 | 1倍 | **-66%** |

### 大规模并发场景

假设页面同时发起 **10个API请求**，Token已过期：

| 指标 | 旧实现 | 新实现 | 提升 |
|------|-------|-------|------|
| 刷新API调用 | 10次 | 1次 | **-90%** |
| 网络请求总数 | 20次 | 11次 | **-45%** |
| 可能触发限流 | ⚠️ 是 | ✅ 否 | - |

## 🎨 实战效果

### 控制台日志对比

#### 优化前

```
🔄 开始刷新Token... /api/notifications/unread-count
🔄 开始刷新Token... /api/user/profile           ← 重复！
🔄 开始刷新Token... /api/messages/list          ← 重复！
✅ Token刷新成功
✅ Token刷新成功
✅ Token刷新成功
```

#### 优化后

```
🔄 开始刷新Token... /api/notifications/unread-count
⏳ Token正在刷新中，当前请求加入队列等待... /api/user/profile
⏳ Token正在刷新中，当前请求加入队列等待... /api/messages/list
✅ Token刷新成功                                 ← 只刷新1次！
🎉 请求重试成功: /api/notifications/unread-count
🎉 请求重试成功: /api/user/profile
🎉 请求重试成功: /api/messages/list
```

### 网络请求对比

#### 优化前

```
Timeline:
  0ms    100ms   200ms   300ms   400ms
  |------|------|------|------|------|
  GET /api/notifications → 401
  GET /api/user/profile → 401
  GET /api/messages/list → 401
         |
         POST /api/auth/refresh  ← 请求1触发
         POST /api/auth/refresh  ← 请求2触发（重复）
         POST /api/auth/refresh  ← 请求3触发（重复）
                |
                GET /api/notifications (retry)
                GET /api/user/profile (retry)
                GET /api/messages/list (retry)
```

#### 优化后

```
Timeline:
  0ms    100ms   200ms   300ms   400ms
  |------|------|------|------|------|
  GET /api/notifications → 401
  GET /api/user/profile → 401
  GET /api/messages/list → 401
         |
         POST /api/auth/refresh  ← 只调用1次！
         (请求2、3 await Promise...)
                |
                GET /api/notifications (retry)
                GET /api/user/profile (retry)
                GET /api/messages/list (retry)
```

## 🧪 测试场景

### 测试1：单个401请求

```typescript
// 场景：只有1个API请求，Token过期
test('单个401请求应该正常刷新Token', async () => {
  // 1. 发起请求 → 401
  const promise = httpApi.get('/api/user/profile');
  
  // 2. 应该触发刷新
  expect(isRefreshing).toBe(true);
  expect(refreshingPromise).not.toBeNull();
  
  // 3. 刷新完成
  await promise;
  
  // 4. 状态已清理
  expect(isRefreshing).toBe(false);
  expect(refreshingPromise).toBeNull();
});
```

### 测试2：并发401请求（核心场景）

```typescript
// 场景：3个并发请求，Token过期
test('并发401请求应该只刷新1次Token', async () => {
  // 1. 同时发起3个请求
  const promises = [
    httpApi.get('/api/notifications/unread-count'),
    httpApi.get('/api/user/profile'),
    httpApi.get('/api/messages/list'),
  ];
  
  // 2. 短暂延迟，让请求都收到401
  await new Promise(resolve => setTimeout(resolve, 10));
  
  // 3. 应该只调用1次刷新API
  expect(mockRefreshToken).toHaveBeenCalledTimes(1);
  
  // 4. 所有请求都应该成功
  const results = await Promise.all(promises);
  expect(results).toHaveLength(3);
  results.forEach(result => {
    expect(result).toBeDefined();
  });
});
```

### 测试3：RefreshToken过期

```typescript
// 场景：RefreshToken也过期了
test('RefreshToken过期应该显示登录过期提示', async () => {
  // 1. Mock过期的RefreshToken
  mockGetToken.mockResolvedValue({
    token: 'expired-token',
    refreshToken: 'expired-refresh-token',
    refreshTokenExpires: new Date('2020-01-01'), // 已过期
  });
  
  // 2. 发起请求
  try {
    await httpApi.get('/api/user/profile');
  } catch (error) {
    // 3. 应该提示重新登录
    expect(mockShowAuthExpiredToast).toHaveBeenCalledWith({
      title: '登录已过期',
      message: 'RefreshToken已过期，请重新登录',
      onConfirm: expect.any(Function),
    });
  }
});
```

### 测试4：刷新失败处理

```typescript
// 场景：刷新Token失败（网络错误）
test('Token刷新失败应该清理状态并提示', async () => {
  // 1. Mock刷新失败
  mockRefreshToken.mockRejectedValue(new Error('Network Error'));
  
  // 2. 同时发起3个请求
  const promises = [
    httpApi.get('/api/notifications'),
    httpApi.get('/api/user/profile'),
    httpApi.get('/api/messages'),
  ];
  
  // 3. 所有请求都应该失败
  await expect(Promise.all(promises)).rejects.toThrow();
  
  // 4. 状态应该已清理（允许下次重试）
  expect(isRefreshing).toBe(false);
  expect(refreshingPromise).toBeNull();
  
  // 5. 应该提示重新登录
  expect(mockShowAuthExpiredToast).toHaveBeenCalledTimes(1);
});
```

## 💡 最佳实践

### 1. 日志记录

添加详细的日志帮助调试：

```typescript
if (isRefreshing && refreshingPromise) {
  console.log('⏳ Token正在刷新中，当前请求加入队列等待...', originalRequest.url);
  // ...
}

console.log('🔄 开始刷新Token...', originalRequest.url);
// ...
console.log('✅ Token刷新成功');
```

日志规范：
- 🔄 表示开始操作
- ⏳ 表示等待中
- ✅ 表示成功
- ❌ 表示失败

### 2. 防止重复通知

```typescript
let authExpiredNotificationShown = false;

if (!authExpiredNotificationShown) {
  authExpiredNotificationShown = true;
  toast.showAuthExpiredToast({
    title: '登录已过期',
    message: 'Token刷新失败，请重新登录',
    onConfirm: () => {
      authExpiredNotificationShown = false; // 重置状态
      router.push('/login');
    },
  });
}
```

### 3. 错误分类处理

不同的401场景，错误提示应该不同：

```typescript
// 场景1：未登录
if (!tokenData || !tokenData.token) {
  return Promise.reject(new BusinessError(401, '用户没有权限，请先登录'));
}

// 场景2：RefreshToken过期
if (tokenData.refreshTokenExpires < new Date()) {
  toast.show({ message: 'RefreshToken已过期，请重新登录' });
}

// 场景3：刷新失败
catch (refreshError) {
  toast.show({ message: 'Token刷新失败，请重新登录' });
}
```

### 4. TypeScript 类型安全

```typescript
// ✅ 明确的类型定义
let refreshingPromise: Promise<string> | null = null;

// 使用时有类型提示
if (refreshingPromise) {
  const newToken: string = await refreshingPromise;  // 类型安全
}
```

### 5. 清理状态的时机

```typescript
// ✅ 使用 finally 确保清理
refreshingPromise = (async () => {
  try {
    return await authStore.refreshToken(...);
  } finally {
    isRefreshing = false;
    refreshingPromise = null;  // 允许下次刷新
  }
})();
```

## 🚀 进阶优化

### 优化1：取消已超时的请求

```typescript
// 为每个请求添加AbortController
const abortController = new AbortController();
config.signal = abortController.signal;

// Token刷新超过5秒，取消所有等待的请求
setTimeout(() => {
  if (isRefreshing) {
    console.warn('⚠️ Token刷新超时，取消等待的请求');
    abortController.abort('Token刷新超时');
  }
}, 5000);
```

### 优化2：预检查Token有效期

```typescript
// 在请求拦截器中，主动刷新即将过期的Token
instance.value.interceptors.request.use(async (config) => {
  const tokenData = await authStore.getToken();
  
  if (tokenData) {
    const expiresIn = tokenData.expires.getTime() - Date.now();
    
    // Token将在30秒内过期，主动刷新
    if (expiresIn < 30000 && !isRefreshing) {
      console.log('⏰ Token即将过期，主动刷新');
      isRefreshing = true;
      refreshingPromise = authStore.refreshToken(...);
      
      try {
        const newToken = await refreshingPromise;
        config.headers.Authorization = `Bearer ${newToken}`;
      } finally {
        isRefreshing = false;
        refreshingPromise = null;
      }
    }
  }
  
  return config;
});
```

### 优化3：指数退避重试

```typescript
// 如果刷新失败，使用指数退避重试
async function refreshTokenWithRetry(maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await authStore.refreshToken(...);
    } catch (error) {
      if (i === maxRetries - 1) throw error;
      
      // 指数退避：2^i * 1000ms
      const delay = Math.pow(2, i) * 1000;
      console.log(`🔄 刷新失败，${delay}ms后重试...`);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
}
```

## ⚠️ 常见陷阱

### 陷阱1：忘记清理状态

```typescript
// ❌ 错误：没有finally，失败后状态未清理
refreshingPromise = (async () => {
  try {
    return await authStore.refreshToken(...);
  } catch (error) {
    throw error;  // isRefreshing 仍然是 true！
  }
})();

// ✅ 正确：使用finally
refreshingPromise = (async () => {
  try {
    return await authStore.refreshToken(...);
  } finally {
    isRefreshing = false;
    refreshingPromise = null;
  }
})();
```

### 陷阱2：在函数内定义状态

```typescript
// ❌ 错误：每个组件一个实例
export function useHttpApi() {
  let isRefreshing = false;  // 多个实例，无法共享！
}

// ✅ 正确：模块级别
let isRefreshing = false;  // 全局唯一
export function useHttpApi() { ... }
```

### 陷阱3：不标记 _retry

```typescript
// ❌ 错误：没有标记，可能无限重试
if (error.response?.status === 401) {
  const newToken = await refreshToken();
  return axios(originalRequest);  // 如果还是401 → 无限循环
}

// ✅ 正确：标记已重试
if (error.response?.status === 401 && !originalRequest._retry) {
  originalRequest._retry = true;
  const newToken = await refreshToken();
  return axios(originalRequest);
}
```

### 陷阱4：第一个请求不 await

```typescript
// ❌ 错误：第一个请求不等待，直接重试
refreshingPromise = authStore.refreshToken(...);
return instance.value(originalRequest);  // 可能用的还是旧Token

// ✅ 正确：第一个请求也要await
refreshingPromise = authStore.refreshToken(...);
const newToken = await refreshingPromise;
originalRequest.headers.Authorization = `Bearer ${newToken}`;
return instance.value(originalRequest);
```

## 📚 总结

### 核心要点

1. **模块级别全局状态**：确保所有组件共享同一个 `isRefreshing` 和 `refreshingPromise`
2. **Promise 等待机制**：后续401请求直接 `await` 同一个 Promise，无需队列
3. **标记已重试**：使用 `_retry` 防止无限重试循环
4. **finally 清理**：无论成功失败，都要清理状态
5. **错误分类处理**：不同场景（未登录、过期、刷新失败）显示不同提示

### 优势总结

| 方面 | Promise等待机制 | 传统队列方案 |
|------|---------------|-------------|
| **代码行数** | 10-15行 | 30+行 |
| **概念复杂度** | 低（只有Promise） | 高（队列+Promise） |
| **类型安全** | ✅ `Promise<string>` | ⚠️ 弱类型回调 |
| **错误处理** | ✅ Promise自动传播 | ❌ 手动处理 |
| **调试难度** | ✅ 低（清晰的await） | ❌ 高（队列状态） |
| **性能** | ✅ 单次刷新 | ✅ 单次刷新 |
| **可维护性** | ✅ 高 | ⚠️ 中 |

### 适用场景

✅ **适合**：
- SPA应用的Token刷新
- 任何需要"单次操作+多个等待"的场景
- 需要类型安全和清晰代码的项目

❌ **不适合**：
- 需要精确控制每个请求的重试策略（使用队列更灵活）


