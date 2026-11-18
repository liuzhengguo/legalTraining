import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { setTimeout as pause } from 'node:timers/promises'
import { createRebootCoordinator } from './v3-kiosk-reboot.mjs'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.LT_KIOSK_PLAYWRIGHT || 'playwright')
const config = JSON.parse(await readFile(process.argv[2], 'utf8'))
const pageUrl = new URL(config.url)
const commandBase = new URL(config.commandBase)
const trusted = url => url.protocol === 'https:' || url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname)
if (!trusted(pageUrl) || !trusted(commandBase)
    || pageUrl.username || pageUrl.password || commandBase.username || commandBase.password
    || !config.profile) throw new Error('Invalid kiosk configuration')

const galleryApi = config.galleryApi || '/gallery-api'
const key = 'lt-device:' + galleryApi + ':credentials'
const viewport = { width: 1280, height: 720 }
const proxy = config.proxy && typeof config.proxy.server === 'string' && config.proxy.server ? {
  server: config.proxy.server,
  ...(config.proxy.bypass ? { bypass: config.proxy.bypass } : {}),
  ...(config.proxy.username ? { username: config.proxy.username } : {}),
  ...(config.proxy.password ? { password: config.proxy.password } : {})
} : undefined
if (proxy && !['http:', 'https:', 'socks5:'].includes(new URL(proxy.server).protocol)) {
  throw new Error('Invalid kiosk proxy configuration')
}
const reboot = createRebootCoordinator(config.profile, config.allowSystemReboot === true)
let context
let page
let stopping = false
let lastCredentials

async function openKiosk() {
  context = await chromium.launchPersistentContext(config.profile, {
    headless: Boolean(config.headless),
    viewport,
    executablePath: config.browserBinary,
    proxy,
    args: config.headless ? [] : ['--kiosk', '--no-first-run']
  })
  page = context.pages()[0] || await context.newPage()
  if (config.credentialsFile) {
    const credentials = JSON.parse(await readFile(config.credentialsFile, 'utf8'))
    await context.addInitScript(({ expectedOrigin, storageKey, credentials }) => {
      if (location.origin === expectedOrigin && !localStorage.getItem(storageKey)) {
        localStorage.setItem(storageKey, JSON.stringify(credentials))
      }
    }, { expectedOrigin: pageUrl.origin, storageKey: key, credentials })
  }
  await page.goto(pageUrl.href, { waitUntil: 'domcontentloaded', timeout: 15000 })
}

async function request(path, headers, body) {
  return fetch(commandBase.href.replace(/\/$/, '') + '/commands/' + path, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(6500),
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
}

async function credentials() {
  if (new URL(page.url()).origin !== pageUrl.origin) return null
  return page.evaluate(storageKey => {
    try { return JSON.parse(localStorage.getItem(storageKey)) } catch { return null }
  }, key)
}

async function offlineCheck() {
  const inspect = () => page.evaluate(apiBase => {
    const storagePrefix = 'lt-device:' + apiBase + ':'
    let manifest = null
    let meta = null
    try { manifest = JSON.parse(localStorage.getItem(storagePrefix + 'manifest') || 'null') } catch {}
    try { meta = JSON.parse(localStorage.getItem(storagePrefix + 'asset-meta') || 'null') } catch {}
    return {
      controller: Boolean(navigator.serviceWorker && navigator.serviceWorker.controller),
      items: Array.isArray(manifest?.items) ? manifest.items.length : 0,
      cachedAssets: Object.keys(meta?.urls || {}).length,
      offlineText: /离线模式|暂无可播放的离线展项/.test(document.body.innerText)
    }
  }, galleryApi)
  await inspect()
  await context.setOffline(true)
  try {
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForFunction(() => /离线模式|暂无可播放的离线展项/.test(document.body.innerText),
      null, { timeout: 6000 })
    const after = await inspect()
    const ok = after.controller && after.items > 0 && after.cachedAssets > 0 && after.offlineText
    const detail = ok
      ? `离线冷启动通过：${after.items}项展项/${after.cachedAssets}个缓存素材`
      : `离线冷启动证据不足：${JSON.stringify(after)}`
    return { success: ok, detail: detail.slice(0, 220) }
  } catch {
    return { success: false, detail: '离线冷启动失败：请确认应用壳与素材已缓存' }
  } finally {
    await context.setOffline(false)
  }
}

async function handle(command, headers, deviceCode) {
  let result
  try {
    if (command.action === 'capture') {
      const png = await page.screenshot({ type: 'png', timeout: 5000 })
      if (png.length > 2 * 1024 * 1024) throw new Error('Screenshot is too large')
      result = { success: true, png: png.toString('base64'), detail: '终端截图已保存' }
    } else if (command.action === 'restart') {
      await context.close()
      context = null
      await openKiosk()
      result = { success: true, detail: '终端浏览器已重新启动' }
    } else if (command.action === 'offline-check') {
      result = await offlineCheck()
    } else if (command.action === 'reboot') {
      await reboot.begin(command, deviceCode)
      return
    } else {
      throw new Error('Unsupported command')
    }
  } catch {
    result = { success: false, detail: '终端无法完成命令' }
  }
  await request(command.commandId + '/result', headers, { lease: command.lease, ...result })
}

async function reportCrash(auth) {
  if (!auth) return
  await fetch(commandBase.href.replace(/\/$/, '') + '/events', {
    method: 'POST',
    redirect: 'error',
    signal: AbortSignal.timeout(3000),
    headers: { 'X-Device-Code': auth.deviceCode, 'X-Device-Token': auth.token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'crash', detail: '终端浏览器异常退出，运行器已尝试重新启动' })
  }).catch(() => {})
}

for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { stopping = true })
try {
  await openKiosk()
  while (!stopping) {
    try {
      if (!context || page.isClosed()) {
        await reportCrash(lastCredentials)
        await context?.close().catch(() => {})
        await openKiosk()
      }
      const auth = await credentials()
      lastCredentials = auth
      if (auth?.deviceCode && /^[a-f0-9]{32,64}$/.test(auth.token)) {
        const headers = { 'X-Device-Code': auth.deviceCode, 'X-Device-Token': auth.token }
        const rebootPending = await reboot.reconcile(auth.deviceCode, async (id, result) => {
          const response = await request(id + '/result', headers, result)
          return [204, 401, 404].includes(response.status)
        })
        if (!rebootPending) {
          const response = await request('claim', headers)
          if (response.status === 200) await handle((await response.json()).data, headers, auth.deviceCode)
        }
      }
    } catch (error) {
      process.stderr.write('Kiosk connection or browser error: ' + error.name + '\n')
    }
    await pause(2500)
  }
} finally {
  await context?.close().catch(() => {})
}
