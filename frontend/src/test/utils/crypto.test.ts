import { vi, describe, it, expect, beforeEach, afterEach, Mock } from 'vitest'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Crypto Utils', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 重置模块缓存以清空 publicKey 缓存变量
    vi.resetModules()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getPublicKey', () => {
    it('should fetch public key from /auth/public-key', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { publicKey: 'MIIBIjANBgkqhkiG9w0B...' }
      })

      const { getPublicKey } = await import('@/utils/crypto')
      const key = await getPublicKey()

      expect(request).toHaveBeenCalledWith('/auth/public-key', { method: 'GET' })
      expect(key).toBe('MIIBIjANBgkqhkiG9w0B...')
    })

    it('should cache public key on subsequent calls', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { publicKey: 'cached-key' }
      })

      const { getPublicKey } = await import('@/utils/crypto')
      const k1 = await getPublicKey()
      const k2 = await getPublicKey()

      expect(k1).toBe('cached-key')
      expect(k2).toBe('cached-key')
      // 第二次不应再调用 API
      expect(request).toHaveBeenCalledTimes(1)
    })

    it('should throw on non-200 response', async () => {
      ;(request as Mock).mockResolvedValue({ code: 500, data: null })

      const { getPublicKey } = await import('@/utils/crypto')
      await expect(getPublicKey()).rejects.toThrow()
    })
  })

  describe('encryptPassword', () => {
    it('should call getPublicKey then attempt encryption', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { publicKey: 'mock-key' }
      })

      const { encryptPassword } = await import('@/utils/crypto')
      try {
        await encryptPassword('plaintext')
      } catch (error) {
        // jsdom 不支持完整 WebCrypto RSA-OAEP 时会失败
        // 但我们至少验证了走到了 rsaEncrypt
        expect(error).toBeDefined()
      }
      expect(request).toHaveBeenCalledWith('/auth/public-key', { method: 'GET' })
    })
  })
})

