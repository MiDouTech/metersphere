/** Invalidates work across reload, context changes and unmount without owning business state. */
export default function createLatestRequest() {
  let sequence = 0;
  return {
    begin: () => ++sequence,
    current: () => sequence,
    isCurrent: (ticket: number) => ticket === sequence,
    invalidate: () => {
      sequence += 1;
    },
  };
}
