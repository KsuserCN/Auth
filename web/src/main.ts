import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/element/index.scss'
import ElementPlus from 'element-plus'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/element/dark-override.css'
import './styles/mobile-adapter.css'
import '@fortawesome/fontawesome-free/css/all.min.css'

import { i18n } from './i18n'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus)

app.mount('#app')
