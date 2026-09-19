<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <b>社团资料库</b>
          <div class="toolbar">
            <el-select v-model="query.clubId" placeholder="选择社团" style="width: 180px" @change="loadList">
              <el-option v-for="c in myClubs" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 130px" @change="loadList">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
            <el-input
              v-model="query.keyword"
              placeholder="按标题搜索"
              clearable
              style="width: 200px"
              @keyup.enter="loadList"
              @clear="loadList"
            >
              <template #append>
                <el-button @click="loadList">搜索</el-button>
              </template>
            </el-input>
            <el-button type="primary" @click="uploadVisible = true">上传资料</el-button>
          </div>
        </div>
      </template>

      <el-table :data="files" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="clubName" label="所属社团" min-width="130" />
        <el-table-column prop="category" label="分类" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="资料标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileName" label="原始文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="uploaderName" label="上传人" width="100" />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="handleDownload(row)">下载</el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && files.length === 0" description="暂无资料，点击右上角「上传资料」添加" />
    </el-card>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传资料" width="480px">
      <el-form label-width="90px">
        <el-form-item label="所属社团">
          <el-select v-model="uploadForm.clubId" style="width: 100%">
            <el-option v-for="c in myClubs" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="uploadForm.category" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="资料标题">
          <el-input v-model="uploadForm.title" placeholder="留空则使用文件名" />
        </el-form-item>
        <el-form-item label="文件" required>
          <input type="file" @change="onFileChange" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 资料库页（社团负责人）：按分类上传/检索/下载/删除社团资料（移植自旧版 zhituan-system）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyClubs, getResourceList, uploadResource, downloadResource, deleteResource } from '@/api'

const categories = ['策划', '总结', '照片', '预算', '其他']

const myClubs = ref([])
const files = ref([])
const loading = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const query = reactive({ clubId: null, category: '', keyword: '' })
const uploadForm = reactive({ clubId: null, category: '', title: '' })
let pickedFile = null

const loadList = async () => {
  loading.value = true
  try {
    const params = {}
    if (query.clubId) params.clubId = query.clubId
    if (query.category) params.category = query.category
    if (query.keyword) params.keyword = query.keyword
    files.value = (await getResourceList(params)) || []
  } finally {
    loading.value = false
  }
}

const onFileChange = (e) => {
  pickedFile = e.target.files && e.target.files[0]
}

const handleUpload = async () => {
  if (!uploadForm.clubId) return ElMessage.warning('请选择社团')
  if (!uploadForm.category) return ElMessage.warning('请选择分类')
  if (!pickedFile) return ElMessage.warning('请选择文件')
  const fd = new FormData()
  fd.append('clubId', uploadForm.clubId)
  fd.append('category', uploadForm.category)
  if (uploadForm.title) fd.append('title', uploadForm.title)
  fd.append('file', pickedFile)
  uploading.value = true
  try {
    await uploadResource(fd)
    ElMessage.success('上传成功')
    uploadVisible.value = false
    uploadForm.category = ''
    uploadForm.title = ''
    pickedFile = null
    loadList()
  } finally {
    uploading.value = false
  }
}

const handleDownload = async (row) => {
  const resp = await downloadResource(row.id)
  const url = window.URL.createObjectURL(new Blob([resp.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = row.fileName || row.title
  a.click()
  window.URL.revokeObjectURL(url)
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除资料「${row.title}」？该操作不可恢复。`, '提示', { type: 'warning' })
  await deleteResource(row.id)
  ElMessage.success('删除成功')
  loadList()
}

onMounted(async () => {
  myClubs.value = (await getMyClubs()) || []
  if (myClubs.value.length > 0) {
    query.clubId = myClubs.value[0].id
    uploadForm.clubId = myClubs.value[0].id
  }
  loadList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
