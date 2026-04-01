import { readFileSync, writeFileSync, existsSync } from 'fs';
import { createInterface } from 'readline';
import { fileURLToPath } from 'url';
import path from 'path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const configDir = path.join(__dirname, '..');

/**
 * 根据环境加载配置文件
 * NODE_ENV=development → cj.config.development.json
 * NODE_ENV=production  → cj.config.production.json
 * 默认：production
 */
export function loadConfig() {
  const env = process.env.NODE_ENV || 'production';
  const envConfigPath = path.join(configDir, `cj.config.${env}.json`);
  const tokenConfigPath = path.join(configDir, 'cj.config.json');

  // 加载环境配置
  if (!existsSync(envConfigPath)) {
    console.error(`[openHSD] 配置文件不存在：${envConfigPath}`);
    process.exit(1);
  }
  const envConfig = JSON.parse(readFileSync(envConfigPath, 'utf-8'));

  // 加载 token 配置（如果存在）
  let tokenConfig = { cloud: { token: '' } };
  if (existsSync(tokenConfigPath)) {
    tokenConfig = JSON.parse(readFileSync(tokenConfigPath, 'utf-8'));
  }

  // 合并配置：环境配置 + token
  const mergedConfig = {
    ...envConfig,
    cloud: {
      ...envConfig.cloud,
      token: tokenConfig.cloud?.token || ''
    }
  };

  console.log(`[openHSD] 已加载 ${env} 环境配置`);
  return mergedConfig;
}

/**
 * 启动前询问用户是否更新 token
 * - yes → 输入新 token 并写入 cj.config.json
 * - no  → 使用现有值
 */
export async function setupToken() {
  const config = loadConfig();
  const currentToken = config.cloud?.token || '';

  const rl = createInterface({
    input:  process.stdin,
    output: process.stdout,
  });

  const ask = (question) => new Promise((resolve) => rl.question(question, resolve));

  console.log('');
  console.log('╔══════════════════════════════════════════════════════╗');
  console.log('║              openHSD Plugin 启动向导                  ║');
  console.log('╚══════════════════════════════════════════════════════╝');
  console.log('');

  if (currentToken) {
    console.log(`  当前 token：${currentToken.substring(0, 20)}...（已隐藏）`);
  } else {
    console.log('  当前 token：未配置');
  }
  console.log('');

  const answer = (await ask('  是否需要更新连接 Token？(yes/no): ')).trim().toLowerCase();

  if (answer === 'yes' || answer === 'y') {
    console.log('');
    const newToken = (await ask('  请输入新的 JWT Token: ')).trim();

    if (!newToken) {
      console.log('  Token 为空，取消更新，使用原有值。');
    } else {
      config.cloud.token = newToken;
      const tokenConfigPath = path.join(configDir, 'cj.config.json');
      const tokenConfig = { cloud: { token: newToken } };
      writeFileSync(tokenConfigPath, JSON.stringify(tokenConfig, null, 2) + '\n', 'utf-8');
      console.log('  Token 已保存到 cj.config.json');
    }
  } else {
    if (!currentToken) {
      console.log('');
      console.error('  [错误] 当前 token 为空，插件无法连接云端。');
      console.error('  请重新启动并输入有效的 JWT Token。');
      rl.close();
      process.exit(1);
    }
    console.log('  使用原有 Token，继续启动...');
  }

  console.log('');
  rl.close();

  return config;
}
