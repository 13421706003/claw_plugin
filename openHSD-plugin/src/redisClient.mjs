import Redis from 'ioredis';

/**
 * 创建 Redis 客户端
 * @param {Object} config - Redis 配置
 * @param {string} config.host - Redis 主机地址
 * @param {number} config.port - Redis 端口
 * @param {string} [config.password] - Redis 密码
 * @param {number} [config.db] - Redis 数据库编号
 * @returns {Promise<Redis|null>} Redis 客户端实例或 null
 */
export async function createRedisClient(config) {
  if (!config || !config.host) {
    console.log('[Redis] 配置缺失，跳过 Redis 连接');
    return null;
  }

  try {
    const redisOptions = {
      host: config.host,
      port: config.port || 6379,
      db: config.db || 0,
      connectTimeout: 5000,
      maxRetriesPerRequest: 2,
      retryStrategy: (times) => {
        if (times > 2) {
          console.log('[Redis] 连接重试次数超限，放弃连接');
          return null;
        }
        return Math.min(times * 100, 2000);
      }
    };

    // 添加用户名和密码（如果存在）
    if (config.username) {
      redisOptions.username = config.username;
    }
    if (config.password) {
      redisOptions.password = config.password;
    }

    const redis = new Redis(redisOptions);

    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('连接超时'));
      }, 5000);

      redis.once('ready', () => {
        clearTimeout(timeout);
        resolve();
      });

      redis.once('error', (err) => {
        clearTimeout(timeout);
        reject(err);
      });
    });

    console.log('[Redis] 连接成功');
    return redis;
  } catch (error) {
    console.log('[Redis] 连接失败：', error.message);
    return null;
  }
}

/**
 * 从 Redis 中随机获取一个 hsd:* key 的 token
 * @param {Redis} redis - Redis 客户端实例
 * @returns {Promise<string|null>} token 或 null
 */
export async function getRandomTokenFromRedis(redis) {
  if (!redis) {
    return null;
  }

  try {
    // 使用 SCAN 命令遍历所有 hsd:* key
    const keys = [];
    let cursor = '0';

    do {
      const result = await redis.scan(cursor, 'MATCH', 'hsd:*', 'COUNT', 100);
      cursor = result[0];
      keys.push(...result[1]);
    } while (cursor !== '0');

    if (keys.length === 0) {
      console.log('[Redis] 未找到任何 hsd:* key');
      return null;
    }

    console.log(`[Redis] 找到 ${keys.length} 个 hsd:* key`);

    // 随机选择一个 key
    const randomIndex = Math.floor(Math.random() * keys.length);
    const selectedKey = keys[randomIndex];

    // 获取 token 值
    const token = await redis.get(selectedKey);

    if (!token) {
      console.log(`[Redis] key ${selectedKey} 的值为空`);
      return null;
    }

    // 删除该 key，避免重复使用
    await redis.del(selectedKey);
    console.log(`[Redis] 已从 ${selectedKey} 获取 token 并删除该 key`);
    return token;
  } catch (error) {
    console.log('[Redis] 获取 token 失败：', error.message);
    return null;
  }
}

/**
 * 关闭 Redis 客户端连接
 * @param {Redis} redis - Redis 客户端实例
 */
export async function closeRedisClient(redis) {
  if (redis) {
    try {
      await redis.quit();
      console.log('[Redis] 连接已关闭');
    } catch (error) {
      console.log('[Redis] 关闭连接失败：', error.message);
    }
  }
}
