/** 卡密导出：列定义、筛选与行处理（卡密管理 / 专属卡密共用） */

export const CARD_EXPORT_COLUMNS = [
  { key: 'id', label: '序号' },
  { key: 'card_key', label: '卡密' },
  { key: 'storage_type', label: '加密方式' },
  { key: 'encrypted_key', label: '传输加密串' },
  { key: 'user_info', label: '使用者' },
  { key: 'remaining_time', label: '剩余时间' },
  { key: 'remaining_count', label: '剩余次数' },
  { key: 'expire_time', label: '过期时间' },
  { key: 'card_type', label: '卡密类型' },
  { key: 'status', label: '状态' },
  { key: 'create_time', label: '创建时间' },
  { key: 'machine_code', label: '机器码' },
  { key: 'is_exclusive', label: '是否专属' },
  { key: 'api_key_id', label: '专属API Key' },
]

export const EXPORT_STORAGE_SCOPE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'encrypted', label: '仅加密卡密' },
  { value: 'simple', label: '仅简单卡密' },
]

export const EXPORT_USAGE_SCOPE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'unused', label: '仅未使用' },
  { value: 'used', label: '已使用' },
]

export const EXPORT_FORMAT_OPTIONS = [
  { value: 'xlsx', label: 'Excel (.xlsx)' },
  { value: 'csv', label: 'CSV (.csv)' },
]

export function getExportColumnLabel(key) {
  return CARD_EXPORT_COLUMNS.find((c) => c.key === key)?.label || key
}

export function obfuscateCardKey(rawKey) {
  if (!rawKey) return rawKey
  try {
    const encoded = encodeURIComponent(rawKey)
    const reversed = encoded.split('').reverse().join('')
    const base64 = btoa(reversed)
    return base64.replace(/e/g, '*').replace(/U/g, '-')
  } catch (e) {
    console.error('Obfuscation failed:', e)
    return rawKey
  }
}

export function isPermanentDurationUnit(unit) {
  return unit === 'permanent'
}

export function getCardKeyText(item) {
  return (item?.card_key || item?.cardKey || item?.code || '').toString()
}

export function getCardTypeText(cardType) {
  const typeMap = {
    time: '时间卡密',
    count: '次数卡密',
  }
  return typeMap[cardType] || cardType
}

export function getStatusText(status) {
  const statusMap = {
    0: '未使用',
    1: '已使用',
    2: '已暂停',
    4: '已合并(续期)',
    unused: '未使用',
    used: '已使用',
    merged: '已合并(续期)',
  }
  return statusMap[status] ?? status
}

export function formatDurationSpec(key) {
  if (isPermanentDurationUnit(key?.duration_unit)) return '永久'
  const amount = key?.duration ?? 0
  const unit = key?.duration_unit === 'hours' ? 'hours' : 'days'
  return unit === 'hours' ? `${amount}小时` : `${amount}天`
}

export function parseCreateTimeMs(key) {
  const raw = key?.create_time ?? key?.createTime
  if (raw == null || raw === '') return null
  const t = new Date(raw).getTime()
  return Number.isFinite(t) ? t : null
}

export function getCardNumericStatus(item) {
  if (typeof item?.status === 'number') return Number(item.status)
  if (item?.statusCode != null) return Number(item.statusCode)
  const map = { unused: 0, used: 1, merged: 4, disabled: 2 }
  return map[item?.status] ?? item?.status
}

export function mapApiKeyCard(c) {
  const storage_type = c.storage_type || (c.encryption_type === 'simple' ? 'simple' : 'encrypted')
  const statusCode = Number(c.status)
  let statusDisplay = 'used'
  if (statusCode === 0) statusDisplay = 'unused'
  else if (statusCode === 4) statusDisplay = 'merged'

  const durationUnit = c.duration_unit || 'days'
  const typeLabel = c.card_type === 'time' ? '时间卡' : '次数卡'
  let valueLabel
  if (c.card_type === 'time') {
    if (durationUnit === 'permanent') valueLabel = '永久'
    else if (durationUnit === 'hours') valueLabel = `${c.duration}小时`
    else valueLabel = `${c.duration}天`
  } else {
    valueLabel = `${c.total_count}次`
  }

  return {
    id: c.id,
    code: c.card_key,
    card_key: c.card_key,
    storage_type,
    status: statusDisplay,
    statusCode,
    expiryDate: c.expire_time,
    type: typeLabel,
    value: valueLabel,
    usedBy: c.device_id ? `Device ${c.device_id.substring(0, 6)}...` : null,
    create_time: c.create_time,
    expire_time: c.expire_time,
    card_type: c.card_type,
    duration: c.duration,
    duration_unit: durationUnit,
    device_id: c.device_id,
    ip_address: c.ip_address,
    machine_code: c.machine_code,
    remaining_count: c.remaining_count,
    total_count: c.total_count,
    api_key_id: c.api_key_id,
  }
}

export function filterCardsForExport(cards, options = {}) {
  const {
    storageScope = 'all',
    usageScope = 'all',
    createDateStart = '',
    createDateEnd = '',
  } = options

  let list = cards || []

  if (storageScope === 'encrypted') {
    list = list.filter((k) => k.storage_type !== 'simple')
  } else if (storageScope === 'simple') {
    list = list.filter((k) => k.storage_type === 'simple')
  }

  if (usageScope === 'unused') {
    list = list.filter((k) => getCardNumericStatus(k) === 0)
  } else if (usageScope === 'used') {
    list = list.filter((k) => [1, 2, 4].includes(getCardNumericStatus(k)))
  }

  if (createDateStart) {
    const start = new Date(`${createDateStart}T00:00:00`).getTime()
    list = list.filter((k) => {
      const ms = parseCreateTimeMs(k)
      return ms != null && ms >= start
    })
  }

  if (createDateEnd) {
    const end = new Date(`${createDateEnd}T23:59:59.999`).getTime()
    list = list.filter((k) => {
      const ms = parseCreateTimeMs(k)
      return ms != null && ms <= end
    })
  }

  return list
}

export function processCardExportData(data, selectedColumns, formatDate) {
  return data.map((item) => {
    const processed = {}

    if (selectedColumns.includes('id')) processed.id = item.id
    if (selectedColumns.includes('card_key')) processed.card_key = getCardKeyText(item)
    if (selectedColumns.includes('storage_type')) {
      processed.storage_type = item.storage_type === 'simple' ? '简单' : '加密'
    }
    if (selectedColumns.includes('encrypted_key')) {
      processed.encrypted_key = item.storage_type === 'simple'
        ? '-'
        : obfuscateCardKey(getCardKeyText(item))
    }
    if (selectedColumns.includes('user_info')) {
      processed.user_info = item.device_id
        ? `Device: ${item.device_id}`
        : (item.ip_address ? `IP: ${item.ip_address}` : '-')
    }
    if (selectedColumns.includes('remaining_time')) {
      if (item.card_type === 'time') {
        if (isPermanentDurationUnit(item.duration_unit)) {
          processed.remaining_time = item.expire_time ? '永久' : '永久（未激活）'
        } else if (item.expire_time) {
          const ms = new Date(item.expire_time).getTime() - Date.now()
          if (ms <= 0) {
            processed.remaining_time = '已过期'
          } else {
            const d = Math.floor(ms / 86400000)
            const h = Math.floor((ms % 86400000) / 3600000)
            const m = Math.floor((ms % 3600000) / 60000)
            processed.remaining_time = d > 0 ? `${d}天${h}小时${m}分钟` : `${h}小时${m}分钟`
          }
        } else {
          processed.remaining_time = `${formatDurationSpec(item)}（未激活）`
        }
      } else {
        processed.remaining_time = '-'
      }
    }
    if (selectedColumns.includes('remaining_count')) {
      processed.remaining_count = item.card_type === 'count'
        ? `${item.remaining_count}/${item.total_count}`
        : '-'
    }
    if (selectedColumns.includes('expire_time')) {
      processed.expire_time = item.expire_time
        ? formatDate(item.expire_time)
        : (item.card_type === 'time' ? '未激活' : '-')
    }
    if (selectedColumns.includes('create_time')) {
      processed.create_time = formatDate(item.create_time)
    }
    if (selectedColumns.includes('card_type')) {
      processed.card_type = getCardTypeText(item.card_type)
    }
    if (selectedColumns.includes('status')) {
      processed.status = getStatusText(getCardNumericStatus(item))
    }
    if (selectedColumns.includes('machine_code')) {
      processed.machine_code = item.machine_code || '-'
    }
    if (selectedColumns.includes('is_exclusive')) {
      processed.is_exclusive = item.api_key_id ? '是' : '否'
    }
    if (selectedColumns.includes('api_key_id')) {
      processed.api_key_id = item.api_key_id || '-'
    }

    return processed
  })
}
