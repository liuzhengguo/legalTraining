<template>
  <div class="app-container">
    <el-button type="primary" @click="open('register')">登记终端</el-button>
    <el-button @click="load">刷新状态</el-button>
    <el-table :data="devices" v-loading="loading">
      <el-table-column prop="deviceCode" label="设备码" />
      <el-table-column prop="siteNo" label="现场编号" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ ['待激活', '在线', '离线', '停用'][row.status] }}</template>
      </el-table-column>
      <el-table-column prop="bindZoneName" label="绑定展区" />
      <el-table-column prop="bindPlaylist" label="播放列表" />
      <el-table-column prop="lastHeartbeat" label="最近心跳" />
      <el-table-column prop="cacheSize" label="清单字节数" />
      <el-table-column label="操作" width="270" fixed="right" align="center" class-name="nowrap-actions">
        <template slot-scope="{ row }">
          <template v-if="row.status !== 3">
            <el-button type="text" @click="open('bind', row)">绑定</el-button>
            <el-button type="text" @click="open('config', row)">远程配置</el-button>
            <el-button type="text" @click="revoke(row)">停用</el-button>
            <el-button v-if="$auth.hasRole('admin')" type="text" @click="openCommands(row)">远程操作</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog :visible.sync="commandsOpen" title="终端远程操作" width="760px">
      <p>{{ commandDevice.deviceCode }}</p>
      <el-button :disabled="commandBusy" @click="sendCommand('capture')">请求截图</el-button>
      <el-button :disabled="commandBusy" @click="sendCommand('restart')">重启终端浏览器</el-button>
      <el-button :disabled="commandBusy" @click="sendCommand('offline-check')">离线冷启动自检</el-button>
      <el-button :disabled="commandBusy" @click="sendCommand('reboot')">重启终端系统</el-button>
      <el-button :disabled="commandBusy" @click="loadCommands">刷新</el-button>
      <el-table :data="commands">
        <el-table-column label="命令"><template slot-scope="{ row }">{{ { capture: '截图', restart: '重启浏览器', 'offline-check': '离线冷启动自检', reboot: '重启系统' }[row.action] }}</template></el-table-column>
        <el-table-column label="状态"><template slot-scope="{ row }">{{ ['待执行', '执行中', '已完成', '失败'][row.status] }}</template></el-table-column>
        <el-table-column prop="detail" label="结果" />
        <el-table-column label="文件"><template slot-scope="{ row }">
          <el-button v-if="row.hasImage" type="text" @click="downloadImage(row)">下载截图</el-button>
        </template></el-table-column>
      </el-table>
    </el-dialog>
    <h3>终端事件</h3>
    <el-table :data="events" empty-text="暂无终端事件">
      <el-table-column prop="deviceCode" label="设备码" />
      <el-table-column prop="detail" label="事件" />
      <el-table-column prop="reportTime" label="上报时间" />
    </el-table>
    <h3>播放列表</h3>
    <el-button @click="editPlaylist()">新建播放列表</el-button>
    <el-table :data="playlists">
      <el-table-column prop="playlistId" label="编号" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="展项数"><template slot-scope="{ row }">{{ row.items.length }}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right" align="center" class-name="nowrap-actions"><template slot-scope="{ row }"><el-button type="text" @click="editPlaylist(row)">编辑</el-button></template></el-table-column>
    </el-table>
    <el-dialog :visible.sync="playlistVisible" title="编辑播放列表" width="650px">
      <el-form label-width="100px" @submit.native.prevent="savePlaylist">
        <el-form-item label="名称"><el-input v-model.trim="playlist.name" maxlength="100" /></el-form-item>
        <el-form-item v-for="(entry, index) in playlist.items" :key="index" :label="'展项 ' + (index + 1)">
          <el-select v-model="entry.itemId" filterable placeholder="选择展项">
            <el-option v-for="row in publicItems" :key="row.targetId" :value="row.targetId" :label="row.targetName" />
          </el-select>
          <el-input-number v-model="entry.staySeconds" :min="1" :max="3600" aria-label="停留秒数" />
          <el-button type="text" :disabled="index === 0" @click="moveUp(index)">上移</el-button>
          <el-button type="text" @click="playlist.items.splice(index, 1)">移除</el-button>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="playlist.items.length >= 200" @click="playlist.items.push({ itemId: null, staySeconds: 15 })">添加展项</el-button>
          <el-button native-type="submit" type="primary" :loading="saving">保存</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="visible" :title="titles[action]" width="520px">
      <el-form label-width="110px" @submit.native.prevent="save">
        <template v-if="action === 'register'">
          <el-form-item label="设备码"><el-input v-model.trim="form.deviceCode" maxlength="64" /></el-form-item>
          <el-form-item label="现场编号"><el-input v-model.trim="form.siteNo" maxlength="64" /></el-form-item>
        </template>
        <template v-if="action === 'bind'">
          <el-form-item label="绑定方式">
            <el-radio-group v-model="binding"><el-radio label="zone">展区</el-radio><el-radio label="playlist">播放列表</el-radio><el-radio label="none">清除</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item v-if="binding === 'zone'" label="展区编号">
            <el-input v-model.trim="form.bindZone" inputmode="numeric" />
          </el-form-item>
          <el-form-item v-if="binding === 'playlist'" label="播放列表编号">
            <el-select v-model="form.bindPlaylist" filterable>
              <el-option v-for="row in playlists" :key="row.playlistId" :value="row.playlistId" :label="row.name" />
            </el-select>
          </el-form-item>
        </template>
        <template v-if="action === 'config'">
          <el-form-item label="展示模式">
            <el-select v-model="form.mode"><el-option label="轮播" value="kiosk" /><el-option label="图文" value="lite" /><el-option label="沉浸" value="immersive" /></el-select>
          </el-form-item>
          <el-form-item label="空闲时间（秒）"><el-input-number v-model="form.timeoutSeconds" :min="30" :max="3600" /></el-form-item>
          <el-form-item label="音量"><el-input-number v-model="form.volume" :min="0" :max="100" /></el-form-item>
          <el-form-item label="页面亮度"><el-input-number v-model="form.brightness" :min="0" :max="100" /></el-form-item>
        </template>
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving">保存</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'
import { saveAs } from 'file-saver'

export default {
  name: 'ExhibitDevice',
  data() {
    return {
      commandsOpen: false, commandDevice: {}, commandBusy: false, commands: [], devices: [], loading: false, visible: false, saving: false,
      events: [], playlists: [], publicItems: [], playlistVisible: false, playlist: { items: [] },
      action: 'register', binding: 'none', form: {}, id: null,
      titles: { register: '登记终端', bind: '绑定展区或播放列表', config: '远程配置' }
    }
  },
  created() { this.load() },
  methods: {
    async openCommands(row) {
      this.commandDevice = row
      this.commands = []
      this.commandsOpen = true
      await this.loadCommands()
    },
    async loadCommands() {
      const id = this.commandDevice.deviceId
      this.commands = (await request({ url: '/exh/device/' + id + '/commands' })).data
    },
    async sendCommand(action) {
      if (action === 'restart' || action === 'reboot') {
        const title = action === 'reboot' ? '重启终端系统' : '重启终端浏览器'
        try { await this.$modal.confirm(title + '将暂时中断展示。确定继续？') } catch { return }
      }
      this.commandBusy = true
      try {
        await request({ url: '/exh/device/' + this.commandDevice.deviceId + '/commands', method: 'post', data: { action } })
        await this.loadCommands()
      } finally { this.commandBusy = false }
    },
    async downloadImage(row) {
      const blob = await request({ url: '/exh/device/' + this.commandDevice.deviceId + '/commands/' + row.commandId + '/image',
        responseType: 'blob' })
      if (blob.type === 'image/png') saveAs(blob, 'kiosk-' + row.commandId + '.png')
      else this.$modal.msgError('截图无法下载')
    },
    async load() {
      this.loading = true
      try {
        const [devices, playlists, events] = await Promise.all([
          request({ url: '/exh/device/list' }), request({ url: '/exh/device/playlists' }), request({ url: '/exh/device/events' })
        ])
        this.devices = devices.data
        this.playlists = playlists.data
        this.events = events.data
      } finally { this.loading = false }
    },
    async editPlaylist(row) {
      const { data } = await request({ url: '/exh/device/content-options' })
      this.publicItems = data
      this.playlist = row ? { ...row, items: row.items.map(item => ({ ...item })) } : { name: '', items: [] }
      this.playlistVisible = true
    },
    moveUp(index) {
      const entries = this.playlist.items
      entries.splice(index - 1, 2, entries[index], entries[index - 1])
    },
    async savePlaylist() {
      if (this.saving) return
      const { playlistId, name, items } = this.playlist
      if (!name || !items.length || items.some(item => !item.itemId || !item.staySeconds)) {
        return this.$modal.msgError('请填写名称，并为每个展项设置停留时间')
      }
      this.saving = true
      try {
        await request({ url: '/exh/device/playlists' + (playlistId ? '/' + playlistId : ''),
          method: playlistId ? 'put' : 'post', data: { name, items } })
        this.playlistVisible = false
        await this.load()
      } finally { this.saving = false }
    },
    open(action, row = {}) {
      this.id = row.deviceId
      this.action = action
      this.binding = row.bindPlaylist ? 'playlist' : row.bindZone ? 'zone' : 'none'
      if (action === 'register') this.form = { deviceCode: '', siteNo: '' }
      if (action === 'bind') this.form = { bindZone: row.bindZone, bindPlaylist: row.bindPlaylist }
      if (action === 'config') this.form = { timeoutSeconds: 180, volume: 60, brightness: 80, ...row.config, mode: row.mode }
      this.visible = true
    },
    async save() {
      if (this.saving) return
      const register = this.action === 'register'
      let data = this.form
      if (register && !data.deviceCode) return this.$modal.msgError('请输入设备码')
      if (this.action === 'bind') {
        const key = this.binding === 'zone' ? 'bindZone' : 'bindPlaylist'
        if (this.binding !== 'none' && !/^[1-9][0-9]*$/.test(String(data[key] || ''))) {
          return this.$modal.msgError('请输入有效编号')
        }
        data = this.binding === 'none' ? {} : { [key]: data[key] }
      }
      this.saving = true
      try {
        const result = await request({
          url: register ? '/exh/device' : '/exh/device/' + this.id + '/' + this.action,
          method: register ? 'post' : 'put', data
        })
        this.visible = false
        if (register) {
          this.$alert('激活码 ' + result.data.activateCode + '，24 小时内有效。请在终端输入设备码和激活码。', '终端激活')
        } else {
          this.$modal.msgSuccess('已保存')
        }
        await this.load()
      } finally { this.saving = false }
    },
    async revoke(row) {
      try { await this.$modal.confirm('停用设备 ' + row.deviceCode + '？') } catch { return }
      await request({ url: '/exh/device/' + row.deviceId + '/revoke', method: 'put' })
      await this.load()
    }
  }
}
</script>
