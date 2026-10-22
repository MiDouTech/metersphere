/** Coalesce browser activation events without overlapping a list request. */
export default function createListRefreshGate(
  refresh: () => Promise<unknown>,
  ready: () => boolean,
  now: () => number = Date.now
) {
  let disposed = false;
  let busy = false;
  let lastStarted = Number.NEGATIVE_INFINITY;
  return {
    async run() {
      if (disposed || busy || !ready() || now() - lastStarted < 1000) return;
      busy = true;
      lastStarted = now();
      try {
        await refresh();
      } finally {
        busy = false;
      }
    },
    dispose() {
      disposed = true;
    },
  };
}
