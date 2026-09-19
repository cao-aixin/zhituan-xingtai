<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">智团星台</h2>
      <p class="login-sub">校园社团智能协同系统</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
// 登录页：调 /api/auth/login，成功后保存用户信息与 token，跳转角色首页
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api'
import { useUserStore } from '@/stores/user'
import { roleHome } from '@/router'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ studentNo: '', password: '123456' })
const rules = {
  studentNo: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const data = await login({ studentNo: form.studentNo, password: form.password })
      userStore.setUser(data)
      ElMessage.success(`欢迎，${data.name}`)
      router.push(roleHome[data.roleCodes[0]] || '/login')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f6feb 0%, #6e40c9 100%);
}
.login-card {
  width: 380px;
  padding: 10px 20px 0;
}
.login-title {
  text-align: center;
  margin: 10px 0 4px;
}
.login-sub {
  text-align: center;
  color: #909399;
  margin-bottom: 24px;
  font-size: 13px;
}
.login-btn {
  width: 100%;
}
</style>
