/**
 * Used to parse the .env.development proxy configuration
 */
import type { ProxyOptions } from 'vite';

type ProxyItem = [string, string];

type ProxyList = ProxyItem[];

type ProxyTargetList = Record<string, ProxyOptions & { rewrite: (path: string) => string }>;

const httpsRE = /^https:\/\//;

/**
 * Generate proxy
 * @param list
 */
export function createProxy(list: ProxyList = []) {
  const ret: ProxyTargetList = {};
  for (const [prefix, target] of list) {
    // 允许通过 API_PROXY_TARGET 环境变量覆盖代理目标（默认取 .env 中的配置）
    const resolvedTarget = process.env.API_PROXY_TARGET || target;
    const isHttps = httpsRE.test(resolvedTarget);

    // https://github.com/http-party/node-http-proxy#options
    // 注意：CuteGoals 后端路由本身带 /api 前缀，这里不做 rewrite 剥离前缀
    ret[prefix] = {
      target: resolvedTarget,
      changeOrigin: true,
      ws: true,
      // https is require secure=false
      ...(isHttps ? { secure: false } : {}),
    };
  }
  return ret;
}
