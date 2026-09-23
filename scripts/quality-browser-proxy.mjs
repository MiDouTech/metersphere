import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve('frontend/dist');
const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
http.createServer((req, res) => {
  if (req.url.startsWith('/api/') || req.url.startsWith('/auth/')) {
    const upstream = http.request({ hostname: '127.0.0.1', port: 18081,
      path: req.url.startsWith('/api/') ? req.url.slice(4) : req.url,
      method: req.method, headers: { ...req.headers, host: '127.0.0.1:18081' } }, reply => {
      res.writeHead(reply.statusCode, reply.headers); reply.pipe(res);
    });
    upstream.on('error', () => { res.writeHead(502); res.end('Test backend unavailable'); });
    req.pipe(upstream); return;
  }
  let relative;
  try { relative = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); }
  catch { res.writeHead(400); res.end(); return; }
  let target = path.resolve(root, '.' + relative);
  if (target !== root && !target.startsWith(root + path.sep)) { res.writeHead(403); res.end(); return; }
  if (!fs.existsSync(target) || fs.statSync(target).isDirectory()) target = path.join(root, 'index.html');
  res.writeHead(200, { 'content-type': mime[path.extname(target)] || 'application/octet-stream', 'cache-control': 'no-store' });
  fs.createReadStream(target).pipe(res);
}).listen(15173, '127.0.0.1', () => console.log('Isolated browser acceptance proxy on 127.0.0.1:15173'));
