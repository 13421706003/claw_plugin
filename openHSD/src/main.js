import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// 导入 Ant Design Vue
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

// 导入 Ant Design X Vue
import AntDesignXVue from 'ant-design-x-vue'

const app = createApp(App)

app.use(router)
app.use(Antd)
app.use(AntDesignXVue)

app.mount('#app')
