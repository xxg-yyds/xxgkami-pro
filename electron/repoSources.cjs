/** 开源仓库地址（国内 Gitee / 国外 GitHub） */
const REPO_SOURCES = {
  gitee: {
    region: 'domestic',
    regionLabel: '国内',
    platformLabel: 'Gitee',
    repoUrl: 'https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro',
    cloneUrl: 'https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro.git',
    releasesUrl: 'https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/releases',
  },
  github: {
    region: 'international',
    regionLabel: '国外',
    platformLabel: 'GitHub',
    repoUrl: 'https://github.com/xxg-yyds/xxgkami-pro',
    cloneUrl: 'https://github.com/xxg-yyds/xxgkami-pro.git',
    releasesUrl: 'https://github.com/xxg-yyds/xxgkami-pro/releases',
  },
}

function getRepoSourceByRegion(region) {
  return region === 'domestic' ? REPO_SOURCES.gitee : REPO_SOURCES.github
}

module.exports = {
  REPO_SOURCES,
  getRepoSourceByRegion,
}
