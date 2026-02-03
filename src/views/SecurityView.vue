<template>
    <div>
        <div class="content-header">
            <div>
                <h1 class="page-title">安全性</h1>
                <p class="page-subtitle">管理您的账户安全设置和登录认证方式</p>
            </div>
        </div>

        <el-row :gutter="16">
            <el-col :xs="24" :lg="16">
                <el-card class="card" shadow="never">
                    <div class="card-title">
                        <el-icon>
                            <Lock />
                        </el-icon>
                        <span>两步验证</span>
                    </div>
                    <div class="security-item">
                        <div class="item-header">
                            <span class="item-title">邮箱验证码</span>
                            <el-tag type="success" effect="light">已启用</el-tag>
                        </div>
                        <p class="item-desc">使用发送到您邮箱的验证码进行登录</p>
                        <div class="card-actions">
                            <el-button plain>管理</el-button>
                        </div>
                    </div>
                    <el-divider />
                    <div class="security-item">
                        <div class="item-header">
                            <span class="item-title">Passkey（生物识别）</span>
                            <el-tag type="warning" effect="light">未启用</el-tag>
                        </div>
                        <p class="item-desc">使用生物识别或设备密码进行快速安全登录</p>
                        <div class="card-actions">
                            <el-button plain type="primary">启用</el-button>
                        </div>
                    </div>
                </el-card>
            </el-col>

            <el-col :xs="24" :lg="8">
                <el-card class="card" shadow="never">
                    <div class="card-title">
                        <el-icon>
                            <Key />
                        </el-icon>
                        <span>密码管理</span>
                    </div>
                    <div class="hint">定期更新密码可以提高账户安全性</div>
                    <el-divider />
                    <div class="hint">上次修改：45 天前</div>
                    <div class="card-actions">
                        <el-button plain type="primary">修改密码</el-button>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <el-row :gutter="16" class="row-gap">
            <el-col :xs="24">
                <el-card class="card" shadow="never">
                    <div class="card-title">
                        <el-icon>
                            <Monitor />
                        </el-icon>
                        <span>活跃会话</span>
                    </div>
                    <el-table :data="sessions" class="device-table" size="small">
                        <el-table-column prop="device" label="设备" width="200" />
                        <el-table-column prop="location" label="位置" />
                        <el-table-column prop="lastActive" label="最后活动" />
                        <el-table-column label="操作" width="100">
                            <template #default>
                                <el-button text type="danger" size="small">退出</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
    Key,
    Lock,
    Monitor,
} from '@element-plus/icons-vue'

const sessions = ref([
    { device: 'MacBook Pro (Chrome)', location: '上海', lastActive: '现在' },
    { device: 'iPhone 15 (Safari)', location: '北京', lastActive: '10 分钟前' },
    { device: 'Windows (Edge)', location: '深圳', lastActive: '2 小时前' },
])
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

.card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    margin-bottom: 12px;
}

.security-item {
    padding: 12px 0;
}

.item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
}

.item-title {
    font-size: 15px;
    font-weight: 500;
    color: var(--el-text-color-primary);
}

.item-desc {
    margin: 0 0 8px 0;
    font-size: 13px;
    color: var(--el-text-color-secondary);
}

.hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
}

.card-actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
}

:deep(.el-button) {
    transition: all 0.2s ease;
}

:deep(.el-button):not(.is-disabled):hover {
    box-shadow: 0 6px 14px rgba(0, 0, 0, 0.08);
}

.row-gap {
    margin-top: 16px;
}

.device-table :deep(.el-table__inner-wrapper) {
    border-radius: 12px;
}
</style>
