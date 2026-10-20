<template>
  <div class="page">
    <div class="page-heading"><div><h1>UnionID 审核</h1><p>审核网站与移动应用获取同一开发者账号下统一用户标识的申请。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <section class="panel">
      <div class="toolbar"><el-select v-model="status" aria-label="申请状态" @change="filterChanged"><el-option label="全部状态" value="all" /><el-option v-for="(label, key) in labels" :key="key" :label="label" :value="key" /></el-select><span class="muted">共 {{ total }} 条申请</span></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table v-else v-loading="loading" :data="applications" row-key="id" empty-text="暂无申请">
        <el-table-column label="应用" min-width="200"><template #default="{ row }"><strong>{{ row.appName }}</strong><div class="muted mono">{{ row.appId }}</div><el-tag size="small" effect="plain">{{ row.applicationType === 'mobile' ? '移动应用' : '网站' }}</el-tag></template></el-table-column>
        <el-table-column label="开发者" prop="ownerUsername" min-width="140" />
        <el-table-column label="申请理由" prop="reason" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'approved' ? 'success' : 'info'">{{ labels[row.status as UnionidApplicationStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="申请时间" width="170"><template #default="{ row }">{{ date(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="showReview(row)">{{ row.status === 'pending' ? '审核' : '详情' }}</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" class="pagination" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </section>
    <el-dialog v-model="dialogVisible" title="UnionID 申请" width="580px" :close-on-click-modal="false">
      <template v-if="selected">
        <el-descriptions :column="1" border><el-descriptions-item label="应用">{{ selected.appName }} · {{ selected.appId }}</el-descriptions-item><el-descriptions-item label="开发者">{{ selected.ownerUsername }}</el-descriptions-item><el-descriptions-item label="状态">{{ labels[selected.status] }}</el-descriptions-item><el-descriptions-item label="申请理由">{{ selected.reason }}</el-descriptions-item><el-descriptions-item v-if="selected.reviewReason" label="审核意见">{{ selected.reviewReason }}</el-descriptions-item><el-descriptions-item v-if="selected.reviewedAt" label="审核时间">{{ date(selected.reviewedAt) }}</el-descriptions-item></el-descriptions>
        <el-alert v-if="selected.status === 'approved'" title="撤销后停止继续提供 UnionID；已交付的标识无法收回。" type="warning" :closable="false" />
        <el-form v-if="['pending', 'approved'].includes(selected.status)" label-position="top"><el-form-item label="审核意见" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写批准、驳回或撤销的原因" /></el-form-item></el-form>
        <el-alert v-if="reviewError" :title="reviewError" type="error" :closable="false" />
      </template>
      <template #footer><el-button :disabled="saving" @click="dialogVisible = false">关闭</el-button><template v-if="selected?.status === 'pending'"><el-button type="danger" :disabled="saving" @click="review('reject')">驳回</el-button><el-button type="primary" :loading="saving" @click="review('approve')">批准</el-button></template><el-button v-if="selected?.status === 'approved'" type="danger" :loading="saving" @click="review('revoke')">撤销权限</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { consoleApi, type UnionidApplication, type UnionidApplicationStatus } from '@/api/console'
import { VerificationCancelledError, withConsoleVerification } from '@/utils/consoleVerification'
const labels: Record<UnionidApplicationStatus, string> = { withdrawn: '已撤回', pending: '待审核', approved: '已开通', rejected: '已驳回', revoked: '已撤销' }
const applications = ref<UnionidApplication[]>([])
const status = ref<UnionidApplicationStatus | 'all'>('pending')
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const selected = ref<UnionidApplication | null>(null)
const reason = ref('')
const saving = ref(false)
const reviewError = ref('')
let requestGeneration = 0
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
async function load() {
  const generation = ++requestGeneration
  loading.value = true
  error.value = ''
  try { const result = await consoleApi.unionidApplications({ page: page.value - 1, size, status: status.value }); if (generation === requestGeneration) { applications.value = result.items; total.value = result.total } }
  catch (failure) { if (generation === requestGeneration) error.value = failure instanceof Error ? failure.message : '加载失败' }
  finally { if (generation === requestGeneration) loading.value = false }
}
function filterChanged() { page.value = 1; void load() }
function showReview(application: UnionidApplication) { selected.value = application; reason.value = ''; reviewError.value = ''; dialogVisible.value = true }
async function review(decision: 'approve' | 'reject' | 'revoke') {
  if (!selected.value || saving.value) return
  if (!reason.value.trim()) { reviewError.value = '请填写审核意见'; return }
  const id = selected.value.id
  const input = { decision, reason: reason.value.trim() }
  saving.value = true
  reviewError.value = ''
  try { await withConsoleVerification(() => consoleApi.reviewUnionidApplication(id, input)); dialogVisible.value = false; ElMessage.success('审核状态已更新'); await load() }
  catch (failure) { if (!(failure instanceof VerificationCancelledError)) reviewError.value = failure instanceof Error ? failure.message : '审核失败，请刷新后重试' }
  finally { saving.value = false }
}
onMounted(load)
</script>
<style scoped>
.toolbar { display: flex; gap: 16px; align-items: center; margin-bottom: 20px; }
.toolbar .el-select { width: 160px; }
.el-dialog .el-alert, .el-form { margin-top: 20px; }
.el-descriptions { overflow-wrap: anywhere; }
</style>
