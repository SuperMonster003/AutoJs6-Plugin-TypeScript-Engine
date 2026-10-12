<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p><img src="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="TypeScript Engine" width="128" /></p>
  <p>為 Rhino 和 Node.js 編譯 TypeScript 專案, 提供語意診斷與 Source Map.</p>
  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/releases"><img alt="Release" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine?label=Release" /></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/issues"><img alt="Issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine?label=Issues" /></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/LICENSE"><img alt="License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine?label=License" /></a>
  </p>
</div>

**繁體中文 (台灣)** | [English](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-en.md) | [العربية](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ar.md) | [Español](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-es.md) | [Français](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-fr.md) | [日本語](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ja.md) | [한국어](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ko.md) | [Русский](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-ru.md) | [简体中文](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-zh-Hans.md) | [繁體中文 (香港)](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-zh-Hant-HK.md) | [繁體中文 (台灣)](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/.readme/README-zh-Hant-TW.md)

安裝官方發行頁的簽章 APK 和相容的 AutoJs6, 在外掛中心啟用 TypeScript Engine, 再執行 .ts 或 .tsx 專案. Rhino 無需 Node.js Runtime; Node.js 專案需要安裝對應執行階段外掛.

APK: `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` / `universal`

```typescript
const message: string = 'Hello, TypeScript';
console.log(message);
```

需要 Android 7 (API 24) 及以上, 同時需要 Chrome/WebView 119 及以上. 內建 TypeScript 6.0.3. 不支援 native npm addon, 套件安裝生命週期指令碼和 emitDecoratorMetadata. 公開分發隨相容宿主的發佈安排; 歷史驗收記錄不代表後續每個 APK 已通過驗收.

[CHANGELOG](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md) | [Development](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/docs/DEVELOPMENT.md) | [LICENSE](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/LICENSE) | [Third-party notices](https://github.com/SuperMonster003/AutoJs6-Plugin-TypeScript-Engine/blob/master/THIRD_PARTY_NOTICES.md)
