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
              <a-form-item label="发件人显示名称" help="邮件客户端展示的发件人名称，如团队名">
                <a-input v-model="emailForm['smtp.display_name']" placeholder="如：XX公司 PSIRT" />
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
            <h3 class="panel-title">同步通知</h3>
            <p class="test-hint">数据同步完成后，如有变更，自动发送邮件报告（全 0 同步不发信）</p>
            <a-form layout="vertical" class="form-narrow">
              <a-form-item label="启用同步通知">
                <a-switch v-model="notifyEnabled" />
              </a-form-item>
              <a-form-item label="收件人列表" help="多个邮箱用英文逗号分隔">
                <a-input v-model="notifyForm['notify.recipients']" placeholder="admin@example.com, user2@example.com" />
              </a-form-item>
            </a-form>
            <a-button type="primary" :loading="savingConfig" @click="saveNotifyConfig">保存通知配置</a-button>
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

        <!-- 数据同步（ADMIN） -->
        <div v-if="activeTab === 'sync' && isAdmin" class="content-panel">
          <div class="panel-card">
            <h3 class="panel-title">Dependency-Track 连接</h3>
            <a-form layout="vertical" class="form-narrow">
              <a-form-item label="API 地址">
                <a-input v-model="syncForm['dtrack.url']" placeholder="http://dtrack-server:8081" />
              </a-form-item>
              <a-form-item label="API Key">
                <a-input-password v-model="syncForm['dtrack.api_key']" placeholder="Dependency-Track API Key" />
              </a-form-item>
            </a-form>
            <a-space>
              <a-button type="primary" :loading="savingSync" @click="saveSyncConfig">保存连接配置</a-button>
              <a-button status="success" :loading="testingSync" @click="testSync">测试连接</a-button>
              <span v-if="testResult" :style="{ color: testResult.ok ? '#16a34a' : '#dc2626', fontSize: '12px' }">
                {{ testResult.ok ? `连接成功（${testResult.projectCount} 个项目）` : `失败: ${testResult.message}` }}
              </span>
            </a-space>
          </div>

          <div class="panel-card">
            <h3 class="panel-title">项目映射（同步白名单）</h3>
            <p class="sync-hint">仅同步已配置映射的项目；删除映射即停止同步（已同步数据保留）</p>
            <div v-for="(m, i) in syncMapping" :key="i" class="mapping-row">
              <a-input v-model="m.dt" placeholder="DTrack 项目名" style="flex: 1" />
              <span class="mapping-arrow">→</span>
              <a-select v-model="m.asset" placeholder="VulnArk 资产" style="flex: 1" allow-clear>
                <a-option v-for="a in syncAssets" :key="a.id" :value="a.name">{{ a.name }}</a-option>
              </a-select>
              <a-button status="danger" size="small" @click="syncMapping.splice(i, 1)">删除</a-button>
            </div>
            <a-space style="margin-top: 8px">
              <a-button size="small" @click="syncMapping.push({ dt: '', asset: '' })">+ 添加映射</a-button>
              <a-button size="small" :loading="matching" @click="autoMatch">同名自动匹配</a-button>
              <a-button type="primary" size="small" :loading="savingSync" @click="saveSyncConfig">保存映射</a-button>
            </a-space>
          </div>

          <div class="panel-card">
            <h3 class="panel-title">同步策略</h3>
            <a-form layout="vertical" class="form-narrow">
              <a-form-item label="同步频率">
                <a-select v-model="syncForm['dtrack.frequency']">
                  <a-option value="hourly">每小时</a-option>
                  <a-option value="daily">每天</a-option>
                  <a-option value="off">关闭（仅手动）</a-option>
                </a-select>
              </a-form-item>
              <a-form-item label="自动置已解决">
                <a-switch v-model="autoResolve" />
                <div class="sync-hint" style="margin-top: 4px">
              当组件升级后 DTrack 不再报告某 CVE 时，自动把它标记为"已解决"（附自动备注）。<br/>
              仅作用于"待处理"且由同步创建的漏洞——人工录入、Excel 导入、以及已手动改过状态的漏洞一律不受影响。
            </div>
              </a-form-item>
          <a-form-item label="EPSS/KEV 自动补全">
            <a-switch v-model="autoEnrich" />
            <div class="sync-hint" style="margin-top: 4px">
              同步后从 FIRST（EPSS）与 CISA（KEV 目录）官方源补全风险字段——DTrack 不提供这两个数据。<br/>
              字段对所有漏洞补全；Band 重算仍仅限同步创建的漏洞（人工分析成果不受影响）。
            </div>
          </a-form-item>
              <a-form-item label="刷新风险字段">
                <a-switch v-model="refreshFields" />
                <div class="sync-hint" style="margin-top: 4px">
              比对并刷新所有已存在 CVE 的 CVSS 级别/分数/EPSS/KEV，并按 CRA 规则重算 Band——
              包括人工/Excel 导入的数据，保证资产体现最新风险状态。<br/>
              人工判定与分析内容（分析状态/处置状态/负责人/解决方案）永不覆盖。
            </div>
              </a-form-item>
            </a-form>
            <a-button type="primary" :loading="savingSync" @click="saveSyncConfig">保存策略</a-button>
          </div>

          <div class="panel-card">
            <h3 class="panel-title">同步状态</h3>
            <a-space>
              <a-button type="primary" status="warning" :loading="syncRunning" :disabled="syncRunning" @click="triggerSync">
                {{ syncRunning ? '同步进行中...' : '立即同步' }}
              </a-button>
              <a-button @click="loadSyncStatus">刷新状态</a-button>
            </a-space>
            <div v-if="syncLogs.length" class="sync-logs">
              <div v-for="log in syncLogs" :key="log.id" class="sync-log-entry">
                <div class="sync-log-item" @click="toggleLogDetail(log)">
                  <span class="sync-log-time">{{ formatSyncTime(log.syncTime) }}</span>
                  <a-tag size="small" :color="log.triggerType === 'AUTO' ? 'blue' : 'green'">
                    {{ log.triggerType === 'AUTO' ? '定时' : '手动' }}
                  </a-tag>
                  <span class="sync-log-summary">
                    新增 {{ log.added }} · 更新 {{ log.updated }} · 置已解决 {{ log.resolved }} · 失败 {{ log.failed }}
                  </span>
                  <span v-if="parseLogDetail(log)" class="sync-log-toggle">{{ log._open ? '收起' : '明细' }}</span>
                </div>
<div v-if="log._open && parseLogDetail(log)" class="sync-log-detail">
                  <template v-if="parseLogDetail(log).assets">
                    <div v-for="(block, assetName) in parseLogDetail(log).assets" :key="assetName" class="asset-block">
                      <div class="asset-block-title">{{ assetName }}<span class="asset-block-summary">{{ block.summary }}</span></div>
                      <div v-if="block.added?.length">
                        <div class="detail-group-title">新增（{{ block.added.length }}）</div>
                        <div class="detail-band-summary">Band 分布：{{ bandSummary(block.added) }}</div>
                        <div class="detail-head">
                          <span>CVE 编号</span><span>CVSS 级别</span><span>Band</span><span>标题</span>
                        </div>
                        <div v-for="(d, k) in block.added" :key="'a'+k" class="detail-row">
                          <span>{{ d.cve }}</span><span>{{ d.cvssLevel || '-' }}</span><span>{{ d.band || d.severity || '-' }}</span><span>{{ d.title }}</span>
                        </div>
                      </div>
                      <div v-if="block.resolved?.length">
                        <div class="detail-group-title">置已解决（{{ block.resolved.length }}）</div>
                        <div class="detail-head">
                          <span>CVE 编号</span><span>标题</span><span></span><span></span>
                        </div>
                        <div v-for="(d, k) in block.resolved" :key="'r'+k" class="detail-row">
                          <span>{{ d.cve }}</span><span>{{ d.title }}</span><span></span><span></span>
                        </div>
                      </div>
                      <div v-if="block.updated?.length">
                        <div class="detail-group-title">更新（{{ block.updated.length }}）</div>
                        <div class="detail-band-summary">Band 分布：{{ bandSummary(block.updated) }}</div>
                        <div class="detail-head cols-update">
                          <span>CVE 编号</span><span>CVSS 级别</span><span>分数</span><span>Band</span><span>触发信号</span>
                        </div>
                        <div v-for="(d, k) in block.updated" :key="'u'+k" class="detail-row cols-update">
                          <span>{{ d.cve }}</span><span>{{ d.cvssLevel || '-' }}</span><span>{{ d.cvssScore || '-' }}</span><span>{{ d.band || d.severity || '-' }}</span><span style="color: var(--primary-600)">{{ d.reason || '-' }}</span>
                        </div>
                      </div>
                      <div v-if="!block.added?.length && !block.updated?.length && !block.resolved?.length" class="detail-row">
                        <span>无变更</span><span></span><span></span><span></span>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <div v-if="parseLogDetail(log).added?.length">
                      <div class="detail-group-title">新增（{{ parseLogDetail(log).added.length }}）</div>
                      <div class="detail-band-summary">Band 分布：{{ bandSummary(parseLogDetail(log).added) }}</div>
                      <div class="detail-head">
                        <span>资产</span><span>CVE 编号</span><span>CVSS 级别</span><span>Band</span><span>标题</span>
                      </div>
                      <div v-for="(d, k) in parseLogDetail(log).added" :key="'a2'+k" class="detail-row">
                        <span>{{ d.asset || '-' }}</span><span>{{ d.cve }}</span><span>{{ d.cvssLevel || '-' }}</span><span>{{ d.band || d.severity || '-' }}</span><span>{{ d.title }}</span>
                      </div>
                    </div>
                    <div v-if="parseLogDetail(log).resolved?.length">
                      <div class="detail-group-title">置已解决（{{ parseLogDetail(log).resolved.length }}）</div>
                      <div class="detail-head">
                        <span>资产</span><span>CVE 编号</span><span>标题</span><span></span><span></span>
                      </div>
                      <div v-for="(d, k) in parseLogDetail(log).resolved" :key="'r2'+k" class="detail-row">
                        <span>{{ d.asset || '-' }}</span><span>{{ d.cve }}</span><span>{{ d.title }}</span><span></span><span></span>
                      </div>
                    </div>
                    <div v-if="parseLogDetail(log).updated?.length">
                      <div class="detail-group-title">更新（{{ parseLogDetail(log).updated.length }}）</div>
                      <div class="detail-band-summary">Band 分布：{{ bandSummary(parseLogDetail(log).updated) }}</div>
                      <div class="detail-head cols-update-old">
                        <span>资产</span><span>CVE 编号</span><span>CVSS 级别</span><span>分数</span><span>Band</span><span>触发信号</span>
                      </div>
                      <div v-for="(d, k) in parseLogDetail(log).updated" :key="'u2'+k" class="detail-row cols-update-old">
                        <span>{{ d.asset || '-' }}</span><span>{{ d.cve }}</span><span>{{ d.cvssLevel || '-' }}</span><span>{{ d.cvssScore || '-' }}</span><span>{{ d.band || d.severity || '-' }}</span><span style="color: var(--primary-600)">{{ d.reason || '-' }}</span>
                      </div>
                    </div>
                  </template>
                  <div v-if="parseLogDetail(log).descSupplemented" class="detail-row" style="color:#94a3b8">
                    <span>描述补全</span>
                    <span>{{ parseLogDetail(log).descSupplemented }} 条（DTrack 官方漏洞描述批量补充，不计入更新）</span>
                    <span></span><span></span>
                  </div>
                  <div v-if="parseLogDetail(log).enrich">
                    <div class="detail-group-title">EPSS/KEV 补全（全库）</div>
                    <div class="detail-row" v-if="parseLogDetail(log).enrich.error" style="color:#dc2626">
                      <span>失败</span><span>{{ parseLogDetail(log).enrich.error }}</span><span></span><span></span>
                    </div>
                    <div class="detail-row" v-else>
                      <span>结果</span>
                      <span>EPSS 更新 {{ parseLogDetail(log).enrich.epssUpdated ?? 0 }} · KEV 更新 {{ parseLogDetail(log).enrich.kevUpdated ?? 0 }} · Band 重算 {{ parseLogDetail(log).enrich.bandRecalced ?? 0 }}（KEV 目录 {{ parseLogDetail(log).enrich.kevCatalogSize ?? '-' }} 条）</span>
                      <span></span><span></span>
                    </div>
                    <template v-if="parseLogDetail(log).enrich.bandRecalcedList?.length">
                      <div class="detail-head cols-brecalc">
                        <span>资产</span><span>CVE 编号</span><span>原 Band</span><span>触发信号</span><span>新 Band</span>
                      </div>
                      <div v-for="(d, k) in parseLogDetail(log).enrich.bandRecalcedList" :key="'b2'+k" class="detail-row cols-brecalc">
                        <span>{{ d.asset || '-' }}</span><span>{{ d.cve }}</span><span>{{ d.from }}</span><span style="color: #94a3b8">{{ d.reason || '-' }}</span><span style="color: var(--primary-600)">{{ d.to }}</span>
                      </div>
                    </template>
                  </div>
                </div>
              </div>
            </div>
            <p v-else class="sync-hint">暂无同步记录</p>
          </div>
        </div>

      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import {
  IconUser,
  IconLock,
  IconNotification,
  IconSettings,
  IconSync
} from '@arco-design/web-vue/es/icon'
import { useAuthStore } from '@/stores/auth'
import { getCurrentUser, updateOwnProfile, changeOwnPassword } from '@/api/auth'
import { getSystemConfigs, saveSystemConfigs, sendTestMail } from '@/api/systemConfig'
import { testSyncConnection, runSync, getSyncStatus, getDtrackProjects } from '@/api/sync'

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
  { key: 'general', label: '系统配置', description: '站点名称等全局参数', icon: IconSettings, adminOnly: true },
  { key: 'sync', label: '数据同步', description: 'Dependency-Track 定期同步', icon: IconSync, adminOnly: true }
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
  'smtp.display_name': '',
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

// ===== 数据同步（DTrack）=====
const savingSync = ref(false)
const testingSync = ref(false)
const testResult = ref<{ ok: boolean; projectCount?: number; message?: string } | null>(null)
const syncMapping = ref<{ dt: string; asset: string }[]>([])
const syncAssets = ref<any[]>([])
const matching = ref(false)
const syncRunning = ref(false)
const syncLogs = ref<any[]>([])

const syncForm = reactive<Record<string, any>>({
  'dtrack.url': '',
  'dtrack.api_key': '',
  'dtrack.mapping': '{}',
  'dtrack.frequency': 'daily',
  'dtrack.auto_resolve': 'true',
  'dtrack.refresh_fields': 'true',
  'dtrack.enrich': 'true',
  'notify.enabled': 'false',
  'notify.recipients': ''
})
const autoResolve = computed({
  get: () => syncForm['dtrack.auto_resolve'] === 'true',
  set: (v: boolean) => { syncForm['dtrack.auto_resolve'] = v ? 'true' : 'false' }
})
const notifyEnabled = ref(false)
const notifyForm = reactive<Record<string, any>>({
  'notify.recipients': ''
})
const saveNotifyConfig = async () => {
  savingConfig.value = true
  try {
    const payload: Record<string, string> = {
      'notify.enabled': notifyEnabled.value ? 'true' : 'false',
      'notify.recipients': notifyForm['notify.recipients'] || ''
    }
    await saveSystemConfigs(payload)
    Message.success('通知配置保存成功')
  } catch (e) { /* 拦截器已提示 */ } finally { savingConfig.value = false }
}
const autoEnrich = computed({
  get: () => syncForm['dtrack.enrich'] === 'true',
  set: (v: boolean) => { syncForm['dtrack.enrich'] = v ? 'true' : 'false' }
})
const refreshFields = computed({
  get: () => syncForm['dtrack.refresh_fields'] === 'true',
  set: (v: boolean) => { syncForm['dtrack.refresh_fields'] = v ? 'true' : 'false' }
})

const initSyncTab = async () => {
  try {
    const grouped = await getSystemConfigs()
    if (grouped.dtrack) {
      Object.keys(syncForm).forEach(k => {
        if (grouped.dtrack[k] !== undefined) syncForm[k] = grouped.dtrack[k]
      })
    }
    try {
      const parsed = JSON.parse(syncForm['dtrack.mapping'] || '[]')
      if (Array.isArray(parsed)) {
        syncMapping.value = parsed
      } else if (parsed && typeof parsed === 'object') {
        syncMapping.value = Object.entries(parsed).map(([dt, asset]) => ({ dt, asset }))
      } else {
        syncMapping.value = []
      }
    } catch { syncMapping.value = [] }
    // 加载通知配置
    try {
      if (grouped.notify) {
        notifyEnabled.value = grouped.notify['notify.enabled'] === 'true'
        if (grouped.notify['notify.recipients'] !== undefined) {
          notifyForm['notify.recipients'] = grouped.notify['notify.recipients']
        }
      }
    } catch { /* 忽略 */ }
  } catch { /* 忽略 */ }
  loadSyncAssets()
  loadSyncStatus()
}

const loadSyncAssets = async () => {
  try {
    const { assetApi } = await import('@/api/asset')
    const r: any = await assetApi.getAssets({ page: 0, size: 100 })
    syncAssets.value = (r && (r.content || r)) || []
  } catch { syncAssets.value = [] }
}

const saveSyncConfig = async () => {
  savingSync.value = true
  try {
    const pairs = syncMapping.value.filter(m => m.dt && m.asset)
    const obj: Record<string, string> = {}
    pairs.forEach(m => { obj[m.dt] = m.asset })
    syncForm['dtrack.mapping'] = JSON.stringify(obj)
    const payload: Record<string, string> = {}
    Object.entries(syncForm).forEach(([k, v]) => { payload[k] = String(v ?? '') })
    await saveSystemConfigs(payload)
    Message.success('同步配置保存成功')
  } catch (e) { /* 拦截器已提示 */ } finally { savingSync.value = false }
}

const testSync = async () => {
  testingSync.value = true
  testResult.value = null
  try {
    await saveSyncConfig()
    testResult.value = await testSyncConnection()
  } catch (e) { /* 拦截器已提示 */ } finally { testingSync.value = false }
}

const autoMatch = async () => {
  matching.value = true
  try {
    const names = await getDtrackProjects()
    const assetNames = syncAssets.value.map(a => a.name)
    const matched = names.filter(n => assetNames.includes(n))
    if (!matched.length) { Message.warning('没有找到两边同名的项目/资产'); return }
    matched.forEach(n => {
      if (!syncMapping.value.some(m => m.dt === n)) syncMapping.value.push({ dt: n, asset: n })
    })
    Message.success(`匹配到 ${matched.length} 个同名对，请确认后保存`)
  } catch (e) { /* 拦截器已提示 */ } finally { matching.value = false }
}

const loadSyncStatus = async () => {
  try {
    const r = await getSyncStatus()
    syncRunning.value = r.running
    syncLogs.value = r.logs || []
  } catch { /* 忽略 */ }
}

let syncPollTimer: any = null
const triggerSync = async () => {
  try {
    await runSync()
    Message.success('同步已启动')
    // 持续轮询直到同步完成（全量比对需 1-3 分钟）
    if (syncPollTimer) clearInterval(syncPollTimer)
    let polls = 0
    syncPollTimer = setInterval(async () => {
      await loadSyncStatus()
      polls++
      if (!syncRunning.value || polls > 40) {
        clearInterval(syncPollTimer)
        syncPollTimer = null
        if (!syncRunning.value) Message.success('同步已完成')
      }
    }, 5000)
  } catch (e) { /* 拦截器已提示 */ }
}

const formatSyncTime = (t: string) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

const toggleLogDetail = (log: any) => { log._open = !log._open }
// Band 分布统计：按 EMERGENCY>CRITICAL>HIGH>MEDIUM>LOW 顺序输出有值的级别
const bandSummary = (list: any[]): string => {
  if (!list || !list.length) return '-'
  const order = ['EMERGENCY', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  const counts: Record<string, number> = {}
  list.forEach(d => {
    const b = d.band || d.severity || '-'
    counts[b] = (counts[b] || 0) + 1
  })
  return order.filter(k => counts[k]).map(k => `${k} ${counts[k]}`).join(' · ') || '-'
}
const parseLogDetail = (log: any): any => {
  if (log._parsed !== undefined) return log._parsed
  try {
    const d = JSON.parse(log.detail || '')
    log._parsed = (d && (d.assets || d.added || d.resolved || d.updated)) ? d : null
  } catch { log._parsed = null }
  return log._parsed
}

// ===== 初始化 =====
watch(activeTab, (tab) => {
  if (tab === 'sync' && isAdmin.value) initSyncTab()
})

onMounted(async () => {
  try {
    me.value = await getCurrentUser()
    authStore.user = me.value
    profileForm.fullName = me.value?.fullName || ''
    profileForm.email = me.value?.email || ''
    profileForm.phone = me.value?.phone || ''
  } catch (e) { /* 拦截器已提示 */ }
  loadConfigs()
  // 覆盖直接刷新到 sync 页签或 URL 带 ?tab=sync 的场景
  if (isAdmin.value) initSyncTab()
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
.mapping-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.mapping-arrow { color: #94a3b8; }
.sync-hint { font-size: 12px; color: #94a3b8; margin: 4px 0; }
.sync-logs { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
.sync-log-entry { border-bottom: 1px solid var(--border); padding-bottom: 6px; }
.sync-log-item { display: flex; align-items: center; gap: 10px; font-size: 13px; cursor: pointer; }
.sync-log-item:hover { background: var(--surface-hover); }
.sync-log-toggle { color: var(--primary-600); font-size: 12px; margin-left: auto; }
.sync-log-detail { margin: 6px 0 8px 0; padding: 10px; background: var(--surface-hover); border-radius: 6px; max-height: 260px; overflow-y: auto; font-size: 12px; }
.detail-group-title { font-weight: 600; margin: 6px 0 4px; color: var(--text-primary); }
.detail-head, .detail-row { display: grid; grid-template-columns: 110px 150px 100px 90px 1fr; gap: 8px; padding: 2px 12px; }
.detail-head { color: var(--text-primary); font-weight: 600; border-bottom: 1px solid var(--border); margin-bottom: 4px; }
.asset-block { margin-bottom: 12px; }
.asset-block-title { font-weight: 600; color: var(--primary-600); margin: 8px 0 4px; font-size: 13px; }
.asset-block-summary { font-weight: 400; color: var(--text-secondary, #64748b); margin-left: 10px; font-size: 12px; }
.cols-update { grid-template-columns: 150px 110px 100px 140px minmax(170px, 1fr) !important; }
.cols-update-old { grid-template-columns: 100px 140px 100px 90px 130px minmax(160px, 1fr) !important; }
.cols-brecalc { grid-template-columns: 100px 140px 90px minmax(160px, 1fr) 100px !important; }
.detail-head span, .detail-row span { min-width: 0; word-break: break-word; white-space: normal; }
.detail-band-summary { color: var(--primary-600); font-size: 12px; margin: 2px 0 6px 12px; }
.detail-row { color: var(--text-secondary, #64748b); }
.sync-log-time { color: #94a3b8; font-size: 12px; min-width: 140px; }
.sync-log-summary { color: var(--text-primary); }
.detail-head.two, .detail-row.two { grid-template-columns: 150px 1fr; }
</style>
