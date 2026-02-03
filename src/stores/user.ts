import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo, type User } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const loading = ref(false)
  const error = ref('')

  // 获取用户信息
  async function fetchUserInfo() {
    // 如果已经有用户信息且不在加载中，直接返回
    if (user.value && !loading.value) {
      return user.value
    }

    // 如果正在加载，等待加载完成
    if (loading.value) {
      return new Promise((resolve, reject) => {
        const checkLoading = setInterval(() => {
          if (!loading.value) {
            clearInterval(checkLoading)
            if (error.value) {
              reject(error.value)
            } else {
              resolve(user.value)
            }
          }
        }, 100)
      })
    }

    // 开始加载
    loading.value = true
    error.value = ''
    try {
      const res = await getUserInfo()
      user.value = res as User
      return user.value
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err)
      error.value = message || '获取用户信息失败'
      throw error.value
    } finally {
      loading.value = false
    }
  }

  // 清除用户信息
  function clearUser() {
    user.value = null
    error.value = ''
  }

  return {
    user,
    loading,
    error,
    fetchUserInfo,
    clearUser,
  }
})
