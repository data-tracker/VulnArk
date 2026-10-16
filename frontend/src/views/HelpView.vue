<template>
  <div class="help-container">
    <div class="help-header">
      <h1>帮助中心</h1>
      <p class="help-subtitle">了解平台的严重程度评级、分析流程与数据同步机制</p>
    </div>

    <div class="help-card">
      <a-tabs v-model:active-key="activeTab" default-active-key="severity">
        <!-- 严重程度评级 -->
        <a-tab-pane key="severity" title="严重程度评级">
          <div class="tab-body">
            <h3>什么是严重程度？</h3>
            <p>每条漏洞的严重程度由系统根据三个风险信号<b>自动评定</b>，无需人工设置：</p>
            <table class="help-table">
              <tr><th>信号</th><th>含义</th><th>数据来源</th></tr>
              <tr><td><b>CVSS 评分</b></td><td>技术严重性，0-10 分</td><td>NVD</td></tr>
              <tr><td><b>EPSS 评分</b></td><td>未来 30 天内被利用的概率（0-1）</td><td>FIRST 组织</td></tr>
              <tr><td><b>KEV</b></td><td>是否为已知在野利用漏洞</td><td>CISA 目录</td></tr>
            </table>

            <h3>评级标准</h3>
            <div class="band-list">
              <div class="band-item" v-for="b in bands" :key="b.name">
                <span class="band-badge" :style="{ background: b.color, color: '#1f2937' }">{{ b.label }}</span>
                <span class="band-desc">{{ b.desc }}</span>
              </div>
            </div>

            <div class="help-note">
              <b>💡 评分变化会自动更新评级</b>：系统每天从 FIRST 和 CISA 获取最新的 EPSS 与 KEV 数据，
              并自动重新计算所有漏洞的严重程度。例如某条漏洞的 EPSS 从 0.3 升至 0.6，
              其严重程度可能从"中危"自动升至"高危"。
            </div>
          </div>
        </a-tab-pane>

        <!-- 分析与状态 -->
        <a-tab-pane key="analysis" title="分析与状态">
          <div class="tab-body">
            <h3>两个独立的维度</h3>
            <p>漏洞管理中最重要的两个概念，很多人容易混淆：</p>
            <table class="help-table">
              <tr><th></th><th>分析</th><th>状态</th></tr>
              <tr><td><b>回答什么</b></td><td>这个漏洞是真的吗？</td><td>处置到哪一步了？</td></tr>
              <tr><td><b>取值</b></td>
                <td>未分析 / 受影响 / 不受影响 / 误报</td>
                <td>待处理 → 处理中 → 已解决 → 已关闭</td>
              </tr>
              <tr><td><b>谁来确定</b></td><td>人工分析判定</td><td>人工推进（或自动置已解决）</td></tr>
              <tr><td><b>会被同步覆盖吗</b></td><td>❌ 不会</td><td>仅"待处理"且 DTrack 不再报告时自动置为"已解决"</td></tr>
            </table>

            <h3>典型工作流</h3>
            <div class="workflow-steps">
              <div class="step">📥 <b>导入/同步</b>：新漏洞进入系统，分析=未分析，状态=待处理</div>
              <div class="step">🔍 <b>分析</b>：确认漏洞是否真实影响 → 受影响 / 不受影响 / 误报</div>
              <div class="step">👤 <b>分配</b>：指定负责人跟进</div>
              <div class="step">🔧 <b>处置</b>：升级组件、修改配置 → 状态改为处理中</div>
              <div class="step">✅ <b>关闭</b>：确认修复 → 已解决 → 已关闭</div>
            </div>

            <div class="help-note">
              <b>💡 分析状态不会被自动修改</b>：无论数据同步如何运行，您对漏洞的分析判定
              （受影响/不受影响/误报）永远不会被系统覆盖。
            </div>
          </div>
        </a-tab-pane>

        <!-- 数据同步 -->
        <a-tab-pane key="sync" title="数据同步">
          <div class="tab-body">
            <h3>同步机制</h3>
            <table class="help-table">
              <tr><th>项目</th><th>说明</th></tr>
              <tr><td><b>同步频率</b></td><td>每天凌晨 2:00 自动执行，也可手动触发</td></tr>
              <tr><td><b>数据来源</b></td><td>Dependency-Track（漏洞清单 + CVSS）+ FIRST（EPSS）+ CISA（KEV）</td></tr>
              <tr><td><b>通知方式</b></td><td>同步完成后如有变更，自动发送邮件报告</td></tr>
            </table>

            <h3>每次同步会做什么</h3>
            <div class="sync-actions">
              <div class="action add">✚ <b>新增</b>：Dependency-Track 新发现的 CVE 自动录入</div>
              <div class="action update">⟳ <b>更新</b>：CVSS/EPSS/KEV 有变化时刷新并重算严重程度</div>
              <div class="action resolve">✓ <b>置已解决</b>：DTrack 不再报告的漏洞自动标记为已解决</div>
              <div class="action desc">📝 <b>补全</b>：补充 DTrack 官方漏洞描述</div>
            </div>

            <h3>不会覆盖的内容</h3>
            <div class="protected-list">
              <div class="protected">🔒 分析状态（受影响/不受影响/误报）</div>
              <div class="protected">🔒 处置状态（处理中/已解决/已关闭等人工状态）</div>
              <div class="protected">🔒 负责人</div>
              <div class="protected">🔒 解决方案与分析备注</div>
            </div>
          </div>
        </a-tab-pane>

        <!-- 筛选技巧 -->
        <a-tab-pane key="tips" title="筛选技巧">
          <div class="tab-body">
            <h3>常用筛选组合</h3>
            <table class="help-table">
              <tr><th>场景</th><th>筛选条件</th></tr>
              <tr><td>优先处置队列</td><td>严重程度=紧急+严重（多选）+ 分析=未分析</td></tr>
              <tr><td>我的待办</td><td>负责人=自己的名字 + 状态=待处理</td></tr>
              <tr><td>在野利用风险</td><td>KEV=Yes，或 EPSS≥0.5</td></tr>
              <tr><td>误报清单</td><td>分析=误报</td></tr>
              <tr><td>某资产的全部漏洞</td><td>关联资产=目标资产（或从资产页点击漏洞数）</td></tr>
            </table>

            <h3>自定义筛选器</h3>
            <p>筛选区右侧有<b>"自定义筛选"</b>按钮，可自由增减筛选字段：
              漏洞标题、严重程度、状态、分析、关联资产、负责人、CVE 编号、
              CVSS 级别/评分、EPSS 评分、KEV、分类 等 12 个字段。
              配置自动保存在浏览器中。</p>

            <div class="help-note">
              <b>💡 提示</b>：严重程度支持多选（如同时勾选"紧急"和"严重"），
              可组合出最适合自己的工作队列。
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineOptions({ name: 'HelpView' })
const activeTab = ref('severity')

const bands = [
  { label: '紧急', name: 'EMERGENCY', color: '#FFC7CE', desc: '已知在野利用（KEV），或 CVSS≥9 且 EPSS>0.5（双重高危信号）' },
  { label: '严重', name: 'CRITICAL', color: '#FFC000', desc: 'CVSS≥9 且 EPSS≤0.5 且非 KEV（技术严重但利用概率较低）' },
  { label: '高危', name: 'HIGH', color: '#FFEB9C', desc: 'CVSS 7.0-8.9，或 EPSS>0.5（利用概率高）' },
  { label: '中危', name: 'MEDIUM', color: '#BDD7EE', desc: 'CVSS 4.0-6.9 且 EPSS≤0.5 且非 KEV' },
  { label: '低危', name: 'LOW', color: '#C6EFCE', desc: 'CVSS<4.0' }
]
</script>

<style scoped>
.help-container { padding: 24px; max-width: 900px; margin: 0 auto; }
.help-header h1 { margin: 0 0 4px 0; font-size: 22px; color: var(--text-primary); }
.help-subtitle { margin: 0 0 16px 0; color: var(--text-secondary, #64748b); font-size: 14px; }
.help-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 20px 24px; }
.tab-body { padding: 12px 4px; max-width: 720px; }
.tab-body h3 { margin: 18px 0 10px 0; font-size: 15px; color: var(--text-primary); }
.tab-body p { font-size: 14px; color: var(--text-secondary, #475569); line-height: 1.7; }
.help-table { width: 100%; border-collapse: collapse; font-size: 13px; margin: 8px 0; }
.help-table th { background: #f1f5f9; padding: 8px 12px; text-align: left; border: 1px solid #e2e8f0; color: var(--text-primary); font-weight: 600; }
.help-table td { padding: 8px 12px; border: 1px solid #e2e8f0; color: var(--text-secondary, #475569); }
.band-list { display: flex; flex-direction: column; gap: 10px; margin: 12px 0; }
.band-item { display: flex; align-items: center; gap: 12px; }
.band-badge { display: inline-block; padding: 4px 14px; border-radius: 4px; font-size: 13px; font-weight: 600; min-width: 60px; text-align: center; flex-shrink: 0; }
.band-desc { font-size: 13px; color: var(--text-secondary, #475569); }
.help-note { background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; padding: 12px 16px; font-size: 13px; color: #92400e; margin: 14px 0; line-height: 1.6; }
.workflow-steps { display: flex; flex-direction: column; gap: 8px; margin: 12px 0; }
.step { background: var(--surface-hover, #f8fafc); border-radius: 6px; padding: 10px 16px; font-size: 13px; color: var(--text-secondary, #475569); }
.sync-actions { display: flex; flex-direction: column; gap: 6px; margin: 10px 0; }
.action { padding: 8px 14px; font-size: 13px; border-left: 3px solid transparent; }
.action.add { border-left-color: #dc2626; }
.action.update { border-left-color: #ea580c; }
.action.resolve { border-left-color: #16a34a; }
.action.desc { border-left-color: #3b82f6; }
.protected-list { display: flex; flex-wrap: wrap; gap: 8px; margin: 10px 0; }
.protected { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 6px; padding: 6px 14px; font-size: 13px; color: #166534; }
</style>
