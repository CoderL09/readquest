import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { createAppRouter } from './router'
import './styles/main.css'
import './styles/workspace.css'

const pinia = createPinia()
const app = createApp(App)
app.use(pinia)
app.use(createAppRouter(pinia))
app.mount('#app')
