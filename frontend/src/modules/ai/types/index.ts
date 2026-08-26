export interface AiChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface AiChatRequest {
  message: string;
  history: AiChatMessage[];
}

export interface AiChatResponse {
  reply: string;
}
