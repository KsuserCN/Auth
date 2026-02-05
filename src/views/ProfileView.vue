<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">基本信息</h1>
        <p class="page-subtitle">编辑和管理您的个人基本信息</p>
      </div>
    </div>

    <el-row :gutter="16" class="profile-grid">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <User />
            </el-icon>
            <span>个人信息</span>
          </div>

          <!-- 只读信息列表 -->
          <el-skeleton v-if="detailsLoading" animated>
            <template #template>
              <div class="info-list">
                <div class="info-row avatar-row skeleton-row">
                  <div class="row-left">
                    <el-skeleton-item variant="circle" class="skeleton-icon" />
                    <el-skeleton-item variant="text" class="skeleton-label" />
                  </div>
                  <div class="row-right">
                    <el-skeleton-item variant="circle" class="skeleton-avatar" />
                    <el-skeleton-item variant="text" class="skeleton-tip" />
                  </div>
                </div>

                <div v-for="index in skeletonRowCount" :key="index" class="info-row skeleton-row">
                  <div class="row-left">
                    <el-skeleton-item variant="circle" class="skeleton-icon" />
                    <el-skeleton-item variant="text" class="skeleton-label" />
                  </div>
                  <div class="row-right">
                    <el-skeleton-item variant="text" class="skeleton-value" />
                  </div>
                </div>
              </div>
            </template>
          </el-skeleton>

          <div v-else class="info-list">
            <!-- 头像 -->
            <div class="info-row avatar-row">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Camera />
                </el-icon>
                <span class="row-label">头像</span>
              </div>
              <div class="row-right">
                <div class="avatar-preview">
                  <el-avatar :size="72" :src="form.avatarUrl" class="profile-avatar">
                    {{ form.username?.slice(0, 1) || 'K' }}
                  </el-avatar>
                  <div class="upload-overlay">
                    <el-icon class="upload-icon">
                      <Camera />
                    </el-icon>
                  </div>
                  <input type="file" ref="fileInput" class="file-input" accept="image/*" @change="handleAvatarChange" />
                </div>
                <span class="upload-tip">点击上传头像</span>
              </div>
            </div>

            <!-- 用户 ID -->
            <div class="info-row disabled">
              <div class="row-left">
                <el-icon class="row-icon">
                  <User />
                </el-icon>
                <span class="row-label">用户 ID</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ user?.uuid || '—' }}</span>
              </div>
            </div>

            <!-- 账户状态 -->
            <div class="info-row disabled">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Lock />
                </el-icon>
                <span class="row-label">账户状态</span>
              </div>
              <div class="row-right">
                <el-tag type="success" class="status-tag">正常</el-tag>
              </div>
            </div>

            <!-- 用户名 -->
            <div class="info-row" @click="openEditDialog('username')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <User />
                </el-icon>
                <span class="row-label">用户名</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ form.username || '—' }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- 邮箱（只读） -->
            <div class="info-row disabled">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Message />
                </el-icon>
                <span class="row-label">邮箱</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ form.email || '—' }}</span>
                <el-icon class="row-arrow" style="opacity: 0.3;">
                  <Lock />
                </el-icon>
              </div>
            </div>

            <!-- 真实姓名 -->
            <div class="info-row" @click="openEditDialog('realName')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Iphone />
                </el-icon>
                <span class="row-label">真实姓名</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ form.realName || '—' }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- 性别 -->
            <div class="info-row" @click="openEditDialog('gender')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <User />
                </el-icon>
                <span class="row-label">性别</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ genderText(form.gender) }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- 出生日期 -->
            <div class="info-row" @click="openEditDialog('birthday')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Calendar />
                </el-icon>
                <span class="row-label">出生日期</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ form.birthday || '—' }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- 地区 -->
            <div class="info-row" @click="openEditDialog('region')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Location />
                </el-icon>
                <span class="row-label">地区</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ regionText(form.region) }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- 个人简介 -->
            <div class="info-row" @click="openEditDialog('bio')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Document />
                </el-icon>
                <span class="row-label">个人简介</span>
              </div>
              <div class="row-right">
                <span class="row-value bio-preview">{{ form.bio || '—' }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" :title="editDialogTitle" width="500px" @close="resetEditDialog">
      <el-form ref="editFormRef" :model="editForm" :rules="getFieldRules()" label-width="80px" @submit.prevent>
        <!-- 用户名编辑 -->
        <el-form-item v-if="editingField === 'username'" label="用户名" prop="value">
          <el-input v-model="editForm.value" placeholder="请输入用户名" maxlength="20" clearable>
            <template #suffix>
              <span class="char-count">{{ editForm.value?.length || 0 }}/20</span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 真实姓名编辑 -->
        <el-form-item v-if="editingField === 'realName'" label="真实姓名" prop="value">
          <el-input v-model="editForm.value" placeholder="请输入真实姓名" maxlength="30" clearable />
        </el-form-item>

        <!-- 性别编辑 -->
        <el-form-item v-if="editingField === 'gender'" label="性别" prop="value">
          <el-select v-model="editForm.value" placeholder="请选择性别" clearable>
            <el-option label="男" value="male" />
            <el-option label="女" value="female" />
            <el-option label="保密" value="secret" />
          </el-select>
        </el-form-item>

        <!-- 出生日期编辑 -->
        <el-form-item v-if="editingField === 'birthday'" label="出生日期" prop="value">
          <el-date-picker v-model="editForm.value" type="date" placeholder="请选择出生日期" value-format="YYYY-MM-DD" />
        </el-form-item>

        <!-- 地区编辑 -->
        <el-form-item v-if="editingField === 'region'" label="地区" prop="value">
          <el-select v-model="editForm.value" placeholder="请选择地区" clearable>
            <el-option label="中国" value="CN" />
            <el-option label="美国" value="US" />
            <el-option label="日本" value="JP" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <!-- 个人简介编辑 -->
        <el-form-item v-if="editingField === 'bio'" label="个人简介" prop="value">
          <el-input v-model="editForm.value" type="textarea" placeholder="请输入个人简介" maxlength="200" show-word-limit
            rows="4" />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleEditSave" :loading="submitLoading">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Camera, Message, ArrowRight, Lock, Iphone, Calendar, Location, Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateUserProfile, type UserDetails } from '@/api/auth'
import { storeToRefs } from 'pinia'

type ValidationRule = {
  required?: boolean
  message: string
  trigger: string
  min?: number
  max?: number
  pattern?: RegExp
}

const userStore = useUserStore()
const { user, detailsLoading } = storeToRefs(userStore)
const fileInput = ref<HTMLInputElement>()
const editFormRef = ref()
const submitLoading = ref(false)

// 编辑对话框相关
const editDialogVisible = ref(false)
const editingField = ref<string | null>(null)
const editForm = reactive({
  value: ''
})

const skeletonRowCount = 9

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

// 性别文本转换
const genderText = (value: string | null | undefined) => {
  if (!value) return '—'
  const genderMap: Record<string, string> = {
    male: '男',
    female: '女',
    secret: '保密'
  }
  return genderMap[value] || '—'
}

// 地区文本转换
const regionText = (value: string | null | undefined) => {
  if (!value) return '—'
  const regionMap: Record<string, string> = {
    CN: '中国',
    US: '美国',
    JP: '日本',
    OTHER: '其他'
  }
  return regionMap[value] || '—'
}

// 获取字段对应的编辑对话框标题
const editDialogTitle = computed(() => {
  const titleMap: Record<string, string> = {
    username: '编辑用户名',
    realName: '编辑真实姓名',
    gender: '编辑性别',
    birthday: '编辑出生日期',
    region: '编辑地区',
    bio: '编辑个人简介'
  }
  return titleMap[editingField.value || ''] || '编辑'
})

// 获取字段对应的验证规则
const getFieldRules = (): { value: ValidationRule[] } => {
  const rulesMap: Record<string, ValidationRule[]> = {
    username: [
      { required: true, message: '用户名不能为空', trigger: 'blur' },
      { min: 3, max: 20, message: '用户名长度应为 3-20 个字符', trigger: 'blur' },
      {
        message: '用户名格式不正确（3-20字符，字母数字下划线连字符或简体中文）',
        trigger: 'blur',

        pattern: /^[A-Za-z0-9_\-\u4e00-\u9fa5]{3,20}$/
      }
    ],
    realName: [
      { max: 30, message: '真实姓名长度不超过 30 个字符', trigger: 'blur' }
    ],
    gender: [],
    birthday: [],
    region: [],
    bio: []
  }
  return { value: rulesMap[editingField.value || ''] || [] }
}

// 初始化表单数据
onMounted(async () => {
  // 清除缓存，确保每次访问时都获取最新的详细信息
  userStore.clearUserDetailsCache()

  // 然后获取详细信息
  try {
    const details = await userStore.fetchUserDetails() as UserDetails
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
    } else {
      ElMessage.error('获取的详细信息格式不正确')
    }
  } catch {
    ElMessage.error('获取详细信息失败')
  }
})

// 打开编辑对话框
const openEditDialog = (field: string) => {
  editingField.value = field
  editForm.value = form[field as keyof typeof form] || ''
  editDialogVisible.value = true
}

// 重置编辑对话框
const resetEditDialog = () => {
  editingField.value = null
  editForm.value = ''
  editFormRef.value?.clearValidate()
}

// 保存编辑
const handleEditSave = async () => {
  try {
    await editFormRef.value.validate()

    if (!editingField.value) return

    const fieldName = editingField.value
    const oldValue = form[fieldName as keyof typeof form]
    const newValue = editForm.value

    if (newValue === '') {
      ElMessage.error('新值不能为空')
      return
    }

    // 检查是否有实际改动
    if (oldValue === newValue) {
      ElMessage.info('没有任何改动')
      editDialogVisible.value = false
      return
    }

    submitLoading.value = true

    // 构建更新数据
    let updateKey: 'username' | 'realName' | 'gender' | 'birthDate' | 'region' | 'bio'

    if (fieldName === 'birthday') {
      updateKey = 'birthDate'
    } else {
      updateKey = fieldName as 'username' | 'realName' | 'gender' | 'region' | 'bio'
    }

    // 调用更新接口
    const result = await updateUserProfile({
      key: updateKey,
      value: newValue
    })

    // 更新表单数据
    form[fieldName as keyof typeof form] = newValue

    // 同步更新用户信息到 store
    userStore.user = result
    userStore.userDetails = result

    ElMessage.success('信息保存成功')
    editDialogVisible.value = false
    submitLoading.value = false
  } catch {
    ElMessage.error('保存失败，请检查信息')
    submitLoading.value = false
  }
}

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
  reader.onload = async (e) => {
    const avatarUrl = e.target?.result as string
    if (!avatarUrl) return

    try {
      const result = await updateUserProfile({
        key: 'avatarUrl',
        value: avatarUrl
      })
      form.avatarUrl = avatarUrl
      userStore.user = result
      userStore.userDetails = result
      ElMessage.success('头像已更新')
    } catch {
      ElMessage.error('头像更新失败')
    }
  }
  reader.readAsDataURL(file)
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

.profile-grid {
  align-items: stretch;
}

.profile-grid :deep(.el-col) {
  display: flex;
}

.profile-grid :deep(.el-card) {
  width: 100%;
}

.avatar-card {
  display: flex;
  align-items: center;
  min-height: 100%;
  padding: 8px;
  background: linear-gradient(180deg, var(--el-bg-color) 0%, var(--el-fill-color-extra-light) 100%);
}

.avatar-section {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 18px;
  padding: 8px 6px;
}

.avatar-preview {
  position: relative;
  cursor: pointer;
  width: 72px;
  height: 72px;
}

.profile-avatar {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  transition: all 0.25s ease;
  border: 2px solid var(--el-border-color-lighter);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.12);
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
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
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
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: left;
}

.avatar-row .row-right {
  gap: 10px;
}

.avatar-info {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}

.info-item .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.info-item .value {
  font-size: 13px;
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  max-width: 180px;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}

.status-tag {
  border-radius: 999px;
  padding: 0 10px;
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

.skeleton-row {
  cursor: default;
}

.skeleton-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
}

.skeleton-label {
  width: 80px;
  height: 14px;
}

.skeleton-value {
  width: 160px;
  height: 14px;
}

.skeleton-avatar {
  width: 72px;
  height: 72px;
  border-radius: 20px;
}

.skeleton-tip {
  width: 88px;
  height: 12px;
}

/* 信息列表样式 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0 -16px;
  padding: 0;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-light);
  user-select: none;
}

.info-row:hover:not(.disabled) {
  background-color: var(--el-fill-color-light);
}

.info-row.disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.row-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.row-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.row-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  min-width: 80px;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: flex-end;
}

.row-value {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: right;
  flex: 1;
  word-break: break-all;
}

.row-value.bio-preview {
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-arrow {
  font-size: 16px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.info-row:hover:not(.disabled) .row-arrow {
  color: var(--el-color-primary);
}

:deep(.el-dialog) {
  border-radius: 12px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--el-border-color-light);
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.char-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1024px) {
  .avatar-card {
    min-height: auto;
  }

  .avatar-section {
    flex-direction: row;
    justify-content: flex-start;
    gap: 16px;
  }

  .avatar-preview {
    width: 64px;
    height: 64px;
  }

  .profile-avatar {
    width: 64px;
    height: 64px;
    border-radius: 16px;
  }

  .avatar-info {
    flex: 1;
    margin-left: 16px;
  }

  .info-item .value {
    max-width: 100%;
  }
}

@media (max-width: 640px) {
  .profile-grid :deep(.el-col) {
    display: block;
  }

  .avatar-section {
    flex-direction: column;
    align-items: center;
  }

  .avatar-info {
    width: 100%;
  }

  .info-item {
    padding: 10px 12px;
  }

  .info-row {
    flex-direction: column;
    align-items: flex-start;
    padding: 12px 16px;
  }

  .row-right {
    width: 100%;
    margin-top: 6px;
    justify-content: flex-start;
  }

  .row-value {
    text-align: left;
  }

  .row-value.bio-preview {
    max-width: 100%;
  }
}
</style>
