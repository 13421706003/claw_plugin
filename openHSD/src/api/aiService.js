import { ref } from 'vue';

// AI 服务配置
const AI_CONFIG = {
  baseURL: 'https://api.x.ant.design/api/llm_siliconflow_deepSeek-r1-distill-1wen-7b',
  model: 'DeepSeek-R1-Distill-Qwen-7B',
  // 注意：在实际项目中，API密钥应该从环境变量中获取
  apiKey: 'Bearer sk-xxxxxxxxxxxxxxxxxxxx'
};

// 创建 AbortController 的引用，用于中断请求
const abortController = ref(null);

// 请求状态
const loading = ref(false);

// 消息历史记录
const messageHistory = ref({});

// 会话列表
const conversations = ref([
  {
    key: 'default-0',
    label: '新对话',
    group: '今天'
  }
]);

// 当前会话
const currentConversation = ref('default-0');

// 消息列表
const messages = ref([]);

// 发送消息到 AI
const sendMessage = async (content) => {
  if (!content || loading.value) return;

  loading.value = true;

  // 添加用户消息到列表
  const userMessage = { role: 'user', content };
  messages.value = [...messages.value, userMessage];

  try {
    // 创建新的 AbortController
    const controller = new AbortController();
    abortController.value = controller;

    // 模拟 AI 响应（实际项目中替换为真实 API 调用）
    setTimeout(() => {
      const assistantMessage = { 
        role: 'assistant', 
        content: `这是对 "${content}" 的模拟回复。在实际项目中，这里将连接到真实的 AI API。` 
      };
      
      messages.value = [...messages.value, assistantMessage];
      loading.value = false;
      
      // 保存到历史记录
      messageHistory.value[currentConversation.value] = messages.value;
    }, 1000);

  } catch (error) {
    console.error('发送消息失败:', error);
    loading.value = false;
    
    const errorMessage = {
      role: 'assistant',
      content: '请求失败，请稍后重试。'
    };
    messages.value = [...messages.value, errorMessage];
  }
};

// 中断请求
const abortRequest = () => {
  if (abortController.value) {
    abortController.value.abort();
    loading.value = false;
  }
};

// 创建新会话
const createNewConversation = () => {
  const now = Date.now().toString();
  const newConversation = {
    key: now,
    label: `新对话 ${conversations.value.length + 1}`,
    group: '今天'
  };
  
  conversations.value = [newConversation, ...conversations.value];
  currentConversation.value = now;
  messages.value = [];
};

// 切换会话
const switchConversation = (key) => {
  // 保存当前会话的消息
  if (messages.value.length > 0) {
    messageHistory.value[currentConversation.value] = [...messages.value];
  }
  
  // 切换到新会话
  currentConversation.value = key;
  messages.value = messageHistory.value[key] || [];
};

// 删除会话
const deleteConversation = (key) => {
  conversations.value = conversations.value.filter(conv => conv.key !== key);
  
  if (currentConversation.value === key) {
    // 如果删除的是当前会话，切换到第一个会话
    const newKey = conversations.value[0]?.key || '';
    currentConversation.value = newKey;
    messages.value = messageHistory.value[newKey] || [];
  }
  
  // 删除历史记录
  delete messageHistory.value[key];
};

// 重命名会话
const renameConversation = (key, newLabel) => {
  const conversation = conversations.value.find(conv => conv.key === key);
  if (conversation) {
    conversation.label = newLabel;
  }
};

export {
  AI_CONFIG,
  loading,
  messageHistory,
  conversations,
  currentConversation,
  messages,
  sendMessage,
  abortRequest,
  createNewConversation,
  switchConversation,
  deleteConversation,
  renameConversation
};