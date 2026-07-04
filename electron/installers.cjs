/** 官方 / 推荐安装包（Windows x64 MSI） */
const INSTALLERS = {
  nodejs: {
    key: 'nodejs',
    label: 'Node.js 24.18.0',
    fileName: 'node-v24.18.0-x64.msi',
    url: 'https://nodejs.org/dist/v24.18.0/node-v24.18.0-x64.msi',
    description: '用于前端构建与部分运维脚本',
    installGuide: {
      type: 'link',
      steps: '下载完成后双击 MSI 安装；安装后请配置环境变量（PATH）。',
      tutorialUrl: 'https://blog.csdn.net/AV_VA1/article/details/149789138',
      tutorialLabel: '查看 Node.js 安装与配置教程',
    },
  },
  java: {
    key: 'java',
    label: 'JDK 21',
    fileName: 'jdk-21_windows-x64_bin.msi',
    url: 'https://d10.injdk.cn/openjdk/oraclejdk/21/jdk-21_windows-x64_bin.msi',
    description: '用于运行后端 Spring Boot 服务（需 JDK 20 及以上）',
    installGuide: {
      type: 'auto',
      steps: '下载完成后双击 MSI 安装包，按向导点击「下一步」即可自动完成安装，一般无需额外配置。',
    },
  },
  mysql: {
    key: 'mysql',
    label: 'MySQL 8.0.27',
    fileName: 'mysql-8.0.27-winx64.msi',
    url: 'https://mirrors.aliyun.com/mysql/MySQL-8.0/mysql-8.0.27-winx64.msi',
    description: '用于存储卡密与系统数据',
    installGuide: {
      type: 'link',
      steps: '下载完成后双击 MSI 安装；安装与初始化配置请参考教程。',
      tutorialUrl: 'https://blog.csdn.net/qq_52853542/article/details/124669072',
      tutorialLabel: '查看 MySQL 安装与配置教程',
    },
  },
  git: {
    key: 'git',
    label: 'Git for Windows',
    fileName: 'Git-2.47.1.2-64-bit.exe',
    url: 'https://github.com/git-for-windows/git/releases/download/v2.47.1.windows.2/Git-2.47.1.2-64-bit.exe',
    description: '用于从 Gitee / GitHub 克隆源码',
    installGuide: {
      type: 'auto',
      steps: '下载完成后双击安装包，按向导完成安装（可全部使用默认选项）。',
    },
  },
}

const MIN_JAVA_MAJOR = 20
const MIN_MYSQL_MAJOR = 8

function parseJavaMajor(version) {
  if (!version) return 0
  const v = String(version).trim()
  if (v.startsWith('1.')) {
    const parts = v.split('.')
    return parseInt(parts[1], 10) || 0
  }
  return parseInt(v.split('.')[0], 10) || 0
}

function parseMysqlMajor(versionText) {
  const text = String(versionText || '')
  const match = text.match(/(?:Ver\s+|version\s+)?(\d+)\.(\d+)/i)
  if (!match) return 0
  return parseInt(match[1], 10) || 0
}

module.exports = {
  INSTALLERS,
  MIN_JAVA_MAJOR,
  MIN_MYSQL_MAJOR,
  parseJavaMajor,
  parseMysqlMajor,
}
