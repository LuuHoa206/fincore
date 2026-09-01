export function getAuthenticationRedirect(state: unknown) {
  if (typeof state !== 'object' || state === null || !('from' in state)) {
    return '/'
  }

  const from = (state as { from?: unknown }).from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}
