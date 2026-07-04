export const ADMIN_PERMISSION_LABELS = {
  overview: '概览',
  keys: '卡密管理',
  pricing: '定价管理',
  orders: '订单管理',
  api: 'API管理',
  api_open: 'API开放中心',
  users: '用户管理',
  notification: '通知管理',
  settings: '系统设置',
  maintenance: '系统维护',
  system_info: '系统信息',
  admins: '管理员管理',
  logs: '操作日志'
}

export function hasAdminPermission(userInfo, code) {
  if (!userInfo || userInfo.role !== 'admin') return false
  if (userInfo.isSuper === true) return true
  const perms = userInfo.permissions
  return Array.isArray(perms) && perms.includes(code)
}

export function firstAllowedTab(userInfo) {
  const order = Object.keys(ADMIN_PERMISSION_LABELS)
  for (const code of order) {
    if (hasAdminPermission(userInfo, code)) {
      return code
    }
  }
  return 'overview'
}
