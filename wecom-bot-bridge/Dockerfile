FROM docker.io/gzmidoo/node:22-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

FROM docker.io/gzmidoo/node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY --from=dependencies /app/node_modules ./node_modules
COPY package.json ./
COPY src ./src
RUN addgroup -S -g 10001 bridge && adduser -S -u 10001 -G bridge bridge
RUN mkdir -p /var/lib/wecom-bot && chown -R bridge:bridge /var/lib/wecom-bot
USER bridge
EXPOSE 8095
CMD ["node", "src/main.mjs"]
