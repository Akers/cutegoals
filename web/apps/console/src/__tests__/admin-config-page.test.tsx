import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { AdminConfigPage } from '../admin/pages';

// Backend GET /api/admin/config contract: array of config entries
// (see InstanceConfigService.getAllConfig): {key, type, description, masked, value, configured}
const CONFIG_ENTRIES = [
  {
    key: 'sms.provider',
    type: 'string',
    description: 'SMS service provider (e.g., aliyun, tencent)',
    masked: false,
    value: 'aliyun',
    configured: true,
  },
  {
    key: 'sms.api_key',
    type: 'string',
    description: 'SMS API key (secret)',
    masked: true,
    value: '***MASKED***',
    configured: true,
  },
  {
    key: 'backup.retention_days',
    type: 'integer',
    description: 'Backup retention in days',
    masked: false,
    value: '7',
    configured: true,
  },
];

const { getMock, putMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  putMock: vi.fn(),
}));

vi.mock('@shared/api/client', () => ({
  getClient: () => ({ get: getMock, put: putMock, post: vi.fn() }),
}));

describe('AdminConfigPage', () => {
  beforeEach(() => {
    getMock.mockReset();
    putMock.mockReset();
    getMock.mockResolvedValue({ data: CONFIG_ENTRIES });
    putMock.mockResolvedValue({ data: null });
  });

  it('按配置键渲染标签与真实值，秘密字段使用密码输入框', async () => {
    render(<AdminConfigPage />);

    // 标签必须是配置键，而不是数组索引 "0"/"1"/"2"
    expect(await screen.findByText('sms.provider')).toBeInTheDocument();
    expect(screen.getByText('sms.api_key')).toBeInTheDocument();
    expect(screen.getByText('backup.retention_days')).toBeInTheDocument();

    // 输入框必须显示真实值，而不是 "[object Object]"
    expect(screen.getByDisplayValue('aliyun')).toBeInTheDocument();
    expect(screen.getByDisplayValue('7')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('[object Object]')).toBeNull();

    // masked 秘密字段必须以密码输入框渲染
    const passwordInputs = document.querySelectorAll('input[type="password"]');
    expect(passwordInputs.length).toBe(1);
  });

  it('保存时仅提交变更的键，未改动的掩码秘密字段不回传', async () => {
    render(<AdminConfigPage />);

    const input = await screen.findByDisplayValue('aliyun');
    fireEvent.change(input, { target: { value: 'tencent' } });
    fireEvent.click(screen.getByRole('button', { name: '保存配置' }));

    await waitFor(() => expect(putMock).toHaveBeenCalledTimes(1));
    expect(putMock).toHaveBeenCalledWith('/admin/config', { 'sms.provider': 'tencent' });
  });
});
