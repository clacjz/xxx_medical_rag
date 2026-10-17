import { request } from '@/api/request'

let publicKey: string | null = null

export async function getPublicKey(): Promise<string> {
  if (publicKey) {
    return publicKey
  }

  try {
    const res = await request<{ publicKey: string }>('/auth/public-key', {
      method: 'GET'
    })

    if (res.code === 200 && res.data?.publicKey) {
      publicKey = res.data.publicKey
      return publicKey
    }

    throw new Error('Failed to get public key')
  } catch (error) {
    console.error('Failed to get public key:', error)
    throw error
  }
}

export async function encryptPassword(password: string): Promise<string> {
  const key = await getPublicKey()

  try {
    const encrypted = await rsaEncrypt(password, key)
    return encrypted
  } catch (error) {
    console.error('Password encryption failed:', error)
    throw new Error('密码加密失败')
  }
}

async function rsaEncrypt(text: string, publicKeyBase64: string): Promise<string> {
  const publicKeyBytes = base64ToBytes(publicKeyBase64)
  const publicKey = await importPublicKey(publicKeyBytes)

  const encoder = new TextEncoder()
  const data = encoder.encode(text)

  const encrypted = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    publicKey,
    data
  )

  return bytesToBase64(new Uint8Array(encrypted))
}

async function importPublicKey(keyData: Uint8Array): Promise<CryptoKey> {
  return await crypto.subtle.importKey(
    'spki',
    keyData.buffer as ArrayBuffer,
    {
      name: 'RSA-OAEP',
      hash: 'SHA-256'
    },
    false,
    ['encrypt']
  )
}

function base64ToBytes(base64: string): Uint8Array {
  const binaryString = atob(base64)
  const bytes = new Uint8Array(binaryString.length)
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return bytes
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

export function clearCachedPublicKey(): void {
  publicKey = null
}
