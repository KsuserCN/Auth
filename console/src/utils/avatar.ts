export const avatarFallback = (username?: string | null): string => {
  const normalized = username?.trim()
  return normalized ? Array.from(normalized)[0] || 'K' : 'K'
}
