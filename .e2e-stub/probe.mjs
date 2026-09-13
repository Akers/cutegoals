import { chromium } from '@playwright/test';

const BASE = 'http://127.0.0.1:8000';
const browser = await chromium.launch();
const ctx = await browser.newContext();
const page = await ctx.newPage();
page.on('console', (m) => console.log('[console]', m.type(), m.text()));
page.on('requestfailed', (r) => console.log('[reqfail]', r.method(), r.url(), r.failure()?.errorText));
page.on('response', (r) => { if (r.url().includes('/api/')) console.log('[resp]', r.status(), r.url()); });

const login = await ctx.request.post(`${BASE}/api/auth/login`, {
  data: { phone: '13800001234', password: 'E2eTest#2026' },
});
console.log('login status', login.status());
console.log('cookies', (await ctx.cookies()).map((c) => c.name));

await page.goto(`${BASE}/parent/tasks`, { waitUntil: 'networkidle' }).catch((e) => console.log('goto err', e.message));
console.log('final url', page.url());
console.log('body text:', (await page.locator('body').innerText().catch(() => '<none>')).slice(0, 500));
await browser.close();
