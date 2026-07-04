/**
 * 开放平台 v1 接口：开通授权 / 解绑设备码 / 健康检查 / 生成卡密 / 创建 API Key 调用示例
 * BASE_URL 为 API 根路径（含 /api，勿以 / 结尾）
 */

export const API_HEALTH_INTRO = `健康检查接口用于探测开放平台服务是否正常运行，无需鉴权。
ping 与 verify_api_key 可额外验证 API Key 是否有效。`

export const API_CREATE_CARDS_INTRO = `通过已鉴权的 API Key 批量生成卡密，须同时携带开放平台 Token（Authorization: Bearer <open_token>）。
exclusive=false 生成普通全局卡密（任意 API Key 可核销）；exclusive=true 生成专属卡密（仅当前 API Key 可核销）。
use_encrypted=true 时使用高级加密卡密，否则为简单明文卡密。单次最多 100 条。`

export const API_CREATE_API_KEY_INTRO = `创建新的 API Key，须携带开放平台 Token（Header: Authorization: Bearer <open_token> 或 X-Open-Token）。
Token 可在「API 开放中心 → 创建 Token」中生成，仅创建时显示一次。
响应中的 api_key 同样仅返回一次，请妥善保存。`

export const API_ACTIVATE_INTRO = `开通授权接口用于核销/激活卡密，并在响应中返回剩余时长、到期时间或剩余次数。
与 use_card 行为一致，但会额外返回 data 字段。`

export const API_UNBIND_INTRO = `解绑设备码接口用于清空卡密已绑定的 machine_code 与 device_id。
须使用 API Key 鉴权；专属卡密须使用对应 API Key。若卡密开启了「原设备解绑」，须传入 machine_code。`

export const API_ACTIVATE_EXAMPLES = [
  {
    id: 'curl',
    label: 'cURL',
    code: `# GET
curl -G "BASE_URL/v1/activate" \\
  --data-urlencode "api_key=YOUR_API_KEY" \\
  --data-urlencode "card_key=YOUR_CARD_KEY" \\
  --data-urlencode "machine_code=YOUR_MACHINE_CODE"

# POST JSON
curl -X POST "BASE_URL/v1/activate" \\
  -H "Content-Type: application/json" \\
  -d '{"api_key":"YOUR_API_KEY","card_key":"YOUR_CARD_KEY","machine_code":"YOUR_MACHINE_CODE"}'`
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    code: `const res = await fetch('BASE_URL/v1/activate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    api_key: 'YOUR_API_KEY',
    card_key: 'YOUR_CARD_KEY',
    machine_code: 'YOUR_MACHINE_CODE'
  })
});
const data = await res.json();
console.log(data.data?.remaining_time, data.data?.remaining_count);`
  },
  {
    id: 'python',
    label: 'Python',
    code: `import requests

r = requests.post('BASE_URL/v1/activate', json={
    'api_key': 'YOUR_API_KEY',
    'card_key': 'YOUR_CARD_KEY',
    'machine_code': 'YOUR_MACHINE_CODE'
})
print(r.json())`
  }
]

export const API_UNBIND_EXAMPLES = [
  {
    id: 'curl',
    label: 'cURL',
    code: `# GET
curl -G "BASE_URL/v1/unbind_device" \\
  --data-urlencode "api_key=YOUR_API_KEY" \\
  --data-urlencode "card_key=YOUR_CARD_KEY" \\
  --data-urlencode "machine_code=YOUR_MACHINE_CODE"

# POST JSON
curl -X POST "BASE_URL/v1/unbind_device" \\
  -H "Content-Type: application/json" \\
  -d '{"api_key":"YOUR_API_KEY","card_key":"YOUR_CARD_KEY","machine_code":"YOUR_MACHINE_CODE"}'`
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    code: `const res = await fetch('BASE_URL/v1/unbind_device', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    api_key: 'YOUR_API_KEY',
    card_key: 'YOUR_CARD_KEY',
    machine_code: 'YOUR_MACHINE_CODE'
  })
});
console.log(await res.json());`
  },
  {
    id: 'python',
    label: 'Python',
    code: `import requests

r = requests.post('BASE_URL/v1/unbind_device', json={
    'api_key': 'YOUR_API_KEY',
    'card_key': 'YOUR_CARD_KEY',
    'machine_code': 'YOUR_MACHINE_CODE'
})
print(r.json())`
  }
]

export const API_HEALTH_EXAMPLES = [
  {
    id: 'curl',
    label: 'cURL',
    code: `# 健康检查（无需鉴权）
curl "BASE_URL/v1/health"

# Ping（可选验证 API Key）
curl -G "BASE_URL/v1/ping" --data-urlencode "api_key=YOUR_API_KEY"

# 验证 API Key
curl -G "BASE_URL/v1/verify_api_key" --data-urlencode "api_key=YOUR_API_KEY"`
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    code: `// 健康检查
const health = await fetch('BASE_URL/v1/health').then(r => r.json());
console.log(health.data?.status);

// 验证 API Key
const verify = await fetch('BASE_URL/v1/verify_api_key?api_key=YOUR_API_KEY').then(r => r.json());
console.log(verify.data?.valid, verify.data?.key_name);`
  },
  {
    id: 'python',
    label: 'Python',
    code: `import requests

print(requests.get('BASE_URL/v1/health').json())
print(requests.get('BASE_URL/v1/verify_api_key', params={'api_key': 'YOUR_API_KEY'}).json())`
  }
]

export const API_CREATE_CARDS_EXAMPLES = [
  {
    id: 'curl',
    label: 'cURL',
    code: `# 生成 5 条普通时间卡（30 天）
curl -X POST "BASE_URL/v1/create_cards" \\
  -H "Content-Type: application/json" \\
  -d '{
    "open_token": "YOUR_OPEN_TOKEN",
    "api_key": "YOUR_API_KEY",
    "exclusive": false,
    "card_type": "time",
    "count": 5,
    "duration": 30,
    "duration_unit": "days"
  }'

# 生成 3 条专属 API Key 卡密
curl -X POST "BASE_URL/v1/create_cards" \\
  -H "Content-Type: application/json" \\
  -d '{
    "open_token": "YOUR_OPEN_TOKEN",
    "api_key": "YOUR_API_KEY",
    "exclusive": true,
    "card_type": "time",
    "count": 3,
    "duration": 7,
    "duration_unit": "days"
  }'`
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    code: `const res = await fetch('BASE_URL/v1/create_cards', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    open_token: 'YOUR_OPEN_TOKEN',
    api_key: 'YOUR_API_KEY',
    exclusive: true,
    card_type: 'time',
    count: 3,
    duration: 7,
    duration_unit: 'days'
  })
});
const data = await res.json();
console.log(data.data?.cards?.map(c => c.card_key));`
  },
  {
    id: 'python',
    label: 'Python',
    code: `import requests

r = requests.post(
    'BASE_URL/v1/create_cards',
    json={
    'open_token': 'YOUR_OPEN_TOKEN',
    'api_key': 'YOUR_API_KEY',
    'exclusive': True,
    'card_type': 'time',
    'count': 3,
    'duration': 7,
    'duration_unit': 'days'
})
print(r.json()['data']['cards'])`
  }
]

export const API_CREATE_API_KEY_EXAMPLES = [
  {
    id: 'curl',
    label: 'cURL',
    code: `curl -X POST "BASE_URL/v1/create_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{
    "open_token": "YOUR_OPEN_TOKEN",
    "name": "第三方对接密钥",
    "description": "自动化创建",
    "enable_card_encryption": false
  }'`
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    code: `const res = await fetch('BASE_URL/v1/create_api_key', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    open_token: 'YOUR_OPEN_TOKEN',
    name: '第三方对接密钥',
    description: '自动化创建',
    enable_card_encryption: false
  })
});
const data = await res.json();
console.log(data.data?.api_key);`
  },
  {
    id: 'python',
    label: 'Python',
    code: `import requests

r = requests.post(
    'BASE_URL/v1/create_api_key',
    json={
        'open_token': 'YOUR_OPEN_TOKEN',
        'name': '第三方对接密钥',
        'description': '自动化创建',
        'enable_card_encryption': False
    }
)
print(r.json())`
  }
]
