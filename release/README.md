# 车耗记上架资料

本目录放上架应用商店需要的资料草稿、隐私政策和素材。

## 文件说明

- `privacy-policy.html`：隐私政策页面，可部署到网站后填写到各应用市场。
- `store-listing.md`：应用市场标题、简介、详细描述、关键词。
- `release-checklist.md`：上架前检查清单。
- `compliance.md`：权限、数据、本地存储和广告合规说明。
- `assets/`：应用图标和宣传图素材。
- `keystore/`：本地生成的正式签名文件目录，已加入 `.gitignore`，不要上传。

## 构建正式 APK

```bash
./build-release.sh
```

输出：

```text
dist/车耗记-release.apk
```

构建 Google Play AAB：

```bash
./build-release-aab.sh
```

输出：

```text
dist/车耗记-release.aab
```

首次运行会在 `release/keystore/` 生成签名文件和密码配置。这个目录必须妥善备份，后续更新同一个应用必须使用同一套签名。
