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
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon /><el-table
        v-else
        v-loading="loading"
        :data="logs"
        empty-text="暂无操作记录"
        ><el-table-column label="时间" min-width="170"
          ><template #default="{ row }">{{
            new Date(row.createdAt).toLocaleString('zh-CN')
          }}</template></el-table-column
        ><el-table-column label="操作" min-width="190"
          ><template #default="{ row }">{{ actionLabel(row.action) }}</template></el-table-column
        ><el-table-column
          label="操作者"
          min-width="270"
          ><template #default="{ row }"
            ><span class="mono">{{ row.actorUuid }}</span></template
          ></el-table-column
        ><el-table-column prop="target" label="目标" min-width="250" /><el-table-column
          prop="ipAddress"
          label="IP 地址"
          width="140"
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
const loading = ref(true)
const error = ref('')
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
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const result = await consoleApi.audit(page.value - 1, size)
    logs.value = result.items
    total.value = result.total
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '操作记录加载失败'
  } finally {
    loading.value = false
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
