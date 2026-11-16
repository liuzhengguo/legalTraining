<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  source: { type: String, required: true },
  alt: { type: String, default: '' },
  lazy: { type: Boolean, default: false }
})
const emit = defineEmits(['unavailable'])
const failed = ref(false)
watch(() => props.source, () => { failed.value = false })

function markUnavailable() {
  failed.value = true
  emit('unavailable')
}
</script>

<template>
  <img v-if="!failed" :src="source" :alt="alt" :loading="lazy ? 'lazy' : 'eager'" decoding="async"
       @error="markUnavailable">
  <div v-else class="image-placeholder" role="img" :aria-label="alt ? alt + '暂不可用' : '图片暂不可用'">
    图片暂不可用
  </div>
</template>

<style scoped>
.image-placeholder { display: grid; place-items: center; min-height: 6rem; padding: .75rem;
  background: var(--ex-panel); color: var(--ex-text); }
</style>
