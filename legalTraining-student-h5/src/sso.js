import { request } from './api'

let pending

function ticketFromLocation() {
  return new URLSearchParams(window.location.search).get('sso')
}

function stripTicket() {
  const url = new URL(window.location.href)
  if (!url.searchParams.has('sso')) return
  url.searchParams.delete('sso')
  window.history.replaceState(null, '', url.pathname + url.search + url.hash)
}

async function exchange(ticket) {
  try {
    const result = await request('/edu/student/sso-login', {
      method: 'POST',
      authenticated: false,
      body: { ticket }
    })
    const token = result?.token || result?.data?.token
    if (token) localStorage.setItem('lt_token', token)
    return !!token
  } catch {
    return false
  } finally {
    stripTicket()
  }
}

/** 门户票据只在地址栏短暂出现，换取令牌后立即从地址栏移除。 */
export function consumeSsoTicket() {
  const ticket = ticketFromLocation()
  if (!ticket) return Promise.resolve(false)
  if (!pending) {
    pending = exchange(ticket).finally(() => { pending = null })
  }
  return pending
}
