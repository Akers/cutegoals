import { App } from 'vue';
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import { RedirectRoute } from '@/router/base';
import { PageEnum } from '@/enums/pageEnum';
import { createRouterGuards } from './guards';
import type { IModuleType } from './types';

const modules = import.meta.glob<IModuleType>('./modules/**/*.ts', { eager: true });

const routeModuleList: RouteRecordRaw[] = Object.keys(modules).reduce((list, key) => {
  const mod = modules[key].default ?? {};
  const modList = Array.isArray(mod) ? [...mod] : [mod];
  return [...list, ...modList];
}, []);

function sortRoute(a, b) {
  return (a.meta?.sort ?? 0) - (b.meta?.sort ?? 0);
}

routeModuleList.sort(sortRoute);

export const RootRoute: RouteRecordRaw = {
  path: '/',
  name: 'Root',
  redirect: PageEnum.BASE_HOME,
  meta: {
    title: 'Root',
  },
};

// 公开页面（无需登录）：家长端登录 / 管理端登录 / 管理员初始化
export const ParentLoginRoute: RouteRecordRaw = {
  path: PageEnum.PARENT_LOGIN,
  name: 'ParentLogin',
  component: () => import('@/views/login/parent.vue'),
  meta: {
    title: '家长登录',
    ignoreAuth: true,
  },
};

export const AdminLoginRoute: RouteRecordRaw = {
  path: PageEnum.ADMIN_LOGIN,
  name: 'AdminLogin',
  component: () => import('@/views/login/admin.vue'),
  meta: {
    title: '管理员登录',
    ignoreAuth: true,
  },
};

export const AdminInitRoute: RouteRecordRaw = {
  path: PageEnum.ADMIN_INIT,
  name: 'AdminInit',
  component: () => import('@/views/login/init.vue'),
  meta: {
    title: '初始化',
    ignoreAuth: true,
  },
};

//需要验证权限
export const asyncRoutes = [...routeModuleList];

//普通路由 无需验证权限
export const constantRouter: RouteRecordRaw[] = [
  ParentLoginRoute,
  AdminLoginRoute,
  AdminInitRoute,
  RootRoute,
  RedirectRoute,
];

const router = createRouter({
  history: createWebHistory(),
  routes: constantRouter,
  strict: true,
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

export function setupRouter(app: App) {
  app.use(router);
  // 创建路由守卫
  createRouterGuards(router);
}

export default router;
