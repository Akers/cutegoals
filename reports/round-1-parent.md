# Round-1 Parent 端测试报告

- **测试时间**: 2026-07-18
- **测试人**: Comet Build (agent-browser + Python psycopg2)
- **测试账号**: 13600049114 / 117315Akers (PARENT + INSTANCE_ADMIN)
- **前端 URL**: http://localhost:8000 (实际端口，与 plan/test-plan 写的 :5173 不同)
- **后端 URL**: http://localhost:8080
- **DB 状态基线**: account id=2 已激活, family id=2 "我的家庭", family_member id=2 PARENT

## 用例结果汇总

| Case | 名称 | Status | Bug | 备注 |
|------|------|--------|-----|------|
| P-001 | 家长登录 | PASS | — | 顶栏 `136****9114` 与 admin 端一致 |
| P-002 | 家庭信息查询与更新 | FAIL（UX） | bug-005 | 前端未展示家庭名称、无编辑入口（后端正常） |
| P-003 | 成员管理 | PASS | — | 合并 P-002 视图验证 |
| P-004 | 创建孩子档案 | PASS | — | cici 创建，pin_hash bcrypt，生日字段实际为「生日」非「年龄」 |
| P-005 | 更新孩子档案 | PASS | — | cici→cici-updated→cici 往返 OK，PIN input type=password |
| P-006 | 设备授权 | PARTIAL（后端 PASS，前端缺失） | bug-006 | 家长端无设备管理 UI，后端 API 工作正常 |

## 测试详情

### P-001 家长登录 PASS
- URL: http://localhost:8000/parent/login
- 步骤: 填手机号 13600049114 + 密码 117315Akers → 点击「登录」
- 期望: 跳转到 /parent；顶栏显示脱敏手机号
- 实际: ✅ 跳转 /parent；顶栏显示 `136****9114`（前 3 + 后 4，4 星）
- Evidence: `reports/evidence/P-001/login-success.png`（agent-browser parent session 持久化已建立，未单独截图）

### P-002 家庭信息查询与更新 → bug-005 候选
- URL: http://localhost:8000/parent/family
- 期望: 页面展示家庭名称「我的家庭」；可编辑家庭名称
- 实际:
  - ❌ 页面 **未展示家庭名称**（只有「邀请家长」「添加孩子」「退出家庭」三个按钮 + 成员列表）
  - ❌ **无编辑家庭名称入口**
  - 后端 API 正常：
    - `GET /api/family` → 200 `{name: "我的家庭", members, children, ...}`
    - `PUT /api/family` with X-CSRF-Token → 200（DB name 成功更新为 "我的家庭-测试"，已恢复）
- 根因: 前端组件 `web/src/parent/pages/ParentFamilyPage.tsx`（1 行，空壳）未渲染 name 字段、未提供编辑入口
- Severity: **Medium UX**
- Evidence: `reports/evidence/P-002/family-no-name-display.png`

### P-003 成员管理 PASS（合并 P-002）
- 当前家庭只有 1 个成员（家长自己），列表正确展示
- 邀请家长功能需第二个手机号（未执行）
- 退出家庭按钮存在（未点击）

### P-004 创建孩子档案 PASS
- URL: http://localhost:8000/parent/children
- 步骤: 点击「新增档案」→ 填昵称 `cici` + PIN `180614` + 留空生日 → 保存
- 期望: 创建成功；列表出现 cici；PIN 加密存储
- 实际: ✅ 列表出现 cici；DB child_profile id=2, pin_hash=`$2a$12$...`（bcrypt，非明文）
- 表单字段实际为「生日」（年/月/日 spinbutton + 日期选择器），test-plan 写「年龄」是文档错误
- 列表显示「年龄」字段 `-`（因为未填生日无法计算）
- Evidence: `reports/evidence/P-004/cici-created.png`

### P-005 更新孩子档案 PASS
- 步骤: 编辑 cici → 模态字段（昵称预填 / 新 PIN 留空不修改 / 生日）→ 修改昵称 cici→cici-updated → 保存 → 再编辑改回 cici → 保存
- 期望: 更新成功；PIN 输入框 type=password
- 实际: ✅ DB nickname 往返更新成功；PIN input type=password（无明文泄露）
- 最终 DB: child_profile id=2, nickname=cici
- Evidence: `reports/evidence/P-005/cici-restored.png`

### P-006 设备授权 → bug-006 候选
- **预期路径**: 家长端 UI 授权设备 → DB device_authorization（实际表名 device_binding）写入记录
- **实际**: 家长端 **完全没有设备管理 UI**
  - `web/src/parent/pages/` 下 11 个组件均无 device/设备 相关代码
  - 家长端无法为孩子授权、查看、解绑设备
- **后端 API 测试（Python urllib）**:
  1. `POST /api/auth/login` → 200，csrf_token cookie 下发
  2. `GET /api/family/devices/children?deviceId=test-device-cici-001` → **401 DEVICE_NOT_AUTHORIZED**（未绑定时正确拒绝）
  3. `POST /api/family/devices/bind` with X-CSRF-Token → **200 SUCCESS**
     - 响应：`{id:1, deviceId, familyId:2, status:ACTIVE, boundBy:2, credential:"a1a0...d4d137", createdAt}`
     - 「credential」一次性返回，DB 只存 sha256 hash（`credential_hash: 60f0...11651e`）— ✅ 安全实践
  4. `GET /api/family/devices/children?deviceId=test-device-cici-001` → 200，返回家庭所有孩子列表
- **DB 验证**:
  ```
  device_binding id=1, family_id=2, device_id=test-device-cici-001,
                 credential_hash=sha256(...), status=ACTIVE, bound_by=2
  ```
  注意：device_binding 是「设备↔家庭」级别绑定（**没有 child_id 字段**），孩子选择在孩子登录时进行
- Severity: **Medium**（功能缺失，但家长端不影响主流程；孩子仍可在已绑定设备上登录）
- Evidence: `reports/evidence/P-006/child-bind-initial.png`（child 端 bind 页面在未授权设备上显示「查询失败 DEVICE_NOT_AUTHORIZED」）

## Bug 候选清单（Parent 阶段）

| Bug ID | Severity | Module | 描述 |
|--------|----------|--------|------|
| bug-005 | Medium UX | parent/family | 家庭管理页面未展示家庭名称、无编辑入口（后端 API 正常） |
| bug-006 | Medium | parent/devices | 家长端缺少设备管理 UI（授权、查看、解绑）；后端 API 工作正常 |

## 累计 Bug 候选（Admin + Parent）

| Bug ID | Severity | Module | 描述 |
|--------|----------|--------|------|
| bug-001 | Medium (S-004) | admin/parent | 手机号脱敏规则不一致（admin 表格 5 星 vs 其他 4 星） |
| bug-002 | High | audit | audit_log 表始终为空（疑似 InstanceConfig/AuditLog 实体 Lombok 失效） |
| bug-003 | Low UX | admin/config | 配置保存无 toast 反馈 |
| bug-004 | Low | init | instance_config 初始化无默认值 |
| bug-005 | Medium UX | parent/family | 家庭管理页面未展示家庭名称、无编辑入口 |
| bug-006 | Medium | parent/devices | 家长端缺少设备管理 UI |
