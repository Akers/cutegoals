// UmiJS 运行时类型声明——UmiJS 构建时拦截 `from 'umi'` 并将其路由到
// `.umi/exports.ts`，后者从 `@umijs/renderer-react` 和 `umi/client/client/plugin` 等模块再导出。
// 此文件为 tsc 提供等效声明，确保类型检查通过。

declare module 'umi' {
  // Re-exports from @umijs/renderer-react
  export {
    Outlet,
    useNavigate,
    useLocation,
    Navigate,
    Link,
    useParams,
    useSearchParams,
    useOutlet,
    useOutletContext,
    useMatch,
    useResolvedPath,
    useRoutes,
    generatePath,
    matchPath,
    matchRoutes,
    resolvePath,
    createSearchParams,
    createMemoryHistory,
    createBrowserHistory,
    createHashHistory,
    Helmet,
    HelmetProvider,
    NavLink,
    useAppData,
    useClientLoaderData,
    useLoaderData,
    useRouteProps,
    useSelectedRoutes,
    useServerLoaderData,
    renderClient,
    withRouter,
    __useFetcher,
    __getRoot,
  } from '@umijs/renderer-react';

  export type { History, ClientLoader, RouteComponentProps } from '@umijs/renderer-react';

  // The history singleton is from @umijs/core (via .umi/core/history)
  export const history: {
    push(path: string, state?: unknown): void;
    replace(path: string, state?: unknown): void;
    back(): void;
    go(n: number): void;
    listen(fn: (location: any) => void): () => void;
    location: Location;
  };

  // Plugin-related exports
  export { ApplyPluginsType, PluginManager } from 'umi/client/client/plugin.js';
  export { defineConfig, defineMock } from 'umi';

  // Test utilities (used by vi.mock factory in setup.ts)
  export function __setMockLocation(fullPath: string): void;
  export { TestBrowser } from '../.umi/testBrowser';
}
