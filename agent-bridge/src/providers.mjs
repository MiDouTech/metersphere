import { spawn } from 'node:child_process';
import { isolatedWorkingDirectory, sanitizeError, spawnLines, stopProcess } from './process-runner.mjs';

function runStatus(command, args) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { shell: false, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    child.stdout.on('data', (chunk) => { output += chunk; });
    child.stderr.on('data', (chunk) => { output += chunk; });
    child.once('error', () => resolve({ installed: false, authenticated: false }));
    child.once('close', (code) => resolve({ installed: code !== 127, authenticated: code === 0,
      detail: sanitizeError(output).slice(0, 255) }));
  });
}

export class CodexProvider {
  constructor(command = 'codex') {
    this.command = command; this.processes = new Map();
    this.capabilities = { stream: true, tools: false, files: false, cancel: true,
      vision: false, sessionResume: true, outputFormat: 'jsonl' };
  }
  status() { return runStatus(this.command, ['login', 'status']); }
  login() { return spawn(this.command, ['login'], { shell: false, detached: false, windowsHide: false,
    stdio: 'inherit' }); }
  logout() { return runStatus(this.command, ['logout']); }

  async *execute(request) {
    const cwd = await isolatedWorkingDirectory(request.requestId);
    const args = ['exec', '--json', '--ephemeral', '--sandbox', 'read-only', '--skip-git-repo-check'];
    if (request.externalSessionId) {
      args.push('resume', request.externalSessionId);
    }
    args.push(`${request.systemPrompt}\n\n${request.prompt}`);
    const running = spawnLines(this.command, args, { cwd });
    this.processes.set(request.requestId, running.child);
    try {
      for await (const line of running.lines) {
        let event;
        try { event = JSON.parse(line); } catch { continue; }
        if (event.type === 'thread.started') {
          yield { type: 'execution.accepted', payload: { externalSessionId: event.thread_id } };
        } else if (event.type === 'item.completed' && event.item?.type === 'agent_message') {
          yield { type: 'content.delta', payload: { delta: event.item.text ?? '' } };
        } else if (event.type === 'turn.completed') {
          yield { type: 'usage.reported', payload: {
            inputTokens: event.usage?.input_tokens ?? 0,
            outputTokens: event.usage?.output_tokens ?? 0,
            estimated: false,
          } };
        } else if (event.type === 'turn.failed' || event.type === 'error') {
          throw new Error(event.error?.message ?? event.message ?? 'Codex execution failed');
        }
      }
    } finally { this.processes.delete(request.requestId); }
  }
  cancel(requestId) { stopProcess(this.processes.get(requestId)); }
}

export class CursorProvider {
  constructor({ command = 'cursor-agent', allowUnsandboxed = false } = {}) {
    this.command = command; this.allowUnsandboxed = allowUnsandboxed; this.processes = new Map();
    this.capabilities = { stream: true, tools: false, files: false, cancel: true,
      vision: false, sessionResume: true, outputFormat: 'stream-json' };
  }
  status() { return runStatus(this.command, ['status']); }
  login() { return spawn(this.command, ['login'], { shell: false, detached: false, windowsHide: false,
    stdio: 'inherit' }); }
  logout() { return runStatus(this.command, ['logout']); }
  async *execute(request) {
    if (!this.allowUnsandboxed) throw new Error('CURSOR_OS_SANDBOX_REQUIRED');
    const cwd = await isolatedWorkingDirectory(request.requestId);
    const args = ['--print', '--output-format', 'stream-json'];
    if (request.externalSessionId) args.push('--resume', request.externalSessionId);
    args.push(`${request.systemPrompt}\n\n${request.prompt}`);
    const running = spawnLines(this.command, args, { cwd });
    this.processes.set(request.requestId, running.child);
    let accumulated = '';
    try {
      for await (const line of running.lines) {
        let event;
        try { event = JSON.parse(line); } catch { continue; }
        if (event.type === 'system' && event.subtype === 'init') {
          yield { type: 'execution.accepted', payload: { externalSessionId: event.session_id } };
        } else if (event.type === 'assistant' || event.type === 'result') {
          const text = event.message?.content?.map?.((item) => item.text ?? '').join('')
            ?? event.result ?? event.text ?? '';
          const delta = text.startsWith(accumulated) ? text.slice(accumulated.length)
            : text === accumulated ? '' : text;
          if (text) accumulated = text;
          if (delta) yield { type: 'content.delta', payload: { delta } };
        }
      }
    } finally { this.processes.delete(request.requestId); }
  }
  cancel(requestId) { stopProcess(this.processes.get(requestId)); }
}

export class WorkBuddyProvider {
  constructor() { this.capabilities = {}; }
  async status() {
    return { installed: false, authenticated: false, detail: 'WORKBUDDY_MANAGED_SDK_CREDENTIAL_REQUIRED' };
  }
  async *execute() { throw new Error('WORKBUDDY_MANAGED_SDK_NOT_CONFIGURED'); }
  cancel() {}
}

export function providers(config = {}) {
  return new Map([
    ['CODEX', new CodexProvider(config.codexCommand)],
    ['CURSOR', new CursorProvider({ command: config.cursorCommand,
      allowUnsandboxed: config.allowUnsandboxedCursor === true })],
    ['WORKBUDDY', new WorkBuddyProvider()],
  ]);
}
