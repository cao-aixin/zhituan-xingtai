<template>
  <!-- AI 对话助手：右下角悬浮按钮 + 聊天抽屉面板（全角色可见，按角色差异化） -->
  <div class="ai-chat-root">
    <!-- 悬浮按钮：点击展开/收起聊天面板 -->
    <div class="ai-chat-fab" :class="{ active: visible }" @click="toggle">
      <el-icon :size="22"><ChatDotRound /></el-icon>
      <span class="fab-text">AI 助手</span>
    </div>

    <!-- 聊天面板 -->
    <el-card v-show="visible" class="ai-chat-panel" shadow="always" :body-style="{ padding: '0' }">
      <!-- 面板头部：标题按角色切换（学生=活动问答助手 / 负责人=社团事务助手 / 管理员=审批管理助手） -->
      <div class="chat-header">
        <div class="chat-title">
          <el-icon><MagicStick /></el-icon>
          <span>{{ view.title }}</span>
        </div>
        <div class="chat-actions">
          <el-button link size="small" @click="clearHistory">清空</el-button>
          <el-button link size="small" @click="visible = false">收起</el-button>
        </div>
      </div>

      <!-- 消息列表：用户气泡靠右，AI 气泡靠左 -->
      <div ref="listRef" class="chat-body">
        <div v-for="(msg, idx) in messages" :key="idx" class="chat-row" :class="msg.role">
          <div class="chat-bubble" :class="msg.role">
            <!-- AI 回复做基本换行处理；用户消息直接展示 -->
            <template v-if="msg.role === 'ai'">
              <div v-if="msg.source" class="bubble-source">
                <el-tag :type="msg.source === 'AI大模型' ? 'success' : 'warning'" size="small">
                  {{ msg.source }}
                </el-tag>
              </div>
              <div class="bubble-text" v-html="renderText(msg.content)"></div>
            </template>
            <div v-else class="bubble-text">{{ msg.content }}</div>
          </div>
        </div>
        <!-- 正在思考 -->
        <div v-if="loading" class="chat-row ai">
          <div class="chat-bubble ai loading-bubble">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>AI 正在思考…</span>
          </div>
        </div>
      </div>

      <!-- 快捷提问：按角色给出不同模板，降低使用门槛 -->
      <div v-if="messages.length <= 1" class="chat-quick">
        <el-tag
          v-for="q in view.quickQuestions"
          :key="q"
          class="quick-tag"
          size="small"
          effect="plain"
          @click="ask(q)"
        >
          {{ q }}
        </el-tag>
      </div>

      <!-- 输入区：placeholder 按角色切换 -->
      <div class="chat-footer">
        <el-input
          v-model="input"
          :placeholder="view.placeholder"
          size="default"
          :disabled="loading"
          @keyup.enter="ask()"
        />
        <el-button type="primary" :loading="loading" :disabled="!input.trim()" @click="ask()">
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 【独立组件】AI 对话助手（全角色可见，按角色差异化）
 *
 * 架构位置：挂在 layout/index.vue 中，所有登录角色均可见（右下角悬浮按钮）。
 *
 * 【本次改造要点：前端按角色切换文案】
 * 后端 ActivityQaTool 已按角色返回不同视角的事实数据；若前端仍用"学生口吻"的
 * 欢迎语与快捷提问，负责人/管理员一进来就会被引导问学生问题，体验割裂。
 * 因此这里用 useUserStore 的角色信息，统一切换四处文案：
 *   1. 面板标题；2. 欢迎语；3. 快捷提问；4. 输入框 placeholder。
 * 角色优先级与后端保持一致：ADMIN > 负责人(CLUB_LEADER / CLUB_PARTICIPANT) > 学生，
 * 用 computed 实现，登录信息变化（切换账号）时自动跟随。
 *
 * 交互设计：
 *   - 悬浮按钮点击展开/收起聊天抽屉，不打断当前页面操作（不做路由跳转）；
 *   - 自由输入提问（如「有什么适合新手的活动？」），回车或点「发送」提交；
 *   - 多轮对话**不做会话记忆**（后端 AgentService 每次独立上下文），
 *     但前端保留 messages 数组用于聊天记录展示，提升连续提问体验；
 *   - 每次提问调用统一入口 /api/ai/generate，skill=ACTIVITY_QA，问题放 extraInput；
 *   - AI 回答只基于当前用户有权限查看的真实数据（数据权限在 Tool 内自动生效）；
 *   - AI 挂掉不影响页面：捕获异常后以本地提示气泡回复，不弹全局错误阻塞操作。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { aiGenerate } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/**
 * 三种角色的文案配置（集中管理，便于后续调整与扩展）
 * - STUDENT：活动问答助手，学生关心"能参加什么、我加了哪些社团"
 * - LEADER：社团事务助手，负责人关心"我的活动卡在哪一步、哪些待我处理"
 * - ADMIN：审批管理助手，团委老师关心"有哪些待我审批、全局态势如何"
 */
const ROLE_VIEW = {
  STUDENT: {
    title: '活动问答助手',
    welcome:
      '你好呀！我是活动问答助手 👋\n你可以问我：有什么活动可以参加？某个活动在哪里举办？我加入了哪些社团？',
    placeholder: '问问有什么活动可以参加…',
    quickQuestions: [
      '有什么活动可以参加？',
      '有什么适合新手的活动？',
      '本周有什么活动？',
      '我加入了哪些社团？'
    ]
  },
  LEADER: {
    title: '社团事务助手',
    welcome: '你好，我是社团事务助手 👋\n我可以帮你查看社团活动与待办事项，例如：我有哪些活动待处理？哪些草稿可以提交审批？',
    placeholder: '问问我的活动待办或报名情况…',
    quickQuestions: [
      '我有哪些活动待处理？',
      '我社团的活动报名情况如何？',
      '有哪些草稿可以提交审批？',
      '我发起过哪些活动？'
    ]
  },
  ADMIN: {
    title: '审批管理助手',
    welcome: '您好，我是审批管理助手 👋\n我可以帮您查看待审批活动与系统概览，例如：有哪些活动待我审批？当前活动整体情况如何？',
    placeholder: '问问待审批活动或整体情况…',
    quickQuestions: [
      '有哪些活动待我审批？',
      '当前活动整体情况如何？',
      '有哪些活动即将开始？',
      '有多少社团和活动？'
    ]
  }
}

/**
 * 当前角色标识：优先级 ADMIN > 负责人 > 学生（与后端 ActivityQaTool 分流优先级一致）。
 * 用 computed 而非一次性取值，保证切换账号后文案自动跟随，无需刷新页面。
 */
const roleKey = computed(() => {
  if (userStore.hasRole('ADMIN')) {
    return 'ADMIN'
  }
  if (userStore.hasRole('CLUB_LEADER') || userStore.hasRole('CLUB_PARTICIPANT')) {
    return 'LEADER'
  }
  return 'STUDENT'
})

/** 当前角色对应的文案配置（标题 / 欢迎语 / 快捷提问 / placeholder） */
const view = computed(() => ROLE_VIEW[roleKey.value])

const visible = ref(false)
const input = ref('')
const loading = ref(false)
const listRef = ref()

/** 聊天记录：{ role: 'user' | 'ai', content: string, source?: string }，首条为按角色生成的欢迎语 */
const messages = ref([{ role: 'ai', content: view.value.welcome, source: '' }])

/**
 * 角色变化时刷新欢迎语：切换账号（如从学生切到负责人）而页面未重新挂载时，
 * 首条欢迎语会残留上一个角色的口吻，这里在角色变化时把欢迎语重置为新角色的内容。
 * 仅当用户还未提问（messages 只有欢迎语）时才覆盖，避免清掉用户已有聊天记录。
 */
watch(roleKey, () => {
  if (messages.value.length <= 1) {
    messages.value = [{ role: 'ai', content: view.value.welcome, source: '' }]
  }
})

/** 展开/收起面板 */
const toggle = () => {
  visible.value = !visible.value
  if (visible.value) {
    scrollToBottom()
  }
}

/** 清空聊天记录（保留按当前角色生成的欢迎语） */
const clearHistory = () => {
  messages.value = [{ role: 'ai', content: view.value.welcome, source: '' }]
}

/** 渲染 AI 文本：转义 HTML 防 XSS 后，把换行转为 <br>（基本换行处理） */
const renderText = (text) => {
  const escaped = (text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/\n/g, '<br>')
}

/** 滚动到底部，保证最新消息可见 */
const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

/**
 * 提问：把问题作为用户气泡入列 -> 调用 AI 接口 -> 把回答作为 AI 气泡入列
 * @param {string} preset 快捷提问内容，未传时取输入框内容
 */
const ask = async (preset) => {
  const question = (preset || input.value || '').trim()
  if (!question) {
    return
  }
  messages.value.push({ role: 'user', content: question })
  input.value = ''
  loading.value = true
  scrollToBottom()
  try {
    // skill=ACTIVITY_QA，问题通过 extraInput 传递；后端不保存会话记忆
    const data = await aiGenerate({ skill: 'ACTIVITY_QA', extraInput: question })
    messages.value.push({
      role: 'ai',
      content: data.content || '抱歉，我暂时没有想到答案，请稍后再试。',
      source: data.source || ''
    })
  } catch (e) {
    // 失败降级：以气泡形式提示，不弹全局错误，避免打断用户浏览
    messages.value.push({
      role: 'ai',
      content: 'AI 助手暂时繁忙，请稍后重试。' + (e.message ? '\n（' + e.message + '）' : ''),
      source: ''
    })
    ElMessage.warning('AI 助手暂时繁忙，请稍后重试')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.ai-chat-root {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
}
/* 悬浮按钮呼吸态 */
.ai-chat-fab {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 18px;
  border-radius: 22px;
  background: linear-gradient(135deg, #409eff, #6a8dff);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s;
}
.ai-chat-fab:hover {
  transform: translateY(-2px);
}
.ai-chat-fab.active {
  background: linear-gradient(135deg, #909399, #b1b3b8);
  box-shadow: 0 4px 14px rgba(144, 147, 153, 0.4);
}
.fab-text {
  font-weight: 500;
}
/* 聊天面板 */
.ai-chat-panel {
  position: absolute;
  right: 0;
  bottom: 56px;
  width: 380px;
  height: 520px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #ebeef5;
  background: #f8f9fb;
}
.chat-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
  font-size: 14px;
}
.chat-actions {
  display: flex;
  align-items: center;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f5f7fa;
}
.chat-row {
  display: flex;
  margin-bottom: 12px;
}
.chat-row.user {
  justify-content: flex-end;
}
.chat-row.ai {
  justify-content: flex-start;
}
.chat-bubble {
  max-width: 82%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}
.chat-bubble.user {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}
.chat-bubble.ai {
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-top-left-radius: 2px;
}
.bubble-source {
  margin-bottom: 4px;
}
.loading-bubble {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
}
.chat-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px 0;
  background: #fff;
}
.quick-tag {
  cursor: pointer;
}
.chat-footer {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}
</style>
