import { useState } from 'react';
import { history } from 'umi';
import { getClient } from '@shared/api';
import { useAuth, maskPhone } from '@shared/auth';
import { Button, Input } from 'antd';
import { FormField, PageHeader } from '@shared/components';
import { useFormField } from '@shared/hooks/useApi';

export function ParentLoginPage() {
  const { login } = useAuth();
  const phone = useFormField();
  const password = useFormField();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!phone.value.trim() || !password.value) {
      setError('请输入手机号和密码');
      return;
    }
    setError(null);
    setLoading(true);
    const response = await getClient().post('/auth/login', {
      phone: phone.value.trim(),
      password: password.value,
    });
    setLoading(false);
    if (response.error) {
      // Do not enumerate account existence; use a uniform message.
      setError('手机号或密码错误');
    } else {
      const data = response.data as {
        accountId: string | number;
        phone?: string;
        roles: string[];
        familyId?: string | number;
        expiresIn?: number;
      };
      login({
        accountId: data.accountId,
        phone: maskPhone(data.phone ?? phone.value),
        roles: data.roles,
        familyId: data.familyId,
        expiresIn: data.expiresIn,
      });
      history.push('/parent');
    }
  };

  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center">
      <div className="w-full max-w-md cg-card p-6">
        <PageHeader title="家长登录" subtitle="使用手机号与密码登录" />
        {error && (
          <div className="mb-4 rounded-cg-md bg-cg-warning-bg px-4 py-3 text-sm text-cg-warning" role="alert">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <FormField label="手机号" htmlFor="parent-phone">
            <Input id="parent-phone" type="tel" autoComplete="username" placeholder="11 位手机号" {...phone.inputProps} />
          </FormField>
          <FormField label="密码" htmlFor="parent-password">
            <Input id="parent-password" type="password" autoComplete="current-password" placeholder="请输入密码" {...password.inputProps} />
          </FormField>
          <Button type="primary" htmlType="submit" loading={loading} className="w-full">
            登录
          </Button>
          <p className="text-center text-sm text-cg-text-muted">
            首次部署请使用初始化向导
          </p>
        </form>
      </div>
    </div>
  );
}

export default ParentLoginPage;
