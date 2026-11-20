import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import router from './app/router'
import permissionDirective from './app/directives/permission'
import App from './app/App.vue'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.directive('permission', permissionDirective)
app.mount('#app')
