#!/usr/bin/env node
/**
 * 检查 src/styles 下的 CSS 文件是否被 src 下其他源码（.ts / .tsx / .css）通过
 * import 或 @import 引用。未引用则 exit 1，预防 “CSS 写了但没 import” 回归
 * （如 fix-global-styles-not-loaded 暴露的根因）。
 *
 * 检测规则：
 *   - JS/TS: `import '@/styles/index.css'` / `import x from './x.css'` / `from './x.css'`
 *   - CSS:    `@import './themes.css'`
 *   - 按 basename 匹配（不解析路径别名），排除 CSS 文件自身
 */
import { readdir, readFile } from 'node:fs/promises';
import { extname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_DIR = fileURLToPath(new URL('../src/', import.meta.url));
const STYLES_DIR = fileURLToPath(new URL('styles/', new URL('../src/', import.meta.url)));

/** 递归列出目录下匹配扩展名的所有文件（相对/绝对路径返回绝对）。 */
async function listFiles(dir, exts) {
  const result = [];
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch (err) {
    if (err.code === 'ENOENT') return result;
    throw err;
  }
  for (const entry of entries) {
    const full = `${dir.endsWith('/') ? dir : `${dir}/`}${entry.name}`;
    if (entry.isDirectory()) {
      result.push(...(await listFiles(full, exts)));
    } else if (exts.includes(extname(entry.name))) {
      result.push(full);
    }
  }
  return result;
}

function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// 1. 列出 src/styles/*.css
const cssFiles = await listFiles(STYLES_DIR, ['.css']);
if (cssFiles.length === 0) {
  console.error('✗ No CSS files found in src/styles/');
  process.exit(1);
}

// 2. 列出 src/**/*.{ts,tsx,css} 源码
const sourceFiles = await listFiles(SRC_DIR, ['.ts', '.tsx', '.css']);

// 3. 读取所有源码内容
const sources = await Promise.all(
  sourceFiles.map(async (path) => ({ path, content: await readFile(path, 'utf8') })),
);

// 4. 检查每个 CSS 文件是否被引用
const orphans = [];
for (const cssPath of cssFiles) {
  const name = basename(cssPath);
  const escaped = escapeRegex(name);
  // 匹配 `import '...<name>'` / `from '...<name>'` / `@import '...<name>'`
  const importPattern = new RegExp(
    `(import\\s+|from\\s+|@import\\s+)['"][^'"]*${escaped}['"]`,
  );
  const referenced = sources.some(({ path, content }) => {
    if (path === cssPath) return false; // 排除自引（如 index.css 不应因含自身 basename 而判为已引用）
    if (!content.includes(name)) return false; // 快速预筛
    return importPattern.test(content);
  });
  if (!referenced) orphans.push(cssPath);
}

// 5. 输出结果
if (orphans.length > 0) {
  console.error(
    `✗ ${orphans.length} CSS file(s) in src/styles/ not imported by any source:`,
  );
  for (const path of orphans) {
    const rel = path.replace(SRC_DIR, 'src/');
    console.error(`  - ${rel}`);
  }
  console.error('');
  console.error(
    "Fix: import these files via `import '@/styles/<file>'` in a TS/TSX module,",
  );
  console.error("     or via `@import './<file>'` in another CSS file.");
  process.exit(1);
}

const relFiles = cssFiles.map((p) => p.replace(SRC_DIR, 'src/'));
console.log(`✓ All ${cssFiles.length} CSS file(s) imported: ${relFiles.join(', ')}`);
