import { createPinia } from 'pinia'
import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import App from './App.vue'
import { router } from './router'
import './styles/tokens.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)
app.mount('#app')
