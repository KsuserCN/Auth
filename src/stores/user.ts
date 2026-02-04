import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo, getUserDetailsInfo, type User, type UserDetails } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const userDetails = ref<UserDetails | null>(null)
  const loading = ref(false)
  const detailsLoading = ref(false)
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
    userDetails.value = null
    error.value = ''
  }

  // 清除详细信息缓存（当需要重新加载时使用）
  function clearUserDetailsCache() {
    userDetails.value = null
  }

  // 获取用户详细信息（仅在需要时调用，如 ProfileView）
  async function fetchUserDetails() {
    // 如果已经有详细信息，直接返回
    if (userDetails.value && !detailsLoading.value) {
      return userDetails.value
    }

    // 如果正在加载，等待加载完成
    if (detailsLoading.value) {
      return new Promise((resolve, reject) => {
        const checkLoading = setInterval(() => {
          if (!detailsLoading.value) {
            clearInterval(checkLoading)
            if (error.value) {
              reject(error.value)
            } else {
              resolve(userDetails.value)
            }
          }
        }, 100)
      })
    }

    // 开始加载详细信息
    detailsLoading.value = true
    error.value = ''
    try {
      const res = await getUserDetailsInfo()
      // res 现在已经是提取后的真实数据
      userDetails.value = res
      // 同时更新基本用户信息
      if (user.value) {
        user.value = {
          ...user.value,
          ...res,
        }
      }
      return res
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err)
      error.value = message || '获取用户详细信息失败'
      throw error.value
    } finally {
      detailsLoading.value = false
    }
  }

  return {
    user,
    userDetails,
    loading,
    detailsLoading,
    error,
    fetchUserInfo,
    fetchUserDetails,
    clearUser,
    clearUserDetailsCache,
  }
})
