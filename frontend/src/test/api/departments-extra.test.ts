import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { departmentsApi } from '@/api/departments'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Departments API (additional)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getTree calls /departments', async () => {
    ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

    await departmentsApi.getTree()

    expect(request).toHaveBeenCalledWith('/departments', { method: 'GET' })
  })

  it('getById calls /departments/{id}', async () => {
    ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 'd-1' } })

    await departmentsApi.getById('d-1')

    expect(request).toHaveBeenCalledWith('/departments/d-1')
  })

  it('create posts to /departments', async () => {
    ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 'd-1' } })

    await departmentsApi.create({ name: '内科', code: 'IM' } as any)

    expect(request).toHaveBeenCalledWith('/departments', {
      method: 'POST',
      data: { name: '内科', code: 'IM' }
    })
  })

  it('update posts to /departments/{id}', async () => {
    ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

    await departmentsApi.update('d-1', { name: '外科' } as any)

    expect(request).toHaveBeenCalledWith('/departments/d-1', {
      method: 'POST',
      data: { name: '外科' }
    })
  })

  it('delete calls DELETE /departments/{id}', async () => {
    ;(request as Mock).mockResolvedValue({ code: 200 })

    await departmentsApi.delete('d-1')

    expect(request).toHaveBeenCalledWith('/departments/d-1', { method: 'DELETE' })
  })
})
