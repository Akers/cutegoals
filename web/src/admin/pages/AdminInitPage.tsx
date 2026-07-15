import { useEffect, useState } from 'react';
import { history } from 'umi';
import { getClient } from '@shared/api';
import { useAuth, maskPhone } from '@shared/auth';
import { Button, Input } from 'antd';
import { FormField, PageHeader } from '@shared/components';
import { useFormField } from '@shared/hooks/useApi';

export function AdminInitPage() {
  const { login } = useAuth();
  const token = useFormField();
  const phone = useFormField();
  const password = useFormField();
  const confirmPassword = useFormField();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // If already initialized, redirect to login
  useEffect(() => {
    getClient()
      .get<{ instanceStatus: string }>('/instance/status')
      .then((response) => {
        if (response.data?.instanceStatus === 'INITIALIZED') {
          history.replace('/admin/login');
        }
      });
  }, []);

  const validate = (): string | null => {
    if (!token.value.trim()) return '请输入初始化令牌';
    if (!phone.value.trim()) return '请输入手机号';
    if (!/^1\d{10}$/.test(phone.value)) return '手机号格式不正确';
    if (!password.value || password.value.length < 8) return '密码至少 8 位';
    if (password.value !== confirmPassword.value) return '两次输入的密码不一致';
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setLoading(true);
    const response = await getClient().post('/auth/initialize', {
      token: token.value.trim(),
      phone: phone.value.trim(),
      password: password.value,
    });
    setLoading(false);
    if (response.error) {
      setError(response.error.message ?? '初始化失败');
    } else {
      const data = response.data as {
        accountId: number;
        phone?: string;
        roles: string[];
        familyId?: number;
      };
      login({
        accountId: data.accountId,
        phone: maskPhone(data.phone ?? phone.value),
        roles: data.roles,
        familyId: data.familyId,
      });
      history.replace('/admin');
    }
  };

  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center">
      <div className="w-full max-w-md cg-card p-6">
        <PageHeader title="初始化 CuteGoals" subtitle="创建首位管理员账号与家庭" />
        {error && (
          <div className="mb-4 rounded-cg-md bg-cg-warning-bg px-4 py-3 text-sm text-cg-warning" role="alert">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <FormField label="初始化令牌" htmlFor="init-token">
            <Input id="init-token" type="text" placeholder="从部署命令获取" {...token.inputProps} />
          </FormField>
          <FormField label="管理员手机号" htmlFor="init-phone">
            <Input id="init-phone" type="tel" placeholder="11 位手机号" {...phone.inputProps} />
          </FormField>
          <FormField label="密码" htmlFor="init-password">
            <Input id="init-password" type="password" placeholder="至少 8 位" {...password.inputProps} />
          </FormField>
          <FormField label="确认密码" htmlFor="init-confirm">
            <Input id="init-confirm" type="password" placeholder="再次输入密码" {...confirmPassword.inputProps} />
          </FormField>
          <Button type="primary" htmlType="submit" loading={loading} className="w-full">
            完成初始化
          </Button>
        </form>
      </div>
    </div>
  );
}

export default AdminInitPage;
