import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { Outlet, history, useLocation } from 'umi';
import { useAuth } from '@shared/auth';
import { useTheme } from '@shared/theme';
import type { ThemeConfig } from 'antd';

/** 孩子端 antd 主题 —— 仅作用于保留的少量 antd 底层组件（message 等）。
 *  页面主体由自绘童趣组件层（design/components）呈现。 */
export const kidAntdTheme: ThemeConfig = {
  token: {
    colorPrimary: '#0284c7',
    colorBgLayout: '#f0f9ff',
    colorBgContainer: '#ffffff',
    borderRadius: 16,
    fontFamily:
      "ui-rounded, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', system-ui, sans-serif",
  },
};

const TABS = [
  { path: '/child', emoji: '🏠', label: '首页' },
  { path: '/child/tasks', emoji: '✅', label: '任务' },
  { path: '/child/prizes', emoji: '🎁', label: '奖品' },
  { path: '/child/blind-boxes', emoji: '🎉', label: '盲盒' },
  { path: '/child/exchanges', emoji: '📜', label: '记录' },
] as const;

function isActive(pathname: string, tabPath: string): boolean {
  if (tabPath === '/child') return pathname === '/child' || pathname === '/child/';
  return pathname.startsWith(tabPath);
}

function BottomTabBar() {
  const location = useLocation();
  return (
    <nav className="kid-tabbar" aria-label="主导航">
      <div className="kid-tabbar-inner">
        {TABS.map((tab) => (
          <button
            key={tab.path}
            type="button"
            className={`kid-tab${isActive(location.pathname, tab.path) ? ' kid-tab--active' : ''}`}
            aria-current={isActive(location.pathname, tab.path) ? 'page' : undefined}
            onClick={() => history.push(tab.path)}
          >
            <span className="kid-tab-emoji" aria-hidden="true">{tab.emoji}</span>
            {tab.label}
          </button>
        ))}
      </div>
    </nav>
  );
}

function KidHeader() {
  const { account, logout } = useAuth();
  const userDisplay = account?.nickname ?? '小朋友';
  return (
    <header className="kid-header">
      <span className="kid-header-brand">
        <span aria-hidden="true">🌈</span> CuteGoals
      </span>
      <span className="kid-header-user">
        <span>{userDisplay}</span>
        <button
          type="button"
          className="kid-tab"
          style={{ flex: 'none', minHeight: 44, padding: '0 10px' }}
          aria-label="退出登录"
          onClick={() => logout()}
        >
          <span className="kid-tab-emoji" aria-hidden="true">🚪</span>
        </button>
      </span>
    </header>
  );
}

export default function KidLayout() {
  const location = useLocation();
  useTheme('child');

  // 登录/绑定页：全屏，无顶部栏与底部导航
  const bare = location.pathname === '/child/login' || location.pathname === '/child/bind';

  return (
    <ConfigProvider theme={kidAntdTheme} locale={zhCN}>
      <AntdApp>
        {bare ? (
          <Outlet />
        ) : (
          <>
            <KidHeader />
            <Outlet />
            <BottomTabBar />
          </>
        )}
      </AntdApp>
    </ConfigProvider>
  );
}
