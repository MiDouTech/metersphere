FROM docker.io/gzmidoo/node:22-bookworm-slim AS build
WORKDIR /runner
COPY package*.json tsconfig.json ./
RUN npm ci
COPY src ./src
RUN npm run build

FROM mcr.microsoft.com/playwright:v1.62.1-noble
WORKDIR /runner
ENV NODE_ENV=production
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=build /runner/dist ./dist
USER pwuser
CMD ["node", "dist/src/index.js"]
