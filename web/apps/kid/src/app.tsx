// 全局 dayjs 插件注册，必须早于任何 dayjs 实例方法调用。
import '@shared/dayjs';

import '@/design/tokens.css';
import '@/design/kid.css';

import React from 'react';
import { App } from 'antd';
import { AuthProvider } from '@shared/auth';
import { RoleProvider } from '@shared/RoleContext';

/**
 * UmiJS runtime rootContainer（孩子端）。
 * 关键差异：API baseURL 为 '/child/api' —— 孩子端全部 API 流量经
 * 入口网关分流至 kid 容器，由 kid 容器 nginx 白名单过滤后转发后端。
 */
export function rootContainer(container: React.ReactNode) {
  return (
    <AuthProvider apiBaseUrl="/child/api">
      <RoleProvider role="child">
        <App>{container}</App>
      </RoleProvider>
    </AuthProvider>
  );
}
