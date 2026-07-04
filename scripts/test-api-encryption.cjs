#!/usr/bin/env node
/**
 * 测试开放平台入参/出参加密（独立开关）
 * 用法: node scripts/test-api-encryption.cjs
 */
const CryptoJS = require('crypto-js')

const BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS || '123456'

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

function encryptParams(params, key, iv) {
  const keyWord = CryptoJS.enc.Base64.parse(key)
  const ivWord = CryptoJS.enc.Base64.parse(iv)
  const encrypted = CryptoJS.AES.encrypt(CryptoJS.enc.Utf8.parse(JSON.stringify(params)), keyWord, {
    iv: ivWord,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  })
  return encrypted.ciphertext.toString(CryptoJS.enc.Base64)
}

function decryptPayload(payload, key, iv) {
  const keyWord = CryptoJS.enc.Base64.parse(key)
  const ivWord = CryptoJS.enc.Base64.parse(iv)
  const cipherParams = CryptoJS.lib.CipherParams.create({
    ciphertext: CryptoJS.enc.Base64.parse(payload),
  })
  const plain = CryptoJS.AES.decrypt(cipherParams, keyWord, {
    iv: ivWord,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  }).toString(CryptoJS.enc.Utf8)
  return JSON.parse(plain)
}

function decryptResponse(raw, cfg) {
  if (!raw?.response_encrypted) return raw
  return decryptPayload(raw.encrypted_payload, cfg.key, cfg.iv)
}

function pass(label) {
  console.log(`  ✅ ${label}`)
}

function fail(label, detail) {
  console.log(`  ❌ ${label}`)
  if (detail) console.log(`     ${detail}`)
  process.exitCode = 1
}

async function saveCfg(authHeaders, patch) {
  return request('/admin/open-api-encryption', {
    method: 'PUT',
    headers: authHeaders,
    body: patch,
  })
}

async function main() {
  console.log('=== 入参/出参加密独立开关测试 ===\n')

  const login = await request('/auth/admin/login', {
    method: 'POST',
    body: { username: ADMIN_USER, password: ADMIN_PASS },
  })
  if (!login.json?.success) {
    fail('管理员登录失败', JSON.stringify(login.json))
    return
  }
  const authHeaders = { Authorization: `Bearer ${login.json.data.token}` }
  pass('管理员登录成功')

  await saveCfg(authHeaders, { request_enabled: false, response_enabled: false, regenerate_key: false })

  console.log('\n--- A. 全部关闭 ---')
  const pingA = await request('/v1/ping')
  if (pingA.json?.success && !pingA.json?.response_encrypted) {
    pass('ping 明文响应')
  } else {
    fail('ping 应返回明文', JSON.stringify(pingA.json))
  }

  console.log('\n--- B. 仅出参加密 ---')
  await saveCfg(authHeaders, { response_enabled: true })
  let cfg = (await request('/admin/open-api-encryption', { headers: authHeaders })).json.data
  const pingB = await request('/v1/ping')
  if (pingB.json?.response_encrypted && pingB.json?.encrypted_payload) {
    const decoded = decryptResponse(pingB.json, cfg)
    if (decoded?.success && decoded?.message === 'pong') {
      pass('ping 出参密文可解密为 pong')
    } else {
      fail('出参解密结果异常', JSON.stringify(decoded))
    }
  } else {
    fail('ping 应返回 encrypted_payload', JSON.stringify(pingB.json))
  }

  const plainVerifyB = await request('/v1/verify_api_key?api_key=INVALID_TEST_KEY')
  if (plainVerifyB.status === 403) {
    pass('仅出参加密时，入参仍可明文')
  } else {
    fail('入参不应被拦截', JSON.stringify(plainVerifyB.json))
  }

  console.log('\n--- C. 仅入参加密 ---')
  await saveCfg(authHeaders, { request_enabled: true, response_enabled: false })
  const blocked = await request('/v1/verify_api_key?api_key=INVALID_TEST_KEY')
  if (blocked.status === 400 && String(blocked.json?.message || '').includes('encrypted_payload')) {
    pass('明文入参被拦截')
  } else {
    fail('明文入参应被拦截', JSON.stringify(blocked.json))
  }

  cfg = (await request('/admin/open-api-encryption', { headers: authHeaders })).json.data
  const enc = encryptParams({ api_key: 'INVALID_TEST_KEY' }, cfg.key, cfg.iv)
  const encVerify = await request(`/v1/verify_api_key?encrypted_payload=${encodeURIComponent(enc)}`)
  if (encVerify.status === 403 && encVerify.json?.success === false && !encVerify.json?.response_encrypted) {
    pass('加密入参 + 明文出参正常')
  } else {
    fail('加密入参测试异常', JSON.stringify(encVerify.json))
  }

  console.log('\n--- D. 入参 + 出参均加密 ---')
  await saveCfg(authHeaders, { response_enabled: true })
  cfg = (await request('/admin/open-api-encryption', { headers: authHeaders })).json.data
  const enc2 = encryptParams({ api_key: 'INVALID_TEST_KEY' }, cfg.key, cfg.iv)
  const encBoth = await request(`/v1/verify_api_key?encrypted_payload=${encodeURIComponent(enc2)}`)
  if (encBoth.json?.response_encrypted) {
    const decoded = decryptResponse(encBoth.json, cfg)
    if (decoded?.success === false && decoded?.code === 403) {
      pass('入参/出参均加密，解密后得到 Invalid API Key')
    } else {
      fail('出参解密异常', JSON.stringify(decoded))
    }
  } else {
    fail('应返回出参密文', JSON.stringify(encBoth.json))
  }

  await saveCfg(authHeaders, { request_enabled: false, response_enabled: false })
  pass('已恢复：入参/出参均关闭')

  console.log('\n=== 测试完成 ===')
  console.log(process.exitCode ? '存在失败项 ❌' : '全部通过 ✅')
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
