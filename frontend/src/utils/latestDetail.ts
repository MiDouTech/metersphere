export function isRequestCanceled(error: unknown): boolean {
  const value = error as { code?: string; __CANCEL__?: boolean; name?: string } | undefined;
  return value?.code === 'ERR_CANCELED' || value?.__CANCEL__ === true || value?.name === 'AbortError';
}

/** One owner per detail view; stale requests never publish state. */
export function createLatestDetailLoader<T>(options: {
  fetch: (id: string, signal: AbortSignal) => Promise<T>;
  start: () => void;
  success: (value: T) => void;
  failure: (error: unknown) => void;
  finish: () => void;
}) {
  let sequence = 0;
  let active: { id: string; controller: AbortController; promise: Promise<void> } | undefined;
  function invalidate() {
    sequence += 1;
    active?.controller.abort();
    active = undefined;
    options.finish();
  }
  function load(id: string): Promise<void> {
    if (!id) return Promise.resolve();
    if (active?.id === id) return active.promise;
    active?.controller.abort();
    const current = ++sequence;
    const controller = new AbortController();
    options.start();
    const promise = Promise.resolve()
      .then(() => options.fetch(id, controller.signal))
      .then((value) => {
        if (current === sequence) options.success(value);
      })
      .catch((error: unknown) => {
        if (current === sequence && !isRequestCanceled(error)) options.failure(error);
      })
      .finally(() => {
        if (current === sequence) {
          active = undefined;
          options.finish();
        }
      });
    active = { id, controller, promise };
    return promise;
  }
  return { load, invalidate };
}
