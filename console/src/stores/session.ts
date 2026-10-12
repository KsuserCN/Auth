import { defineStore } from 'pinia'
import { ref } from 'vue'
import { consoleApi, type ConsoleSession } from '@/api/console'

export const useSessionStore = defineStore('console-session', () => {
  const session = ref<ConsoleSession | null>(null)
  const load = async () => {
    session.value = await consoleApi.session()
    return session.value
  }
  const clear = () => {
    session.value = null
  }
  return { session, load, clear }
})
