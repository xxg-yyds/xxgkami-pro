<div align="center">

<img src="src/assets/icon.gif" alt="icon" width="120" />

# 🚀 小小怪卡密验证系统 Pro

### 🎯 全新一代卡密验证解决方案 - 正式发布！-完全免费-团队维护更新

[![Vue 3](https://img.shields.io/badge/Vue-3.5+-4FC08D?style=for-the-badge&logo=vue.js&logoColor=white)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-7.1+-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.11+-409EFF?style=for-the-badge&logo=element&logoColor=white)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

**🔥 采用最新技术栈重构，性能提升300%，用户体验全面升级！**

🏠 [官方网站](https://www.xxgkami.com/) · 📖 [官网文档](https://doc.xxgkami.com/) · 🌟 [官方体验](https://demo.xxgkami.com/)

[🌟 立即体验](https://demo.xxgkami.com/) · [📖 部署指南](#-快速部署指南) · [🐛 反馈问题](https://github.com/xxg-yyds/xxgkami-pro/issues) · [💬 加入讨论](https://github.com/xxg-yyds/xxgkami-pro/discussions)

</div>

> ⚠️ **仓库迁移说明**：旧版 GitHub 发布地址 [xiaoxiaoguai-yyds/xxgkami-pro](https://github.com/xiaoxiaoguai-yyds/xxgkami-pro) **已失效**，请勿再从此仓库拉取安装脚本或 Releases。  
> 当前**新版本**仓库与发布地址：[**xxg-yyds/xxgkami-pro**](https://github.com/xxg-yyds/xxgkami-pro) · [Releases 下载页](https://github.com/xxg-yyds/xxgkami-pro/releases)

---

## 📦 快速部署指南

本项目提供四种灵活的部署方式，满足不同场景的需求。

### 方式一：Linux 一键脚本安装（推荐 🔥）

适用于 CentOS 7+ / Debian 10+ / Ubuntu 20.04+ 系统，全自动环境配置。

**国内服务器（Gitee 源）：**
```bash
curl -O https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/raw/master/install.sh && chmod +x install.sh && sudo ./install.sh
```

**海外服务器（GitHub 源）：**
```bash
curl -O https://raw.githubusercontent.com/xxg-yyds/xxgkami-pro/refs/heads/master/install.sh && chmod +x install.sh && sudo ./install.sh
```

**脚本功能：**
- ✅ 自动检测网络环境（GitHub/Gitee 源自动切换）
- ✅ 自动安装 JDK 20, MySQL 8.0, Nginx, Node.js 环境
- ✅ 自动编译前后端代码并配置 Systemd 开机自启
- ✅ 自动配置 Nginx 反向代理与静态资源托管

---

### 方式二：本地文件部署（编译版）

适用于无法在服务器上编译、或希望直接上传制品快速上线的场景（Linux 服务器 + Nginx，不依赖宝塔面板亦可）。

#### 1. 获取部署文件

编译制品（如 `backend.jar`、`dist.zip` 等）可通过以下渠道获取：

| 渠道 | 说明 |
|------|------|
| **GitHub Releases** | [https://github.com/xxg-yyds/xxgkami-pro/releases](https://github.com/xxg-yyds/xxgkami-pro/releases) 下载最新发布包 |
| **GitHub 仓库** | [https://github.com/xxg-yyds/xxgkami-pro](https://github.com/xxg-yyds/xxgkami-pro) 查看源码与仓库内附带文件 |
| **官方 QQ 群** | 群号 `1050160397`，群内可获取最新编译包与部署答疑 |

<a target="_blank" href="https://qm.qq.com/cgi-bin/qm/qr?k=CuHJ-5Rf6uW94FT7ogPCodTjAghwBl0C&jump_from=webapi&authKey=GkL+FpvNF54Wko1C+gVqqiONrrYnpTCka5uKQyFHboOc9hZUVa9eeCMWqyFUyIob"><img border="0" src="https://pub.idqqimg.com/wpa/images/group.png" alt="小小怪卡密" title="小小怪卡密"></a>

#### 2. 环境准备

- JDK 20+
- MySQL 8.0+
- Nginx

#### 3. 部署步骤

1. **数据库**：导入 `kami.sql`（或按安装文档初始化），在 `application.properties` 中配置数据库连接。
2. **后端**：上传 `backend.jar` 至服务器，执行：
   ```bash
   nohup java -jar backend.jar > backend.log 2>&1 &
   ```
   默认监听 `8080`，上下文路径为 `/api`。
3. **前端**：将 `dist.zip` 解压到网站根目录（如 `/www/wwwroot/your-domain/`）。
4. **Nginx**：配置静态站点，并将 API 反向代理到后端（**注意** `proxy_pass` 末尾不要多加 `/`，以免去掉 `/api` 前缀）：
   ```nginx
   location /api {
       proxy_pass http://127.0.0.1:8080;
   }
   ```
   可参考仓库内 [deployment/nginx.conf](deployment/nginx.conf) 与 [deployment/README.md](deployment/README.md)。

---

### 方式三：宝塔面板部署（编译版）

适用于已有宝塔面板的服务器，使用编译好的制品快速部署。

1. **下载编译制品**：从 [Releases](https://github.com/xxg-yyds/xxgkami-pro/releases) 下载最新的 `backend.jar` 和 `dist.zip`。
2. **后端部署**：
   - 在宝塔“Java项目”中添加项目。
   - 上传 `backend.jar`，选择 JDK 20。
   - 配置端口 `8080`，并设置数据库连接信息（`application.properties`）。
3. **前端部署**：
   - 在宝塔“网站”中添加静态站点。
   - 上传并解压 `dist.zip` 到网站根目录。
   - 配置 Nginx 反向代理：
     ```nginx
     location /api {
         proxy_pass http://localhost:8080/api;
     }
     ```

---

### 方式四：手动编译部署

适用于开发者或需要深度定制的场景。

**前提条件：** JDK 20+, Maven 3.8+, Node.js 18+, MySQL 8.0+

1. **克隆代码**
   ```bash
   git clone https://github.com/xxg-yyds/xxgkami-pro.git
   cd xxgkami-pro
   ```

2. **后端编译**
   ```bash
   cd backend
   # 修改 src/main/resources/application.properties 中的数据库配置
   mvn clean package -DskipTests
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

3. **前端编译**
   ```bash
   cd ../
   npm install
   npm run build
   # 构建产物位于 dist/ 目录，可使用 Nginx 进行托管
   ```

---

## 📊 功能模块

### 🎛️ **管理员后台**
- 📈 **数据概览** - 实时统计面板，掌控全局数据
- 🔑 **卡密管理** - 批量生成、导出、状态管理，支持多种卡密类型
- 📋 **订单管理** - 订单实时监控、状态跟踪、补单操作
- 🔌 **API管理** - 接口密钥生成、权限控制、WebHook回调配置
- ⚙️ **系统设置** - 网站信息配置、支付接口对接、邮件通知设置
- 🛡️ **安全中心** - IP黑白名单、访问日志审计

### 👤 **用户中心**
- 🏠 **个人面板** - 账户信息概览、近期活动记录
- 🛒 **购买中心** - 在线选购卡密、支持多种支付方式
- 📱 **卡密验证** - 快速验证卡密有效性、查看使用说明
- 💰 **钱包管理** - 余额充值、消费明细查询
- 🎫 **我的卡密** - 已购卡密历史记录、一键复制

### 🔌 **开发者接口**
- ✅ **卡密验证 API** - 标准化 RESTful 接口，轻松集成
- 📊 **状态查询 API** - 实时查询卡密状态和剩余次数/时间
- 🔔 **WebHook 回调** - 卡密核销实时推送通知
- 🛡️ **安全认证** - 支持 API Key 签名认证，保障数据安全

---

## 🎯 使用场景

### 💼 **软件授权**
- 🖥️ 桌面软件激活
- 📱 移动应用授权
- 🎮 游戏道具验证
- 🔧 插件功能解锁

### 🎓 **在线教育**
- 📚 课程访问控制
- 🎥 视频观看权限
- 📝 考试系统验证
- 🏆 证书颁发管理

### 💰 **电商平台**
- 🎁 优惠券系统
- 🎪 会员权益管理
- 🛍️ 限时抢购控制
- 💎 VIP服务激活

---

## 📈 性能对比

| 功能特性 | 旧版本 | 新版本 Pro | 提升幅度 |
|---------|--------|------------|----------|
| 🚀 页面加载速度 | 3.2s | 0.8s | **300%** ⬆️ |
| 💾 内存占用 | 120MB | 45MB | **62%** ⬇️ |
| 📊 数据处理能力 | 1000/s | 5000/s | **400%** ⬆️ |
| 🔄 并发支持 | 100 | 1000 | **900%** ⬆️ |
| 📱 移动端适配 | 60% | 100% | **40%** ⬆️ |

---

## 🛣️ 开发路线图

### 📅 更新日志

#### v1.0.2 (2026-02-09)
- 🐛 **注册修复**：修复用户注册时因邮件配置缺失导致的 500 错误，优化异常处理逻辑。
- 🔧 **工具升级**：升级 xxgkami 命令行工具，新增数据库智能增量更新功能，支持无损升级。
- 📦 **部署优化**：优化一键安装脚本，提升部署稳定性和兼容性。
- 🛠️ **体验改进**：修复部分已知的小问题，提升系统整体稳定性。

#### v1.0.1 (2026-01-30)
- 🔒 **安全性升级**：完善管理员账号密码加密逻辑，数据库存储由明文全面升级为 BCrypt 加密。
- ℹ️ **新增系统信息页**：管理员后台新增"系统信息"页面，支持查看版本号、开源协议、开发团队等信息。
- 🔄 **在线检查更新**：系统信息页支持一键检查新版本，并提供国内/海外一键更新脚本。
- 🎨 **UI 优化**：优化顶部导航栏布局，提升视觉体验。
- 🐛 **问题修复**：修复已知的小问题，提升系统稳定性。

### 🎯 **第一阶段 (当前)**
- [x] 核心功能重构
- [x] 现代化UI设计
- [x] 基础API接口
- [ ] 用户系统完善
- [ ] 支付系统集成

### 🚀 **第二阶段 (2025 Q1)**
- [ ] 移动端APP
- [ ] 微信小程序
- [ ] 高级数据分析
- [ ] 多语言支持
- [ ] 插件系统

### 🌟 **第三阶段 (2025 Q2)**
- [ ] AI智能推荐
- [ ] 区块链集成
- [ ] 云原生部署
- [ ] 企业级功能
- [ ] 开放平台

---

## 🤝 参与贡献

我们欢迎所有形式的贡献！

### 🎯 **如何参与**
1. 🍴 Fork 本仓库
2. 🌿 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 💾 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 📤 推送分支 (`git push origin feature/AmazingFeature`)
5. 🔄 创建 Pull Request

### 🏆 **贡献者排行榜**
感谢所有为项目做出贡献的开发者！

<a href="https://github.com/xxg-yyds/xxgkami-pro/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=xxg-yyds/xxgkami-pro" />
</a>

---

## ☕ 赞助与支持

如果您觉得本项目对您有帮助，可以请作者喝一杯咖啡，这将成为我们持续更新的动力！

<div align="center">
  <a href="https://ifdian.net/a/xxgyyds" target="_blank">
    <img src="aifadian.svg" width="200" alt="爱发电赞助" />
  </a>
  <br/>
  <br/>
  <a href="https://ifdian.net/a/xxgyyds">
    <img src="https://img.shields.io/badge/Sponsor-爱发电-946CE6?style=for-the-badge&logo=electricity&logoColor=white" alt="爱发电" />
  </a>
</div>

---

## 📞 联系我们

### 💬 **社区交流**
- 🐛 [问题反馈](https://github.com/xxg-yyds/xxgkami-pro/issues)
- 💡 [功能建议](https://github.com/xxg-yyds/xxgkami-pro/discussions)
- 💬 **官方交流群**：`1050160397`（售后 / 技术支持）  
  <a target="_blank" href="https://qm.qq.com/cgi-bin/qm/qr?k=CuHJ-5Rf6uW94FT7ogPCodTjAghwBl0C&jump_from=webapi&authKey=GkL+FpvNF54Wko1C+gVqqiONrrYnpTCka5uKQyFHboOc9hZUVa9eeCMWqyFUyIob"><img border="0" src="https://pub.idqqimg.com/wpa/images/group.png" alt="小小怪卡密" title="小小怪卡密"></a>
- 📧 **联系邮箱**：`xxgyyds@vip.qq.com`

### 🌐 **开源地址**
- 🐙 **GitHub**: [https://github.com/xxg-yyds/xxgkami-pro](https://github.com/xxg-yyds/xxgkami-pro)
- 🔴 **Gitee**: [https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro](https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro)
- 🚀 **GitCode**: [https://atomgit.com/xxg-yyds/xxgkami-pro](https://atomgit.com/xxg-yyds/xxgkami-pro)

### 🌐 **官方链接**
- 🏠 [官方网站](https://www.xxgkami.com/)
- 📖 [官网文档](https://doc.xxgkami.com/)
- 🌟 [官方体验](https://demo.xxgkami.com/)
- 🐱 [GitHub 仓库](https://github.com/xxg-yyds/xxgkami-pro)
- 🔴 [Gitee 仓库](https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro)
- 🚀 [GitCode 仓库](https://atomgit.com/xxg-yyds/xxgkami-pro)

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源协议发布。

---

## ⭐ Star 历史

[![Star History Chart](https://api.star-history.com/svg?repos=xxg-yyds/xxgkami-pro&type=Date)](https://star-history.com/#xxg-yyds/xxgkami-pro&Date)

---

<div align="center">

### 🎉 感谢您的关注！

**如果这个项目对您有帮助，请不要忘记给我们一个 ⭐ Star！**

**让我们一起构建更好的卡密验证系统！** 🚀

---

*© 2025 小小怪卡密验证系统. All rights reserved.*

</div>
