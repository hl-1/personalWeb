import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { elementPlusPlugins } from './element-plus-plugins'

export default defineConfig({
  plugins: [
    vue(),
    ...elementPlusPlugins(true),
  ],
})
