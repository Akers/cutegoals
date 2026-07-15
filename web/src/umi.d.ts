// UmiJS 运行时类型声明——UmiJS 构建时拦截 `from 'umi'` 并将其路由到
// `.umi/exports.ts`，后者从 `@umijs/renderer-react` 和 `react-router-dom` 再导出。
// 此文件为 tsc 提供等效声明，确保在 Vite + UmiJS 混合模式下类型检查通过。
declare module 'umi' {
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
  } from '@umijs/renderer-react';
}
