---
change: fix-parent-family-points-endpoints
design-doc: openspec/changes/fix-parent-family-points-endpoints/design.md
base-ref: dd8360080ebfb10a7305261792e5ae793a847408
---

# fix-parent-family-points-endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 修复 parent 端「家庭」与「积分」菜单的资源未找到/404 报错：在 JWT filter 中注入 familyId，并避免积分页发起空路径请求。

**Architecture:** 在 `WebSecurityConfig.jwtAuthenticationFilter` 解析 token 后，使用 `FamilyMemberMapper` 查询账号所属 familyId 并写入请求属性；在前端 `ParentPointsPage` 中对未选孩子时跳过 `useApi` 请求。

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, React 18, TypeScript.

## Global Constraints

- 不新增 capability、不修改 API 契约、不动数据库 schema。
- 后端以 Maven 构建为最终正确性来源，Lombok LSP 报错可忽略。
- 前端 `npm test` 失败数需与 baseline（14 failed / 65 passed）一致。
- Commit message 格式：`fix: <简述>` 或 `fix(parent): <简述>`。

## Files

- **Modify**: `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java` — 注入 `FamilyMemberMapper`，在 JWT filter 中设置 `ATTR_FAMILY_ID`。
- **Modify**: `web/src/parent/pages/index.tsx` — `ParentPointsPage` 的 `useApi` 增加 `skip` 选项。

---

### Task 1: 在 WebSecurityConfig 中注入 FamilyMemberMapper

**Files:**
- Modify: `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`

**Interfaces:**
- Consumes: `com.cutegoals.auth.mapper.FamilyMemberMapper` (from auth module, already on web classpath).
- Produces: `private final FamilyMemberMapper familyMemberMapper;` added to constructor-injected fields.

- [x] **Step 1: Add import and field**

Add import near existing auth imports:

```java
import com.cutegoals.auth.mapper.FamilyMemberMapper;
```

Add field and constructor injection:

```java
private final FamilyMemberMapper familyMemberMapper;
```

- [x] **Step 2: Verify compile**

Run: `mvn -pl :web -am compile -q`
Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
git add server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java
git commit -m "chore: inject FamilyMemberMapper into WebSecurityConfig"
```

---

### Task 2: 在 JWT filter 中设置 familyId

**Files:**
- Modify: `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`

**Interfaces:**
- Consumes: `claims.accountId()`, `claims.roles()`, `AuthConstants.ATTR_FAMILY_ID`, `FamilyMemberMapper.findByAccountIdAndRole`.
- Produces: `request.setAttribute(AuthConstants.ATTR_FAMILY_ID, familyMember.getFamilyId())` when a member exists.

- [x] **Step 1: Add familyId lookup after token parsing**

In `jwtAuthenticationFilter()`, after the existing `request.setAttribute(...)` lines, add:

```java
if (!claims.roles().isEmpty()) {
    familyMemberMapper.findByAccountIdAndRole(claims.accountId(), claims.roles().get(0))
            .ifPresent(member -> request.setAttribute(AuthConstants.ATTR_FAMILY_ID, member.getFamilyId()));
}
```

- [x] **Step 2: Verify compile**

Run: `mvn -pl :web -am compile -q`
Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
git add server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java
git commit -m "fix(parent): set familyId in JWT filter for family-scoped endpoints"
```

---

### Task 3: 积分页跳过空路径请求

**Files:**
- Modify: `web/src/parent/pages/index.tsx`

**Interfaces:**
- Consumes: `useApi` options `{ skip: boolean }`.
- Produces: `useApi` call no longer fires when `selectedChild` is empty.

- [x] **Step 1: Add skip option**

Locate `ParentPointsPage`:

```typescript
const { data, loading, error, refetch } = useApi<{
  balance: number;
  transactions: { id: number; amount: number; type: string; createdAt: string; reason?: string }[];
}>(selectedChild ? `/points/ledger/${selectedChild}` : '');
```

Change to:

```typescript
const { data, loading, error, refetch } = useApi<{
  balance: number;
  transactions: { id: number; amount: number; type: string; createdAt: string; reason?: string }[];
}>(selectedChild ? `/points/ledger/${selectedChild}` : '', { skip: !selectedChild });
```

- [x] **Step 2: Verify TypeScript**

Run: `cd web && npx tsc -b`
Expected: 0 errors.

- [x] **Step 3: Commit**

```bash
git add web/src/parent/pages/index.tsx
git commit -m "fix(parent): skip empty points ledger request until child selected"
```

---

### Task 4: 验证后端测试基线

**Files:** None.

- [x] **Step 1: Run backend tests**

Run: `mvn -pl :web -am test -q`
Expected: BUILD SUCCESS, baseline tests pass.

- [x] **Step 2: Commit (if no changes, just note)**

No source changes to commit. Mark task complete.

---

### Task 5: 验证前端测试基线

**Files:** None.

- [x] **Step 1: Run frontend tests**

Run: `cd web && npm test -- --run`
Expected: 14 failed / 65 passed (same as baseline), 0 new failures.

- [x] **Step 2: Commit (if no changes, just note)**

No source changes to commit. Mark task complete.

---

### Task 6: 根因消除检查

**Files:** None.

- [x] **Step 1: Confirm no remaining empty-path useApi calls**

Search: `grep -n "useApi<.*>\(''" web/src/parent/pages/index.tsx`
Expected: 0 matches.

- [x] **Step 2: Confirm ATTR_FAMILY_ID is set in JWT filter**

Search: `grep -n "ATTR_FAMILY_ID" server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`
Expected: at least 1 match (setAttribute).

- [x] **Step 3: Mark complete**

No commit needed.

---

### Task 7: 推进 build guard

**Files:** None.

- [x] **Step 1: Ensure all tasks in tasks.md are checked**

- [x] **Step 2: Run build guard**

Run: `node /home/akers/projects/cutegoals/.opencode/skills/comet/scripts/comet-guard.mjs fix-parent-family-points-endpoints build --apply`
Expected: ALL CHECKS PASSED, phase=verify.

- [x] **Step 3: Commit any remaining state changes**

If `.comet.yaml` or `.comet/` files changed, commit them.
