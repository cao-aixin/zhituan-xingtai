<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="640px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <!-- 加载中 -->
    <div v-if="loading" class="ai-loading">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <span>AI 正在生成内容，请稍候…</span>
    </div>

    <!-- 生成成功：展示 AI 文本，支持编辑 -->
    <template v-else-if="!failed">
      <div class="ai-source">
        <el-tag :type="source === 'AI大模型' ? 'success' : 'warning'" size="small">
          生成来源：{{ source || '未知' }}
        </el-tag>
        <span class="ai-tip">以下内容由 AI 生成，您可以编辑后确认回填，不会自动提交</span>
      </div>
      <!-- 通知润色：风格切换（切换后重新生成，保留原意仅改语气） -->
      <div v-if="skillType === 'NOTICE_POLISH'" class="ai-style-row">
        <span class="ai-style-label">通知风格：</span>
        <el-radio-group :model-value="style || '正式'" size="small" @change="(v) => { emit('update:style', v); handleOpen() }">
          <el-radio-button v-for="s in styleOptions" :key="s.value" :value="s.value">
            {{ s.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <el-input
        v-model="content"
        type="textarea"
        :rows="14"
        placeholder="AI 生成内容"
      />
    </template>

    <!-- 生成失败 -->
    <el-result v-else icon="warning" title="AI智能服务暂时繁忙，请稍后重试" :sub-title="errMsg" />

    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button v-if="failed" type="primary" @click="handleOpen">重新生成</el-button>
      <el-button v-else type="primary" :disabled="loading || !content" @click="handleConfirm">
        确认回填
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 【全局复用】AI 生成弹窗组件
 * 所有 AI 场景共用本组件，禁止重复实现弹窗：
 *   - 活动文案 ACTIVITY_PLAN / 活动总结 ACTIVITY_SUMMARY / 社团分析 CLUB_ANALYSE
 *   - 活动风险巡检 RISK_INSPECT（管理员审批页）
 *   - 通知润色 NOTICE_POLISH（社团负责人，支持正式/亲切/紧急风格）
 *   - 联合活动方案 JOINT_PLAN（社团负责人，可选受邀社团）
 *
 * 使用方式（旧技能，保持兼容）：
 *   <AiGenerateDialog v-model="aiVisible" skill-type="ACTIVITY_PLAN"
 *                     :biz-id="activityId" extra-input="主题" title="AI 生成活动文案"
 *                     @confirm="onAiConfirm" />
 * 使用方式（新技能）：
 *   <!-- 风险巡检：skill-type="RISK_INSPECT" :biz-id="row.id" -->
 *   <AiGenerateDialog v-model="riskVisible" skill-type="RISK_INSPECT" :biz-id="row.id"
 *                     title="AI 风险巡检" @confirm="onRiskConfirm" />
 *   <!-- 通知润色：text=草稿正文，style=风格，支持 v-model:style 双向绑定 -->
 *   <AiGenerateDialog v-model="polishVisible" skill-type="NOTICE_POLISH"
 *                     v-model:text="noticeDraft" v-model:style="noticeStyle"
 *                     title="AI 通知润色" @confirm="onPolishConfirm" />
 *   <!-- 联合方案：extra-input=主题，club-ids=受邀社团ID数组 -->
 *   <AiGenerateDialog v-model="jointVisible" skill-type="JOINT_PLAN"
 *                     :extra-input="jointForm.title" :club-ids="jointForm.inviteClubIds"
 *                     title="AI 生成联合方案" @confirm="onJointConfirm" />
 * - 打开弹窗即自动调用后端 Agent 接口
 * - 成功：展示 AI 文本（可编辑），来源以 tag 标注（AI大模型 / 本地降级模板）
 * - 确认：把编辑后的内容通过 confirm 事件回传父组件回填表单（不自动提交任何表单）
 * - 失败：提示「AI智能服务暂时繁忙，请稍后重试」，AI 挂掉不影响表单手填提交
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { aiGenerate } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }, // 弹窗可见性
  skillType: { type: String, required: true },   // 技能类型
  bizId: { type: [Number, String], default: null }, // 业务ID（activityId / clubId）
  extraInput: { type: String, default: '' },     // 额外输入（如活动主题、提问）
  title: { type: String, default: 'AI 生成' },    // 弹窗标题
  // ===== 扩展入参（新技能使用，均为可选，不影响旧调用） =====
  text: { type: String, default: '' },           // 待润色正文（NOTICE_POLISH）
  style: { type: String, default: '' },          // 风格偏好（NOTICE_POLISH）
  clubIds: { type: Array, default: () => [] }    // 受邀社团ID列表（JOINT_PLAN）
})
const emit = defineEmits(['update:modelValue', 'update:text', 'update:style', 'confirm'])

const visible = ref(false)
const loading = ref(false)
const failed = ref(false)
const errMsg = ref('')
const content = ref('')
const source = ref('')

// v-model 双向绑定
watch(
  () => props.modelValue,
  (val) => (visible.value = val)
)
watch(visible, (val) => emit('update:modelValue', val))

// 通知润色：风格选择（正式/亲切/紧急），选择后同步回父组件
const styleOptions = [
  { label: '正式', value: '正式' },
  { label: '亲切', value: '亲切' },
  { label: '紧急', value: '紧急' }
]
const switchStyle = () => {
  emit('update:style', props.style)
}

// 弹窗打开时自动调用 AI 接口
const handleOpen = async () => {
  loading.value = true
  failed.value = false
  content.value = ''
  source.value = ''
  try {
    // 按技能精确传参：旧技能仍走原有 activityId / clubId 分支，保证向后兼容
    const payload = {
      skill: props.skillType,
      activityId: props.skillType.startsWith('ACTIVITY') ? props.bizId || undefined : undefined,
      clubId: props.skillType === 'CLUB_ANALYSE' ? props.bizId || undefined : undefined,
      extraInput: props.extraInput || undefined
    }
    // 风险巡检：activityId 传待审批活动ID
    if (props.skillType === 'RISK_INSPECT') {
      payload.activityId = props.bizId || undefined
    }
    // 通知润色：text=草稿正文，style=风格
    if (props.skillType === 'NOTICE_POLISH') {
      payload.text = props.text || undefined
      payload.style = props.style || undefined
    }
    // 联合方案：clubIds=受邀社团ID列表
    if (props.skillType === 'JOINT_PLAN') {
      payload.clubIds = props.clubIds && props.clubIds.length ? props.clubIds : undefined
    }
    const data = await aiGenerate(payload)
    content.value = data.content || ''
    source.value = data.source || ''
    loading.value = false
  } catch (e) {
    loading.value = false
    failed.value = true
    errMsg.value = e.message || ''
    ElMessage.error('AI智能服务暂时繁忙，请稍后重试')
  }
}

// 确认：把编辑后内容回传父组件
const handleConfirm = () => {
  emit('confirm', content.value)
  visible.value = false
}
</script>

<style scoped>
.ai-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 0;
  color: #909399;
}
.ai-source {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.ai-tip {
  font-size: 12px;
  color: #909399;
}
/* 通知润色的风格切换行 */
.ai-style-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.ai-style-label {
  font-size: 13px;
  color: #606266;
}
</style>
