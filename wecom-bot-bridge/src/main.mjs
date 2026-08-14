import { loadConfig } from './config.mjs';
import { createHttpServer } from './health-server.mjs';
import { IdempotencyStore } from './idempotency-store.mjs';
import { logger } from './logger.mjs';
import { PlatformClient } from './platform-client.mjs';
import { WecomBotClient } from './wecom-client.mjs';

const config = loadConfig();
const platformClient = new PlatformClient(config);
const botClient = new WecomBotClient(config, platformClient);
const store = new IdempotencyStore(config.idempotencyTtlMs, () => Date.now(), config.idempotencyFile);
const server = createHttpServer(config, botClient, store);

server.listen(config.port, '0.0.0.0', async () => {
  logger.info('wecom bot bridge listening', { port: config.port });
  if (config.enabled) {
    try {
      await botClient.start();
    } catch (error) {
      logger.warn('initial bot connection failed; bridge remains available for reconfiguration', { error: error.message });
    }
  }
});

const shutdown = () => {
  logger.info('wecom bot bridge shutting down');
  botClient.stop();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10000).unref();
};

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
