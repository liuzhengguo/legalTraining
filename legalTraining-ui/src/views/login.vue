<template>
  <main class="access-page">
    <header class="access-brand">
      <span class="access-mark" aria-hidden="true">法</span>
      <span>武汉理工大学法学院</span>
    </header>
    <div class="access-panel">
      <section class="access-art">
        <h1>法律实训数字平台</h1>
      </section>
      <section class="access-content">
        <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form" @submit.native.prevent="handleLogin">
          <div class="login-heading">
            <h2>管理后台登录</h2>
            <p>请使用平台账号登录</p>
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
      </section>
    </div>
  </main>
</template>

<script>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from '@/utils/jsencrypt'

export default {
  name: "Login",
  data() {
    return {
      loadingCode: false,
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
.login-form { width: 100%; }
.login-heading { margin-bottom: 28px; }
.access-content .login-heading p { margin-bottom: 0; }
.login-heading::before {
  display: block;
  width: 34px;
  height: 3px;
  margin-bottom: 17px;
  border-radius: 4px;
  background: #b78652;
  content: "";
}
.login-form label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 650; }
.login-form ::v-deep .el-input__inner { height: 48px; border-radius: 8px; border-color: #dccfc3; background: #fffdfb; color: inherit; }
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
@media (max-width: 760px) {
  .login-heading { margin-bottom: 24px; }
  .login-heading::before { margin-bottom: 12px; }
}
</style>
