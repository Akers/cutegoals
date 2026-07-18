import { ConfigProvider, Dropdown } from 'antd';
import { ProLayout } from '@ant-design/pro-components';
import { Outlet, useNavigate, useLocation } from 'umi';
import zhCN from 'antd/locale/zh_CN';
import {
  HomeOutlined,
  CheckSquareOutlined,
  TrophyOutlined,
  GiftOutlined,
  SwapOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { useAuth, maskPhone } from '@/shared/auth';
import { childTheme } from '@/styles/themes';

export default function ChildLayout() {
  const { account, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const userDisplay = account?.nickname ?? maskPhone(account?.phone) ?? 'Child';

  // 登录页跳过 ProLayout，避免显示侧边栏菜单，确保全屏居中
  if (location.pathname === '/child/login') {
    return (
      <ConfigProvider theme={childTheme} locale={zhCN}>
        <Outlet />
      </ConfigProvider>
    );
  }

  return (
    <ConfigProvider theme={childTheme} locale={zhCN}>
      <ProLayout
        title="CuteGoals Child"
        layout="side"
        route={{
          routes: [
            { path: '/child', name: '首页', icon: <HomeOutlined /> },
            { path: '/child/tasks', name: '我的任务', icon: <CheckSquareOutlined /> },
            { path: '/child/prizes', name: '奖品商店', icon: <TrophyOutlined /> },
            { path: '/child/blind-boxes', name: '盲盒', icon: <GiftOutlined /> },
            { path: '/child/exchanges', name: '兑换记录', icon: <SwapOutlined /> },
          ],
        }}
        avatarProps={{
          src: undefined,
          title: userDisplay,
          render: (_, defaultDom) => (
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'logout',
                    icon: <LogoutOutlined />,
                    label: '退出登录',
                    onClick: () => logout(),
                  },
                ],
              }}
            >
              {defaultDom}
            </Dropdown>
          ),
        }}
        actionsRender={(props) => {
          if (props.isMobile) return [];
          return [];
        }}
        menuItemRender={(item, dom) => (
          <a onClick={() => navigate(item.path!)}>{dom}</a>
        )}
        location={location}
        headerTitleRender={(logo, title) => <>{logo}{title}</>}
      >
        <Outlet />
      </ProLayout>
    </ConfigProvider>
  );
}
