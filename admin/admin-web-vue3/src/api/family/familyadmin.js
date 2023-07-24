import request from '@/utils/request'

// 查询家庭信息列表
export function listFamily(query) {
  return request({
    url: '/family/admin/list',
    method: 'get',
    params: query
  })
}

// 查询家庭信息详细
export function getFamily(id) {
  return request({
    url: '/family/admin/' + id,
    method: 'get'
  })
}

// 新增家庭信息
export function addFamily(data) {
  return request({
    url: '/family/admin',
    method: 'post',
    data: data
  })
}

// 修改家庭信息
export function updateFamily(data) {
  return request({
    url: '/family/admin',
    method: 'put',
    data: data
  })
}

// 删除家庭信息
export function delFamily(id) {
  return request({
    url: '/family/admin/' + id,
    method: 'delete'
  })
}
