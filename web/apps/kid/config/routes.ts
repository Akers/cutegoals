export default [
  {
    path: '/child',
    component: '@/layouts/KidLayout',
    routes: [
      { path: '/child/login', component: '@/pages/LoginPage' },
      { path: '/child/bind', component: '@/pages/BindPage' },
      { path: '/child', component: '@/pages/HomePage', wrappers: ['@/wrappers/AuthGuard'] },
      { path: '/child/tasks', component: '@/pages/TasksPage', wrappers: ['@/wrappers/AuthGuard'] },
      { path: '/child/prizes', component: '@/pages/PrizesPage', wrappers: ['@/wrappers/AuthGuard'] },
      { path: '/child/blind-boxes', component: '@/pages/BlindBoxesPage', wrappers: ['@/wrappers/AuthGuard'] },
      { path: '/child/exchanges', component: '@/pages/ExchangesPage', wrappers: ['@/wrappers/AuthGuard'] },
    ],
  },
  { path: '/', redirect: '/child' },
];
