<template>
  <div>
    <el-card shadow="never">
      <template #header><b>新建活动</b></template>
      <el-tabs v-model="activeTab">
        <!-- ============ Tab 1：普通活动 ============ -->
        <el-tab-pane label="普通活动" name="normal">
          <el-form
            ref="normalFormRef"
            :model="normalForm"
            :rules="rules"
            label-width="100px"
            style="max-width: 680px"
          >
            <el-form-item label="活动名称" prop="title">
              <el-input v-model="normalForm.title" placeholder="请输入活动名称" />
            </el-form-item>
            <el-form-item label="活动地点" prop="location">
              <el-input v-model="normalForm.location" placeholder="请输入活动地点" />
            </el-form-item>
            <el-form-item label="活动时间" prop="timeRange" required>
              <el-date-picker
                v-model="normalForm.timeRange"
                type="datetimerange"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="名额" prop="capacity">
              <el-input-number v-model="normalForm.capacity" :min="1" :max="9999" />
            </el-form-item>
            <el-form-item label="活动背景" prop="background">
              <el-input
                v-model="normalForm.background"
                type="textarea"
                :rows="3"
                placeholder="非必填：活动背景说明，可由 AI 一键分段填入"
              />
            </el-form-item>
            <el-form-item label="活动流程" prop="flow">
              <el-input
                v-model="normalForm.flow"
                type="textarea"
                :rows="4"
                placeholder="非必填：活动流程安排，可由 AI 一键分段填入"
              />
            </el-form-item>
            <el-form-item label="宣传口号" prop="slogan">
              <el-input
                v-model="normalForm.slogan"
                :rows="2"
                type="textarea"
                placeholder="非必填：活动宣传口号，可由 AI 一键分段填入"
              />
              <!-- AI 生成整段文案后，按【活动背景】【活动流程】【宣传口号】自动分段回填到上方三个输入框 -->
              <div class="ai-btn-row">
                <el-button type="primary" plain size="small" @click="openAi('normal')">
                  <el-icon><MagicStick /></el-icon>
                  AI 生成文案（一键分段填入）
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveNormal">
                {{ normalForm.id ? '保存草稿修改' : '保存为草稿' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ============ Tab 2：创建联合活动 ============ -->
        <el-tab-pane label="创建联合活动" name="joint">
          <el-form
            ref="jointFormRef"
            :model="jointForm"
            :rules="rules"
            label-width="100px"
            style="max-width: 680px"
          >
            <el-form-item label="活动名称" prop="title">
              <el-input v-model="jointForm.title" placeholder="请输入活动名称" />
            </el-form-item>
            <el-form-item label="活动地点" prop="location">
              <el-input v-model="jointForm.location" placeholder="请输入活动地点" />
            </el-form-item>
            <el-form-item label="活动时间" prop="timeRange" required>
              <el-date-picker
                v-model="jointForm.timeRange"
                type="datetimerange"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="名额" prop="capacity">
              <el-input-number v-model="jointForm.capacity" :min="1" :max="9999" />
            </el-form-item>
            <el-form-item label="安全负责人" prop="safetyOfficer">
              <el-input v-model="jointForm.safetyOfficer" placeholder="请输入安全负责人姓名" />
            </el-form-item>
            <el-form-item label="受邀社团" prop="inviteClubIds">
              <!-- 多选社团组件：选择受邀社团 -->
              <el-select
                v-model="jointForm.inviteClubIds"
                multiple
                placeholder="请选择受邀社团（可多选）"
                style="width: 100%"
              >
                <el-option
                  v-for="c in otherClubs"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="活动背景" prop="background">
              <el-input
                v-model="jointForm.background"
                type="textarea"
                :rows="3"
                placeholder="非必填：活动背景说明，可由 AI 一键分段填入"
              />
            </el-form-item>
            <el-form-item label="活动流程" prop="flow">
              <el-input
                v-model="jointForm.flow"
                type="textarea"
                :rows="4"
                placeholder="非必填：活动流程安排，可由 AI 一键分段填入"
              />
            </el-form-item>
            <el-form-item label="宣传口号" prop="slogan">
              <el-input
                v-model="jointForm.slogan"
                type="textarea"
                :rows="2"
                placeholder="非必填：活动宣传口号，可由 AI 一键分段填入"
              />
              <!-- AI 生成整段文案后，按【活动背景】【活动流程】【宣传口号】自动分段回填到上方三个输入框 -->
              <div class="ai-btn-row">
                <el-button type="primary" plain size="small" @click="openAi('joint')">
                  <el-icon><MagicStick /></el-icon>
                  AI 生成文案（一键分段填入）
                </el-button>
                <!-- 联合活动专属：AI 生成完整策划方案（主题定位/分工/时间线/宣传/应急预案），整段回填到下方方案框 -->
                <el-button type="warning" plain size="small" @click="openJointPlan">
                  <el-icon><Connection /></el-icon>
                  AI 生成联合方案
                </el-button>
              </div>
            </el-form-item>
            <el-form-item label="策划方案" prop="plan">
              <el-input
                v-model="jointForm.plan"
                type="textarea"
                :rows="6"
                placeholder="非必填：联合活动完整策划方案（分工/时间线/宣传/应急预案），可由「AI 生成联合方案」整段填入"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" @click="saveJoint">创建联合活动（发出邀请）</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 我的草稿（仅普通活动草稿可编辑/提交） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><b>我的草稿活动</b></template>
      <el-table :data="drafts" border size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动名称" min-width="160" />
        <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="capacity" label="名额" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.statusDesc || '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="editDraft(row)">编辑</el-button>
            <el-button type="primary" size="small" @click="submitDraft(row)">提交校方审批</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="drafts.length === 0" description="暂无草稿活动" />
    </el-card>

    <!-- 【复用组件】AI 生成弹窗：生成活动文案，确认后回填 intro -->
    <AiGenerateDialog
      v-model="aiVisible"
      skill-type="ACTIVITY_PLAN"
      :extra-input="aiTheme"
      title="AI 生成活动文案"
      @confirm="onAiConfirm"
    />

    <!-- 【复用组件】AI 生成联合方案（skill=JOINT_PLAN）：入参=主题 + 受邀社团列表，回填 intro -->
    <AiGenerateDialog
      v-model="jointPlanVisible"
      skill-type="JOINT_PLAN"
      :extra-input="jointPlanTheme"
      :club-ids="jointPlanClubIds"
      title="AI 生成联合活动方案"
      @confirm="onJointPlanConfirm"
    />
  </div>
</template>

<script setup>
// 新建活动页（社团负责人）：普通活动 / 联合活动 两个Tab切换
// - AI 生成文案：按【活动背景】【活动流程】【宣传口号】一键分段填入对应输入框（均非必填），
//   保存时拼接为带标题的 intro 整段入库；绝不自动提交表单
// - 联合活动：多选社团组件选择受邀社团，创建后直接进入「待社团确认」
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createActivity, updateActivity, submitActivity, getActivityList, getClubList, getMyClubs } from '@/api'
import AiGenerateDialog from '@/components/ai-generate-dialog.vue'

const activeTab = ref('normal')
const normalFormRef = ref()
const jointFormRef = ref()

// 表单校验规则（两个Tab共用）
const rules = {
  title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入活动地点', trigger: 'blur' }],
  capacity: [{ required: true, message: '请填写名额', trigger: 'blur' }]
}

// 普通活动表单（含 id 时为草稿编辑）
// 文案拆为 背景/流程/口号 三个非必填框（AI 可一键分段填入），保存时拼接为 intro 整段
const normalForm = reactive({
  id: null,
  title: '',
  location: '',
  timeRange: null,
  capacity: 30,
  background: '',
  flow: '',
  slogan: ''
})

// 联合活动表单（比普通活动多 安全负责人/受邀社团/策划方案 四项）
const jointForm = reactive({
  title: '',
  location: '',
  timeRange: null,
  capacity: 100,
  safetyOfficer: '',
  inviteClubIds: [],
  background: '',
  flow: '',
  slogan: '',
  plan: ''
})

// ==================== 文案 拼接 / 拆分 工具 ====================
// 表单三个非必填框（背景/流程/口号）+ 联合方案的策划方案框，与 intro 整段文本互转。
// intro 保存格式（带【标题】头的纯文本，与 AI 生成格式一致，便于展示与反向解析）：
//   【活动背景】xxx\n\n【活动流程】xxx\n\n【宣传口号】xxx\n\n【联合活动策划方案】xxx
// 没有任何一个框填写时 intro 为空串，不影响提交（均为非必填）。

/** 把表单各段拼接为 intro 整段文本（只拼非空段，自动带标题头） */
const buildIntro = (form, withPlan) => {
  const parts = []
  if (form.background && form.background.trim()) parts.push(`【活动背景】\n${form.background.trim()}`)
  if (form.flow && form.flow.trim()) parts.push(`【活动流程】\n${form.flow.trim()}`)
  if (form.slogan && form.slogan.trim()) parts.push(`【宣传口号】\n${form.slogan.trim()}`)
  if (withPlan && form.plan && form.plan.trim()) parts.push(`【联合活动策划方案】\n${form.plan.trim()}`)
  return parts.join('\n\n')
}

/** 把 intro 整段文本解析拆回表单各段（编辑草稿时反向回填） */
const parseIntro = (intro) => {
  const result = { background: '', flow: '', slogan: '', plan: '' }
  if (!intro || !intro.trim()) return result
  // 按已知标题头切段；【活动主题】与活动名称重复、解析后丢弃；
  // 【联合活动策划方案】为旧版联合方案兼容标题
  const marks = []
  ;[
    { head: '【活动主题】', key: null },
    { head: '【活动背景】', key: 'background' },
    { head: '【活动流程】', key: 'flow' },
    { head: '【宣传口号】', key: 'slogan' },
    { head: '【联合活动策划方案】', key: 'plan' }
  ].forEach(({ head, key }) => {
    const idx = intro.indexOf(head)
    if (idx >= 0) marks.push({ idx, head, key })
  })
  if (marks.length === 0) {
    // 没有任何标题头（自由手写的旧数据）：整段放背景框，不丢失内容
    result.background = intro.trim()
    return result
  }
  marks.sort((a, b) => a.idx - b.idx)
  // 第一个标记（含被丢弃的主题段）之前的未知前导文字（如有）并入背景段开头
  const preface = intro.slice(0, marks[0].idx).trim()
  marks.forEach((m, i) => {
    if (!m.key) return // 活动主题段：与名称重复，丢弃
    const end = i + 1 < marks.length ? marks[i + 1].idx : intro.length
    const seg = intro.slice(m.idx + m.head.length, end).trim()
    result[m.key] = seg
  })
  if (preface) result.background = preface + '\n' + (result.background || '')
  return result
}

/** 把 AI 生成的整段文案按标题拆分为 背景/流程/口号（一键分段回填用） */
const splitAiCopy = (content) => {
  // 复用 parseIntro：AI 文案与 intro 保存格式一致（【标题】分段），可直接解析
  return parseIntro(content)
}

// 我的草稿列表
const drafts = ref([])
// 其他社团列表（联合活动多选受邀社团用，排除自己管理的社团）
const otherClubs = ref([])
const myClubIds = ref([])

// 加载草稿 + 社团列表
const loadData = async () => {
  const [all, clubs, myClubs] = await Promise.all([
    getActivityList({ status: 0 }),
    getClubList(),
    getMyClubs()
  ])
  drafts.value = all || []
  myClubIds.value = (myClubs || []).map((c) => c.id)
  otherClubs.value = (clubs || []).filter((c) => !myClubIds.value.includes(c.id))
}

// ============ AI 生成文案 ============
const aiVisible = ref(false)
const aiTarget = ref('normal') // 当前 AI 生成作用于哪个表单
const aiTheme = ref('')

// 打开 AI 弹窗：以活动名称作为主题传给后端（extraInput）
const openAi = (target) => {
  const form = target === 'normal' ? normalForm : jointForm
  if (!form.title || !form.title.trim()) {
    ElMessage.warning('请先填写活动名称，AI 将根据主题生成文案')
    return
  }
  aiTarget.value = target
  aiTheme.value = form.title.trim()
  aiVisible.value = true
}

// AI 确认回调：把编辑后内容按【活动背景】【活动流程】【宣传口号】
// 一键分段回填到表单对应输入框（各框独立可再编辑，不提交表单）
const onAiConfirm = (content) => {
  const seg = splitAiCopy(content)
  const form = aiTarget.value === 'normal' ? normalForm : jointForm
  form.background = seg.background
  form.flow = seg.flow
  form.slogan = seg.slogan
  ElMessage.success('AI 文案已按标题分段填入 背景/流程/口号 输入框，请检查后手动保存')
}

// ============ AI 生成联合活动方案（skill=JOINT_PLAN） ============
// 入参：活动主题（extraInput）+ 受邀社团ID列表（clubIds）
// 后端会查 ClubService 拿受邀社团真实名称，生成贴合实际的分工建议
const jointPlanVisible = ref(false)
const jointPlanTheme = ref('')
const jointPlanClubIds = ref([])

// 打开联合方案弹窗：校验主题与受邀社团（缺一无法生成有效分工）
const openJointPlan = () => {
  if (!jointForm.title || !jointForm.title.trim()) {
    ElMessage.warning('请先填写活动名称，AI 将据此生成联合方案')
    return
  }
  if (!jointForm.inviteClubIds || jointForm.inviteClubIds.length === 0) {
    ElMessage.warning('请先选择受邀社团，AI 将据此生成各社团分工建议')
    return
  }
  jointPlanTheme.value = jointForm.title.trim()
  jointPlanClubIds.value = [...jointForm.inviteClubIds]
  jointPlanVisible.value = true
}

// 联合方案确认回调：整段回填到「策划方案」输入框（不提交表单）
const onJointPlanConfirm = (content) => {
  jointForm.plan = content
  ElMessage.success('联合方案已填入策划方案输入框，请检查后手动创建活动')
}

// ============ 保存普通活动 ============
const saveNormal = () => {
  normalFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!normalForm.timeRange || normalForm.timeRange.length !== 2) {
      ElMessage.warning('请选择活动时间')
      return
    }
    const payload = {
      id: normalForm.id || undefined,
      title: normalForm.title,
      isJoint: 0,
      location: normalForm.location,
      startTime: normalForm.timeRange[0],
      endTime: normalForm.timeRange[1],
      capacity: normalForm.capacity,
      // 三段拼接为带标题的整段文案入库（均为非必填，全空则 intro 为空）
      intro: buildIntro(normalForm, false)
    }
    if (normalForm.id) {
      await updateActivity(payload)
      ElMessage.success('草稿已更新')
    } else {
      await createActivity(payload)
      ElMessage.success('活动已保存为草稿，可在下方列表提交校方审批')
    }
    resetNormal()
    loadData()
  })
}

// ============ 创建联合活动 ============
const saveJoint = () => {
  jointFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!jointForm.timeRange || jointForm.timeRange.length !== 2) {
      ElMessage.warning('请选择活动时间')
      return
    }
    if (!jointForm.inviteClubIds || jointForm.inviteClubIds.length === 0) {
      ElMessage.warning('请至少选择一个受邀社团')
      return
    }
    await createActivity({
      title: jointForm.title,
      isJoint: 1,
      location: jointForm.location,
      startTime: jointForm.timeRange[0],
      endTime: jointForm.timeRange[1],
      capacity: jointForm.capacity,
      safetyOfficer: jointForm.safetyOfficer,
      inviteClubIds: jointForm.inviteClubIds,
      // 背景/流程/口号 + 策划方案 拼接为整段文案入库（均非必填）
      intro: buildIntro(jointForm, true)
    })
    ElMessage.success('联合活动已创建，已向受邀社团发出确认邀请')
    resetJoint()
    loadData()
  })
}

// 编辑草稿：解析 intro 拆回 背景/流程/口号 各框并回填表单，切到普通Tab
const editDraft = (row) => {
  normalForm.id = row.id
  normalForm.title = row.title
  normalForm.location = row.location
  normalForm.timeRange = [row.startTime, row.endTime]
  normalForm.capacity = row.capacity
  const seg = parseIntro(row.intro || '')
  normalForm.background = seg.background
  normalForm.flow = seg.flow
  normalForm.slogan = seg.slogan
  activeTab.value = 'normal'
  ElMessage.info(`已载入草稿「${row.title}」，修改后保存`)
}

// 提交草稿进入校方审批
const submitDraft = async (row) => {
  await ElMessageBox.confirm(`确认将「${row.title}」提交校方审批？`, '提示', { type: 'warning' })
  await submitActivity(row.id)
  ElMessage.success('已提交校方审批')
  loadData()
}

// 重置表单
const resetNormal = () => {
  Object.assign(normalForm, { id: null, title: '', location: '', timeRange: null, capacity: 30, background: '', flow: '', slogan: '' })
}
const resetJoint = () => {
  Object.assign(jointForm, { title: '', location: '', timeRange: null, capacity: 100, safetyOfficer: '', inviteClubIds: [], background: '', flow: '', slogan: '', plan: '' })
}

onMounted(loadData)
</script>

<style scoped>
.ai-btn-row {
  margin-top: 6px;
  text-align: right;
  width: 100%;
}
</style>
