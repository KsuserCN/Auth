import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/element/index.scss'
import ElementPlus from 'element-plus'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/element/dark-override.css'

import App from './App.vue'
import router from './router'
import request from './utils/request'

// 应用启动前获取 CSRF Token
async function initApp() {
  try {
    // 调用任意接口让服务端下发 XSRF-TOKEN cookie
    await request.get('/auth/health')
  } catch (error) {
    console.warn('Failed to fetch CSRF token:', error)
  }

  const app = createApp(App)

  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus)

  app.mount('#app')
}

initApp()
