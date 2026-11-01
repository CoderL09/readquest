import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    include: ['tests/vue/**/*.spec.ts'],
    clearMocks: true,
    restoreMocks: true,
  },
})
