import CryptoJS from 'crypto-js'

/**
 * 将业务参数 JSON 加密为 encrypted_payload（AES-256-CBC / PKCS7 / Base64）
 * @param {Record<string, unknown>} params
 * @param {{ key: string, iv: string }} cryptoConfig Base64 编码的 key(32B) 与 iv(16B)
 */
export function encryptOpenApiParams(params, cryptoConfig) {
  const keyBase64 = cryptoConfig?.key
  const ivBase64 = cryptoConfig?.iv
  if (!keyBase64 || !ivBase64) {
    throw new Error('缺少 AES Key 或 IV')
  }

  const keyWord = CryptoJS.enc.Base64.parse(keyBase64)
  const ivWord = CryptoJS.enc.Base64.parse(ivBase64)
  const plaintext = JSON.stringify(params ?? {})

  const encrypted = CryptoJS.AES.encrypt(CryptoJS.enc.Utf8.parse(plaintext), keyWord, {
    iv: ivWord,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  })

  return encrypted.ciphertext.toString(CryptoJS.enc.Base64)
}

export function decryptOpenApiParams(encryptedPayload, cryptoConfig) {
  const keyWord = CryptoJS.enc.Base64.parse(cryptoConfig.key)
  const ivWord = CryptoJS.enc.Base64.parse(cryptoConfig.iv)
  const cipherParams = CryptoJS.lib.CipherParams.create({
    ciphertext: CryptoJS.enc.Base64.parse(encryptedPayload),
  })
  const decrypted = CryptoJS.AES.decrypt(cipherParams, keyWord, {
    iv: ivWord,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  })
  const plaintext = decrypted.toString(CryptoJS.enc.Utf8)
  if (!plaintext) {
    throw new Error('解密失败，请检查 Key / IV / 密文')
  }
  return JSON.parse(plaintext)
}

export function decryptOpenApiPayload(encryptedPayload, cryptoConfig) {
  return decryptOpenApiParams(encryptedPayload, cryptoConfig)
}

export function decryptOpenApiResponse(responseJson, cryptoConfig) {
  if (!responseJson?.response_encrypted || !responseJson?.encrypted_payload) {
    return responseJson
  }
  return decryptOpenApiParams(responseJson.encrypted_payload, cryptoConfig)
}

export function buildEncryptedRequestBody(params, cryptoConfig, payloadField = 'encrypted_payload') {
  return {
    [payloadField]: encryptOpenApiParams(params, cryptoConfig),
  }
}

export function buildEncryptedQuery(params, cryptoConfig, payloadField = 'encrypted_payload') {
  return `${payloadField}=${encodeURIComponent(encryptOpenApiParams(params, cryptoConfig))}`
}
