import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

export class IdempotencyStore {
  constructor(ttlMs, clock = () => Date.now(), file = '') {
    this.ttlMs = ttlMs;
    this.clock = clock;
    this.file = file;
    this.entries = new Map();
    this.load();
  }

  get(key) {
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (entry.expiresAt <= this.clock()) {
      this.entries.delete(key);
      this.persist();
      return undefined;
    }
    return entry.value;
  }

  set(key, value) {
    this.entries.set(key, { value, expiresAt: this.clock() + this.ttlMs });
    if (this.entries.size > 10000) this.cleanup();
    this.persist();
  }

  cleanup() {
    const now = this.clock();
    for (const [key, entry] of this.entries) if (entry.expiresAt <= now) this.entries.delete(key);
  }

  load() {
    if (!this.file) return;
    try {
      const stored = JSON.parse(readFileSync(this.file, 'utf8'));
      for (const [key, entry] of stored.entries ?? []) {
        if (entry?.expiresAt > this.clock()) this.entries.set(key, entry);
      }
    } catch (error) {
      if (error?.code !== 'ENOENT') throw new Error('Unable to read idempotency store');
    }
  }

  persist() {
    if (!this.file) return;
    mkdirSync(dirname(this.file), { recursive: true });
    const temporary = `${this.file}.tmp`;
    writeFileSync(temporary, JSON.stringify({ entries: [...this.entries] }), { mode: 0o600 });
    renameSync(temporary, this.file);
  }
}
