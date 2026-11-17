<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from './api'

const route = useRoute()
const router = useRouter()
const form = ref({ username: '', password: '' })
const submitting = ref(false)
const passwordVisible = ref(false)
const error = ref('')
const agreement = ref(null)

async function signIn() {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const result = await request('/edu/student/login', {
      method: 'POST',
      authenticated: false,
      body: form.value
    })
    localStorage.setItem('lt_token', result.token)
    const destination = route.query.redirect
    const validDestination = typeof destination === 'string' &&
      /^\/(course|task|score|achievements|assistant|notifications|profile)(\/|\?|$)/.test(destination)
    await router.replace(validDestination ? destination : '/course')
  } catch (cause) {
    error.value = cause.message
  } finally {
    submitting.value = false
  }
}

</script>

<template>
  <main class="student-login">
    <div class="login-surface">
      <header class="login-hero">
        <div class="login-brand"><span aria-hidden="true">法</span> 法律实训</div>
        <h1 id="student-login-title">学生登录</h1>
      </header>

      <form class="student-form" aria-labelledby="student-login-title" :aria-busy="submitting" @submit.prevent="signIn">
        <div class="form-field">
          <label for="student-account">账号</label>
          <input id="student-account" v-model.trim="form.username" autocomplete="username"
            autocapitalize="none" spellcheck="false" placeholder="输入学号或登录账号" required>
        </div>
        <div class="form-field">
          <label for="student-password">密码</label>
          <div class="password-field">
            <input id="student-password" v-model="form.password" :type="passwordVisible ? 'text' : 'password'"
              autocomplete="current-password" placeholder="输入密码" required>
            <button type="button" class="reveal" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
              @click="passwordVisible = !passwordVisible">
              <van-icon :name="passwordVisible ? 'eye-o' : 'closed-eye'" aria-hidden="true" />
            </button>
          </div>
        </div>
        <p v-if="error" class="login-error" role="alert">{{ error }}</p>
        <button class="enter" type="submit" :disabled="submitting">
          {{ submitting ? '正在登录…' : '登录' }}
        </button>
      </form>
      <footer class="login-footer">
        <button type="button" @click="agreement.showModal()">《用户使用协议》</button>
      </footer>
      <dialog ref="agreement" class="agreement" aria-labelledby="agreement-title">
        <h2 id="agreement-title">用户使用协议</h2>
        <p>法律实训学生端用于查看课程、提交实训任务、查询成绩和管理学习成果。</p>
        <ul>
          <li>请使用本人账号，妥善保管密码。</li>
          <li>请按课程要求提交作业。上传资料前，请删除与实训无关的个人信息，并确认有权使用相关材料。</li>
          <li>课程资料和其他同学的成果请在授权范围内使用。遇到账号或使用问题，可联系任课教师或平台管理员。</li>
        </ul>
        <button type="button" autofocus @click="agreement.close()">关闭</button>
      </dialog>
    </div>
  </main>
</template>

<style scoped>
.student-login {
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  justify-items: center;
  background: var(--lt-bg);
  color: var(--lt-text);
}
.login-surface {
  width: min(100%, var(--lt-app-width));
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(2rem + var(--lt-safe-bottom));
  background-color: #481510;
  background-image: linear-gradient(180deg, #30100ca6, #38100d2e 35%, #481510 98%),
    url('./assets/exhibition-hall.webp');
  background-position: center top, center top;
  background-size: 100% clamp(17rem, 46svh, 30rem), auto clamp(17rem, 46svh, 30rem);
  background-repeat: no-repeat;
}
.login-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: 2.8rem;
  min-height: clamp(11.5rem, 39svh, 24rem);
  padding: calc(1.6rem + var(--lt-safe-top)) 1rem 1.4rem;
  color: var(--lt-on-dark);
}
.login-brand {
  display: flex; align-items: center; gap: .6rem; padding: .35rem .8rem .35rem .35rem;
  border: 1px solid #f5d59d66; border-radius: 999px; background: #35100cbb;
  font-size: .9rem; font-weight: 650; letter-spacing: .08em;
}
.login-brand span {
  display: grid;
  place-items: center;
  width: 2.18rem;
  height: 2.18rem;
  border-radius: 50%;
  background: var(--lt-badge);
  color: var(--lt-primary);
  font: 700 1.25rem "Songti SC", SimSun, serif;
}
.student-form {
  margin-inline: clamp(.85rem, 5vw, 1.65rem);
  padding: 1.35rem clamp(1rem, 5vw, 1.75rem) 1.45rem;
  border: 1px solid #f3d4ad;
  border-radius: 1.4rem;
  background: var(--lt-card);
  box-shadow: 0 1rem 2rem #24070555;
}
.login-hero h1 {
  margin: 0;
  text-shadow: 0 2px 9px #260704;
  color: #fff0d2;
  font: 700 clamp(1.85rem, 7vw, 2.35rem)/1.2 "Songti SC", SimSun, serif;
}
.form-field { margin-bottom: .85rem; }
.form-field label { display: block; margin-bottom: .3rem; color: var(--lt-text); font-size: .85rem; font-weight: 650; }
.form-field input {
  display: block;
  width: 100%;
  min-width: 0;
  height: 3rem;
  padding: 0 .8rem;
  border: 1px solid var(--lt-border);
  border-radius: var(--lt-radius);
  background: var(--lt-field);
  color: inherit;
  font: inherit;
  font-size: 16px;
}
.form-field input::placeholder { color: var(--lt-sub); }
.form-field input:autofill {
  box-shadow: 0 0 0 100px var(--lt-field) inset;
  -webkit-text-fill-color: var(--lt-text);
}
.password-field { position: relative; }
.password-field input { padding-right: 4rem; }
.reveal {
  position: absolute;
  inset: 0 0 0 auto;
  min-width: 3.5rem;
  border: 0;
  border-radius: var(--lt-radius);
  background: transparent;
  color: var(--lt-primary);
  font-size: 1.15rem;
}
.student-form button { cursor: pointer; font-family: inherit; }
.student-form button:disabled { cursor: default; opacity: .6; }
.student-form :is(input, button):focus-visible { outline: 2px solid var(--lt-focus); outline-offset: 2px; }
.login-error { margin: .65rem 0; color: var(--lt-danger); font-size: .85rem; }
.enter {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 3.25rem;
  margin-top: 1.1rem;
  border: 1px solid #deb372;
  border-radius: 999px;
  background: linear-gradient(175deg, #ffe8bc, #eebd7c);
  color: #551b12;
  font-size: 1rem;
  font-weight: 700;
}
.enter:hover:enabled { background: #ffdda0; }
.login-footer { margin-top: auto; padding-top: 1.35rem; text-align: center; }
.login-footer button {
  min-height: 44px; padding: .35rem .8rem; border: 0; background: transparent;
  color: #f4d9b9; cursor: pointer; text-decoration: underline; text-underline-offset: .2em;
}
.agreement {
  width: min(25rem, calc(100% - 2rem)); max-height: calc(100dvh - 2rem); padding: 1.35rem;
  border: 1px solid #f3d4ad; border-radius: 1.2rem; background: var(--lt-card); color: var(--lt-text);
}
.agreement::backdrop { background: #230806b8; }
.agreement h2 { margin: 0; font-size: 1.25rem; }
.agreement ul { padding-inline-start: 1.3rem; }
.agreement li + li { margin-top: .5rem; }
.agreement button {
  width: 100%; min-height: 44px; border: 0; border-radius: .75rem;
  color: var(--lt-on-dark); background: var(--lt-primary); cursor: pointer;
}
@media (min-width: 768px) and (pointer: fine) {
  .student-login { align-items: center; padding: 1.5rem; }
  .login-surface { min-height: 0; overflow: hidden; border-radius: 1.65rem; box-shadow: 0 20px 64px #20060466; }
}
</style>
