# 合规说明

## 数据处理

- 车辆、里程、加油、充电、保养、费用数据保存在本地 SQLite。
- 不需要注册登录。
- 当前版本不上传云端。
- 导入导出由用户主动选择文件完成。

## 权限

- `POST_NOTIFICATIONS`：用于本地提醒。
- `SCHEDULE_EXACT_ALARM`：用于按日期触发提醒。
- `INTERNET`：用于友盟 U-AppWin 广告请求和广告素材加载。
- `ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`：用于广告 SDK 判断网络状态，避免无网时反复请求。
- `REQUEST_INSTALL_PACKAGES`：由广告 SDK 声明，用于下载类广告的安装链路；如不上下载类广告，可在正式渠道包中评估移除。
- `com.google.android.gms.permission.AD_ID`、`freemme.permission.msa`：由友盟基础 SDK 声明，用于广告标识获取、归因和反作弊。

## 第三方 SDK

当前版本集成友盟 U-AppWin 广告 SDK：

- SDK 名称：友盟 U-AppWin / Umeng Union
- 用途：开屏广告、首页信息流广告、浮窗广告展示及广告效果统计
- 数据处理：广告 SDK 可能读取设备标识、网络状态、应用信息等用于广告请求、反作弊和效果归因，具体以友盟官方合规说明为准。

当前版本未集成推送 SDK、地图 SDK、登录 SDK。

## 上架注意

- 广告正式上线前，需要在 `AdConfig.java` 填入友盟后台 AppKey 和广告位 ID。
- 国内应用市场通常要求隐私政策列明第三方 SDK 名称、用途、收集字段和官网链接。
- 如果接入云同步，必须增加账号体系、数据上传说明、注销和删除数据能力。
- 如果上架国内应用市场，建议准备软件著作权和隐私政策 URL。
