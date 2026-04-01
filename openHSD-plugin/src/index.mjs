import { OpenHSDService } from './service.mjs';
import { setupToken, loadConfig } from './setup.mjs';

async function main() {
  const config = await setupToken();
  const service = new OpenHSDService(config);

  process.on('SIGINT', () => shutdown('SIGINT', service));
  process.on('SIGTERM', () => shutdown('SIGTERM', service));

  await service.start();
}

function shutdown(signal, service) {
  console.log(`\n[openHSD] 收到 ${signal}，正在关闭...`);
  service.stop();
  process.exit(0);
}

main().catch(e => {
  console.error('[openHSD] 启动失败：', e);
  process.exit(1);
});
