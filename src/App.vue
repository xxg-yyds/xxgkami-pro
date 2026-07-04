<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import HomePage from './components/HomePage.vue'
import OnlineUnbindPage from './components/OnlineUnbindPage.vue'
import LoginForm from './components/loginform.vue'
import Dashboard from './components/Dashboard.vue'
import UserPage from './components/UserPage.vue'
import NotificationPage from './components/NotificationPage.vue'
import { authApi, maintenanceApi, userProfileApi, setupApi } from './services/api.js'
import SystemSetupPage from './components/SystemSetupPage.vue'
import ElectronEnvCheckPage from './components/ElectronEnvCheckPage.vue'
import ElectronDesktopUpdateHost from './components/ElectronDesktopUpdateHost.vue'
import setupLogo from './assets/icon.png'

const isElectronDesktop = typeof window !== 'undefined' && !!window.electronAPI?.isDesktop
const electronEnvReady = ref(false)
const electronBooting = ref(isElectronDesktop)
const electronUpdateReady = ref(false)

const logoMaskVars = {
  '--setup-logo-mask': `url(${setupLogo})`
}
const brandLogo = setupLogo

// 响应式数据
const currentPage = ref('home') // 默认为首页
const loginType = ref('user')
const isLoggedIn = ref(false)
const userInfo = ref(null)
const loading = ref(true)
const maintenanceData = ref({ enabled: false, content: '', maintenanceTime: '' })
const setupRequired = ref(false)
const versionUpgradeRequired = ref(false)
const setupPageMode = ref('default')

/** 安装向导 / 新版升级检测 */
const refreshSetupStatus = async () => {
  try {
    const res = await setupApi.getStatus()
    const d = res.data || {}
    setupRequired.value = !!(res.success && d.needsSetupWizard)
    versionUpgradeRequired.value = !!(res.success && !d.needsSetupWizard && d.needsVersionUpgradeCheck)
    setupPageMode.value = versionUpgradeRequired.value ? 'version-upgrade' : 'default'
  } catch {
    setupRequired.value = false
    versionUpgradeRequired.value = false
    setupPageMode.value = 'default'
  }
}

const routeAdminAfterAuth = () => {
  if (setupRequired.value || versionUpgradeRequired.value) {
    currentPage.value = 'system-setup'
  } else {
    currentPage.value = 'dashboard'
  }
}

const readStoredAuth = () => ({
  token: localStorage.getItem('token'),
  userInfo: localStorage.getItem('userInfo'),
  isLoggedIn: localStorage.getItem('isLoggedIn') === 'true'
})

const persistAuth = (data) => {
  if (!data?.userInfo) {
    return false
  }
  userInfo.value = data.userInfo
  isLoggedIn.value = true
  localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
  localStorage.setItem('isLoggedIn', 'true')
  if (data.token) {
    localStorage.setItem('token', data.token)
  }
  if (data.refreshToken) {
    localStorage.setItem('refreshToken', data.refreshToken)
  }
  return true
}

const tryAutoAdminLogin = async (payload) => {
  const username = payload?.adminUsername || 'admin'
  const hint = payload?.adminPasswordHint || ''
  const canTryDefault =
    hint.includes('123456') ||
    hint.includes('默认密码') ||
    payload?.tryDefaultPassword === true
  if (!canTryDefault) {
    return false
  }
  try {
    const res = await authApi.loginAdmin(username, '123456')
    if (res.success && res.data?.userInfo) {
      persistAuth({
        userInfo: res.data.userInfo,
        token: res.data.token,
        refreshToken: res.data.refreshToken
      })
      await refreshSetupStatus()
      routeAdminAfterAuth()
      return true
    }
  } catch (e) {
    console.warn('安装后自动登录失败，将跳转登录页', e)
  }
  return false
}

// 检查登录状态
const checkLoginStatus = async () => {
  try {
    let { token: storedToken, userInfo: storedUserInfo, isLoggedIn: storedIsLoggedIn } = readStoredAuth()

    // 1. 优先尝试恢复登录状态
    if (storedToken && storedUserInfo && storedIsLoggedIn) {
      const parsedUserInfo = JSON.parse(storedUserInfo)
      isLoggedIn.value = true
      userInfo.value = parsedUserInfo

      if (parsedUserInfo.role === 'admin') {
        await refreshSetupStatus()
        routeAdminAfterAuth()
      } else {
        currentPage.value = 'user'
      }
      return
    }

    // 2. 未登录：避免与刚完成的登录写入竞态，延迟再读一次
    await new Promise((r) => setTimeout(r, 0))
    const retry = readStoredAuth()
    if (retry.token && retry.userInfo && retry.isLoggedIn) {
      await checkLoginStatus()
      return
    }

    const path = window.location.pathname
    const hash = window.location.hash
    if (path.includes('/admin') || hash.includes('admin')) {
      loginType.value = 'admin'
      await refreshSetupStatus()
      currentPage.value =
        setupRequired.value || versionUpgradeRequired.value ? 'system-setup' : 'login'
    } else if (setupRequired.value || versionUpgradeRequired.value) {
      loginType.value = 'admin'
      currentPage.value = 'system-setup'
    } else {
      isLoggedIn.value = false
      userInfo.value = null
      // 仅确认仍无 token 时再清理，避免覆盖刚写入的登录态
      const finalCheck = readStoredAuth()
      if (!finalCheck.token) {
        localStorage.removeItem('userInfo')
        localStorage.removeItem('isLoggedIn')
        localStorage.removeItem('token')
        localStorage.removeItem('refreshToken')
      }
    }
  } catch (error) {
    console.error('检查登录状态失败:', error)
    isLoggedIn.value = false
    userInfo.value = null
  } finally {
    loading.value = false
    if (isElectronDesktop && electronEnvReady.value) {
      electronUpdateReady.value = true
    }
  }
}

// 显示用户登录页
const showLogin = () => {
  currentPage.value = 'login'
  loginType.value = 'user'
  window.location.hash = '#/login'
}

// 显示管理员登录页（首页「登录管理」应走此入口）
const showAdminLogin = () => {
  loginType.value = 'admin'
  currentPage.value = 'login'
  window.location.hash = '#/admin'
}

// 处理登录成功
const handleLoginSuccess = async (data) => {
  if (!persistAuth(data)) {
    ElMessage.error('登录数据异常，请重试')
    return
  }
  if (data.userInfo.role === 'admin') {
    if (!window.location.hash.includes('admin')) {
      window.location.hash = '#/admin'
    }
    await refreshSetupStatus()
    routeAdminAfterAuth()
  } else {
    currentPage.value = 'user'
  }
}

const handleSetupFinished = async (payload) => {
  setupRequired.value = false
  versionUpgradeRequired.value = false
  setupPageMode.value = 'default'

  if (isLoggedIn.value && userInfo.value?.role === 'admin') {
    currentPage.value = 'dashboard'
    return
  }

  const autoOk = await tryAutoAdminLogin(payload)
  if (autoOk) {
    ElMessage.success('已进入管理后台')
    return
  }

  loginType.value = 'admin'
  currentPage.value = 'login'
  if (payload?.adminUsername) {
    sessionStorage.setItem('prefill_admin_username', payload.adminUsername)
  }
  ElMessage.info('请使用页面显示的管理员账号登录（初始密码多为 123456）')
}

// 处理登出
const handleLogout = async () => {
  try {
    if (userInfo.value) {
      // 尝试调用后端登出API
      await authApi.logout(userInfo.value.id, userInfo.value.role)
    }
  } catch (error) {
    console.error('登出失败:', error)
  } finally {
    // 无论后端是否成功，前端都清除状态
    isLoggedIn.value = false
    userInfo.value = null
    localStorage.removeItem('userInfo')
    localStorage.removeItem('isLoggedIn')
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    
    currentPage.value = 'login'
    
    // 根据当前 URL 判断是否显示管理员登录
    const path = window.location.pathname
    const hash = window.location.hash
    if (path.includes('/admin') || hash.includes('admin')) {
      loginType.value = 'admin'
    } else {
      loginType.value = 'user'
    }
  }
}

// 检查维护状态
const checkMaintenance = async () => {
  try {
    const res = await maintenanceApi.getStatus()
    if (res.success && res.data) {
      maintenanceData.value = {
        ...res.data,
        enabled: Boolean(res.data.enabled) // 确保转换为布尔值
      }
    }
  } catch (error) {
    console.error('检查维护状态失败:', error)
  }
}

// 处理 OAuth 回调
const handleOAuthCallback = async () => {
  const isBinding = sessionStorage.getItem('binding_mode') === 'true';
  
  // Check both search (query) and hash for params
  let urlParams = new URLSearchParams(window.location.search);
  let token = urlParams.get('token');
  let refreshToken = urlParams.get('refreshToken');

  // If not found in search, try hash (e.g. #/oauth/callback?token=...)
  if (!token && window.location.hash.includes('?')) {
      const hashQuery = window.location.hash.split('?')[1];
      const hashParams = new URLSearchParams(hashQuery);
      token = hashParams.get('token');
      refreshToken = hashParams.get('refreshToken');
  }
  
  // If still not found, check if it's in the path (some routers might do this)
  
  if (token && refreshToken) {
    console.log('[App] OAuth callback detected');
    
    if (isBinding) {
        alert('该社交账号已被其他用户绑定！');
        sessionStorage.removeItem('binding_mode');
        window.history.replaceState({}, document.title, window.location.pathname);
        return true; 
    }

    try {
      localStorage.setItem('token', token)
      localStorage.setItem('refreshToken', refreshToken)
      localStorage.setItem('isLoggedIn', 'true')
      
      // 获取用户信息
      const res = await authApi.getUserInfo()
      if (res.success) {
        userInfo.value = res.data
        localStorage.setItem('userInfo', JSON.stringify(res.data))
        isLoggedIn.value = true
        // Clean URL
        window.history.replaceState({}, document.title, window.location.pathname);
        currentPage.value = 'user'
      } else {
          console.error('OAuth login failed:', res);
          handleLogout();
      }
    } catch (e) {
      console.error('OAuth callback error:', e)
      handleLogout()
    }
  } else if (window.location.pathname.includes('/oauth/callback') || window.location.hash.includes('/oauth/callback')) {
      // Check for needRegister param
      const needRegister = urlParams.get('needRegister') || new URLSearchParams(window.location.hash.split('?')[1]).get('needRegister');
      if (needRegister === 'true') {
           const registerToken = urlParams.get('registerToken') || new URLSearchParams(window.location.hash.split('?')[1]).get('registerToken');
           const nickname = urlParams.get('nickname') || new URLSearchParams(window.location.hash.split('?')[1]).get('nickname');
           
           if (isBinding && registerToken) {
               try {
                   const res = await userProfileApi.bindSocial(registerToken);
                   if (res.success) {
                       alert('绑定成功！');
                   } else {
                       alert(res.message || '绑定失败');
                   }
               } catch (e) {
                   alert('绑定失败: ' + e.message);
               } finally {
                   sessionStorage.removeItem('binding_mode');
                   window.history.replaceState({}, document.title, window.location.pathname);
                   currentPage.value = 'user';
                   // Reload to ensure UserPage refreshes
                   window.location.reload();
               }
               return true;
           }

           // We are in register mode
           if (registerToken) {
               // Store temp token
               sessionStorage.setItem('oauth_register_token', registerToken);
               sessionStorage.setItem('oauth_nickname', nickname || '');
               // Redirect to login page with register mode
               currentPage.value = 'login';
               // Pass a flag to login component to show register-bind form?
               // Or we can just use a query param 'mode=oauth_register'
               window.location.hash = '#/login?mode=oauth_register';
               // Actually, since we use currentPage='login', we can pass props or state.
               // But LoginForm is a component.
               // We need to tell LoginForm to show OAuth Register.
               // Let's use a global event or store, or simply url param.
               // App.vue manages currentPage.
               return true;
           }
      } else {
          const error = urlParams.get('error') || new URLSearchParams(window.location.hash.split('?')[1]).get('error');
          if (error) {
              console.error('OAuth Error from provider:', error);
              alert('登录失败: ' + error);
              currentPage.value = 'login';
              return true;
          }
      }
  }
  return false;
}

// 组件挂载时检查登录状态
const bootstrapApp = async () => {
  const oauthSuccess = await handleOAuthCallback()
  if (!oauthSuccess) {
    await refreshSetupStatus()
    if (!setupRequired.value && !versionUpgradeRequired.value) {
      await checkMaintenance()
    }
    await checkLoginStatus()
  } else {
    loading.value = false
  }

  setInterval(async () => {
    if (!setupRequired.value && !versionUpgradeRequired.value) {
      await checkMaintenance()
    }
  }, 30000)
}

const handleElectronEnvPassed = async () => {
  electronEnvReady.value = true
  loading.value = true
  await bootstrapApp()
}

async function initElectronDesktop() {
  electronBooting.value = true
  try {
    const status = await window.electronAPI?.getFirstLaunchStatus?.()
    if (!status?.complete) {
      electronEnvReady.value = false
      loading.value = false
      return
    }

    electronEnvReady.value = true
    loading.value = true

    const backend = await window.electronAPI?.ensureBackendReady?.()
    if (!backend?.started) {
      electronEnvReady.value = false
      loading.value = false
      ElMessage.error(backend?.message || '后端未就绪，请重新完成环境检测')
      return
    }

    await bootstrapApp()
  } catch (error) {
    console.error('Electron 启动失败:', error)
    electronEnvReady.value = false
    loading.value = false
    ElMessage.error(error.message || '程序启动失败')
  } finally {
    electronBooting.value = false
  }
}

onMounted(async () => {
  if (isElectronDesktop) {
    await initElectronDesktop()
    return
  }
  await bootstrapApp()
})
</script>

<template>
  <div id="app">
    <!-- Electron 桌面版：Windows 环境检测 -->
    <div v-if="isElectronDesktop && electronBooting" class="loading-container">
      <div class="loading-content">
        <div class="loading-card">
          <img :src="brandLogo" class="loading-brand-icon" alt="" width="56" height="56" />
          <h2 class="loading-brand">XXG-KAMI-PRO</h2>
          <p class="loading-text">正在启动程序…</p>
        </div>
      </div>
    </div>

    <ElectronEnvCheckPage
      v-else-if="isElectronDesktop && !electronEnvReady"
      @passed="handleElectronEnvPassed"
    />

    <template v-else>
    <!-- 系统维护遮罩层 -->
    <div v-if="maintenanceData && maintenanceData.enabled && (!isLoggedIn || userInfo?.role !== 'admin') && currentPage !== 'login' && currentPage !== 'dashboard' && currentPage !== 'system-setup' && currentPage !== 'online-unbind'" class="maintenance-overlay">
      <div class="maintenance-content">
        <div class="maintenance-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
        </div>
        <h1>系统维护中</h1>
        <div class="maintenance-message">
          <p>{{ maintenanceData.content || '系统正在进行升级维护，请稍后访问。' }}</p>
          <p v-if="maintenanceData.maintenanceTime" class="maintenance-time">
            预计维护时间：{{ maintenanceData.maintenanceTime }}
          </p>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <div class="loading-bg" :style="logoMaskVars" aria-hidden="true">
        <div class="loading-bg-shape loading-bg-shape--glow"></div>
        <div class="loading-bg-shape loading-bg-shape--main"></div>
      </div>
      <div class="loading-content">
        <div class="loading-card">
          <img :src="brandLogo" class="loading-brand-icon" alt="" width="56" height="56" />
          <h2 class="loading-brand">XXG-KAMI-PRO</h2>
          <div class="loading-spinner-wrap" aria-hidden="true">
            <span class="loading-ring loading-ring--outer"></span>
            <span class="loading-ring loading-ring--inner"></span>
          </div>
          <p class="loading-text">
            正在加载<span class="loading-dots" aria-hidden="true"><span>.</span><span>.</span><span>.</span></span>
          </p>
          <div class="loading-bar-track" aria-hidden="true">
            <div class="loading-bar-fill"></div>
          </div>
          <p class="loading-hint">正在连接服务，请稍候</p>
        </div>
      </div>
    </div>
    
    <!-- 首页 -->
    <HomePage 
      v-else-if="currentPage === 'home'" 
      @show-login="showAdminLogin"
      @go-online-unbind="currentPage = 'online-unbind'"
    />

    <OnlineUnbindPage
      v-else-if="currentPage === 'online-unbind'"
      @back-home="currentPage = 'home'"
      @show-login="showLogin"
    />
    
    <!-- 首次系统初始化向导 -->
    <SystemSetupPage
      v-else-if="currentPage === 'system-setup'"
      :initial-mode="setupPageMode"
      @setup-finished="handleSetupFinished"
    />

    <!-- 登录界面 -->
    <LoginForm 
      v-else-if="currentPage === 'login'" 
      :initial-user-type="loginType"
      @login-success="handleLoginSuccess"
      @switch-to-user="loginType = 'user'"
    />
    
    <!-- 管理员界面 -->
    <Dashboard 
      v-else-if="currentPage === 'dashboard'" 
      :user-info="userInfo"
      @logout="handleLogout"
    />
    
    <!-- 普通用户界面 -->
    <UserPage 
      v-else-if="currentPage === 'user'" 
      :user-info="userInfo"
      @logout="handleLogout"
    />
    </template>

    <ElectronDesktopUpdateHost v-if="isElectronDesktop" :enabled="electronUpdateReady" />
  </div>
</template>

<style scoped>
#app {
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.loading-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 60% at 50% 0%, rgba(79, 70, 229, 0.08), transparent 55%),
    radial-gradient(ellipse 70% 50% at 80% 100%, rgba(34, 211, 238, 0.06), transparent 50%),
    #f4f6fb;
}

.loading-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background: #ffffff;
}

.loading-bg-shape {
  position: absolute;
  left: 50%;
  top: 50%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  background: linear-gradient(165deg, #22d3ee 0%, #4f46e5 48%, #c026d3 100%);
  -webkit-mask-image: var(--setup-logo-mask);
  mask-image: var(--setup-logo-mask);
  -webkit-mask-mode: luminance;
  mask-mode: luminance;
  -webkit-mask-size: contain;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  will-change: opacity, transform;
}

.loading-bg-shape--main {
  width: min(108vmin, 960px);
  filter: blur(18px) saturate(1.25);
  animation: loading-logo-fade 6s ease-in-out infinite;
}

.loading-bg-shape--glow {
  width: min(128vmin, 1120px);
  filter: blur(42px) saturate(1.35);
  animation: loading-logo-fade-glow 8s ease-in-out infinite;
  animation-delay: -2.5s;
}

@keyframes loading-logo-fade {
  0%,
  100% {
    opacity: 0.38;
    transform: translate(-50%, -50%) scale(0.97);
  }
  50% {
    opacity: 0.58;
    transform: translate(-50%, -50%) scale(1.05);
  }
}

@keyframes loading-logo-fade-glow {
  0%,
  100% {
    opacity: 0.28;
    transform: translate(-50%, -50%) scale(1.02);
  }
  50% {
    opacity: 0.48;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

.loading-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.5rem;
}

.loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(100%, 320px);
  padding: 2rem 2rem 1.75rem;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow:
    0 4px 24px rgba(15, 23, 42, 0.06),
    0 24px 48px rgba(79, 70, 229, 0.08);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  animation: loading-card-in 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes loading-card-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.loading-brand-icon {
  width: 56px;
  height: 56px;
  object-fit: contain;
  margin-bottom: 0.75rem;
  filter: drop-shadow(0 4px 12px rgba(79, 70, 229, 0.2));
  animation: loading-icon-pulse 2.4s ease-in-out infinite;
}

@keyframes loading-icon-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.04);
    opacity: 0.92;
  }
}

.loading-brand {
  margin: 0 0 1.25rem;
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  background: linear-gradient(120deg, #4f46e5 0%, #7c3aed 45%, #22d3ee 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.loading-spinner-wrap {
  position: relative;
  width: 52px;
  height: 52px;
  margin-bottom: 1.125rem;
}

.loading-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid transparent;
}

.loading-ring--outer {
  border-top-color: #4f46e5;
  border-right-color: rgba(79, 70, 229, 0.25);
  animation: spin 1.1s cubic-bezier(0.5, 0.1, 0.4, 0.9) infinite;
}

.loading-ring--inner {
  inset: 8px;
  border-bottom-color: #22d3ee;
  border-left-color: rgba(34, 211, 238, 0.3);
  animation: spin-reverse 0.85s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes spin-reverse {
  to {
    transform: rotate(-360deg);
  }
}

.loading-text {
  margin: 0 0 0.875rem;
  font-size: 0.9375rem;
  font-weight: 500;
  color: #334155;
  letter-spacing: 0.04em;
}

.loading-dots span {
  display: inline-block;
  animation: loading-dot 1.2s ease-in-out infinite;
}

.loading-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.loading-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes loading-dot {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.loading-bar-track {
  width: 100%;
  height: 4px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 0.75rem;
}

.loading-bar-fill {
  width: 42%;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #4f46e5, #22d3ee, #c026d3, #4f46e5);
  background-size: 200% 100%;
  animation: loading-bar-slide 1.6s ease-in-out infinite;
}

@keyframes loading-bar-slide {
  0% {
    transform: translateX(-120%);
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    transform: translateX(320%);
    background-position: 0% 50%;
  }
}

.loading-hint {
  margin: 0;
  font-size: 0.75rem;
  color: #94a3b8;
  letter-spacing: 0.02em;
}

.main-container {
  min-height: 100vh;
  background: #f5f5f5;
}

.app-header {
  background: white;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h1 {
  color: #333;
  margin: 0;
  font-size: 1.5rem;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.welcome-text {
  color: #666;
  font-size: 0.9rem;
}

.user-type-badge {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  margin-left: 0.5rem;
}

.user-type-badge.admin {
  background: #e3f2fd;
  color: #1976d2;
}

.user-type-badge.user {
  background: #f3e5f5;
  color: #7b1fa2;
}

.logout-button {
  padding: 0.5rem 1rem;
  background: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.3s;
}

.logout-button:hover {
  background: #d32f2f;
}

.app-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

.dashboard {
  display: grid;
  gap: 2rem;
}

.welcome-card {
  background: white;
  border-radius: var(--card-radius);
  padding: 2rem;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.welcome-card h2 {
  color: #333;
  margin-bottom: 0.5rem;
}

.welcome-card > p {
  color: #666;
  margin-bottom: 2rem;
}

.user-details {
  margin-bottom: 2rem;
}

.user-details h3 {
  color: #333;
  margin-bottom: 1rem;
  font-size: 1.1rem;
}

.info-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem;
  background: #f8f9fa;
  border-radius: 4px;
}

.info-item label {
  font-weight: 500;
  color: #555;
}

.info-item span {
  color: #333;
}

.quick-actions h3 {
  color: #333;
  margin-bottom: 1rem;
  font-size: 1.1rem;
}

.action-buttons {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.action-button {
  padding: 1rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 500;
  transition: transform 0.2s, box-shadow 0.2s;
}

.action-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    padding: 1rem;
    flex-direction: column;
    gap: 1rem;
    text-align: center;
  }
  
  .app-main {
    padding: 1rem;
  }
  
  .welcome-card {
    padding: 1.5rem;
  }
  
  .info-grid {
    grid-template-columns: 1fr;
  }
  
  .action-buttons {
    grid-template-columns: 1fr;
  }
}

/* 维护页面样式 */
.maintenance-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(243, 244, 246, 0.98); /* 不透明背景防止透视 */
  backdrop-filter: blur(10px);
  z-index: 99999; /* 确保层级最高 */
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.maintenance-content {
  background: white;
  padding: 3rem;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  max-width: 500px;
  width: 90%;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    transform: translateY(-20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.maintenance-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 1.5rem;
  color: #f59e0b;
}

.maintenance-content h1 {
  font-size: 2rem;
  font-weight: 800;
  color: #111827;
  margin-bottom: 1.5rem;
}

.maintenance-message {
  color: #4b5563;
  margin-bottom: 2rem;
  line-height: 1.6;
}

.maintenance-time {
  margin-top: 1rem;
  font-weight: 600;
  color: #4f46e5;
  background: #eef2ff;
  padding: 0.5rem;
  border-radius: 4px;
}

.maintenance-actions {
  border-top: 1px solid #e5e7eb;
  padding-top: 1.5rem;
}

.admin-login-btn {
  background: transparent;
  border: 1px solid #d1d5db;
  color: #6b7280;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
}

.admin-login-btn:hover {
  border-color: #9ca3af;
  color: #374151;
}
</style>
