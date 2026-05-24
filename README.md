# 车耗记

车耗记是一个离线优先的 Android 车辆能耗与费用记录应用，支持油车和电车分别记录、统计和可视化。项目目前以单一 `main` 分支维护。

## 功能

- 多车辆管理，支持油车/电车类型区分
- 加油、充电、保养、其他费用记录的新增、编辑和删除
- 油耗、电耗、单价、月度费用、站点费用占比图表
- 图表数据摘要与最近明细
- 首页概览：平均能耗、最近能耗、总里程、本月能源费用、真实每公里成本
- 本地提醒
- JSON 备份导出与导入
- CSV 表格导出
- 本地 SQLite 存储，无需登录

## 广告配置

项目保留了友盟 U-AppWin 广告接入代码和 SDK 依赖，但不会在仓库中提交真实 AppKey、广告位 ID 或渠道值。

默认配置位于：

```text
app/src/main/java/com/codex/fuellog/AdConfig.java
```

当前值为空，因此广告模块不会发起请求。需要测试广告时，在本地临时填入自己的配置后再构建，不要把真实配置提交到仓库。

## 目录

```text
app/                  Android 应用源码与资源
app/libs/             第三方 AAR 依赖
dist/                 本地构建输出
build-apk.sh          构建调试签名 APK
build-release.sh      构建正式签名 APK
build-release-aab.sh  构建 AAB
```

## 构建

环境要求：

- Android SDK 35 build-tools
- JDK 17

构建 APK：

```bash
./build-apk.sh
```

构建正式签名 APK：

```bash
./build-release.sh
```

输出文件：

```text
dist/车耗记.apk
dist/车耗记-release.apk
```

## 数据说明

应用数据默认保存在本地 SQLite 数据库中。JSON 备份可用于迁移或恢复，CSV 导出用于查看加油/充电记录表格。

## 注意

- 仓库不再包含应用商店上架资料、隐私政策草稿、合规说明或宣传素材。
- `release/keystore/`、SDK 压缩包和本地系统文件已加入 `.gitignore`，不要提交。
- 若重新启用广告，请确认后台广告位配置完整，并使用本地配置方式管理真实密钥。
