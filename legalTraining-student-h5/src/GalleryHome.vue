<script setup>
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { request } from './api'
import { navigation } from './navigation'
import heroImage from './assets/gallery-hero.webp'

const notices = ref([])
const loading = ref(true)

const fallbackNotices = [
  { id: 'local-1', title: '云墙数字展厅已开放参观', content: '足不出户，沉浸式参观法治文化展厅。', sendTime: '2026-11-15 09:00', isRead: '1' },
  { id: 'local-2', title: '法治宣传月活动安排已发布', content: '请同学们及时查看课程任务与活动通知。', sendTime: '2026-11-12 14:30', isRead: '1' },
  { id: 'local-3', title: '课程任务提交提醒', content: '请在截止时间前完成并提交相关学习任务。', sendTime: '2026-11-08 10:15', isRead: '1' }
]

async function loadNotices() {
  try {
    const result = await request('/edu/learning/notifications')
    notices.value = Array.isArray(result?.data) ? result.data.slice(0, 4) : fallbackNotices
  } catch {
    notices.value = fallbackNotices
  } finally {
    loading.value = false
  }
}
onMounted(loadNotices)

const quickEntries = [
  { to: '/course', label: '我的课程', icon: 'records' },
  { to: '/score', label: '我的成绩', icon: 'chart-trending-o' },
  { to: '/achievements', label: '我的成果', icon: 'award-o' }
]

async function shareApp() {
  const url = window.location.origin + '/home'
  try {
    if (navigator.share) {
      await navigator.share({ title: '云墙数字展厅', text: '云上法治数字展厅，随时随地参观学习', url })
    } else {
      await navigator.clipboard.writeText(url)
      showToast('展厅链接已复制')
    }
  } catch { /* 用户取消分享 */ }
}
</script>

<template>
  <div class="gallery-home">
    <header class="hero" :style="{ backgroundImage: 'url(' + heroImage + ')' }">
      <div class="hero-mask"></div>
      <div class="hero-top">
        <span class="hero-brand">云墙数字展厅</span>
        <button type="button" class="hero-share" aria-label="分享展厅" @click="shareApp">
          <van-icon name="share-o" />
        </button>
      </div>
      <div class="hero-copy">
        <p class="hero-sub">云上法治 · 数字展厅</p>
        <h1>云墙数字展厅</h1>
        <p class="hero-desc">足不出户，沉浸式参观法治文化展厅</p>
      </div>
    </header>

    <div class="home-body">
      <nav class="quick-grid" aria-label="常用功能">
        <RouterLink v-for="entry in quickEntries" :key="entry.to" :to="entry.to" class="quick-item">
          <span class="quick-icon"><van-icon :name="entry.icon" /></span>
          <span>{{ entry.label }}</span>
        </RouterLink>
      </nav>

      <section class="notice-section" aria-labelledby="notice-title">
        <div class="section-head">
          <h2 id="notice-title">通知公告</h2>
          <RouterLink to="/notifications" class="section-more">查看更多 <van-icon name="arrow" /></RouterLink>
        </div>
        <ul class="notice-list">
          <li v-for="notice in notices" :key="notice.id">
            <RouterLink to="/notifications" class="notice-card">
              <span class="notice-dot"></span>
              <span class="notice-info">
                <strong>{{ notice.title }}<em v-if="notice.isRead === '0'">未读</em></strong>
                <small>{{ notice.content || '点击查看通知详情' }}</small>
                <time>{{ notice.sendTime || '' }}</time>
              </span>
              <van-icon name="arrow" />
            </RouterLink>
          </li>
        </ul>
        <p v-if="!loading && !notices.length" class="empty">暂无通知公告</p>
      </section>
    </div>

    <nav class="primary-nav" aria-label="主导航">
      <RouterLink v-for="entry in navigation" :key="entry.to" :to="entry.to"
                  :class="{ selected: entry.to === '/home' }" aria-current="page">
        <van-icon :name="entry.icon" aria-hidden="true" />
        <span>{{ entry.title }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.gallery-home {
  width: 100%;
  max-width: var(--lt-app-width);
  min-height: 100dvh;
  margin-inline: auto;
  padding-bottom: calc(4.2rem + var(--lt-safe-bottom));
  background: var(--lt-field);
  box-shadow: 0 0 48px #21050466;
}
.hero {
  position: relative;
  height: clamp(15rem, 52vw, 20rem);
  background-position: center;
  background-size: cover;
  color: var(--lt-on-dark);
  overflow: hidden;
}
.hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #3d100ea8 0%, #3d100e3d 40%, #3d100ecc 100%);
}
.hero-top, .hero-copy { position: relative; z-index: 1; }
.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(.9rem + var(--lt-safe-top)) 1.15rem 0;
}
.hero-brand { display: flex; align-items: center; gap: .4rem; font-size: 1rem; font-weight: 700; letter-spacing: .04em; }
.hero-brand::before { content: '⚖'; font-size: 1.2rem; }
.hero-share { display: grid; place-items: center; width: 2.2rem; height: 2.2rem; border: 1px solid #ffffff59; border-radius: 50%; background: #3d100e80; color: var(--lt-on-dark); font-size: 1.1rem; }
.hero-copy { position: absolute; left: 1.15rem; right: 1.15rem; bottom: 1.4rem; }
.hero-sub { margin: 0 0 .35rem; color: var(--lt-on-dark-muted); font-size: .82rem; letter-spacing: .18em; }
.hero-copy h1 { margin: 0; font: 700 1.9rem/1.2 "Songti SC", SimSun, serif; text-shadow: 0 2px 12px #1a0505a8; }
.hero-desc { margin: .55rem 0 0; color: var(--lt-on-dark-muted); font-size: .86rem; }

.home-body { padding: 1rem; }
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: .4rem;
  padding: .9rem .4rem;
  border: 1px solid var(--lt-border);
  border-radius: var(--lt-card-radius);
  background: var(--lt-card);
  box-shadow: 0 4px 16px #551c1414;
}
.quick-item { display: grid; justify-items: center; gap: .4rem; padding: .2rem 0; color: var(--lt-sub); text-decoration: none; font-size: .8rem; font-weight: 600; }
.quick-icon { display: grid; place-items: center; width: 2.9rem; height: 2.9rem; border-radius: 50%; background: var(--lt-tint); color: var(--lt-primary); font-size: 1.35rem; }
.quick-item:hover .quick-icon { background: var(--lt-primary); color: var(--lt-on-dark); }

.notice-section { margin-top: 1.35rem; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin: 0 .15rem .75rem; }
.section-head h2 { margin: 0; font-size: 1.12rem; font-weight: 700; color: var(--lt-text); }
.section-more { display: inline-flex; align-items: center; gap: .15rem; color: var(--lt-sub); text-decoration: none; font-size: .8rem; }
.notice-list { list-style: none; margin: 0; padding: 0; display: grid; gap: .75rem; }
.notice-card {
  display: flex;
  align-items: flex-start;
  gap: .7rem;
  width: 100%;
  padding: .8rem .7rem;
  border: 1px solid var(--lt-border);
  border-radius: var(--lt-card-radius);
  background: var(--lt-card);
  text-decoration: none;
  transition: transform .15s, box-shadow .15s;
}
.notice-card:hover { transform: translateY(-2px); box-shadow: 0 8px 20px #551c1424; }
.notice-dot { flex: none; width: .5rem; height: .5rem; margin-top: .5rem; border-radius: 50%; background: var(--lt-primary); }
.notice-info { display: grid; gap: .25rem; flex: 1; min-width: 0; }
.notice-info strong { color: var(--lt-text); font-size: .95rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-info strong em { margin-left: .4rem; padding: .05rem .35rem; border-radius: .5rem; background: var(--lt-primary); color: var(--lt-on-dark); font-size: .65rem; font-style: normal; }
.notice-info small { color: var(--lt-sub); font-size: .78rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-info time { color: var(--lt-sub); font-size: .72rem; }
.notice-card > .van-icon { flex: none; margin-top: .35rem; color: var(--lt-primary); }
.empty { margin: 1.5rem 0; color: var(--lt-sub); text-align: center; font-size: .85rem; }

.primary-nav {
  position: fixed;
  z-index: 5;
  inset: auto 0 0;
  width: min(100%, var(--lt-app-width));
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: .2rem;
  margin-inline: auto;
  padding: .4rem .3rem calc(.4rem + var(--lt-safe-bottom));
  background: var(--lt-nav);
  border-top: 1px solid #a15e45;
  box-shadow: 0 -4px 18px #551c1414;
}
.primary-nav a {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: .16rem;
  min-height: 3.35rem;
  padding: .3rem .1rem;
  border-radius: var(--lt-radius);
  color: var(--lt-on-dark-muted);
  text-align: center;
  text-decoration: none;
  font-size: .76rem;
  font-weight: 650;
}
.primary-nav a.selected { background: var(--lt-active); color: var(--lt-on-dark); box-shadow: inset 0 0 0 1px #c080507d; }
.primary-nav .van-icon { font-size: 1.35rem; }
</style>
