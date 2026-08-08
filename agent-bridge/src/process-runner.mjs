import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const SAFE_ID = /^[a-zA-Z0-9_-]{1,80}$/;

export async function isolatedWorkingDirectory(requestId) {
  if (!SAFE_ID.test(requestId)) throw new Error('invalid request id');
  const directory = path.join(os.tmpdir(), 'metersphere-agent-bridge', requestId);
  await mkdir(directory, { recursive: true, mode: 0o700 });
  return directory;
}

export function spawnLines(command, args, options = {}) {
  if (!command || !Array.isArray(args) || args.some((arg) => typeof arg !== 'string' || arg.includes('\0'))) {
    throw new Error('invalid process arguments');
  }
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: options.env ?? process.env,
    shell: false,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stderr = '';
  child.stderr.setEncoding('utf8');
  child.stderr.on('data', (chunk) => {
    stderr = `${stderr}${chunk}`.slice(-16_000);
  });
  async function* lines() {
    child.stdout.setEncoding('utf8');
    let pending = '';
    for await (const chunk of child.stdout) {
      pending += chunk;
      let end;
      while ((end = pending.indexOf('\n')) >= 0) {
        const line = pending.slice(0, end).trim();
        pending = pending.slice(end + 1);
        if (line) yield line;
      }
    }
    if (pending.trim()) yield pending.trim();
    const code = await new Promise((resolve, reject) => {
      child.once('error', reject);
      child.once('close', resolve);
    });
    if (code !== 0) throw new Error(sanitizeError(stderr || `${command} exited with ${code}`));
  }
  return { child, lines: lines() };
}

export function stopProcess(child) {
  if (!child || child.killed) return;
  child.kill('SIGTERM');
  const timer = setTimeout(() => child.kill('SIGKILL'), 5_000);
  timer.unref();
}

export function sanitizeError(value) {
  return String(value ?? 'Agent process failed')
    .replace(/(api[_-]?key|authorization|token|secret)\s*[=:]\s*[^\s,;]+/gi, '$1=******')
    .slice(0, 1_000);
}
