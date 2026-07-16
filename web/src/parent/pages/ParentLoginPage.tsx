import { useState } from 'react';
import { history } from 'umi';
import { getClient } from '@shared/api';
import { useAuth, maskPhone } from '@shared/auth';
import { Button, Input } from 'antd';
import { MobileOutlined, LockOutlined } from '@ant-design/icons';
import { FormField } from '@shared/components';
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
    <div className="cg-login-bg cg-login-bg--parent">
      <div className="cg-login-card">
        <div className="cg-login-logo" aria-hidden="true">👨‍👩‍👧</div>
        <h1 className="cg-login-title">家长登录</h1>
        <p className="cg-login-subtitle">使用手机号与密码登录</p>
        {error && (
          <div className="cg-login-error" role="alert">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="cg-login-form">
          <FormField label="手机号" htmlFor="parent-phone">
            <Input
              id="parent-phone"
              type="tel"
              autoComplete="username"
              placeholder="11 位手机号"
              prefix={<MobileOutlined />}
              {...phone.inputProps}
            />
          </FormField>
          <FormField label="密码" htmlFor="parent-password">
            <Input
              id="parent-password"
              type="password"
              autoComplete="current-password"
              placeholder="请输入密码"
              prefix={<LockOutlined />}
              {...password.inputProps}
            />
          </FormField>
          <Button type="primary" htmlType="submit" loading={loading} className="cg-login-submit">
            登录
          </Button>
          <p className="cg-login-footer">
            首次部署请使用初始化向导
          </p>
        </form>
      </div>
    </div>
  );
}

export default ParentLoginPage;
