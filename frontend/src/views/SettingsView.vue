<template>
  <div class="settings-container">
    <!-- 顶部标题 -->
    <div class="settings-header">
      <h2>⚙️ 系统设置</h2>
      <p class="settings-desc">管理您的账户、安全设置和系统配置</p>
    </div>

    <div class="settings-layout">
      <!-- 左侧分类导航 -->
      <div class="settings-nav">
        <div
          v-for="tab in visibleTabs"
          :key="tab.key"
          class="nav-item"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <div class="nav-icon">
            <component :is="tab.icon" />
          </div>
          <div class="nav-text">
            <div class="nav-title">
              {{ tab.label }}
              <span v-if="tab.badge" class="nav-badge" :class="tab.badgeClass">{{ tab.badge }}</span>
            </div>
            <div class="nav-desc">{{ tab.description }}</div>
          </div>
        </div>
      </div>

      <!-- 右侧内容区 -->
      <div class="settings-content">

        <!-- 个人资料 -->
        <div v-if="activeTab === 'profile'" class="content-panel">
          <div class="panel-card">
            <h3 class="panel-title">基本信息</h3>
            <a-form :model="profileForm" layout="vertical" class="form-narrow">
              <a-form-item label="用户名">
                <a-input :model-value="me?.username" disabled />
              </a-form-item>
              <a-form-item label="姓名">
                <a-input v-model="profileForm.fullName" placeholder="真实姓名" />
              </a-form-item>
              <a-form-item label="邮箱">
                <a-input v-model="profileForm.email" placeholder="user@example.com" />
              </a-form-item>
              <a-form-item label="手机号码">
                <a-input v-model="profileForm.phone" placeholder="13800000000" />
              </a-form-item>
              <a-button type="primary" :loading="savingProfile" @click="saveProfile">保存更改</a-button>
            </a-form>
          </div>

          <div class="panel-card">
            <h3 class="panel-title">账户信息</h3>
            <div class="account-grid">
              <div class="account-item">
                <div class="account-label">账户状态</div>
                <div><span class="tag-badge tag-green">{{ me?.status || '-' }}</span></div>
              </div>
              <div class="account-item">
                <div class="account-label">角色</div>
                <div><span class="tag-badge" :class="roleBadgeClass">{{ roleText }}</span></div>
              </div>
              <div class="account-item">
                <div class="account-label">创建时间</div>
                <div class="account-value">{{ formatTime(me?.createdTime) }}</div>
              </div>
              <div class="account-item">
                <div class="account-label">最后登录</div>
                <div class="account-value">{{ formatTime(me?.lastLoginTime) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 安全设置 -->
        <div v-if="activeTab === 'security'" class="content-panel">
          <div class="panel-card">
            <h3 class="panel-title">修改密码</h3>
            <div class="pwd-notice">
              密码要求：长度至少 6 位，建议包含大小写字母、数字与特殊字符
            </div>
            <a-form :model="passwordForm" layout="vertical" class="form-narrow">
              <a-form-item label="当前密码">
                <a-input-password v-model="passwordForm.oldPassword" placeholder="请输入当前密码" />
              </a-form-item>
              <a-form-item label="新密码">
                <a-input-password v-model="passwordForm.newPassword" placeholder="请输入新密码" />
              </a-form-item>
              <a-form-item label="确认新密码">
                <a-input-password v-model="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
              </a-form-item>
              <a-button type="primary" status="warning" :loading="savingPassword" @click="savePassword">修改密码</a-button>
            </a-form>
          </div>

          <div class="panel-card placeholder-card">
            <h3 class="panel-title">双因素认证 (2FA)</h3>
            <p class="placeholder-text">🚧 即将上线：TOTP 动态口令（扫码绑定验证器 App）</p>
          </div>

          <div class="panel-card placeholder-card">
            <h3 class="panel-title">活跃会话管理</h3>
            <p class="placeholder-text">🚧 即将上线：查看登录设备并强制下线</p>
          </div>
        </div>

        <!-- 邮件通知（ADMIN） -->
        <div v-if="activeTab === 'email' && isAdmin" class="content-panel">
          <div class="panel-card">
            <h3 class="panel-title">SMTP 邮件服务器</h3>
            <a-form :model="emailForm" layout="vertical" class="form-narrow">
              <a-form-item label="启用邮件通知">
                <a-switch v-model="emailEnabled" />
              </a-form-item>
              <a-form-item label="SMTP 服务器">
                <a-input v-model="emailForm['smtp.host']" placeholder="例如 smtp.example.com" />
              </a-form-item>
              <a-form-item label="SMTP 端口">
                <a-input-number v-model="emailForm['smtp.port']" :min="1" :max="65535" style="width: 100%" />
              </a-form-item>
              <a-form-item label="SMTP 用户名">
                <a-input v-model="emailForm['smtp.username']" placeholder="发信账号" />
              </a-form-item>
              <a-form-item label="SMTP 密码/授权码">
                <a-input-password v-model="emailForm['smtp.password']" placeholder="密码或授权码" />
              </a-form-item>
              <a-form-item label="发件人地址">
                <a-input v-model="emailForm['smtp.from']" placeholder="noreply@example.com" />
              </a-form-item>
              <a-form-item label="加密方式">
                <a-radio-group v-model="emailForm['smtp.ssl']">
                  <a-radio value="true">SSL (465)</a-radio>
                  <a-radio value="false">STARTTLS (587)</a-radio>
                </a-radio-group>
              </a-form-item>
              <a-button type="primary" :loading="savingConfig" @click="saveConfig('email')">保存设置</a-button>
            </a-form>
          </div>

          <div class="panel-card">
            <h3 class="panel-title">发送测试邮件</h3>
            <p class="test-hint">使用当前表单中的配置发送测试邮件（无需先保存），用于验证配置是否有效</p>
            <div class="test-row">
              <a-input v-model="testTo" placeholder="测试收件邮箱" class="test-input" />
              <a-button status="success" :loading="testing" @click="sendTest">发送测试</a-button>
            </div>
          </div>
        </div>

        <!-- 系统配置（ADMIN） -->
        <div v-if="activeTab === 'general' && isAdmin" class="content-panel">
          <div class="panel-card">
            <h3 class="panel-title">基本配置</h3>
            <a-form :model="generalForm" layout="vertical" class="form-narrow">
              <a-form-item label="站点名称" help="显示在系统界面与浏览器标题">
                <a-input v-model="generalForm['site.name']" placeholder="VulnArk" />
              </a-form-item>
              <a-button type="primary" :loading="savingConfig" @click="saveConfig('general')">保存设置</a-button>
            </a-form>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import {
  IconUser,
  IconLock,
  IconNotification,
  IconSettings
} from '@arco-design/web-vue/es/icon'
import { useAuthStore } from '@/stores/auth'
import { getCurrentUser, updateOwnProfile, changeOwnPassword } from '@/api/auth'
import { getSystemConfigs, saveSystemConfigs, sendTestMail } from '@/api/systemConfig'

defineOptions({ name: 'SettingsView' })

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

// ===== 页签导航 =====
interface TabDef {
  key: string
  label: string
  description: string
  icon: any
  badge?: string
  badgeClass?: string
  adminOnly?: boolean
}
const allTabs: TabDef[] = [
  { key: 'profile', label: '个人资料', description: '管理个人信息和偏好设置', icon: IconUser },
  { key: 'security', label: '安全设置', description: '密码与安全选项', icon: IconLock, badge: '重要', badgeClass: 'badge-red' },
  { key: 'email', label: '邮件通知', description: 'SMTP 服务器与测试发送', icon: IconNotification, adminOnly: true },
  { key: 'general', label: '系统配置', description: '站点名称等全局参数', icon: IconSettings, adminOnly: true }
]
const visibleTabs = computed(() => allTabs.filter(t => !t.adminOnly || isAdmin.value))
const activeTab = ref('profile')

// ===== 个人资料 =====
const me = ref<any>(null)
const savingProfile = ref(false)
const profileForm = reactive({ fullName: '', email: '', phone: '' })

const roleText = computed(() => {
  const map: Record<string, string> = { ADMIN: '管理员', MANAGER: '项目经理', ANALYST: '安全分析师', VIEWER: '查看者', USER: '用户' }
  return map[me.value?.role] || me.value?.role || '-'
})
const roleBadgeClass = computed(() => {
  const map: Record<string, string> = { ADMIN: 'tag-blue', MANAGER: 'tag-cyan', ANALYST: 'tag-orange', VIEWER: 'tag-gray' }
  return map[me.value?.role] || 'tag-gray'
})
const formatTime = (t?: string) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

const saveProfile = async () => {
  savingProfile.value = true
  try {
    await updateOwnProfile({ ...profileForm })
    Message.success('资料更新成功')
    me.value = await getCurrentUser()
    authStore.user = me.value
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingProfile.value = false
  }
}

// ===== 安全设置 =====
const savingPassword = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    Message.warning('请填写当前密码和新密码'); return
  }
  if (passwordForm.newPassword.length < 6) {
    Message.warning('新密码长度至少 6 位'); return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    Message.warning('两次输入的新密码不一致'); return
  }
  savingPassword.value = true
  try {
    await changeOwnPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    Message.success('密码修改成功，请牢记新密码')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingPassword.value = false
  }
}

// ===== 系统配置 / 邮件（ADMIN） =====
const savingConfig = ref(false)
const testing = ref(false)
const testTo = ref('')
const generalForm = reactive<Record<string, any>>({ 'site.name': '' })
const emailForm = reactive<Record<string, any>>({
  'smtp.enabled': 'false',
  'smtp.host': '',
  'smtp.port': 587,
  'smtp.username': '',
  'smtp.password': '',
  'smtp.from': '',
  'smtp.ssl': 'true'
})
const emailEnabled = computed({
  get: () => emailForm['smtp.enabled'] === 'true',
  set: (v: boolean) => { emailForm['smtp.enabled'] = v ? 'true' : 'false' }
})

const loadConfigs = async () => {
  if (!isAdmin.value) return
  try {
    const grouped = await getSystemConfigs()
    if (grouped.general) {
      Object.keys(generalForm).forEach(k => {
        if (grouped.general[k] !== undefined) generalForm[k] = grouped.general[k]
      })
    }
    if (grouped.email) {
      Object.keys(emailForm).forEach(k => {
        if (grouped.email[k] !== undefined) emailForm[k] = grouped.email[k]
      })
      emailForm['smtp.port'] = Number(emailForm['smtp.port']) || 587
    }
  } catch (e) { /* 拦截器已提示 */ }
}

const saveConfig = async (category: string) => {
  savingConfig.value = true
  try {
    const form = category === 'general' ? generalForm : emailForm
    const payload: Record<string, string> = {}
    Object.entries(form).forEach(([k, v]) => { payload[k] = String(v ?? '') })
    await saveSystemConfigs(payload)
    Message.success('设置保存成功')
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingConfig.value = false
  }
}

const sendTest = async () => {
  if (!testTo.value.trim()) { Message.warning('请填写测试收件邮箱'); return }
  if (!emailForm['smtp.host']) { Message.warning('请先填写 SMTP 服务器地址'); return }
  testing.value = true
  try {
    const smtp: Record<string, string> = {}
    Object.entries(emailForm).forEach(([k, v]) => { smtp[k] = String(v ?? '') })
    await sendTestMail(smtp, testTo.value.trim())
  } catch (e) { /* 拦截器已提示 */ } finally {
    testing.value = false
  }
}

// ===== 初始化 =====
onMounted(async () => {
  try {
    me.value = await getCurrentUser()
    authStore.user = me.value
    profileForm.fullName = me.value?.fullName || ''
    profileForm.email = me.value?.email || ''
    profileForm.phone = me.value?.phone || ''
  } catch (e) { /* 拦截器已提示 */ }
  loadConfigs()
})
</script>

<style scoped>
.settings-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.settings-header h2 {
  margin: 0 0 4px 0;
  color: var(--text-primary);
  font-size: 20px;
}

.settings-desc {
  margin: 0 0 16px 0;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
}

/* 左右布局 */
.settings-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* 左侧导航 */
.settings-nav {
  width: 280px;
  flex-shrink: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px;
  position: sticky;
  top: 80px;
}

.nav-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;
}

.nav-item:hover {
  background: var(--surface-hover);
}

.nav-item.active {
  background: var(--primary-50);
}

.nav-item.active .nav-title {
  color: var(--primary-600);
}

.nav-item.active .nav-icon {
  color: var(--primary-600);
}

.nav-icon {
  font-size: 20px;
  color: var(--text-secondary, #64748b);
  display: flex;
  align-items: center;
  padding-top: 2px;
}

.nav-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.nav-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 6px;
  vertical-align: middle;
}

.badge-red {
  background: #fee2e2;
  color: #dc2626;
}

.nav-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  margin-top: 2px;
}

/* 右侧内容 */
.settings-content {
  flex: 1;
  min-width: 0;
}

.content-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 20px 24px;
}

.panel-title {
  margin: 0 0 16px 0;
  font-size: 15px;
  color: var(--text-primary);
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.form-narrow {
  max-width: 420px;
}

/* 密码要求提示 */
.pwd-notice {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  margin-bottom: 16px;
}

/* 账户信息 */
.account-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.account-label {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  margin-bottom: 6px;
}

.account-value {
  font-size: 13px;
  color: var(--text-primary);
}

.tag-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
}

.tag-green { background: #dcfce7; color: #16a34a; }
.tag-blue { background: #dbeafe; color: #2563eb; }
.tag-cyan { background: #cffafe; color: #0891b2; }
.tag-orange { background: #ffedd5; color: #ea580c; }
.tag-gray { background: #f1f5f9; color: #64748b; }

/* 占位卡 */
.placeholder-card .placeholder-text {
  margin: 0;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  padding: 8px 0;
}

/* 测试邮件 */
.test-hint {
  margin: 0 0 12px 0;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
}

.test-row {
  display: flex;
  gap: 12px;
  max-width: 420px;
}

.test-input {
  flex: 1;
}
</style>
