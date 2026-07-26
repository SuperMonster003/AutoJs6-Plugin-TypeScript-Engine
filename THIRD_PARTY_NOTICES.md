# Third-party notices

R0 尚未把 TypeScript 可执行发行物打包进 APK. 项目只固定了
`typescript@6.0.3` 的来源和完整性元数据.

从 R1 开始打包 TypeScript 时, 构建必须从上游发行包原样保留:

- `LICENSE.txt`.
- `ThirdPartyNoticeText.txt`.

发布校验在上述文件缺失时必须失败.
