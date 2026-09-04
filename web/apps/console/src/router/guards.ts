import { PageEnum } from '@/enums/pageEnum';
import { ErrorPageRoute } from '@/router/base';
import { useAsyncRoute } from '@/store/modules/asyncRoute';
import { useUser } from '@/store/modules/user';
import { getInstanceStatus } from '@/api/admin';
import type { RouteRecordRaw } from 'vue-router';
import { isNavigationFailure, Router } from 'vue-router';
import { RedirectName } from './constant';

// 公开页面：两端登录页 + 管理员初始化页
const whitePathList: string[] = [PageEnum.PARENT_LOGIN, PageEnum.ADMIN_LOGIN, PageEnum.ADMIN_INIT];

// 实例初始化状态缓存（null=未查询）。通过 setInstanceInitialized 在初始化成功后失效。
let instanceInitialized: boolean | null = null;

async function isInstanceInitialized(): Promise<boolean> {
  if (instanceInitialized !== null) return instanceInitialized;
  try {
    const status = await getInstanceStatus();
    instanceInitialized = status?.instanceStatus === 'INITIALIZED' || status?.initialized === true;
  } catch (error) {
    // 状态查询失败时按未初始化处理，由初始化/登录页面自行提示错误
    console.log(error);
    instanceInitialized = false;
  }
  return instanceInitialized;
}

/** 初始化成功后由初始化页调用，刷新守卫缓存 */
export function setInstanceInitialized(value: boolean) {
  instanceInitialized = value;
}

/**
 * 根据目标路径前缀返回所属区域的登录页（/admin/* → /admin/login，其余 → /parent/login）
 */
export function loginPathFor(path: string): string {
  return path.startsWith('/admin') ? PageEnum.ADMIN_LOGIN : PageEnum.PARENT_LOGIN;
}

export function createRouterGuards(router: Router) {
  const userStore = useUser();
  const asyncRouteStore = useAsyncRoute();
  router.beforeEach(async (to, _, next) => {
    const Loading = window['$loading'] || null;
    Loading && Loading.start();

    // ===== 公开页面 =====
    if (whitePathList.includes(to.path)) {
      // 已登录用户访问登录/初始化页 → 跳转自己区域的首页
      if (!userStore.authed) {
        // Cookie 会话本地不可见，尝试 /auth/me 恢复（未登录时静默失败）
        try {
          await userStore.fetchMe();
        } catch {
          // 未登录或会话过期
        }
      }
      if (userStore.authed) {
        next(userStore.homePath);
        return;
      }
      // 管理端登录/初始化页：实例未初始化 → 初始化页；已初始化 → 登录页
      // （对应旧版应用访问 /admin 时未初始化自动跳 /admin/init 的行为）
      if (to.path === PageEnum.ADMIN_LOGIN || to.path === PageEnum.ADMIN_INIT) {
        const isInit = await isInstanceInitialized();
        const target = isInit ? PageEnum.ADMIN_LOGIN : PageEnum.ADMIN_INIT;
        if (to.path !== target) {
          next(target);
          return;
        }
      }
      next();
      return;
    }

    // ===== 受保护页面 =====
    // 会话仅存在于 Cookie：首次进入时通过 /auth/me 恢复
    if (!userStore.authed) {
      try {
        await userStore.fetchMe();
      } catch {
        // 未登录或会话过期 → 跳转对应区域登录页
        next({
          path: loginPathFor(to.path),
          replace: true,
          query: to.path ? { redirect: to.path } : {},
        });
        return;
      }
    }

    // 角色不符 → 跳转自己区域的首页（家长进不了管理端业务页，反之亦然）
    const allowedRoles = to.meta?.roles as string[] | undefined;
    if (allowedRoles?.length && !allowedRoles.some((role) => userStore.roles.includes(role))) {
      next(userStore.homePath);
      return;
    }

    if (asyncRouteStore.getIsDynamicRouteAdded) {
      next();
      return;
    }

    // 按角色生成可访问路由（meta.roles 过滤）
    const routes = await asyncRouteStore.generateRoutes(userStore.roles);

    // 动态添加可访问路由表
    routes.forEach((item) => {
      router.addRoute(item as unknown as RouteRecordRaw);
    });

    //添加404
    const isErrorPage = router.getRoutes().findIndex((item) => item.name === ErrorPageRoute.name);
    if (isErrorPage === -1) {
      router.addRoute(ErrorPageRoute as unknown as RouteRecordRaw);
    }

    const redirectPath = (to.query.redirect || to.path) as string;
    const redirect = decodeURIComponent(redirectPath);
    const nextData = to.path === redirect ? { ...to, replace: true } : { path: redirect };
    asyncRouteStore.setDynamicRouteAdded(true);
    next(nextData);
    Loading && Loading.finish();
  });

  router.afterEach((to, _, failure) => {
    document.title = (to?.meta?.title as string) || document.title;
    if (isNavigationFailure(failure)) {
      //console.log('failed navigation', failure)
    }
    const asyncRouteStore = useAsyncRoute();
    // 在这里设置需要缓存的组件名称
    const keepAliveComponents = asyncRouteStore.keepAliveComponents;
    const currentComName: any = to.matched.find((item) => item.name == to.name)?.name;
    if (currentComName && !keepAliveComponents.includes(currentComName) && to.meta?.keepAlive) {
      // 需要缓存的组件
      keepAliveComponents.push(currentComName);
    } else if (!to.meta?.keepAlive || to.name == RedirectName) {
      // 不需要缓存的组件
      const index = asyncRouteStore.keepAliveComponents.findIndex((name) => name == currentComName);
      if (index != -1) {
        keepAliveComponents.splice(index, 1);
      }
    }
    asyncRouteStore.setKeepAliveComponents(keepAliveComponents);
    const Loading = window['$loading'] || null;
    Loading && Loading.finish();
  });

  router.onError((error) => {
    console.log(error, '路由错误');
  });
}
