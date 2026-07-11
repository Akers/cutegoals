# CuteGoals 2.0 容量与性能测试报告

> **文档版本**: 1.0  
> **生成日期**: 2026-07-11  
> **任务**: 9.7 容量与性能测试  
> **参考环境**: 2 vCPU, 4 GB RAM, 10 万条业务记录, 20 并发会话  

---

## 1. 测试目标

| 指标 | 目标 | 测量方法 |
|---|---|---|
| 核心 API P95 延迟 | ≤ 300ms | k6 / JMeter HTTP 压测 |
| 前端 LCP | ≤ 2.5s | Lighthouse / Playwright Performance API |
| 10 万条数据分页 | 有界且稳定 | 数据库查询计划 + API 分页 |
| 并发会话 20 个 | 无资源耗尽 | 并发负载测试 |

## 2. 测试环境

- **CPU**: 2 vCPU (AMD64)
- **内存**: 4 GB
- **数据库**: PostgreSQL 16 (Docker)
- **缓存**: Redis 7 (Docker)
- **应用**: Spring Boot 3.2, Java 21
- **压测工具**: k6 (推荐) 或 JMeter

## 3. 压测场景

### 3.1 核心 API 性能测试

| 端点 | 方法 | 业务场景 | 目标 P95 |
|---|---|---|---|
| `/api/health` | GET | 健康检查 | ≤ 50ms |
| `/api/auth/login` | POST | 家长登录 | ≤ 200ms |
| `/api/family` | GET | 查询家庭信息 | ≤ 200ms |
| `/api/task-assignments?childId=X` | GET | 查询孩子任务 | ≤ 300ms |
| `/api/task-review/submissions` | POST | 孩子提交任务 | ≤ 300ms |
| `/api/task-review/:id/approve` | POST | 批准任务 | ≤ 300ms |
| `/api/exchanges/direct` | POST | 直接兑换 | ≤ 300ms |
| `/api/exchanges/blind-box` | POST | 盲盒兑换 | ≤ 300ms |
| `/api/points/balance/:childId` | GET | 积分余额 | ≤ 200ms |
| `/api/admin/audit-logs` | GET | 审计日志分页 | ≤ 300ms |

### 3.2 并发压力测试

```bash
# k6 压测脚本示例
# 20 个并发虚拟用户，持续 60 秒
k6 run --vus 20 --duration 60s scripts/load-test.js
```

### 3.3 大数据量分页测试

- 预置 100,000 条积分流水记录
- 测试分页查询第 1 页、第 1000 页、最后一页
- 验证每个分页响应 ≤ 300ms

## 4. 测试脚本示例

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 20,
  duration: '60s',
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  // 健康检查
  let res = http.get(`${BASE_URL}/api/health`);
  check(res, { 'health OK': (r) => r.status === 200 });

  // 积分查询
  res = http.get(`${BASE_URL}/api/points/balance/1`);
  check(res, { 'balance OK or auth': (r) => r.status < 500 });

  sleep(1);
}
```

## 5. 测试执行记录

| 日期 | 环境 | 数据量 | 并发数 | P95 | 状态 |
|---|---|---|---|---|---|
| TBD | TBD | TBD | TBD | TBD | ⏳ 待执行 |

## 6. 前端性能预算

| 页面 | LCP 目标 | 实际 | 状态 |
|---|---|---|---|
| 家长首页 | ≤ 2.5s | TBD | ⏳ |
| 孩子任务看板 | ≤ 2.5s | TBD | ⏳ |
| 积分商城 | ≤ 2.5s | TBD | ⏳ |
| 管理员面板 | ≤ 2.5s | TBD | ⏳ |

## 7. 结论

⏳ **待完成**: 性能测试需要在具备参考容量的环境中执行。  
当前环境满足 Docker 可用条件，可运行 Compose 烟雾测试；  
完整性能测试需待目标环境就绪。

### 待办事项

- [ ] 安装 k6 或配置 JMeter
- [ ] 实现压测脚本 `scripts/load-test.js`
- [ ] 在 2 vCPU / 4 GB 环境执行压测
- [ ] 记录结果并更新本报告
