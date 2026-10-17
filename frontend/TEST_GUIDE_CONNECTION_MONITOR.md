# 后端连接异常自动登出功能 - 测试指南

## 功能概述

已实现后端连接异常检测机制，当检测到连续网络错误超过 **30秒** 时，系统将自动：
1. 清除本地存储的用户认证信息（token、userInfo）
2. 显示友好的错误提示消息
3. 自动重定向至登录页面

## 涉及文件

### 新增文件
- `src/services/connectionMonitor.ts` - 连接状态监控核心服务
- `src/services/networkStatus.ts` - 浏览器网络状态监听器

### 修改文件
- `src/api/request.ts` - 集成连接监控
- `src/main.ts` - 初始化网络状态监听器
- `src/services/index.ts` - 导出新服务
- `vite.config.ts` - 调整前端端口（3000 → 3001）

## 测试场景

### 场景1: 正常连接 → 网络中断（30秒内恢复）

**测试步骤：**
1. 启动前后端服务
2. 使用管理员账号登录系统
3. 在Dashboard页面进行正常操作
4. **断开网络**（例如：禁用网卡/关闭Wi-Fi）
5. 等待 **< 30秒**
6. **恢复网络连接**
7. 观察系统行为

**预期结果：**
- 显示警告消息："检测到网络连接不稳定，正在尝试重新连接..."
- 网络恢复后，显示成功消息："网络连接已恢复"
- 用户可以继续正常操作，无需重新登录

---

### 场景2: 正常连接 → 网络中断（超过30秒未恢复）

**测试步骤：**
1. 启动前后端服务
2. 使用管理员账号登录系统
3. 导航到任意页面（如知识库管理）
4. **断开网络连接**
5. 持续操作页面，触发多次API请求失败
6. **等待30秒以上**
7. 观察系统自动登出行为

**预期结果：**
- 首次网络错误：显示警告 "检测到网络连接不稳定，正在尝试重新连接..."
- 持续错误：计时器开始倒计时
- 30秒后：
  - 显示错误消息："服务连接超时，为保护您的账户安全，已自动退出登录"
  - 自动清除 localStorage 中的 token 和 userInfo
  - 自动跳转至登录页面
  - 控制台输出：`[ConnectionMonitor] 后端服务连接异常超过30秒，执行自动登出`

---

### 场景3: 服务器宕机/无响应

**测试步骤：**
1. 启动前端服务（不启动后端）
2. 使用账号登录（如果前端缓存了token）
3. 尝试执行任意需要后端API的操作
4. 观察连接超时错误
5. 等待30秒

**预期结果：**
- API请求超时（30秒axios timeout）
- 触发 connectionMonitor 记录错误
- 30秒后自动登出机制生效

---

### 场景4: Token过期 + 网络正常

**测试步骤：**
1. 登录系统
2. 手动删除 localStorage 中的 token（或等待token过期）
3. 执行需要认证的API请求
4. 观察401处理

**预期结果：**
- 原有逻辑保持不变：显示提示并跳转登录页
- 不会触发30秒超时机制

---

## 测试工具与方法

### 方法1: 使用浏览器开发者工具

1. 打开浏览器开发者工具（F12）
2. 切换到 **Network** 标签
3. 将网络设置为 **Offline**（离线模式）
4. 执行页面操作，触发API请求
5. 观察控制台输出和页面行为

### 方法2: 使用命令行禁用网络（Linux/macOS）

```bash
# 断开网络
sudo ifconfig en0 down  # macOS
# 或
sudo ip link set eth0 down  # Linux

# 恢复网络
sudo ifconfig en0 up
```

### 方法3: 使用代理工具模拟超时

使用 Fiddler/Charles 工具：
1. 启动代理工具
2. 设置断点或延迟规则
3. 将所有API请求延迟 > 30秒 或 直接返回超时错误

---

## 关键代码逻辑

### ConnectionMonitor 工作流程

```
1. recordNetworkError()
   ├─ 如果是首次错误：记录firstErrorTime，启动30秒计时器
   └─ 如果不是首次错误：更新firstErrorTime（重置计时器）

2. recordNetworkSuccess()
   └─ 重置所有状态（停止计时器）

3. 30秒计时器到期
   ├─ 检查用户是否已登录
   ├─ 检查当前是否在登录页
   ├─ 清除认证信息
   ├─ 显示错误消息
   └─ 跳转登录页
```

### 网络错误检测规则

```typescript
// 以下情况会触发 connectionMonitor.recordNetworkError()
const isTimeout = error.code === 'ECONNABORTED' || error.message.includes('timeout')
const isNetworkFailure = !navigator.onLine || error.code === 'ERR_NETWORK' || error.code === 'ERR_CONNECTION_REFUSED'

if (isTimeout || isNetworkFailure) {
  connectionMonitor.recordNetworkError()
}
```

---

## 调试日志

开启浏览器控制台，观察以下日志：

### ConnectionMonitor 日志
```
[ConnectionMonitor] 后端服务连接异常超过30秒，执行自动登出
```

### NetworkStatusListener 日志
```
[NetworkStatusListener] 网络状态监听器已初始化
[NetworkStatusListener] 网络连接已恢复
[NetworkStatusListener] 检测到网络连接已断开
```

### Request Interceptor 日志
```
[Network Error] 网络连接失败，请检查网络 xxx
```

---

## 注意事项

1. **30秒阈值是硬编码的**：如果需要调整，修改 `CONNECTION_ERROR_THRESHOLD` 常量（毫秒）

2. **错误消息不会重复显示**：首次网络错误会显示警告，30秒内不会再次显示

3. **登录页不触发登出**：如果在登录页检测到网络错误，不会触发自动登出逻辑

4. **token验证优先级**：401错误会立即处理，不会等待30秒

5. **浏览器网络事件**：
   - `window.online` - 浏览器检测到网络恢复
   - `window.offline` - 浏览器检测到网络断开

---

## 性能与安全考虑

### 性能
- 使用 `setTimeout` 而非 `setInterval`，避免不必要的定时器开销
- 状态对象使用类实例而非全局变量，减少内存泄漏风险
- 网络恢复时立即清理计时器

### 安全
- 自动登出时清除所有认证信息
- 延迟2秒跳转，确保错误消息有足够时间显示
- 不在URL中传递敏感信息
- 登录页不显示敏感数据

---

## 常见问题

**Q: 为什么设置为30秒而不是更短？**
A: 30秒是一个合理的阈值，足够容忍短暂的网络波动（如路由器重启、切换Wi-Fi等），同时也不会让用户等待过长时间。

**Q: 如果用户在30秒内多次尝试操作会怎样？**
A: 每次网络错误都会重置计时器的起始时间，从最后一次错误开始计算30秒。

**Q: 能否禁用这个功能？**
A: 目前不支持动态禁用。如需禁用，可以注释掉 `request.ts` 中的 `connectionMonitor.recordNetworkError()` 调用。

**Q: 这个功能会影响正常的登录流程吗？**
A: 不会。登录页面的请求失败不会触发自动登出机制，只有已登录用户在非登录页检测到网络错误时才会触发。
