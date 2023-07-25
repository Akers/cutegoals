import request from '@/utils/request'

// 查询用户家庭关联列表
export function listFamilyuser(query) {
  return request({
    url: '/family/familyuser/list',
    method: 'get',
    params: query
  })
}

// 查询用户家庭关联详细
export function getFamilyuser(familyUserId) {
  return request({
    url: '/family/familyuser/' + familyUserId,
    method: 'get'
  })
}

// 新增用户家庭关联
export function addFamilyuser(data) {
  return request({
    url: '/family/familyuser',
    method: 'post',
    data: data
  })
}

// 修改用户家庭关联
export function updateFamilyuser(data) {
  return request({
    url: '/family/familyuser',
    method: 'put',
    data: data
  })
}

// 删除用户家庭关联
export function delFamilyuser(familyUserId) {
  return request({
    url: '/family/familyuser/' + familyUserId,
    method: 'delete'
  })
}
