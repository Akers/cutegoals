/**
 * shared 包独立 typecheck 用的 umi 最小类型声明。
 * 应用（console/kid）的 typecheck 使用 umi 生成的真实类型（src/.umi），
 * 本文件只被 packages/shared/tsconfig.json 包含，不会泄漏到应用侧。
 */
declare module 'umi' {
  import type React from 'react';
  export const history: {
    push: (to: string, state?: unknown) => void;
    replace: (to: string, state?: unknown) => void;
    back: () => void;
    go: (delta: number) => void;
    listen: (listener: unknown) => () => void;
    location: { pathname: string; search: string; hash: string; state: unknown };
  };
  export const Navigate: React.FC<{ to: string; replace?: boolean; state?: unknown }>;
  export const Link: React.FC<{ to: string; children?: React.ReactNode; [k: string]: unknown }>;
  export const Outlet: React.FC;
  export function useLocation(): { pathname: string; search: string; hash: string; state: unknown };
  export function useNavigate(): typeof history;
  export function useSearchParams(): [URLSearchParams, (next: URLSearchParams) => void];
  export function useParams(): Record<string, string>;
}
