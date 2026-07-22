import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { elementPlusPlugins } from './element-plus-plugins'

export default defineConfig({
  plugins: [vue(), ...elementPlusPlugins(false)],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
  },
})
