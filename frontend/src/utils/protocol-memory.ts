// Deliberately never serialized into localStorage, sessionStorage, IndexedDB or service-worker caches.
import { onBeforeUnmount, onMounted } from 'vue';

export const protocolRequests = new Map<string, { fingerprint: string; request: Record<string, unknown> }>();
let generation = 0;
export const protocolMemoryGeneration = () => generation;
export function clearProtocolMemory() {
  generation += 1;
  protocolRequests.clear();
  window.dispatchEvent(new Event('protocol-memory-cleared'));
}
export function useProtocolMemoryClear(clear: () => void) {
  onMounted(() => window.addEventListener('protocol-memory-cleared', clear));
  onBeforeUnmount(() => window.removeEventListener('protocol-memory-cleared', clear));
}
