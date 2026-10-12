<template>
  <div class="page">
    <div class="page-heading">
      <div>
        <h1>操作记录</h1>
        <p>查看账号删除、管理员任免和官方应用配置的变更记录。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <section class="panel">
      <div v-if="selectedLogs.length" class="bulk-toolbar">
        <span class="muted">本页已选择 {{ selectedLogs.length }} 条记录</span>
        <el-button size="small" type="primary" @click="exportSelected">导出选中记录</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon /><el-table
        ref="tableRef"
        v-else
        v-loading="loading"
        :data="logs"
        :default-sort="{ prop: 'createdAt', order: 'descending' }"
        empty-text="暂无操作记录"
        @selection-change="onSelectionChange"
        @sort-change="onSortChange"
        ><el-table-column type="selection" width="48" /><el-table-column
          label="时间"
          prop="createdAt"
          min-width="170"
          sortable="custom"
          ><template #default="{ row }">{{
            new Date(row.createdAt).toLocaleString('zh-CN')
          }}</template></el-table-column
        ><el-table-column label="操作" prop="action" min-width="190" sortable="custom"
          ><template #default="{ row }">{{ actionLabel(row.action) }}</template></el-table-column
        ><el-table-column
          label="操作者"
          prop="actorUuid"
          min-width="270"
          sortable="custom"
          ><template #default="{ row }"
            ><span class="mono">{{ row.actorUuid }}</span></template
          ></el-table-column
        ><el-table-column prop="target" label="目标" min-width="250" sortable="custom" /><el-table-column
          prop="ipAddress"
          label="IP 地址"
          width="140"
          sortable="custom"
        /><el-table-column width="90" label="变更详情" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="selected = row">详情</el-button></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-model:current-page="page"
        class="pagination"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </section>
    <el-dialog
      :model-value="!!selected"
      title="操作详情"
      width="620px"
      @update:model-value="selected = null"
      ><template v-if="selected"
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="操作">{{ actionLabel(selected.action) }}</el-descriptions-item
          ><el-descriptions-item label="操作者"
            ><span class="mono">{{ selected.actorUuid }}</span></el-descriptions-item
          ><el-descriptions-item label="目标">{{ selected.target }}</el-descriptions-item
          ><el-descriptions-item label="IP 地址">{{
            selected.ipAddress
          }}</el-descriptions-item></el-descriptions
        >
        <pre>{{ details(selected.details) }}</pre>
      </template></el-dialog
    >
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { consoleApi, type AuditLog } from '@/api/console'
const page = ref(1)
const size = 20
const total = ref(0)
const logs = ref<AuditLog[]>([])
const selected = ref<AuditLog | null>(null)
const tableRef = ref<{ clearSelection: () => void } | null>(null)
const selectedLogs = ref<AuditLog[]>([])
const loading = ref(true)
const error = ref('')
type AuditSortField = 'createdAt' | 'action' | 'actorUuid' | 'target' | 'ipAddress'
const sort = ref<AuditSortField>('createdAt')
const direction = ref<'asc' | 'desc'>('desc')
let loadGeneration = 0
const actionLabel = (action: string) =>
  (
    {
      'user.role.update': '更新用户角色',
      'user.delete': '删除用户账号',
      'official.application.create': '创建官方应用',
      'official.application.update': '更新官方应用',
      'official.application.delete': '删除官方应用',
      'official.application.secret.rotate': '轮换应用密钥',
    } as Record<string, string>
  )[action] ?? action
const details = (raw: string) => {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}
const clearSelection = () => {
  selectedLogs.value = []
  tableRef.value?.clearSelection()
}
const onSelectionChange = (selection: AuditLog[]) => {
  selectedLogs.value = selection
}
const onSortChange = (event: { prop: string; order: 'ascending' | 'descending' | null }) => {
  const prop = event.prop as AuditSortField
  if (['createdAt', 'action', 'actorUuid', 'target', 'ipAddress'].includes(prop) && event.order) {
    sort.value = prop
    direction.value = event.order === 'ascending' ? 'asc' : 'desc'
  } else {
    sort.value = 'createdAt'
    direction.value = 'desc'
  }
  page.value = 1
  void load()
}
const exportSelected = () => {
  if (!selectedLogs.value.length) return
  const quote = (value: unknown) => {
    const raw = String(value ?? '')
    const safe = /^[\s]*[=+\-@]/.test(raw) ? `'${raw}` : raw
    return `"${safe.replace(/"/g, '""')}"`
  }
  const rows = [
    ['时间', '操作', '操作者 UUID', '目标', 'IP 地址', '变更详情'],
    ...selectedLogs.value.map((log) => [
      new Date(log.createdAt).toLocaleString('zh-CN'),
      actionLabel(log.action),
      log.actorUuid,
      log.target,
      log.ipAddress,
      log.details,
    ]),
  ]
  const content = `\uFEFF${rows.map((row) => row.map(quote).join(',')).join('\r\n')}`
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `console-audit-logs-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}
const load = async () => {
  const generation = ++loadGeneration
  clearSelection()
  loading.value = true
  error.value = ''
  try {
    const result = await consoleApi.audit(page.value - 1, size, sort.value, direction.value)
    if (generation !== loadGeneration) return
    logs.value = result.items
    total.value = result.total
  } catch (failure) {
    if (generation === loadGeneration)
      error.value = failure instanceof Error ? failure.message : '操作记录加载失败'
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}
onMounted(load)
</script>
<style scoped>
pre {
  margin-top: 20px;
  background: var(--console-inset-bg);
  padding: 18px;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
