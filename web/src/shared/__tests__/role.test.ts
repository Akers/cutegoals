import { describe, it, expect } from 'vitest';
import { normalizeRoles } from '@shared/role';

describe('normalizeRoles', () => {
  it('映射后端角色字符串为前端 Role 集合', () => {
    expect(normalizeRoles(['INSTANCE_ADMIN', 'PARENT'])).toEqual([
      'admin',
      'parent',
    ]);
    expect(normalizeRoles(['CHILD'])).toEqual(['child']);
  });

  it('兼容小写与 ROLE_ 前缀', () => {
    expect(normalizeRoles(['parent'])).toEqual(['parent']);
    expect(normalizeRoles(['ROLE_PARENT'])).toEqual(['parent']);
  });

  it('忽略未知角色与非字符串条目', () => {
    expect(normalizeRoles(['ANYTHING'])).toEqual([]);
    expect(normalizeRoles([null, 123, 'PARENT'] as unknown as string[])).toEqual([
      'parent',
    ]);
  });

  it('去重并处理空输入', () => {
    expect(normalizeRoles(['PARENT', 'parent', 'ROLE_PARENT'])).toEqual([
      'parent',
    ]);
    expect(normalizeRoles([])).toEqual([]);
    expect(normalizeRoles(undefined)).toEqual([]);
  });
});
