<script setup>
import { Image } from 'ant-design-vue'
</script>

<template>
  <div class="tutorial-view">
    <h1 class="page-title">使用教程</h1>

    <section class="step">
      <h2 class="step-title">1. 下载插件</h2>
      <div class="step-content">
        <p>
          下载地址：
          <a href="http://huashidai1.com:9001/buckets/hsdclaw/hsdclaw_plugin/openHSD-plugin.zip" class="download-link" download>
            点击下载 openHSD-plugin.zip
          </a>
        </p>
        <p>下载完成后，将压缩包解压到任意目录。</p>
      </div>
    </section>

    <section class="step">
      <h2 class="step-title">2. 访问网站，进行登录注册</h2>
      <div class="step-content">
        <p>打开浏览器，访问网站地址，进入登录页面。</p>
        <p>如果没有账号，点击"注册"按钮进行账号注册；已有账号可直接输入用户名和密码登录。</p>
      </div>
      <div class="step-image">
        <Image src="/tutorial/connect.png" alt="登录注册页面" />
      </div>
    </section>

    <section class="step">
      <h2 class="step-title">3. 启动插件与配置</h2>
      <div class="step-content">
        <p>打开插件文件夹，双击 <code>start.bat</code> 运行插件。</p>
        <p>首次启动会提示："是否重新输入token？"</p>
        <p>输入 <code>yes</code> 或 <code>y</code>：重新配置 Token</p>
        <p>输入 <code>no</code> 或 <code>n</code>：使用上次保存的 Token</p>
      </div>
      <div class="step-image">
        <Image src="/tutorial/plugin-start.png" alt="插件启动界面" />
      </div>
    </section>

    <section class="step">
      <h2 class="step-title">4. 获取Token</h2>
      <div class="step-content">
        <p>登录成功后，进入主界面。</p>
        <p>点击右侧导航栏底部的用户头像，弹出用户面板。</p>
        <p>面板中显示"连接 Token"，点击"复制 Token"按钮即可将 Token 复制到剪贴板。</p>
      </div>
      <div class="step-image">
        <Image src="/tutorial/connect.png" alt="获取Token" />
      </div>
    </section>

    <section class="step">
      <h2 class="step-title">5. 输入Token连接</h2>
      <div class="step-content">
        <p>将复制的 Token 粘贴到插件终端中，按 <code>Enter</code> 键确认。</p>
        <p>如果 Token 正确且网络正常，终端会显示"连接成功"提示。</p>
        <p>此时插件已与服务器建立 WebSocket 连接，可以正常使用聊天功能。</p>
      </div>
      <div class="step-image">
        <Image src="/tutorial/connect-token.png" alt="连接成功" />
      </div>
    </section>

    <section class="step">
      <h2 class="step-title">6. 连接失败情况</h2>
      <div class="step-content">
        <p>如果出现以下情况，连接可能失败：</p>
        <ul>
          <li>Token 已过期或无效</li>
          <li>网络连接异常</li>
          <li>服务器未响应</li>
        </ul>
        <p>请检查 Token 是否正确，或重新登录网站获取新的 Token。</p>
      </div>
      <div class="step-images">
        <Image src="/tutorial/connect-false1.png" alt="连接失败情况1" />
        <Image src="/tutorial/connect-false2.png" alt="连接失败情况2" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.tutorial-view {
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 32px 40px;
  background: #fafafa;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.step {
  background: #fff;
  border-radius: 12px;
  padding: 24px 28px;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.step-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 16px;
}

.step-content {
  margin-bottom: 20px;
}

.step-content p {
  font-size: 14px;
  color: #4a4a4a;
  line-height: 1.8;
  margin: 0 0 12px;
}

.step-content p:last-child {
  margin-bottom: 0;
}

.step-content code {
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  color: #d46b08;
}

.step-content ul {
  margin: 12px 0;
  padding-left: 20px;
}

.step-content li {
  font-size: 14px;
  color: #4a4a4a;
  line-height: 1.8;
}

.download-link {
  color: #1677ff;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.download-link:hover {
  color: #4096ff;
}

.config-title {
  margin-top: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.config-code {
  background: #f6f6f6;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 12px 16px;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 8px 0;
  color: #333;
}

.step-image {
  max-width: 480px;
}

.step-image :deep(.ant-image) {
  width: 100%;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  background: #fafafa;
  overflow: hidden;
}

.step-image :deep(.ant-image img) {
  width: 100%;
  max-width: 100%;
  height: auto;
  min-height: 120px;
  cursor: pointer;
}

.step-images {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.step-images :deep(.ant-image) {
  width: 240px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  background: #fafafa;
  overflow: hidden;
}

.step-images :deep(.ant-image img) {
  width: 240px;
  height: auto;
  min-height: 120px;
  cursor: pointer;
}
</style>
