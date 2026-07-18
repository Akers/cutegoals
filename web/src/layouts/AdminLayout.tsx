import { ConfigProvider } from 'antd';
import { ProLayout } from '@ant-design/pro-components';
import { Outlet, useNavigate, useLocation } from 'umi';
import zhCN from 'antd/locale/zh_CN';
import {
  DashboardOutlined,
  SettingOutlined,
  UserOutlined,
  FileTextOutlined,
  HeartOutlined,
} from '@ant-design/icons';
import { useAuth, maskPhone } from '@/shared/auth';
import { adminTheme } from '@/styles/themes';

export default function AdminLayout() {
  const { account } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 登录页跳过 ProLayout，避免显示侧边栏菜单，确保全屏居中
  if (location.pathname === '/admin/login') {
    return (
      <ConfigProvider theme={adminTheme} locale={zhCN}>
        <Outlet />
      </ConfigProvider>
    );
  }

  return (
    <ConfigProvider theme={adminTheme} locale={zhCN}>
      <ProLayout
        title="CuteGoals Admin"
        layout="side"
        route={{
          routes: [
            { path: '/admin', name: '实例概览', icon: <DashboardOutlined /> },
            { path: '/admin/config', name: '系统配置', icon: <SettingOutlined /> },
            { path: '/admin/accounts', name: '账号管理', icon: <UserOutlined /> },
            { path: '/admin/audit', name: '审计日志', icon: <FileTextOutlined /> },
            { path: '/admin/health', name: '健康面板', icon: <HeartOutlined /> },
          ],
        }}
        avatarProps={{
          src: undefined,
          title: account?.nickname ?? maskPhone(account?.phone) ?? 'Admin',
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
