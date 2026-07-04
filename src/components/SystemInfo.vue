<template>
  <div class="system-info-container">
    <div class="page-header">
      <h2>系统信息</h2>
      <p class="subtitle">查看系统版本、作者信息及开源协议</p>
    </div>

    <div class="info-content">
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="系统名称">XXG-KAMI-PRO 卡密验证系统</el-descriptions-item>
          <el-descriptions-item label="当前版本">
            <div class="version-row">
              <span>{{ currentVersion }}</span>
              <el-button type="primary" link size="small" @click="checkUpdate" :loading="checking">检查更新</el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="isDesktop" label="桌面版版本">
            <div class="version-row">
              <span>v{{ desktopVersion }}</span>
              <span v-if="bundledAppVersion" class="version-sub">内置业务 v{{ bundledAppVersion }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">2026-07-04</el-descriptions-item>
          <el-descriptions-item label="公司">小小怪</el-descriptions-item>
          <el-descriptions-item label="开发语言">Vue 3 + Spring Boot 3</el-descriptions-item>
          <el-descriptions-item label="官方网站">
            <a href="https://www.xxgkami.com/" target="_blank" class="link">https://www.xxgkami.com/</a>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>开发团队</span>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="作者">小小怪</el-descriptions-item>
          <el-descriptions-item label="联系邮箱">xxgyyds@vip.qq.com</el-descriptions-item>
          <el-descriptions-item label="QQ群组">
            <a href="https://qm.qq.com/cgi-bin/qm/qr?k=5q7h3tdOC-fXyszk3kGCJxIImDW_hVBP&jump_from=webapi&authKey=n7o2H5vcTCkRNpnTbOSU9BxI4jP3WKv9Qytmfk2I2Y+zP28lb614xqvd3+qETV8x" target="_blank" class="link">1050160397 (点击加入)</a>
          </el-descriptions-item>
          <el-descriptions-item label="Gitee">
            <a href="https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro" target="_blank" class="link">https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro</a>
          </el-descriptions-item>
          <el-descriptions-item label="GitHub">
            <a href="https://github.com/xxg-yyds/xxgkami-pro" target="_blank" class="link">https://github.com/xxg-yyds/xxgkami-pro</a>
          </el-descriptions-item>
          <el-descriptions-item label="AtomGit">
            <a href="https://atomgit.com/xiaoxiaoguai-yyds/xxgkami-pro" target="_blank" class="link">https://atomgit.com/xiaoxiaoguai-yyds/xxgkami-pro</a>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>开源协议</span>
          </div>
        </template>
        <div class="license-content">
          <p>本系统遵循 Apache-2.0 开源协议。</p>
          <p>您可以免费使用、修改和分发本软件，但必须保留原作者的版权声明。</p>
        </div>
      </el-card>

      <!-- 赞助模块 -->
      <el-card class="info-card sponsor-card">
        <template #header>
          <div class="card-header">
            <span>赞助开源</span>
          </div>
        </template>
        <div class="sponsor-content">
          <img src="../assets/aifadian.svg" alt="爱发电" class="sponsor-logo">
          <p>如果觉得本项目对您有帮助，欢迎赞助支持作者持续开发！</p>
          <a href="https://ifdian.net/a/xxgyyds" target="_blank" class="sponsor-btn">
            <el-button type="primary" size="large" color="#946ce6" :dark="false">
              前往爱发电赞助
            </el-button>
          </a>
        </div>
      </el-card>

      <el-card class="info-card prank-card">
        <template #header>
          <div class="card-header">
            <span>千万不要点</span>
          </div>
        </template>
        <div class="prank-content">
          <button type="button" class="prank-btn" @click="onPrankClick">
            千万不要点
          </button>
        </div>
      </el-card>

      <el-card class="info-card supporters-card">
        <template #header>
          <div class="card-header supporters-card-header">
            <span>共创使用者</span>
            <a
              :href="BUG_REPORT_FORM_URL"
              target="_blank"
              rel="noopener noreferrer"
              class="bug-report-btn"
            >
              提交 Bug
            </a>
          </div>
        </template>
        <div class="supporters-content">
          <p class="supporters-desc">
            感谢以下提交有效反馈的共创使用者（提交者自动收录，每人仅展示一次，按贡献次数排序）。
            发现 Bug 可点击右上角「提交 Bug」填写
            <a :href="BUG_REPORT_FORM_URL" target="_blank" rel="noopener noreferrer" class="supporters-form-link">小小怪卡密系统 Bug 收集表</a>。
          </p>
          <ol class="supporters-list">
            <li v-for="(item, index) in topSupporters" :key="item.name" class="supporter-item">
              <span class="supporter-rank">{{ index + 1 }}</span>
              <span class="supporter-name">{{ item.name }}</span>
              <span class="supporter-count">{{ item.count }} 次</span>
            </li>
          </ol>
          <div class="supporters-actions">
            <a
              :href="BUG_REPORT_FORM_URL"
              target="_blank"
              rel="noopener noreferrer"
              class="bug-report-btn bug-report-btn-block"
            >
              提交 Bug 反馈
            </a>
            <button
              v-if="feedbackSupporters.length > 3"
              type="button"
              class="supporters-more-btn"
              @click="showSupportersDialog = true"
            >
              查看完整名单（共 {{ feedbackSupporters.length }} 人）
            </button>
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog
      v-model="showSupportersDialog"
      class="supporters-dialog"
      title="共创使用者完整名单"
      width="520px"
      align-center
      destroy-on-close
    >
      <p class="supporters-dialog-desc">按贡献次数排序，每人仅展示一次</p>
      <ol class="supporters-list supporters-list-dialog">
        <li v-for="(item, index) in feedbackSupporters" :key="item.name" class="supporter-item">
          <span class="supporter-rank">{{ index + 1 }}</span>
          <span class="supporter-name">{{ item.name }}</span>
          <span class="supporter-count">{{ item.count }} 次</span>
        </li>
      </ol>
    </el-dialog>

    <!-- 更新时间轴 -->
    <div class="timeline-section">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>版本历程</span>
          </div>
        </template>
        <el-timeline>
          <el-timeline-item timestamp="2026-07-04" placement="top" type="primary" size="large">
            <el-card>
              <h4>v1.0.8 版本更新</h4>
              <p>1. 新增自助解绑「原设备解绑」：开启后解绑须传入与原绑定一致的设备码；支持解绑冷却间隔与解绑次数上限（每张卡可单独配置）</p>
              <p>2. 新增多管理员与权限管理：超级管理员可添加子管理员并分配功能权限；导航与页面按权限显隐</p>
              <p>3. 新增操作日志：记录登录、卡密操作、管理员变更等，管理后台可分页查询</p>
              <p>4. 新增卡密 Excel 导入：支持下载示例模板，可指定类型、时长或次数批量导入</p>
              <p>5. 时间卡密新增「永久卡密」选项，列表与详情显示为「永久」</p>
              <p>6. 美化添加管理员页面 UI，优化权限选择交互样式</p>
              <p>7. 新增「API 开放中心」独立页面：接口文档从 API 管理页迁出，各板块独立分页展示（非锚点翻页）</p>
              <p>8. API 开放中心文档中文化；新增 activate（开通授权）、unbind_device（解绑设备）、create_cards、create_api_key、health/ping 等开放平台接口文档与多语言示例</p>
              <p>9. 新增开放平台 Token：API 开放中心可创建 Token（明文仅展示一次、可复制）；服务端仅存 SHA-256 哈希；每人最多 5 个，删除后立即失效</p>
              <p>10. create_cards、create_api_key 接口改为须携带开放平台 Token（Authorization / X-Open-Token / open_token 参数）</p>
              <p>11. 概览页顶部新增服务器物理位置：自动探测公网/内网 IP，支持国内（太平洋 IP 库）与国外（ipwho.is）双源查询；各区域独立异步加载互不阻塞</p>
              <p>12. 系统信息页新增「共创使用者」名单：按 Bug 反馈贡献次数排序，默认展示前三，点击可查看完整弹窗名单</p>
              <p>13. 新增 Electron 桌面版：支持 build:electron:safe 打包为 Windows NSIS 安装程序（XXG-KAMI-PRO-Setup-x.x.x.exe）</p>
              <p>14. Electron 内置前端 dist 与后端 JAR（resources/runtime），无需从 Git 拉取即可离线运行</p>
              <p>15. Electron 启动流程：首次不自动启动服务；先检测 Java 20+ 与 MySQL 8.0+，通过后点击「下一步」再启动前后端</p>
              <p>16. Electron 环境页新增 MySQL 连接配置与在线检测（密码失焦自动测试），配置持久化到用户数据目录</p>
              <p>17. Electron 启动进度弹窗：分步展示 HTML 前端加载、Java 后端启动、API 健康检查，附带日志与命令输出</p>
              <p>18. Electron 8080 端口冲突检测：显示占用进程 PID，支持「一键清除并重新启动」；修复终端与程序重复启动导致的端口占用</p>
              <p>19. Electron 前端改为 HTTP 静态服务（127.0.0.1:5174），便于云桌面端口映射与公网访问</p>
              <p>20. Electron 前端在独立 CMD 终端启动（标题 XXG-KAMI Frontend），与后端终端并列显示日志</p>
              <p>21. Electron 安装包使用自定义 icon.ico；build:electron:safe 输出至 xxgkami-electron-release 目录</p>
              <p>22. Electron 首次配置完成后写入标记文件，再次启动跳过环境检测页并静默拉起后端</p>
              <p>23. 新增开放平台全局 AES-256-CBC 加解密：入参/出参独立开关，可单独启用或组合使用</p>
              <p>24. 入参加密：业务参数 JSON 加密后放入 encrypted_payload；出参加密：响应包装为 response_encrypted + encrypted_payload</p>
              <p>25. API 开放中心新增「参数加密」配置：展示 Key/IV/算法规范，美化双开关 UI，并提供 JS/Java/Python 加解密对接文档</p>
              <p>26. 修复 Electron 桌面版「检查更新」Failed to fetch；新增 electron/npm 国内镜像加速打包</p>
              <p>27. 桌面版启动顺序优化：后端就绪后再确认前端 HTTP；8080 端口检测改用 netstat/tasklist/taskkill</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-06-04" placement="top" size="large">
            <el-card>
              <h4>v1.0.7 版本更新</h4>
              <p>1. 新增在线更新：系统信息页可一键更新；自动探测后端 JAR 与前端 dist 路径，支持手动修改确认后下载 Release 制品并替换，完成后自动重启后端</p>
              <p>2. version.json 新增 releaseDownloads（Gitee/GitHub 各含 dist.zip 与 backend JAR 下载地址，支持 {version} 占位符）</p>
              <p>3. 检查更新优化：并行 ping gitee.com 与 github.com，优先使用先连通的通道拉取 version.json，并提示所用通道</p>
              <p>4. 首次安装向导完善：检测 kami 库是否存在 admins 表；无 admins 视为未初始化业务库，自动执行全新导入（跳过「覆盖/智能更新」策略页）</p>
              <p>5. 首次安装向导：选 MySQL 5.6 时由 kami.sql 自动转译为 5.6 兼容脚本并显示转译进度，无需单独维护 kami_mysql56.sql</p>
              <p>6. 种子 SQL 内嵌 JAR：打包时纳入 databaes/kami.sql，部署仅上传 JAR 亦可安装；支持解压到 data/.xxgkami-seed 及自定义 seed-sql-override</p>
              <p>7. 安装/升级标记：以 data/.xxgkami-setup.complete 与 admins 表共同判定业务库就绪；未完成前跳过业务 JDBC 探测与 ApiKey 等初始化，避免空库报错</p>
              <p>8. Spring Session：使用 spring_session 表名，启动时初始化会话表；修复 SPRING_SESSION 相关启动与清理错误</p>
              <p>9. 新版升级检测：业务库就绪后按 data/.xxgkami-version.json 与远程版本比对，支持智能合并更新与版本升级向导</p>
              <p>10. 引导页与加载页 UI：logo 蒙版渐隐背景；系统初始化向导支持版本升级模式与 5.6 SQL 转译进度展示</p>
              <p>11. 卡密管理列表：缩短卡密列显示宽度；新增 IP 列（有 ip_address 显示 IP，否则显示「未绑定」）</p>
              <p>12. 修复 MySQL 5.6 转译时 replaceAll「${1}」触发 Java 命名捕获组告警，改为安全替换逻辑</p>
              <p>13. 修复宝塔等仅部署 JAR 环境提示「未找到 databaes/kami.sql」导致安装卡在 0% 的问题</p>
              <p>14. 修复首次启动 Spring Session 等自动建表导致向导误报「库已存在需覆盖/合并」的问题</p>
              <p>15. 远程 version.json Gitee 源地址调整为 xiaoxiaoguai-yyds/xxgkami-pro，与 Release 下载仓库一致</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-05-06" placement="top" size="large">
            <el-card>
              <h4>v1.0.6 版本更新</h4>
              <p>1. 新增 API 密钥管理页「代码实例」入口（位于「接口文档」左侧），便于快速查阅核销接口调用方式</p>
              <p>2. 新增核销接口多语言示例弹窗：覆盖 Shell（cURL/Wget/HTTPie 等）、PowerShell、Node.js、浏览器/JavaScript、HTML、Vue 3、Python、PHP、Go、Java、C#、Ruby、Kotlin、Dart、Rust、Swift 等常见语言与工具</p>
              <p>3. 示例数据独立维护于 apiUseCardCodeExamples.js，弹窗内支持左侧切换语言、一键复制源码</p>
              <p>4. 集成 highlight.js（GitHub Dark 主题），各语言代码块支持语法高亮；展示当前环境 API 根路径参考，便于替换示例中的 BASE_URL</p>
              <p>5. 卡密管理：支持按设备码/机器码查找对应卡密；导出支持筛选「未使用」「已使用」</p>
              <p>6. 时间卡规则：新增「叠加 / 单张」模式——单张为现行逻辑（以激活时刻起算到期）；叠加模式下同一设备续充时间卡时，将新卡时长累加到当前生效授权的到期时间（原到期时间 + 新卡含时），实现无缝续期；参与叠加的卡密标记为已使用（叠加）或已合并；不同面额仅叠加时长数值、不改写套餐类型；修正原同机多张时间卡「后者覆盖前者剩余时长、每次均以当前时间重新起算」及多记录并行不合理倒计时等问题</p>
              <p>7. 一机一码增强：卡密在首台设备绑定且为已使用后，其它机器码无法再通过该卡核销，补全跨机校验与防重复滥用</p>
              <p>8. API 密钥能力：支持按密钥配置「同一机器码对指定卡密类型仅可成功核销一次」（例如每台机仅一次 1 天体验卡）</p>
              <p>9. 用户首页：支持用户自行解绑已绑定的机器码（无需仅依赖管理员后台）</p>
              <p>10. 管理后台：顶部导航栏布局与交互重做，提升入口清晰度与操作效率</p>
              <p>11. 修正部分示例片段（如 Go JSON 请求体、C# HttpClient 调用等），保证可对照使用</p>
              <p>12. 修复次数卡剩余最后一次无法正常扣减、次数无法归零的问题</p>
              <p>13. 修复已激活时间卡在后台修改时长不生效的问题</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-04-08" placement="top" size="large">
            <el-card>
              <h4>v1.0.5 版本更新</h4>
              <p>1. 新增一机一码功能：卡密首次核销时自动绑定机器码，后续验证必须匹配</p>
              <p>2. 新增机器码管理：卡密列表展示机器码列，编辑弹窗支持重置机器码</p>
              <p>3. 新增时间卡密实时倒计时：管理端按秒刷新显示剩余时间</p>
              <p>4. 新增卡密启用/暂停功能：已使用卡密被暂停后提示"卡密被停止使用"</p>
              <p>5. 新增重复验证控制：时间卡密支持开启/关闭重复验证，关闭后仅允许验证一次</p>
              <p>6. 新增批量创建进度条：大量创建卡密时逐条创建，实时显示进度</p>
              <p>7. 优化登录页 UI：管理员与用户登录页采用不同主题，管理员页新增返回入口</p>
              <p>8. 优化自定义接口：输入/输出参数新增机器码变量，新增机器码不匹配/重复验证拒绝状态码</p>
              <p>9. 修复高级时间卡密激活后管理端显示"未激活"的问题</p>
              <p>10. 修复新创建卡密立即开始倒计时的问题（改为首次核销后才计时）</p>
              <p>11. 修复卡密导出时间卡到期时间为空的问题</p>
              <p>12. 修复 API 管理无法分配用户的问题</p>
              <p>13. 修复 CORS 不支持 PATCH 方法的问题</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-03-05" placement="top" size="large">
            <el-card>
              <h4>v1.0.4 版本更新</h4>
              <p>1. 修复次数卡密在设置过期时间后被误识别为时间卡密的问题</p>
              <p>2. 优化卡密验证核心逻辑，优先根据总次数判断卡密类型</p>
              <p>3. 修复部分已知后端逻辑问题，提升系统稳定性</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-02-13" placement="top" size="large">
            <el-card>
              <h4>v1.0.3 版本更新</h4>
              <p>1. 全面优化移动端 UI 体验，重构用户管理页面为响应式卡片布局</p>
              <p>2. 修复移动端侧边栏菜单无法展开的问题，优化首页导航栏显示</p>
              <p>3. API 接口自定义返回配置新增“卡密状态”字段支持</p>
              <p>4. 优化管理后台顶部导航栏，移除冗余的消息通知入口</p>
              <p>5. 修复部分已知 UI 溢出和布局错位问题</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-02-09" placement="top" size="large">
            <el-card>
              <h4>v1.0.2 版本更新</h4>
              <p>1. 修复用户注册时因邮件配置缺失导致的 500 错误，优化异常处理逻辑</p>
              <p>2. 升级 xxgkami 命令行工具，新增数据库智能增量更新功能</p>
              <p>3. 优化系统安装脚本，提升部署体验</p>
              <p>4. 修复部分已知的小问题</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-01-30" placement="top" size="large">
            <el-card>
              <h4>v1.0.1 版本更新</h4>
              <p>1. 完善管理员账号密码加密逻辑，数据库存储由明文全面升级为 BCrypt 加密</p>
              <p>2. 新增系统信息页面，支持查看版本信息、开源协议及开发团队</p>
              <p>3. 新增在线检查更新功能，提供国内/海外一键更新脚本</p>
              <p>4. 优化前端导航栏布局，提升用户体验</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-01-17" placement="top">
            <el-card>
              <h4>v1.0.0 正式发布</h4>
              <p>小小怪卡密验证系统 1.0 正式发布</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>

    <!-- 更新提示弹窗 -->
    <el-dialog
      v-model="showUpdateDialog"
      class="update-dialog"
      width="560px"
      align-center
      destroy-on-close
    >
      <template #header>
        <div class="update-dialog-header">
          <div class="update-dialog-logo-wrap">
            <img :src="brandLogo" alt="XXG-KAMI" class="update-dialog-logo" />
          </div>
          <div>
            <h3 class="update-dialog-title">发现新版本</h3>
            <p class="update-dialog-sub">建议尽快更新以获得最新功能与修复</p>
          </div>
        </div>
      </template>
      <div v-if="updateInfo" class="update-dialog-body">
        <div class="version-compare">
          <div class="version-pill version-pill--current">
            <span class="version-pill-label">当前</span>
            <span class="version-pill-value">{{ currentVersion }}</span>
          </div>
          <span class="version-arrow" aria-hidden="true">
            <img :src="brandLogo" alt="" class="version-arrow-logo" />
          </span>
          <div class="version-pill version-pill--new">
            <span class="version-pill-label">最新</span>
            <span class="version-pill-value">v{{ updateInfo.version }}</span>
          </div>
        </div>
        <p v-if="updateInfo.buildDate" class="update-meta">
          <span>发布时间</span> {{ updateInfo.buildDate }}
        </p>

        <div v-if="changelogItems.length" class="changelog-panel">
          <div class="panel-head">
            <span>更新内容</span>
            <span class="panel-count">共 {{ changelogItems.length }} 条</span>
          </div>
          <ul class="changelog-list">
            <li v-for="(item, index) in visibleChangelogItems" :key="'v-' + index">{{ item }}</li>
          </ul>
          <button
            v-if="changelogItems.length > changelogPreviewCount"
            type="button"
            class="changelog-toggle"
            @click="changelogExpanded = !changelogExpanded"
          >
            {{ changelogExpanded ? '收起更新说明' : `展开全部（还有 ${changelogItems.length - changelogPreviewCount} 条）` }}
          </button>
        </div>

        <el-collapse v-if="updateInfo.updateScripts" class="update-collapse">
          <el-collapse-item title="命令行更新脚本（可选）" name="scripts">
            <div class="script-block">
              <div class="script-header">
                <span>国内 · Gitee</span>
                <el-button type="primary" link size="small" @click="copyScript(updateInfo.updateScripts.cn)">复制</el-button>
              </div>
              <div class="script-content">{{ updateInfo.updateScripts.cn }}</div>
            </div>
            <div class="script-block">
              <div class="script-header">
                <span>海外 · GitHub</span>
                <el-button type="primary" link size="small" @click="copyScript(updateInfo.updateScripts.global)">复制</el-button>
              </div>
              <div class="script-content">{{ updateInfo.updateScripts.global }}</div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <template #footer>
        <div class="update-dialog-footer">
          <button type="button" class="update-action-btn update-action-btn--ghost" @click="showUpdateDialog = false">
            稍后
          </button>
          <button type="button" class="update-action-btn update-action-btn--outline" @click="goToRepo">
            <img :src="brandLogo" alt="" class="update-btn-logo" />
            前往仓库
          </button>
          <button
            v-if="hasNewerVersion"
            type="button"
            class="update-action-btn update-action-btn--brand"
            @click="openOnlineUpdateDialog"
          >
            <img :src="brandLogo" alt="" class="update-btn-logo" />
            在线更新
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 在线更新：路径确认 -->
    <el-dialog
      v-model="showOnlineUpdateDialog"
      class="update-dialog update-dialog--online"
      width="640px"
      align-center
      :close-on-click-modal="!updatingOnline"
      destroy-on-close
    >
      <template #header>
        <div class="update-dialog-header">
          <div class="update-dialog-logo-wrap update-dialog-logo-wrap--online">
            <img :src="brandLogo" alt="XXG-KAMI" class="update-dialog-logo" />
          </div>
          <div>
            <h3 class="update-dialog-title">在线更新</h3>
            <p v-if="updateInfo" class="update-dialog-sub">
              将升级至 <strong>v{{ updateInfo.version }}</strong>，请确认路径后执行
            </p>
          </div>
        </div>
      </template>

      <div class="online-update-body">
        <div class="path-detect-bar">
          <span>
            优先扫描 <code>/www/wwwroot</code> 及站点子目录，匹配
            <code>{{ updatePaths.defaultBackendJarName || 'backend-0.0.1-SNAPSHOT.jar' }}</code> /
            <code>{{ updatePaths.defaultFrontendDirName || 'dist' }}</code>
          </span>
          <button
            type="button"
            class="update-link-btn"
            :disabled="detectingPaths || updatingOnline"
            @click="refreshUpdatePaths"
          >
            <img :src="brandLogo" alt="" class="update-link-btn-logo" />
            {{ detectingPaths ? '检测中…' : '重新检测' }}
          </button>
        </div>

        <div class="path-cards">
          <div class="path-card" :class="{ 'is-invalid': updatePaths.jarExists === false }">
            <div class="path-card-head">
              <span class="path-card-title">后端 JAR</span>
              <el-tag size="small" :type="updatePaths.jarExists ? 'success' : 'warning'">
                {{ updatePaths.jarExists ? '已找到' : '未找到文件' }}
              </el-tag>
            </div>
            <p v-if="updatePaths.backendJarMatchRule" class="path-match-hint">{{ updatePaths.backendJarMatchRule }}</p>
            <el-input
              v-model="updatePaths.backendJarPath"
              :disabled="updatingOnline"
              :placeholder="`匹配 ${updatePaths.defaultBackendJarName || 'backend-0.0.1-SNAPSHOT.jar'}`"
            />
          </div>
          <div class="path-card" :class="{ 'is-invalid': !updatePaths.distExists }">
            <div class="path-card-head">
              <span class="path-card-title">前端 dist</span>
              <el-tag size="small" :type="distStatusTagType">
                {{ distStatusLabel }}
              </el-tag>
            </div>
            <p v-if="updatePaths.frontendDistMatchRule" class="path-match-hint" :class="{ 'is-warn': !updatePaths.frontendDistDetected }">
              {{ updatePaths.frontendDistMatchRule }}
            </p>
            <p v-if="!updatePaths.frontendDistDetected && updatePaths.distExists" class="path-match-hint is-ok">
              目录存在且含 index.html，可手动确认后更新
            </p>
            <el-input
              v-model="updatePaths.frontendDistPath"
              :disabled="updatingOnline"
              :placeholder="`匹配目录名 ${updatePaths.defaultFrontendDirName || 'dist'}（含 index.html）`"
            />
          </div>
        </div>

        <div class="path-meta-row">
          <span>运行目录：{{ updatePaths.userDir || '—' }}</span>
          <span v-if="updatePaths.channel">下载通道：{{ updatePaths.channel === 'github' ? 'GitHub' : 'Gitee' }}</span>
        </div>

        <div v-if="updatingOnline || onlineUpdateStatus" class="online-update-progress">
          <el-progress :percentage="onlineUpdateStatus?.percent ?? 0" :status="progressStatus" :stroke-width="10" striped striped-flow />
          <p class="progress-msg">{{ onlineUpdateStatus?.message || '准备中…' }}</p>
          <el-alert
            v-if="onlineUpdateStatus?.status === 'done'"
            class="update-done-baota-hint"
            type="warning"
            :closable="false"
            show-icon
            title="更新已完成"
            :description="onlineUpdateStatus?.baotaHint || BAOTA_RESTART_HINT"
          />
        </div>
      </div>

      <template #footer>
        <div class="update-dialog-footer">
          <button
            type="button"
            class="update-action-btn update-action-btn--ghost"
            :disabled="updatingOnline"
            @click="showOnlineUpdateDialog = false"
          >
            取消
          </button>
          <button
            type="button"
            class="update-action-btn update-action-btn--brand"
            :disabled="!canStartOnlineUpdate || updatingOnline"
            @click="startOnlineUpdate"
          >
            <img :src="brandLogo" alt="" class="update-btn-logo" />
            {{ updatingOnline ? '更新中…' : '确认并开始更新' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { copyToClipboard } from '../utils/clipboard.js'
import { monitorApi } from '../services/api.js'
import { getFeedbackSupporters } from '../data/feedbackSupporters.js'
import { useElectronDesktopUpdate } from '../composables/useElectronDesktopUpdate.js'
import brandLogo from '../assets/icon.png'

const currentVersion = 'v1.0.8'
const isDesktop = ref(!!window.electronAPI?.isDesktop)
const desktopVersion = ref('1.0.0')
const bundledAppVersion = ref('1.0.8')
const { checkForUpdate: checkDesktopUpdate } = useElectronDesktopUpdate()
const BUG_REPORT_FORM_URL = 'https://docs.qq.com/form/page/DRUFmT29vUGd1QnRR'
const feedbackSupporters = getFeedbackSupporters()
const topSupporters = computed(() => feedbackSupporters.slice(0, 3))
const showSupportersDialog = ref(false)

const checking = ref(false)
const showUpdateDialog = ref(false)
const updateInfo = ref(null)
const hasNewerVersion = ref(false)
const showOnlineUpdateDialog = ref(false)
const detectingPaths = ref(false)
const updatingOnline = ref(false)
const changelogPreviewCount = 4
const changelogExpanded = ref(false)
const updatePaths = ref({
  backendJarPath: '',
  frontendDistPath: '',
  userDir: '',
  channel: '',
  jarExists: false,
  distExists: false,
  defaultBackendJarName: 'backend-0.0.1-SNAPSHOT.jar',
  defaultFrontendDirName: 'dist',
  backendJarMatchRule: '',
  frontendDistMatchRule: ''
})
const onlineUpdateStatus = ref(null)

/** 在线更新完成后提示（宝塔权限可能导致无法自动拉起 Java） */
const BAOTA_RESTART_HINT =
  '若刷新后仍无法访问，可能因宝塔权限导致后端未能自动启动，请前往：宝塔 → 网站 → Java 项目，手动启动后端 JAR 文件。'
let statusPollTimer = null

const loadDesktopVersion = async () => {
  if (!window.electronAPI?.getDesktopVersion) return
  try {
    const info = await window.electronAPI.getDesktopVersion()
    if (info?.desktopVersion) desktopVersion.value = info.desktopVersion
    if (info?.bundledAppVersion) bundledAppVersion.value = info.bundledAppVersion
  } catch {
    // ignore
  }
}

const changelogItems = computed(() => {
  const list = updateInfo.value?.changelog
  return Array.isArray(list) ? list : []
})

const distStatusLabel = computed(() => {
  if (updatePaths.value?.frontendDistDetected && updatePaths.value?.distExists) return '已找到'
  if (updatePaths.value?.distExists) return '目录有效'
  if (updatePaths.value?.frontendDistDetected) return '待确认'
  return '未自动匹配'
})

const distStatusTagType = computed(() => {
  if (updatePaths.value?.frontendDistDetected && updatePaths.value?.distExists) return 'success'
  if (updatePaths.value?.distExists) return 'success'
  return 'warning'
})

const visibleChangelogItems = computed(() => {
  if (changelogExpanded.value || changelogItems.value.length <= changelogPreviewCount) {
    return changelogItems.value
  }
  return changelogItems.value.slice(0, changelogPreviewCount)
})

const copyScript = async (text) => {
  const success = await copyToClipboard(text)
  if (success) {
    ElMessage.success('脚本已复制到剪贴板')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const compareVersions = (a, b) => {
  const pa = (a || '0').replace(/^v/i, '').split('.')
  const pb = (b || '0').replace(/^v/i, '').split('.')
  const len = Math.max(pa.length, pb.length)
  for (let i = 0; i < len; i++) {
    const na = parseInt(pa[i] || '0', 10) || 0
    const nb = parseInt(pb[i] || '0', 10) || 0
    if (na !== nb) return na - nb
  }
  return 0
}

const progressStatus = computed(() => {
  const st = onlineUpdateStatus.value?.status
  if (st === 'error') return 'exception'
  if (st === 'done') return 'success'
  return undefined
})

const canStartOnlineUpdate = computed(() => {
  return !!(updatePaths.value.backendJarPath?.trim() && updatePaths.value.frontendDistPath?.trim())
})

const stopStatusPoll = () => {
  if (statusPollTimer) {
    clearInterval(statusPollTimer)
    statusPollTimer = null
  }
}

const pollUpdateStatus = () => {
  stopStatusPoll()
  statusPollTimer = setInterval(async () => {
    try {
      const res = await monitorApi.getOnlineUpdateStatus()
      if (res.success && res.data) {
        onlineUpdateStatus.value = res.data
        if (res.data.status === 'done') {
          stopStatusPoll()
          updatingOnline.value = false
          const hint = res.data.baotaHint || BAOTA_RESTART_HINT
          ElMessage({
            type: 'success',
            duration: 12000,
            showClose: true,
            message: `${res.data.message || '更新完成，请刷新页面'} ${hint}`
          })
        } else if (res.data.status === 'error') {
          stopStatusPoll()
          updatingOnline.value = false
          ElMessage.error(res.data.message || '在线更新失败')
        }
      }
    } catch {
      // 后端重启中，连接中断属正常
    }
  }, 2000)
}

const refreshUpdatePaths = async () => {
  detectingPaths.value = true
  try {
    const res = await monitorApi.detectUpdatePaths()
    if (res.success && res.data) {
      updatePaths.value = {
        ...updatePaths.value,
        ...res.data,
        channel: updatePaths.value.channel || res.data.channel || ''
      }
      if (res.data.detectError) {
        ElMessage.warning(`路径自动检测部分失败：${res.data.detectError}，请核对默认路径`)
      } else if (!res.data.frontendDistDetected) {
        ElMessage.warning('未自动匹配到 dist，请填写含 index.html 的站点目录（宝塔多为 /www/wwwroot/域名/ ）')
      } else if (!res.data.jarExists || !res.data.distExists) {
        ElMessage.warning('部分路径无效，请核对是否存在 index.html')
      }
    } else {
      ElMessage.error(res.message || '检测路径失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '检测路径失败')
  } finally {
    detectingPaths.value = false
  }
}

const openOnlineUpdateDialog = async () => {
  showOnlineUpdateDialog.value = true
  onlineUpdateStatus.value = null
  updatePaths.value.channel = ''
  await refreshUpdatePaths()
  try {
    const chk = await monitorApi.checkUpdate()
    updatePaths.value.channel = chk.channel || ''
  } catch {
    /* 通道信息可选 */
  }
}

const startOnlineUpdate = async () => {
  try {
    await ElMessageBox.confirm(
      '将下载并覆盖后端 JAR 与前端 dist，随后自动重启后端服务。更新过程中请勿关闭服务器。是否继续？',
      '确认在线更新',
      { type: 'warning', confirmButtonText: '开始更新', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  updatingOnline.value = true
  onlineUpdateStatus.value = { status: 'running', percent: 0, message: '正在启动…' }
  try {
    const res = await monitorApi.startOnlineUpdate({
      backendJarPath: updatePaths.value.backendJarPath.trim(),
      frontendDistPath: updatePaths.value.frontendDistPath.trim()
    })
    if (!res.success) {
      throw new Error(res.message || '启动失败')
    }
    onlineUpdateStatus.value = res.data || { status: 'running', percent: 0, message: '更新中…' }
    pollUpdateStatus()
  } catch (e) {
    updatingOnline.value = false
    ElMessage.error(e.message || '在线更新失败')
  }
}

const checkUpdate = async () => {
  checking.value = true
  hasNewerVersion.value = false
  try {
    if (isDesktop.value) {
      await checkDesktopUpdate({ force: true, silent: false })
      return
    }

    const { data, channel } = await monitorApi.checkUpdate()
    const remoteVersion = data.version
    const local = currentVersion.replace(/^v/i, '')
    const channelLabel = channel === 'github' ? 'GitHub' : channel === 'gitee' ? 'Gitee' : ''

    if (compareVersions(local, remoteVersion) < 0) {
      updateInfo.value = data
      changelogExpanded.value = false
      hasNewerVersion.value = true
      showUpdateDialog.value = true
      if (channelLabel) {
        ElMessage.info(`已通过 ${channelLabel} 通道获取版本信息`)
      }
    } else {
      const suffix = channelLabel ? `（${channelLabel} 通道）` : ''
      ElMessage.success(`当前已是最新版本${suffix}`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.message || '检查更新失败，请稍后重试')
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  loadDesktopVersion()
})

onUnmounted(() => {
  stopStatusPoll()
})

const goToRepo = () => {
  const url = updateInfo.value?.releasesUrl || updateInfo.value?.repoUrl
  if (url) {
    if (isDesktop.value && window.electronAPI?.openExternal) {
      window.electronAPI.openExternal(url)
    } else {
      window.open(url, '_blank')
    }
    showUpdateDialog.value = false
  }
}

/** 恶搞彩蛋入口（预留，后期在此扩展） */
const onPrankClick = () => {
  // TODO: 在此增加恶搞逻辑
}
</script>

<style scoped>
.system-info-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  color: #303133;
  margin-bottom: 8px;
}

.subtitle {
  color: #909399;
  font-size: 14px;
}

.sponsor-card {
  border-color: #946ce6;
}

.sponsor-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 20px 0;
  gap: 15px;
}

.sponsor-logo {
  width: 80px;
  height: 80px;
  margin-bottom: 10px;
}

.sponsor-btn {
  text-decoration: none;
}

.prank-card {
  border-color: #fca5a5;
  display: flex;
  flex-direction: column;
}

.prank-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.prank-content {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.prank-btn {
  margin-top: 0;
  padding: 10px 28px;
  border: 2px solid #ef4444;
  border-radius: 999px;
  background: linear-gradient(180deg, #fef2f2 0%, #fee2e2 100%);
  color: #b91c1c;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.15);
}

.prank-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.25);
  background: linear-gradient(180deg, #fee2e2 0%, #fecaca 100%);
}

.prank-btn:active {
  transform: translateY(0);
}

.supporters-card {
  grid-column: 1 / -1;
}

.supporters-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.bug-report-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.35rem 0.9rem;
  border-radius: 999px;
  background: #0369a1;
  color: #fff;
  font-size: 0.8125rem;
  font-weight: 600;
  text-decoration: none;
  line-height: 1.4;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.bug-report-btn:hover {
  background: #0284c7;
  color: #fff;
}

.bug-report-btn-block {
  border-radius: 8px;
  padding: 0.5rem 1rem;
}

.supporters-form-link {
  color: #0369a1;
  text-decoration: none;
}

.supporters-form-link:hover {
  text-decoration: underline;
}

.supporters-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.supporters-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.supporters-desc {
  margin: 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.supporters-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.supporter-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.supporter-rank {
  flex-shrink: 0;
  width: 1.75rem;
  height: 1.75rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 600;
}

.supporter-name {
  flex: 1;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.4;
}

.supporter-count {
  flex-shrink: 0;
  color: #64748b;
  font-size: 12px;
}

.supporters-more-btn {
  padding: 0;
  border: none;
  background: none;
  color: #0369a1;
  font-size: 13px;
  cursor: pointer;
  line-height: 1.5;
}

.supporters-more-btn:hover {
  color: #0284c7;
  text-decoration: underline;
}

.supporters-dialog-desc {
  margin: 0 0 12px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.supporters-list-dialog {
  max-height: 420px;
  overflow-y: auto;
  padding-right: 4px;
}

:deep(.supporters-dialog .el-dialog__body) {
  padding-top: 8px;
}

.info-content {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  margin-bottom: 24px;
}

.timeline-section {
  margin-top: 24px;
}

.timeline-section h4 {
  margin: 0 0 10px;
  color: #303133;
}

.timeline-section p {
  margin: 0 0 5px;
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
}

.info-card {
  height: 100%;
}

.card-header {
  font-weight: bold;
}

.link {
  color: #409EFF;
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}

.license-content {
  color: #606266;
  line-height: 1.6;
}

.version-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.version-sub {
  color: #909399;
  font-size: 13px;
}

.electron-download-progress {
  margin-top: 16px;
}

.electron-install-mode {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8f9fc;
  border: 1px solid #ebeef5;
}

.electron-install-mode-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.install-mode-hint {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.install-dir-hint {
  margin: 10px 0 0;
  color: #606266;
  font-size: 12px;
  word-break: break-all;
}

:deep(.update-dialog .el-dialog__header) {
  margin-right: 0;
  padding-bottom: 0;
}

:deep(.update-dialog .el-dialog__body) {
  padding-top: 12px;
}

.update-dialog-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.update-dialog-logo-wrap {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.update-dialog-logo-wrap--online {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.update-dialog-logo {
  width: 36px;
  height: 36px;
  object-fit: contain;
  display: block;
}

.update-dialog-title {
  margin: 0;
  font-size: 18px;
  color: #1e293b;
}

.update-dialog-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

.update-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.version-compare {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 14px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.version-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 16px;
  border-radius: 10px;
  min-width: 88px;
}

.version-pill--current {
  background: #fff;
  border: 1px solid #e2e8f0;
}

.version-pill--new {
  background: #409eff;
  color: #fff;
  border: 1px solid #337ecc;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.28);
}

.version-pill-label {
  font-size: 11px;
  opacity: 0.85;
}

.version-pill-value {
  font-size: 16px;
  font-weight: 700;
}

.version-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
}

.version-arrow-logo {
  width: 22px;
  height: 22px;
  object-fit: contain;
  opacity: 0.75;
}

.update-meta {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  text-align: center;
}

.update-meta span {
  color: #94a3b8;
}

.changelog-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 14px;
  max-height: 280px;
  overflow-y: auto;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-weight: 600;
  font-size: 14px;
  color: #334155;
}

.panel-count {
  font-weight: 400;
  font-size: 12px;
  color: #94a3b8;
}

.changelog-list {
  padding-left: 18px;
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.55;
}

.changelog-list li {
  margin-bottom: 6px;
}

.changelog-toggle {
  display: block;
  width: 100%;
  margin-top: 10px;
  padding: 8px 0;
  border: none;
  background: none;
  color: #409eff;
  font-size: 13px;
  cursor: pointer;
  text-align: center;
}

.changelog-toggle:hover {
  color: #337ecc;
  text-decoration: underline;
}

.update-collapse {
  border: none;
}

:deep(.update-collapse .el-collapse-item__header) {
  background: #f1f5f9;
  border-radius: 8px;
  padding: 0 12px;
  height: 40px;
  border: 1px solid #e2e8f0;
}

:deep(.update-collapse .el-collapse-item__wrap) {
  border: none;
}

.update-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.update-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.update-action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.update-action-btn--ghost {
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
}

.update-action-btn--ghost:hover:not(:disabled) {
  background: #f5f7fa;
  border-color: #c0c4cc;
}

.update-action-btn--outline {
  border: 1px solid #409eff;
  background: #ecf5ff;
  color: #409eff;
}

.update-action-btn--outline:hover:not(:disabled) {
  background: #d9ecff;
  border-color: #337ecc;
  color: #337ecc;
}

.update-action-btn--brand {
  border: 1px solid #67c23a;
  background: #67c23a;
  color: #fff;
}

.update-action-btn--brand:hover:not(:disabled) {
  background: #5daf34;
  border-color: #5daf34;
}

.update-btn-logo {
  width: 18px;
  height: 18px;
  object-fit: contain;
  flex-shrink: 0;
}

.update-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: #409eff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 6px;
}

.update-link-btn:hover:not(:disabled) {
  background: #ecf5ff;
  color: #337ecc;
}

.update-link-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.update-link-btn-logo {
  width: 14px;
  height: 14px;
  object-fit: contain;
}

.script-block {
  margin-bottom: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
}

.script-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
  font-size: 13px;
  color: #606266;
  font-weight: bold;
}

.script-content {
  font-family: monospace;
  background-color: #303133;
  color: #fff;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  word-break: break-all;
  white-space: pre-wrap;
}

.online-update-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.path-detect-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: #f1f5f9;
  border-radius: 8px;
  font-size: 12px;
  color: #64748b;
}

.path-detect-bar code {
  font-size: 11px;
  background: #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  color: #334155;
}

.path-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.path-card {
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.path-card.is-invalid {
  border-color: #fcd34d;
  background: #fffbeb;
}

.path-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.path-card-title {
  font-weight: 600;
  font-size: 14px;
  color: #334155;
}

.path-match-hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
}

.path-match-hint.is-warn {
  color: #b45309;
}

.path-match-hint.is-ok {
  color: #15803d;
}

.path-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  font-size: 12px;
  color: #94a3b8;
}

.online-update-progress {
  margin-top: 4px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.progress-msg {
  margin: 8px 0 0;
  font-size: 13px;
  color: #475569;
}

.update-done-baota-hint {
  margin-top: 12px;
}
</style>
