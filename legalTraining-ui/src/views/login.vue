<template>
  <main class="login-page">
    <section class="login-art" aria-label="法律实训数字平台">
      <div class="login-brand"><span class="brand-mark" aria-hidden="true">法</span> 法律实训数字平台</div>
      <div class="login-art-title">
        <h1>法律实训<br>数字平台</h1>
      </div>
    </section>
    <section class="login-content">
      <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form" @submit.native.prevent="handleLogin">
        <div class="login-heading">
          <h2>登录管理后台</h2>
          <p>使用平台账号进入工作台</p>
        </div>
        <label for="login-account">账号</label>
        <el-form-item prop="username">
          <el-input id="login-account" v-model="loginForm.username" autocomplete="username" placeholder="请输入账号" prefix-icon="el-icon-user" />
        </el-form-item>
        <label for="login-password">密码</label>
        <el-form-item prop="password">
          <el-input id="login-password" v-model="loginForm.password" type="password" autocomplete="current-password"
            placeholder="请输入密码" prefix-icon="el-icon-lock" show-password />
        </el-form-item>
        <template v-if="captchaEnabled">
          <label for="login-code">验证码</label>
          <el-form-item prop="code">
            <div class="login-verification">
              <el-input id="login-code" v-model="loginForm.code" autocomplete="off" placeholder="请输入验证码" />
              <button class="captcha-button" type="button" aria-label="刷新验证码" :disabled="loadingCode" @click="getCode">
                <img v-if="codeUrl" :src="codeUrl" alt="登录验证码">
                <span v-else>{{ loadingCode ? '加载中' : '重新加载' }}</span>
              </button>
            </div>
          </el-form-item>
        </template>
        <div class="login-options">
          <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
          <router-link v-if="register" to="/register">立即注册</router-link>
        </div>
        <el-button class="login-submit" :loading="loading" :disabled="captchaEnabled && !codeUrl" native-type="submit" type="primary">
          {{ loading ? '登录中…' : '登录' }} <i v-if="!loading" class="el-icon-arrow-right" aria-hidden="true" />
        </el-button>
      </el-form>
      <footer v-if="footerContent" class="login-footer">{{ footerContent }}</footer>
    </section>
  </main>
</template>

<script>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from '@/utils/jsencrypt'
import defaultSettings from '@/settings'

export default {
  name: "Login",
  data() {
    return {
      loadingCode: false,
      footerContent: defaultSettings.footerContent,
      codeUrl: "",
      loginForm: {
        username: "",
        password: "",
        rememberMe: false,
        code: "",
        uuid: ""
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" }
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }]
      },
      loading: false,
      // 验证码开关
      captchaEnabled: true,
      // 注册开关
      register: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  created() {
    this.getCookie()
    this.getCode()
  },
  methods: {
    getCode() {
      if (this.loadingCode) return
      this.loadingCode = true
      this.codeUrl = ""
      this.loginForm.code = ""
      return getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
        if (this.captchaEnabled) {
          this.codeUrl = "data:image/gif;base64," + res.img
          this.loginForm.uuid = res.uuid
        }
      }).catch(() => {}).finally(() => {
        this.loadingCode = false
      })
    },
    getCookie() {
      const username = Cookies.get("username")
      const password = Cookies.get("password")
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        ...this.loginForm,
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
      }
    },
    handleLogin() {
      if (this.loading || (this.captchaEnabled && !this.codeUrl)) return
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 })
            Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 })
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 })
          } else {
            Cookies.remove("username")
            Cookies.remove("password")
            Cookies.remove('rememberMe')
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{})
          }).catch(() => {
            this.loading = false
            if (this.captchaEnabled) {
              this.getCode()
            }
          })
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(0, 1fr);
  min-height: 100vh;
  color: var(--lt-login-text);
  background: var(--lt-login-background);
}
.login-art {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100%;
  padding: clamp(28px, 5vw, 76px);
  color: #fff;
  background: var(--lt-login-art);
}
.login-brand { display: flex; align-items: center; gap: 12px; font-size: 16px; font-weight: 650; }
.brand-mark { display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid #e9d4b8; border-radius: 9px; color: #f4d9b4; font-family: "Songti SC", SimSun, serif; font-size: 22px; }
.login-art .login-brand { font-size: 0; gap: 0; }
.login-art-title h1 { margin: 15px 0 0; font: 700 clamp(38px, 5.4vw, 88px)/1.16 "Songti SC", "Noto Serif CJK SC", SimSun, serif; }
.login-content { position: relative; display: grid; place-items: center; padding: 58px 32px 82px; }
.login-form { width: min(100%, 392px); }
.login-heading { margin-bottom: 35px; }
.login-heading h2 { margin: 0; font-size: clamp(27px, 3vw, 38px); font-weight: 750; letter-spacing: -.025em; }
.login-heading p { margin: 10px 0 0; color: #76574a; font-size: 14px; }
.login-form label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 650; }
.login-form ::v-deep .el-input__inner { height: 48px; border-radius: 8px; border-color: #e1c6ad; background: #fff; color: inherit; }
.login-form ::v-deep .el-input__inner:focus { border-color: #963b2e; }
.login-form ::v-deep .el-form-item { margin-bottom: 22px; }
.login-verification { display: grid; grid-template-columns: minmax(0, 1fr) 112px; gap: 10px; align-items: center; }
.captcha-button { display: grid; place-items: center; height: 48px; padding: 3px; border: 1px solid #e1c6ad; border-radius: 8px; background: #fff; color: #963b2e; cursor: pointer; }
.captcha-button img { display: block; max-width: 100%; height: auto; }
.login-options { display: flex; justify-content: space-between; margin: 4px 0 22px; color: #963b2e; }
.login-options ::v-deep .el-checkbox { margin: 0; color: #76574a; }
.login-submit.el-button--primary { width: 100%; min-height: 48px; border-color: #842d22; border-radius: 8px; background: #842d22; font-size: 15px; font-weight: 650; }
.login-submit.el-button--primary:hover:not(.is-disabled) { background: #963b2e; }
.login-submit.is-disabled { opacity: .65; }
.login-submit i { margin-left: 4px; }
.captcha-button:focus-visible, .login-submit:focus-visible { outline: 2px solid #963b2e; outline-offset: 3px; }
.login-footer { position: absolute; bottom: 23px; left: 16px; right: 16px; text-align: center; font-size: 12px; color: #76574a; }
@media (max-width: 740px) {
  .login-page { grid-template-columns: 1fr; grid-template-rows: auto 1fr; }
  .login-art { min-height: 188px; padding: 20px 24px; background-position: center 50%; }
  .login-art .login-brand { font-size: 16px; gap: 12px; }
  .login-art-title h1 { display: none; }
  .login-content { padding: 32px 22px 68px; }
  .login-heading { margin-bottom: 27px; }
}
</style>
