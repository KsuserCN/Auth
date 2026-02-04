<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">基本信息</h1>
        <p class="page-subtitle">编辑和管理您的个人基本信息</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card class="card avatar-card" shadow="never">
          <div class="avatar-section">
            <div class="avatar-preview">
              <el-avatar :size="120" :src="form.avatarUrl" class="profile-avatar">
                {{ form.username?.slice(0, 1) || 'K' }}
              </el-avatar>
              <div class="upload-overlay">
                <el-icon class="upload-icon">
                  <Camera />
                </el-icon>
              </div>
              <input type="file" ref="fileInput" class="file-input" accept="image/*" @change="handleAvatarChange" />
              <div class="upload-tip">点击上传头像</div>
            </div>
            <div class="avatar-info">
              <div class="info-item">
                <span class="label">用户 ID</span>
                <span class="value">{{ user?.uuid || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">账户状态</span>
                <el-tag type="success">正常</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <User />
            </el-icon>
            <span>个人信息</span>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent="handleSubmit">
            <el-form-item label="用户名" prop="username">
              <el-input v-if="!detailsLoading" v-model="form.username" placeholder="请输入用户名" clearable maxlength="20">
                <template #suffix>
                  <span class="char-count">{{ form.username?.length || 0 }}/20</span>
                </template>
              </el-input>
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 32px;" />
                </template>
              </el-skeleton>
            </el-form-item>

            <el-form-item label="邮箱" prop="email">
              <el-input v-if="!detailsLoading" v-model="form.email" type="email" placeholder="请输入邮箱地址" disabled />
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 32px;" />
                </template>
              </el-skeleton>
              <div v-if="!detailsLoading" class="field-tip">邮箱地址不可修改，如需更改请联系管理员</div>
            </el-form-item>

            <el-form-item label="真实姓名" prop="realName">
              <el-input v-if="!detailsLoading" v-model="form.realName" placeholder="请输入真实姓名" clearable maxlength="30" />
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 32px;" />
                </template>
              </el-skeleton>
            </el-form-item>

            <el-form-item label="性别" prop="gender">
              <el-select v-if="!detailsLoading" v-model="form.gender" placeholder="请选择性别" clearable>
                <el-option label="男" value="male" />
                <el-option label="女" value="female" />
                <el-option label="保密" value="secret" />
              </el-select>
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 32px;" />
                </template>
              </el-skeleton>
            </el-form-item>

            <el-form-item label="出生日期" prop="birthday">
              <el-date-picker v-if="!detailsLoading" v-model="form.birthday" type="date" placeholder="请选择出生日期"
                value-format="YYYY-MM-DD" />
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 32px;" />
                </template>
              </el-skeleton>
            </el-form-item>

            <el-form-item label="地区" prop="region">
              <el-select v-if="!detailsLoading" v-model="form.region" placeholder="请选择地区" clearable>
                <el-option label="中国" value="CN" />
                <el-option label="美国" value="US" />
                <el-option label="日本" value="JP" />
                <el-option label="其他" value="OTHER" />
              </el-select>
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 32px;" />
                </template>
              </el-skeleton>
            </el-form-item>

            <el-form-item label="个人简介" prop="bio">
              <el-input v-if="!detailsLoading" v-model="form.bio" type="textarea" placeholder="请输入个人简介" maxlength="200"
                show-word-limit rows="4" />
              <el-skeleton v-else animated>
                <template #template>
                  <el-skeleton-item variant="text" style="width: 100%; height: 96px;" />
                </template>
              </el-skeleton>
            </el-form-item>

            <div class="form-actions">
              <el-button @click="handleReset" :disabled="detailsLoading">重置</el-button>
              <el-button type="primary" @click="handleSubmit" :loading="submitLoading"
                :disabled="detailsLoading || !hasChanged">
                保存修改
              </el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Camera } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateUserProfile } from '@/api/auth'
import { storeToRefs } from 'pinia'

const userStore = useUserStore()
const { user, userDetails, detailsLoading } = storeToRefs(userStore)
const fileInput = ref<HTMLInputElement>()
const formRef = ref()
const submitLoading = ref(false)

const form = reactive({
  username: '',
  email: '',
  realName: '',
  gender: '',
  birthday: '',
  phone: '',
  region: '',
  bio: '',
  avatarUrl: ''
})

// 保存原始数据，用于比对是否有改动
const originalForm = reactive({
  username: '',
  email: '',
  realName: '',
  gender: '',
  birthday: '',
  phone: '',
  region: '',
  bio: '',
  avatarUrl: ''
})

// 检查表单是否有改动
const hasChanged = computed(() => {
  return (
    form.username !== originalForm.username ||
    form.realName !== originalForm.realName ||
    form.gender !== originalForm.gender ||
    form.birthday !== originalForm.birthday ||
    form.region !== originalForm.region ||
    form.bio !== originalForm.bio ||
    form.avatarUrl !== originalForm.avatarUrl
  )
})

const rules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度应为 2-20 个字符', trigger: 'blur' }
  ],
  realName: [
    { max: 30, message: '真实姓名长度不超过 30 个字符', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^[0-9\-+]*$/, message: '请输入有效的电话号码', trigger: 'blur' }
  ]
}

// 初始化表单数据
onMounted(async () => {
  // 清除缓存，确保每次访问时都获取最新的详细信息
  userStore.clearUserDetailsCache()

  // 然后获取详细信息
  try {
    const details = await userStore.fetchUserDetails()
    // 使用返回的详细信息更新表单（包含所有字段）
    if (details && typeof details === 'object') {
      form.username = details.username || ''
      form.email = details.email || ''
      form.avatarUrl = details.avatarUrl || ''
      form.realName = details.realName || ''
      form.gender = details.gender || ''
      form.birthday = details.birthDate || ''
      form.region = details.region || ''
      form.bio = details.bio || ''

      // 保存原始数据
      Object.assign(originalForm, form)
    } else {
      ElMessage.error('获取的详细信息格式不正确')
    }
  } catch (err) {
    ElMessage.error('获取详细信息失败')
  }
})

const handleAvatarChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  // 验证文件大小（最大 5MB）
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }

  // 读取文件并显示预览
  const reader = new FileReader()
  reader.onload = (e) => {
    form.avatarUrl = e.target?.result as string
    ElMessage.success('头像已更新')
  }
  reader.readAsDataURL(file)
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    // 如果没有改动，提示用户
    if (!hasChanged.value) {
      ElMessage.info('没有任何改动')
      return
    }

    submitLoading.value = true

    // 构建只包含改动字段的请求数据
    const updateData: any = {}
    if (form.username !== originalForm.username) {
      updateData.username = form.username
    }
    if (form.realName !== originalForm.realName) {
      updateData.realName = form.realName || null
    }
    if (form.gender !== originalForm.gender) {
      updateData.gender = form.gender || null
    }
    if (form.birthday !== originalForm.birthday) {
      updateData.birthDate = form.birthday || null
    }
    if (form.region !== originalForm.region) {
      updateData.region = form.region || null
    }
    if (form.bio !== originalForm.bio) {
      updateData.bio = form.bio || null
    }
    if (form.avatarUrl !== originalForm.avatarUrl) {
      updateData.avatarUrl = form.avatarUrl || null
    }

    // 调用更新接口
    const result = await updateUserProfile(updateData)

    // 更新原始数据和表单
    Object.assign(originalForm, form)

    // 同步更新用户信息到 store
    userStore.user = result
    userStore.userDetails = result

    ElMessage.success('信息保存成功')
    submitLoading.value = false
  } catch (err) {
    ElMessage.error('保存失败，请检查表单信息')
    submitLoading.value = false
  }
}

const handleReset = () => {
  // 重置表单，但不重置用户名和邮箱
  form.realName = originalForm.realName
  form.gender = originalForm.gender
  form.birthday = originalForm.birthday
  form.region = originalForm.region
  form.bio = originalForm.bio
  form.avatarUrl = originalForm.avatarUrl
  ElMessage.info('已重置为原始数据')
}
</script>

<style scoped>
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.avatar-card {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.avatar-section {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.avatar-preview {
  position: relative;
  cursor: pointer;
  width: 120px;
  height: 120px;
}

.profile-avatar {
  width: 120px;
  height: 120px;
  border-radius: 16px;
  transition: all 0.3s ease;
}

.avatar-preview:hover .profile-avatar {
  opacity: 0.7;
}

.upload-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.avatar-preview:hover .upload-overlay {
  opacity: 1;
}

.upload-icon {
  color: white;
  font-size: 32px;
}

.file-input {
  display: none;
}

.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.avatar-info {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.info-item .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.info-item .value {
  font-size: 13px;
  color: var(--el-text-color-primary);
  font-family: monospace;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 20px;
  font-size: 16px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.field-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.char-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-light);
}

:deep(.el-button) {
  transition: all 0.2s ease;
}

@media (max-width: 1024px) {
  .avatar-card {
    min-height: auto;
  }

  .avatar-section {
    flex-direction: row;
    justify-content: space-between;
  }

  .avatar-preview {
    width: 100px;
    height: 100px;
  }

  .profile-avatar {
    width: 100px;
    height: 100px;
  }

  .avatar-info {
    flex: 1;
    margin-left: 16px;
  }
}
</style>
