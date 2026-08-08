import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const root = path.join(os.homedir(), '.metersphere-agent-bridge', 'secrets');

function powershell(script, input = '') {
  return new Promise((resolve, reject) => {
    const child = spawn('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', script], {
      shell: false, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = ''; let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.once('error', reject);
    child.once('close', (code) => code === 0 ? resolve(stdout.trim()) : reject(new Error(stderr.trim())));
    child.stdin.end(input);
  });
}

export async function setSecret(name, value) {
  if (!/^[a-zA-Z0-9_-]{1,100}$/.test(name)) throw new Error('invalid secret name');
  await mkdir(root, { recursive: true, mode: 0o700 });
  const target = path.join(root, `${name}.bin`);
  if (process.platform === 'win32') {
    const encoded = await powershell(`
      Add-Type -AssemblyName System.Security;
      $plain = [Console]::In.ReadToEnd();
      $bytes = [Text.Encoding]::UTF8.GetBytes($plain);
      $cipher = [Security.Cryptography.ProtectedData]::Protect($bytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
      [Convert]::ToBase64String($cipher)
    `, value);
    await writeFile(target, encoded, { mode: 0o600 });
    return;
  }
  if (process.platform === 'linux') {
    await new Promise((resolve, reject) => {
      const child = spawn('secret-tool', ['store', '--label=MeterSphere Agent Bridge',
        'service', 'metersphere-agent-bridge', 'account', name], { shell: false, stdio: ['pipe', 'ignore', 'pipe'] });
      child.once('error', reject); child.once('close', (code) => code === 0 ? resolve() : reject(new Error('secret-tool failed')));
      child.stdin.end(value);
    });
    return;
  }
  throw new Error('OS credential store is not supported on this platform');
}

export async function getSecret(name) {
  if (!/^[a-zA-Z0-9_-]{1,100}$/.test(name)) throw new Error('invalid secret name');
  if (process.platform === 'win32') {
    const encoded = await readFile(path.join(root, `${name}.bin`), 'utf8');
    return powershell(`
      Add-Type -AssemblyName System.Security;
      $cipher = [Convert]::FromBase64String([Console]::In.ReadToEnd());
      $plain = [Security.Cryptography.ProtectedData]::Unprotect($cipher, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
      [Text.Encoding]::UTF8.GetString($plain)
    `, encoded);
  }
  if (process.platform === 'linux') {
    return new Promise((resolve, reject) => {
      const child = spawn('secret-tool', ['lookup', 'service', 'metersphere-agent-bridge', 'account', name],
        { shell: false, stdio: ['ignore', 'pipe', 'pipe'] });
      let output = ''; child.stdout.on('data', (chunk) => { output += chunk; });
      child.once('error', reject); child.once('close', (code) => code === 0 ? resolve(output.trim()) : reject(new Error('secret-tool lookup failed')));
    });
  }
  throw new Error('OS credential store is not supported on this platform');
}
