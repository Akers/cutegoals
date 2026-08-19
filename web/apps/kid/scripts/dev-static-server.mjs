// 本地静态冒烟服务器：模拟 kid 容器 nginx 的 /child/ alias 行为（仅开发验证用）
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname, normalize } from 'node:path';

const DIST = new URL('../dist/', import.meta.url).pathname;
const PORT = Number(process.env.PORT ?? 4180);
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.json': 'application/json',
};

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://localhost');
  let p = url.pathname;
  if (p === '/' || p === '/child') {
    res.writeHead(302, { location: '/child/' });
    return res.end();
  }
  if (p.startsWith('/child/api/')) {
    res.writeHead(403);
    return res.end('whitelist fallback (no backend in static smoke)');
  }
  if (p.startsWith('/child/')) p = p.slice('/child'.length);
  const file = normalize(join(DIST, p));
  try {
    const data = await readFile(file);
    res.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream' });
    res.end(data);
  } catch {
    // SPA 回退
    const data = await readFile(join(DIST, 'index.html'));
    res.writeHead(200, { 'content-type': 'text/html' });
    res.end(data);
  }
}).listen(PORT, () => console.log(`kid static smoke: http://localhost:${PORT}/child/`));
