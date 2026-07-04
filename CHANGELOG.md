# XXG-KAMI 更新日志

> 当前版本：**v1.0.7** · 构建日期：2026-06-04  
> 仓库：[GitHub xxg-yyds/xxgkami-pro](https://github.com/xxg-yyds/xxgkami-pro) · [Gitee xiaoxiaoguai-yyds/xxgkami-pro](https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro)

---

## v1.0.7（2026-06-04）

### 新功能

1. **在线更新**：系统信息页可一键更新；自动探测后端 JAR 与前端 dist 路径，支持手动修改确认后下载 Release 制品并替换，完成后自动重启后端
2. **version.json 新增 releaseDownloads**：Gitee / GitHub 各含 `dist.zip` 与 `backend-0.0.1-SNAPSHOT.jar` 下载地址，支持 `{version}` 占位符
3. **检查更新优化**：并行 ping gitee.com 与 github.com，优先使用先连通的通道拉取 version.json，并提示所用通道
4. **首次安装向导完善**：检测 kami 库是否存在 `admins` 表；无 `admins` 视为未初始化业务库，自动执行全新导入（跳过「覆盖/智能更新」策略页）
5. **MySQL 5.6 兼容**：选 MySQL 5.6 时由 `kami.sql` 自动转译为 5.6 兼容脚本并显示转译进度，无需单独维护 `kami_mysql56.sql`
6. **种子 SQL 内嵌 JAR**：打包时纳入 `databaes/kami.sql`，部署仅上传 JAR 亦可安装；支持解压到 `data/.xxgkami-seed` 及自定义 `seed-sql-override`
7. **安装/升级标记**：以 `data/.xxgkami-setup.complete` 与 `admins` 表共同判定业务库就绪；未完成前跳过业务 JDBC 探测与 ApiKey 等初始化，避免空库报错
8. **Spring Session**：使用 `spring_session` 表名，启动时初始化会话表；修复 `SPRING_SESSION` 相关启动与清理错误
9. **新版升级检测**：业务库就绪后按 `data/.xxgkami-version.json` 与远程版本比对，支持智能合并更新与版本升级向导
10. **引导页与加载页 UI**：logo 蒙版渐隐背景；系统初始化向导支持版本升级模式与 5.6 SQL 转译进度展示
11. **卡密管理列表**：缩短卡密列显示宽度；新增 IP 列（有 `ip_address` 显示 IP，否则显示「未绑定」）

### 问题修复

12. 修复 MySQL 5.6 转译时 `replaceAll("${1}")` 触发 Java 命名捕获组告警，改为安全替换逻辑
13. 修复宝塔等仅部署 JAR 环境提示「未找到 databaes/kami.sql」导致安装卡在 0% 的问题
14. 修复首次启动 Spring Session 等自动建表导致向导误报「库已存在需覆盖/合并」的问题
15. 远程 version.json Gitee 源地址调整为 `xiaoxiaoguai-yyds/xxgkami-pro`，与 Release 下载仓库一致

### v1.0.7 后续补丁（已合入当前代码）

16. **路径探测增强**：优先从 Java 进程 `-jar` 参数 / `ApplicationHome` 解析 JAR 路径；支持宝塔 `/www/wwwroot/backend/` 布局；dist 扫描站点子目录与 `index.html`
17. **远程更新通道**：Gitee 未同步 `releaseDownloads` 时自动回退 GitHub；下载优先 Gitee，支持 `1.0.7` / `v1.0.7` 标签与多地址重试
18. **在线更新权限**：修复 JWT 在匿名认证下未生效导致 `/api/monitor/update/*` Access Denied
19. **更新完成提示**：提示宝塔权限可能导致无法自动重启，需前往「网站 → Java 项目」手动启动
20. **智能更新管道**：修复 `mysqldump` 警告混入 SQL 导致 `mysql=1`；失败时输出 mysql 详细错误
21. **智能更新结构同步**：合并前自动 `ALTER TABLE` 补齐种子库新增字段（如 `api_keys.require_machine_code`），避免 Unknown column
22. **登录与向导跳转**：修复管理员登录成功不进入后台；安装/升级完成后支持默认密码自动登录

### Release 下载

| 通道 | dist.zip | backend JAR |
|------|----------|-------------|
| Gitee | `https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/releases/download/{version}/dist.zip` | `https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/releases/download/{version}/backend-0.0.1-SNAPSHOT.jar` |
| GitHub | `https://github.com/xxg-yyds/xxgkami-pro/releases/download/{version}/dist.zip` | `https://github.com/xxg-yyds/xxgkami-pro/releases/download/{version}/backend-0.0.1-SNAPSHOT.jar` |

### 命令行更新脚本

```bash
# 国内 · Gitee
curl -O https://gitee.com/xxg-yyds/xxgkami-pro/raw/master/install.sh && chmod +x install.sh && sudo ./install.sh

# 海外 · GitHub
curl -O https://raw.githubusercontent.com/xxg-yyds/xxgkami-pro/refs/heads/master/install.sh && chmod +x install.sh && sudo ./install.sh
```

---

## v1.0.6（2026-05-06）

1. 新增 API 密钥管理页「代码实例」入口（位于「接口文档」左侧），便于快速查阅核销接口调用方式
2. 新增核销接口多语言示例弹窗：覆盖 Shell（cURL/Wget/HTTPie 等）、PowerShell、Node.js、浏览器/JavaScript、HTML、Vue 3、Python、PHP、Go、Java、C#、Ruby、Kotlin、Dart、Rust、Swift 等常见语言与工具
3. 示例数据独立维护于 `apiUseCardCodeExamples.js`，弹窗内支持左侧切换语言、一键复制源码
4. 集成 highlight.js（GitHub Dark 主题），各语言代码块支持语法高亮；展示当前环境 API 根路径参考，便于替换示例中的 BASE_URL
5. 卡密管理：支持按设备码/机器码查找对应卡密；导出支持筛选「未使用」「已使用」
6. 时间卡规则：新增「叠加 / 单张」模式——单张为现行逻辑（以激活时刻起算到期）；叠加模式下同一设备续充时间卡时，将新卡时长累加到当前生效授权的到期时间，实现无缝续期；参与叠加的卡密标记为已使用（叠加）或已合并
7. 一机一码增强：卡密在首台设备绑定且为已使用后，其它机器码无法再通过该卡核销，补全跨机校验与防重复滥用
8. API 密钥能力：支持按密钥配置「同一机器码对指定卡密类型仅可成功核销一次」（例如每台机仅一次 1 天体验卡）
9. 用户首页：支持用户自行解绑已绑定的机器码（无需仅依赖管理员后台）
10. 管理后台：顶部导航栏布局与交互重做，提升入口清晰度与操作效率
11. 修正部分示例片段（如 Go JSON 请求体、C# HttpClient 调用等），保证可对照使用
12. 修复次数卡剩余最后一次无法正常扣减、次数无法归零的问题
13. 修复已激活时间卡在后台修改时长不生效的问题

---

## v1.0.5（2026-04-08）

1. 新增一机一码功能：卡密首次核销时自动绑定机器码，后续验证必须匹配
2. 新增机器码管理：卡密列表展示机器码列，编辑弹窗支持重置机器码
3. 新增时间卡密实时倒计时：管理端按秒刷新显示剩余时间
4. 新增卡密启用/暂停功能：已使用卡密被暂停后提示「卡密被停止使用」
5. 新增重复验证控制：时间卡密支持开启/关闭重复验证，关闭后仅允许验证一次
6. 新增批量创建进度条：大量创建卡密时逐条创建，实时显示进度
7. 优化登录页 UI：管理员与用户登录页采用不同主题，管理员页新增返回入口
8. 优化自定义接口：输入/输出参数新增机器码变量，新增机器码不匹配/重复验证拒绝状态码
9. 修复高级时间卡密激活后管理端显示「未激活」的问题
10. 修复新创建卡密立即开始倒计时的问题（改为首次核销后才计时）
11. 修复卡密导出时间卡到期时间为空的问题
12. 修复 API 管理无法分配用户的问题
13. 修复 CORS 不支持 PATCH 方法的问题

---

## v1.0.4（2026-03-05）

1. 修复次数卡密在设置过期时间后被误识别为时间卡密的问题
2. 优化卡密验证核心逻辑，优先根据总次数判断卡密类型
3. 修复部分已知后端逻辑问题，提升系统稳定性

---

## v1.0.3（2026-02-13）

1. 全面优化移动端 UI 体验，重构用户管理页面为响应式卡片布局
2. 修复移动端侧边栏菜单无法展开的问题，优化首页导航栏显示
3. API 接口自定义返回配置新增「卡密状态」字段支持
4. 优化管理后台顶部导航栏，移除冗余的消息通知入口
5. 修复部分已知 UI 溢出和布局错位问题

---

## v1.0.2（2026-02-09）

1. 修复用户注册时因邮件配置缺失导致的 500 错误，优化异常处理逻辑
2. 升级 xxgkami 命令行工具，新增数据库智能增量更新功能
3. 优化系统安装脚本，提升部署体验
4. 修复部分已知的小问题

---

## v1.0.1（2026-01-30）

1. 完善管理员账号密码加密逻辑，数据库存储由明文全面升级为 BCrypt 加密
2. 新增系统信息页面，支持查看版本信息、开源协议及开发团队
3. 新增在线检查更新功能，提供国内/海外一键更新脚本
4. 优化前端导航栏布局，提升用户体验

---

## v1.0.0（2026-01-17）

小小怪卡密验证系统 1.0 正式发布。

---

*本文档由 `public/version.json` 与 `SystemInfo.vue` 版本历程整理，并补充 v1.0.7 后续补丁说明。*
