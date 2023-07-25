import request from '@/utils/request'

// 查询家庭信息管理列表
export function listFamilyadmin(query) {
  return request({
    url: '/family/admin/list',
    method: 'get',
    params: query
  })
}

// 查询家庭信息管理详细
export function getFamilyadmin(familyId) {
  return request({
    url: '/family/admin/' + familyId,
    method: 'get'
  })
}

// 新增家庭信息管理
export function addFamilyadmin(data) {
  return request({
    url: '/family/admin',
    method: 'post',
    data: data
  })
}

// 修改家庭信息管理
export function updateFamilyadmin(data) {
  return request({
    url: '/family/admin',
    method: 'put',
    data: data
  })
}

// 删除家庭信息管理
export function delFamilyadmin(familyId) {
  return request({
    url: '/family/admin/' + familyId,
    method: 'delete'
  })
}

// 查询用户家庭关联列表
export function listFamilyuser(query) {
  return request({
    url: '/family/admin/familyuser/list',
    method: 'get',
    params: query
  })
}

// 查询用户家庭关联详细
export function getFamilyuser(familyUserId) {
  return request({
    url: '/family/admin/familyuser/' + familyUserId,
    method: 'get'
  })
}

// 新增用户家庭关联
export function addFamilyuser(data) {
  return request({
    url: '/family/admin/familyuser',
    method: 'post',
    data: data
  })
}

// 修改用户家庭关联
export function updateFamilyuser(data) {
  return request({
    url: '/family/admin/familyuser',
    method: 'put',
    data: data
  })
}

// 删除用户家庭关联
export function delFamilyuser(familyUserId) {
  return request({
    url: '/family/admin/familyuser/' + familyUserId,
    method: 'delete'
  })
}