import { readFile, open, rename, unlink } from 'node:fs/promises'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { join } from 'node:path'

const run = promisify(execFile)
const lifetime = 15 * 60 * 1000

async function bootIdentity() {
  if (process.platform === 'linux') return (await readFile('/proc/sys/kernel/random/boot_id', 'utf8')).trim()
  if (process.platform === 'win32') {
    const { stdout } = await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      '(Get-CimInstance Win32_OperatingSystem).LastBootUpTime.ToUniversalTime().Ticks'], { timeout: 8000, windowsHide: true })
    const id = stdout.trim()
    if (!/^\d+$/.test(id)) throw new Error('Cannot determine Windows boot identity')
    return id
  }
  throw new Error('This operating system does not support remote reboot')
}

async function invokeReboot() {
  if (process.platform === 'win32') {
    await run('shutdown.exe', ['/r', '/t', '0'], { timeout: 10000, windowsHide: true })
  } else if (process.platform === 'linux') {
    await run('systemctl', ['reboot'], { timeout: 10000 })
  } else {
    throw new Error('This operating system does not support remote reboot')
  }
}

export function createRebootCoordinator(profile, enabled, { getBootId = bootIdentity, reboot = invokeReboot, now = Date.now } = {}) {
  const marker = join(profile, 'legaltraining-pending-reboot.json')
  async function pending() {
    try {
      const data = JSON.parse(await readFile(marker, 'utf8'))
      if (!Number.isSafeInteger(data.commandId) || !/^[a-f0-9-]{36}$/.test(data.lease)
          || typeof data.deviceCode !== 'string' || typeof data.bootId !== 'string' || !Number.isFinite(data.requestedAt)) {
        throw new Error('Invalid reboot marker')
      }
      return data
    } catch (error) {
      if (error.code === 'ENOENT') return null
      throw error
    }
  }

  const remove = () => unlink(marker)
  return {
    async begin(command, deviceCode) {
      if (!enabled) throw new Error('Operating system reboot is disabled on this terminal')
      if (await pending()) throw new Error('A reboot was already requested')
      const data = { commandId: command.commandId, lease: command.lease, deviceCode,
        bootId: await getBootId(), requestedAt: now() }
      const temp = marker + '.tmp'
      const target = await open(temp, 'w', 0o600)
      try {
        await target.writeFile(JSON.stringify(data))
        await target.sync()
      } finally { await target.close() }
      await rename(temp, marker)
      try { await reboot() }
      catch (error) { await remove(); throw error }
    },
    async reconcile(deviceCode, report) {
      const data = await pending()
      if (!data) return false
      if (data.deviceCode !== deviceCode) {
        await remove()
        return false
      }
      const booted = await getBootId() !== data.bootId
      if (!booted && now() - data.requestedAt < lifetime) return true
      const accepted = await report(data.commandId, { lease: data.lease, success: booted,
        detail: booted ? '终端系统已重启，运行器已恢复' : '未检测到终端系统重启' })
      if (accepted) await remove()
      return !accepted
    }
  }
}
