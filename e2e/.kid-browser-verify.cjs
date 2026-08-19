// 孩子端浏览器真实环境验证（CJS 自包含：静态服务器 + API 拦截 mock + 三视口断言 + 截图）
// 用作 receipt 自动化命令的真实可执行。
const { createServer } = require('node:http');
const { readFile } = require('node:fs/promises');
const { join, extname, normalize } = require('node:path');
const { chromium } = require('@playwright/test');

const DIST = join(__dirname, '..', 'web', 'apps', 'kid', 'dist');
const PORT = Number(process.env.PORT || 4180);
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.json': 'application/json',
};

function startServer() {
  return new Promise((resolve) => {
    const server = createServer(async (req, res) => {
      let p = new URL(req.url, 'http://localhost').pathname;
      if (p === '/' || p === '/child') { res.writeHead(302, { location: '/child/' }); return res.end(); }
      if (p.startsWith('/child/api/')) { res.writeHead(403); return res.end('whitelist fallback'); }
      if (p.startsWith('/child/')) p = p.slice('/child'.length);
      const file = normalize(join(DIST, p));
      try { const data = await readFile(file); res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream' }); res.end(data); }
      catch { try { const data = await readFile(join(DIST, 'index.html')); res.writeHead(200, { 'content-type': 'text/html' }); res.end(data); } catch { res.writeHead(404); res.end(); } }
    });
    server.listen(PORT, () => resolve(server));
  });
}

const NL = String.fromCharCode(10);
const today = new Date().toISOString().split('T')[0];
const future = new Date(Date.now() + 86400000 * 30).toISOString().split('T')[0];
const TASKS = { data: { content: [
  { id: 1, childId: 3, templateId: 1, difficultyId: 1, snapshotTemplateName: '整理房间', snapshotDifficultyReward: 10, status: 'PENDING', deadline: today + 'T20:00:00', overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: true, submissionBlockReason: null },
  { id: 2, childId: 3, templateId: 2, difficultyId: 1, snapshotTemplateName: '阅读绘本', snapshotDifficultyReward: 5, status: 'SUBMITTED', deadline: today + 'T21:00:00', overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: false, submissionBlockReason: null },
  { id: 3, childId: 3, templateId: 3, difficultyId: 1, snapshotTemplateName: '周末运动', snapshotDifficultyReward: 20, status: 'PENDING', deadline: future + 'T10:00:00', overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: true, submissionBlockReason: null },
] } };

function fulfill(route, body) { route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }); }

async function mockApi(page) {
  await page.route('**/child/api/**', (route) => {
    const url = route.request().url();
    if (url.includes('/auth/me')) return fulfill(route, { data: { accountId: 3, phone: '13800000000', roles: ['CHILD'], familyId: 1, childId: 3 } });
    if (url.includes('/task-assignments')) return fulfill(route, TASKS);
    if (url.includes('/points/balance')) return fulfill(route, { data: { balance: 120 } });
    if (url.includes('/prizes')) return fulfill(route, { data: { content: [{ id: 1, name: '乐高积木', description: '创意拼装', pointsCost: 100, stock: 3 }], page: 0, pageSize: 20, totalElements: 1, totalPages: 1 } });
    if (url.includes('/blind-boxes')) return fulfill(route, { data: { items: [{ id: 1, name: '幸运盲盒', cost: 50, availabilityVersion: 'v12345678' }] } });
    if (url.includes('/exchanges')) return fulfill(route, { data: { content: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0 } });
    return fulfill(route, { data: {} });
  });
}

(async () => {
  const server = await startServer();
  const failures = [];
  const check = (name, cond, detail) => { console.log((cond ? 'PASS' : 'FAIL') + ' | ' + name + (detail ? ' | ' + detail : '')); if (!cond) failures.push(name); };
  const browser = await chromium.launch();
  for (const vp of [
    { name: 'mobile-375', width: 375, height: 812, isMobile: true, hasTouch: true },
    { name: 'tablet-834', width: 834, height: 1112, isMobile: true, hasTouch: true },
    { name: 'pc-1280', width: 1280, height: 800 },
  ]) {
    const ctx = await browser.newContext({ viewport: { width: vp.width, height: vp.height }, isMobile: !!vp.isMobile, hasTouch: !!vp.hasTouch });
    const page = await ctx.newPage();
    await mockApi(page);
    await page.goto('http://localhost:4180/child/tasks', { waitUntil: 'networkidle' });
    await page.waitForSelector('text=整理房间', { timeout: 10000 });
    const tabbar = await page.locator('nav[aria-label="主导航"]'); check(vp.name + ' 底部Tab可见', await tabbar.isVisible());
    const activeChip = page.locator('.kid-chip--active'); check(vp.name + ' 默认选中进行中', (await activeChip.textContent()) === '进行中');
    const bg = await activeChip.evaluate((el) => getComputedStyle(el).backgroundColor); const fg = await activeChip.evaluate((el) => getComputedStyle(el).color);
    check(vp.name + ' Chip=主题色背景', bg === 'rgb(2, 132, 199)', bg); check(vp.name + ' Chip=白字', fg === 'rgb(255, 255, 255)', fg);
    const noHScroll = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth); check(vp.name + ' 无横向滚动', noHScroll);
    const chipOk = await page.locator('.kid-chip').first().evaluate((el) => el.getBoundingClientRect().height >= 44); check(vp.name + ' Chip触控>=44px', chipOk);
    const tabOk = await page.locator('.kid-tab').first().evaluate((el) => el.getBoundingClientRect().height >= 44); check(vp.name + ' Tab触控>=44px', tabOk);
    check(vp.name + ' 未开始显示', await page.locator('text=未开始').first().isVisible());
    await page.locator('.kid-chip', { hasText: '已提交' }).click();
    await page.waitForSelector('text=阅读绘本'); check(vp.name + ' 切换已提交过滤', !(await page.locator('text=整理房间').count()));
    if (vp.name === 'pc-1280') {
      const box = await page.locator('.kid-page').first().boundingBox();
      check('pc-1280 内容受限(<=620)', box.width <= 620, 'w=' + Math.round(box.width));
      check('pc-1280 内容居中', box.x > 200, 'x=' + Math.round(box.x));
    }
    await ctx.close();
  }
  await browser.close();
  server.close();
  console.log(NL + (failures.length ? 'RESULT: FAIL (' + failures.length + ')' : 'RESULT: ALL PASS'));
  process.exit(failures.length ? 1 : 0);
})().catch((e) => { console.error('ERROR', e.message); process.exit(2); });
