<template>
  <div class="settings-container">
    <div class="settings-header">
      <h2>系统设置</h2>
      <p class="settings-desc">全局配置管理，仅管理员可访问</p>
    </div>

    <div class="settings-card">
      <a-tabs v-model:active-key="activeTab" default-active-key="general">
        <!-- 基本配置 -->
        <a-tab-pane key="general" title="基本配置">
          <div class="tab-body">
            <a-form :model="generalForm" layout="vertical" class="form-narrow">
              <a-form-item label="站点名称" help="显示在浏览器标签和系统界面">
                <a-input v-model="generalForm['site.name']" placeholder="VulnArk" />
              </a-form-item>
            </a-form>
            <div class="form-actions">
              <a-button type="primary" :loading="saving" @click="saveConfig('general')">保存基本配置</a-button>
            </div>
          </div>
        </a-tab-pane>

        <!-- 邮件通知 -->
        <a-tab-pane key="email" title="邮件通知">
          <div class="tab-body">
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
            </a-form>
            <div class="form-actions">
              <a-button type="primary" :loading="saving" @click="saveConfig('email')">保存邮件配置</a-button>
            </div>

            <a-divider />
            <div class="test-section">
              <h4>发送测试邮件</h4>
              <p class="test-hint">使用当前表单中的配置发送测试邮件（无需先保存），用于验证配置是否有效</p>
              <div class="test-row">
                <a-input v-model="testTo" placeholder="测试收件邮箱" class="test-input" />
                <a-button status="success" :loading="testing" @click="sendTest">发送测试</a-button>
              </div>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { getSystemConfigs, saveSystemConfigs, sendTestMail } from '@/api/systemConfig'

defineOptions({ name: 'SettingsView' })

const activeTab = ref('general')
const saving = ref(false)
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
  } catch (e) {
    // 拦截器已提示
  }
}

const saveConfig = async (category: string) => {
  saving.value = true
  try {
    const form = category === 'general' ? generalForm : emailForm
    const payload: Record<string, string> = {}
    Object.entries(form).forEach(([k, v]) => { payload[k] = String(v ?? '') })
    await saveSystemConfigs(payload)
    Message.success('系统配置保存成功')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const sendTest = async () => {
  if (!testTo.value.trim()) {
    Message.warning('请填写测试收件邮箱')
    return
  }
  if (!emailForm['smtp.host']) {
    Message.warning('请先填写 SMTP 服务器地址')
    return
  }
  testing.value = true
  try {
    const smtp: Record<string, string> = {}
    Object.entries(emailForm).forEach(([k, v]) => { smtp[k] = String(v ?? '') })
    await sendTestMail(smtp, testTo.value.trim())
  } catch (e) {
    // 拦截器已提示
  } finally {
    testing.value = false
  }
}

onMounted(loadConfigs)
</script>

<style scoped>
.settings-container {
  padding: 24px;
  max-width: 960px;
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

.settings-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 20px 24px;
}

.tab-body {
  padding: 12px 4px;
}

.form-narrow {
  max-width: 460px;
}

.form-actions {
  margin-top: 8px;
}

.test-section h4 {
  margin: 0 0 4px 0;
  color: var(--text-primary);
}

.test-hint {
  margin: 0 0 12px 0;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
}

.test-row {
  display: flex;
  gap: 12px;
  max-width: 460px;
}

.test-input {
  flex: 1;
}
</style>
