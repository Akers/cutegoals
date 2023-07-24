import request from '@/utils/request'

// 查询家庭用户关联表列表
export function listFamilyUser(query) {
  return request({
    url: '/family/FamilyUser/list',
    method: 'get',
    params: query
  })
}

// 查询家庭用户关联表详细
export function getFamilyUser(id) {
  return request({
    url: '/family/FamilyUser/' + id,
    method: 'get'
  })
}

// 新增家庭用户关联表
export function addFamilyUser(data) {
  return request({
    url: '/family/FamilyUser',
    method: 'post',
    data: data
  })
}

// 修改家庭用户关联表
export function updateFamilyUser(data) {
  return request({
    url: '/family/FamilyUser',
    method: 'put',
    data: data
  })
}

// 删除家庭用户关联表
export function delFamilyUser(id) {
  return request({
    url: '/family/FamilyUser/' + id,
    method: 'delete'
  })
}
