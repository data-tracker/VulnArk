import request from './request'

// 系统配置（按 category 分组）
export type SystemConfigGrouped = Record<string, Record<string, string>>

// 获取全部系统配置
export const getSystemConfigs = async (): Promise<SystemConfigGrouped> => {
  return request.get('/system-configs')
}

// 批量保存系统配置
export const saveSystemConfigs = async (configs: Record<string, string>): Promise<void> => {
  return request.put('/system-configs', configs)
}

// SMTP 测试发送
export const sendTestMail = async (smtp: Record<string, string>, to: string): Promise<void> => {
  return request.post('/system-configs/smtp/test', { smtp, to })
}
