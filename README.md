<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p><img src="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="TypeScript Engine" width="128" /></p>
  <p>为 Rhino 和 Node.js 编译 TypeScript 项目, 提供语义诊断与 Source Map.</p>
  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/releases"><img alt="Release" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine?label=Release" /></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/issues"><img alt="Issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine?label=Issues" /></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/LICENSE"><img alt="License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine?label=License" /></a>
  </p>
</div>

**简体中文** | [English](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-en.md) | [العربية](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ar.md) | [Español](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-es.md) | [Français](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-fr.md) | [日本語](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ja.md) | [한국어](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ko.md) | [Русский](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ru.md) | [简体中文](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-zh-Hans.md) | [繁體中文 (香港)](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-zh-Hant-HK.md) | [繁體中文 (台灣)](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-zh-Hant-TW.md)

安装官方发行页的签名 APK 和兼容的 AutoJs6, 在插件中心激活或启用 TypeScript Engine, 再运行 .ts 或 .tsx 项目. Rhino 无需 Node.js Runtime; Node.js 项目需要安装对应运行时插件.

APK: `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` / `universal`

```typescript
const message: string = 'Hello, TypeScript';
console.log(message);
```

需要 Android 7 (API 24) 及以上, 同时需要 Chrome/WebView 119 及以上. 内置 TypeScript 6.0.3. 不支持 native npm addon, 包安装生命周期脚本和 emitDecoratorMetadata. 公开分发随兼容宿主的发布安排; 历史验收记录不代表后续每个 APK 已通过验收.

[CHANGELOG](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md) | [Development](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/docs/DEVELOPMENT.md) | [LICENSE](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/LICENSE) | [Third-party notices](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/THIRD_PARTY_NOTICES.md)
