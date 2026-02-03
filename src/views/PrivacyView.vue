<template>
    <div>
        <div class="content-header">
            <div>
                <h1 class="page-title">隐私与数据</h1>
                <p class="page-subtitle">管理您的数据和隐私设置</p>
            </div>
        </div>

        <el-empty description="隐私设置" />
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
