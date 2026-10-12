import type { User } from '@/api/auth'

type RoleInfo = Pick<User, 'systemRole' | 'developerVerificationType' | 'verificationType'>

export const isSystemAdmin = (user?: RoleInfo | null): boolean =>
  user?.systemRole === 'ADMIN' || user?.systemRole === 'SUPER_ADMIN'

export const canCreateApplications = (user?: RoleInfo | null): boolean => {
  const verification = user?.developerVerificationType ?? user?.verificationType
  return isSystemAdmin(user) || verification === 'personal' || verification === 'enterprise'
}

export const verificationDisplayType = (user?: RoleInfo | null): string =>
  isSystemAdmin(user) ? 'admin' : user?.developerVerificationType ?? user?.verificationType ?? 'none'
