# AcademyCraft + ExtraAcademy 0.0.33 原生战斗与 HUD 图层

基线：`v0.0.32-scex-automation-ui` / `3423b53e0e65b47f7d47bdc30b6ec71d4cb63995`。
版本：`0.0.33-scex-combat-hud`，Minecraft 1.21.1 / NeoForge 21.1 / Java 21。

## 技能伤害来源

技能通过显式 `SkillDamageSource` 工厂提供伤害来源。新增 `academy:ability`、`ability_magic`、`ability_thrown`、`ability_drowning` 四类数据驱动伤害类型，分别保留原玩家攻击、魔法、投掷和溺水的原生属性与标签。原有 `psycho_needle`、`air_blade`、`cruise_bomb` 注册 ID 及穿甲投射物分类继续保留；等离子炮仍使用原生 `minecraft:player_explosion` 分类。

技能来源不借用施法者手持物品的武器属性，修复手持“突破”附魔重锤时改变技能穿甲伤害的问题。死亡消息显示受害者及施法者，不引用无关的手持武器名称。直接实体、施法者和伤害位置继续按实际技能来源提供，护甲、盾牌、抗性、保护和吸收沿用原生伤害流程。

空气分离对其他生物的溺水伤害现在保留施法者，使原生 PVP 开关、队伍友伤和击杀归属能够识别该攻击；自身缺氧继续使用无敌对施法者的来源。气流被动按原生 `is_drowning` 标签识别溺水，兼容新增来源。

本轮保持既有技能数值、CP/过载结算时点和公开伤害事件中的来源对象身份。普通近战、原版投射物及非能力伤害保持原有入口；没有为所有技能新增武器命中或荆棘触发钩子。

2026-10-25 联动补修：将 `academy:ability_magic` 追加到公共伤害标签 `c:is_magic`，恢复空间撕裂和威慑传送受 CoFH/Thermal 魔法抗性拦截的行为。使用 `replace: false` 保留其他模组的标签条目，不增加 CoFH 硬依赖或 Mixin。标签作为静态资源维护于 `src/main/resources/data/c/tags/damage_type/is_magic.json`；`runData` 仅向 `src/generated/resources` 生成现有方块标签，不会覆盖该文件。此次仅构建与数据契约核验，未重跑游戏场景。

## 原生 HUD 图层

八个既有 HUD 使用 `RegisterGuiLayersEvent` 注册独立具名图层，移除原来依附全局或快捷栏绘制事件的订阅：

| 图层 | 顺序 |
|---|---|
| `academy:vector_wave` | 原版相机覆盖层之前 |
| `academy:screen_mask` | 原版快捷栏之后 |
| `academy:cp_bar` | 屏幕遮罩之后 |
| `academy:skill_hints` | CP 条之后 |
| `academy:media_player` | 技能提示之后 |
| `academy:notifications` | 媒体 HUD 之后 |
| `academy:charging` | 原版保存提示之后 |
| `academy:terminal_install` | 蓄力 HUD 之后 |

其他客户端联动可按图层 ID 排序或取消单层。补齐通知、媒体和矢量波纹的 F1 隐藏行为；保留原有美术、配置开关及界面开闭规则。通知与 CP 条或数值相交时，若屏幕空间足够，整组通知移到 CP 区域之外；不会逐张上移而互相叠压。上下空间均不足的窄窗口保留既有通知位置。蓄力状态仍由 tick 更新，隐藏期间的时效处理和退出世界时的会话清理继续执行。

已知布局边界：创造模式刚选中物品时，原版物品名会与既有蓄力提示短暂叠字；本轮 scale 2 首帧截图记录了这一现象，相关坐标未在本轮更改。通知避让也不保证上下空间不足的窄窗口无重叠。

## 验证与交付

新增服务端测试通过原生 `Player.attack`、投射物的原生 tick/碰撞入口、真实技能入口及延时真空效果验证重锤与附魔、箭、三叉戟、风弹、护盾和伤害结算。客户端探针经真实图层分派核对顺序、取消、F1、配置、缩放、界面切换及退出清理，并输出截图供逐张审阅。重锤的下落状态由夹具设置，投射物由探针显式执行原生 tick；不将它们称为真实玩家按键或自然飞行时序。程序驱动输入和受控场景不代表人工操作体验或所有第三方模组组合已验证。

回归期间修正了测试环境自身的三个输入条件：风爆场景清除从旁观模式继承的飞行标志；冷区传送等待期间每轮暂停 50 ms，为加速 GameTest 补足正常 tick 的墙钟预算，生产 200 tick 超时不变；影子障碍场景固定实体随机种子，追逐夹具整体移入模板强制区块内部。原来的 60/75 tick、伤害、距离、资源和超时断言保留。客户端探针也补齐了原版退出流程中的连接断开顺序，隔离启动选项显式关闭失焦自动暂停；这些不改变普通游戏的暂停配置。前期失败结果与诊断逐轮保存在本轮证据中；这些调整不代表重写或修复所有生产寻路行为。

342 项 JUnit 检查、Vicerach 上的 487 项 GameTest，以及 Monarch 上的 19 项客户端检查均通过。客户端覆盖 GUI scale 2/3 各九个场景及保存退出清理，输出 18 张截图；进程正常以 0 退出，未强制终止。Vicerach 客户端启动前可用内存低于既定 6 GiB 门槛，客户端转到通过容量与资源检查的 Monarch 隔离副本。服务端通过后仅调整客户端通知布局和客户端探针；交付核验按精确 JAR 条目清单证明服务端公共代码与已通过的候选一致。

最终执行数、运行结果、截图和源码／JAR 身份以 `evidence/combat-hud-0.0.33/final-validation.json` 为准。构建锁定 NeoForge 21.1.248；运行范围保持 `[21.1,21.2)`，其他小版本未逐一实测。协议保持 `academy-1.21.1-payload-v20-data-v4`，能力存档保持 v4；客户端与服务端安装同一发行版本。

私密 GitHub Release 交付主 JAR、对应源码 ZIP 和 `SHA256SUMS.txt`。测试探针独立打包，不进入发行主 JAR；本轮发布范围为私密仓库归档。

API 核对依据为 [NeoForge 1.21.1 Damage Types](https://docs.neoforged.net/docs/1.21.1/resources/server/damagetypes/) 及本次解析的 Minecraft / NeoForge 21.1.248 sources JAR。
