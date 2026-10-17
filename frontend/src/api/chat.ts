import { request, axiosInstance } from './request'
import type {
  ChatSession,
  ChatMessage,
  ChatQueryRequest,
  ChatQueryResponse,
  SearchSource,
  StreamEvent,
  CreateSessionRequest
} from '@/types'

export interface SessionListParams {
  page?: number
  pageSize?: number
  collectionId?: string
  status?: string
}

export interface StreamCallbacks {
  onToken?: (content: string) => void
  onReasoning?: (content: string) => void
  onSource?: (source: SearchSource) => void
  onRewrite?: (rewrittenQuery: string) => void
  onDone?: (messageId: string, tokensUsed?: number, generationTimeMs?: number) => void
  onError?: (error: string) => void
}

export class ChatStreamService {
  private abortController: AbortController | null = null

  async streamQuery(request: ChatQueryRequest, callbacks: StreamCallbacks): Promise<void> {
    this.abortController = new AbortController()

    const token = localStorage.getItem('token') || ''
    const url = `/api/v1/chat/stream`

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(request),
        signal: this.abortController.signal,
        credentials: 'include'
      })

      if (!response.ok) {
        const errorText = await response.text()
        let errorMessage = '连接失败，请检查网络'
        try {
          const errorJson = JSON.parse(errorText)
          errorMessage = errorJson.message || errorMessage
        } catch {}
        callbacks.onError?.(errorMessage)
        throw new Error(errorMessage)
      }

      if (!response.body) {
        callbacks.onError?.('无法读取响应流')
        throw new Error('No response body')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        let readResult: ReadableStreamReadResult<Uint8Array>
        try {
          readResult = await reader.read()
        } catch (readError) {
          if (this.abortController?.signal.aborted) {
            callbacks.onError?.('请求已取消')
          } else {
            console.error('[ChatStream] Read error:', readError)
            callbacks.onError?.('读取响应失败')
          }
          throw readError
        }

        const { done, value } = readResult

        if (done) {
          // Process any remaining data in the buffer
          const remaining = buffer.trim()
          if (remaining) {
            console.warn('[ChatStream] Incomplete data in buffer:', remaining)
            // Try to parse as a last line
            if (remaining.startsWith('data:')) {
              const dataStr = remaining.slice(5).trim()
              if (dataStr && dataStr !== '[DONE]') {
                try {
                  const data = JSON.parse(dataStr) as StreamEvent
                  // Only handle token/reasoning events for partial data
                  if (data.type === 'token') callbacks.onToken?.(data.content || '')
                  else if (data.type === 'reasoning') callbacks.onReasoning?.(data.content || '')
                } catch {}
              }
            }
          }
          break
        }

        buffer += decoder.decode(value, { stream: true })

        // 使用更健壮的换行符处理
        while (true) {
          const newlineIndex = buffer.indexOf('\n')
          if (newlineIndex === -1) break

          let line = buffer.slice(0, newlineIndex)
          // 移除可能的 \r 结尾（处理 \r\n 或单独的 \r）
          if (line.endsWith('\r')) {
            line = line.slice(0, -1)
          }
          line = line.trim()
          buffer = buffer.slice(newlineIndex + 1)

          if (!line) continue

          if (line.startsWith('data:')) {
            const dataStr = line.slice(5).trim()
            if (dataStr === '[DONE]') {
              continue
            }

            try {
              const data = JSON.parse(dataStr) as StreamEvent

              switch (data.type) {
                case 'token':
                  callbacks.onToken?.(data.content || '')
                  break
                case 'reasoning':
                  callbacks.onReasoning?.(data.content || '')
                  break
                case 'source':
                  callbacks.onSource?.({
                    documentId: data.documentId || '',
                    title: data.title || '',
                    snippet: data.snippet || '',
                    score: data.score,
                    similarity: data.score,
                    collectionId: data.collectionId,
                    startPage: data.startPage,
                    endPage: data.endPage
                  })
                  break
                case 'rewrite':
                  callbacks.onRewrite?.(data.rewrittenQuery || '')
                  break
                case 'done':
                  callbacks.onDone?.(data.messageId || '', data.tokensUsed, data.generationTimeMs)
                  return
                case 'error':
                  callbacks.onError?.(data.message || '发生未知错误')
                  return
              }
            } catch (parseError) {
              console.error('[ChatStream] Failed to parse SSE data:', parseError, dataStr)
            }
          }
        }
      }

      callbacks.onDone?.('', undefined, undefined)
    } catch (error: any) {
      if (error.name === 'AbortError') {
        callbacks.onError?.('请求已取消')
      } else {
        console.error('[ChatStream] Stream error:', error)
        callbacks.onError?.(error.message || '连接失败，请检查网络')
      }
      throw error
    }
  }

  abort(): void {
    this.abortController?.abort()
    this.abortController = null
  }
}

export const chatStreamService = new ChatStreamService()

export const chatApi = {
  query: (data: ChatQueryRequest) => {
    return request<ChatQueryResponse>('/chat/query', {
      method: 'POST',
      data
    })
  },

  streamQuery: (request: ChatQueryRequest, callbacks: StreamCallbacks) => {
    return chatStreamService.streamQuery(request, callbacks)
  },

  listSessions: (params?: SessionListParams) => {
    return request<{ items: ChatSession[]; total: number }>('/chat/sessions', {
      method: 'GET',
      params: {
        page: params?.page ?? 0,
        pageSize: params?.pageSize ?? 20,
        collectionId: params?.collectionId,
        status: params?.status
      }
    })
  },

  listConversations: (params?: { page?: number; pageSize?: number; collectionId?: string }) => {
    return request<{ items: ChatSession[]; total: number }>('/chat/sessions', {
      method: 'GET',
      params: {
        page: params?.page ?? 0,
        pageSize: params?.pageSize ?? 20,
        collectionId: params?.collectionId
      }
    })
  },

  getSession: (sessionId: string) => {
    return request<ChatSession>(`/chat/sessions/${sessionId}`)
  },

  createSession: (data: CreateSessionRequest) => {
    return request<ChatSession>('/chat/sessions', {
      method: 'POST',
      data
    })
  },

  createConversation: (data: { title: string; collectionId: string }) => {
    return request<ChatSession>('/chat/sessions', {
      method: 'POST',
      data: {
        title: data.title,
        collectionIds: [data.collectionId]
      }
    })
  },

  updateSession: (sessionId: string, data: Partial<ChatSession>) => {
    return request<ChatSession>(`/chat/sessions/${sessionId}`, {
      method: 'PUT',
      data
    })
  },

  renameSession: (sessionId: string, title: string) => {
    return request<ChatSession>(`/chat/sessions/${sessionId}/title`, {
      method: 'PUT',
      params: { title }
    })
  },

  updateSessionCollections: (sessionId: string, collectionIds: string[]) => {
    return request<ChatSession>(`/chat/sessions/${sessionId}/collections`, {
      method: 'PUT',
      data: collectionIds
    })
  },

  deleteSession: (sessionId: string) => {
    return request(`/chat/sessions/${sessionId}`, {
      method: 'DELETE'
    })
  },

  deleteConversation: (conversationId: string) => {
    return request(`/chat/sessions/${conversationId}`, {
      method: 'DELETE'
    })
  },

  getSessionMessages: (sessionId: string) => {
    return request<{ items: ChatMessage[] }>(`/chat/sessions/${sessionId}/messages`)
  },

  getConversationMessages: (conversationId: string) => {
    return request<{ items: ChatMessage[] }>(`/chat/sessions/${conversationId}/messages`)
  },

  deleteMessage: (sessionId: string, messageId: string) => {
    return request(`/chat/sessions/${sessionId}/messages/${messageId}`, {
      method: 'DELETE'
    })
  },

  regenerateResponse: (sessionId: string, messageId: string) => {
    return request<ChatMessage>(`/chat/sessions/${sessionId}/messages/${messageId}/regenerate`, {
      method: 'POST'
    })
  },

  feedbackMessage: (sessionId: string, messageId: string, feedback: 'like' | 'dislike', comment?: string) => {
    return request<void>(`/chat/sessions/${sessionId}/messages/${messageId}/feedback`, {
      method: 'POST',
      params: { feedback, comment }
    })
  },

  exportSession: (sessionId: string, format: 'json' | 'markdown' | 'pdf' = 'markdown') => {
    return axiosInstance.get(`/chat/sessions/${sessionId}/export`, {
      params: { format },
      responseType: 'blob'
    })
  }
}
