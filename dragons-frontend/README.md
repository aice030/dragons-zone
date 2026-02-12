# dragons-frontend

Vue 3 + Vite 前端项目，与 dragons-core-server 后端配合使用。

## 功能说明

### 登录 / 注册与导航栏用户区

- **导航栏右侧**（在首页顶部导航栏）：
  - **未登录**：显示「登录」「注册」按钮，点击后弹出对应弹窗。
  - **已登录**：显示用户**昵称（nickName）**和「个人管理」按钮；点击「个人管理」进入个人管理页。
- **登录**：弹窗内输入登录名、密码，调用后端 `POST /api/user/login`，成功后将 JWT 与用户信息写入 Pinia 与 localStorage，后续请求会自动携带 Token。
- **注册**：弹窗内输入登录名、密码、昵称、手机号，调用 `POST /api/user/register`；注册成功后关闭注册弹窗并自动打开登录弹窗，用户可立即登录。
- **个人管理页**（`/profile`）：展示当前登录用户的昵称、登录名，提供「返回首页」「退出登录」；未登录访问会提示先登录。

登录状态在刷新页面后会从 localStorage 恢复（与 `api/media.js` 中使用的 `token`、`userInfo` 一致）。

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```
