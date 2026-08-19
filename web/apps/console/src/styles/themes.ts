import type { ThemeConfig } from 'antd';

const fontFamily =
  "PingFang SC, Microsoft YaHei, Hiragino Sans GB, system-ui, sans-serif";

/**
 * Admin 主题 — professional, restrained
 */
export const adminTheme: ThemeConfig = {
  token: {
    colorPrimary: '#4f46e5',
    colorBgLayout: '#f8fafc',
    colorBgContainer: '#ffffff',
    borderRadius: 6,
    fontFamily,
  },
};

/**
 * Parent 主题 — warm, family-oriented
 */
export const parentTheme: ThemeConfig = {
  token: {
    colorPrimary: '#0d9488',
    colorBgLayout: '#faf7f2',
    colorBgContainer: '#ffffff',
    borderRadius: 8,
    fontFamily,
  },
};

/**
 * Child 主题 — lively, playful
 */
export const childTheme: ThemeConfig = {
  token: {
    colorPrimary: '#0284c7',
    colorBgLayout: '#f0f9ff',
    colorBgContainer: '#ffffff',
    borderRadius: 12,
    fontFamily,
  },
};
