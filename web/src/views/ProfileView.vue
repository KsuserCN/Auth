<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">{{ t('ui.mainLayout.message010') }}</h1>
        <p class="page-subtitle">{{ t('ui.profile.message001') }}</p>
      </div>
    </div>

    <el-row :gutter="16" class="profile-grid">
      <el-col :xs="24" :lg="24">
        <el-card class="card profile-card" shadow="never">
          <div class="card-title">
            <el-icon>
              <User />
            </el-icon>
            <span>{{ t('ui.profile.message002') }}</span>
          </div>
          <p class="card-description">{{ t('ui.profile.message003') }}</p>

          <!-- 只读信息列表 -->
          <el-skeleton v-if="detailsLoading" animated>
            <template #template>
              <div
                v-for="(rowHeight, index) in skeletonRows"
                :key="index"
                class="skeleton-row"
                :style="{ height: `${rowHeight}px` }"
              >
                <el-skeleton-item variant="text" class="skeleton-item" />
              </div>
            </template>
          </el-skeleton>

          <div v-else class="info-list">
            <div class="profile-identity">
              <button
                class="avatar-preview"
                type="button"
                :aria-label="t('ui.profile.message004')"
                @click="triggerAvatarSelect"
              >
                <el-avatar :size="72" :src="form.avatarUrl" class="profile-avatar">
                  {{ form.username?.slice(0, 1) || 'K' }}
                </el-avatar>
                <span class="upload-overlay">
                  <el-icon class="upload-icon">
                    <Camera />
                  </el-icon>
                </span>
                <input
                  type="file"
                  ref="fileInput"
                  class="file-input"
                  accept="image/*"
                  @change="handleAvatarChange"
                />
              </button>
              <div class="identity-copy">
                <button class="identity-name" type="button" @click="openEditDialog('username')">
                  {{ form.username || t('ui.profile.message005') }}
                  <el-icon><Edit /></el-icon>
                </button>
                <div class="identity-email">
                  <el-icon><Message /></el-icon>
                  <span>{{ form.email || t('ui.home.message003') }}</span>
                </div>
                <span class="upload-tip">{{ t('ui.profile.message006') }}</span>
              </div>
            </div>

            <!-- 用户 ID -->
            <div class="info-row disabled">
              <div class="row-left">
                <el-icon class="row-icon">
                  <User />
                </el-icon>
                <span class="row-label">{{ t('ui.profile.message007') }}</span>
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
                <span class="row-label">{{ t('ui.profile.message008') }}</span>
              </div>
              <div class="row-right">
                <el-tag type="success" class="status-tag">{{ t('ui.profile.message009') }}</el-tag>
              </div>
            </div>

            <!-- 账号认证 -->
            <div class="info-row disabled">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Lock />
                </el-icon>
                <span class="row-label">{{ t('ui.openPlatform.message007') }}</span>
              </div>
              <div class="row-right">
                <el-tag :type="verificationTagType" effect="light">{{ verificationLabel }}</el-tag>
              </div>
            </div>

            <!-- 真实姓名 -->
            <div class="info-row" @click="openEditDialog('realName')">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Iphone />
                </el-icon>
                <span class="row-label">{{ t('ui.profile.message010') }}</span>
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
                <span class="row-label">{{ t('ui.profile.message011') }}</span>
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
                <span class="row-label">{{ t('ui.profile.message012') }}</span>
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
                <span class="row-label">{{ t('ui.profile.message013') }}</span>
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
                <span class="row-label">{{ t('ui.profile.message014') }}</span>
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

    <el-dialog
      v-model="avatarCropDialogVisible"
      :title="t('ui.profile.message015')"
      width="520px"
      :close-on-click-modal="false"
      @close="resetAvatarCropDialog"
    >
      <div class="avatar-crop-wrapper">
        <div class="crop-stage" @mousedown="startDragImage" @touchstart.prevent="startDragImage">
          <img
            v-if="cropImageUrl"
            :src="cropImageUrl"
            class="crop-image"
            :style="cropImageStyle"
            draggable="false"
          />
          <div class="crop-mask crop-mask-top" />
          <div class="crop-mask crop-mask-bottom" />
          <div class="crop-mask crop-mask-left" />
          <div class="crop-mask crop-mask-right" />
          <div class="crop-frame" />
        </div>
        <div class="crop-slider-row">
          <span class="slider-label">{{ t('ui.openPlatform.message066') }}</span>
          <el-slider
            v-model="cropScale"
            :min="cropMinScale"
            :max="cropMaxScale"
            :step="0.01"
            @input="handleCropScaleChange"
          />
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="avatarCropDialogVisible = false">{{
            t('ui.addPasskey.message009')
          }}</el-button>
          <el-button type="primary" :loading="avatarUploading" @click="confirmAvatarCrop">
            {{ t('ui.openPlatform.message067') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      class="profile-edit-dialog"
      :title="editDialogTitle"
      width="480px"
      align-center
      @close="resetEditDialog"
    >
      <p class="edit-dialog-hint">{{ t('ui.profile.message016') }}</p>
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="getFieldRules()"
        label-position="top"
        @submit.prevent
      >
        <!-- 用户名编辑 -->
        <el-form-item
          v-if="editingField === 'username'"
          :label="t('ui.deleteAccount.message012')"
          prop="value"
        >
          <el-input
            v-model="editForm.value"
            :placeholder="t('ui.deleteAccount.message021')"
            maxlength="20"
            clearable
          >
            <template #suffix>
              <span class="char-count">{{ editForm.value?.length || 0 }}/20</span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 真实姓名编辑 -->
        <el-form-item
          v-if="editingField === 'realName'"
          :label="t('ui.profile.message010')"
          prop="value"
        >
          <el-input
            v-model="editForm.value"
            :placeholder="t('ui.profile.message017')"
            maxlength="30"
            clearable
          />
        </el-form-item>

        <!-- 性别编辑 -->
        <el-form-item
          v-if="editingField === 'gender'"
          :label="t('ui.profile.message011')"
          prop="value"
        >
          <el-select v-model="editForm.value" :placeholder="t('ui.profile.message018')" clearable>
            <el-option :label="t('ui.profile.message019')" value="male" />
            <el-option :label="t('ui.profile.message020')" value="female" />
            <el-option :label="t('ui.profile.message021')" value="secret" />
          </el-select>
        </el-form-item>

        <!-- 出生日期编辑 -->
        <el-form-item
          v-if="editingField === 'birthday'"
          :label="t('ui.profile.message012')"
          prop="value"
        >
          <el-date-picker
            v-model="editForm.value"
            type="date"
            :placeholder="t('ui.profile.message022')"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <!-- 地区编辑 -->
        <el-form-item
          v-if="editingField === 'region'"
          :label="t('ui.profile.message013')"
          prop="value"
        >
          <el-select v-model="editForm.value" :placeholder="t('ui.profile.message023')" clearable>
            <el-option :label="t('ui.profile.message024')" value="CN" />
            <el-option :label="t('ui.profile.message025')" value="US" />
            <el-option :label="t('ui.profile.message026')" value="JP" />
            <el-option :label="t('ui.profile.message027')" value="OTHER" />
          </el-select>
        </el-form-item>

        <!-- 个人简介编辑 -->
        <el-form-item
          v-if="editingField === 'bio'"
          :label="t('ui.profile.message014')"
          prop="value"
        >
          <el-input
            v-model="editForm.value"
            type="textarea"
            :placeholder="t('ui.profile.message028')"
            maxlength="200"
            show-word-limit
            :autosize="{ minRows: 4, maxRows: 7 }"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">{{
            t('ui.addPasskey.message009')
          }}</el-button>
          <el-button type="primary" @click="handleEditSave" :loading="submitLoading">{{
            t('ui.profile.message029')
          }}</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/i18n'

import { ref, reactive, onMounted, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import {
  User,
  Camera,
  Message,
  ArrowRight,
  Lock,
  Iphone,
  Calendar,
  Location,
  Document,
  Edit,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateUserProfile, uploadAvatar, type UserDetails } from '@/api/auth'
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
const avatarCropDialogVisible = ref(false)
const avatarUploading = ref(false)

const CROP_STAGE_SIZE = 320
const CROP_FRAME_SIZE = 220
const CROP_STAGE_CENTER = CROP_STAGE_SIZE / 2
const CROP_FRAME_LEFT = (CROP_STAGE_SIZE - CROP_FRAME_SIZE) / 2
const CROP_FRAME_TOP = (CROP_STAGE_SIZE - CROP_FRAME_SIZE) / 2
const MAX_AVATAR_SIZE = 3 * 1024 * 1024

const cropImageUrl = ref('')
const cropImageNaturalWidth = ref(0)
const cropImageNaturalHeight = ref(0)
const cropScale = ref(1)
const cropMinScale = ref(1)
const cropMaxScale = ref(3)
const cropOffsetX = ref(0)
const cropOffsetY = ref(0)
const selectedAvatarFile = ref<File | null>(null)

const dragState = reactive({
  active: false,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
})

// 编辑对话框相关
const editDialogVisible = ref(false)
const editingField = ref<string | null>(null)
const editForm = reactive({
  value: '',
})

const skeletonRows = [101, 40, 40, 40, 40, 40, 40, 40, 40, 40]

const form = reactive({
  username: '',
  email: '',
  realName: '',
  gender: '',
  birthday: '',
  phone: '',
  region: '',
  bio: '',
  avatarUrl: '',
})

const verificationLabel = computed(() => {
  const type = user.value?.verificationType
  if (type === 'admin') return t('ui.mainLayout.message017')
  if (type === 'enterprise') return t('ui.mainLayout.message018')
  if (type === 'personal') return t('ui.mainLayout.message019')
  return t('ui.openPlatform.message070')
})

const verificationTagType = computed(() => {
  const type = user.value?.verificationType
  if (type === 'admin') return 'danger'
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
})

// 性别文本转换
const genderText = (value: string | null | undefined) => {
  if (!value) return '—'
  const genderMap: Record<string, string> = {
    male: t('ui.profile.message019'),
    female: t('ui.profile.message020'),
    secret: t('ui.profile.message021'),
  }
  return genderMap[value] || '—'
}

// 地区文本转换
const regionText = (value: string | null | undefined) => {
  if (!value) return '—'
  const regionMap: Record<string, string> = {
    CN: t('ui.profile.message024'),
    US: t('ui.profile.message025'),
    JP: t('ui.profile.message026'),
    OTHER: t('ui.profile.message027'),
  }
  return regionMap[value] || '—'
}

// 获取字段对应的编辑对话框标题
const editDialogTitle = computed(() => {
  const titleMap: Record<string, string> = {
    username: t('ui.profile.message030'),
    realName: t('ui.profile.message031'),
    gender: t('ui.profile.message032'),
    birthday: t('ui.profile.message033'),
    region: t('ui.profile.message034'),
    bio: t('ui.profile.message035'),
  }
  return titleMap[editingField.value || ''] || t('ui.openPlatform.message034')
})

// 获取字段对应的验证规则
const getFieldRules = (): { value: ValidationRule[] } => {
  const rulesMap: Record<string, ValidationRule[]> = {
    username: [
      { required: true, message: t('ui.deleteAccount.message022'), trigger: 'blur' },
      { min: 3, max: 30, message: t('ui.profile.message036'), trigger: 'blur' },
      {
        message: t('ui.profile.message037'),
        trigger: 'blur',

        pattern: /^[A-Za-z0-9_\-\u4e00-\u9fa5]{3,20}$/,
      },
    ],
    realName: [{ max: 30, message: t('ui.profile.message038'), trigger: 'blur' }],
    gender: [],
    birthday: [],
    region: [],
    bio: [],
  }
  return { value: rulesMap[editingField.value || ''] || [] }
}

// 初始化表单数据
onMounted(async () => {
  // 清除缓存，确保每次访问时都获取最新的详细信息
  userStore.clearUserDetailsCache()

  // 然后获取详细信息
  try {
    const details = (await userStore.fetchUserDetails()) as UserDetails
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
      ElMessage.error(t('ui.profile.message039'))
    }
  } catch {
    ElMessage.error(t('ui.profile.message040'))
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
      ElMessage.error(t('ui.profile.message041'))
      return
    }

    // 检查是否有实际改动
    if (oldValue === newValue) {
      ElMessage.info(t('ui.profile.message042'))
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
      value: newValue,
    })

    // 更新表单数据
    form[fieldName as keyof typeof form] = newValue

    // 同步更新用户信息到 store
    userStore.user = result
    userStore.userDetails = result

    ElMessage.success(t('ui.profile.message043'))
    editDialogVisible.value = false
    submitLoading.value = false
  } catch {
    ElMessage.error(t('ui.profile.message044'))
    submitLoading.value = false
  }
}

const handleAvatarChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.error(t('ui.openPlatform.message100'))
    target.value = ''
    return
  }

  // 验证文件大小（最大 3MB）
  if (file.size > MAX_AVATAR_SIZE) {
    ElMessage.error(t('ui.openPlatform.message101'))
    target.value = ''
    return
  }

  selectedAvatarFile.value = file
  openAvatarCropDialog(file)
}

const triggerAvatarSelect = () => {
  fileInput.value?.click()
}

const openAvatarCropDialog = (file: File) => {
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  const objectUrl = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    cropImageNaturalWidth.value = image.naturalWidth
    cropImageNaturalHeight.value = image.naturalHeight
    initializeCropState()
    cropImageUrl.value = objectUrl
    avatarCropDialogVisible.value = true
  }
  image.onerror = () => {
    URL.revokeObjectURL(objectUrl)
    ElMessage.error(t('ui.openPlatform.message102'))
  }
  image.src = objectUrl
}

const initializeCropState = () => {
  if (!cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return

  const minScaleByFrame = Math.max(
    CROP_FRAME_SIZE / cropImageNaturalWidth.value,
    CROP_FRAME_SIZE / cropImageNaturalHeight.value,
  )

  cropMinScale.value = minScaleByFrame
  cropMaxScale.value = Math.max(minScaleByFrame * 4, minScaleByFrame + 1)
  cropScale.value = minScaleByFrame
  cropOffsetX.value = CROP_STAGE_CENTER - (cropImageNaturalWidth.value * cropScale.value) / 2
  cropOffsetY.value = CROP_STAGE_CENTER - (cropImageNaturalHeight.value * cropScale.value) / 2
  clampCropOffset()
}

const clampCropOffset = () => {
  if (!cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return

  const displayWidth = cropImageNaturalWidth.value * cropScale.value
  const displayHeight = cropImageNaturalHeight.value * cropScale.value
  const minX = CROP_FRAME_LEFT + CROP_FRAME_SIZE - displayWidth
  const maxX = CROP_FRAME_LEFT
  const minY = CROP_FRAME_TOP + CROP_FRAME_SIZE - displayHeight
  const maxY = CROP_FRAME_TOP

  cropOffsetX.value = Math.min(Math.max(cropOffsetX.value, minX), maxX)
  cropOffsetY.value = Math.min(Math.max(cropOffsetY.value, minY), maxY)
}

const startDragImage = (event: MouseEvent | TouchEvent) => {
  if (!cropImageUrl.value) return

  dragState.active = true
  const point = getEventPoint(event)
  dragState.startX = point.x
  dragState.startY = point.y
  dragState.startOffsetX = cropOffsetX.value
  dragState.startOffsetY = cropOffsetY.value

  window.addEventListener('mousemove', handleDragging)
  window.addEventListener('mouseup', stopDragImage)
  window.addEventListener('touchmove', handleDragging, { passive: false })
  window.addEventListener('touchend', stopDragImage)
}

const handleDragging = (event: MouseEvent | TouchEvent) => {
  if (!dragState.active) return
  if (event instanceof TouchEvent) {
    event.preventDefault()
  }

  const point = getEventPoint(event)
  const deltaX = point.x - dragState.startX
  const deltaY = point.y - dragState.startY
  cropOffsetX.value = dragState.startOffsetX + deltaX
  cropOffsetY.value = dragState.startOffsetY + deltaY
  clampCropOffset()
}

const stopDragImage = () => {
  dragState.active = false
  window.removeEventListener('mousemove', handleDragging)
  window.removeEventListener('mouseup', stopDragImage)
  window.removeEventListener('touchmove', handleDragging)
  window.removeEventListener('touchend', stopDragImage)
}

const getEventPoint = (event: MouseEvent | TouchEvent) => {
  if (event instanceof TouchEvent) {
    const touch = event.touches[0] || event.changedTouches[0]
    return {
      x: touch?.clientX || 0,
      y: touch?.clientY || 0,
    }
  }
  return {
    x: event.clientX,
    y: event.clientY,
  }
}

const handleCropScaleChange = (value: number | number[]) => {
  const nextValue = Array.isArray(value) ? value[0] : value
  if (!nextValue || !cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return

  const prevScale = cropScale.value
  const prevCenterX = CROP_STAGE_CENTER
  const prevCenterY = CROP_STAGE_CENTER
  const imagePointX = (prevCenterX - cropOffsetX.value) / prevScale
  const imagePointY = (prevCenterY - cropOffsetY.value) / prevScale

  cropScale.value = nextValue
  cropOffsetX.value = prevCenterX - imagePointX * cropScale.value
  cropOffsetY.value = prevCenterY - imagePointY * cropScale.value
  clampCropOffset()
}

const confirmAvatarCrop = async () => {
  if (!cropImageUrl.value || !selectedAvatarFile.value) return

  try {
    avatarUploading.value = true
    const blob = await exportCroppedAvatarBlob()
    const result = await uploadAvatar(blob)

    form.avatarUrl = result.avatarUrl || ''
    userStore.user = result
    userStore.userDetails = result
    avatarCropDialogVisible.value = false
    ElMessage.success(t('ui.profile.message045'))
  } catch {
    ElMessage.error(t('ui.profile.message046'))
  } finally {
    avatarUploading.value = false
  }
}

const exportCroppedAvatarBlob = async (): Promise<Blob> => {
  return await new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      const sourceX = (CROP_FRAME_LEFT - cropOffsetX.value) / cropScale.value
      const sourceY = (CROP_FRAME_TOP - cropOffsetY.value) / cropScale.value
      const sourceSize = CROP_FRAME_SIZE / cropScale.value

      const canvas = document.createElement('canvas')
      canvas.width = 512
      canvas.height = 512
      const context = canvas.getContext('2d')
      if (!context) {
        reject(new Error('canvas_context_unavailable'))
        return
      }

      context.drawImage(
        image,
        sourceX,
        sourceY,
        sourceSize,
        sourceSize,
        0,
        0,
        canvas.width,
        canvas.height,
      )

      canvas.toBlob(
        (blob) => {
          if (!blob) {
            reject(new Error('crop_failed'))
            return
          }
          resolve(blob)
        },
        'image/jpeg',
        0.92,
      )
    }
    image.onerror = () => reject(new Error('image_load_failed'))
    image.src = cropImageUrl.value
  })
}

const resetAvatarCropDialog = () => {
  stopDragImage()
  avatarUploading.value = false
  selectedAvatarFile.value = null
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  cropImageUrl.value = ''
  cropImageNaturalWidth.value = 0
  cropImageNaturalHeight.value = 0
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const cropImageStyle = computed(() => ({
  width: `${cropImageNaturalWidth.value * cropScale.value}px`,
  height: `${cropImageNaturalHeight.value * cropScale.value}px`,
  transform: `translate(${cropOffsetX.value}px, ${cropOffsetY.value}px)`,
}))

onBeforeUnmount(() => {
  resetAvatarCropDialog()
})
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
  box-shadow: 0 4px 14px color-mix(in srgb, var(--el-text-color-primary) 4%, transparent);
}

.profile-card :deep(.el-card__body) {
  padding: 22px;
}

.card-description {
  margin: -10px 0 18px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
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
  flex: 0 0 72px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
}

.profile-avatar {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  transition: all 0.25s ease;
  border: 2px solid var(--el-border-color-lighter);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12);
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
  border-radius: 6px;
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
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.profile-identity {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px;
  margin-bottom: 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: linear-gradient(110deg, var(--el-fill-color-extra-light), var(--el-bg-color) 72%);
}

.identity-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  gap: 5px;
}

.identity-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-size: 18px;
  font-weight: 650;
  cursor: pointer;
}

.identity-name .el-icon {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.identity-name:hover .el-icon {
  opacity: 1;
}

.identity-email {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.identity-email span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
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
  margin-bottom: 12px;
}

.skeleton-item {
  width: 100%;
  height: 100%;
}

/* 信息列表样式 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  user-select: none;
}

.info-row:hover:not(.disabled) {
  background-color: var(--el-fill-color-extra-light);
  border-color: var(--el-color-primary-light-7);
  transform: translateY(-1px);
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
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
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
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);
}

:deep(.el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

:deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

:deep(.el-dialog__headerbtn:hover) {
  background: var(--el-fill-color-light);
}

:deep(.el-dialog__body) {
  padding: 20px 24px 8px;
}

:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  font-weight: 500;
  padding-bottom: 7px;
}

:deep(.el-input),
:deep(.el-select),
:deep(.el-date-editor.el-input),
:deep(.el-date-editor.el-input__wrapper) {
  width: 100%;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
  transition: box-shadow 0.2s ease;
}

:deep(.el-input__wrapper:hover),
:deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5) inset;
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focused) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
}

:deep(.el-textarea__inner) {
  border-radius: 10px;
  padding: 11px 12px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

:deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
}

.edit-dialog-hint {
  margin: 0 0 18px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

:deep(.el-dialog__footer) {
  padding: 12px 24px 22px;
}

.dialog-footer :deep(.el-button) {
  min-width: 88px;
  height: 38px;
  border-radius: 10px;
  font-weight: 600;
}

.char-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.avatar-crop-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.crop-stage {
  position: relative;
  width: 320px;
  height: 320px;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 12px;
  background: #111827;
  touch-action: none;
  cursor: move;
}

.crop-image {
  position: absolute;
  top: 0;
  left: 0;
  user-select: none;
}

.crop-mask {
  position: absolute;
  background: rgba(0, 0, 0, 0.55);
  pointer-events: none;
}

.crop-mask-top {
  top: 0;
  left: 0;
  width: 100%;
  height: 50px;
}

.crop-mask-bottom {
  bottom: 0;
  left: 0;
  width: 100%;
  height: 50px;
}

.crop-mask-left {
  top: 50px;
  left: 0;
  width: 50px;
  height: 220px;
}

.crop-mask-right {
  top: 50px;
  right: 0;
  width: 50px;
  height: 220px;
}

.crop-frame {
  position: absolute;
  top: 50px;
  left: 50px;
  width: 220px;
  height: 220px;
  border: 2px solid #ffffff;
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.6);
  pointer-events: none;
}

.crop-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.slider-label {
  min-width: 36px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.crop-slider-row :deep(.el-slider) {
  flex: 1;
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
    flex-basis: 64px;
  }

  .profile-avatar {
    width: 64px;
    height: 64px;
    border-radius: 6px;
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
    align-items: flex-start;
    padding: 11px 12px;
  }

  .profile-identity {
    gap: 14px;
    padding: 14px;
  }

  .avatar-preview,
  .profile-avatar {
    width: 60px;
    height: 60px;
  }

  .avatar-preview {
    flex-basis: 60px;
  }

  .avatar-preview:focus-visible,
  .identity-name:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 3px;
  }

  .identity-name {
    font-size: 16px;
  }

  .row-right {
    min-width: 0;
  }

  .row-value {
    text-align: right;
  }

  .row-value.bio-preview {
    max-width: 100%;
  }

  .profile-card :deep(.el-card__body) {
    padding: 16px;
  }

  :deep(.el-dialog__header) {
    padding: 18px 18px 14px;
  }

  :deep(.el-dialog__body) {
    padding: 16px 18px 4px;
  }

  :deep(.el-dialog__footer) {
    padding: 10px 18px 18px;
  }
}
</style>
