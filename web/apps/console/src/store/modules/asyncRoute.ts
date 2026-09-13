import { toRaw, unref } from 'vue';
import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import { store } from '@/store';
import { asyncRoutes, constantRouter } from '@/router/index';
import { generateDynamicRoutes } from '@/router/generator';
import { useProjectSetting } from '@/hooks/setting/useProjectSetting';

interface TreeHelperConfig {
  id: string;
  children: string;
  pid: string;
}

const DEFAULT_CONFIG: TreeHelperConfig = {
  id: 'id',
  children: 'children',
  pid: 'pid',
};

const getConfig = (config: Partial<TreeHelperConfig>) => Object.assign({}, DEFAULT_CONFIG, config);

export interface IAsyncRouteState {
  menus: RouteRecordRaw[];
  routers: any[];
  routersAdded: any[];
  keepAliveComponents: string[];
  isDynamicRouteAdded: boolean;
}

function filter<T = any>(
  tree: T[],
  func: (n: T) => boolean,
  config: Partial<TreeHelperConfig> = {}
): T[] {
  config = getConfig(config);
  const children = config.children as string;

  function listFilter(list: T[]) {
    return list
      .map((node: any) => ({ ...node }))
      .filter((node) => {
        node[children] = node[children] && listFilter(node[children]);
        return func(node) || (node[children] && node[children].length);
      });
  }

  return listFilter(tree);
}

export const useAsyncRouteStore = defineStore({
  id: 'app-async-route',
  state: (): IAsyncRouteState => ({
    menus: [],
    routers: constantRouter,
    routersAdded: [],
    keepAliveComponents: [],
    // Whether the route has been dynamically added
    isDynamicRouteAdded: false,
  }),
  getters: {
    getMenus(): RouteRecordRaw[] {
      return this.menus;
    },
    getIsDynamicRouteAdded(): boolean {
      return this.isDynamicRouteAdded;
    },
  },
  actions: {
    getRouters() {
      return toRaw(this.routersAdded);
    },
    setDynamicRouteAdded(added: boolean) {
      this.isDynamicRouteAdded = added;
    },
    // 设置动态路由
    setRouters(routers: RouteRecordRaw[]) {
      this.routersAdded = routers;
      this.routers = constantRouter.concat(routers);
    },
    setMenus(menus: RouteRecordRaw[]) {
      // 设置动态路由
      this.menus = menus;
    },
    setKeepAliveComponents(compNames: string[]) {
      // 设置需要缓存的组件
      this.keepAliveComponents = compNames;
    },
    async generateRolesFilteredRoutes(roles: string[]) {
      const roleList = roles ?? [];
      // FIXED 模式：按 meta.roles 过滤（角色已归一化为 parent / admin / child）。
      // 后端初始化账号为 INSTANCE_ADMIN + PARENT 双角色，routeFilter 天然按角色放行两端路由，
      // 不再做"管理员只看管理端"的一刀切区域过滤（否则双角色账号永远进不了 /parent）。
      const routeFilter = (route) => {
        const { meta } = route;
        const { roles: allowedRoles } = meta || {};
        if (!allowedRoles || !allowedRoles.length) return true;
        return allowedRoles.some((role) => roleList.includes(role));
      };
      let accessedRouters;
      try {
        //过滤角色是否拥有该路由，并将菜单从加载列表移除
        accessedRouters = filter(asyncRoutes, routeFilter);
      } catch (error) {
        console.log(error);
      }
      accessedRouters = accessedRouters.filter(routeFilter);
      this.setRouters(accessedRouters);
      this.setMenus(accessedRouters);
      return toRaw(accessedRouters);
    },
    async generateRoutes(roles: string[]) {
      const { permissionMode } = useProjectSetting();
      if (unref(permissionMode) === 'BACK') {
        // 动态获取菜单（后端无该端点，仅保留模板接线；FIXED 模式不会走到这里）
        let accessedRoutersBack;
        try {
          accessedRoutersBack = await generateDynamicRoutes();
        } catch (error) {
          console.log(error);
        }
        this.setRouters(accessedRoutersBack);
        this.setMenus(accessedRoutersBack);
        return toRaw(accessedRoutersBack);
      }
      return this.generateRolesFilteredRoutes(roles);
    },
  },
});

// Need to be used outside the setup
export function useAsyncRoute() {
  return useAsyncRouteStore(store);
}
