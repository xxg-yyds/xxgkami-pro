<template>
  <div class="keys-manage-page">
    <div class="section-header">
      <h2>卡密管理</h2>
      <div class="header-actions">
        <button class="btn-secondary" @click="showExportModal = true">
          <i class="fas fa-file-export"></i>
          导出数据
        </button>
        <button class="btn-secondary" @click="showImportModal = true">
          <i class="fas fa-file-import"></i>
          导入卡密
        </button>
        <button class="btn-primary" @click="showCreateKeyModal = true">
          <i class="fas fa-plus"></i>
          生成卡密
        </button>
      </div>
    </div>

    <div class="keys-toolbar">
      <div class="toolbar-search">
        <label class="toolbar-label" for="keys-search-type">搜索</label>
        <select id="keys-search-type" v-model="searchType" class="toolbar-select">
          <option value="card_key">卡密</option>
          <option value="machine_code">机器码</option>
          <option value="ip_address">IP</option>
        </select>
        <select id="keys-search-storage" v-model="searchStorageScope" class="toolbar-select">
          <option value="all">全部类型</option>
          <option value="encrypted">仅加密</option>
          <option value="simple">仅简单</option>
        </select>
        <input
          id="keys-search-input"
          v-model.trim="searchQuery"
          type="search"
          class="toolbar-input"
          :placeholder="searchPlaceholder"
          autocomplete="off"
          spellcheck="false"
        />
        <button v-if="searchQuery" type="button" class="toolbar-clear" @click="clearSearch">
          清除
        </button>
      </div>
      <p class="toolbar-meta">
        当前列表：<strong>{{ filteredKeys.length }}</strong> 条
        <template v-if="searchQuery || searchStorageScope !== 'all'">
          （已筛选<template v-if="searchStorageScope !== 'all'">·{{ searchStorageScopeLabel }}</template><template v-if="searchQuery">·{{ searchTypeLabel }}</template>）
        </template>
        <span class="toolbar-divider">|</span>
        全库共计 {{ props.keys?.length || 0 }} 条
      </p>
    </div>

    <div class="toolbar-bulk" v-if="filteredKeys.length > 0">
      <button
        type="button"
        class="btn-danger bulk-delete-btn"
        :disabled="!selectedIds.length"
        @click="batchDeleteSelected"
      >
        <i class="fas fa-trash-alt"></i>
        批量删除（{{ selectedIds.length }}）
      </button>
      <button
        type="button"
        class="btn-secondary btn-toolbar"
        @click="batchUnbindKeys"
      >
        <i class="fas fa-unlink"></i>
        {{ selectedIds.length ? `解绑选中（${selectedIds.length}）` : '一键解绑全库' }}
      </button>
      <button
        type="button"
        class="btn-secondary btn-toolbar"
        @click="openBatchAdjustModal"
      >
        <i class="fas fa-clock"></i>
        {{ selectedIds.length ? `加扣时（${selectedIds.length}）` : '全库加扣时' }}
      </button>
      <button type="button" class="btn-secondary btn-toolbar" @click="clearSelection">
        清空所选
      </button>
      <button
        type="button"
        class="btn-secondary btn-toolbar"
        title="在当前列表中对所有「时间卡且已过期」的卡密勾选上"
        @click="selectExpiredInFiltered"
      >
        选中已过期的
      </button>
      <button
        type="button"
        class="btn-secondary btn-toolbar"
        title="在当前列表中选择未使用之外的卡（已使用 / 暂停 / 已合并）"
        @click="selectUsedInFiltered"
      >
        选中已使用的（含暂停/合并）
      </button>
      <span class="bulk-hint">
        可跨分页多选；搜索筛选后不符合条件的勾选会自动取消。
      </span>
    </div>

    <div class="keys-table">
      <table>
        <thead>
          <tr>
            <th class="col-checkbox">
              <el-checkbox
                :model-value="pageAllSelected"
                :indeterminate="pagePartialSelected"
                @change="toggleSelectCurrentPage"
              />
            </th>
            <th>ID</th>
            <th>卡密</th>
            <th>加密</th>
            <th>类型</th>
            <th>状态</th>
            <th>机器码</th>
            <th>IP</th>
            <th>创建时间</th>
            <th>剩余</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredKeys.length === 0">
            <td colspan="11" class="empty-table-hint">
              {{ searchQuery ? emptyFilterHint : '暂无卡密数据' }}
            </td>
          </tr>
          <template v-else>
            <tr v-for="key in paginatedKeys" :key="rowSelectKey(key)">
            <td class="col-checkbox" @click.stop>
              <el-checkbox
                :model-value="selectedIdSet.has(rowSelectKey(key))"
                @change="(v) => setRowSelected(rowSelectKey(key), !!v)"
              />
            </td>
            <td class="col-id">{{ key.id }}</td>
            <td class="key-code-cell" @click="copyKey(getCardKeyText(key))" :title="getCardKeyText(key) + '（点击复制）'">
              <span class="key-code">{{ getCardKeyText(key) }}</span>
            </td>
            <td>
              <span :class="['encrypt-tag', isSimpleCard(key) ? 'encrypt-simple' : 'encrypt-advanced']">
                {{ getEncryptLabel(key) }}
              </span>
            </td>
            <td>
              <span class="card-type" :class="key.card_type">
                {{ getCardTypeText(key.card_type) }}
              </span>
            </td>
            <td>
              <span :class="['status', getStatusClass(key.status)]">
                {{ getStatusText(key.status) }}
              </span>
            </td>
            <td class="machine-code-cell" :title="key.machine_code || ''">
              <span v-if="key.machine_code" class="machine-code-tag">{{ key.machine_code }}</span>
              <span v-else class="machine-code-empty">未绑定</span>
            </td>
            <td class="ip-cell" :title="key.ip_address || ''">
              <span v-if="key.ip_address" class="ip-tag">{{ key.ip_address }}</span>
              <span v-else class="ip-empty">未绑定</span>
            </td>
            <td class="col-time">{{ formatTableDate(key.create_time) }}</td>
            <td class="duration-cell">
              <template v-if="key.card_type === 'time'">
                <span
                  v-if="key.expire_time"
                  :class="['time-countdown', { 'is-expired': isTimeCardExpired(key) }]"
                >
                  {{ formatTimeCardRemaining(key) }}
                </span>
                <span v-else class="time-spec">{{ formatDurationSpec(key) }}·未激活</span>
              </template>
              <template v-else>{{ key.remaining_count }} 次</template>
            </td>
            <td class="col-actions">
              <div class="action-buttons">
                <button type="button" class="btn-secondary btn-sm" @click="copyKey(getCardKeyText(key))">复制</button>
                <button type="button" class="btn-primary btn-sm" @click="editKey(key)">编辑</button>
                <button
                  v-if="key.status === 0 || key.status === 2"
                  type="button"
                  class="btn-success btn-sm"
                  @click="toggleKeyStatus(key, 1)"
                >启用</button>
                <button
                  v-if="key.status === 1"
                  type="button"
                  class="btn-warning btn-sm"
                  @click="toggleKeyStatus(key, 2)"
                >暂停</button>
                <button type="button" class="btn-danger btn-sm" @click="deleteKey(key)">删除</button>
              </div>
            </td>
          </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination-container" v-if="totalPages > 1">
      <div class="pagination">
        <!-- 上一页按钮 -->
        <button 
          class="pagination-btn" 
          :disabled="currentPage === 1" 
          @click="goToPage(currentPage - 1)"
        >
          ‹ 上一页
        </button>
        
        <!-- 页码按钮 -->
        <div class="page-numbers">
          <!-- 第一页 -->
          <button 
            v-if="showFirstPage" 
            class="page-btn" 
            :class="{ active: currentPage === 1 }" 
            @click="goToPage(1)"
          >
            1
          </button>
          
          <!-- 省略号 -->
          <span v-if="showStartEllipsis" class="ellipsis">...</span>
          
          <!-- 中间页码 -->
          <button 
            v-for="page in visiblePages" 
            :key="page" 
            class="page-btn" 
            :class="{ active: currentPage === page }" 
            @click="goToPage(page)"
          >
            {{ page }}
          </button>
          
          <!-- 省略号 -->
          <span v-if="showEndEllipsis" class="ellipsis">...</span>
          
          <!-- 最后一页 -->
          <button 
            v-if="showLastPage" 
            class="page-btn" 
            :class="{ active: currentPage === totalPages }" 
            @click="goToPage(totalPages)"
          >
            {{ totalPages }}
          </button>
        </div>
        
        <!-- 下一页按钮 -->
        <button 
          class="pagination-btn" 
          :disabled="currentPage === totalPages" 
          @click="goToPage(currentPage + 1)"
        >
          下一页 ›
        </button>
        
        <!-- 页码跳转 -->
        <div class="page-jump">
          <span>跳转到</span>
          <input 
            type="number" 
            v-model.number="jumpPage" 
            :min="1" 
            :max="totalPages" 
            @keyup.enter="jumpToPage"
            class="jump-input"
          />
          <span>页</span>
          <button class="jump-btn" @click="jumpToPage">跳转</button>
        </div>
        
        <!-- 分页信息 -->
        <div class="pagination-info">
          共 {{ totalItems }} 条记录，第 {{ currentPage }} / {{ totalPages }} 页
        </div>
      </div>
    </div>

    <!-- 导出数据模态框 -->
    <div v-if="showExportModal" class="modal-overlay" @click="showExportModal = false">
      <div class="modal-content export-modal" @click.stop>
        <div class="modal-header">
          <h3>导出卡密数据</h3>
          <button class="close-btn" @click="showExportModal = false">×</button>
        </div>
        <div class="modal-body export-modal-body">
          <div class="export-layout">
            <div class="export-layout-left">
              <div class="setting-group">
                <h4>卡密加密方式</h4>
                <p class="export-scope-hint">按存储类型筛选要导出的记录（加密卡密在 cards 表，简单卡密在 simple_cards 表）。</p>
                <div class="export-segment-group export-segment-group--stack">
                  <label
                    v-for="opt in exportStorageScopeOptions"
                    :key="opt.value"
                    class="export-segment-option"
                    :class="{ active: exportStorageScope === opt.value }"
                  >
                    <input type="radio" v-model="exportStorageScope" :value="opt.value">
                    <span>{{ opt.label }}</span>
                  </label>
                </div>
              </div>

              <div class="setting-group">
                <h4>导出范围</h4>
                <p class="export-scope-hint">先应用列表顶部搜索筛选，再按使用状态筛选。</p>
                <div class="export-segment-group">
                  <label
                    v-for="opt in exportUsageScopeOptions"
                    :key="opt.value"
                    class="export-segment-option"
                    :class="{ active: exportUsageScope === opt.value }"
                  >
                    <input type="radio" v-model="exportUsageScope" :value="opt.value">
                    <span>{{ opt.label }}</span>
                  </label>
                </div>
              </div>

              <div class="setting-group">
                <h4>创建时间</h4>
                <p class="export-scope-hint">可选：仅导出指定日期范围内创建的卡密（留空表示不限制）。</p>
                <div class="export-date-range">
                  <label class="export-date-field">
                    <span>起</span>
                    <input v-model="exportCreateDateStart" type="date" />
                  </label>
                  <label class="export-date-field">
                    <span>止</span>
                    <input v-model="exportCreateDateEnd" type="date" />
                  </label>
                </div>
              </div>

              <div class="setting-group">
                <h4>导出格式</h4>
                <div class="export-segment-group">
                  <label
                    v-for="opt in exportFormatOptions"
                    :key="opt.value"
                    class="export-segment-option"
                    :class="{ active: exportFormat === opt.value }"
                  >
                    <input type="radio" v-model="exportFormat" :value="opt.value">
                    <span>{{ opt.label }}</span>
                  </label>
                </div>
              </div>

              <div class="setting-group">
                <h4>选择导出列</h4>
                <p class="export-column-hint">导入其它平台时通常只勾选「卡密」；每行一条密钥。</p>
                <div class="checkbox-grid">
                  <label v-for="col in cardExportColumns" :key="col.key" class="checkbox-label">
                    <input type="checkbox" v-model="selectedColumns" :value="col.key">
                    {{ col.label }}
                  </label>
                </div>
              </div>
            </div>

            <div class="export-layout-right preview-section">
              <h4>数据预览</h4>
              <p class="preview-meta">前 5 条 · 共 {{ keysForExport.length }} 条符合条件</p>
              <div class="preview-table-container">
                <table v-if="previewData.length" class="preview-table">
                  <thead>
                    <tr>
                      <th v-for="colKey in selectedColumns" :key="colKey">
                        {{ getExportColumnLabel(colKey) }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, index) in previewData" :key="index">
                      <td v-for="colKey in selectedColumns" :key="colKey" :title="String(row[colKey] ?? '')">
                        {{ row[colKey] }}
                      </td>
                    </tr>
                  </tbody>
                </table>
                <div v-else class="preview-empty">当前筛选条件下暂无数据</div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showExportModal = false">取消</button>
          <button class="btn-primary" @click="exportData" :disabled="selectedColumns.length === 0 || exporting">
            <i class="fas" :class="exporting ? 'fa-spinner fa-spin' : 'fa-file-export'"></i>
            {{ exporting ? '导出中...' : '确认导出' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 导入卡密模态框 -->
    <div v-if="showImportModal" class="modal-overlay" @click="showImportModal = false">
      <div class="modal-content import-modal" @click.stop>
        <div class="modal-header">
          <h3>导入卡密</h3>
          <button class="close-btn" @click="showImportModal = false">×</button>
        </div>
        <div class="modal-body">
          <p class="form-hint">
            支持 .txt / .csv / .xlsx。可仅填卡密（使用下方默认值），或在 Excel/CSV 中填写「卡密、类型、时长/次数、单位」列。
          </p>
          <p class="import-template-row">
            <button type="button" class="import-template-btn" @click="downloadImportTemplate">
              <i class="fas fa-file-excel"></i>
              下载 Excel 导入示例
            </button>
            <span class="import-template-note">含示例卡密与填写说明，可按格式填写后导入</span>
          </p>
          <div class="form-group">
            <label>选择文件</label>
            <input type="file" accept=".txt,.csv,.xlsx,.xls" @change="onImportFileChange">
          </div>
          <div v-if="importPreview.length" class="form-group">
            <label>预览（前 5 条，共 {{ importPreview.length }} 条）</label>
            <pre class="import-preview">{{ importPreviewDisplay }}</pre>
          </div>
          <p v-if="importPreview.length && !importFileHasSpecs" class="form-hint import-defaults-hint">
            文件中未包含类型/时长/次数列，将统一使用下方默认值。
          </p>
          <div class="form-group">
            <label>默认卡密类型</label>
            <select v-model="importForm.card_type">
              <option value="time">时间卡</option>
              <option value="count">次数卡</option>
            </select>
          </div>
          <div v-if="importForm.card_type === 'time'" class="form-group">
            <label>默认时长</label>
            <div class="inline-fields">
              <input
                v-if="importForm.duration_unit !== 'permanent'"
                v-model.number="importForm.duration"
                type="number"
                min="1"
              >
              <select v-model="importForm.duration_unit">
                <option value="days">天</option>
                <option value="hours">小时</option>
                <option value="permanent">永久</option>
              </select>
            </div>
          </div>
          <div v-if="importForm.card_type === 'count'" class="form-group">
            <label>默认总次数</label>
            <input v-model.number="importForm.total_count" type="number" min="1">
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showImportModal = false">取消</button>
          <button class="btn-primary" :disabled="importing || importPreview.length === 0" @click="confirmImport">
            {{ importing ? '导入中…' : `确认导入 ${importPreview.length} 条` }}
          </button>
        </div>
      </div>
    </div>

    <!-- 编辑卡密模态框 -->
    <div v-if="showEditKeyModal" class="modal-overlay" @click="showEditKeyModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>编辑卡密</h3>
          <button class="close-btn" @click="showEditKeyModal = false">
            ×
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>卡密</label>
            <input type="text" :value="editingKey.card_key" readonly class="readonly-input" />
          </div>
          <div class="form-group">
            <label>卡密类型</label>
            <select v-model="editingKey.card_type">
              <option value="time">时间卡密</option>
              <option value="count">次数卡密</option>
            </select>
          </div>
          <div class="form-group" v-if="editingKey.card_type === 'time'">
            <label>持续时长</label>
            <div class="duration-input-row">
              <input
                v-if="editingKey.duration_unit !== 'permanent'"
                type="number"
                v-model="editingKey.duration"
                min="1"
                :max="editingKey.duration_unit === 'hours' ? 8760 : 365"
              />
              <select v-model="editingKey.duration_unit">
                <option value="days">天</option>
                <option value="hours">小时</option>
                <option value="permanent">永久</option>
              </select>
            </div>
          </div>
          <div class="form-group" v-if="editingKey.card_type === 'count'">
            <label>总次数</label>
            <input type="number" v-model="editingKey.total_count" min="1" max="10000" />
          </div>
          <div class="form-group" v-if="editingKey.card_type === 'count'">
            <label>剩余次数</label>
            <input type="number" v-model="editingKey.remaining_count" min="0" :max="editingKey.total_count" />
          </div>
          <div class="form-group adjust-group">
            <label>{{ editingKey.card_type === 'time' ? '加时 / 扣时（可选）' : '加次 / 扣次（可选）' }}</label>
            <div class="adjust-row">
              <select v-model="editingKey.adjust_direction">
                <option value="add">{{ editingKey.card_type === 'time' ? '加时' : '加次' }}</option>
                <option value="subtract">{{ editingKey.card_type === 'time' ? '扣时' : '扣次' }}</option>
              </select>
              <input
                type="number"
                v-model.number="editingKey.adjust_amount"
                min="0"
                placeholder="数量"
              />
              <select v-model="editingKey.adjust_unit">
                <template v-if="editingKey.card_type === 'time'">
                  <option value="hours">小时</option>
                  <option value="days">天</option>
                </template>
                <option v-else value="times">次</option>
              </select>
            </div>
            <small class="form-hint">
              保存时若数量大于 0 则生效；已激活时间卡按到期时间加减，未激活则改有效时长。
            </small>
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="editingKey.status">
              <option value="0">未使用</option>
              <option value="1">已使用</option>
              <option value="2">已停用</option>
            </select>
          </div>
          <div class="form-group" v-if="editingKey.card_type === 'time'">
            <label>允许重复验证</label>
            <select v-model="editingKey.allow_reverify">
              <option value="1">允许</option>
              <option value="0">不允许</option>
            </select>
            <small class="form-hint" v-if="editingKey.allow_reverify == 0">关闭后，时间卡密激活一次即不可再次验证</small>
            <small class="form-hint" v-else>开启后，时间卡密在有效期内可无限次验证</small>
          </div>
          <div class="form-group stack-time-stack-group">
            <div
              class="stack-option-card"
              :class="{ 'stack-option-card--active': editingKey.allow_self_unbind }"
            >
              <label class="stack-toggle-row">
                <span class="stack-switch">
                  <input
                    type="checkbox"
                    v-model="editingKey.allow_self_unbind"
                    class="stack-switch-input"
                  />
                  <span class="stack-switch-track">
                    <span class="stack-switch-thumb"></span>
                  </span>
                </span>
                <span class="stack-toggle-copy">
                  <span class="stack-toggle-title">
                    允许自助解绑
                    <span v-if="editingKey.allow_self_unbind" class="stack-toggle-pill">已开启</span>
                  </span>
                  <span class="stack-toggle-desc">
                    开启后，用户可凭卡密在首页「在线解绑」自行清空机器码；关闭则只能通过管理员重置。
                  </span>
                </span>
              </label>
            </div>
          </div>
          <template v-if="editingKey.allow_self_unbind">
            <div class="form-group stack-time-stack-group">
              <div
                class="stack-option-card"
                :class="{ 'stack-option-card--active': editingKey.require_device_unbind }"
              >
                <label class="stack-toggle-row">
                  <span class="stack-switch">
                    <input
                      type="checkbox"
                      v-model="editingKey.require_device_unbind"
                      class="stack-switch-input"
                    />
                    <span class="stack-switch-track">
                      <span class="stack-switch-thumb"></span>
                    </span>
                  </span>
                  <span class="stack-toggle-copy">
                    <span class="stack-toggle-title">原设备解绑</span>
                    <span class="stack-toggle-desc">开启后，用户解绑须填写与原绑定一致的设备码。</span>
                  </span>
                </label>
              </div>
            </div>
            <div class="form-group">
              <label>解绑冷却间隔（小时）</label>
              <input
                v-model.number="editingKey.unbind_cooldown_hours"
                type="number"
                min="0"
                placeholder="0 表示不限制"
              />
              <small class="form-hint">两次自助解绑之间的最短间隔，0 为不限制。</small>
            </div>
            <div class="form-group">
              <label>解绑次数上限</label>
              <input
                v-model.number="editingKey.unbind_max_count"
                type="number"
                min="0"
                placeholder="0 表示不限制"
              />
              <small class="form-hint">该卡累计可自助解绑的最大次数，0 为不限制。</small>
            </div>
            <div v-if="editingKey.unbind_count > 0 || editingKey.last_unbind_time" class="form-group">
              <label>解绑统计</label>
              <p class="form-hint">
                已解绑 {{ editingKey.unbind_count || 0 }} 次
                <span v-if="editingKey.last_unbind_time">，上次解绑：{{ editingKey.last_unbind_time }}</span>
              </p>
            </div>
          </template>
          <div class="form-group">
            <label>机器码</label>
            <div class="machine-code-edit">
              <input
                type="text"
                :value="editingKey.machine_code || ''"
                readonly
                class="readonly-input"
                :placeholder="editingKey.machine_code ? '' : '未绑定'"
              />
              <button
                v-if="editingKey.machine_code"
                class="btn-danger btn-sm"
                @click="editingKey.machine_code = ''"
                title="重置机器码"
              >
                <i class="fas fa-undo"></i> 重置
              </button>
              <span v-else class="machine-code-hint">未绑定，无需重置</span>
            </div>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showEditKeyModal = false">取消</button>
          <button class="btn-primary" @click="updateKey">
            <i class="fas fa-save"></i>
            保存
          </button>
        </div>
      </div>
    </div>

    <!-- 批量加扣时模态框 -->
    <div v-if="showBatchAdjustModal" class="modal-overlay" @click="showBatchAdjustModal = false">
      <div class="modal-content batch-adjust-modal" @click.stop>
        <div class="modal-header">
          <h3>批量加时 / 扣时</h3>
          <button class="close-btn" @click="showBatchAdjustModal = false">×</button>
        </div>
        <div class="modal-body">
          <p class="batch-adjust-scope">
            作用范围：
            <strong v-if="selectedIds.length">已勾选 {{ selectedIds.length }} 条</strong>
            <strong v-else>全库 {{ props.keys?.length || 0 }} 条</strong>
            <span class="form-hint">（时间卡按小时/天，次数卡按「次」；不匹配类型自动跳过）</span>
          </p>
          <div class="form-group">
            <label>操作</label>
            <select v-model="batchAdjustForm.adjust_direction">
              <option value="add">增加</option>
              <option value="subtract">扣减</option>
            </select>
          </div>
          <div class="form-group">
            <label>数量</label>
            <input type="number" v-model.number="batchAdjustForm.adjust_amount" min="1" />
          </div>
          <div class="form-group">
            <label>单位</label>
            <select v-model="batchAdjustForm.adjust_unit">
              <option value="hours">小时（时间卡）</option>
              <option value="days">天（时间卡）</option>
              <option value="times">次（次数卡）</option>
            </select>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showBatchAdjustModal = false">取消</button>
          <button class="btn-primary" @click="confirmBatchAdjust">
            <i class="fas fa-check"></i>
            确认执行
          </button>
        </div>
      </div>
    </div>

    <!-- 生成卡密模态框 -->
    <div v-if="showCreateKeyModal" class="modal-overlay" @click="showCreateKeyModal = false">
      <div
        class="modal-content create-key-modal"
        :class="{ 'create-key-modal--simple': !newKey.use_encrypted }"
        @click.stop
      >
        <div class="modal-header">
          <h3>生成卡密</h3>
          <button class="close-btn" @click="showCreateKeyModal = false">
            ×
          </button>
        </div>
        <div class="modal-body create-key-modal-body">
          <div class="create-form-layout" :class="{ 'is-simple': !newKey.use_encrypted }">
            <div class="create-form-col create-form-col--fields">
              <div class="form-row form-row-2">
                <div class="form-group form-group-compact">
                  <label>卡密形式</label>
                  <select v-model="newKey.use_encrypted">
                    <option :value="true">加密卡密（高级）</option>
                    <option :value="false">简单卡密（明文）</option>
                  </select>
                </div>
                <div class="form-group form-group-compact">
                  <label>卡密类型</label>
                  <select v-model="newKey.card_type">
                    <option value="time">时间卡密</option>
                    <option value="count">次数卡密</option>
                  </select>
                </div>
              </div>

              <div
                class="form-row"
                :class="{
                  'form-row-3': !newKey.use_encrypted && newKey.card_type === 'count',
                  'form-row-2': (!newKey.use_encrypted && newKey.card_type === 'time') || (newKey.use_encrypted && newKey.card_type === 'count')
                }"
              >
                <div v-if="!newKey.use_encrypted" class="form-group form-group-compact">
                  <label>卡密长度</label>
                  <input
                    type="number"
                    v-model.number="newKey.key_length"
                    min="4"
                    max="128"
                    :disabled="newKey.manual_mode"
                  />
                </div>
                <div class="form-group form-group-compact">
                  <label>生成数量</label>
                  <input
                    type="number"
                    v-model="newKey.count"
                    min="1"
                    max="100"
                    :disabled="!newKey.use_encrypted && newKey.manual_mode"
                  />
                </div>
                <div v-if="newKey.card_type === 'count'" class="form-group form-group-compact">
                  <label>总次数</label>
                  <input type="number" v-model="newKey.total_count" min="1" max="10000" />
                </div>
              </div>

              <div v-if="newKey.card_type === 'time'" class="form-group form-group-compact create-duration-field">
                <label>持续时长</label>
                <div class="duration-input-row">
                  <input
                    v-if="newKey.duration_unit !== 'permanent'"
                    type="number"
                    v-model="newKey.duration"
                    min="1"
                    :max="newKey.duration_unit === 'hours' ? 8760 : 365"
                  />
                  <select v-model="newKey.duration_unit">
                    <option value="days">天</option>
                    <option value="hours">小时</option>
                    <option value="permanent">永久</option>
                  </select>
                </div>
              </div>

              <div v-if="newKey.card_type === 'time'" class="form-group form-group-compact">
                <label>允许重复验证</label>
                <select v-model="newKey.allow_reverify">
                  <option value="1">允许</option>
                  <option value="0">不允许</option>
                </select>
              </div>
            </div>

            <div class="create-form-col create-form-col--options">
              <div v-if="!newKey.use_encrypted" class="form-group stack-time-stack-group stack-time-stack-group--compact">
                <div class="stack-option-card stack-option-card--compact" :class="{ 'stack-option-card--active': newKey.manual_mode }">
                  <label class="stack-toggle-row">
                    <span class="stack-switch stack-switch--sm">
                      <input type="checkbox" v-model="newKey.manual_mode" class="stack-switch-input" />
                      <span class="stack-switch-track"><span class="stack-switch-thumb"></span></span>
                    </span>
                    <span class="stack-toggle-copy">
                      <span class="stack-toggle-title">手动输入卡密</span>
                      <span class="stack-toggle-desc">按生成数量逐行填写；关闭则按序号自动递增（不与库内重复）。</span>
                    </span>
                  </label>
                </div>
              </div>

              <div v-if="newKey.card_type === 'time' && newKey.duration_unit !== 'permanent'" class="form-group stack-time-stack-group stack-time-stack-group--compact">
                <div
                  class="stack-option-card stack-option-card--compact"
                  :class="{ 'stack-option-card--active': newKey.stack_time_if_same_machine }"
                >
                  <label class="stack-toggle-row">
                    <span class="stack-switch stack-switch--sm">
                      <input type="checkbox" v-model="newKey.stack_time_if_same_machine" class="stack-switch-input" />
                      <span class="stack-switch-track"><span class="stack-switch-thumb"></span></span>
                    </span>
                    <span class="stack-toggle-copy">
                      <span class="stack-toggle-title">同机时长叠加（续期）</span>
                      <span class="stack-toggle-desc">续期时累加到原卡到期时间。</span>
                    </span>
                  </label>
                </div>
              </div>

              <div class="form-group stack-time-stack-group stack-time-stack-group--compact">
                <div
                  class="stack-option-card stack-option-card--compact"
                  :class="{ 'stack-option-card--active': newKey.allow_self_unbind }"
                >
                  <label class="stack-toggle-row">
                    <span class="stack-switch stack-switch--sm">
                      <input type="checkbox" v-model="newKey.allow_self_unbind" class="stack-switch-input" />
                      <span class="stack-switch-track"><span class="stack-switch-thumb"></span></span>
                    </span>
                    <span class="stack-toggle-copy">
                      <span class="stack-toggle-title">允许自助解绑</span>
                      <span class="stack-toggle-desc">用户可在首页自行解绑机器码。</span>
                    </span>
                  </label>
                </div>
              </div>

              <template v-if="newKey.allow_self_unbind">
                <div class="form-group stack-time-stack-group stack-time-stack-group--compact">
                  <div
                    class="stack-option-card stack-option-card--compact"
                    :class="{ 'stack-option-card--active': newKey.require_device_unbind }"
                  >
                    <label class="stack-toggle-row">
                      <span class="stack-switch stack-switch--sm">
                        <input type="checkbox" v-model="newKey.require_device_unbind" class="stack-switch-input" />
                        <span class="stack-switch-track"><span class="stack-switch-thumb"></span></span>
                      </span>
                      <span class="stack-toggle-copy">
                        <span class="stack-toggle-title">原设备解绑</span>
                        <span class="stack-toggle-desc">解绑时须验证原设备码一致。</span>
                      </span>
                    </label>
                  </div>
                </div>
                <div class="form-group form-group-compact">
                  <label>解绑冷却（小时）</label>
                  <input v-model.number="newKey.unbind_cooldown_hours" type="number" min="0" placeholder="0=不限" />
                </div>
                <div class="form-group form-group-compact">
                  <label>解绑次数上限</label>
                  <input v-model.number="newKey.unbind_max_count" type="number" min="0" placeholder="0=不限" />
                </div>
              </template>
            </div>
          </div>

              <div v-if="!newKey.use_encrypted && !newKey.manual_mode" class="form-group form-group-compact create-form-full">
                <label>卡密前缀（可选）</label>
                <input
                  v-model.trim="newKey.key_prefix"
                  type="text"
                  maxlength="64"
                  placeholder="如 VIP，后面自动递增数字"
                />
                <small class="form-hint">自动按「前缀 + 递增序号」生成，并与库内已有卡密查重。</small>
              </div>

              <div v-if="!newKey.use_encrypted && newKey.manual_mode" class="form-group form-group-compact create-form-full">
            <label>卡密内容（每行一条，共 {{ newKey.count }} 条）</label>
            <textarea
              v-model="newKey.manual_keys_text"
              rows="4"
              class="manual-keys-textarea"
              placeholder="例如：&#10;VIP2026-A&#10;VIP2026-B"
              spellcheck="false"
            />
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showCreateKeyModal = false">取消</button>
          <button class="btn-primary" @click="createKeys">
            <i class="fas fa-key"></i>
            生成
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { cardApi } from '../services/api.js'
import { copyToClipboard } from '../utils/clipboard.js'
import '../styles/card-export-modal.css'
import {
  CARD_EXPORT_COLUMNS,
  EXPORT_FORMAT_OPTIONS,
  EXPORT_STORAGE_SCOPE_OPTIONS,
  EXPORT_USAGE_SCOPE_OPTIONS,
  filterCardsForExport,
  getCardKeyText,
  getExportColumnLabel,
  isPermanentDurationUnit,
  parseCreateTimeMs,
  processCardExportData,
} from '../utils/cardExport.js'

const props = defineProps({
  keys: Array
})

const emit = defineEmits([
  'create-keys',
  'import-keys',
  'delete-key',
  'batch-delete-keys',
  'batch-unbind-keys',
  'batch-adjust-keys',
  'update-key',
  'toggle-key-status'
])

/** 每秒刷新，驱动时间卡密倒计时 */
const nowMs = ref(Date.now())
let countdownTimer = null

onMounted(() => {
  countdownTimer = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})

const pad2 = (n) => String(n).padStart(2, '0')

const parseExpireTimeMs = (key) => {
  const raw = key?.expire_time
  if (raw == null || raw === '') return null
  const t = new Date(raw).getTime()
  return Number.isFinite(t) ? t : null
}

const isTimeCardExpired = (key) => {
  if (isPermanentDurationUnit(key?.duration_unit)) return false
  const end = parseExpireTimeMs(key)
  return end != null && end <= nowMs.value
}

const formatTimeCardRemaining = (key) => {
  if (isPermanentDurationUnit(key?.duration_unit)) return '永久'
  const end = parseExpireTimeMs(key)
  if (end == null) return '—'
  const ms = end - nowMs.value
  if (ms <= 0) return '已过期'
  const totalSec = Math.floor(ms / 1000)
  const days = Math.floor(totalSec / 86400)
  const h = Math.floor((totalSec % 86400) / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  if (days > 0) {
    return `${days} 天 ${pad2(h)}:${pad2(m)}:${pad2(s)}`
  }
  return `${pad2(h)}:${pad2(m)}:${pad2(s)}`
}

const showCreateKeyModal = ref(false)
const showEditKeyModal = ref(false)
const showBatchAdjustModal = ref(false)
const showExportModal = ref(false)
const showImportModal = ref(false)
const importing = ref(false)
const importPreview = ref([])
const importFileHasSpecs = ref(false)
const importForm = reactive({
  card_type: 'time',
  duration: 30,
  duration_unit: 'days',
  total_count: 100,
  verify_method: 'web',
  allow_reverify: 1,
  allow_self_unbind: false
})
const exporting = ref(false)
const exportFormat = ref('xlsx')
/** all | unused | used — 导出时筛选未使用 / 已使用（含暂停） */
const exportUsageScope = ref('all')
/** all | encrypted | simple — 按卡密存储/加密方式筛选 */
const exportStorageScope = ref('all')
/** all | encrypted | simple — 列表搜索按卡密类型筛选 */
const searchStorageScope = ref('all')
const searchType = ref('card_key')
const searchQuery = ref('')
/** 导出：创建时间起止（YYYY-MM-DD，留空不限制） */
const exportCreateDateStart = ref('')
const exportCreateDateEnd = ref('')

const searchStorageScopeLabel = computed(() => {
  const map = { all: '全部', encrypted: '加密卡密', simple: '简单卡密' }
  return map[searchStorageScope.value] || '全部'
})

const isSimpleCard = (key) =>
  key?.storage_type === 'simple' || key?.encryption_type === 'simple'

const getSearchFieldValue = (key, field) => {
  if (field === 'card_key') return getCardKeyText(key)
  if (field === 'machine_code') return (key?.machine_code || key?.machineCode || '').toString()
  if (field === 'ip_address') return (key?.ip_address || key?.ipAddress || '').toString()
  return (key?.[field] ?? '').toString()
}

const searchTypeLabel = computed(() => {
  const map = { card_key: '卡密', machine_code: '机器码', ip_address: 'IP' }
  return map[searchType.value] || '关键字'
})

const searchPlaceholder = computed(() => {
  const map = {
    card_key: '输入卡密关键字，支持部分匹配',
    machine_code: '输入设备码关键字，筛选已绑定该机器码的卡密',
    ip_address: '输入 IP 关键字，筛选使用该 IP 激活的卡密'
  }
  return map[searchType.value] || '输入关键字搜索'
})

const emptyFilterHint = computed(
  () => `没有匹配该${searchTypeLabel.value}的卡密（可尝试缩短关键字或清除筛选）`
)

const clearSearch = () => {
  searchQuery.value = ''
}
/** 默认仅导出卡密，便于批量导入外部系统（不再默认附带状态、创建时间等列） */
const selectedColumns = ref(['card_key'])
const cardExportColumns = CARD_EXPORT_COLUMNS
const exportStorageScopeOptions = EXPORT_STORAGE_SCOPE_OPTIONS
const exportUsageScopeOptions = EXPORT_USAGE_SCOPE_OPTIONS
const exportFormatOptions = EXPORT_FORMAT_OPTIONS

const filteredKeys = computed(() => {
  let list = props.keys || []
  if (searchStorageScope.value === 'simple') {
    list = list.filter((k) => isSimpleCard(k))
  } else if (searchStorageScope.value === 'encrypted') {
    list = list.filter((k) => !isSimpleCard(k))
  }
  const q = (searchQuery.value || '').trim().toLowerCase()
  if (!q) return list
  const field = searchType.value
  return list.filter((k) => getSearchFieldValue(k, field).toLowerCase().includes(q))
})

/** 勾选 id，用于表格与导出范围独立 */
const selectedIds = ref([])
const selectedIdSet = computed(() => new Set(selectedIds.value))

watch(filteredKeys, (list) => {
  const keys = new Set((list ?? []).map((k) => rowSelectKey(k)))
  selectedIds.value = selectedIds.value.filter((sk) => keys.has(sk))
})

const keysForExport = computed(() => {
  return filterCardsForExport(filteredKeys.value, {
    storageScope: exportStorageScope.value,
    usageScope: exportUsageScope.value,
    createDateStart: exportCreateDateStart.value,
    createDateEnd: exportCreateDateEnd.value,
  })
})

const previewData = computed(() => {
  const src = keysForExport.value
  if (!src.length) return []
  return processCardExportData(src.slice(0, 5), selectedColumns.value, formatDate)
})

const IMPORT_HEADER_KEY = new Set(['卡密', 'card_key', 'key'])
const IMPORT_HEADER_TYPE = new Set(['类型', 'card_type', 'type'])
const IMPORT_HEADER_VALUE = new Set(['时长/次数', '时长', '次数', 'duration', 'total_count', 'value'])
const IMPORT_HEADER_UNIT = new Set(['单位', 'duration_unit', 'unit'])

function splitImportLine(line) {
  return String(line ?? '')
    .split(/[,;\t]/)
    .map((s) => s.trim().replace(/^["']|["']$/g, ''))
}

function isImportHeaderCell(value, headerSet) {
  const normalized = String(value ?? '').trim().toLowerCase()
  return [...headerSet].some((h) => h.toLowerCase() === normalized)
}

function isImportHeaderRow(cells) {
  if (!cells?.length) return false
  return isImportHeaderCell(cells[0], IMPORT_HEADER_KEY)
    || isImportHeaderCell(cells[1], IMPORT_HEADER_TYPE)
    || cells.some((cell) => isImportHeaderCell(cell, IMPORT_HEADER_VALUE))
}

function resolveImportColumnIndexes(cells) {
  const indexes = { key: 0, type: -1, value: -1, unit: -1 }
  cells.forEach((cell, idx) => {
    const normalized = String(cell ?? '').trim().toLowerCase()
    if (isImportHeaderCell(cell, IMPORT_HEADER_KEY)) indexes.key = idx
    else if (isImportHeaderCell(cell, IMPORT_HEADER_TYPE)) indexes.type = idx
    else if (isImportHeaderCell(cell, IMPORT_HEADER_VALUE)) indexes.value = idx
    else if (isImportHeaderCell(cell, IMPORT_HEADER_UNIT)) indexes.unit = idx
  })
  return indexes
}

function normalizeImportCardType(raw) {
  const text = String(raw ?? '').trim().toLowerCase()
  if (!text) return null
  if (text === 'time' || text.includes('时间')) return 'time'
  if (text === 'count' || text.includes('次数')) return 'count'
  return null
}

function normalizeImportDurationUnit(raw) {
  const text = String(raw ?? '').trim().toLowerCase()
  if (!text) return null
  if (text === 'permanent' || text.includes('永久')) return 'permanent'
  if (text === 'hours' || text.includes('小时')) return 'hours'
  if (text === 'days' || text.includes('天') || text.includes('日')) return 'days'
  return null
}

function getImportDefaults() {
  return {
    card_type: importForm.card_type,
    duration: importForm.duration,
    duration_unit: importForm.duration_unit,
    total_count: importForm.total_count,
  }
}

function buildImportItem(cardKey, cardTypeRaw, valueRaw, unitRaw, defaults, fromFileSpec = false) {
  const cardKeyText = String(cardKey ?? '').trim()
  if (!cardKeyText) return null
  if (isImportHeaderCell(cardKeyText, IMPORT_HEADER_KEY)) return null

  const cardType = normalizeImportCardType(cardTypeRaw) || defaults.card_type
  const valueText = String(valueRaw ?? '').trim()
  const unitText = String(unitRaw ?? '').trim()
  const hasFileSpec = fromFileSpec && (normalizeImportCardType(cardTypeRaw) || valueText || unitText)

  if (cardType === 'count') {
    const parsedCount = valueText ? Number.parseInt(valueText, 10) : Number.NaN
    const totalCount = Number.isFinite(parsedCount) && parsedCount > 0 ? parsedCount : defaults.total_count
    return {
      card_key: cardKeyText,
      card_type: 'count',
      duration: 0,
      duration_unit: 'days',
      total_count: totalCount,
      _fromFileSpec: hasFileSpec,
    }
  }

  let durationUnit = normalizeImportDurationUnit(unitText) || defaults.duration_unit
  let duration = defaults.duration
  if (valueText) {
    if (normalizeImportDurationUnit(valueText) === 'permanent' || valueText.includes('永久')) {
      durationUnit = 'permanent'
      duration = 0
    } else {
      const parsedDuration = Number.parseInt(valueText, 10)
      if (Number.isFinite(parsedDuration) && parsedDuration > 0) {
        duration = parsedDuration
      }
    }
  }
  if (isPermanentDurationUnit(durationUnit)) {
    duration = 0
  }

  return {
    card_key: cardKeyText,
    card_type: 'time',
    duration,
    duration_unit: durationUnit,
    total_count: 0,
    _fromFileSpec: hasFileSpec,
  }
}

function parseImportRowCells(cells, columnIndexes, defaults, isHeaderRow = false) {
  if (!Array.isArray(cells) || cells.length === 0 || isHeaderRow) return null
  const keyIdx = columnIndexes?.key ?? 0
  const cardKey = cells[keyIdx]
  const typeIdx = columnIndexes?.type ?? -1
  const valueIdx = columnIndexes?.value ?? -1
  const unitIdx = columnIndexes?.unit ?? -1
  const hasExtendedColumns = typeIdx >= 0 || valueIdx >= 0 || unitIdx >= 0 || cells.length >= 2

  if (!hasExtendedColumns) {
    return buildImportItem(cardKey, null, '', '', defaults, false)
  }

  const fallbackType = cells[1]
  const fallbackValue = cells[2]
  const fallbackUnit = cells[3]
  return buildImportItem(
    cardKey,
    typeIdx >= 0 ? cells[typeIdx] : fallbackType,
    valueIdx >= 0 ? cells[valueIdx] : fallbackValue,
    unitIdx >= 0 ? cells[unitIdx] : fallbackUnit,
    defaults,
    true,
  )
}

function mergeImportItems(items) {
  const merged = []
  const seen = new Set()
  let hasSpecs = false
  for (const item of items) {
    if (!item?.card_key || seen.has(item.card_key)) continue
    seen.add(item.card_key)
    if (item._fromFileSpec) hasSpecs = true
    const { _fromFileSpec, ...rest } = item
    merged.push(rest)
  }
  return { items: merged, hasSpecs }
}

function parseImportRows(rows, defaults) {
  if (!rows.length) return { items: [], hasSpecs: false }

  let startIndex = 0
  let columnIndexes = { key: 0, type: -1, value: -1, unit: -1 }
  const firstCells = rows[0].map((cell) => String(cell ?? '').trim())
  const maxCols = rows.reduce((max, row) => Math.max(max, row.length), 0)

  if (isImportHeaderRow(firstCells)) {
    columnIndexes = resolveImportColumnIndexes(firstCells)
    startIndex = 1
  } else if (maxCols >= 2) {
    columnIndexes = { key: 0, type: 1, value: 2, unit: 3 }
  }

  const parsed = []
  for (let i = startIndex; i < rows.length; i += 1) {
    const cells = rows[i].map((cell) => String(cell ?? '').trim())
    const item = parseImportRowCells(cells, columnIndexes, defaults, false)
    if (item) parsed.push(item)
  }
  return mergeImportItems(parsed)
}

function parseImportTextContent(text, defaults) {
  const lines = String(text ?? '').split(/\r?\n/).filter((line) => line.trim())
  const rows = lines.map((line) => splitImportLine(line))
  return parseImportRows(rows, defaults)
}

function formatImportPreviewRow(item) {
  if (item.card_type === 'count') {
    return `${item.card_key}  |  次数卡  |  ${item.total_count}次`
  }
  if (isPermanentDurationUnit(item.duration_unit)) {
    return `${item.card_key}  |  时间卡  |  永久`
  }
  const unitLabel = item.duration_unit === 'hours' ? '小时' : '天'
  return `${item.card_key}  |  时间卡  |  ${item.duration}${unitLabel}`
}

const importPreviewDisplay = computed(() =>
  importPreview.value.slice(0, 5).map(formatImportPreviewRow).join('\n'),
)

watch(
  () => [importForm.card_type, importForm.duration, importForm.duration_unit, importForm.total_count],
  () => {
    if (!importPreview.value.length || importFileHasSpecs.value) return
    const defaults = getImportDefaults()
    importPreview.value = importPreview.value.map((item) => {
      const rebuilt = buildImportItem(item.card_key, null, '', '', defaults, false)
      const { _fromFileSpec, ...rest } = rebuilt
      return rest
    })
  },
)

function downloadImportTemplate() {
  const wb = XLSX.utils.book_new()

  const sampleRows = [
    ['卡密', '类型', '时长/次数', '单位'],
    ['VIP20260001', '时间卡', '30', '天'],
    ['VIP20260002', '次数卡', '100', ''],
    ['VIP20260003', '时间卡', '永久', '永久'],
    ['VIP20260004', '时间卡', '24', '小时'],
    ['VIP20260005', '时间卡', '30', '天'],
  ]
  const ws = XLSX.utils.aoa_to_sheet(sampleRows)
  ws['!cols'] = [{ wch: 24 }, { wch: 10 }, { wch: 12 }, { wch: 10 }]
  XLSX.utils.book_append_sheet(wb, ws, '卡密列表')

  const instructionRows = [
    ['卡密导入模板 — 填写说明'],
    [''],
    ['列说明：'],
    ['  卡密 — 必填，每条一行'],
    ['  类型 — 时间卡 / 次数卡（可留空，使用导入页默认值）'],
    ['  时长/次数 — 时间卡填数字或「永久」；次数卡填总次数'],
    ['  单位 — 时间卡填 天 / 小时 / 永久（可留空，默认「天」）'],
    [''],
    ['1. 也支持 .txt / .csv：仅卡密时每行一条；含类型时用逗号/分号/制表符分隔各列。'],
    ['2. 首行标题可保留，导入时会自动识别并跳过。'],
    ['3. 请勿在同一文件中重复填写相同卡密。'],
  ]
  const wsInfo = XLSX.utils.aoa_to_sheet(instructionRows)
  wsInfo['!cols'] = [{ wch: 58 }]
  XLSX.utils.book_append_sheet(wb, wsInfo, '填写说明')

  XLSX.writeFile(wb, '卡密导入示例.xlsx')
  ElMessage.success('示例模板已开始下载')
}

async function onImportFileChange(event) {
  const file = event.target.files?.[0]
  importPreview.value = []
  importFileHasSpecs.value = false
  if (!file) return
  try {
    const defaults = getImportDefaults()
    const name = file.name.toLowerCase()
    let result = { items: [], hasSpecs: false }
    if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
      const buffer = await file.arrayBuffer()
      const wb = XLSX.read(buffer, { type: 'array' })
      const sheet = wb.Sheets[wb.SheetNames[0]]
      const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '' })
      const normalizedRows = rows
        .filter((row) => Array.isArray(row) && row.some((cell) => String(cell ?? '').trim()))
        .map((row) => row.map((cell) => String(cell ?? '').trim()))
      result = parseImportRows(normalizedRows, defaults)
    } else {
      const text = await file.text()
      result = parseImportTextContent(text, defaults)
    }
    importPreview.value = result.items
    importFileHasSpecs.value = result.hasSpecs
    if (importPreview.value.length === 0) {
      ElMessage.warning('未从文件中解析到有效卡密')
    }
  } catch (e) {
    ElMessage.error('文件解析失败：' + (e.message || '未知错误'))
  } finally {
    event.target.value = ''
  }
}

function confirmImport() {
  if (!importPreview.value.length) {
    ElMessage.warning('请先选择并解析文件')
    return
  }
  const payload = {
    import_items: importPreview.value,
    count: importPreview.value.length,
    verify_method: importForm.verify_method,
    allow_reverify: importForm.allow_reverify,
    allow_self_unbind: importForm.allow_self_unbind,
    use_encrypted: false,
  }
  emit('import-keys', payload)
  showImportModal.value = false
  importPreview.value = []
  importFileHasSpecs.value = false
}

const exportData = async () => {
  if (selectedColumns.value.length === 0) return

  const allData = keysForExport.value
  if (allData.length === 0) {
    ElMessage.warning('当前筛选条件下没有可导出的数据')
    return
  }

  exporting.value = true
  try {
    const dataToExport = processCardExportData(allData, selectedColumns.value, formatDate)
    
    // 创建工作簿
    const wb = XLSX.utils.book_new()
    
    // 转换表头为中文
    const header = selectedColumns.value.map(key => getExportColumnLabel(key))
    const body = dataToExport.map(row => selectedColumns.value.map(key => row[key]))
    
    const ws = XLSX.utils.aoa_to_sheet([header, ...body])
    XLSX.utils.book_append_sheet(wb, ws, "卡密数据")
    
    // 导出文件
    const fileName = `卡密导出_${new Date().toISOString().slice(0,10)}.${exportFormat.value}`
    XLSX.writeFile(wb, fileName)
    
    ElMessage.success('导出成功')
    showExportModal.value = false
  } catch (error) {
    console.error('Export failed:', error)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// 分页相关状态
const currentPage = ref(1)
const pageSize = ref(10)
const jumpPage = ref(1)

watch([searchQuery, searchType, searchStorageScope], () => {
  currentPage.value = 1
  jumpPage.value = 1
})

const newKey = reactive({
  card_type: 'time',
  count: 1,
  duration: 30,
  duration_unit: 'days',
  total_count: 100,
  verify_method: 'web',
  encryption_type: 'advanced',
  use_encrypted: true,
  key_length: 16,
  key_prefix: '',
  manual_mode: false,
  manual_keys_text: '',
  allow_reverify: 1,
  stack_time_if_same_machine: false,
  allow_self_unbind: false,
  require_device_unbind: false,
  unbind_cooldown_hours: 0,
  unbind_max_count: 0
})

const editingKey = reactive({
  id: null,
  card_key: '',
  card_type: 'time',
  duration: 30,
  duration_unit: 'days',
  total_count: 100,
  remaining_count: 100,
  status: 0,
  verify_method: 'web',
  encryption_type: 'sha1',
  allow_reverify: 1,
  machine_code: '',
  allow_self_unbind: false,
  require_device_unbind: false,
  unbind_cooldown_hours: 0,
  unbind_max_count: 0,
  unbind_count: 0,
  last_unbind_time: '',
  storage_type: 'encrypted',
  adjust_direction: 'add',
  adjust_unit: 'days',
  adjust_amount: 0
})

const batchAdjustForm = reactive({
  adjust_direction: 'add',
  adjust_unit: 'days',
  adjust_amount: 1
})

watch(
  () => editingKey.card_type,
  (type) => {
    editingKey.adjust_unit = type === 'count' ? 'times' : 'days'
    editingKey.adjust_amount = 0
  }
)

const rowSelectKey = (key) => `${key.storage_type || 'encrypted'}:${key.id}`

const getEncryptLabel = (key) => (isSimpleCard(key) ? '简单' : '加密')

// 计算属性
const totalItems = computed(() => filteredKeys.value?.length ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

// 当前页显示的数据
const paginatedKeys = computed(() => {
  const list = filteredKeys.value || []
  if (!list.length) return []
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return list.slice(start, end)
})

const pageAllSelected = computed(() => {
  const rows = paginatedKeys.value
  if (!rows.length) return false
  const set = selectedIdSet.value
  return rows.every((r) => set.has(rowSelectKey(r)))
})

const pagePartialSelected = computed(() => {
  const rows = paginatedKeys.value
  if (!rows.length) return false
  const set = selectedIdSet.value
  let n = 0
  for (const r of rows) {
    if (set.has(rowSelectKey(r))) n++
  }
  return n > 0 && n < rows.length
})

const toggleSelectCurrentPage = (checked) => {
  const rows = paginatedKeys.value
  const set = new Set(selectedIds.value)
  const want = !!checked
  for (const r of rows) {
    const sk = rowSelectKey(r)
    if (want) set.add(sk)
    else set.delete(sk)
  }
  selectedIds.value = Array.from(set)
}

const setRowSelected = (selectKey, checked) => {
  const set = new Set(selectedIds.value)
  if (checked) set.add(selectKey)
  else set.delete(selectKey)
  selectedIds.value = Array.from(set)
}

const parseSelectKey = (sk) => {
  const i = sk.indexOf(':')
  if (i < 0) return { id: Number(sk), storage_type: 'encrypted' }
  return { storage_type: sk.slice(0, i), id: Number(sk.slice(i + 1)) }
}

const mergeIntoSelection = (extraIds) => {
  selectedIds.value = [...new Set([...selectedIds.value, ...extraIds])]
}

const clearSelection = () => {
  selectedIds.value = []
}

const selectExpiredInFiltered = () => {
  const extras = filteredKeys.value
    .filter((k) => k.card_type === 'time' && isTimeCardExpired(k))
    .map((k) => rowSelectKey(k))
  if (!extras.length) {
    ElMessage.info('当前列表中没有已到期的时间卡')
    return
  }
  mergeIntoSelection(extras)
  ElMessage.success(`已勾选已到期的时间卡 ${extras.length} 条`)
}

const selectUsedInFiltered = () => {
  const extras = filteredKeys.value.filter((k) => Number(k.status) !== 0).map((k) => rowSelectKey(k))
  if (!extras.length) {
    ElMessage.info('当前列表中没有「已使用 / 暂停 / 已合并」的卡密')
    return
  }
  mergeIntoSelection(extras)
  ElMessage.success(`已勾选 ${extras.length} 条非「未使用」卡密`)
}

const batchDeleteSelected = async () => {
  const selected = [...selectedIds.value]
  if (!selected.length) {
    ElMessage.warning('请先勾选要删除的卡密')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selected.length} 条卡密？已使用、已过期、已暂停等均可删除，且不可恢复。`,
      '批量删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  const parsed = selected.map(parseSelectKey)
  emit('batch-delete-keys', {
    ids: parsed.map((p) => p.id),
    storageTypes: parsed.map((p) => p.storage_type)
  })
}

const buildBatchTargetPayload = () => {
  const selected = [...selectedIds.value]
  if (selected.length) {
    const parsed = selected.map(parseSelectKey)
    return {
      ids: parsed.map((p) => p.id),
      storageTypes: parsed.map((p) => p.storage_type),
      all: false
    }
  }
  return { all: true }
}

const batchUnbindKeys = async () => {
  const hasSelection = selectedIds.value.length > 0
  const targetLabel = hasSelection
    ? `选中的 ${selectedIds.value.length} 条`
    : `全库 ${props.keys?.length || 0} 条`
  try {
    await ElMessageBox.confirm(
      `确定对${targetLabel}卡密解绑机器码？仅已绑定设备码的会被清空，未绑定的不受影响。`,
      '批量解绑确认',
      {
        confirmButtonText: '确定解绑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  emit('batch-unbind-keys', buildBatchTargetPayload())
}

const openBatchAdjustModal = () => {
  batchAdjustForm.adjust_direction = 'add'
  batchAdjustForm.adjust_unit = 'days'
  batchAdjustForm.adjust_amount = 1
  showBatchAdjustModal.value = true
}

const confirmBatchAdjust = async () => {
  const amount = Number(batchAdjustForm.adjust_amount)
  if (!amount || amount <= 0) {
    ElMessage.warning('请输入大于 0 的数量')
    return
  }
  const hasSelection = selectedIds.value.length > 0
  const targetLabel = hasSelection
    ? `选中的 ${selectedIds.value.length} 条`
    : `全库 ${props.keys?.length || 0} 条`
  const unitLabel =
    batchAdjustForm.adjust_unit === 'hours'
      ? '小时'
      : batchAdjustForm.adjust_unit === 'days'
        ? '天'
        : '次'
  const actionLabel = batchAdjustForm.adjust_direction === 'subtract' ? '扣减' : '增加'
  try {
    await ElMessageBox.confirm(
      `确定对${targetLabel}卡密${actionLabel} ${amount} ${unitLabel}？时间卡与次数卡将按类型分别处理。`,
      '批量加扣时确认',
      {
        confirmButtonText: '确定执行',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  emit('batch-adjust-keys', {
    ...buildBatchTargetPayload(),
    adjust_direction: batchAdjustForm.adjust_direction,
    adjust_unit: batchAdjustForm.adjust_unit,
    adjust_amount: amount
  })
  showBatchAdjustModal.value = false
}

// 可见页码计算
const visiblePages = computed(() => {
  const pages = []
  const total = totalPages.value
  const current = currentPage.value
  
  if (total <= 7) {
    // 总页数小于等于7，显示所有页码
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 总页数大于7，显示当前页前后各2页
    let start = Math.max(2, current - 2)
    let end = Math.min(total - 1, current + 2)
    
    // 调整范围确保显示5个页码
    if (end - start < 4) {
      if (start === 2) {
        end = Math.min(total - 1, start + 4)
      } else {
        start = Math.max(2, end - 4)
      }
    }
    
    for (let i = start; i <= end; i++) {
      pages.push(i)
    }
  }
  
  return pages
})

// 是否显示第一页
const showFirstPage = computed(() => {
  return totalPages.value > 7 && !visiblePages.value.includes(1)
})

// 是否显示最后一页
const showLastPage = computed(() => {
  return totalPages.value > 7 && !visiblePages.value.includes(totalPages.value)
})

// 是否显示开始省略号
const showStartEllipsis = computed(() => {
  return showFirstPage.value && visiblePages.value[0] > 2
})

// 是否显示结束省略号
const showEndEllipsis = computed(() => {
  return showLastPage.value && visiblePages.value[visiblePages.value.length - 1] < totalPages.value - 1
})

// 分页方法
const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    jumpPage.value = page
  }
}

const jumpToPage = () => {
  if (jumpPage.value >= 1 && jumpPage.value <= totalPages.value) {
    currentPage.value = jumpPage.value
  } else {
    jumpPage.value = currentPage.value
  }
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString('zh-CN')
}

/** 表格内紧凑时间显示 */
const formatTableDate = (dateString) => {
  if (!dateString) return '-'
  const d = new Date(dateString)
  if (!Number.isFinite(d.getTime())) return '-'
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const getCardTypeText = (cardType) => {
  const typeMap = {
    time: '时间卡密',
    count: '次数卡密'
  }
  return typeMap[cardType] || cardType
}

const getStatusText = (status) => {
  const statusMap = {
    0: '未使用',
    1: '已使用',
    2: '已暂停',
    4: '已合并(续期)'
  }
  return statusMap[status] || status
}

const getStatusClass = (status) => {
  const statusClassMap = {
    0: 'unused',
    1: 'used',
    2: 'disabled',
    4: 'used'
  }
  return statusClassMap[status] || 'unknown'
}

const formatDurationSpec = (key) => {
  if (isPermanentDurationUnit(key?.duration_unit)) return '永久'
  const amount = key?.duration ?? 0
  const unit = key?.duration_unit === 'hours' ? 'hours' : 'days'
  return unit === 'hours' ? `${amount}小时` : `${amount}天`
}

const createKeys = () => {
  const keyData = { ...newKey }
  if (keyData.card_type === 'time') {
    keyData.total_count = 0
    if (isPermanentDurationUnit(keyData.duration_unit)) {
      keyData.duration = 0
      keyData.stack_time_if_same_machine = false
    }
  }
  if (keyData.use_encrypted) {
    keyData.encryption_type = 'advanced'
  } else {
    keyData.encryption_type = 'simple'
    if (keyData.manual_mode) {
      const lines = (keyData.manual_keys_text || '')
        .split(/\r?\n/)
        .map((s) => s.trim())
        .filter(Boolean)
      if (lines.length !== Number(keyData.count)) {
        ElMessage.warning(`手动卡密须 ${keyData.count} 行，当前 ${lines.length} 行`)
        return
      }
      keyData.manual_card_keys = lines
    } else {
      keyData.key_prefix = (keyData.key_prefix || '').trim()
    }
  }

  emit('create-keys', keyData)
  showCreateKeyModal.value = false
  newKey.card_type = 'time'
  newKey.count = 1
  newKey.duration = 30
  newKey.duration_unit = 'days'
  newKey.total_count = 100
  newKey.verify_method = 'web'
  newKey.encryption_type = 'advanced'
  newKey.use_encrypted = true
  newKey.key_length = 16
  newKey.key_prefix = ''
  newKey.manual_mode = false
  newKey.manual_keys_text = ''
  newKey.allow_reverify = 1
  newKey.stack_time_if_same_machine = false
  newKey.allow_self_unbind = false
  newKey.require_device_unbind = false
  newKey.unbind_cooldown_hours = 0
  newKey.unbind_max_count = 0
}

const editKey = (key) => {
  Object.assign(editingKey, {
    id: key.id,
    card_key: key.card_key,
    card_type: key.card_type,
    duration: key.duration,
    duration_unit: key.duration_unit === 'hours'
      ? 'hours'
      : key.duration_unit === 'permanent'
        ? 'permanent'
        : 'days',
    total_count: key.total_count || 100,
    remaining_count: key.remaining_count || key.total_count || 100,
    status: key.status,
    verify_method: key.verify_method || 'web',
    encryption_type: key.encryption_type || 'advanced',
    allow_reverify: key.allow_reverify !== undefined ? key.allow_reverify : 1,
    machine_code: key.machine_code || '',
    allow_self_unbind: key.allow_self_unbind === true || key.allow_self_unbind === 1,
    require_device_unbind: key.require_device_unbind === true || key.require_device_unbind === 1,
    unbind_cooldown_hours: key.unbind_cooldown_hours != null ? Number(key.unbind_cooldown_hours) : 0,
    unbind_max_count: key.unbind_max_count != null ? Number(key.unbind_max_count) : 0,
    unbind_count: key.unbind_count != null ? Number(key.unbind_count) : 0,
    last_unbind_time: key.last_unbind_time || '',
    storage_type: key.storage_type || 'encrypted',
    adjust_direction: 'add',
    adjust_unit: key.card_type === 'count' ? 'times' : 'days',
    adjust_amount: 0
  })
  showEditKeyModal.value = true
}

const updateKey = () => {
  const payload = { ...editingKey }
  if (payload.card_type === 'time' && isPermanentDurationUnit(payload.duration_unit)) {
    payload.duration = 0
  }
  if (!payload.adjust_amount || Number(payload.adjust_amount) <= 0) {
    delete payload.adjust_amount
    delete payload.adjust_unit
    delete payload.adjust_direction
  }
  emit('update-key', payload)
  showEditKeyModal.value = false
}

const toggleKeyStatus = (key, newStatus) => {
  emit('toggle-key-status', {
    id: key.id,
    status: newStatus,
    storage_type: key.storage_type || 'encrypted'
  })
}

const deleteKey = (key) => {
  if (confirm('确定要删除这个卡密吗？此操作不可恢复！')) {
    emit('delete-key', { id: key.id, storage_type: key.storage_type || 'encrypted' })
  }
}

const copyKey = async (cardKey) => {
  const success = await copyToClipboard(cardKey)
  if (success) {
    ElMessage.success('卡密已复制到剪贴板')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.keys-manage-page {
  padding: 0;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  background: #fafbfc;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0;
  padding: 0.85rem 1rem;
  background: white;
  border-bottom: 1px solid #e1e5e9;
}

.keys-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem 1rem;
  padding: 0.65rem 1rem 0.75rem;
  background: white;
  border-bottom: 1px solid #e1e5e9;
}

.toolbar-search {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  flex: 1;
  min-width: 220px;
}

.toolbar-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: #4a5568;
  white-space: nowrap;
}

.toolbar-select {
  padding: 0.5rem 0.65rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.875rem;
  color: #2d3748;
  background: #fff;
  min-width: 5.5rem;
}

.toolbar-select:focus {
  outline: none;
  border-color: #3182ce;
  box-shadow: 0 0 0 3px rgba(49, 130, 206, 0.15);
}

.toolbar-input {
  flex: 1;
  min-width: 200px;
  max-width: 480px;
  padding: 0.5rem 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.875rem;
}

.toolbar-input:focus {
  outline: none;
  border-color: #3182ce;
  box-shadow: 0 0 0 3px rgba(49, 130, 206, 0.15);
}

.toolbar-clear {
  background: none;
  border: none;
  color: #718096;
  font-size: 0.8125rem;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
}

.toolbar-clear:hover {
  color: #2d3748;
  text-decoration: underline;
}

.toolbar-meta {
  margin: 0;
  font-size: 0.8125rem;
  color: #718096;
}

.toolbar-bulk {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem 0.65rem;
  background: white;
  border-bottom: 1px solid #e1e5e9;
}

.bulk-delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.bulk-delete-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-toolbar {
  padding: 0.4rem 0.75rem;
  font-size: 0.8125rem;
}

.bulk-hint {
  flex: 1;
  min-width: 220px;
  margin: 0;
  font-size: 0.75rem;
  color: #a0aec0;
  line-height: 1.45;
}

.col-checkbox {
  width: 2.75rem;
  text-align: center;
  vertical-align: middle;
}

.toolbar-divider {
  margin: 0 0.35rem;
  opacity: 0.5;
}

.header-actions {
  display: flex;
  gap: 1rem;
}

.import-preview {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 0.75rem;
  font-size: 0.8rem;
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.inline-fields {
  display: flex;
  gap: 0.5rem;
}

.inline-fields input,
.inline-fields select {
  flex: 1;
}

.import-modal {
  max-width: 520px;
}

.import-template-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  margin: -0.25rem 0 1rem;
}

.import-template-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.65rem;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.import-template-btn:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}

.import-template-note {
  font-size: 0.75rem;
  color: #6b7280;
}

.import-defaults-hint {
  margin: -0.5rem 0 0.75rem;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  padding: 0.5rem 0.65rem;
}

.export-modal {
  max-width: 920px;
  width: 92%;
  min-height: 32rem;
  max-height: calc(100vh - 1.5rem);
  display: flex;
  flex-direction: column;
}

.export-modal .modal-header {
  padding: 0.85rem 1.25rem 0;
  flex-shrink: 0;
}

.export-modal .modal-actions {
  flex-shrink: 0;
  padding: 0.75rem 1.25rem 1rem;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.export-modal-body {
  padding: 1rem 1.25rem;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.export-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 0;
  flex: 1;
  min-height: 26rem;
  max-height: min(calc(100vh - 10rem), 36rem);
  align-items: stretch;
}

.export-layout-left {
  overflow-x: hidden;
  overflow-y: auto;
  min-height: 0;
  max-height: 100%;
  padding-right: 1rem;
  padding-bottom: 0.5rem;
  border-right: 1px solid #e2e8f0;
}

.export-layout-right {
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-height: 100%;
  padding-left: 1rem;
  overflow: hidden;
}

.setting-group {
  margin-bottom: 0.85rem;
}

.setting-group h4 {
  margin: 0 0 0.45rem 0;
  color: #2d3748;
  font-size: 0.875rem;
  font-weight: 600;
}

.checkbox-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.35rem 0.45rem;
  padding-bottom: 0.25rem;
}

.checkbox-label, .radio-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  color: #4a5568;
  font-size: 0.875rem;
}

.radio-group {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.radio-group.horizontal {
  flex-direction: row;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 0.75rem 1.25rem;
}

.export-scope-hint {
  margin: 0 0 0.45rem 0;
  font-size: 0.75rem;
  color: #718096;
  line-height: 1.4;
}

.export-date-range {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.export-date-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #374151;
}

.export-date-field input[type='date'] {
  padding: 0.4rem 0.55rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
}

.setting-group > .export-column-hint {
  margin: 0 0 0.4rem 0;
  font-size: 0.75rem;
  color: #718096;
  line-height: 1.4;
}

.preview-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.preview-section h4 {
  margin: 0;
  color: #2d3748;
  font-size: 0.875rem;
  font-weight: 600;
}

.preview-meta {
  margin: 0.2rem 0 0.5rem;
  font-size: 0.75rem;
  color: #718096;
}

.preview-table-container {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fafbfc;
}

.preview-empty {
  padding: 2rem 1rem;
  text-align: center;
  color: #94a3b8;
  font-size: 0.8125rem;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.75rem;
}

.preview-table th,
.preview-table td {
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-table th {
  background: #f7fafc;
  font-weight: 600;
  color: #4a5568;
  position: sticky;
  top: 0;
  z-index: 1;
}

.radio-group {
  gap: 0.45rem;
}

.radio-label {
  font-size: 0.8125rem;
}

@media (max-width: 768px) {
  .export-modal {
    min-height: auto;
    max-height: calc(100vh - 1rem);
  }

  .export-layout {
    grid-template-columns: 1fr;
    min-height: auto;
    max-height: none;
  }

  .export-layout-left {
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
    padding-right: 0;
    padding-bottom: 0.75rem;
    max-height: 45vh;
  }

  .export-layout-right {
    padding-left: 0;
    padding-top: 0.75rem;
    min-height: 12rem;
    max-height: 35vh;
  }

  .checkbox-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.section-header h2 {
  color: #2d3748;
  margin: 0;
  font-size: 1.75rem;
  font-weight: 600;
}

.btn-primary,
.btn-secondary,
.btn-danger,
.btn-success,
.btn-warning {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  transition: all 0.2s ease;
  font-size: 0.875rem;
  font-weight: 500;
  text-decoration: none;
}

.btn-sm {
  padding: 0.375rem 0.5rem;
  font-size: 0.75rem;
  border-radius: 4px;
  min-width: auto;
}

.btn-primary {
  background: #4f46e5;
  color: white;
}

.btn-primary:hover {
  background: #4338ca;
  transform: translateY(-1px);
}

.btn-secondary {
  background: #6b7280;
  color: white;
}

.btn-secondary:hover {
  background: #4b5563;
}

.btn-danger {
  background: #ef4444;
  color: white;
}

.btn-danger:hover {
  background: #dc2626;
}

.btn-success {
  background: #10b981;
  color: white;
}

.btn-success:hover {
  background: #059669;
}

.btn-warning {
  background: #f59e0b;
  color: white;
}

.btn-warning:hover {
  background: #d97706;
}

.keys-table {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  margin: 0.75rem 1rem 1.25rem;
  border: 1px solid #e1e5e9;
}

.keys-table table {
  width: 100%;
  min-width: 920px;
  border-collapse: collapse;
  table-layout: fixed;
}

.empty-table-hint {
  text-align: center;
  padding: 2.5rem 1rem !important;
  color: #718096;
  font-size: 0.9rem;
}

.keys-table th:nth-child(1) { width: 2.25rem; }
.keys-table th:nth-child(2) { width: 2.5rem; }
.keys-table th:nth-child(3) { width: 11%; }
.keys-table th:nth-child(4) { width: 3.25rem; }
.keys-table th:nth-child(5) { width: 4.5rem; }
.keys-table th:nth-child(6) { width: 4rem; }
.keys-table th:nth-child(7) { width: 6.5rem; }
.keys-table th:nth-child(8) { width: 6.5rem; }
.keys-table th:nth-child(9) { width: 9.5rem; }
.keys-table th:nth-child(10) { width: 8.5rem; }
.keys-table th:nth-child(11),
.keys-table td.col-actions { width: 11.5rem; }

.keys-table th,
.keys-table td {
  padding: 0.3rem 0.45rem;
  text-align: left;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
  font-size: 0.8125rem;
  line-height: 1.35;
}

.keys-table th {
  background: #f8fafc;
  font-weight: 600;
  color: #475569;
  font-size: 0.75rem;
  white-space: nowrap;
}

.keys-table td.col-id {
  color: #64748b;
  font-size: 0.75rem;
}

.keys-table td.col-time {
  font-size: 0.75rem;
  color: #64748b;
  white-space: nowrap;
}

.keys-table td.col-actions {
  position: sticky;
  right: 0;
  z-index: 2;
  background: #fff;
  box-shadow: -4px 0 6px -4px rgba(15, 23, 42, 0.12);
  text-align: left;
}

.keys-table th.col-actions {
  position: sticky;
  right: 0;
  z-index: 3;
  background: #f8fafc;
  box-shadow: -4px 0 6px -4px rgba(15, 23, 42, 0.08);
  text-align: left;
}

.keys-table tbody tr:hover td.col-actions {
  background: #f8fafc;
}

.keys-table tbody tr {
  transition: background-color 0.2s ease;
}

.keys-table tbody tr:hover {
  background: #f8fafc;
}

.key-code-cell {
  max-width: 9rem;
  overflow: hidden;
  cursor: pointer;
}

.key-code {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  background: #f1f5f9;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
  font-size: 0.6875rem;
  font-weight: 500;
  color: #475569;
  display: block;
  max-width: 9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.35;
}

.key-code-cell:hover .key-code {
  background: #e2e8f0;
  color: #2563eb;
}

.machine-code-cell {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.machine-code-tag {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 0.75rem;
  background: #f0fdf4;
  color: #15803d;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  border: 1px solid #bbf7d0;
}

.machine-code-empty {
  font-size: 0.75rem;
  color: #a1a1aa;
}

.ip-cell {
  max-width: 6.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ip-tag {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 0.75rem;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  border: 1px solid #bfdbfe;
}

.ip-empty {
  font-size: 0.75rem;
  color: #a1a1aa;
}

.machine-code-edit {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.machine-code-edit .readonly-input {
  flex: 1;
}

.machine-code-hint {
  font-size: 0.8rem;
  color: #a1a1aa;
  white-space: nowrap;
}

.form-hint {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: #6b7280;
}

/* 生成卡密：同机时长叠加 — 卡片 + iOS 风格开关 */
.stack-time-stack-group {
  margin-bottom: 1.5rem;
}

.stack-option-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 1rem 1.125rem;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.stack-option-card--active {
  border-color: #c7d2fe;
  background: linear-gradient(165deg, #f8faff 0%, #ffffff 55%);
  box-shadow:
    0 0 0 1px rgba(79, 70, 229, 0.07),
    0 6px 20px rgba(79, 70, 229, 0.08);
}

.stack-toggle-row {
  display: flex !important;
  align-items: flex-start;
  gap: 0.875rem;
  margin: 0 !important;
  cursor: pointer;
  font-weight: normal !important;
  color: inherit !important;
}

.stack-switch {
  position: relative;
  width: 48px;
  height: 28px;
  flex-shrink: 0;
  margin-top: 2px;
}

.stack-switch-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  z-index: 2;
  cursor: pointer;
}

.stack-switch-track {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #d1d5db;
  transition: background 0.22s ease;
}

.stack-switch-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.18);
  transition: transform 0.22s cubic-bezier(0.34, 1.1, 0.64, 1);
  pointer-events: none;
}

.stack-switch-input:checked + .stack-switch-track {
  background: #4f46e5;
}

.stack-switch-input:checked + .stack-switch-track .stack-switch-thumb {
  transform: translateX(20px);
}

.stack-switch-input:focus-visible + .stack-switch-track {
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.28);
}

.stack-toggle-copy {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  min-width: 0;
}

.stack-toggle-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9375rem;
  font-weight: 600;
  color: #111827;
  line-height: 1.35;
}

.stack-toggle-pill {
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.5rem;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #4338ca;
  background: #e0e7ff;
}

.stack-toggle-desc {
  font-size: 0.8125rem;
  line-height: 1.5;
  color: #6b7280;
  font-weight: 400;
}

.duration-cell {
  min-width: 120px;
  font-size: 0.75rem;
  line-height: 1.3;
  white-space: nowrap;
}

.duration-input-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.duration-input-row input {
  flex: 1;
  min-width: 5rem;
  width: auto;
}

.duration-input-row select {
  flex-shrink: 0;
  width: auto;
  min-width: 72px;
}

.time-countdown {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-variant-numeric: tabular-nums;
  color: #0369a1;
  font-weight: 600;
  font-size: 0.75rem;
}

.time-countdown.is-expired {
  color: #b91c1c;
}

.time-spec {
  color: #64748b;
  font-size: 0.75rem;
}

.card-type {
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.6875rem;
  font-weight: 500;
  display: inline-block;
  white-space: nowrap;
}

.card-type.time {
  background: #dbeafe;
  color: #1e40af;
}

.card-type.count {
  background: #fce7f3;
  color: #be185d;
}

.encrypt-tag {
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.6875rem;
  font-weight: 500;
  display: inline-block;
  white-space: nowrap;
}

.encrypt-tag.encrypt-advanced {
  background: #ede9fe;
  color: #5b21b6;
}

.encrypt-tag.encrypt-simple {
  background: #ecfdf5;
  color: #047857;
}

.status {
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.6875rem;
  font-weight: 500;
  white-space: nowrap;
  display: inline-block;
}

.status.unused {
  background: #dcfce7;
  color: #166534;
}

.status.used {
  background: #e0f2fe;
  color: #0c4a6e;
}

.status.disabled {
  background: #fef2f2;
  color: #991b1b;
}

.status.expired {
  background: #fef2f2;
  color: #991b1b;
}

.action-buttons {
  display: flex;
  gap: 0.2rem;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: nowrap;
}

.action-buttons .btn-sm {
  padding: 0.2rem 0.35rem;
  font-size: 0.6875rem;
  line-height: 1.25;
  white-space: nowrap;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
  box-sizing: border-box;
}

.modal-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  max-height: calc(100vh - 2rem);
  display: flex;
  flex-direction: column;
}

.create-key-modal {
  max-width: 640px;
}

.create-key-modal.create-key-modal--simple {
  max-width: 720px;
}

.create-key-modal-body {
  overflow-y: auto;
  padding: 1rem 1.25rem;
  flex: 1;
  min-height: 0;
}

.create-form-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 0.75rem 1rem;
  align-items: start;
}

.create-form-col {
  min-width: 0;
}

.create-form-col--fields .form-group-compact {
  min-width: 0;
}

.create-duration-field .duration-input-row {
  max-width: 220px;
}

.create-form-col--fields .form-row {
  display: grid;
  gap: 0.5rem 0.65rem;
  margin-bottom: 0.5rem;
}

.form-row-2 {
  grid-template-columns: 1fr 1fr;
}

.form-row-3 {
  grid-template-columns: repeat(3, 1fr);
}

.form-group-compact {
  margin-bottom: 0.5rem;
}

.form-group-compact label {
  margin-bottom: 0.25rem;
  font-size: 0.8125rem;
}

.form-group-compact input,
.form-group-compact select,
.form-group-compact textarea {
  padding: 0.45rem 0.55rem;
  font-size: 0.8125rem;
  border-radius: 6px;
}

.manual-keys-textarea {
  width: 100%;
  resize: vertical;
  min-height: 4.5rem;
  box-sizing: border-box;
  border: 1px solid #d1d5db;
  font-family: inherit;
}

.create-form-full {
  margin-top: 0.65rem;
  margin-bottom: 0;
}

.stack-time-stack-group--compact {
  margin-bottom: 0.5rem;
}

.stack-option-card--compact {
  padding: 0.55rem 0.65rem;
  border-radius: 8px;
}

.stack-option-card--compact .stack-toggle-desc {
  font-size: 0.7rem;
  line-height: 1.35;
}

.stack-option-card--compact .stack-toggle-title {
  font-size: 0.8125rem;
}

.stack-switch--sm {
  width: 40px;
  height: 24px;
  margin-top: 1px;
}

.stack-switch--sm .stack-switch-thumb {
  width: 18px;
  height: 18px;
  top: 3px;
  left: 3px;
}

.stack-switch--sm .stack-switch-input:checked + .stack-switch-track .stack-switch-thumb {
  transform: translateX(16px);
}

.create-key-modal .modal-header {
  padding: 0.85rem 1.25rem 0;
  flex-shrink: 0;
}

.create-key-modal .modal-actions {
  flex-shrink: 0;
  padding: 0 1.25rem 1rem;
}

@media (max-width: 640px) {
  .create-form-layout {
    grid-template-columns: 1fr;
  }

  .form-row-3 {
    grid-template-columns: 1fr 1fr;
  }
}

.modal-header {
  padding: 1.5rem 1.5rem 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.modal-header h3 {
  margin: 0;
  color: #2d3748;
  font-size: 1.25rem;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.25rem;
  color: #6b7280;
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.modal-body {
  padding: 1.5rem;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
  font-size: 0.875rem;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 0.875rem;
  transition: border-color 0.2s ease;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
}

.adjust-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.adjust-row select,
.adjust-row input {
  flex: 1;
  min-width: 0;
}

.batch-adjust-scope {
  margin: 0 0 1rem;
  color: #374151;
  font-size: 0.875rem;
  line-height: 1.5;
}

.batch-adjust-modal {
  max-width: 420px;
}

.readonly-input {
  background: #f9fafb !important;
  color: #6b7280 !important;
  cursor: not-allowed !important;
}

.modal-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  padding: 0 1.5rem 1.5rem;
  flex-shrink: 0;
}

/* 分页样式 */
.pagination-container {
  padding: 1.5rem 2rem;
  background: white;
  border-top: 1px solid #e1e5e9;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.pagination-btn {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  background: white;
  color: #374151;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.pagination-btn:hover:not(:disabled) {
  background: #f3f4f6;
  border-color: #9ca3af;
}

.pagination-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #f9fafb;
}

.page-numbers {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.page-btn {
  width: 2.5rem;
  height: 2.5rem;
  border: 1px solid #d1d5db;
  background: white;
  color: #374151;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}

.page-btn.active {
  background: #4f46e5;
  border-color: #4f46e5;
  color: white;
}

.ellipsis {
  padding: 0 0.5rem;
  color: #6b7280;
  font-size: 0.875rem;
}

.page-jump {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #374151;
}

.jump-input {
  width: 4rem;
  padding: 0.375rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.875rem;
  text-align: center;
}

.jump-input:focus {
  outline: none;
  border-color: #4f46e5;
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.1);
}

.jump-btn {
  padding: 0.375rem 0.75rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: background-color 0.2s ease;
}

.jump-btn:hover {
  background: #4338ca;
}

.pagination-info {
  font-size: 0.875rem;
  color: #6b7280;
  white-space: nowrap;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .keys-table {
    margin: 0 1rem 2rem;
  }
  
  .section-header {
    padding: 1.5rem 1rem;
  }

  .pagination-container {
    padding: 1.5rem 1rem;
  }
}

@media (max-width: 768px) {
  .section-header {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
    padding: 1rem;
  }

  .section-header h2 {
    font-size: 1.5rem;
    text-align: center;
  }

  .keys-table {
    font-size: 0.875rem;
    margin: 0 0.5rem 1rem;
    border-radius: 8px;
  }

  .keys-table th,
  .keys-table td {
    padding: 0.35rem 0.4rem;
  }

  .action-buttons {
    flex-wrap: nowrap;
    justify-content: flex-start;
  }

  .modal-content {
    margin: 1rem;
    width: calc(100% - 2rem);
    border-radius: 8px;
  }

  .modal-header,
  .modal-body,
  .modal-actions {
    padding: 1rem;
  }

  .btn-primary,
  .btn-secondary,
  .btn-danger {
    padding: 0.75rem 1rem;
    font-size: 0.875rem;
  }

  .pagination-container {
    padding: 1rem 0.5rem;
  }

  .pagination {
    flex-direction: column;
    gap: 0.75rem;
  }

  .page-numbers {
    order: 1;
  }

  .pagination-btn {
    order: 2;
    padding: 0.5rem 0.75rem;
    font-size: 0.8rem;
  }

  .page-jump {
    order: 3;
    font-size: 0.8rem;
  }

  .pagination-info {
    order: 4;
    text-align: center;
    font-size: 0.8rem;
  }
}

@media (max-width: 480px) {
  .keys-manage-page {
    border-radius: 0;
  }
  
  .section-header {
    border-radius: 0;
  }
  
  .keys-table {
    border-radius: 0;
    margin: 0 0 1rem;
  }
  
  .modal-content {
    border-radius: 0;
    margin: 0;
    width: 100%;
    height: 100%;
  }

  .pagination-container {
    padding: 0.75rem 0.25rem;
    border-radius: 0;
  }

  .page-btn {
    width: 2rem;
    height: 2rem;
    font-size: 0.75rem;
  }

  .jump-input {
    width: 3rem;
  }
}
</style>