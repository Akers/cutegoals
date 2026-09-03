import { RouteRecordRaw } from 'vue-router';
import { Layout } from '@/router/constant';
import {
  DashboardOutlined,
  SettingOutlined,
  UserSwitchOutlined,
  FileSearchOutlined,
  SafetyCertificateOutlined,
} from '@vicons/antd';
import { renderIcon } from '@/utils/index';

const routeName = 'admin';

// 管理端路由：meta.roles = ['admin']，家长不会看到/进入这些页面
const routes: Array<RouteRecordRaw> = [
  {
    path: '/admin',
    name: routeName,
    redirect: '/admin/index',
    component: Layout,
    meta: {
      title: '管理端',
      icon: renderIcon(DashboardOutlined),
      sort: 1,
      roles: ['admin'],
    },
    children: [
      {
        path: 'index',
        name: `${routeName}_index`,
        meta: {
          title: '概览',
          affix: true,
          roles: ['admin'],
        },
        component: () => import('@/views/admin/index.vue'),
      },
      {
        path: 'config',
        name: `${routeName}_config`,
        meta: {
          title: '系统配置',
          icon: renderIcon(SettingOutlined),
          roles: ['admin'],
        },
        component: () => import('@/views/admin/config/index.vue'),
      },
      {
        path: 'accounts',
        name: `${routeName}_accounts`,
        meta: {
          title: '账号管理',
          icon: renderIcon(UserSwitchOutlined),
          roles: ['admin'],
        },
        component: () => import('@/views/admin/accounts/index.vue'),
      },
      {
        path: 'audit',
        name: `${routeName}_audit`,
        meta: {
          title: '审计日志',
          icon: renderIcon(FileSearchOutlined),
          roles: ['admin'],
        },
        component: () => import('@/views/admin/audit/index.vue'),
      },
      {
        path: 'health',
        name: `${routeName}_health`,
        meta: {
          title: '健康面板',
          icon: renderIcon(SafetyCertificateOutlined),
          roles: ['admin'],
        },
        component: () => import('@/views/admin/health/index.vue'),
      },
    ],
  },
];

export default routes;
