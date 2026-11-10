/** Coalesce browser activation events without overlapping a list request. */
export default function createListRefreshGate(
  refresh: () => Promise<unknown>,
  ready: () => boolean,
  now: () => number = Date.now
) {
  let disposed = false;
  let running: Promise<void> | undefined;
  let pendingActivation: Promise<void> | undefined;
  let lastStarted = Number.NEGATIVE_INFINITY;
  return {
    async run(activation = false): Promise<void> {
      if (disposed) return;
      // Returning to a cached page must recover a request cancelled on departure.
      // Coalesce repeated activations, but replay once after the old request settles.
      if (running) {
        if (!activation) return;
        if (!pendingActivation) {
          pendingActivation = running
            .catch(() => undefined)
            .then(() => {
              pendingActivation = undefined;
              return this.run(true);
            });
        }
        return pendingActivation;
      }
      if (!ready() || (!activation && now() - lastStarted < 1000)) return;
      lastStarted = now();
      running = (async () => {
        await refresh();
      })();
      try {
        await running;
      } finally {
        running = undefined;
      }
    },
    dispose() {
      disposed = true;
    },
  };
}
