<template>
  <div class="api-open-center">
    <div class="page-header">
      <div>
        <h2>API 开放中心</h2>
        <p class="page-subtitle">{{ currentPageSubtitle }}</p>
      </div>
    </div>

    <div class="open-center-layout">
      <aside class="open-center-nav" aria-label="文档目录">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          class="nav-item"
          :class="{ active: activePage === item.id }"
          @click="switchPage(item.id)"
        >
          {{ item.label }}
        </button>
      </aside>

      <div class="open-center-content">
        <section v-if="activePage === 'overview'" class="doc-section doc-page">
          <p class="docs-base-hint">
            所有开放平台接口均位于 <code>{{ apiBaseUrlHint }}/v1/</code> 下，支持 GET 查询参数或 POST JSON。
            多数接口须传 <code>api_key</code>；若 API Key 开启卡密加密，<code>card_key</code> 须传加密后的值。
            若开启<strong>入参加密</strong>（见「参数加密」页），业务参数须放入 <code>encrypted_payload</code>。
            若开启<strong>出参加密</strong>，响应须先解密 <code>encrypted_payload</code> 后再解析。
            <strong>生成卡密</strong>与<strong>创建 API Key</strong>须使用开放平台 Token（在「创建 Token」页生成，仅显示一次）。
          </p>
          <div class="overview-nav-grid">
            <button
              v-for="item in docNavItems"
              :key="item.id"
              type="button"
              class="overview-nav-card"
              @click="switchPage(item.id)"
            >
              <span class="overview-nav-title">{{ item.label }}</span>
              <span class="overview-nav-desc">{{ item.subtitle }}</span>
            </button>
          </div>
        </section>

        <section v-if="activePage === 'create-token'" class="doc-section doc-page">
          <div class="token-page-header">
            <div>
              <h3>创建 Token</h3>
              <p class="token-page-desc">
                开放平台 Token 用于调用「生成卡密」「创建 API Key」等管理接口。为保障安全，Token 仅在创建时显示一次，请立即复制并妥善保存。
                每位管理员最多持有 {{ tokenLimit }} 个 Token（当前 {{ tokenList.length }}/{{ tokenLimit }}）。
              </p>
            </div>
            <button
              type="button"
              class="btn-primary"
              :disabled="tokenList.length >= tokenLimit"
              @click="openCreateTokenForm"
            >
              创建 Token
            </button>
          </div>

          <div class="table-container">
            <table class="params-table token-table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>Token 前缀</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>最后使用</th>
                  <th>过期时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="tokenLoading">
                  <td colspan="7" class="token-empty">加载中…</td>
                </tr>
                <tr v-else-if="!tokenList.length">
                  <td colspan="7" class="token-empty">暂无 Token，点击右上角创建</td>
                </tr>
                <tr v-for="row in tokenList" :key="row.id">
                  <td>{{ row.name }}</td>
                  <td><code>{{ row.token_prefix }}</code></td>
                  <td>
                    <span class="token-status" :class="tokenStatusClass(row)">{{ tokenStatusLabel(row) }}</span>
                  </td>
                  <td>{{ formatTokenTime(row.create_time) }}</td>
                  <td>{{ formatTokenTime(row.last_use_time) || '—' }}</td>
                  <td>{{ row.expires_at ? formatTokenTime(row.expires_at) : '永不过期' }}</td>
                  <td>
                    <button
                      type="button"
                      class="btn-link-danger"
                      @click="confirmDeleteToken(row)"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <p class="token-usage-hint">
            调用开放接口时在 Header 传递：<code>Authorization: Bearer &lt;您的 Token&gt;</code>，或使用 <code>X-Open-Token</code>、参数 <code>open_token</code>。
          </p>
        </section>

        <!-- 创建 Token 表单 -->
        <div v-if="showCreateTokenForm" class="token-modal-overlay" @click.self="closeCreateTokenForm">
          <div class="token-modal">
            <h4>新建开放平台 Token</h4>
            <label class="token-field">
              <span>名称 *</span>
              <input v-model="tokenForm.name" type="text" placeholder="例如：自动化脚本" maxlength="100" />
            </label>
            <label class="token-field">
              <span>备注</span>
              <input v-model="tokenForm.description" type="text" placeholder="可选" maxlength="255" />
            </label>
            <div class="token-modal-actions">
              <button type="button" class="btn-secondary" @click="closeCreateTokenForm">取消</button>
              <button type="button" class="btn-primary" :disabled="tokenCreating" @click="submitCreateToken">
                {{ tokenCreating ? '创建中…' : '创建' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Token 一次性展示 -->
        <div v-if="newlyCreatedToken" class="token-modal-overlay" @click.self="closeNewTokenDialog">
          <div class="token-modal token-reveal-modal">
            <h4>Token 创建成功</h4>
            <p class="token-reveal-warn">⚠️ 此 Token 仅显示一次，关闭后将无法再次查看，请立即复制保存。</p>
            <div class="token-reveal-box">
              <code class="token-reveal-value">{{ newlyCreatedToken.token }}</code>
              <button type="button" class="btn-primary btn-code-copy" @click="copyNewToken">复制 Token</button>
            </div>
            <p class="token-reveal-meta">名称：{{ newlyCreatedToken.name }} · 前缀：{{ newlyCreatedToken.token_prefix }}</p>
            <div class="token-modal-actions">
              <button type="button" class="btn-primary" @click="closeNewTokenDialog">我已保存，关闭</button>
            </div>
          </div>
        </div>

        <section v-if="activePage === 'health'" class="doc-section doc-page">
          <h3>健康检查（health / ping / verify_api_key）</h3>
          <p>用于探测服务是否正常运行，或验证 API Key 是否有效。<code>/v1/health</code> 无需鉴权；<code>ping</code> 可选传 <code>api_key</code>；<code>verify_api_key</code> 须传 <code>api_key</code>。</p>
          <div class="endpoint-box">
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/health</code>
          </div>
          <div class="endpoint-box">
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/ping</code>
          </div>
          <div class="endpoint-box">
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/verify_api_key</code>
          </div>
          <h4>响应示例（health）</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "服务运行正常",
  "success": true,
  "data": {
    "status": "ok",
    "service": "xxgkami-open-api",
    "timestamp": "2026-07-03T09:00:00"
  }
}</pre>
          <h4>响应示例（verify_api_key）</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "API Key 有效",
  "success": true,
  "data": {
    "valid": true,
    "api_key_id": 9,
    "key_name": "我的密钥",
    "enable_card_encryption": false
  }
}</pre>
          <h4>调用示例</h4>
          <pre class="code-block">{{ healthDocExample }}</pre>
        </section>

        <section v-if="activePage === 'use-card'" class="doc-section doc-page">
          <h3>使用卡密接口（use_card）</h3>
          <p>通过 API 密钥和卡密，直接使用/核销卡密。仅返回成功/失败，不含详细状态。</p>
          <div class="endpoint-box">
            <span class="method-badge post">POST</span>
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/use_card</code>
          </div>
          <h4>请求参数</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>参数名</th><th>必选</th><th>类型</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>api_key</td><td>是</td><td>String</td><td>您的 API 密钥</td></tr>
                <tr><td>card_key</td><td>是</td><td>String</td><td>要使用的卡密</td></tr>
                <tr><td>machine_code</td><td>否</td><td>String</td><td>机器码（一机一码，首次使用时绑定）</td></tr>
                <tr><td>device_id</td><td>否</td><td>String</td><td>设备标识</td></tr>
                <tr><td>ip_address</td><td>否</td><td>String</td><td>客户端 IP（不传则自动获取请求 IP）</td></tr>
              </tbody>
            </table>
          </div>
          <h4>响应示例</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "Card used successfully",
  "success": true
}</pre>
        </section>

        <section v-if="activePage === 'activate'" class="doc-section doc-page">
          <h3>开通授权接口（activate）</h3>
          <p>核销/激活卡密，并在响应 <code>data</code> 中返回卡密类型、剩余时长、到期时间或剩余次数。推荐客户端集成时使用本接口。</p>
          <div class="endpoint-box">
            <span class="method-badge post">POST</span>
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/activate</code>
          </div>
          <h4>请求参数</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>参数名</th><th>必选</th><th>类型</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>api_key</td><td>是</td><td>String</td><td>您的 API 密钥</td></tr>
                <tr><td>card_key</td><td>是</td><td>String</td><td>要开通授权的卡密</td></tr>
                <tr><td>machine_code</td><td>否*</td><td>String</td><td>机器码；API Key 若开启「须机器码」则为必填</td></tr>
                <tr><td>device_id</td><td>否</td><td>String</td><td>设备标识</td></tr>
                <tr><td>ip_address</td><td>否</td><td>String</td><td>客户端 IP</td></tr>
              </tbody>
            </table>
          </div>
          <h4>响应示例（时间卡）</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "授权成功",
  "success": true,
  "data": {
    "card_type": "time",
    "status": 1,
    "machine_code": "ABC123",
    "expire_time": "2026-08-01T12:00:00",
    "remaining_time": "30天"
  }
}</pre>
          <h4>响应示例（次数卡）</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "授权成功",
  "success": true,
  "data": {
    "card_type": "count",
    "status": 1,
    "remaining_count": 99,
    "total_count": 100
  }
}</pre>
          <h4>调用示例</h4>
          <pre class="code-block">{{ activateDocExample }}</pre>
        </section>

        <section v-if="activePage === 'unbind-device'" class="doc-section doc-page">
          <h3>解绑设备码接口（unbind_device）</h3>
          <p>清空卡密已绑定的 <code>machine_code</code> 与 <code>device_id</code>，便于用户换机后重新绑定。须 API Key 鉴权；专属卡须使用对应密钥。</p>
          <div class="endpoint-box">
            <span class="method-badge post">POST</span>
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/unbind_device</code>
          </div>
          <h4>请求参数</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>参数名</th><th>必选</th><th>类型</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>api_key</td><td>是</td><td>String</td><td>您的 API 密钥</td></tr>
                <tr><td>card_key</td><td>是</td><td>String</td><td>要解绑的卡密</td></tr>
                <tr><td>machine_code</td><td>否*</td><td>String</td><td>原设备码；卡密开启「原设备解绑」时为必填</td></tr>
              </tbody>
            </table>
          </div>
          <h4>响应示例</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "解绑成功",
  "success": true,
  "data": {
    "unbound": true
  }
}</pre>
          <h4>常见错误</h4>
          <ul class="docs-error-list">
            <li>卡密未绑定设备 — 无法解绑</li>
            <li>解绑冷却中 / 已达解绑次数上限</li>
            <li>原设备码不匹配（开启原设备解绑时）</li>
            <li>专属卡密使用了不匹配的 API Key</li>
          </ul>
          <h4>调用示例</h4>
          <pre class="code-block">{{ unbindDocExample }}</pre>
        </section>

        <section v-if="activePage === 'create-cards'" class="doc-section doc-page">
          <h3>生成卡密接口（create_cards）</h3>
          <p>通过 API Key 批量生成卡密，须同时携带<strong>开放平台 Token</strong>（请求体参数 <code>open_token</code>，或 Header <code>Authorization: Bearer &lt;token&gt;</code> / <code>X-Open-Token</code>）。设置 <code>exclusive=false</code> 生成普通全局卡密；<code>exclusive=true</code> 生成专属 API Key 卡密。</p>
          <div class="endpoint-box">
            <span class="method-badge post">POST</span>
            <span class="method-badge get">GET</span>
            <code class="url">/api/v1/create_cards</code>
          </div>
          <h4>请求头</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>Header</th><th>必选</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>Authorization</td><td>是*</td><td>Bearer &lt;开放平台 Token&gt;；也可用请求体 <code>open_token</code> 或 <code>X-Open-Token</code></td></tr>
              </tbody>
            </table>
          </div>
          <h4>请求参数</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>参数名</th><th>必选</th><th>类型</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>api_key</td><td>是</td><td>String</td><td>业务 API 密钥</td></tr>
                <tr><td>open_token</td><td>是*</td><td>String</td><td>开放平台 Token；也可通过 Header <code>Authorization</code> / <code>X-Open-Token</code> 传递</td></tr>
                <tr><td>exclusive</td><td>否</td><td>Boolean</td><td>是否专属卡密，默认 false（普通卡密）</td></tr>
                <tr><td>use_encrypted</td><td>否</td><td>Boolean</td><td>是否高级加密卡密，默认 false（简单卡密）</td></tr>
                <tr><td>card_type</td><td>否</td><td>String</td><td>time / count，默认 time</td></tr>
                <tr><td>count</td><td>否</td><td>Integer</td><td>生成数量，1～100，默认 1</td></tr>
                <tr><td>duration</td><td>否</td><td>Integer</td><td>时间卡时长，默认 30</td></tr>
                <tr><td>duration_unit</td><td>否</td><td>String</td><td>days / hours / permanent</td></tr>
                <tr><td>total_count</td><td>否</td><td>Integer</td><td>次数卡总次数，默认 100</td></tr>
                <tr><td>key_length</td><td>否</td><td>Integer</td><td>简单卡密长度（4～128），默认 16</td></tr>
                <tr><td>key_prefix</td><td>否</td><td>String</td><td>简单卡密前缀</td></tr>
                <tr><td>allow_self_unbind</td><td>否</td><td>Boolean</td><td>是否允许用户自助解绑设备</td></tr>
              </tbody>
            </table>
          </div>
          <h4>响应示例</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "成功生成 3 条卡密",
  "success": true,
  "data": {
    "count": 3,
    "exclusive": true,
    "use_encrypted": false,
    "api_key_id": 9,
    "card_type": "time",
    "cards": [
      { "card_key": "ABC123...", "card_type": "time", "duration": 7, "duration_unit": "days" }
    ]
  }
}</pre>
          <h4>调用示例</h4>
          <pre class="code-block">{{ createCardsDocExample }}</pre>
        </section>

        <section v-if="activePage === 'create-api-key'" class="doc-section doc-page">
          <h3>创建 API Key（create_api_key）</h3>
          <p>创建新的 API 密钥。须携带<strong>开放平台 Token</strong>（请求体参数 <code>open_token</code>，或 Header <code>Authorization: Bearer &lt;token&gt;</code> / <code>X-Open-Token</code>）。响应中的 <code>api_key</code> 仅返回一次，请妥善保存。</p>
          <div class="endpoint-box">
            <span class="method-badge post">POST</span>
            <code class="url">/api/v1/create_api_key</code>
          </div>
          <h4>请求头</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>Header</th><th>必选</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>Authorization</td><td>是*</td><td>Bearer &lt;开放平台 Token&gt;；也可用请求体 <code>open_token</code> 或 <code>X-Open-Token</code></td></tr>
                <tr><td>Content-Type</td><td>是</td><td>application/json</td></tr>
              </tbody>
            </table>
          </div>
          <h4>请求体参数</h4>
          <div class="table-container">
            <table class="params-table">
              <thead>
                <tr><th>参数名</th><th>必选</th><th>类型</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr><td>open_token</td><td>是*</td><td>String</td><td>开放平台 Token；也可通过 Header <code>Authorization</code> / <code>X-Open-Token</code> 传递</td></tr>
                <tr><td>name</td><td>是</td><td>String</td><td>密钥名称</td></tr>
                <tr><td>description</td><td>否</td><td>String</td><td>描述</td></tr>
                <tr><td>enable_card_encryption</td><td>否</td><td>Boolean</td><td>是否开启卡密加密传输</td></tr>
              </tbody>
            </table>
          </div>
          <h4>响应示例</h4>
          <pre class="code-block">{
  "code": 200,
  "message": "API Key 创建成功",
  "success": true,
  "data": {
    "id": 12,
    "key_name": "第三方对接密钥",
    "api_key": "32位密钥",
    "enable_card_encryption": false,
    "status": 1
  }
}</pre>
          <h4>调用示例</h4>
          <pre class="code-block">{{ createApiKeyDocExample }}</pre>
        </section>

        <section v-if="activePage === 'encryption-settings'" class="doc-section doc-page encryption-settings-page">
          <div class="encryption-panel encryption-panel--hero">
            <div class="encryption-hero-main">
              <div class="encryption-hero-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <rect x="5" y="11" width="14" height="10" rx="2" />
                  <path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" />
                  <circle cx="12" cy="16" r="1.2" fill="currentColor" stroke="none" />
                </svg>
              </div>
              <div>
                <h3>全局加解密（AES-256-CBC）</h3>
                <p class="encryption-page-desc">
                  入参与出参<strong>独立控制</strong>，互不影响。适用于开放平台 v1、自定义回调（<code>/api/custom/.../use</code>）、旧版 <code>/api/cards/use</code> 等外部接口。
                  开放平台 Token 仍通过 Header 传递，不参与加解密。
                </p>
              </div>
            </div>
          </div>

          <div class="encryption-panel encryption-panel--switch">
            <div class="encryption-switch-row">
              <div class="encryption-switch-info">
                <h4>入参加密</h4>
                <p>开启后，请求参数须加密为 JSON 并放入 <code>{{ encryptionConfig.payload_field || 'encrypted_payload' }}</code></p>
              </div>
              <div class="encryption-hero-control">
                <span class="encryption-status-badge" :class="encryptionConfig.request_enabled ? 'is-on' : 'is-off'">
                  {{ encryptionConfig.request_enabled ? '入参已加密' : '入参明文' }}
                </span>
                <div class="encryption-toggle" role="radiogroup" aria-label="入参加密开关">
                  <button
                    type="button"
                    class="encryption-toggle-option encryption-toggle-option--off"
                    :class="{ active: !encryptionConfig.request_enabled }"
                    :disabled="encryptionLoading || encryptionSaving"
                    @click="setRequestEncryptionEnabled(false)"
                  >
                    <span class="encryption-toggle-dot" />
                    关闭
                  </button>
                  <button
                    type="button"
                    class="encryption-toggle-option encryption-toggle-option--on"
                    :class="{ active: encryptionConfig.request_enabled }"
                    :disabled="encryptionLoading || encryptionSaving"
                    @click="setRequestEncryptionEnabled(true)"
                  >
                    <span class="encryption-toggle-dot" />
                    开启
                  </button>
                </div>
              </div>
            </div>

            <div class="encryption-switch-divider" />

            <div class="encryption-switch-row">
              <div class="encryption-switch-info">
                <h4>出参加密</h4>
                <p>开启后，接口返回 JSON 会包装为 <code>{ "response_encrypted": true, "encrypted_payload": "..." }</code></p>
              </div>
              <div class="encryption-hero-control">
                <span class="encryption-status-badge" :class="encryptionConfig.response_enabled ? 'is-on' : 'is-off'">
                  {{ encryptionConfig.response_enabled ? '出参已加密' : '出参明文' }}
                </span>
                <div class="encryption-toggle" role="radiogroup" aria-label="出参加密开关">
                  <button
                    type="button"
                    class="encryption-toggle-option encryption-toggle-option--off"
                    :class="{ active: !encryptionConfig.response_enabled }"
                    :disabled="encryptionLoading || encryptionSaving"
                    @click="setResponseEncryptionEnabled(false)"
                  >
                    <span class="encryption-toggle-dot" />
                    关闭
                  </button>
                  <button
                    type="button"
                    class="encryption-toggle-option encryption-toggle-option--on"
                    :class="{ active: encryptionConfig.response_enabled }"
                    :disabled="encryptionLoading || encryptionSaving"
                    @click="setResponseEncryptionEnabled(true)"
                  >
                    <span class="encryption-toggle-dot" />
                    开启
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="encryption-panel encryption-panel--spec">
            <div class="encryption-panel-head">
              <h4>加密规范</h4>
              <span class="encryption-panel-tip">对接时请严格使用以下算法参数</span>
            </div>
            <div class="encryption-info-grid">
              <div class="encryption-info-card">
                <span class="encryption-info-label">算法</span>
                <code>{{ encryptionConfig.algorithm || 'AES-256-CBC' }}</code>
              </div>
              <div class="encryption-info-card">
                <span class="encryption-info-label">填充模式</span>
                <code>{{ encryptionConfig.padding || 'PKCS5Padding' }}</code>
              </div>
              <div class="encryption-info-card">
                <span class="encryption-info-label">编码格式</span>
                <code>{{ encryptionConfig.encoding || 'Base64' }}</code>
              </div>
              <div class="encryption-info-card">
                <span class="encryption-info-label">参数字段名</span>
                <code>{{ encryptionConfig.payload_field || 'encrypted_payload' }}</code>
              </div>
            </div>
          </div>

          <div class="encryption-panel encryption-panel--keys">
            <div class="encryption-panel-head">
              <h4>密钥信息</h4>
              <span class="encryption-panel-tip">用于自行加密 / 解密参数，请妥善保管</span>
            </div>
            <div class="encryption-key-list">
              <article class="encryption-key-item">
                <div class="encryption-key-item-head">
                  <div>
                    <strong>密钥 Key</strong>
                    <p>32 字节 AES-256 密钥，Base64 编码</p>
                  </div>
                  <button type="button" class="btn-secondary btn-mini" @click="copyEncryptionValue(encryptionConfig.key)">复制</button>
                </div>
                <code class="encryption-key-value">{{ encryptionConfig.key || '—' }}</code>
              </article>
              <article class="encryption-key-item">
                <div class="encryption-key-item-head">
                  <div>
                    <strong>偏移量 IV</strong>
                    <p>16 字节 CBC 初始向量，Base64 编码</p>
                  </div>
                  <button type="button" class="btn-secondary btn-mini" @click="copyEncryptionValue(encryptionConfig.iv)">复制</button>
                </div>
                <code class="encryption-key-value">{{ encryptionConfig.iv || '—' }}</code>
              </article>
            </div>
            <div class="encryption-actions">
              <button type="button" class="btn-secondary" :disabled="encryptionLoading || encryptionSaving" @click="loadEncryptionConfig">
                刷新配置
              </button>
              <button type="button" class="btn-secondary" :disabled="encryptionLoading || encryptionSaving" @click="confirmRegenerateEncryptionKey">
                重新生成 Key / IV
              </button>
            </div>
          </div>

          <div class="encryption-panel encryption-panel--guide">
            <div class="encryption-panel-head">
              <h4>加解密对接文档</h4>
              <span class="encryption-panel-tip">入参 / 出参独立开关，以下说明均基于当前页 Key 与 IV</span>
            </div>

            <div class="encryption-doc-section">
              <h5>适用范围</h5>
              <ul class="encryption-doc-list">
                <li v-for="(line, idx) in encryptionDocScope" :key="'scope-' + idx">{{ line }}</li>
              </ul>
            </div>

            <div class="encryption-doc-section">
              <h5>探测当前开关</h5>
              <p class="encryption-doc-p">
                调用 <code>GET {{ apiBaseUrlHint }}/v1/health</code> 或 <code>/v1/ping</code>，
                响应 <code>data.param_encryption</code> 中包含
                <code>request_enabled</code>（入参）与 <code>response_enabled</code>（出参）及 Key/IV。
              </p>
            </div>

            <div class="encryption-doc-section encryption-doc-section--highlight">
              <h5>一、如何传入加密参数（入参）</h5>
              <p class="encryption-doc-p" v-if="encryptionConfig.request_enabled">
                <strong>当前状态：入参加密已开启</strong> — 所有业务参数必须加密后传递。
              </p>
              <p class="encryption-doc-p" v-else>
                <strong>当前状态：入参明文</strong> — 可按接口文档直接传 query/body；开启入参加密后须遵循下列步骤。
              </p>
              <ol class="encryption-steps">
                <li v-for="(step, idx) in encryptionDocRequestSteps" :key="'req-' + idx">{{ step }}</li>
              </ol>
              <div class="encryption-doc-format">
                <div>
                  <span class="encryption-doc-format-label">明文 JSON 示例</span>
                  <pre class="code-block encryption-doc-pre">{{ encryptionPlainParamsExample }}</pre>
                </div>
                <div>
                  <span class="encryption-doc-format-label">GET 请求格式</span>
                  <pre class="code-block encryption-doc-pre">{{ encryptionGetFormatExample }}</pre>
                </div>
                <div>
                  <span class="encryption-doc-format-label">POST JSON 格式</span>
                  <pre class="code-block encryption-doc-pre">{{ encryptionPostFormatExample }}</pre>
                </div>
              </div>
            </div>

            <div class="encryption-doc-section encryption-doc-section--highlight">
              <h5>二、如何加密（入参）</h5>
              <p class="encryption-doc-p">
                算法 <code>{{ encryptionConfig.algorithm }}</code> · 填充 <code>{{ encryptionConfig.padding }}</code> · 编码 <code>{{ encryptionConfig.encoding }}</code>。
                Key、IV 使用 Base64 解码为字节后再参与运算（PKCS5Padding 等同 PKCS7）。
              </p>
              <div class="encryption-doc-tabs">
                <button
                  v-for="tab in encryptionCodeTabs"
                  :key="tab.id"
                  type="button"
                  class="encryption-doc-tab"
                  :class="{ active: encryptionCodeTab === tab.id }"
                  @click="encryptionCodeTab = tab.id"
                >
                  {{ tab.label }}
                </button>
              </div>
              <div class="encryption-example-head">
                <span>加密代码示例（{{ encryptionCodeTabLabel }}）</span>
                <button type="button" class="btn-primary btn-code-copy btn-mini" @click="copyEncryptionCodeSnippet('encrypt')">复制</button>
              </div>
              <pre class="code-block encryption-example-pre">{{ currentEncryptCodeSnippet }}</pre>
            </div>

            <div class="encryption-doc-section encryption-doc-section--highlight">
              <h5>三、如何解密传出参数（出参）</h5>
              <p class="encryption-doc-p" v-if="encryptionConfig.response_enabled">
                <strong>当前状态：出参加密已开启</strong> — 响应体为密文包装，须先解密再解析。
              </p>
              <p class="encryption-doc-p" v-else>
                <strong>当前状态：出参明文</strong> — 响应可直接 JSON.parse；开启出参加密后须遵循下列步骤。
              </p>
              <ol class="encryption-steps">
                <li v-for="(step, idx) in encryptionDocResponseSteps" :key="'res-' + idx">{{ step }}</li>
              </ol>
              <div class="encryption-doc-format">
                <div>
                  <span class="encryption-doc-format-label">出参密文包装结构</span>
                  <pre class="code-block encryption-doc-pre">{{ encryptionWrappedResponseExample }}</pre>
                </div>
                <div>
                  <span class="encryption-doc-format-label">解密后的明文 JSON（示例）</span>
                  <pre class="code-block encryption-doc-pre">{{ encryptionDecryptedResponseExample }}</pre>
                </div>
              </div>
              <div class="encryption-example-head">
                <span>解密代码示例（{{ encryptionCodeTabLabel }}）</span>
                <button type="button" class="btn-primary btn-code-copy btn-mini" @click="copyEncryptionCodeSnippet('decrypt')">复制</button>
              </div>
              <pre class="code-block encryption-example-pre">{{ currentDecryptCodeSnippet }}</pre>
            </div>

            <div class="encryption-doc-section">
              <h5>四、完整流程速查</h5>
              <table class="params-table encryption-flow-table">
                <thead>
                  <tr><th>步骤</th><th>入参加密开</th><th>出参加密开</th></tr>
                </thead>
                <tbody>
                  <tr>
                    <td>1. 组装数据</td>
                    <td>业务参数 → JSON 对象</td>
                    <td>—</td>
                  </tr>
                  <tr>
                    <td>2. 发送请求</td>
                    <td>JSON AES 加密 → <code>encrypted_payload</code></td>
                    <td>同左（若入参也开）</td>
                  </tr>
                  <tr>
                    <td>3. 接收响应</td>
                    <td>—</td>
                    <td>读取 <code>response_encrypted</code> + <code>encrypted_payload</code></td>
                  </tr>
                  <tr>
                    <td>4. 解析结果</td>
                    <td>直接读 JSON（出参明文时）</td>
                    <td>解密 <code>encrypted_payload</code> → 原 JSON</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section v-if="activePage === 'code-examples'" class="doc-section doc-page code-examples-section">
          <h3>代码示例</h3>
          <p class="code-examples-hint">
            当前环境 API 根路径参考：<code>{{ apiBaseUrlHint }}</code>
            — 请将示例中的 <code>BASE_URL</code> 或 <code>{BASE_URL}</code> 换为此值（勿以 <code>/</code> 结尾）。
          </p>

          <div class="example-endpoint-tabs">
            <button
              v-for="tab in exampleTabs"
              :key="tab.id"
              type="button"
              class="example-tab-btn"
              :class="{ active: selectedExampleEndpoint === tab.id }"
              @click="selectedExampleEndpoint = tab.id"
            >
              {{ tab.label }}
            </button>
          </div>

          <p v-if="currentExampleIntro" class="code-examples-intro">{{ currentExampleIntro }}</p>

          <div class="code-examples-layout">
            <aside class="code-examples-lang" aria-label="语言列表">
              <button
                v-for="ex in currentExampleList"
                :key="ex.id"
                type="button"
                class="code-lang-btn"
                :class="{ active: selectedExampleId === ex.id }"
                @click="selectedExampleId = ex.id"
              >
                {{ ex.label }}
              </button>
            </aside>
            <div class="code-examples-panel">
              <div class="code-examples-toolbar-inner">
                <span class="code-examples-lang-title">{{ currentExample?.label }}</span>
                <button type="button" class="btn-primary btn-code-copy" @click="copyExampleCode">
                  复制代码
                </button>
              </div>
              <pre class="code-block code-examples-pre"><code class="hljs" v-html="highlightedExampleHtml"></code></pre>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { copyToClipboard } from '../utils/clipboard.js'
import { openPlatformTokenApi, openApiEncryptionApi } from '../services/api.js'
import { encryptOpenApiParams } from '../utils/openApiParamCrypto.js'
import {
  ENCRYPTION_DOC_SCOPE,
  ENCRYPTION_DOC_REQUEST_STEPS,
  ENCRYPTION_DOC_RESPONSE_STEPS,
  buildJsEncryptSnippet,
  buildJsDecryptSnippet,
  buildJavaEncryptSnippet,
  buildJavaDecryptSnippet,
  buildPythonEncryptSnippet,
  buildPythonDecryptSnippet,
} from '../data/apiOpenEncryptionDoc.js'
import { highlightUseCardExample } from '../utils/useCardCodeHighlight.js'
import { API_USE_CARD_INTRO, API_USE_CARD_EXAMPLES } from '../data/apiUseCardCodeExamples.js'
import {
  API_ACTIVATE_INTRO,
  API_ACTIVATE_EXAMPLES,
  API_UNBIND_INTRO,
  API_UNBIND_EXAMPLES,
  API_HEALTH_INTRO,
  API_HEALTH_EXAMPLES,
  API_CREATE_CARDS_INTRO,
  API_CREATE_CARDS_EXAMPLES,
  API_CREATE_API_KEY_INTRO,
  API_CREATE_API_KEY_EXAMPLES,
} from '../data/apiOpenPlatformExamples.js'
import 'highlight.js/styles/github-dark.css'

const navItems = [
  { id: 'overview', label: '概述', subtitle: '开放平台 v1 接口总览与通用说明' },
  { id: 'create-token', label: '创建 Token', subtitle: '管理开放平台访问 Token' },
  { id: 'health', label: '健康检查', subtitle: 'health / ping / verify_api_key' },
  { id: 'use-card', label: '使用卡密', subtitle: 'use_card 接口文档' },
  { id: 'activate', label: '开通授权', subtitle: 'activate 接口文档' },
  { id: 'unbind-device', label: '解绑设备码', subtitle: 'unbind_device 接口文档' },
  { id: 'create-cards', label: '生成卡密', subtitle: 'create_cards 接口文档' },
  { id: 'create-api-key', label: '创建 API Key', subtitle: 'create_api_key 接口文档' },
  { id: 'encryption-settings', label: '参数加密', subtitle: 'AES-256-CBC 开关、密钥与加解密文档' },
  { id: 'code-examples', label: '代码示例', subtitle: '多语言调用示例' },
]

const PAGE_TO_EXAMPLE = {
  health: 'health',
  'use-card': 'use_card',
  activate: 'activate',
  'unbind-device': 'unbind_device',
  'create-cards': 'create_cards',
  'create-api-key': 'create_api_key',
}

const activePage = ref('overview')
const selectedExampleEndpoint = ref('use_card')
const selectedExampleId = ref('curl')

const tokenList = ref([])
const tokenLimit = ref(5)
const tokenLoading = ref(false)
const tokenCreating = ref(false)
const showCreateTokenForm = ref(false)
const tokenForm = reactive({ name: '', description: '' })
const newlyCreatedToken = ref(null)

const encryptionConfig = reactive({
  enabled: false,
  request_enabled: false,
  response_enabled: false,
  algorithm: 'AES-256-CBC',
  key: '',
  iv: '',
  padding: 'PKCS5Padding',
  encoding: 'Base64',
  payload_field: 'encrypted_payload',
  response_encrypted_flag: 'response_encrypted',
})
const encryptionLoading = ref(false)
const encryptionSaving = ref(false)
const encryptionCodeTab = ref('javascript')
const encryptionDocScope = ENCRYPTION_DOC_SCOPE
const encryptionDocRequestSteps = ENCRYPTION_DOC_REQUEST_STEPS
const encryptionDocResponseSteps = ENCRYPTION_DOC_RESPONSE_STEPS

const encryptionCodeTabs = [
  { id: 'javascript', label: 'JavaScript' },
  { id: 'java', label: 'Java' },
  { id: 'python', label: 'Python' },
]

const samplePlainParams = {
  api_key: 'YOUR_API_KEY',
  card_key: 'YOUR_CARD_KEY',
  machine_code: 'DEVICE-001',
}

const samplePlainResponse = {
  code: 200,
  success: true,
  message: 'Card used successfully',
}

const encryptionPlainParamsExample = computed(() => JSON.stringify(samplePlainParams, null, 2))

const encryptionGetFormatExample = computed(() => {
  const field = encryptionConfig.payload_field || 'encrypted_payload'
  let payload = '<Base64密文>'
  if (encryptionConfig.key && encryptionConfig.iv) {
    try {
      payload = encryptOpenApiParams(samplePlainParams, encryptionConfig)
    } catch {
      // keep placeholder
    }
  }
  return `${apiBaseUrlHint.value}/v1/use_card?${field}=${encodeURIComponent(payload)}`
})

const encryptionPostFormatExample = computed(() => {
  const field = encryptionConfig.payload_field || 'encrypted_payload'
  let payload = '<Base64密文>'
  if (encryptionConfig.key && encryptionConfig.iv) {
    try {
      payload = encryptOpenApiParams(samplePlainParams, encryptionConfig)
    } catch {
      // keep placeholder
    }
  }
  return JSON.stringify({ [field]: payload }, null, 2)
})

const encryptionWrappedResponseExample = computed(() => {
  const field = encryptionConfig.payload_field || 'encrypted_payload'
  const flag = encryptionConfig.response_encrypted_flag || 'response_encrypted'
  let payload = '<Base64密文>'
  if (encryptionConfig.key && encryptionConfig.iv) {
    try {
      payload = encryptOpenApiParams(samplePlainResponse, encryptionConfig)
    } catch {
      // keep placeholder
    }
  }
  return JSON.stringify({ [flag]: true, [field]: payload }, null, 2)
})

const encryptionDecryptedResponseExample = computed(() => JSON.stringify(samplePlainResponse, null, 2))

const encryptionCodeTabLabel = computed(() =>
  encryptionCodeTabs.find((tab) => tab.id === encryptionCodeTab.value)?.label ?? 'JavaScript',
)

const cryptoSnippetArgs = computed(() => ({
  key: encryptionConfig.key || 'YOUR_KEY_BASE64',
  iv: encryptionConfig.iv || 'YOUR_IV_BASE64',
  payloadField: encryptionConfig.payload_field || 'encrypted_payload',
}))

const currentEncryptCodeSnippet = computed(() => {
  const args = cryptoSnippetArgs.value
  if (encryptionCodeTab.value === 'java') return buildJavaEncryptSnippet(args)
  if (encryptionCodeTab.value === 'python') return buildPythonEncryptSnippet(args)
  return buildJsEncryptSnippet(args)
})

const currentDecryptCodeSnippet = computed(() => {
  const args = cryptoSnippetArgs.value
  if (encryptionCodeTab.value === 'java') return buildJavaDecryptSnippet(args)
  if (encryptionCodeTab.value === 'python') return buildPythonDecryptSnippet(args)
  return buildJsDecryptSnippet(args)
})

const encryptionExampleText = computed(() => {
  const sampleParams = {
    api_key: 'YOUR_API_KEY',
    card_key: 'YOUR_CARD_KEY',
    machine_code: 'DEVICE-001',
  }
  let payload = '（请先加载或开启加密配置以生成示例密文）'
  if (encryptionConfig.key && encryptionConfig.iv) {
    try {
      payload = encryptOpenApiParams(sampleParams, encryptionConfig)
    } catch {
      payload = '（示例生成失败）'
    }
  }
  return `// 1. 明文参数 JSON
${JSON.stringify(sampleParams, null, 2)}

// 2. AES-256-CBC 加密后（POST JSON）
{
  "${encryptionConfig.payload_field || 'encrypted_payload'}": "${payload}"
}

// 3. GET 示例
${apiBaseUrlHint.value}/v1/use_card?${encryptionConfig.payload_field || 'encrypted_payload'}=${encodeURIComponent(payload)}`
})

const docNavItems = computed(() => navItems.filter((item) => item.id !== 'overview'))

const currentPageSubtitle = computed(() => {
  return navItems.find((item) => item.id === activePage.value)?.subtitle ?? '开放平台 v1 接口文档'
})

const exampleTabs = [
  { id: 'health', label: '健康检查' },
  { id: 'use_card', label: '使用卡密' },
  { id: 'activate', label: '开通授权' },
  { id: 'unbind_device', label: '解绑设备码' },
  { id: 'create_cards', label: '生成卡密' },
  { id: 'create_api_key', label: '创建 API Key' },
]

const apiBaseUrlHint = computed(() => {
  const raw = import.meta.env?.VITE_API_BASE_URL
  let base = typeof raw === 'string' && raw.trim() ? raw.trim() : '/api'
  base = base.replace(/\/+$/, '')
  if (base.startsWith('http://') || base.startsWith('https://')) {
    return base
  }
  if (typeof window !== 'undefined') {
    const prefix = base.startsWith('/') ? base : `/${base}`
    return `${window.location.origin}${prefix}`
  }
  return base
})

const healthDocExample = computed(() => {
  const sample = API_HEALTH_EXAMPLES[0]?.code ?? ''
  return sample.replace(/BASE_URL/g, apiBaseUrlHint.value)
})

const createCardsDocExample = computed(() => {
  const sample = API_CREATE_CARDS_EXAMPLES[0]?.code ?? ''
  return sample.replace(/BASE_URL/g, apiBaseUrlHint.value)
})

const createApiKeyDocExample = computed(() => {
  const sample = API_CREATE_API_KEY_EXAMPLES[0]?.code ?? ''
  return sample.replace(/BASE_URL/g, apiBaseUrlHint.value)
})

const activateDocExample = computed(() => {
  const sample = API_ACTIVATE_EXAMPLES[0]?.code ?? ''
  return sample.replace(/BASE_URL/g, apiBaseUrlHint.value)
})

const unbindDocExample = computed(() => {
  const sample = API_UNBIND_EXAMPLES[0]?.code ?? ''
  return sample.replace(/BASE_URL/g, apiBaseUrlHint.value)
})

const currentExampleList = computed(() => {
  if (selectedExampleEndpoint.value === 'health') return API_HEALTH_EXAMPLES
  if (selectedExampleEndpoint.value === 'activate') return API_ACTIVATE_EXAMPLES
  if (selectedExampleEndpoint.value === 'unbind_device') return API_UNBIND_EXAMPLES
  if (selectedExampleEndpoint.value === 'create_cards') return API_CREATE_CARDS_EXAMPLES
  if (selectedExampleEndpoint.value === 'create_api_key') return API_CREATE_API_KEY_EXAMPLES
  return API_USE_CARD_EXAMPLES
})

const currentExampleIntro = computed(() => {
  const base = apiBaseUrlHint.value
  if (selectedExampleEndpoint.value === 'health') {
    return API_HEALTH_INTRO
  }
  if (selectedExampleEndpoint.value === 'activate') {
    return API_ACTIVATE_INTRO
  }
  if (selectedExampleEndpoint.value === 'unbind_device') {
    return API_UNBIND_INTRO
  }
  if (selectedExampleEndpoint.value === 'create_cards') {
    return API_CREATE_CARDS_INTRO
  }
  if (selectedExampleEndpoint.value === 'create_api_key') {
    return API_CREATE_API_KEY_INTRO
  }
  return API_USE_CARD_INTRO.replace(/\{BASE_URL\}/g, base)
})

const currentExample = computed(
  () => currentExampleList.value.find((e) => e.id === selectedExampleId.value) ?? currentExampleList.value[0],
)

const highlightedExampleHtml = computed(() => {
  const ex = currentExample.value
  if (!ex?.code) return ''
  let code = ex.code.replace(/\{BASE_URL\}/g, apiBaseUrlHint.value).replace(/BASE_URL/g, apiBaseUrlHint.value)
  return highlightUseCardExample(code, ex.id)
})

watch(selectedExampleEndpoint, (endpoint) => {
  const list = endpoint === 'health'
    ? API_HEALTH_EXAMPLES
    : endpoint === 'activate'
      ? API_ACTIVATE_EXAMPLES
      : endpoint === 'unbind_device'
        ? API_UNBIND_EXAMPLES
        : endpoint === 'create_cards'
          ? API_CREATE_CARDS_EXAMPLES
          : endpoint === 'create_api_key'
            ? API_CREATE_API_KEY_EXAMPLES
            : API_USE_CARD_EXAMPLES
  selectedExampleId.value = list[0]?.id ?? 'curl'
})

function switchPage(id) {
  activePage.value = id
  const exampleId = PAGE_TO_EXAMPLE[id]
  if (exampleId) {
    selectedExampleEndpoint.value = exampleId
  }
  if (id === 'create-token') {
    loadTokenList()
  }
  if (id === 'encryption-settings') {
    loadEncryptionConfig()
  }
  nextTick(() => {
    window.scrollTo(0, 0)
  })
}

async function loadTokenList() {
  tokenLoading.value = true
  try {
    const res = await openPlatformTokenApi.list()
    tokenList.value = Array.isArray(res?.data) ? res.data : []
    if (typeof res?.limit === 'number') {
      tokenLimit.value = res.limit
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载 Token 列表失败')
  } finally {
    tokenLoading.value = false
  }
}

function applyEncryptionConfig(data = {}) {
  encryptionConfig.request_enabled = !!(data.request_enabled ?? data.enabled)
  encryptionConfig.response_enabled = !!data.response_enabled
  encryptionConfig.enabled = encryptionConfig.request_enabled
  encryptionConfig.algorithm = data.algorithm || 'AES-256-CBC'
  encryptionConfig.key = data.key || ''
  encryptionConfig.iv = data.iv || ''
  encryptionConfig.padding = data.padding || 'PKCS5Padding'
  encryptionConfig.encoding = data.encoding || 'Base64'
  encryptionConfig.payload_field = data.payload_field || 'encrypted_payload'
  encryptionConfig.response_encrypted_flag = data.response_encrypted_flag || 'response_encrypted'
}

async function loadEncryptionConfig() {
  encryptionLoading.value = true
  try {
    const res = await openApiEncryptionApi.getConfig()
    applyEncryptionConfig(res?.data || {})
  } catch (e) {
    ElMessage.error(e?.message || '加载加密配置失败')
  } finally {
    encryptionLoading.value = false
  }
}

async function saveEncryptionConfig({ requestEnabled, responseEnabled, regenerateKey = false } = {}) {
  encryptionSaving.value = true
  try {
    const body = { regenerate_key: !!regenerateKey }
    if (requestEnabled !== undefined) body.request_enabled = !!requestEnabled
    if (responseEnabled !== undefined) body.response_enabled = !!responseEnabled
    const res = await openApiEncryptionApi.saveConfig(body)
    applyEncryptionConfig(res?.data || {})
    ElMessage.success(res?.message || '加密配置已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存加密配置失败')
  } finally {
    encryptionSaving.value = false
  }
}

async function setRequestEncryptionEnabled(nextEnabled) {
  if (encryptionLoading.value || encryptionSaving.value) return
  if (nextEnabled === encryptionConfig.request_enabled) return
  if (nextEnabled) {
    try {
      await ElMessageBox.confirm(
        '开启后，外部 API 调用方须使用 encrypted_payload 传递 AES 加密入参。是否继续？',
        '开启入参加密',
        { type: 'warning', confirmButtonText: '开启', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  await saveEncryptionConfig({ requestEnabled: nextEnabled })
}

async function setResponseEncryptionEnabled(nextEnabled) {
  if (encryptionLoading.value || encryptionSaving.value) return
  if (nextEnabled === encryptionConfig.response_enabled) return
  if (nextEnabled) {
    try {
      await ElMessageBox.confirm(
        '开启后，接口返回 JSON 将以 encrypted_payload 密文形式返回，对接方须自行解密。是否继续？',
        '开启出参加密',
        { type: 'warning', confirmButtonText: '开启', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  await saveEncryptionConfig({ responseEnabled: nextEnabled })
}

async function confirmRegenerateEncryptionKey() {
  try {
    await ElMessageBox.confirm(
      '重新生成 Key / IV 后，旧密文将无法解密，所有对接方须同步更新。是否继续？',
      '重新生成密钥',
      { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await saveEncryptionConfig({
    requestEnabled: encryptionConfig.request_enabled,
    responseEnabled: encryptionConfig.response_enabled,
    regenerateKey: true,
  })
}

async function copyEncryptionValue(value) {
  if (!value) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  await copyToClipboard(value)
  ElMessage.success('已复制')
}

async function copyEncryptionCodeSnippet(type) {
  const text = type === 'decrypt' ? currentDecryptCodeSnippet.value : currentEncryptCodeSnippet.value
  await copyToClipboard(text)
  ElMessage.success('代码已复制')
}

async function copyEncryptionExample() {
  await copyToClipboard(encryptionExampleText.value)
  ElMessage.success('示例已复制')
}

function openCreateTokenForm() {
  if (tokenList.value.length >= tokenLimit.value) {
    ElMessage.warning(`最多可创建 ${tokenLimit.value} 个 Token，请先删除不再使用的 Token`)
    return
  }
  tokenForm.name = ''
  tokenForm.description = ''
  showCreateTokenForm.value = true
}

function closeCreateTokenForm() {
  showCreateTokenForm.value = false
}

async function submitCreateToken() {
  if (!tokenForm.name?.trim()) {
    ElMessage.warning('请填写 Token 名称')
    return
  }
  tokenCreating.value = true
  try {
    const res = await openPlatformTokenApi.create({
      name: tokenForm.name.trim(),
      description: tokenForm.description?.trim() || undefined,
    })
    if (!res?.success || !res?.data?.token) {
      throw new Error(res?.message || '创建失败')
    }
    showCreateTokenForm.value = false
    newlyCreatedToken.value = res.data
    await loadTokenList()
    ElMessage.success('Token 创建成功，请立即复制保存')
  } catch (e) {
    ElMessage.error(e?.message || '创建 Token 失败')
  } finally {
    tokenCreating.value = false
  }
}

function closeNewTokenDialog() {
  newlyCreatedToken.value = null
}

async function copyNewToken() {
  if (!newlyCreatedToken.value?.token) return
  const ok = await copyToClipboard(newlyCreatedToken.value.token)
  ElMessage[ok ? 'success' : 'error'](ok ? 'Token 已复制' : '复制失败')
}

async function confirmDeleteToken(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除 Token「${row.name}」？删除后将立即失效且无法恢复。`,
      '确认删除',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    await openPlatformTokenApi.delete(row.id)
    ElMessage.success('已删除')
    await loadTokenList()
  } catch (e) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

function formatTokenTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

function tokenStatusLabel(row) {
  if (row.status !== 1) return '已失效'
  if (row.expired) return '已过期'
  return '正常'
}

function tokenStatusClass(row) {
  if (row.status !== 1) return 'revoked'
  if (row.expired) return 'expired'
  return 'active'
}

async function copyExampleCode() {
  const ex = currentExample.value
  if (!ex?.code) return
  const code = ex.code.replace(/\{BASE_URL\}/g, apiBaseUrlHint.value).replace(/BASE_URL/g, apiBaseUrlHint.value)
  const ok = await copyToClipboard(code)
  ElMessage[ok ? 'success' : 'error'](ok ? '已复制到剪贴板' : '复制失败')
}
</script>

<style scoped>
.api-open-center {
  padding: 0;
  width: 100%;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h2 {
  margin: 0 0 0.35rem;
  font-size: 1.5rem;
  color: #111827;
}

.page-subtitle {
  margin: 0;
  color: #6b7280;
  font-size: 0.9rem;
}

.open-center-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}

.open-center-nav {
  position: sticky;
  top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.open-center-nav .nav-item {
  display: block;
  width: 100%;
  padding: 0.5rem 0.65rem;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #4b5563;
  text-align: left;
  font-size: 0.875rem;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.open-center-nav .nav-item:hover {
  background: #f3f4f6;
  color: #111827;
}

.open-center-nav .nav-item.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 600;
}

.open-center-content {
  min-width: 0;
}

.doc-page {
  animation: doc-page-in 0.18s ease-out;
}

@keyframes doc-page-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.doc-section {
  margin-bottom: 0;
}

.doc-section h3 {
  margin: 0 0 0.5rem;
  font-size: 1.2rem;
  color: #111827;
}

.doc-section h4 {
  margin: 1.25rem 0 0.65rem;
  font-size: 0.95rem;
  color: #374151;
}

.doc-section p {
  margin: 0 0 0.75rem;
  color: #4b5563;
  line-height: 1.6;
}

.docs-base-hint {
  margin: 0 0 1.25rem;
  padding: 0.75rem 1rem;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  color: #0c4a6e;
  font-size: 0.875rem;
  line-height: 1.6;
}

.overview-nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 0.75rem;
}

.overview-nav-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
  padding: 0.85rem 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.overview-nav-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
}

.overview-nav-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: #111827;
}

.overview-nav-desc {
  font-size: 0.8125rem;
  color: #6b7280;
  line-height: 1.4;
}

.docs-error-list {
  margin: 0.5rem 0 1rem;
  padding-left: 1.25rem;
  color: #6b7280;
  font-size: 0.875rem;
  line-height: 1.7;
}

.endpoint-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1rem;
  display: flex;
  align-items: center;
  gap: 0.8rem;
  margin: 1rem 0;
}

.method-badge {
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: bold;
  text-transform: uppercase;
}

.method-badge.post {
  background: #dbeafe;
  color: #1e40af;
}

.method-badge.get {
  background: #dcfce7;
  color: #166534;
}

.url {
  font-family: monospace;
  color: #475569;
  font-size: 0.9rem;
}

.table-container {
  overflow-x: auto;
}

.params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.params-table th,
.params-table td {
  padding: 0.75rem;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
}

.params-table th {
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
}

.code-block {
  background: #1e293b;
  color: #e2e8f0;
  padding: 1rem;
  border-radius: 6px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 0.85rem;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.code-examples-section {
  padding-top: 0;
}

.code-examples-hint {
  margin: 0 0 1rem;
  font-size: 0.82rem;
  color: #64748b;
  line-height: 1.5;
}

.code-examples-hint code {
  background: #f1f5f9;
  padding: 0.1em 0.35em;
  border-radius: 4px;
  font-size: 0.9em;
}

.example-endpoint-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.example-tab-btn {
  padding: 0.4rem 0.85rem;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  color: #4b5563;
  font-size: 0.8125rem;
  cursor: pointer;
}

.example-tab-btn.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.code-examples-intro {
  white-space: pre-line;
  margin: 0 0 0.85rem;
  color: #374151;
  font-size: 0.9rem;
  line-height: 1.5;
}

.code-examples-layout {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 1rem;
  min-height: 280px;
}

.code-examples-lang {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 420px;
  overflow-y: auto;
}

.code-lang-btn {
  text-align: left;
  padding: 0.45rem 0.65rem;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #f9fafb;
  color: #374151;
  font-size: 0.8125rem;
  cursor: pointer;
}

.code-lang-btn.active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
  font-weight: 600;
}

.code-examples-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.code-examples-toolbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.code-examples-lang-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
}

.btn-code-copy {
  padding: 0.35rem 0.75rem;
  font-size: 0.8125rem;
}

.code-examples-pre {
  flex: 1;
  margin: 0;
  border-radius: 0;
  max-height: 420px;
  overflow: auto;
}

.code-examples-pre code.hljs {
  background: transparent;
  padding: 0;
}

@media (max-width: 900px) {
  .open-center-layout {
    grid-template-columns: 1fr;
  }

  .open-center-nav {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .open-center-nav .nav-item {
    width: auto;
  }

  .code-examples-layout {
    grid-template-columns: 1fr;
  }

  .code-examples-lang {
    flex-direction: row;
    flex-wrap: wrap;
    max-height: none;
  }

  .token-page-header {
    flex-direction: column;
    align-items: stretch;
  }
}

.btn-primary,
.btn-secondary {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  font-size: 0.875rem;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-primary {
  background: #2563eb;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #fff;
  border-color: #d1d5db;
  color: #374151;
}

.btn-secondary:hover {
  background: #f9fafb;
}

.token-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.token-page-desc {
  margin: 0.35rem 0 0;
  color: #6b7280;
  font-size: 0.875rem;
  line-height: 1.6;
  max-width: 640px;
}

.token-table code {
  font-size: 0.8125rem;
  background: #f1f5f9;
  padding: 0.15em 0.4em;
  border-radius: 4px;
}

.token-empty {
  text-align: center;
  color: #9ca3af;
  padding: 2rem !important;
}

.token-status {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.token-status.active {
  background: #dcfce7;
  color: #166534;
}

.token-status.revoked {
  background: #fee2e2;
  color: #991b1b;
}

.token-status.expired {
  background: #fef3c7;
  color: #92400e;
}

.token-muted {
  color: #9ca3af;
}

.btn-link-danger {
  border: none;
  background: none;
  color: #dc2626;
  cursor: pointer;
  font-size: 0.875rem;
  padding: 0;
}

.btn-link-danger:hover {
  text-decoration: underline;
}

.token-usage-hint {
  margin-top: 1.25rem;
  font-size: 0.8125rem;
  color: #64748b;
  line-height: 1.6;
}

.token-usage-hint code {
  background: #f1f5f9;
  padding: 0.1em 0.35em;
  border-radius: 4px;
}

.token-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 1rem;
}

.token-modal {
  width: min(480px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 1.25rem 1.35rem;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.token-modal h4 {
  margin: 0 0 1rem;
  font-size: 1.05rem;
  color: #111827;
}

.token-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.85rem;
  font-size: 0.875rem;
  color: #374151;
}

.token-field input {
  padding: 0.5rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
}

.token-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}

.token-reveal-warn {
  margin: 0 0 0.85rem;
  padding: 0.65rem 0.75rem;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  color: #92400e;
  font-size: 0.8125rem;
  line-height: 1.5;
}

.token-reveal-box {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding: 0.85rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.token-reveal-value {
  display: block;
  word-break: break-all;
  font-size: 0.8125rem;
  color: #0f172a;
  line-height: 1.5;
}

.token-reveal-meta {
  margin: 0.75rem 0 0;
  font-size: 0.8125rem;
  color: #6b7280;
}

.encryption-settings-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.encryption-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 1.15rem 1.25rem;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.encryption-panel--switch {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.encryption-switch-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  padding: 0.35rem 0;
}

.encryption-switch-info h4 {
  margin: 0;
  font-size: 0.95rem;
  color: #111827;
}

.encryption-switch-info p {
  margin: 0.35rem 0 0;
  font-size: 0.8125rem;
  color: #6b7280;
  line-height: 1.55;
  max-width: 520px;
}

.encryption-switch-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 0.85rem 0;
}

.encryption-panel--hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1.25rem;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
  border-color: #c7d2fe;
}

.encryption-hero-main {
  display: flex;
  gap: 0.9rem;
  align-items: flex-start;
  flex: 1;
  min-width: 0;
}

.encryption-hero-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #4f46e5, #6366f1);
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.25);
}

.encryption-hero-icon svg {
  width: 22px;
  height: 22px;
}

.encryption-panel--hero h3 {
  margin: 0;
  font-size: 1.125rem;
  color: #111827;
}

.encryption-hero-control {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.65rem;
  flex-shrink: 0;
}

.encryption-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.encryption-status-badge.is-on {
  background: #dcfce7;
  color: #166534;
}

.encryption-status-badge.is-off {
  background: #f3f4f6;
  color: #6b7280;
}

.encryption-toggle {
  display: inline-flex;
  padding: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #d1d5db;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.06);
}

.encryption-toggle-option {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 88px;
  justify-content: center;
  padding: 0.45rem 0.95rem;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6b7280;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.encryption-toggle-option:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.encryption-toggle-option .encryption-toggle-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
  transition: background 0.2s ease;
}

.encryption-toggle-option.active {
  color: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.16);
}

.encryption-toggle-option--off.active {
  background: linear-gradient(135deg, #64748b, #475569);
}

.encryption-toggle-option--on.active {
  background: linear-gradient(135deg, #059669, #10b981);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.28);
}

.encryption-toggle-option.active .encryption-toggle-dot {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35);
}

.encryption-toggle-option:not(.active):hover:not(:disabled) {
  color: #374151;
  background: #f9fafb;
}

.encryption-panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}

.encryption-panel-head h4 {
  margin: 0;
  font-size: 0.95rem;
  color: #111827;
}

.encryption-panel-tip {
  font-size: 0.75rem;
  color: #9ca3af;
}

.encryption-page-desc {
  margin: 0.45rem 0 0;
  color: #4b5563;
  line-height: 1.65;
  font-size: 0.875rem;
}

.encryption-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
}

.encryption-info-card {
  padding: 0.85rem 0.95rem;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
}

.encryption-info-label {
  display: block;
  font-size: 0.75rem;
  color: #6b7280;
  margin-bottom: 0.35rem;
}

.encryption-key-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.encryption-key-item {
  padding: 0.9rem 1rem;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
}

.encryption-key-item-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 0.65rem;
}

.encryption-key-item-head strong {
  display: block;
  font-size: 0.875rem;
  color: #111827;
}

.encryption-key-item-head p {
  margin: 0.2rem 0 0;
  font-size: 0.75rem;
  color: #6b7280;
}

.encryption-key-value {
  display: block;
  word-break: break-all;
  font-size: 0.8125rem;
  line-height: 1.55;
  padding: 0.65rem 0.75rem;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
}

.encryption-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
}

.encryption-steps {
  margin: 0;
  padding-left: 1.25rem;
  color: #374151;
  line-height: 1.75;
  font-size: 0.875rem;
}

.encryption-example-block {
  margin-top: 1rem;
}

.encryption-example-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
}

.encryption-example-pre {
  margin: 0;
}

.encryption-doc-section {
  margin-top: 1.1rem;
  padding-top: 1rem;
  border-top: 1px solid #eef2f7;
}

.encryption-doc-section:first-of-type {
  margin-top: 0.35rem;
  padding-top: 0;
  border-top: none;
}

.encryption-doc-section--highlight {
  background: #f8fafc;
  margin-left: -0.25rem;
  margin-right: -0.25rem;
  padding: 1rem 0.85rem 0.85rem;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.encryption-doc-section h5 {
  margin: 0 0 0.55rem;
  font-size: 0.92rem;
  color: #111827;
}

.encryption-doc-p {
  margin: 0 0 0.65rem;
  font-size: 0.875rem;
  color: #4b5563;
  line-height: 1.65;
}

.encryption-doc-list {
  margin: 0;
  padding-left: 1.2rem;
  color: #374151;
  font-size: 0.875rem;
  line-height: 1.7;
}

.encryption-doc-format {
  display: grid;
  gap: 0.75rem;
  margin-top: 0.75rem;
}

.encryption-doc-format-label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
}

.encryption-doc-pre {
  margin: 0;
  font-size: 0.75rem;
  max-height: 160px;
  overflow: auto;
}

.encryption-doc-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 0.75rem 0 0.5rem;
}

.encryption-doc-tab {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  border-radius: 999px;
  padding: 0.28rem 0.75rem;
  font-size: 0.8125rem;
  cursor: pointer;
}

.encryption-doc-tab.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.encryption-flow-table {
  margin-top: 0.5rem;
}

.encryption-flow-table code {
  font-size: 0.75rem;
}

.encryption-example-pre {
  margin: 0;
}

.btn-mini {
  padding: 0.35rem 0.75rem;
  font-size: 0.8125rem;
}

@media (max-width: 900px) {
  .encryption-panel--hero {
    flex-direction: column;
  }

  .encryption-hero-control {
    align-items: flex-start;
    width: 100%;
  }

  .encryption-toggle {
    width: 100%;
  }

  .encryption-toggle-option {
    flex: 1;
  }
}
</style>
