import { RouteRecordRaw } from 'vue-router';
import { Layout } from '@/router/constant';
import {
  HomeOutlined,
  TeamOutlined,
  SmileOutlined,
  FileTextOutlined,
  CarryOutOutlined,
  AuditOutlined,
  PayCircleOutlined,
  GiftOutlined,
  RedEnvelopeOutlined,
  MobileOutlined,
  ShoppingOutlined,
} from '@vicons/antd';
import { renderIcon } from '@/utils/index';

const routeName = 'parent';

// 家长端业务路由：meta.roles = ['parent']，管理员不会看到/进入这些页面
const routes: Array<RouteRecordRaw> = [
  {
    path: '/parent',
    name: routeName,
    redirect: '/parent/index',
    component: Layout,
    meta: {
      title: '家长端',
      icon: renderIcon(HomeOutlined),
      sort: 0,
      roles: ['parent'],
    },
    children: [
      {
        path: 'index',
        name: `${routeName}_index`,
        meta: {
          title: '工作台',
          affix: true,
          roles: ['parent'],
        },
        component: () => import('@/views/parent/index/index.vue'),
      },
      {
        path: 'family',
        name: `${routeName}_family`,
        meta: {
          title: '家庭设置',
          icon: renderIcon(TeamOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/family/index.vue'),
      },
      {
        path: 'children',
        name: `${routeName}_children`,
        meta: {
          title: '孩子管理',
          icon: renderIcon(SmileOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/children/index.vue'),
      },
      {
        path: 'templates',
        name: `${routeName}_templates`,
        meta: {
          title: '任务模板',
          icon: renderIcon(FileTextOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/templates/index.vue'),
      },
      {
        path: 'tasks',
        name: `${routeName}_tasks`,
        meta: {
          title: '任务分配',
          icon: renderIcon(CarryOutOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/tasks/index.vue'),
      },
      {
        path: 'reviews',
        name: `${routeName}_reviews`,
        meta: {
          title: '任务审核',
          icon: renderIcon(AuditOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/reviews/index.vue'),
      },
      {
        path: 'points',
        name: `${routeName}_points`,
        meta: {
          title: '积分管理',
          icon: renderIcon(PayCircleOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/points/index.vue'),
      },
      {
        path: 'prizes',
        name: `${routeName}_prizes`,
        meta: {
          title: '奖品管理',
          icon: renderIcon(GiftOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/prizes/index.vue'),
      },
      {
        path: 'blind-boxes',
        name: `${routeName}_blind-boxes`,
        meta: {
          title: '盲盒奖池',
          icon: renderIcon(RedEnvelopeOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/blind-boxes/index.vue'),
      },
      {
        path: 'devices',
        name: `${routeName}_devices`,
        meta: {
          title: '设备绑定',
          icon: renderIcon(MobileOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/devices/index.vue'),
      },
      {
        path: 'exchanges',
        name: `${routeName}_exchanges`,
        meta: {
          title: '兑换记录',
          icon: renderIcon(ShoppingOutlined),
          roles: ['parent'],
        },
        component: () => import('@/views/parent/exchanges/index.vue'),
      },
    ],
  },
];

export default routes;
