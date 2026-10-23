import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve('frontend/dist');
const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api/') || req.url.startsWith('/auth/')) {
    const upstream = http.request({ hostname: '127.0.0.1', port: 18085,
      path: req.url.startsWith('/api/') ? req.url.slice(4) : req.url,
      method: req.method, headers: { ...req.headers, host: '127.0.0.1:18085' } }, reply => {
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
});
server.on('upgrade', (req, socket, head) => {
  if (new URL(req.url, 'http://localhost').pathname !== '/api/functional/case/export/ws') {
    socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n'); return;
  }
  const upstream = http.request({ hostname: '127.0.0.1', port: 18085, path: req.url.slice(4),
    method: 'GET', headers: { ...req.headers, host: '127.0.0.1:18085' } });
  upstream.on('upgrade', (reply, peer, peerHead) => {
    socket.write(`HTTP/1.1 101 Switching Protocols\r\n${Object.entries(reply.headers).map(([key, value]) => `${key}: ${value}`).join('\r\n')}\r\n\r\n`);
    if (peerHead.length) socket.write(peerHead);
    if (head.length) peer.write(head);
    socket.pipe(peer); peer.pipe(socket);
    socket.on('error', () => peer.destroy()); peer.on('error', () => socket.destroy());
    socket.on('close', () => peer.destroy()); peer.on('close', () => socket.destroy());
  });
  upstream.on('response', (reply) => {
    socket.end(`HTTP/1.1 ${reply.statusCode} Rejected\r\nConnection: close\r\n\r\n`); reply.resume();
  });
  upstream.on('error', () => socket.destroy());
  socket.on('error', () => upstream.destroy());
  upstream.end();
});
server.listen(15175, '127.0.0.1', () => console.log('Isolated browser acceptance proxy on 127.0.0.1:15175'));
