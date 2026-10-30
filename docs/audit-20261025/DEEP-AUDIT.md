# AcademyCraft + ExtraAcademy 深度自检台账

**状态：已确认的 P0/P1/P2 修复已落盘；构建、342 项 JUnit、430 项服务器 GameTest、18 条客户端功能检查及 6 个 Extra 客户端场景均有通过回执。** 当前 593 个 main Java 文件均有逐文件审阅记录。Monarch fallback gt-r1 使用 r10 主包及 probe，实际 **430/430 GameTests 全通过**，59.28 秒、exit 0、未强制终止且进程已退出。Extra-r2 使用 r11 包，完成 **5 个真实客户端按键场景和 1 个被动能力环境场景**；五种服务器实体均在客户端实际跟踪。最终封存包的身份、探针复跑与正常退出记录见 [最终验证回执](../../evidence/audit-20261025/final-validation.json)。

本报告替代首批“待修”总表。分域报告保留发现过程；若其中仍写早期“待编译”“待修复”，以本报告关联的快照和实际回执为准。审阅、源码契约、JUnit、服务器 GameTest 与实际客户端各自记账；不宣称“100% 无 bug”或逐像素完全还原。

## 1. 基线、范围与审阅依据

| 项目 | 固定依据 |
|---|---|
| 唯一维护工程 | `D:/Minecraft-Projects/academycraft-deep-audit-0.0.29-20261025`，初始检查点 `ef1617b2c13cb9eedf20ca6d11d3085a9471f59a` |
| 本体上游 | LambdaInnovation/AcademyCraft `7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9`；只读缓存 `D:/Minecraft-Projects/upstream-cache/AcademyCraft-7b1401c` |
| 附属上游 | NullaDev/ExtraAcC-1.12.2- `d66a190e3ae00d9ca8c154b47fca0509d4c171f7`；只读缓存 `D:/Minecraft-Projects/upstream-cache/ExtraAcC-d66a190` |
| 当前 API | Minecraft 1.21.1、Java 21；可复现构建锁 NeoForge 21.1.248，实际运行也仅验证此 patch |
| 环境边界 | 不修改生产世界、现役服务端或旧归档；服务器/客户端使用隔离环境 |

593 个 main Java 的审阅记录由 [核心清单](core-reviewed-files.csv)、[机器清单](machines-reviewed-files.txt)、[Extra 清单](extra-reviewed-files.txt)、[客户端清单](client-secondary-reviewed-files.txt) 和 [根补审清单](root-reviewed-files.txt) 合并去重。它是文件审阅记录，不是分支覆盖率：旧大型 GameTest 的专项补审按全部入口、实质断言和污染点核对，方法见 [机器旧测试审计](machines-existing-tests-audit.md)。补读 55 份 JUnit 及新回归文件另见 [测试清单](test-suite-reviewed-files.txt)。

[最终扫描统计](scan-summary.json)、[源码清单](source-inventory.csv) 和 [审查线索](review-hints.csv) 已刷新：716 个 Java 文件、78,446 行（main 593、JUnit 源文件 103、auditTest 20），人工审阅登记缺口为 0。342 个 JUnit 注解；462 个 GameTest 注解中本轮选择 430 个，另有 22 个历史差分别名及 10 个显式开关控制的可选兼容用例。关键词命中未直接算作缺陷，审阅登记不等同分支覆盖。最终源码和产物身份由 BUILD-INFO 与最终验证回执关联。

## 2. 11 个维度的结论与证据入口

| 维度 | 已核验及实际修改 | 验证入口与限制 |
|---|---|---|
| 1. 施法与 C2S | 保留 nonce/epoch/玩家身份和权威结算；补反馈加载/距离门槛、请求预算、当前等级复核、5 tick 蓄力与开发机定向余额 | [核心审计](core-audit.md)：CoreDeepAudit、CoreSkillLifecycle、DevEnergySync；实际客户端插值/余额。直接 handler、EmbeddedChannel 不冒充真实 socket |
| 2. 四系与破坏 | kill/void 旁路；采掘进度绑定状态；驻留射线/传送/爆炸边界；破坏提交重验权限、硬度与事件 | [核心](core-audit.md)、[数值](core-upstream-values.md)：真实 hurt、BreakEvent、冷区存在性与 ticket 回收；本轮 GT 已通过 |
| 3. Extra、AI、装备 | 空气弹/巡航弹恢复真实飞行；液影固定 AI；纸张、硬币、钩、载体事务；AIM 10 tick 预付脉冲 | [Extra](extra-audit.md)、[液影](liquid-shadow-audit.md)、[机器](machines-audit.md)：实际实体与支付正反例。上游没有 Mental Out；Extra-r2 六个客户端场景已通过 |
| 4. 玩家数据与生命周期 | 保留 codec/copyOnDeath；跨维即时快照、原维实体/会话清理；传送成就迁入 PlayerPersisted | CoreSkillLifecycle、LegacyAdvancementLifecycle、既有 Recheck；磁盘、Clone、真实死亡的证据边界分别记录 |
| 5. 无线与机器 | 合并能源广播/标脏；菜单数据槽/定向余额；驻留端点、活跃图、FE cache、多方块恢复检查 | MachineDeepAudit 实际 capability、NBT、ChunkHolder 广播队列和区块事件；不以 setChanged 次数臆测存盘周期 |
| 6. 渲染与音效 | 修 buffer/纹理释放、死亡 loop、按帧寿命、atlas、布局；新增 Aero/Cruise/水人 renderer | [客户端主审计](client-resources-audit.md)、[补审](client-secondary-audit.md)：r2 18 功能 PASS、字体/绘制及五 renderer 截图；最终 probe 回执独立关联 |
| 7. 静态状态 | ArcGen、硬币、Connection、波纹、音效、传送票据、频率会话清理；AI 不在 tick 中改 goal 集合 | 生命周期 GT；测试玩家/channel 也补成功、失败、重跑清理。源码审阅不等同“零泄漏证明” |
| 8. 守恒与组件 | 修载体重入复制、BE 快照、veto claim、纸张欠付、流体标脏、组件/负量/容量与小数能源 | ExtraDeep/ExtraParity、MachineDeep、实际菜单/配方；核对物品/流体/IF 前后账本 |
| 9. 流体与生成 | 修相位液体 atlas；核对 FluidType/source/flowing、非无限源、开关/分布/随机边界 | [客户端主审计](client-resources-audit.md)；datapack + vanilla Feature，不以资源契约代替全世界生成统计 |
| 10. 原版与三语 | 固定上游实际调用值；恢复 Extra 时序/选区/动量/附魔/堆叠/顺序；三语键/占位符对齐 | [核心全表](core-upstream-values.md)、[Extra 公式/配方](extra-audit.md)、[机器数值](machines-audit.md)；明确差异见第 7 节 |
| 11. 测试真实性 | 删除复制/未接线测试，修顺序/权重漏检、恒真、资源释放及 fixture 前提；新增实际正反例 | [测试深审](test-suite-audit.md)、[机器旧测试](machines-existing-tests-audit.md)、各域测试审计；342 JUnit 与 430 GT 独立记录 |

## 3. P0：确定的严重缺陷

每行按独立根因记录；修复跨多个文件、域报告和测试不会重复计数。以下均已实现并纳入 r10 的 430 项实际 GameTest 回归。

| 条目 | 根因与修复 | 实质回归 |
|---|---|---|
| CORE-P0-001 | generic_kill/fell_out_of_world 曾被矢量反射取消；统一接受伤害边界直接旁路能力防御 | 普通 ServerPlayer 真实 hurt：普通伤害仍反射，kill/void 穿透且不扣防御费 |
| CORE-P0-002 | MineRay 多 tick 进度只绑定位置，换基岩/保护变化后可提交；现绑定 BlockState，重验驻留/硬度/工具/事件 | 替换基岩、延迟保护拒绝和容器单份掉落 |
| CORE-P0-003 | 无效学习 nonce 的反馈直接读任意 devPos BE；现校验当前玩家、距离、驻留、类型并限 4 次/20 tick | 伪造反馈不加载远区、不变状态；合法学习实际付费 |
| CORE-P0-004 | Level.clip/Entity.pick 可在逐格读取中同步加载；现共享 LoadedRaycast 只读驻留区块并截断冷边界 | 正/负方向真实射线与冷 chunk 不存在断言；不按技能重复记根因 |
| CORE-P0-005 | Plasma 原生爆炸未逐块遵守技能 BreakEvent/硬度，冷边界可能加载；现许可缓存、原 state 复核和完整影响区域驻留预检 | 许可玻璃破坏、保护玻璃/基岩保留、冷区不加载；冷边界明确差异见后文 |
| EX-21 | MagManip 落地回调 recoverMaterial 可在源位再恢复一份；现整个提交持有 settling 并复核收据 | 真实放置事件重入、取消、替换落点及单份恢复；明确为复制 P0 |

## 4. P1：加载、事务、机制与性能问题

表内服务器回归已进入 430 项通过范围；实际客户端路径另标。Extra-r2 的六个场景单独记账，不扩大到第三方全矩阵。

| 条目（同根因引用合并） | 已实现结果 | 主要真实验证 |
|---|---|---|
| CORE-P1-001 / C02 | 起止立即、进度 5 tick；客户端游戏 tick 外推/校正、终止/超时清空 | SkillEventHandler 节奏审阅、ChargePresentationStateTest 生产状态机；客户端死亡 HUD/loop 实测。未做真实网络包数量计数 |
| CORE-P1-002 / EX-01 | 坐标/便携传送共用有界异步驻留：加票并 tick 轮询 getChunkNow，完成重验目标/会话/费用/物品 | TeleportChunkRequests：每玩家 1、全局 16、每请求最多 16 chunk、200 tick；实际 ticket 表清理；未用内部 managedBlock 冒充异步 |
| CORE-P1-003 | Electron/Scatter/Plasma/Missile/Storm 保留原维度；生命周期中断不从新世界补发旧攻击 | 同维成功、跨维拒绝与原维清理 |
| CORE-P1-004 | 入口、持续 tick、释放复核当前系/等级，保留开发模式豁免 | 真实按下包：降级拒绝、恢复成功、再降级中断 |
| CORE-P1-005 | StormWing/DirBlast 范围读取补驻留/交互/事件后状态；Shift 构造放置上下文前拒绝冷 hit | 冷区不加载、不变物品/资源与许可正例 |
| CORE-P1-006 | MagManip 源块拒绝负硬度，事件后取 BE，并在提交核对 state、类型、payload 与库存指纹 | 技能入口与实体交易测试；不同于 EX-21 落地重入 |
| 开发机能量视图（核心/客户端共同项） | 普通 Screen 被旧 BE 余额覆盖；新增 24 字节 DevEnergyPacket，owner-only、nonce、5 tick dirty-only，客户端取消旧值回读 | DevEnergySync 真实协商 EmbeddedChannel；client-r2 codec/收包、旧 nonce/spent 拒绝和 tick 不覆盖 |
| M01、M02 | 开发机/太阳能/节点能源变化合并标脏与完整广播；风机净余额不变但电池有变也保存 | 200 次余额变化的真实 ChunkHolder 队列、NBT 末值与电池存盘 |
| M03、M04 | 无线端点、解绑、菜单、风机跨块结构只看驻留区块；未知邻块暂停，加载后清孤儿/坏结构 | 冷区、真实结构与区块事件；矩阵旧测试补完整代理 |
| M06 | 外部 FluidTank 实际 fill/drain 标脏，simulate 不标 | 真实 capability 和保存状态正反例 |
| M10 | 成型输出按完整 DataComponents；自动化负量/超量/空槽容量/模拟守恒 | 真实 item capability、组件冲突与抽插账本 |
| M13 | 代理身份完成后失效 cached-null；主机/代理移除或 UUID 改变后旧 FE view 无效 | cached-null→有效能力、旧 view 拒绝 |
| EX-02、EX-03 | 硬币加入取消不扣物、不占登记；按保存 UUID 清除退出/跨维/移除状态 | 加入 veto/后续成功、离线清理 |
| EX-04 | 三种空气弹从即时射线恢复 AeroProjectileEntity 飞行、寿命、增长、阻力与碰撞 | 延迟命中、移动躲避、墙/基岩、加入/保护取消；Extra-r2 真按键、支付/命中与客户端跟踪通过 |
| EX-05、EX-06、EX-07 | 下界降温除 4/其他维度灭火；喷射完整动量相加；分离器脚底中心、延迟单次脉冲 | 两维度资源/火焰、向下施术、脉冲前后伤害 |
| EX-08、EX-09、EX-18 | 纸飞机加入/付款事务；钩/纸飞机掉落拒绝保留唯一表示；纸钻/纸甲 PlayerPersisted 欠付，成功交付才核销 | 加入/掉落取消、满包、保存和一次返还 |
| EX-10 | 液影加入回调后重验主人/会话、水桶和支付；失败撤销实体 | EntityJoin 回调移除桶负例 |
| EX-11 | CruiseBomb 恢复真实轨道、锁定追踪、加速、寿命/数量及退出清理 | 追击、墙阻挡、跟踪注册；Extra-r2 真按键、费用/伤害与退出通过 |
| 液影固定 AI（独立于 EX-10） | 独立 TamableAnimal 与固定近战/跟随/漫游/目标规则；预付、权限、清理；修现代目标条件先验视线导致不绕墙 | LiquidShadowDeepAudit 5 项及旧自然移动；最终自然绕墙、目标身份/路径和费用均由实际 GT 验证 |
| EX-12 | PaperDrill 保留正常无敌帧，移除额外命中经验 | 有效命中与正常免伤窗口反控 |
| EX-16 | 材料 BlockState 用 registry 名/属性持久化并兼容旧整数字段 | 实体/收据真实 save/load 与旧字段兼容 |
| EX-17 | LaserGun 补钻石采掘资格、原 state 进度、事件后权限/状态复核 | 铁矿真实掉落、取消与换基岩 |
| EX-22 | 载体放置回调后核对 state/BE 对象身份/owner，不写旧 payload 到替换 BE，不覆盖替换块 | 替换落点保留收据，再许可恢复 |
| EX-27 | AirWall/StormCore/Slam 使用快照和会话身份防伤害回调重入；Slam 先移除旧阶段再回调 | 实际事件重入，不只测试集合 helper |
| EX-28、EX-29 | 恢复加入 veto 时 discard 清瞬态 claim、保留收据；tick/hold/投掷扫掠驻留预检，避免 Entity.move 同步读 | 三项追加恢复/冷区/已加载移动；未用 r3 旧包结果冒充已覆盖 |
| C01、C04 | RenderUtils 使用 GUI buffer，不再泄漏私有 ByteBuffer；reload 逐个释放动态封面纹理 | client-r2 真实 GUI buffer、NativeImage/DynamicTexture 释放 |
| C03 | loop 创建/tick 检查 isAlive，死亡清 HUD 和声音 | client-r2 实际先存在 loop 再死亡清理 |
| SkillInputClientState | logout 释放 Connection/listener；同连接跨维保留拒旧 ack 与单调序号 | client-r2 实际 logout/reset 和旧 ack 反控 |
| C09（验证基础设施） | audit JAR 同包导致 JPMS split-package；统一 regression 包并加入 check | gt-r2 曾启动失败；gt-r3 与最终 430 已实际启动运行，非仅 javac 通过 |

## 5. P2：原版偏差、数据与客户端表现

| 条目 | 已实现结果 | 验证范围 |
|---|---|---|
| CORE-P2-001 | 跨维立即发能力快照 | 生命周期/同步实际回归 |
| CORE-P2-002、003、004 | GatherDataEvent 改 MOD 总线；ArcGen/频率会话清理、视觉 RailgunBeam 不保存；节点/矩阵/控制台/开发网络独立请求预算 | 静态约束与真实 handler/lifecycle；节点旧测试补 21 tick 窗口和 token 不误消费反控 |
| CORE-P2-005 | 传送成就进度迁入 PlayerPersisted，Clone/传送迁移旧顶层键，取大值夹 0..400，迁移不发奖 | 两次真实 respawn/Clone、399→400 单次奖励、其他字段保留 |
| M07、M08 | 遮光太阳能仍可按 100 IF/t 给电池供能，状态正确；float 存读/FE 交易不丢小数 | 真实 skylight 等待后 100 IF、399.6 NBT、模拟/负量；保留 3/0.6 IF/t、1000 缓冲 |
| M09、M12 | 熔炉近满只扣可接受整数 IF；成型配方核对 inputCount，缓存随 RecipeManager/配方身份失效 | 电池守恒、数量/组件/重载失效 |
| EX-13、EX-14 | 绝缘真实减伤/经验公式；完美纸张只由实际动作训练 | 生产事件及纸飞机/装备；清除未消费错误 helper |
| EX-19、EX-20 | Transmission 原版宽盒、满包有偿移脚边、无虚构超时；DropMagnet 原版脚底选区/增量动量 | 宽盒、拾取取消、守恒/位置/费用；保留权限与驻留边界 |
| EX-23、EX-24、EX-25、EX-26 | 两系数字技能顺序；纸甲/虚相甲拒绝书附魔；纸飞机 .5 高/脆块/长寿命；Slam 首 tick 与第 30 tick 两段 | 真实指令、铁砧、飞行/保护和时序 |
| CP 药水（Extra 补充） | 恢复堆叠 64；保留恢复 2000、半上限限制、50 tick 饮用与一次空瓶 | 32+32 合并、喝后 63 与 1 瓶、半上限拒绝 |
| C05、C06、C07、C08 | 液体 atlas ID、三语各 573 键/占位符、Railgun 按 tick 寿命、波纹 reset | JUnit 资源/语言；client-r2 atlas、同 tick 144 次与跨 10 tick、会话清理 |
| C10、C11、C12 | 教程 hover/click 共用缩放坐标；代码块结束恢复 section；传送 GUI 动态行数与无重叠页脚 | client-r2 实际 parser、0.5×/2×按钮、320×180 绘制/点击/滚轮到最后记录 |
| C13（核心/客户端共同项） | 预览与服务器共用 PenetrateTargeting：完整碰撞箱、lastSafe、CP 倍率上限；修负坐标截断 | client-r2 真实 ClientLevel 正负坐标、薄玻璃/第二墙；预览不构成服务端授权 |
| CatEngineRender | 上游双面 quad/朝相机；client tick 按 generation 推进，render 仅插值，修弧度/FPS 偏差 | client-r2 144 次渲染不改 BE/pose，500/0/200 IF/t 对应角速度 |
| AIM、HUD、遮罩、设置、技能列表 | 余额用 ContainerData；四槽行高；完整遮罩采样；裁剪外不命中；长列表可到尾 | client-r2 真文本/绘制/点击/滚轮；12400 正确夹到容量 10000，r1 错误 probe 修正但生产容量不改 |
| 外部媒体、只读技能树 | 外部轨道可播放、内置未解锁仍拒绝；JOrbis 构造失败关闭输入；只读详情可见但无学习操作 | client-r2 mouseClicked、合法/损坏 Ogg、详情正反例 |

## 6. OPT：完成与保留

| 条目 | 状态及理由 |
|---|---|
| CORE-OPT-001 RequestNodes | 已从约 3.3 万逐格查询改驻留 chunk BE 表遍历，保持 20 格球形、排序、最多 32 条；无额外全局索引 |
| M05 RF 输出 | 已用 BlockCapabilityCache，owner/邻接失效与卸载清理，访问前 FULL 驻留检查；保持 1 IF = 4 FE |
| M11 无线图 | 已按 owner chunk 活跃集合运行；休眠拓扑保存，只对真实拓扑/缓冲变更标脏；不降频改变带宽 |
| EX-15 载体收据 | 保留 OPT：touch/updateRuntime 可能重复复制 NBT/标脏；未为优化改变材料单表示协议，不列为已修 |
| 客户端有界开销 | 媒体最多 256 行遍历/首次本地索引同步；Matrix/Wind 少量临时对象、ThunderClap 最多 48 束独立 flush、长文本重复 width/substring。已记录，未宣称消除或虚构无限增长 |

## 7. 原版对齐、保留差异与已有保护

**上游范围事实。** Extra 固定快照仅空气使 12 个专属技能、念动力 11 个专属技能；没有 Mental Out、心灵控制或临时 AI goal 注入。液影是固定任务 EntityTameable，不虚构第三系或原版不存在的 Boss 控制规则。AC 开发机原版维护 user/GUI/同步，没有骑乘座椅；本轮验证开发会话与机器身份，不新增座椅玩法。

**数值/物品表。** 四系实际消耗/伤害/距离/曲线见 [core-upstream-values.md](core-upstream-values.md)；Extra 23 技能、25 物品、23 工作台 + 5 融合 + 1 刻蚀配方见 [extra-audit.md](extra-audit.md)；机器容量/带宽/范围/发电值见 [machines-audit.md](machines-audit.md)。保留 `academy` 身份与 `lasor_component`、`lasor_gun`、`teleporter`、`air_jet` 等旧拼写；Java 类名调整不改变组件/注册身份。

按实际调用核对，不能看声明局部值猜原版：MagManip 实际伤害 10；JetEngine/LightShield consume 参数顺序、Groundshock 的 90 弧度、ElectronMissile 实际冷却 700 均有固定源码依据。Extra EntityFlying 每世界更新 age 加两次，不能把常量误当真实 tick 寿命而翻倍。Meltdowner 实体和地形阶段独立，未凭猜测增加墙后命中禁令。

公开保留差异，不宣称字面复制旧漏洞：

- 当前分支既有 Charging 2.5 倍输出、1 IF = 4 FE；AIM 能量恰等于费用可完成预付脉冲。
- 射线、传送和移动遵守无同步加载。异步坐标传送保留冷目的地；便携传送原版只绑定主世界 XYZ，不虚构原版跨维度能力。
- Plasma 冷边界保留定额技能伤害和视觉，跳过可能同步加载的原生爆炸伤害/地形阶段。
- DropMagnet 按 tick 付费保留防绕费约束；纸张/物品持久欠付防吞物/复制；药水不恢复旧版重复给瓶漏洞。取消后的预付规则按实际路径验证。

**已有保护不重复重构。** 输入原已绑定玩家对象/UUID/维度/序列；反射已有 ThreadLocal 防重入/finally；附件已有有限数、长度、坐标清洗和死亡复制；AIM 正常 chunk 卸载经 setRemoved 注销；MachineLoop 虽持 ClientLevel，但 reset/world change 释放。机器一次掉落后清空、代理 UUID/类型/朝向验证及菜单数据槽保留。

`setChanged()` 不等于“每 10 秒强制存盘”。本轮处理真实重复脏标记/比较器通知/完整 BE 广播，clean→dirty 即标，保存读最新值，不等到卸载才补。`isLoaded/hasChunk` 也不无条件等同 FULL 就绪：关键路径使用 getChunkNow，避开 pending holder 的 managedBlock/join。

**退役代码不算现役空壳。** 旧 client/effect 嵌套 renderer、未消费 RenderUtils helper、MagManip deprecated 恢复协议和历史配方表已沿调用图标注；不以其充当现役完整性证据，也不重新接入旧实现。六个终端 App 有实际路由。MagManip 恢复位置可能回退，但已核对同 UUID 准入、BY_OWNER、durable ledger 阻止并行双结算；不能仅凭旧实体不可见就认定复制，未无证据重做协议。

## 8. 测试真实性与新增证据

| 范围 | 实质新增/修正 |
|---|---|
| 核心 | CoreDeepAudit、CoreSkillLifecycle、TeleportChunkRequests、DevEnergySync、LegacyAdvancementLifecycle：普通玩家、真实伤害/事件/ticket/余额/Clone 正反例 |
| Extra | ExtraDeepAudit 27 项 + ExtraParityDeepAudit 13 项：实体、取消、账本、指令/铁砧/饮用/冷边界；LiquidShadowDeepAudit 5 项单列，不重复并入前 40 项 |
| 机器 | MachineDeepAudit 12 项：capability/容器/流体/NBT/广播队列/活跃休眠；区块回调/事件不冒称完整磁盘卸载重载 |
| 教程 | 删测试专用复制 Set 重载，TutorialUnlockDeepAudit 2 项走真实玩家附件；映射 JUnit 保留 |
| 客户端 | ClientDeepAuditGate、ClientSecondaryAuditChecks、ClientLayoutAuditChecks、ClientTeleportPreviewChecks：实际 Font/GuiGraphics/纹理/音效/parser/输入/renderer；直接展示实体不冒充服务器施术 |
| 假覆盖 | 删未接线 LightShieldResourceLedger/FakeAccount 测试；7 处 indexOf 先验证两端存在；布局改生产输出；两表逐项 JSON 权重；清除错误/未消费数学 helper；关闭目录流和 ByteBuf |
| 污染 | Followup/Projectile 的玩家与 EmbeddedChannel 按本用例成功、失败/超时、重跑幂等释放；无 AfterBatch 全服清理，listener 仍 finally 注销 |

[55 文件深审](test-suite-audit.md) 逐项区分源码/资源/生产纯函数/状态机/解析器。七项内存删左 token 后旧顺序仍通过，两表首项权重 4→9 后旧检查仍通过；新条件拒绝。它是有界内存反证，没有冒称完整 mutation run。

直接 handler、手工事件、EmbeddedChannel、纯公式、源码 contains、真实服务器与实际客户端分别声明边界。历史 academy_baseline 8 项和 academy_followup_baseline 14 项是旧版本差分别名，22 项排除于 selected 430；客户端 gate 不混入服务器数量。没有按测试名称中的“完整/真实”提高证据等级。

## 9. 构建与实际运行回执

| 快照/运行 | 实际结果 | 证据与含义 |
|---|---|---|
| r9 构建 | 完整 check/build/auditTestJar 成功；实际 JUnit **342/0 失败** | [r9 构建日志](../../evidence/audit-20261025/build/build-audit-r9.log)、[JUnit XML](../../evidence/audit-20261025/build/junit-xml/)；不沿用删复制测试前数量 |
| r10 构建 | check/build/auditTestJar 成功；仅 probe 变动，部分任务 UP-TO-DATE | [r10 构建日志](../../evidence/audit-20261025/build/build-audit-r10.log)；不称又执行了全部 JUnit |
| Vicerach gt-r3 / r8 | **424 项，412 通过、12 失败**，exit 12、未强制结束 | [result](../../evidence/audit-20261025/gt-r3/result.json)、[产物](../../evidence/audit-20261025/gt-r3/r8-artifacts.json)、[预检](../../evidence/audit-20261025/gt-r3/preflight.json)；保留真实失败回执 |
| Vicerach gt-r4 / r9 | 4 分钟资源窗口不足，未启动 | 不记为测试通过或运行失败 |
| Monarch fallback gt-r1 / r10 | **430/430 全通过，59.28 秒，exit 0，forced=false，process_exited=true** | [result](../../evidence/audit-20261025/monarch-gt-r1/result.json)、[日志](../../evidence/audit-20261025/monarch-gt-r1/latest.log)、[产物](../../evidence/audit-20261025/monarch-gt-r1/r10-artifacts.json)；包括修复与追加回归，不把不同快照数字相加 |
| Monarch fallback client-r2 / r10 | 正常保存退出；**18 条功能 PASS**，首个 13 帧回执记录 **416 次受管文字绘制请求**；五 renderer 完成且调用者 pose 保持 | [result](../../evidence/audit-20261025/monarch-client-r2/result.json)、[详细回执](../../evidence/audit-20261025/monarch-client-r2/academy-deep-audit-client.txt)、[截图](../../evidence/audit-20261025/monarch-client-r2/screenshots/deep-audit-render.png)、[产物](../../evidence/audit-20261025/monarch-client-r2/r10-artifacts.json)。原始自动 result 的 visual_review=pending 保留，后续人工确认另存 [visual-review](../../evidence/audit-20261025/monarch-client-r2/visual-review.json) |
| r11 构建 | check/build/auditTestJar 成功，342 JUnit 全通过 | [r11 日志](../../evidence/audit-20261025/build/build-audit-r11.log)；修复 opt-in 验收夹具的角度同步和单次截图请求 |
| Monarch Extra-r2 / r11 | **6/6 场景 PASS，正常保存退出、exit 0、forced=false** | [result](../../evidence/audit-20261025/monarch-extra-r2/result.json)、[详细回执](../../evidence/audit-20261025/monarch-extra-r2/academy-extra-skill-gate-result.txt)、[视觉核对](../../evidence/audit-20261025/monarch-extra-r2/visual-review.json)。5 个正常 KeyInputHandler 场景→服务器费用/命中→客户端实体跟踪；Perfect Paper 为持有无经验后实际付费动作的被动环境场景，不冒称按键技能 |
| 封存包验证 | 以总回执关联最终源码与产物 | [final-validation.json](../../evidence/audit-20261025/final-validation.json)；18 条功能与字体/绘制分别计数，重复截图不增加覆盖 |

r3 的 12 项已按原因处理：4 项缺 ChargingOutput 测试 capability 显式 flag；其余 8 项中，两项有墙液影测试因同一个目标选择生产缺口失败，另六项为旧水人测试入口/反控、节点预算窗口、矩阵缺代理、太阳能异步光照、Slam 目标高度、下界默认最大过载等 fixture/前提问题。未删守恒/费用/权限/命中断言，未把旧失败改成通过；后续 r10 的 430 实际通过完成服务器复测。详见 [核心](core-audit.md)、[机器](machines-audit.md)、[液影](liquid-shadow-audit.md)、[Extra](extra-audit.md)。

文字绘制请求不是独立测试数；旧 probe 后续重复截图使计数增至 16 帧/512 次，也不增加功能覆盖。构建、Monarch GT 与 client-r2 的上述证据链接已核对存在。

Extra-r1 首次命中失败保留在 [原始回执](../../evidence/audit-20261025/monarch-extra-r1/result.json)：夹具固定朝向未送达服务端便注入按键。现等待服务端角度确认，并在 opt-in gate 释放物理鼠标捕获；没有修改生产施法或放宽命中/费用断言，Extra-r2 六项实际通过。

Extra 客户端场景使用 Level 5、预先解锁技能的隔离夹具，程序注入 KeyMapping 后经过正常按键边沿和联网施法路径；它验证此路径的支付、命中、同步和清理，不证明物理键盘操作或完整生存解锁流程。

最终总回执 [final-validation.json](../../evidence/audit-20261025/final-validation.json) 关联最终源码/主 jar/probe jar、构建/JUnit、430 GT、客户端/六个场景、资源、正常退出与临时现场清理。该回执刻意不纳入源码树哈希，避免 BUILD-INFO 与 JAR/回执循环；报告先封存，最终实际结果写入该回执，不回填源码产生循环身份。

## 10. 交付与兼容边界

1. 安装主 JAR；`-audit-tests.jar` 为隔离测试探针，不安装到正常游戏。截图与实际费用、命中、生命周期断言分别保留。
2. BUILD-INFO 记录源码树；最终验证 JSON 记录构建/JUnit、实际运行及主/probe 包身份。只有包内执行内容一致的快照才能关联已有行为验证，元数据差异需明确列出。
3. 所属进程退出、必要证据保存后，按既有授权清理登记的可重建隔离现场；清理范围与释放量写入最终回执，保留源码、交付物和共享依赖缓存。

NeoForge 声明 `[21.1,21.2)`，构建/实跑仅 21.1.248，其他 patch 未逐一运行。Minecraft 1.21.1、Java 21；JEI 为可选客户端联动，Curios/第三方伤害等未跑完整可选矩阵。新增实体要求两端同时升级；协议 v19、能力数据版本 v4。审阅未发现空壳不等于兼容全覆盖；本报告不构成生产发布或部署已完成的声明。
