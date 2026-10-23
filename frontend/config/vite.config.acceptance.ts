import { mergeConfig } from 'vite';
import baseConfig from './vite.config.base';

// Local acceptance only: source frontend against the isolated export/remediation backend.
export default mergeConfig(baseConfig, {
  mode: 'development',
  server: {
    host: '127.0.0.1', port: 15177, strictPort: true, open: false,
    proxy: {
      '/front': { target: 'http://127.0.0.1:18085', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/front/, '') },
      '/api': { target: 'http://127.0.0.1:18085', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/api/, '') },
      '/auth': { target: 'http://127.0.0.1:18085', changeOrigin: true },
      '/base-display': { target: 'http://127.0.0.1:18085', changeOrigin: true },
    },
  },
});
