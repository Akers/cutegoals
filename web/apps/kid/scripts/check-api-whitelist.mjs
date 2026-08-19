#!/usr/bin/env node
/**
 * 孩子端 API 白名单一致性校验（D2 维护守卫）：
 * 1. kid 源码中的每个 API 调用 MUST 命中 nginx-kid-api-whitelist.conf 的某个前缀；
 * 2. 白名单每个前缀 MUST 被至少一个源码调用使用（防腐化）。
 * 任一不满足即 exit 1。
 */
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const APP = fileURLToPath(new URL('..', import.meta.url));
const SRC = join(APP, 'src');
const SHARED = fileURLToPath(new URL('../../../packages/shared/src', import.meta.url));

async function* walk(dir) {
  for (const e of await readdir(dir, { withFileTypes: true })) {
    const p = join(dir, e.name);
    // 排除生成目录（.umi）、测试目录与隐藏目录
    if (e.isDirectory() && !e.name.startsWith('.') && e.name !== 'test') yield* walk(p);
    else if (/\.(ts|tsx)$/.test(e.name) && !e.name.includes('.test.')) yield p;
  }
}

const normalize = (s) => s.replace(/\$\{[^}]+\}/g, ':id').split('?')[0];

/** kid 应用源码：以 / 开头、非 /child 路由的字符串/模板字面量即 API 调用候选 */
async function collectAppCalls(dir, acc) {
  for await (const file of walk(dir)) {
    const text = await readFile(file, 'utf8');
    for (const m of text.matchAll(/[`'"](\/(?!child\b)[a-z][^'`"]*?)[`'"]/g)) {
      acc.add(normalize(m[1]));
    }
  }
}

/** shared 包：仅收集显式调用形态（getClient().get/post(...)、useApi(...)），忽略 baseUrl 等常量 */
async function collectSharedCalls(dir, acc) {
  for await (const file of walk(dir)) {
    const text = await readFile(file, 'utf8');
    for (const m of text.matchAll(/(?:getClient\(\)\s*\.\s*(?:get|post|put|delete|patch)|useApi)(?:<[^(]*>)?\(\s*[`'"](\/[^'`"]*?)[`'"]/gs)) {
      acc.add(normalize(m[1]));
    }
  }
}

const calls = new Set();
await collectAppCalls(SRC, calls);
await collectSharedCalls(SHARED, calls);

const wlText = await readFile(join(APP, 'nginx-kid-api-whitelist.conf'), 'utf8');
const whitelist = [...wlText.matchAll(/^location (\/child\/api\/[a-z0-9\-\/]+)/gm)].map((m) =>
  m[1].replace('/child/api', ''),
);

const hit = (call) => whitelist.some((p) => call === p || call.startsWith(p + '/') || call.startsWith(p));
const failures = [];
for (const call of [...calls].sort()) {
  if (!hit(call)) failures.push('调用未命中白名单: ' + call);
}
for (const prefix of whitelist) {
  if (![...calls].some((c) => c === prefix || c.startsWith(prefix + '/') || c.startsWith(prefix))) {
    failures.push('白名单条目未被使用: ' + prefix);
  }
}

console.log('孩子端运行时调用:', [...calls].sort().join(', '));
console.log('白名单前缀:', whitelist.join(', '));
if (failures.length) {
  console.error('✗ 白名单不一致:');
  for (const f of failures) console.error('  - ' + f);
  process.exit(1);
}
console.log('✓ 白名单一致：' + calls.size + ' 个调用全部命中 ' + whitelist.length + ' 条白名单前缀，且无未使用条目');
