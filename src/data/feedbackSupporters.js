/** 提交有效反馈的用户原始记录（生产构建时会经混淆插件处理） */
const RAW_SUBMISSIONS = [
  'daemon',
  'daemon',
  'daemon',
  'daemon',
  '爱财的熊熊',
  'DR',
  'Zxi2233',
  'As',
  '呆呆',
  '呆呆',
  '呆呆',
  '执念太深',
  '苹果账户客服',
  '幼儿园老大',
  '幼儿园老大',
  '17',
  '′ 沐燃',
  '17',
  'A黑科技软件（备注名称）',
  'Tong',
  '苦不聊生阳光',
  '苦不聊生阳光',
  '17',
  '′ 沐燃',
  '树叶',
  '苦不聊生阳光',
  '桀翕',
  'Saxophone',
  '执念太深',
  '执念太深',
  '执念太深',
]

/** 去重并按贡献次数降序，同次数按首次出现顺序 */
export function getFeedbackSupporters() {
  const counts = new Map()
  const firstIndex = new Map()
  RAW_SUBMISSIONS.forEach((name, index) => {
    counts.set(name, (counts.get(name) || 0) + 1)
    if (!firstIndex.has(name)) {
      firstIndex.set(name, index)
    }
  })
  return [...counts.entries()]
    .sort((a, b) => {
      if (b[1] !== a[1]) return b[1] - a[1]
      return firstIndex.get(a[0]) - firstIndex.get(b[0])
    })
    .map(([name, count]) => ({ name, count }))
}
