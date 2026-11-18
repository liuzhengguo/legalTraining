<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({ source: String, spots: { type: Array, default: () => [] } })
const emit = defineEmits(['error'])
const canvas = ref(null)
const ready = ref(false)
const yaw = ref(0)
const pitch = ref(0)
const fov = ref(105 * Math.PI / 180)
const aspect = ref(1)
let gl, program, texture, buffer, observer, image, pointer, loadTimer
let active = true
const vertex = 'attribute vec2 point; void main() { gl_Position = vec4(point, 0.0, 1.0); }'
const fragment = "precision highp float;\nuniform sampler2D scene;\nuniform vec2 size;\nuniform vec3 camera;\nvoid main() {\n  vec2 uv = gl_FragCoord.xy / size * 2.0 - 1.0;\n  uv.x *= size.x / size.y;\n  vec3 ray = normalize(vec3(uv * camera.z, 1.0));\n  float cp = cos(camera.y), sp = sin(camera.y);\n  float cy = cos(camera.x), sy = sin(camera.x);\n  vec3 rotated = vec3(ray.x, cp * ray.y + sp * ray.z, -sp * ray.y + cp * ray.z);\n  vec3 world = vec3(cy * rotated.x + sy * rotated.z, rotated.y, -sy * rotated.x + cy * rotated.z);\n  gl_FragColor = texture2D(scene, vec2(0.5 + atan(world.x, world.z)/6.28318530718,\n                                     0.5 - asin(clamp(world.y,-1.0,1.0))/3.14159265359));\n}"

function shader(type, source) {
  const result = gl.createShader(type)
  gl.shaderSource(result, source)
  gl.compileShader(result)
  if (!gl.getShaderParameter(result, gl.COMPILE_STATUS)) {
    gl.deleteShader(result)
    throw new Error('全景着色器不可用')
  }
  return result
}

function draw() {
  if (!ready.value || !gl) return
  const target = canvas.value
  const ratio = Math.min(devicePixelRatio || 1, 2)
  const width = Math.max(1, Math.round(target.clientWidth * ratio))
  const height = Math.max(1, Math.round(target.clientHeight * ratio))
  if (target.width !== width) target.width = width
  if (target.height !== height) target.height = height
  aspect.value = target.width / target.height
  gl.viewport(0, 0, target.width, target.height)
  gl.uniform2f(gl.getUniformLocation(program, 'size'), target.width, target.height)
  gl.uniform3f(gl.getUniformLocation(program, 'camera'), yaw.value, pitch.value, Math.tan(fov.value / 2))
  gl.drawArrays(gl.TRIANGLES, 0, 3)
}

function move(horizontal, vertical) {
  yaw.value = (yaw.value + horizontal) % (2 * Math.PI)
  pitch.value = Math.max(-1.45, Math.min(1.45, pitch.value + vertical))
  draw()
}

function zoom(delta) {
  fov.value = Math.max(Math.PI / 6, Math.min(2 * Math.PI / 3, fov.value + delta))
  draw()
}

function keydown(event) {
  const actions = {
    ArrowLeft: () => move(-0.11, 0),
    ArrowRight: () => move(0.11, 0),
    ArrowUp: () => move(0, 0.11),
    ArrowDown: () => move(0, -0.11),
    '+': () => zoom(-0.12),
    '-': () => zoom(0.12)
  }
  if (actions[event.key]) {
    event.preventDefault()
    event.stopPropagation()
    actions[event.key]()
  }
}

function pointerDown(event) {
  if (pointer || event.button !== 0) return
  pointer = { id: event.pointerId, x: event.clientX, y: event.clientY }
  event.currentTarget.setPointerCapture(event.pointerId)
}

function pointerMove(event) {
  if (pointer?.id !== event.pointerId) return
  move((pointer.x - event.clientX) * 0.005, (event.clientY - pointer.y) * 0.005)
  pointer.x = event.clientX
  pointer.y = event.clientY
}

function pointerEnd(event) {
  if (pointer?.id === event.pointerId) pointer = null
}

const projected = computed(() => {
  const c = Math.cos(yaw.value), s = Math.sin(yaw.value)
  const cp = Math.cos(pitch.value), sp = Math.sin(pitch.value)
  const scale = Math.tan(fov.value / 2)
  return props.spots.flatMap(spot => {
    const longitude = (spot.posX - 0.5) * Math.PI * 2
    const latitude = (0.5 - spot.posY) * Math.PI
    const wx = Math.sin(longitude) * Math.cos(latitude)
    const wy = Math.sin(latitude)
    const wz = Math.cos(longitude) * Math.cos(latitude)
    const x = c * wx - s * wz
    const forward = s * wx + c * wz
    const y = cp * wy - sp * forward
    const z = sp * wy + cp * forward
    if (z <= 0) return []
    const nx = x / (z * scale * aspect.value)
    const ny = y / (z * scale)
    if (Math.abs(nx) > 0.96 || Math.abs(ny) > 0.89) return []
    return [{ ...spot, left: (nx + 1) * 50 + '%', top: (1 - ny) * 50 + '%' }]
  })
})

function failed() {
  clearTimeout(loadTimer)
  ready.value = false
  emit('error')
}

onMounted(() => {
  image = new Image()
  image.crossOrigin = 'anonymous'
  image.onerror = failed
  image.onload = () => {
    clearTimeout(loadTimer)
    if (!active) return
    try {
      gl = canvas.value.getContext('webgl', { alpha: false })
      if (!gl || Math.abs(image.width / image.height - 2) > 0.02) throw new Error('全景图不可用')
      program = gl.createProgram()
      const vs = shader(gl.VERTEX_SHADER, vertex)
      const fs = shader(gl.FRAGMENT_SHADER, fragment)
      gl.attachShader(program, vs)
      gl.attachShader(program, fs)
      gl.linkProgram(program)
      gl.deleteShader(vs)
      gl.deleteShader(fs)
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('全景渲染不可用')
      gl.useProgram(program)
      buffer = gl.createBuffer()
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 3,-1, -1,3]), gl.STATIC_DRAW)
      const point = gl.getAttribLocation(program, 'point')
      gl.enableVertexAttribArray(point)
      gl.vertexAttribPointer(point, 2, gl.FLOAT, false, 0, 0)
      texture = gl.createTexture()
      gl.bindTexture(gl.TEXTURE_2D, texture)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
      const limit = gl.getParameter(gl.MAX_TEXTURE_SIZE)
      let source = image
      if (image.width > limit) {
        source = document.createElement('canvas')
        source.width = limit
        source.height = Math.round(image.height * limit / image.width)
        source.getContext('2d').drawImage(image, 0, 0, source.width, source.height)
      }
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source)
      if (gl.getError() !== gl.NO_ERROR) throw new Error('全景图超出设备可用尺寸')
      observer = new ResizeObserver(draw)
      observer.observe(canvas.value)
      ready.value = true
      draw()
    } catch {
      failed()
    }
  }
  image.src = props.source
  loadTimer = setTimeout(failed, 30000)
})

onUnmounted(() => {
  active = false
  clearTimeout(loadTimer)
  observer?.disconnect()
  if (image) image.onload = image.onerror = null
  if (gl) {
    gl.deleteTexture(texture)
    gl.deleteBuffer(buffer)
    gl.deleteProgram(program)
  }
})
</script>

<template>
  <section class="panorama" aria-label="全景导览">
    <canvas ref="canvas" tabindex="0" aria-label="拖动或用方向键转动视角，用加号和减号缩放"
      @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerEnd" @pointercancel="pointerEnd"
      @keydown="keydown" @wheel.prevent="zoom($event.deltaY * .001)" @webglcontextlost.prevent="failed" />
    <p v-if="!ready" class="loading" role="status">正在加载全景图…</p>
    <template v-if="ready">
      <RouterLink v-for="spot in projected" :key="spot.spotId" class="panorama-spot"
        :style="{ left: spot.left, top: spot.top }"
        :to="'/immersive/' + spot.targetType + '/' + spot.targetId">
        <span aria-hidden="true">{{ { arrow: '➜', item: '▣', info: 'ℹ' }[spot.icon] || '➜' }}</span>
        {{ spot.spotName }}
      </RouterLink>
      <div class="controls">
        <button aria-label="向左转" @click="move(-.2, 0)">←</button>
        <button aria-label="向右转" @click="move(.2, 0)">→</button>
        <button aria-label="放大" @click="zoom(-.12)">＋</button>
        <button aria-label="缩小" @click="zoom(.12)">－</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.panorama { position: relative; width: 100%; height: min(62vh, 640px); min-height: 20rem; overflow: hidden; }
canvas { display: block; width: 100%; height: 100%; cursor: grab; touch-action: none; }
canvas:active { cursor: grabbing; }
.loading { position: absolute; inset: 45% 0 auto; margin: 0; text-align: center; pointer-events: none; }
.panorama-spot, .controls { position: absolute; }
.panorama-spot { transform: translate(-50%, -50%); padding: .3rem .55rem; border-radius: .4rem; background: var(--ex-panel); color: var(--ex-text); }
.controls { display: flex; gap: .4rem; bottom: .8rem; right: .8rem; }
button { padding: .4rem .65rem; }
</style>
