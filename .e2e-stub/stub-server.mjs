/**
 * Contract stub backend for CuteGoals console e2e tests.
 * Listens on :8080 (vite dev server proxies /api here).
 * Only Node built-ins. Not committed.
 */
import http from 'node:http';

const PORT = 8080;
const PARENT_PHONE = '13800001234';
const PARENT_PASSWORD = 'E2eTest#2026';
const SESSION_COOKIE = 'access_token';
const SESSION_VALUE = 'stub-session-e2e';

function envelope(res, status, code, data, message = 'ok') {
  const body = JSON.stringify({ code, message, data, request_id: 'stub-req-' + Math.random().toString(36).slice(2) });
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}
const ok = (res, data) => envelope(res, 200, 'SUCCESS', data);
const fail = (res, status, code, message) => envelope(res, status, code, null, message);

function readCookies(req) {
  const header = req.headers.cookie || '';
  const out = {};
  for (const part of header.split(';')) {
    const idx = part.indexOf('=');
    if (idx > -1) out[part.slice(0, idx).trim()] = decodeURIComponent(part.slice(idx + 1).trim());
  }
  return out;
}

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// ── fixture data ──
const TODAY = todayStr();
const childrenPage = {
  content: [
    { id: 1, familyId: 1, nickname: '豆豆', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    { id: 2, familyId: 1, nickname: '果果', status: 'ACTIVE', createdAt: '2026-01-02T00:00:00Z' },
  ],
  page: 1, pageSize: 20, totalElements: 2, totalPages: 1,
};
const templatesPage = {
  content: [
    {
      id: 1, name: '阅读30分钟', category: 'STUDY', taskType: 'LIMITED', typeConfig: '{}',
      enabled: true, deleted: false, version: 0, familyId: 1, createdAt: '2026-01-01T00:00:00Z',
      difficulties: [
        { id: 11, templateId: 1, name: '简单', rewardPoints: 5, enabled: true },
        { id: 12, templateId: 1, name: '标准', rewardPoints: 10, enabled: true },
      ],
    },
    {
      id: 2, name: '每日练琴', category: 'STUDY', taskType: 'REPEAT',
      typeConfig: JSON.stringify({ frequency: 'DAILY' }),
      enabled: true, deleted: false, version: 0, familyId: 1, createdAt: '2026-01-01T00:00:00Z',
      difficulties: [{ id: 21, templateId: 2, name: '标准', rewardPoints: 8, enabled: true }],
    },
  ],
  page: 1, pageSize: 20, totalElements: 2, totalPages: 1,
};
function assignment(id, tplName, type, status) {
  return {
    id, childId: 1, templateId: 1, difficultyId: 11, status,
    deadline: TODAY + 'T23:59:59', completedAt: null, overdue: false, version: 0,
    snapshotTemplateName: tplName,
    snapshotTemplateTaskType: type,
    snapshotTemplateTypeConfig: type === 'REPEAT' ? JSON.stringify({ frequency: 'DAILY' }) : null,
    snapshotDifficultyReward: 10,
    submissionCount: 0, maxSubmissions: 1,
    createdAt: '2026-01-01T00:00:00Z',
  };
}
const assignmentsPage = {
  content: [
    assignment(101, '阅读30分钟', 'LIMITED', 'APPROVED'),
    assignment(102, '每日练琴', 'REPEAT', 'PENDING'),
    assignment(103, '整理书桌', 'STANDING', 'SUBMITTED'),
  ],
  page: 1, pageSize: 100, totalElements: 3, totalPages: 1,
};
function calendarData(year, month) {
  const days = {};
  // put tasks on today and a few other days of the requested month
  const now = new Date();
  if (now.getFullYear() === year && now.getMonth() + 1 === month) {
    days[TODAY] = mkDay(3);
  }
  for (const d of [3, 8, 15, 22]) {
    const key = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    if (!days[key]) days[key] = mkDay(d % 4 + 1);
  }
  return { year, month, days };
}
function mkDay(total) {
  return {
    total, pending: total, submitted: 0, approved: 0, rejected: 0, cancelled: 0, overdue: 0,
    taskTypes: { LIMITED: total, REPEAT: 0, STANDING: 0 },
  };
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}); } catch { resolve({}); }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname.replace(/^\/api(?=\/)/, '');
  const method = req.method;
  const cookies = readCookies(req);
  const authed = cookies[SESSION_COOKIE] === SESSION_VALUE;

  console.log(`[stub] ${method} ${path}`);

  // ── health ──
  if (method === 'GET' && path === '/health') {
    return ok(res, { status: 'UP' });
  }

  // ── auth ──
  if (method === 'POST' && path === '/auth/login') {
    const body = await readBody(req);
    if (body.phone === PARENT_PHONE && body.password === PARENT_PASSWORD) {
      res.setHeader('Set-Cookie', [
        `${SESSION_COOKIE}=${SESSION_VALUE}; Path=/; HttpOnly; SameSite=Lax`,
        `refresh_token=stub-refresh-${Date.now()}; Path=/; HttpOnly; SameSite=Lax`,
        `csrf_token=stub-csrf-token; Path=/; SameSite=Lax`,
      ]);
      return ok(res, {
        accountId: 1, phone: PARENT_PHONE, roles: ['PARENT'],
        familyId: 1, expiresIn: 3600,
      });
    }
    return fail(res, 401, 'AUTH_INVALID_CREDENTIALS', '手机号或密码错误');
  }

  if (method === 'GET' && path === '/auth/me') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, { accountId: 1, roles: ['PARENT'], childId: null, familyId: 1 });
  }

  if (method === 'POST' && path === '/auth/logout') {
    res.setHeader('Set-Cookie', `${SESSION_COOKIE}=; Path=/; Max-Age=0`);
    return ok(res, null);
  }

  if (method === 'POST' && path === '/auth/refresh') {
    return fail(res, 401, 'UNAUTHORIZED', '刷新令牌无效');
  }

  if ((method === 'PUT' || method === 'POST') && path === '/auth/password') {
    // CSRF protected: require session + X-CSRF-TOKEN header
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    if (req.headers['x-csrf-token'] !== 'stub-csrf-token') {
      return fail(res, 403, 'CSRF_INVALID', 'CSRF 令牌缺失或无效');
    }
    return ok(res, null);
  }

  // ── parent data endpoints (session required) ──
  if (path === '/task-assignments' && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, assignmentsPage);
  }
  if (path === '/task-assignments/calendar' && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, calendarData(Number(url.searchParams.get('year')), Number(url.searchParams.get('month'))));
  }
  if (path === '/task-assignments' && method === 'POST') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, assignment(200, '新任务', 'LIMITED', 'PENDING'));
  }
  if (path === '/task-assignments/batch' && method === 'POST') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, []);
  }
  if (path === '/task-templates' && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, templatesPage);
  }
  if (path === '/task-templates' && method === 'POST') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    const body = await readBody(req);
    if (!body.name) return fail(res, 400, 'VALIDATION_ERROR', '标题不能为空');
    const created = {
      id: templatesPage.content.length + 100,
      name: body.name,
      description: body.description ?? '',
      category: body.category ?? 'STUDY',
      taskType: body.taskType ?? 'LIMITED',
      typeConfig: body.typeConfig ?? '{}',
      allowResubmit: body.allow_resubmit === true,
      maxSubmissions: typeof body.max_submissions === 'number' ? body.max_submissions : null,
      pointsCap: typeof body.points_cap === 'number' ? body.points_cap : null,
      enabled: true, deleted: false, version: 0, familyId: 1,
      createdAt: new Date().toISOString(),
      difficulties: body.difficulties ?? [],
    };
    templatesPage.content.push(created);
    templatesPage.totalElements = templatesPage.content.length;
    return ok(res, created);
  }
  if (/^\/api\/task-templates\/\d+$/.test(path) && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, templatesPage.content[0]);
  }
  if (path === '/children' && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, childrenPage);
  }

  // ── family ──
  if (path === '/family' && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return ok(res, { id: 1, name: '测试家庭', createdAt: '2026-01-01T00:00:00Z' });
  }
  if (/^\/api\/family\/\d+\/members$/.test(path) && method === 'GET') {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return fail(res, 404, 'NOT_FOUND', '家庭不存在');
  }

  // ── instance status (admin login/init routing) ──
  if (path === '/instance/status' && method === 'GET') {
    return ok(res, { instanceStatus: 'INITIALIZED', initialized: true, version: 'stub-1.0', lastBackupAt: null, lastBackupStatus: null });
  }

  // ── admin ──
  if (path.startsWith('/admin/')) {
    if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
    return fail(res, 403, 'FORBIDDEN', '无权限');
  }

  // ── fallback: generic success for unknown GETs (session required), 401 otherwise ──
  if (!authed) return fail(res, 401, 'UNAUTHORIZED', '未登录或会话已过期');
  return ok(res, { content: [], page: 1, pageSize: 20, totalElements: 0, totalPages: 1 });
});

server.listen(PORT, () => console.log(`[stub] listening on http://localhost:${PORT}`));
