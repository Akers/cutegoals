# Bug bug-014: 兑换履约 UI 列表严重残缺 — 缺列、状态未中文化、无核销/取消按钮

| Field | Value |
|-------|-------|
| ID | bug-014 |
| Severity | **High** |
| Module | parent/exchanges（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-018（兑换管理页面） |
| Evidence | reports/evidence/P-019/exchanges-list.png |

## 复现步骤

1. 家长登录 → 导航到兑换管理页面 `/parent/exchanges`（P-018）
2. 查看兑换申请表（如有）或空白列表

## 期望行为

- 兑换申请表格应包含以下列：
  - **孩子**（孩子昵称）
  - **奖品**（奖品名称）
  - **积分**（消耗积分）
  - **状态**（中文：待核销 / 已核销 / 已取消）
  - **创建时间**
  - **操作**（待核销行有「核销」按钮；已取消行可「恢复」或保留）
- 状态值应为中文显示（而非原始的英文枚举值）

## 实际行为

- 列表行只有「**状态**」和「**操作**」两列
- 缺失列：
  - ❌ **孩子**（无 child nickname）
  - ❌ **奖品**（无 prize name 或 snapshot 信息）
  - ❌ **积分**（无 cost_points 展示）
  - ❌ **创建时间**（无 createdAt）
- 状态值未中文化：
  - `PENDING_FULFILLMENT` 显示英文 `fulfilled`（错误显示）
  - 应显示「待核销」「已核销」「已取消」
- 待核销（PENDING_FULFILLMENT）的行无「核销」按钮
- 无「取消」按钮（家长无法取消待核销的申请）

## 根因分析

### 前端 columns 定义问题
- 文件（推测）：`web/src/parent/pages/exchanges/index.tsx`
- Table columns 定义 incomplete，只定义了 `status` 和 `action` 两列
- 其他字段（孩子、奖品、积分、时间）根本没有映射到 columns

### 状态映射问题
- 状态枚举值（PENDING_FULFILLMENT / FULFILLED / CANCELLED）直接以英文展示
- 缺少中文化映射：`statusMap: { PENDING_FULFILLMENT: '待核销', FULFILLED: '已核销', CANCELLED: '已取消' }`
- `fulfilled`（拼写错误+语义错误）显示的是未映射前的原始值或错误映射

### 操作按钮缺失
- PENDING_FULFILLMENT 行的操作列没有「核销」按钮（`POST /api/exchanges/{id}/fulfill`）
- 没有「取消」按钮（`POST /api/exchanges/{id}/cancel`）

## 修复方向

### 推荐方案：重写 exchange list 组件

**步骤 1：完整 columns 定义**
```tsx
const columns = [
  { title: '孩子', dataIndex: 'childNickname', key: 'childNickname' },
  { title: '奖品', dataIndex: 'prizeName', key: 'prizeName' },
  { title: '消耗积分', dataIndex: 'costPoints', key: 'costPoints' },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    render: (status: string) => {
      const statusMap = {
        PENDING_FULFILLMENT: { text: '待核销', color: 'orange' },
        FULFILLED: { text: '已核销', color: 'green' },
        CANCELLED: { text: '已取消', color: 'red' },
      };
      const s = statusMap[status] || { text: status, color: 'default' };
      return <Tag color={s.color}>{s.text}</Tag>;
    },
  },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt',
    render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm') },
  {
    title: '操作',
    key: 'action',
    render: (_, record) => (
      <Space>
        {record.status === 'PENDING_FULFILLMENT' && (
          <Button type="primary" onClick={() => handleFulfill(record)}>核销</Button>
        )}
        {record.status === 'PENDING_FULFILLMENT' && (
          <Button danger onClick={() => handleCancel(record)}>取消</Button>
        )}
        {record.status === 'FULFILLED' && (
          <Tag>已核销</Tag>
        )}
      </Space>
    ),
  },
];
```

**步骤 2：数据获取**
- 调 `GET /api/exchanges` 获取列表
- 如需 childNickname 和 prizeName，检查后端返回中是否已包含（后端可能只返回 childId/prizeId）
- 如果后端未返回，需在 exchange list API 中 join child_profile 和 prize

**步骤 3：核销/取消按钮事件**
```tsx
const handleFulfill = async (record: Exchange) => {
  await fulfillExchange(record.id);
  message.success('兑换已核销');
  refreshList();
};

const handleCancel = async (record: Exchange) => {
  Modal.confirm({
    title: '确认取消',
    content: `确定取消「${record.prizeName}」兑换申请吗？`,
    onOk: async () => {
      await cancelExchange(record.id);
      message.success('兑换申请已取消');
      refreshList();
    },
  });
};
```

### 后端如有必要补充字段
如果 `GET /api/exchanges` 当前不返回 `childNickname` 和 `prizeName`：
- 在 `ExchangeResponse` DTO 中添加 `childNickname`（JOIN child_profile）
- 在 `ExchangeResponse` DTO 中添加 `prizeName`（从 exchange_snapshot 或 JOIN prize）

## 影响范围

- **阻塞用例**：P-018（兑换管理页面 — 无法查看完整信息、无法核销/取消）
- **关联模块**：
  - `web/src/parent/pages/exchanges/index.tsx` — 核心修复文件
  - `web/src/parent/components/ExchangeFulfillModal.tsx` — 可能需要核销确认模态框
  - `server/exchange/.../dto/ExchangeResponse.java` — 如需补充 childNickname/prizeName
  - `server/exchange/.../service/ExchangeService.java` — 如需 SQL join

## 回归测试要点

1. **单 bug 回归**：
   - 列表展示「孩子」「奖品」「积分」「状态」「创建时间」全部列
   - 状态显示「待核销」「已核销」「已取消」（中文）
   - PENDING_FULFILLMENT 行有「核销」「取消」按钮
   - FULFILLED 行无按钮（显示「已核销」标签）
2. **流程回归**：待核销 → 核销 → 状态变为 FULFILLED，积分扣减，库存扣减
3. **幂等回归**：FULFILLED 行再次点击核销无效（后端幂等）
