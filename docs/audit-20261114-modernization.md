# 现代化数值与易用性审计（2026-11-14）

基线：`main` a9c10ac。本次审计只读代码，没有改动任何文件，也没有进游戏实测。标 ✔ 的条目已由我对照源码复核；其余来自四份分领域审计，引用的 `文件:行号` 可以直接核对。其他模组的数值来自社区 wiki，**只是近似值**。

## 目标与结论

目标：让本模组适应 1.21.1 NeoForge “数值大乱斗”的整合包环境——Apotheosis 词缀与 Boss、L2Hostility 按等级放大的怪、Cataclysm Boss、Iron's Spells 法强、Mekanism 的百万级 FE。同时修掉 2016 年风格的操作体验。

结论：目前的数值是有意恢复的 1.12.2 原版值。对原版生物和原版装备仍然合理，但在大数值整合包里有五个结构性问题：

1. **伤害不随成长放大**（D-1）：能力等级只决定能学哪些技能。满熟练度最高单发是等离子炮 150、风暴核心 120、超电磁炮 110；面对几千血的整合包 Boss 要打几十发。
2. **多段技能被无敌帧吞掉**（D-2 ✔）：纸钻每 tick 一跳，约九成伤害被吞，CP 照扣。
3. **没有资源类属性**（P-3 ✔）：装备、饰品、词缀无法提升 CP 上限、回复或冷却；`addCpRegenRate` 没有调用方，而且每 tick 会被覆盖。
4. **能源是早期水平**（E-1、E-5、E-6）：与外部 FE 的唯一通道是转换器，只有 400 FE/t，低于 Mekanism 基础线缆的 3200；高级节点只有 80 万 FE。
5. **操作反馈缺失**（UX-1、UX-2 ✔、UX-3、UX-5 ✔）：
   - 默认键位和原版 F/C 冲突，也和 Iron's Spells 的 V/R/左 Alt 冲突；
   - 技能在冷却时按下没有任何提示；
   - 技能提示里没有伤害、冷却等实际数值；
   - 没有 NeoForge 配置界面。

## 方案框架

沿用 TC4 现代化的做法：**双档位**。

- 服务端配置 `balance.profile = modern | legacy`。`legacy` 完全保留现在的数值，`modern` 是新数值。
- 数值类改动通过内置数据包（`academy:modern`）和配置默认值实现。各配置键的默认值改为“跟随档位”，整合包仍可以逐项覆盖。
- 需要新机制的改动（属性、无敌帧处理、伤害模型）写成代码，但效果强度由档位或数据字段控制；`legacy` 下效果为零。
- 易用性修复不分档位，对所有人生效。如果会改变原版观感，就提供客户端开关。
- 保持 `fePerIf = 4`，确保与奇点迭代的 1 IF = 1 EU 对等。整合包要放大能源规模，就调各机器的数值，而不是改换算比。

## 发现

### 战斗伤害（D）

| ID | 级别 | 问题 | 证据 | 建议 |
|---|---|---|---|---|
| D-1 | 高 | 所有技能用固定的 1.12.2 数值；能力等级不放大伤害；熟练度从最低到最高通常只差 1.5–2.5 倍 | `PlasmaCannonEffect.java:118`、`StormCoreEffect.java:129`、`RailgunEffect.java:210` | 按等级放大（M2）+ 新属性 `skill_power`（M3）+ 对 Boss 按最大生命值百分比附加伤害（M4，有上限） |
| D-2 ✔ | 高 | 多段伤害被无敌帧吞掉：纸钻每 tick 一跳（每目标每 tick 扣 100×身高 CP）；风暴核心终结段约只有 2 跳生效；火山弹、电弧被吞一半。只有散射弹、电子导弹、等离子炮会重置无敌帧，但重置后其他攻击者也能趁机打进来 | `PaperDrillEffect.java:123-132`、`StormCoreEffect.java:117-129`；原版标签 `bypasses_cooldown` 中没有本模组的伤害类型 | 按技能、按目标维护命中间隔；纸钻只在命中时扣 CP；收紧 `invulnerableTime=-1` 的重置，不让其他攻击者受益 |
| D-3 | 中 | 无视护甲由伤害类型决定（魔法、溺水、风刃、念力针、巡航弹无视护甲，超电磁炮、等离子炮不无视），不是按设计决定 | `data/minecraft/tags/damage_type/bypasses_armor.json` | 新增调参字段 `armor_pierce`（0–1），通过 `DamageContainer` 按比例减免护甲 |
| D-4 ✔ | 低 | 技能调参只读取 `academy` 命名空间；倍率为无穷大时静默归零 | `SkillTuningData.java:53`、`DynamicSkillRules.java:42` | 允许任意命名空间，按数据包顺序叠加；非有限值时记警告 |
| D-5 | 中（未核实） | Cataclysm 可能对每次受伤设上限（约 20–30），会让单发大伤害失效 | 来自审计代理的记忆，没有核对 | 实装前先查 Cataclysm 配置；如属实，优先发展多段和持续伤害 |
| D-6 ✔ | 中 | 矢量偏移每次受击固定 12–15 CP，减伤 60–90%，面对大数值近乎无敌；矢量反射把 1.2 倍伤害返还给攻击者，还会再乘上反射者自己的 `skill_damage` | `VecDefenseLegacyMath.java:24-44`、`PassiveSkillEventHandler.java:255-290` | 现代档：偏移的消耗随受到的伤害增长；反射伤害不吃 `skill_damage`，或者设上限 |
| D-7 | 中 | 辐射强化对所有来源的伤害最高乘 1.8，包括队友的词缀武器和法术 | `RadiationIntensifyRuntime.java:84-89` | 保留，补文档；对 Boss 可设上限 |
| D-8 | 中 | 生物施法不读取生物自身的攻击力或 L2H 等级；困难难度 ×1.5，对原版装备的玩家常常一击致命；`minCooldownTicks` 10 让电弧可以连发 | 伤害类型的 `scaling` 设置、`MobCastConfig` | 在 `mob_skills` 中增加 `damage_scale` 和 `scale_with: attack_damage`；对玩家的单次伤害设上限（最大生命值的比例） |
| D-9 | 低 | 磁力操纵的冲击伤害固定为 10；电子导弹冷却受旧夹取 bug 影响，固定 700 tick | `MagManipLegacyMath.java:15`、`ElectronMissileEffect.java:241-245` | 现代档修正 |
| D-10 | 低 | 击退没有统一控制 | `AeroProjectileEntity.java:236` | 低优先级 |
| D-11 | 低（未核实） | `academy:ability` 在 `is_player_attack` 标签里，生物施法也带这个标签 | `is_player_attack.json` | 核实后让生物施法改用不带该标签的伤害类型 |

### 资源与成长（P）

| ID | 级别 | 问题 | 证据 | 建议 |
|---|---|---|---|---|
| P-1 | 高 | 任何等级下 CP 从空回满都约 115 秒，并且每次消耗都会重置回复延迟 | `LegacyAbilityRules.java:62-65` | 现代档回满 40–60 秒；新增属性 `cp_regen` |
| P-2 ✔ | 高 | 用量增长是看不见的长期刷量：5 级的 +12000 CP 需要花掉 480 万 CP（约 15 小时不停施放）；而且升级时会清零 | `PlayerAbilityData.java:148-150`、`LegacyAbilityRules.java:9` | 每级约 1–2 小时达到上限；升级不清零（需改代码）；在界面上显示 |
| P-3 ✔ | 高 | 只有 `skill_damage` 和 `skill_resistance` 两个属性；`addCpRegenRate` 没有调用方，即使有人调用，效果也会被每 tick 的重算覆盖 | `AcademyAttributes.java:25-31`、`PlayerAbilityData.java:238,330,771` | 新增属性 `max_cp`、`cp_regen`、`max_overload`、`overload_recovery`、`cooldown_reduction`、`cp_cost`、`proficiency_gain` |
| P-4 | 高 | 防御技能的消耗随受到的伤害线性增长：反射每点伤害 15–20 CP，加上每 tick 11–15 CP 维持费；硬化每点 30–60 CP；光盾固定吸收 15–50 点 | `VecDefenseLegacyMath.java:31-40`、`LightShieldEffect.java:189-191` | 现代档：消耗改成次线性，或按最大 CP 的百分比；光盾改为按比例吸收再加固定值；维持费不重置回复延迟 |
| P-5 | 高 | 超能力者没有任何生命值成长；模组护甲中，共振护甲 15 护甲 / 4 韧性，纸护甲 15 / 16，下界合金是 20 / 12 | `ExtraArmorMaterials.java:20-22` | 现代档按等级提供可选的最大生命值和韧性加成；能量护甲每点生命值耗多少 IF 改为可配置 |
| P-6 | 中 | 真正卡进度的是“前置技能熟练度必须 100%”，例如身体强化要电弧和充能都满、超电磁炮要磁力操纵满、喷射引擎要原子崩坏满。专注升到 5 级约 3–5 小时 | `SkillRegistry.java:231-310` | 现代档：前置降到 0.7–0.8；`threshold_per_skill` 设为 0.4；熟练度增长 ×2（数据包即可实现） |
| P-7 | 中 | 过载锁定是二元的：锁定后约 10–15 秒内所有技能都不能用，而且必须降到 0 才解锁 | `PlayerAbilityData.java:308-312,762` | 现代档：降到 50% 即解锁；过载上限 ×1.5；可选“软过载” |
| P-8 | 中 | 没有全局的冷却或 CP 消耗倍率，整合包要逐个技能写约 60 个 JSON | `DynamicSkillRules.java:12,18-21` | 在 ACConfig 中加 `cooldownMultiplier` 和 `cpCostMultiplier` |
| P-9 | 中 | 熟练度越高，CP 消耗越大（超电磁炮 200→450），与现代的直觉相反 | 各技能效果类 | 现代档：在 `DynamicSkillRules.cp` 统一按熟练度打折 |
| P-10 | 低 | 开发机学习的能耗对现代整合包来说微不足道 | `DevMachineType.java`、`ProgressionData.java:100-107` | 现代档把刺激能耗提高到 3–5 倍 |
| P-11 | 低 | CP 药水上限固定为 2000 | `CpPotionItem.java:27,37` | 改为按最大 CP 的百分比 |

### 能源与机器（E）

| ID | 级别 | 问题 | 证据 | 建议（现代档） |
|---|---|---|---|---|
| E-1 | 高 | FE 转换器只有 100 IF/t（400 FE/t）、缓存 2000 IF，是唯一的对外通道，也是瓶颈 | `MachineConfig.java:169-171` | 带宽 2000 IF/t，缓存 20000 IF |
| E-2 | 高 | 虚数融合器、金属成型机、相位发电机、节点和矩阵都没有 FE 能力，科技模组的线缆接不上 | `AcademyCraft.java:154-173` | 加 FE 视图，并受各自带宽限制 |
| E-3 ✔ | 中 | FE 视图不受带宽限制，而且总是允许抽取：开发机和干扰器可以无限速灌电，也会被管道抽走能量 | `IFEnergyStorage.java:13-30` | 输入受带宽限制；耗电设备只接收不输出（与 E-2 一起改） |
| E-4 | 中 | 矩阵带宽 60·L²（最高 540 IF/t），小于一个高级节点的 900 | `MachineConfig.java:126-131` | 系数 1000；网络缓存 50000 |
| E-5 | 中 | 节点容量 1.5 万 / 5 万 / 20 万 IF，带宽 150 / 300 / 900 | `MachineConfig.java:122-124` | 25 万 / 100 万 / 400 万 IF；带宽 ×10；范围 ×1.5 |
| E-6 | 中 | 发电机：太阳能白天 3 IF/t，风力 7.5–15，相位最高 50；相位液不可再生；猫引擎没有配方 | `MachineConfig.java:133-153`、`PhaseFluid.java:45` | 太阳能 ×5、风力 ×4、相位能量 ×4 |
| E-7 | 低/中 | 开发机缓存写死在代码里；`applySyncRate` 没有调用方 | `DevNormalBlockEntity.java:16-17`、`DevMachineType.java:68` | 移入配置；现代档缓存 ×10 |
| E-8 | 低 | 融合器和成型机的能耗在合理范围，真正的限制是相位液 | `MachineConfig.java:155-167` | 现代档加工时间减半（可选） |
| E-9 | 中 | 干扰器带宽 50 IF/t，作用范围最多只能维持约 22 格 | `AbilityInterferenceRules.java:23` | 带宽 1000 |
| E-10 ✔ | 高（放大数值的前提） | 节点和矩阵菜单的带宽、容量，以及干扰器的能量，都用单个 short 同步，超过 32767 会溢出 | `BaseNodeMenu.java:58`、`MatrixMenu.java:38-39`、`AbilityInterfererMenu.java:30-31` | 先全部改用 `MenuDataWords`，再放大数值 |
| E-11 | 中 | 能量单元、能量单元组、便携开发机和能量护甲的容量与速率都写死在代码里 | `EnergyUnit.java:12-14` 等 | 移入 `[machines.items]`；现代档 ×10 |
| E-12 | 中 | JEI 不显示能耗和时间；没有 Jade 提示；没有比较器输出；节点范围在世界中看不见；“IF”和沉浸工程的 IF 同名 | `client/jei/*`、全局搜索 | 见 UX-9、UX-10；提示里写成“X IF（Y FE）” |
| E-13 | 低 | 机器配方是中期成本，产出却是早期水平 | 配方 JSON | 只放大产出，配方不变 |

### 易用性（UX）

| ID | 级别 | 问题 | 证据 | 建议 | 工作量 |
|---|---|---|---|---|---|
| UX-1 ✔ | 高 | 默认键位与原版 F（副手交换）、C（工具栏），以及 Iron's Spells 的 V/R/左 Alt 冲突 | `KeyInputHandler.java:46-102` | 左右键保留；技能 3、4 改到 Z/X 或 G/B；预设和终端改为未绑定或其他键；只迁移玩家没改过的键 | 小 |
| UX-2 ✔ | 高 | 冷却中按技能没有任何提示；“CP 不足”和“过载过高”共用一句话 | `UseSkillPacket.java:54` | 在动作栏显示原因和剩余秒数，配拒绝音效，限制提示频率 | 小 |
| UX-3 | 高 | 技能提示只显示基础 CP 和过载，没有伤害、冷却、范围，也不随熟练度变化 | `SkillTreeGui.java:839-858` | 新增 `SkillEffect.describeStats`，按当前熟练度计算，按 Shift 显示满熟练度 | 中 |
| UX-4 ✔ | 高 | 技能描述按字符数硬切，英、俄、韩文会从单词中间断开 | `SkillTreeGui.java:843-846` | 改用 `font.split` 按宽度换行 | 小 |
| UX-5 ✔ | 高 | 没有注册 NeoForge 配置界面，设置只能在终端的设置 App 里改，而且只有一部分 | `AcademyCraft.java:112-113` | 注册 `ConfigurationScreen`，并补齐翻译键 | 小 |
| UX-6 | 中高 | 冷却没有秒数；能力关闭时整个提示列隐藏；CP 具体数值要按住 V 才显示 | `AbilityHudOverlay.java:41,113-124`、`CPBarOverlay.java:67,96` | 客户端选项：显示冷却秒数、常驻数值；能力关闭时以暗色显示 | 小 |
| UX-7 | 中高 | HUD 大小固定；充能条位置写死，会压住手持物品名（文档已记录） | `CPBarOverlay.java:27`、`ChargingHudOverlay.java:63-66` | HUD 编辑器支持逐项缩放；充能条可拖动 | 中 |
| UX-8 | 中 | 前 30 分钟的引导很弱：教程偏剧情；JEI 没有说明诱导因子从哪里来；开发机界面要手打 `learn`；教程物品掉在地上，容易丢 | `develop_ability.md`、`SkillTreeGui.java:1017-1021`、`ServerListener.java:83` | 进度链引导；JEI 信息页；“学习”按钮；教程物品优先放进背包（可配置） | 中 |
| UX-9 | 中 | 没有 Jade/WTHIT/TOP 支持 | — | 编译期可选的 Jade 插件：显示能量、主人、SSID、连接状态、进度 | 中 |
| UX-10 | 中 | 机器没有比较器输出，也没有红石控制 | — | 先加比较器输出 | 小 |
| UX-11 | 中 | 设置 App 里的键位只能看不能改 | `SettingsAppGui.java:66-76` | 点击后打开原版按键设置，并标出冲突 | 小 |
| UX-12 | 中 | 预设只能按顺序循环切换 | `KeyInputHandler.java:309-315` | 按住 C 打开轮盘选择 | 中 |
| UX-13 | 中 | 状态只靠颜色和闪烁表达；140 ms 的闪烁没有关闭开关 | `AbilityHudOverlay.java:91` | “减少闪烁”选项；过载锁定加形状或文字提示 | 小 |
| UX-14 | 低中 | 教程只有英文和简中；繁中、日、韩、俄都回退到英文 | `ACTutorial.java:64-77` | 繁中回退到简中；补译 | 小/中 |
| UX-15 | 低中 | 没有普通玩家可用的指令 | README | `/academy info`、`/academy hud reset`（权限 0） | 小 |
| UX-16 | 低 | 没有 Curios 和原生 EMI 支持（EMI 能读取 JEI 插件，但自定义转移未验证） | — | 暂不处理 | — |
| UX-17 | 低 | 穿透传送用滚轮调距离，但没有任何提示 | `KeyInputHandler.java:320-331` | 在充能提示里显示“滚轮：距离 X 米” | 小 |

## 分期建议

| 期 | 内容 | 依赖 |
|---|---|---|
| MZ0 框架 | `balance.profile` 开关；配置默认值跟随档位；内置 `academy:modern` 数据包；调参字段扩展（`armor_pierce`、`damage_per_level`、`max_health_fraction`、`boss_multiplier`、`pulse_bypass_iframes`）；全局倍率（P-8）；菜单同步改造（E-10）；任意命名空间（D-4） | — |
| MZ1 易用性快修 | UX-1、2、4、5、6、11、13、15、17 | 无，可以和 MZ0 并行 |
| MZ2 资源与成长 | 新属性（P-3）；回复、用量增长、过载解锁（P-1、P-2、P-7）；熟练度折扣（P-9）；药水（P-11）；进度表与前置（P-6） | MZ0 |
| MZ3 战斗 | 无敌帧（D-2，不分档位都修）；等级放大与 `skill_power`；Boss 百分比伤害；按比例破甲（D-3）；防御技能消耗（P-4、D-6）；生物施法放大（D-8）；生存成长（P-5）；D-9 | MZ0、MZ2（属性） |
| MZ4 能源 | FE 能力补齐和带宽限制（E-2、E-3）；数值放大（E-1、4、5、6、9）；物品与开发机配置化（E-7、E-11） | MZ0、E-10 |
| MZ5 信息与引导 | 技能实际数值（UX-3）；Jade（UX-9、E-12）；JEI 显示能耗与时间；比较器（UX-10）；新手引导（UX-8）；HUD 缩放（UX-7）；预设轮盘（UX-12）；教程翻译（UX-14） | MZ2、MZ3（要显示的是新数值） |

D-2（无敌帧）和 E-3（FE 视图不受带宽限制）属于 bug，不分档位都修。

## 已定决定（2026-11-14）

用户选择全部按推荐执行：

1. 默认档位为 `modern`，`legacy` 可选。
2. 伤害放大：按等级放大，加 `skill_power` 属性、对 Boss 的有上限百分比伤害和无敌帧处理；读取外部属性（Iron's 法强等）的桥接做成可选。
3. 默认键位：技能 3、4、预设和终端改到不冲突的键，只迁移玩家没改过的键。
4. 能源：保持 `fePerIf = 4`，靠放大各机器的数值提升规模。

## 进度

- MZ0（本分支 `feature/modern-mz0`）：`balance.profile` 开关、`academy_balance/<profile>/skills/` 档位层、跨命名空间叠加（D-4）、全局冷却和 CP 倍率（P-8）、节点/矩阵/干扰器菜单的 32 位同步（E-10）。新调参字段与各配置键的“跟随档位”默认值，随各自用到它们的阶段（MZ2–MZ4）加入，避免出现没有消费者的字段。

## 不确定之处

- 其他模组的数值（Cataclysm、L2Hostility、Apotheosis、Iron's、Mekanism、Powah、Thermal）来自社区 wiki 或代理的记忆，没有针对 1.21.1 逐一核实。D-5、D-11 需要先核实再动手。
- 游戏时长估算来自公式推算，没有实测。
- 所有发现都没有进游戏验证。
