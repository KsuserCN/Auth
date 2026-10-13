import type { ConsoleUser, DeveloperVerificationType, SystemRole } from '@/api/console'

export const roleLabel = (value: SystemRole) =>
  ({ USER: '普通用户', ADMIN: '普通管理员', SUPER_ADMIN: '超级管理员' })[value]

export const verificationLabel = (value: DeveloperVerificationType) =>
  ({ none: '未认证', personal: '个人认证', enterprise: '企业认证' })[value]

export const isVerifiedUser = (user: ConsoleUser) =>
  user.systemRole !== 'USER' || user.developerVerificationType !== 'none'
