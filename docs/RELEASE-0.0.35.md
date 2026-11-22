# 0.0.35-scex-modern 正式版

发布日期：2026-11-22。SCEX 非官方维护版本，适用于 Minecraft 1.21.1、NeoForge 21.1（构建固定 21.1.248）、Java 21。协议保持 `academy-1.21.1-payload-v25-data-v4`，能力存档保持 v4。

## 主要内容

### 1. 输入与技能事件链大修（[`7459313`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/7459313)）
- 根除蓄力槽位永久死锁：移除服务端蓄力拒绝回包的 1 次/秒限流饥饿，新增客户端 tombstone 过期自愈与重按/开屏驱逐机制，迟到 ACK 仍由 `ChargingCancelPacket` 兜底。
- 修复 `Flashing` 启动被拒后客户端卡死在激活态拦截 WASD 的问题，状态完全回归服务端权威。
- 补齐鼠标中键（Pick Block）拦截与动态键位覆写，非切换类主动蓄力期间默认锁定滚轮切栏，开 GUI 保留已开启的切换类持续技能（风翼、矢量偏转/反射、纸钻）。
- 统一键盘与鼠标中键/侧键辅助输入路由，补齐单 tick 快按与松开再按边缘检测，三类能力同步包落地联动清理失效本地状态，修复穿透传送 HUD 文字重叠与 `ThunderClap`/`StormWing` 会话清理。

### 2. 全模块性能大修（[`c08eb3d`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/c08eb3d)）
- `RadiationIntensifyRuntime` 仅追踪活跃标记实体，消除全服实体每 tick `getPersistentData()` 分配与 NBT 查询。
- `PlayerAbilityData` 与 `AbilitySyncState` 引入零分配 profile/runtime 指纹跳过静止玩家 NBT 序列化，移除每 tick 冗余 `player.setData(...)`。
- `AbilityInterferenceService` 增加距离平方预裁剪，关闭态干扰器停止每 tick 重复移除。
- `MineDetectEffect` 利用 `LevelChunkSection.maybeHas` O(1) 跳过无矿区段；`WirelessNet`/`NodeConn`/`WiWorldData` 全面以 `ArrayList` 替代 `LinkedList` 并消除双倍 `getBlockEntity`；猫引擎与客户端风翼音效扫描降频。
- `MineDetectBatchRenderer` 消除每帧数万次 `float...` 变参数组分配；矩阵与风力扇叶缓存 `BakedQuad`；虚空液收紧视距并预建 HUD/表现层纹理常量。

### 3. 1.21.1 NeoForge 现代化大修（[`c4f2fe6`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/c4f2fe6)）
- 移除生产代码与测试类中的 `@EventBusSubscriber(bus = Bus.MOD)` 待删除参数，补齐 `WirelessSystem` 的 `modid`；迁移 JEI `MetalFormerJeiCategory` 至 `getTooltip(ITooltipBuilder, ...)`。
- `AcademyDataComponents` 新增原生 `ACTIVE` / `LAST_USE` 组件，`ExtraItemData` 全面组件化并保留旧 `CUSTOM_DATA` 读取回退与写时自动迁移。
- 渲染器迁移至 NeoForge `ModelData` / `InventoryMenu.BLOCK_ATLAS` 重载；替换 `hasChunkAt`/`hasChunksAt`、`hasCorrectToolForDrops`、`builtInRegistryHolder` 与废弃配方别名 `IMAG_FUSING`。

### 4. 数据包体系三阶段大修（[`6833df4`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/6833df4)、[`5ab08b6`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/5ab08b6)、[`9298b0d`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/9298b0d)）
- 移除启动期死配方初始化 `MFIFRecipes.init()`；新增 `#academy:railgun_ammo`、`#academy:mine_detectable`、`#academy:mine_detect_excluded`、`#academy:metal`（实体）四个原生标签；为模组矿石补齐 3 个 `minecraft:blasting` 高炉配方；加固 `SyncedJsonData` 畸形快照容错。
- 清理混入主数据包目录的 `academy_charging` / `academy_recheck_compat` 测试命名空间；11 个 ExtraAcC 扩展配方改用 `c:` 通用材料标签。
- 新增原生 NeoForge 流体 DataMap `academy:phase_gen_fuels`，接入相位发电机支持整合包自定义发电燃料流体。

### 5. 仓库历史整合（[`bc2fa6d`](https://github.com/rianfalltwilight-lab/scex-dev-academycraft/commit/bc2fa6d)）
- 将早期私有仓 `scex-academycraft`（`v0.0.15` ～ `v0.0.33-scex-combat-hud`）提交历史与 12 个版本标签完整并入本公开仓 `main`（文件树以公开仓为准）。

## 安装与升级

客户端和服务端同时替换主 JAR `AcademyCraft-neoforge-1.21.1-0.0.35-scex-modern.jar`，移除旧版 AcademyCraft，避免重复模组 ID。升级旧存档前请先备份世界和玩家数据。不要安装带 `audit-tests` 后缀的测试探针包。

## 验证记录

- 构建（`check build auditTestJar`）与全部 **399 项 JUnit** 测试通过（含静态契约与状态机/Codec 行为单测，不等同于完整实机玩法实测）。
- GameTest 与客户端实机验收未在本轮发布前重新执行，标注为 `NOT VERIFIED for this build`。

已构建主 JAR SHA-256：`8fad873c9b39fa45866360023a974bf9d2c9bd9a858e992b36334e0a959e46ad`。  
测试探针 JAR（`audit-tests`）SHA-256：`9fb48381b9dd266ed40a1e54e8e77b444b1d21b96eb13746825afb5233aa9b10`（仅供测试，勿安装至正式客户端/服务端）。

后续仓库说明整理不改变已构建 JAR；从新提交重新构建的哈希可能不同。
