<template>
    <div>
        <div class="content-header">
            <div>
                <h1 class="page-title">设备与登录</h1>
                <p class="page-subtitle">管理您的已连接设备和登录活动</p>
            </div>
        </div>

        <el-empty description="设备列表" />
    </div>
</template>

<script setup lang="ts">
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
</style>
