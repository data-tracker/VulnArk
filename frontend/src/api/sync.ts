import request from './request'

// DTrack 同步状态
export interface SyncLog {
  id: number
  syncTime: string
  triggerType: string
  added: number
  updated: number
  resolved: number
  failed: number
  detail: string | null
}

export const testSyncConnection = async (): Promise<{ ok: boolean; projectCount?: number; message?: string }> => {
  return request.post('/sync/test')
}

export const runSync = async (): Promise<void> => {
  return request.post('/sync/run')
}

export const getSyncStatus = async (): Promise<{ running: boolean; configured: boolean; logs: SyncLog[] }> => {
  return request.get('/sync/status')
}

export const getDtrackProjects = async (): Promise<string[]> => {
  return request.get('/sync/dtrack-projects')
}
