# 车耗记

一个离线优先的 Android 油耗、电耗、充电、加油、保养和费用记录 App。

## 已实现

- 多车辆管理
- 油车/电车独立记录与统计
- 加油记录增删改查
- 充电记录增删改查
- 加满/未加满/漏记区间油耗计算
- 充满/未充满/漏记区间电耗计算
- 首页平均油耗/电耗、最近油耗/电耗、总里程、本月油费/电费、真实每公里成本
- 保养记录和其他费用记录
- 油耗/电耗趋势、油价/电价趋势、月度油费/电费、月度总成本、站点费用占比图表
- 本地提醒
- JSON 备份导出
- CSV 加油记录导出
- 本地 SQLite 存储，无需登录和网络

## 构建

当前 APK 已输出到：

```text
dist/车耗记.apk
```

重新构建：

```bash
./build-apk.sh
```

需要本机存在 Android SDK 35 build-tools 和 JDK 17。

## 上架准备

上架资料在 `release/` 目录：

- `privacy-policy.html`：隐私政策
- `store-listing.md`：应用市场文案
- `compliance.md`：权限和数据合规说明
- `release-checklist.md`：审核前检查清单
- `assets/`：图标和宣传图

构建正式签名 APK：

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
