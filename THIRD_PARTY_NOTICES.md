# Third-party notices

R1 从经过 SHA-1, SHA-256 和 SRI SHA-512 验证的官方 npm tarball 生成
`typescript@6.0.3` compiler distribution. 构建从上游发行包原样保留:

- `LICENSE.txt`.
- `ThirdPartyNoticeText.txt`.

生成目录和 APK 校验在上述文件缺失或内容摘要不一致时必须失败. 完整上游文本
位于 APK 的 `assets/typescript/6.0.3/` 目录.

Rhino 声明仍投影公开的 `@sm003/autojs6-dts@2.1.3` (MPL-2.0), tarball 来源及完整
摘要由 `r2-declarations.lock.json` 锁定. Compose 补充声明来自同一维护者的
`SuperMonster003/AutoJs6-TypeScript-Declarations` 本地 4.35.0 配套源码提交
`de51858b59ec88f75c56d94bcf4af600c54ff34f` (MPL-2.0), 不表示该版本已发布到 npm.
完整 Compose API 及按目录生成的 JSX 映射以源码资源随仓库提供, 原作者说明保留.
来源与两个规范化文件的 SHA-256 记录在
`build-logic/convention/src/main/resources/r2-compose/source.json`.

APK 的 `assets/r2-declarations/v2/licenses/rhino/MPL-2.0.txt` 提供许可证正文,
`assets/r2-declarations/v2/notices/rhino-source.txt` 分别列明公开 tarball 与本地补充
内容的来源. 所有实际声明及说明文件都由 distribution manifest 的完整清单和摘要校验.
