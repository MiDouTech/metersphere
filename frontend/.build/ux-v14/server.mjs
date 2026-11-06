import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';
const frontend = process.cwd(),
  fixture = path.join(frontend, '.build/ux-v14');
const server = await createServer({
  configFile: false,
  root: fixture,
  plugins: [
    {
      name: 'ux-fixture',
      enforce: 'pre',
      resolveId(id, importer) {
        if (
          importer?.includes('linkCard.vue') &&
          ['./newReviewDialog.vue', './noticePanel.vue', './takeoverPanel.vue'].includes(id)
        )
          return '\0stub';
        if (id === '@/enums/routeEnum') return '\0enum';
        if (id === 'vue-router') return '\0router';
      },
      load(id) {
        if (id === '\0enum') return 'export const TestPlanRouteEnum={}';
        if (id === '\0stub') return 'export default {render:()=>null}';
        if (id === '\0router') return 'export const useRouter=()=>({push(){}})';
      },
    },
    vue(),
  ],
  resolve: {
    alias: [
      { find: /^vue$/, replacement: path.join(frontend, 'node_modules/vue/dist/vue.esm-bundler.js') },
      { find: '@/api/modules/plan-review', replacement: path.join(fixture, 'mock.js') },
      { find: '@/store/modules/app', replacement: path.join(fixture, 'store.js') },
      { find: '@/utils/permission', replacement: path.join(fixture, 'permission.js') },
      { find: '@', replacement: path.join(frontend, 'src') },
    ],
    dedupe: ['vue'],
  },
  optimizeDeps: { noDiscovery: true, include: ['vue', '@arco-design/web-vue'] },
  server: { host: '127.0.0.1', port: 5197, strictPort: true, fs: { allow: [frontend] } },
});
await server.listen();
console.log('UX fixture ready: http://127.0.0.1:5197');
