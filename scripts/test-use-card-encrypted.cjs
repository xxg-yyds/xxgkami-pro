#!/usr/bin/env node
/**
 * 最简测试：入参/出参加密后调用 use_card
 */
const CryptoJS = require('crypto-js')

const BASE = process.env.API_BASE || 'http://127.0.0.1:5173/api'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS || '123456'

const DEFAULT_API_KEY = 'fc2f36a70be4499cae351b788622a335'
const DEFAULT_CARD_KEY =
  'uw9DJGfMpnvi7nP6$YXJF/oQKnBKbmvQwAhQRHdRUBAXbZm9ED7d3EJupcH8UIwtwP8LdgOZMZOAJzMCiO0G/v9sz9l+dRs5rncJ10bcnbk9fsnImLTzaOcbEYJ8tDn/c6h8+ewg1nDGz664hxYgvo4QsiLaSp7H4apdyedHHE3GR8HGc0CIhLEV2CXpUrf5dysBATv+gyUAu7Ft49o0cksrGGa0xEOkMX8MerlYWNi4u$MGUCMQDXz+MC11HSs/HGitY7wjiOkqoyT8GSZgTIeQSzDY0rFZh70WTzJahXc8J/jfS9NOACMCyQ1qp2kI4xKtWj9epD4nS6GgFHxnZb295wT3UmWc3Jpa/o7SlaNvs0Kw1Fj3lPsw=='

function parseArgs(argv) {
  const out = { apiKey: DEFAULT_API_KEY, cardKey: DEFAULT_CARD_KEY }
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--api-key' && argv[i + 1]) out.apiKey = argv[++i]
    else if (argv[i] === '--card-key' && argv[i + 1]) out.cardKey = argv[++i]
  }
  return out
}

async function request(path, { method = 'GET', headers = {}, body } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  try {
    return { status: res.status, json: JSON.parse(text) }
  } catch {
    return { status: res.status, json: { raw: text } }
  }
}

function encryptPlaintext(plaintext, keyBase64, ivBase64) {
  const key = CryptoJS.enc.Base64.parse(keyBase64)
  const iv = CryptoJS.enc.Base64.parse(ivBase64)
  const encrypted = CryptoJS.AES.encrypt(CryptoJS.enc.Utf8.parse(plaintext), key, {
    iv,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  })
  return encrypted.ciphertext.toString(CryptoJS.enc.Base64)
}

function decryptPayload(encryptedPayload, keyBase64, ivBase64) {
  const key = CryptoJS.enc.Base64.parse(keyBase64)
  const iv = CryptoJS.enc.Base64.parse(ivBase64)
  const cipherParams = CryptoJS.lib.CipherParams.create({
    ciphertext: CryptoJS.enc.Base64.parse(encryptedPayload),
  })
  const decrypted = CryptoJS.AES.decrypt(cipherParams, key, {
    iv,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  })
  const plaintext = decrypted.toString(CryptoJS.enc.Utf8)
  if (!plaintext) throw new Error('解密失败')
  return JSON.parse(plaintext)
}

function decryptResponse(rawJson, cfg) {
  if (!rawJson?.response_encrypted || !rawJson?.encrypted_payload) {
    return rawJson
  }
  return decryptPayload(rawJson.encrypted_payload, cfg.key, cfg.iv)
}

async function main() {
  const { apiKey, cardKey } = parseArgs(process.argv.slice(2))

  console.log('1) 管理员登录，读取 Key / IV …')
  const login = await request('/auth/admin/login', {
    method: 'POST',
    body: { username: ADMIN_USER, password: ADMIN_PASS },
  })
  if (!login.json?.success) {
    console.error('登录失败:', login.json)
    process.exit(1)
  }
  const token = login.json.data.token

  const cfgRes = await request('/admin/open-api-encryption', {
    headers: { Authorization: `Bearer ${token}` },
  })
  const cfg = cfgRes.json?.data
  if (!cfg?.request_enabled) {
    console.error('当前未开启入参加密，请先在「API 开放中心 → 参数加密」打开入参开关')
    process.exit(1)
  }
  if (!cfg.key || !cfg.iv) {
    console.error('缺少 Key / IV 配置')
    process.exit(1)
  }

  console.log(`   入参加密: ${cfg.request_enabled ? '开' : '关'} | 出参加密: ${cfg.response_enabled ? '开' : '关'}`)

  console.log('2) 加密入参 …')
  const params = { api_key: apiKey, card_key: cardKey }
  const encryptedPayload = encryptPlaintext(JSON.stringify(params), cfg.key, cfg.iv)

  console.log('3) 调用 use_card …')
  const result = await request(`/v1/use_card?encrypted_payload=${encodeURIComponent(encryptedPayload)}`)
  const decoded = decryptResponse(result.json, cfg)

  console.log('\n=== 原始响应 ===')
  console.log('HTTP', result.status)
  console.log(JSON.stringify(result.json, null, 2))

  console.log('\n=== 解密后响应 ===')
  console.log(JSON.stringify(decoded, null, 2))

  if (decoded?.success) {
    console.log('\n✅ 调用成功（解密后 success=true）')
  } else if (String(result.json?.message || '').includes('encrypted_payload')) {
    console.log('\n❌ 仍被明文拦截')
    process.exit(1)
  } else {
    console.log('\n⚠️ 入参解密成功，业务层返回上述 message')
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
