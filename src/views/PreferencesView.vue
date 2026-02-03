<template>
    <div>
        <div class="content-header">
            <div>
                <h1 class="page-title">偏好设置</h1>
                <p class="page-subtitle">自定义您的使用偏好和通知设置</p>
            </div>
        </div>

        <el-empty description="偏好设置" />
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
