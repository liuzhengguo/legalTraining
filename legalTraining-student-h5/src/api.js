const base = import.meta.env.VITE_API_BASE || '/dev-api'
export async function request(path, { method = 'GET', body, authenticated = true, binary = false } = {}) {
  const headers = {}
  const token = localStorage.getItem('lt_token')
  if (authenticated && token) headers.Authorization = `Bearer ${token}`
  const upload = body instanceof FormData
  if (body && !upload) headers['Content-Type'] = 'application/json'
  const response = await fetch(base + path, { method, headers, body: body && (upload ? body : JSON.stringify(body)) })
  const json = response.headers.get('Content-Type')?.includes('application/json')
  const result = json ? await response.json() : null
  if (authenticated && (result?.code === 401 || response.status === 401)) {
    localStorage.removeItem('lt_token')
    window.dispatchEvent(new Event('lt-session-expired'))
  }
  if (binary && response.ok && !json) return response.blob()
  if (response.status === 415) throw new Error('此文件格式暂不支持在线预览')
  if (!response.ok || result?.code !== 200 || binary) throw new Error(result?.msg || '请求失败')
  return result
}
