/* 终端应用壳缓存：只缓存同源静态资源，不缓存公开接口与受控素材。 */
const SHELL_VERSION = 'v1'
const SHELL_CACHE = 'lt-kiosk-shell-' + SHELL_VERSION
const API_PREFIXES = ['/gallery-api', '/profile', '/openapi']

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE)
    const entry = new Request('./index.html', { cache: 'reload' })
    const html = await fetch(entry)
    if (!html.ok) throw new Error('Cannot cache application shell')
    await cache.put(entry, html.clone())
    const assets = new Set(['./', './index.html'])
    const text = await html.text()
    for (const match of text.matchAll(/(?:src|href)\s*=\s*["']([^"']+)["']/gi)) {
      const raw = match[1]
      if (!raw || raw.startsWith('#') || raw.startsWith('data:') || /^(?:https?:)?\/\//i.test(raw)) continue
      const url = new URL(raw, self.registration.scope)
      if (url.origin !== self.location.origin || url.pathname === '/sw.js') continue
      assets.add(url.href)
    }
    await Promise.allSettled([...assets].map(url => cache.add(new Request(url, { cache: 'reload' }))))
    await self.skipWaiting()
  })())
})

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys()
    await Promise.all(keys.filter(key => key.startsWith('lt-kiosk-shell-') && key !== SHELL_CACHE)
      .map(key => caches.delete(key)))
    await self.clients.claim()
  })())
})

function networkFirst(request) {
  const cachePromise = caches.open(SHELL_CACHE)
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 4000)
  return fetch(request, { signal: controller.signal })
    .then(async response => {
      if (response && response.ok) {
        const cache = await cachePromise
        await cache.put('./index.html', response.clone())
      }
      return response
    })
    .catch(async () => {
      const cache = await cachePromise
      return (await cache.match('./index.html')) || (await cache.match('./')) || Response.error()
    })
    .finally(() => clearTimeout(timer))
}

function cacheFirst(request) {
  const cachePromise = caches.open(SHELL_CACHE)
  const network = fetch(request).then(async response => {
    if (response && response.ok) {
      const cache = await cachePromise
      await cache.put(request, response.clone())
    }
    return response
  }).catch(() => null)
  return cachePromise.then(async cache => {
    const saved = await cache.match(request)
    return saved || await network || Response.error()
  })
}

self.addEventListener('fetch', event => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin || API_PREFIXES.some(prefix => url.pathname.startsWith(prefix))) return
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request))
    return
  }
  const staticAsset = /\.(?:js|css|woff2?|png|jpe?g|webp|svg|ico|gif|map)$/i.test(url.pathname)
  if (staticAsset) event.respondWith(cacheFirst(request))
})
