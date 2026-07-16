import { ConfigProvider } from 'antd';
import { ProLayout } from '@ant-design/pro-components';
import { Outlet, useNavigate, useLocation } from 'umi';
import zhCN from 'antd/locale/zh_CN';
import {
  HomeOutlined,
  TeamOutlined,
  SmileOutlined,
  FileAddOutlined,
  SendOutlined,
  CheckCircleOutlined,
  AccountBookOutlined,
  TrophyOutlined,
  GiftOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/shared/auth';
import { parentTheme } from '@/styles/themes';

export default function ParentLayout() {
  const { account } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 登录页跳过 ProLayout，避免显示侧边栏菜单，确保全屏居中
  if (location.pathname === '/parent/login') {
    return (
      <ConfigProvider theme={parentTheme} locale={zhCN}>
        <Outlet />
      </ConfigProvider>
    );
  }

  return (
    <ConfigProvider theme={parentTheme} locale={zhCN}>
      <ProLayout
        title="CuteGoals Parent"
        layout="side"
        route={{
          routes: [
            { path: '/parent', name: '家庭概览', icon: <HomeOutlined /> },
            { path: '/parent/family', name: '家庭管理', icon: <TeamOutlined /> },
            { path: '/parent/children', name: '孩子档案', icon: <SmileOutlined /> },
            { path: '/parent/templates', name: '任务模板', icon: <FileAddOutlined /> },
            { path: '/parent/tasks', name: '任务分配', icon: <SendOutlined /> },
            { path: '/parent/reviews', name: '任务审核', icon: <CheckCircleOutlined /> },
            { path: '/parent/points', name: '积分管理', icon: <AccountBookOutlined /> },
            { path: '/parent/prizes', name: '奖品管理', icon: <TrophyOutlined /> },
            { path: '/parent/blind-boxes', name: '惊喜盲盒', icon: <GiftOutlined /> },
            { path: '/parent/exchanges', name: '兑换履约', icon: <SwapOutlined /> },
          ],
        }}
        avatarProps={{
          src: undefined,
          title: account?.nickname ?? account?.phone ?? 'Parent',
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
