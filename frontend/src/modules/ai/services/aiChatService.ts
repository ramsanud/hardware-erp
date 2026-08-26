import { apiPost } from '@/services/apiClient';
import type { AiChatRequest, AiChatResponse } from '../types';

/**
 * Backend: ai/AiChatController.java. A tool-use round trip against the LLM
 * can take longer than the app's normal 30s default - a slow reply here
 * must not read as "backend is broken" the way a slow CRUD call would.
 */
export const aiChatService = {
  chat: (body: AiChatRequest) => apiPost<AiChatResponse>('/v1/ai/chat', body, { timeout: 60_000 }),
};
