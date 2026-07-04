export const ENCRYPTION_DOC_SCOPE = [
  '开放平台 v1：/api/v1/use_card、activate、unbind_device、create_cards 等',
  '自定义回调：/api/custom/{apiKey}/use',
  '旧版接口：/api/cards/use',
  '开放平台 Token 仍通过 Header 传递，不参与加解密',
]

export const ENCRYPTION_DOC_REQUEST_STEPS = [
  '将本次请求的所有业务参数组装为一个 JSON 对象（键名与接口文档一致）',
  '使用页面上方 Key、IV，按 AES-256-CBC / PKCS5Padding 加密该 JSON 字符串',
  '密文做 Base64 编码，得到 encrypted_payload',
  'GET：仅传 ?encrypted_payload=...；POST：请求体为 {"encrypted_payload":"..."}',
  '不要再同时传明文 api_key、card_key 等字段，否则在开启入参加密时会被拒绝',
]

export const ENCRYPTION_DOC_RESPONSE_STEPS = [
  '若开启出参加密，HTTP 响应体为包装结构，而非直接的业务 JSON',
  '先读取 response_encrypted 字段，若为 true 则取 encrypted_payload',
  '使用相同 Key、IV、算法解密 encrypted_payload，得到原始 JSON 字符串',
  '将解密后的字符串 parse 为 JSON，即与未开启出参加密时相同的结构',
  '若 response_encrypted 不存在或为 false，则响应本身就是明文 JSON',
]

export function buildJsEncryptSnippet({ key, iv, payloadField = 'encrypted_payload' }) {
  return `// JavaScript / Node.js（crypto-js）
import CryptoJS from 'crypto-js'

const key = CryptoJS.enc.Base64.parse('${key || 'YOUR_KEY_BASE64'}')
const iv  = CryptoJS.enc.Base64.parse('${iv || 'YOUR_IV_BASE64'}')

// 1. 明文业务参数
const params = {
  api_key: 'YOUR_API_KEY',
  card_key: 'YOUR_CARD_KEY',
  machine_code: 'DEVICE-001',
}

// 2. AES-256-CBC 加密
const encrypted = CryptoJS.AES.encrypt(
  CryptoJS.enc.Utf8.parse(JSON.stringify(params)),
  key,
  { iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 }
)
const ${payloadField} = encrypted.ciphertext.toString(CryptoJS.enc.Base64)

// 3. 发送请求
// GET:  ?${payloadField}=...
// POST: { "${payloadField}": ${payloadField} }`
}

export function buildJsDecryptSnippet({ key, iv }) {
  return `// JavaScript / Node.js 解密出参
import CryptoJS from 'crypto-js'

function decryptOpenApiResponse(raw, keyBase64, ivBase64) {
  if (!raw?.response_encrypted) return raw
  const key = CryptoJS.enc.Base64.parse(keyBase64)
  const iv  = CryptoJS.enc.Base64.parse(ivBase64)
  const cipher = CryptoJS.lib.CipherParams.create({
    ciphertext: CryptoJS.enc.Base64.parse(raw.encrypted_payload),
  })
  const plain = CryptoJS.AES.decrypt(cipher, key, {
    iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7,
  }).toString(CryptoJS.enc.Utf8)
  return JSON.parse(plain)
}

const plain = decryptOpenApiResponse(httpResponseJson, '${key || 'YOUR_KEY_BASE64'}', '${iv || 'YOUR_IV_BASE64'}')
console.log(plain) // { code: 200, success: true, message: '...' }`
}

export function buildJavaEncryptSnippet({ key, iv, payloadField = 'encrypted_payload' }) {
  return `// Java（javax.crypto）
// 算法: AES/CBC/PKCS5Padding，Key/IV 为 Base64 解码后的字节
String plainJson = "{\\"api_key\\":\\"...\\",\\"card_key\\":\\"...\\"}";
byte[] keyBytes = Base64.getDecoder().decode("${key || 'YOUR_KEY_BASE64'}");
byte[] ivBytes  = Base64.getDecoder().decode("${iv || 'YOUR_IV_BASE64'}");

Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
String ${payloadField} = Base64.getEncoder().encodeToString(
    cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8))
);

// GET: ?${payloadField}=URLEncoder.encode(${payloadField}, UTF_8)
// POST: {"${payloadField}":"..."}`
}

export function buildJavaDecryptSnippet({ key, iv }) {
  return `// Java 解密出参
String encryptedPayload = responseJson.getString("encrypted_payload");
byte[] keyBytes = Base64.getDecoder().decode("${key || 'YOUR_KEY_BASE64'}");
byte[] ivBytes  = Base64.getDecoder().decode("${iv || 'YOUR_IV_BASE64'}");

Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
String plainJson = new String(
    cipher.doFinal(Base64.getDecoder().decode(encryptedPayload)),
    StandardCharsets.UTF_8
);
// plainJson 即原始响应 JSON`
}

export function buildPythonEncryptSnippet({ key, iv, payloadField = 'encrypted_payload' }) {
  return `# Python（pycryptodome）
import base64, json
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

key = base64.b64decode('${key || 'YOUR_KEY_BASE64'}')
iv  = base64.b64decode('${iv || 'YOUR_IV_BASE64'}')
params = {"api_key": "...", "card_key": "..."}

plain = json.dumps(params, ensure_ascii=False).encode('utf-8')
cipher = AES.new(key, AES.MODE_CBC, iv)
${payloadField} = base64.b64encode(cipher.encrypt(pad(plain, AES.block_size))).decode()

# GET: ?${payloadField}=...
# POST: {"${payloadField}": ${payloadField}}`
}

export function buildPythonDecryptSnippet({ key, iv }) {
  return `# Python 解密出参
import base64, json
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad

def decrypt_response(raw, key_b64, iv_b64):
    if not raw.get('response_encrypted'):
        return raw
    key = base64.b64decode(key_b64)
    iv  = base64.b64decode(iv_b64)
    data = base64.b64decode(raw['encrypted_payload'])
    plain = unpad(AES.new(key, AES.MODE_CBC, iv).decrypt(data), AES.block_size)
    return json.loads(plain.decode('utf-8'))

plain = decrypt_response(resp.json(), '${key || 'YOUR_KEY_BASE64'}', '${iv || 'YOUR_IV_BASE64'}')`
}
