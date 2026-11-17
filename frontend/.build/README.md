# MSP-12 local development helpers

These helpers are development evidence, not production code or deployment acceptance tests.

- Run `node .build/ux-v14/server.mjs` from `frontend` after installing the frontend dependencies. The fixture uses real Vue components with mocked API responses and listens on localhost port 5197.
- `local-forbidden.mjs` is the historical one-time source transformation used during development. It is not idempotent. Do not run it on the current source: the scoped forbidden handling is already implemented.

The fixture originally used an absolute path on the author's computer. Its component imports now use relative paths so another checkout can load it.
