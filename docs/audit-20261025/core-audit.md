# 核心能力与网络审计（2026-10-25）

工作副本：`academycraft-deep-audit-0.0.29-20261025`，初始提交 `ef1617b`。固定上游 `AcademyCraft-7b1401c`（7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9）。范围：C2S/核心数据与生命周期/四大基础能力/被动/伤害Mixin/有关实体与测试。全读清单见 `core-reviewed-files.csv`（文件内容SHA256，可与最终构建树比对）；不把静态阅读或源码字符串断言当作运行验证。

| 编号 | 等级 | 确认根因/已有保护 | 修复与验收 |
|---|---|---|---|
| CORE-P0-001 | P0 | `AcceptedAbilityDamage.settle` 对 generic_kill、fell_out_of_world 也执行矢量反射。满熟练反射倍率1.2可把伤害取消，强制扣CP不足也不阻止。 | 统一被接受伤害边界对两种伤害直接旁路能力防御；真实ServerPlayer hurt验证普通伤害仍反射，kill与void穿透且不扣能力费。 |
| CORE-P0-002 | P0 | `AbstractMineRayEffect` 多tick进度仅绑定BlockPos，完成时不复核硬度/权限。原位置换成基岩或保护变更仍可破坏。 | 进度绑定原BlockState，提交重验加载/硬度/工具/BreakEvent；测试替换基岩、延迟领地拒绝及成功容器单份掉落。 |
| CORE-P0-003 | P0 | `LearnSkillPacket.result` 对无效nonce里的任意devPos直接getBlockEntity，无距离或加载检查；无速率限制。正常学习能量检查本已受保护。 | 请求预算4/20tick、当前玩家校验；反馈复用受加载/距离/机器类型限制的验证。真实包负例验证不加载远区、不改状态。 |
| CORE-P0-004 | P0 | 多个远程技能直接Level.clip/Entity.pick；MC BlockGetter.clip逐单元调用Level.getBlockState，后者可同步加载区块。 | 共用只读已加载BlockGetter，保持形状碰撞，在未加载边界截断；覆盖正/负射线方向与真实世界区块不存在断言。 |
| CORE-P1-001 | P1 | SkillEventHandler每持键tick发送完整SyncChargingState，起止协议本已epoch/generation绑定。 | 进度5tick发送，起止即时；客户端由根代理插值与既有epoch门控。 |
| CORE-P1-002 | P1 | LocationTeleportActionPacket在最多16块的临时票据后调用同步getChunk(FULL,true)。HandheldTeleporterItem有同类路径。 | 按根要求改有界异步驻留请求，保留冷区传送能力；完成回主线程复核会话/费用/实体与目标，结束清票据。 |
| CORE-P2-001 | P2 | 跨维度仅清理施法与学习会话，能力HUD要等周期同步。附件持久化本已对称。 | 维度事件立即发能力快照，保留能力数据。 |
| CORE-OPT-001 | OPT | RequestNodes按10tick/20格范围逐块扫描，先isLoaded、返回最多32条，有限但每次约3.3万查询。 | 已改为范围内 resident chunk 的 BE 表遍历，保持20格球形/节点范围/排序/最多32条，无额外全局索引。 |

## 已存在且必须保留的保护

- 输入nonce绑定当前ServerPlayer对象、UUID、维度和单调序列；退出/死亡/跨维/重生清理或轮换。持续释放使用服务器ticks，状态在abort回调之前移除。
- 反射已有ThreadLocal防重入和finally清理；不能把它误报为两人互反无限递归。
- AbilityInterfererBlockEntity正常区块卸载已覆盖：NeoForm `ServerLevel.unload → LevelChunk.clearAllBlockEntities → onChunkUnloaded → setRemoved → AbilityInterferenceService.remove`。维度卸载独立兜底属于生命周期完善，非正常卸载必漏。
- 固定上游TileDeveloper只保留user/GUI/同步，没有骑乘座椅，不能根据题目措辞添加原版不存在的骑乘机制。
- PlayerAbilityData/Codec/Migration有有限数值、列表长度与坐标清洗；copyOnDeath和序列化对称，学习/资源补全顺序避免读档时错误回满。
- MagManip实体交易/恢复链与CoinEntity由物品实体组负责；本组审查技能入口，避免并行改同一文件。

## 验证边界

新增真实GameTest使用普通ServerPlayer+EmbeddedChannel，不使用硬编码isCreative的旧vanilla mock。旧测试中源码contains断言仅作为结构契约，不能证明实际伤害、BreakEvent、实体加入取消和生命周期。由根代理统一编译与在Vicerach执行；本组不启动Gradle或游戏。


## 第二轮全读补充与最终代码状态

| 编号 | 等级 | 证据与修复 | 实际回归边界 |
|---|---|---|---|
| CORE-P0-005 | P0 | `PlasmaOrbEntity` 原生爆炸不发技能 BreakEvent，且 `NONE` 也扫描方块；已加入逐候选方块许可缓存、mayInteract、负硬度和原state复核。原生爆炸仅在 `2r+2` 及被采样实体包围盒全部驻留时执行。 | 实际 PlasmaOrb.arm/tick：保护玻璃保留、许可玻璃破坏、基岩保留；冷边界不生成区块。冷边界保留定额技能伤害和爆炸视觉，跳过原生爆炸伤害/地形阶段，这是明确的无同步加载限制。 |
| CORE-P1-003 | P1 | ElectronBomb全服UUID解析可将旧世界球坐标用于新世界；Scatter取消跨维后从新眼位替代旧球发射。 | Electron增加同世界门槛；Scatter/Plasma/Missile/Storm状态捕获原维key，清理原维实体；Scatter死亡、退出、换维、停服使用销毁路径，同维普通松手/耗尽仍保留上游结算。实际ServerPlayer跨维负例及同维Electron发射正例。 |
| CORE-P1-004 | P1 | PlayerAbilityData.canUseSkill只检查已学/激活，管理端降低等级后旧高等级技能仍可施放/继续持键。 | 入口、持续tick和释放均复核当前职业/等级；开发模式保留豁免。真实SkillKeyDown启动失败/恢复等级成功/再降级中断。 |
| CORE-P1-005 | P1 | StormWing、DirBlast随机/范围方块读未先检查驻留与玩家交互；Shift的BlockPlaceContext构造器提前getBlockState。 | 补驻留/交互/事件后同state复核；Shift在构造前拒绝冷hit，真实冷区释放无资源或物品变化。 |
| CORE-P1-006 | P1 | MagManip世界源没有负硬度门槛，BE payload在BreakEvent前捕获。事件可修改同state容器内容导致搬运陈旧快照。 | 硬度/加载/玩家许可先验，事件后再捕获BE，提交比对原state、BE类型/payload和库存指纹；实体交易恢复由Extra组负责。 |
| CORE-P2-002 | P2 | CommonListener的GatherDataEvent被订阅在GAME总线。 | 改为MOD总线，属于事件接线修复。 |
| CORE-P2-003 | P2 | ArcGen的UUID节流记录只在停服清理；RailgunBeam缺少纯视觉实体不保存声明；FreqTransmitter会话仅定期验证。 | ArcGen退出/跨维清理；RailgunBeam不保存；频率会话失效BE/维度卸载清理，保留nonce和身份指纹。 |
| CORE-P2-004 | P2 | NodeConfig、InitMatrix、ConsoleCommand、OpenDevNetwork可无预算反复请求状态工作。 | 增加独立通道4次/20tick入口预算；LearnSkill采用相同预算；既有授权/距离仍保留。 |

补充：所有传送碰撞预检与异步chunk plan包含凸出方块形状访问所需的邻接1格，仍遵守最多16区块。Exact NeoForm `ServerChunkCache.getChunkFuture` 在服务器主线程内部调用 `managedBlock`，因此没有用它伪装成异步。新 `TeleportChunkRequests` 只加票、正常tick轮询 `getChunkNow`：全局16请求、每玩家1请求、每请求16块、200tick超时，异常/超时/死亡/退出/换维/维度卸载/停服全部回收票据。测试反射读取真正DistanceManager ticket表，避免只清Java map的假通过。

## 上游机制核对与假阳性排除

固定上游根为 `D:/Minecraft-Projects/upstream-cache/AcademyCraft-7b1401c`。数值证据见 `core-upstream-values.md`。不是根据旧Java注释中的“1.0.7”判断，而是读取本轮固定最终1.12.2源码。以下看似反常但确为可观察旧机制，不作错误修复：

- `cn/academy/util/RangedRayDamage.java` 的perform先执行实体伤害，再做独立地形射线。Meltdowner实体命中不被该后续地形阶段截断，不据此凭空增加墙后伤害禁令。
- MagManip虽声明8→15伤害局部曲线，但两个载体构造点实际传常量10；本地MagManipLegacyMath的10正确。
- FleshRipping实际伤害5→12、距离6→14；Shift实际伤害15→35、距离25→35。不能把方法名字/排列顺序误抄。
- JetEngine旧consume参数倒置、16tick按8tick向量外推；LightShield吸收处CP/OL参数倒置；Groundshock rotateAroundY(90)使用弧度；ElectronMissile冷却为clampi的实际700；Threatening对活体忽略方块，Flesh尊重方块。保留这些固定上游行为。
- 已存在的Charging输出2.5倍与IF/FE换算属于当前分支明确功能，未在这次通用审计中回退为上游倍率。

## 测试真实性审查

- 删除退休的 `LightShieldResourceLedger`、仅使用FakeAccount的 `LightShieldResourceLedgerTest` 以及扫描豁免；生产盾从未调用该ledger，旧两项通过不能证明实际扣费。PlayerAbilityData不再实现这个空接线接口。
- `PerformanceCompatibilityContractTest` 与 `ProjectileItemLegacyContractTest` 更新旧isLoaded/同步ticket字符串契约，保留预算、权限、生命周期限制；纯字符串只防结构回退，实际效果以新增GameTest为准。
- `DevLearningCadenceTest.legacySkillStimulationCurveIsExactForLevelsOneThroughFive` 是测试内重写公式，不能独立证明生产接线。相关真实学习会话由 `CoreDeepAuditGameTests.forgedDeveloperFeedbackCannotLoadChunksAndValidInductionStillPays` 穿过入口、session与实际能量账本。
- `LegacyParityMathTest`部分函数仅为旧公式刻画，不作为当前运行时技能已执行的依据。技能表字符串测试也不能代替实际施放。
- 旧 `RadiationIntensifyGameTests` 的首击使用真实hurt，后续只直接调用incoming事件；旧 `MeltdownerParityGameTests.lightShieldAbsorbsFallAndOnlyFrontalDirectDamage`直接构造AcceptedAbilityDamage，只证明局部盾算法；其mock玩家即便setGameMode仍不能证明普通玩家伤害/网络边界。新增CoreDeepAudit使用RecheckPlayers普通ServerPlayer和真实hurt跨65tick登录保护后验收。
- 新GameTests独立包为 `com.mohistmc.academy.regression`。最初Teleport测试放network同包触发JPMS split package启动失败，已迁移；包私有tickPending仅测试反射调用，不扩大生产API。一次编译成功不等于游戏模块可启动。

本域新增回归类：CoreDeepAuditGameTests、CoreSkillLifecycleGameTests、TeleportChunkRequestsGameTests。主代理统一记录真实编译/单测/远端GameTest结果；本文不把待运行或源码静态断言写成通过。已完成本域全读后冻结生产，后续只按真实失败修复。


## 集成阶段回归修复：开发机能量视图

机器组移除纯能量全BE广播后，SkillTreeGui（普通Screen而非Container）每clienttick读取旧BE会覆盖DevLearningResult准确余额；空闲充电也无定向消息。新增 `DevEnergyPacket(nonce,energy,maxEnergy)` 固定24字节 S2C，按当前有效学习nonce/context/距离/驻留验证，每5tick仅数值变化发送owner。issue记录初值，commit后由原开发进度包承担余额；旧close不可撤新watch，当前close/过期/退出/换维/停服清理。客户端删去BE/物品回读，仅当前nonce且未spent视图接受能量快照，不碰pending/反馈文案。此为确认的集成P1，未恢复每tick全NBT。

`DevEnergySyncGameTests` 用普通ServerPlayer和NeoForge注册payload的EmbeddedChannel配置验收真实出站包（明确不等于真实socket握手），覆盖注能、owner定向、dirty-only、旧/新nonce close；客户端 `ClientSecondaryAuditChecks` 由机器组补实际codec往返、收包、nonce/spent拒绝及gui.tick不覆盖探针。另修终端只读树的提前return：详情先绘制，未学技能显示前置/描述而不显示开发操作。

## 全树缺口补审：注册、命令、配置、公开事件与旧回归

补齐原清单之外的31个Java文件：`AcademyCraft.java`、`advancement/`、`api/event/`、`command/`、`config/`、`gametest/recheck/` 全部文件及三个旧Parity/Defense测试类。逐文件路径、行数与已读快照SHA-256已写入 `core-reviewed-files.csv`，不是按目录名称推定已审。

| 范围 | 权限、重入、公式与既有保护结论 |
|---|---|
| AcademyCraft.java | 所有payload显式区分C2S/S2C并使用必需的v19协议。机器FE代理校验主/子块身份及驻留；移除机器后旧代理不能继续操作有效储能。注册与commonSetup无新的确认缺陷。 |
| api/event/ 全5文件 | Ability/Terminal/Tutorial是通知事件，不能误认作可取消前置许可。MatterUnitHarvest复制immutable位置；Wireless的Create/ChangePassword/LinkNode/LinkUser为可取消意图，事务提交保护在调用方，已由对应机器/物品域审查。返回当前attachment及持有运行对象是API语义，不能仅据此认定重入漏洞。 |
| command/ 全4文件 | `/aim`、`/aimp`、`/acach`、`/acmigrate`全部要求permission 2；能力类别/技能索引有边界，经验0..1、等级1..5；改动后发送事件和权威同步。迁移radius最多128，仅getChunkNow遍历驻留BE并拒绝覆盖既有owner。管理员命令批量修改属于明确权限，不作为普通C2S绕过。 |
| config/ 全5文件 | 对照固定上游CPData/default.conf：CP恢复`speed*.0003*max*(1+current/max)`；OL恢复`speed*max(.002*max,.007*max*(1-.25*current/max))`；延迟15/32、成长.0025/.0058一致。个人倍率在现有能力层处理。技能倍率解析过滤NaN/无穷/负值，维度旧数字0/-1/1及金属旧ID有兼容映射。reload读取配置当前值，无陈旧常量副本。 |

### CORE-P2-005：未完成传送成就进度在重生时丢失

`LegacyAdvancementBridge.teleported` 原把计数放在实体persistentData顶层。精确NeoForm `ServerPlayer.restoreFrom`只复制`PlayerPersisted`，本项目原无Clone补复制；399次后死亡会回到0。固定上游 `TPSkillHelper.incrTPCount` 同样使用顶层旧键，说明是继承的存储缺陷，不能把它当作本次新回归，也不能以旧实现为由保留进度丢失。

按根代理明确授权修复：计数改存`PlayerPersisted`，传送及Clone入口兼容迁移已有顶层值，取两处较大值并限制0..400后移除旧键；Clone覆盖“旧存档还未再次传送就死亡”的路径。只写该计数字段，不替换其他持久数据；迁移不发奖励，既有已解锁成就不撤销。

新增 `LegacyAdvancementLifecycleGameTests`：普通ServerPlayer经过真实`PlayerList.respawn → ServerPlayer.restoreFrom → Clone`两次，验证旧399迁移、其他持久字段保留、不到400不奖励、400触发真实AdvancementEarnEvent恰好一次、已完成状态跨重生及再次传送不重复奖励。它验证重生边界，不伪称执行了致死伤害或真实socket握手。编译与运行结论由根统一记录。

### 补审测试的证据边界

- `ElectromasterParityGameTests`实际调用Coin.use/tick与MagManip载体hold/drop，能证明局部运动；它的vanilla mock玩家不能证明普通玩家hurt、物品生存消耗、完整搬运账本和登录会话。
- `TeleporterParityGameTests`实际触达ShiftTp的放置失败后远程掉落分支，断言恰好1件掉落及1件扣除。devMode使CP免费，因此它不能证明正常CP结算或传送越界保护；后者由本轮独立冷区测试覆盖。
- `VecDefenseGameTests`前两项实际tick投射物，第三项直接构造AcceptedAbilityDamage与调用damage，只验证局部强制扣费/反射算法。不能替代真实hurt无敌帧、取消事件和死亡处理。
- `RecheckDamageTransactionGameTests`使用普通ServerPlayer和真实hurt，等待65tick越过出生保护；FakePlayer项明确断言伤害入口不执行。取消/护盾/无敌帧以相同attachment前后对比资源、成长和熟练度；嵌套真实hurt检查生命、运动、hurt阈值未被外层回滚抹掉。未发现测试内重写生产伤害公式的假通过。
- `RecheckSkillPacketGameTests`直接调用生产handler，以最小IPayloadContext只实现player和enqueueWork；验证顺序、nonce、跨玩家、跨维和重连边界。codec往返另测，但这些直接调用不等于真实网络解码与握手。
- `RecheckSkillLifecycleGameTests`用真实tick、hurt、PlayerList.remove及teleportTo产生生命周期事件，监听数量确认触达；charging初始状态由夹具直接建立，因此只证明之后支付/清理，不证明按键网络启动。取消死亡的正例保留存活玩家会话。
- `RecheckPlayerPersistenceGameTests`确实写读随机UUID的.dat/.dat_old，使用生产登录/登出保存；它的malformed测试先通过生产Codec清洗再写盘，证明清洗结果可持久化，不能宣称从损坏原始磁盘字段直接恢复。不会操作既有玩家文件。
- `RecheckCompatDamageGameTests`、`RecheckEquipmentDamageGameTests`和`RecheckCompatPlayers`显式配置开关及所需模组，普通运行未启用。真实Curios装备、dummy受伤、void-spike实体碰撞和IF账本可证明固定整合快照兼容；反射只取安装模组API，不模拟它的实现。协商EmbeddedChannel不等于socket。
- `RecheckSessionState`、`RecheckSessionServerGate`、`RecheckPersistenceProbe`、`RecheckShutdownTransientProbe`默认关闭，要求显式系统属性和隔离标记；只使用内部固定文件名/角色。真实双socket gate有阶段超时、最终保存观察；Jet停服探针清楚标明直接runtime setup，不冒充完整技能激活/支付。此轮未执行该可选gate，不用代码存在宣称整服验收通过。

### 真实运行失败修正：节点旧测试与限流窗口

Vicerach gt-r3的旧`NetworkAdversarialGameTests.nodeHandlerRejectsDuplicateReorderedForgedAndCrossViewerPackets`在一个tick内给同一玩家发送latest/late/duplicate/forged四包，再要求第5包valid-third立即成功，与新4次/20tick预算冲突。生产代码先限流后acceptAction，未污染第三个token。测试现先断言第5包被拒绝且节点未变，再等待真实21tick重试同一token并要求成功；同时覆盖流量上限和令牌不被误消费，没有放宽权限或提高预算。

### 集成P2：穿透预览未同步服务端安全落点

根代理追加核验后确认 `LegacyAbilityPresentationRenderer` 仍保留旧`(int)`负坐标截断、仅检查脚/头两个格子及返回终止cursor的算法，可能在负坐标、薄碰撞形状或第二堵墙处显示与本轮服务端安全落点不同的位置。现将服务端已验证的算法直接提取为只读 `PenetrateTargeting`，两端共用原0.8步进/三阶段/lastSafe及`TeleportDestinations.isSafe`；客户端只给标记加眼高。距离选择也复用包含CP配置倍率的上限。没有新增C2S、没有把客户端预览当作服务端授权。

`ClientTeleportPreviewChecks` 在真实ClientLevel中创建未入场RemotePlayer，以正/负坐标的薄玻璃板验证非相交点可用、相交点拒绝、第二墙前最后安全步及1格请求不超范围。临时本地块在同tick finally恢复，不移动真正本地玩家，不发包。probe等待所需四块驻留后执行；Monarch client-r2真实运行已通过上述断言。源码契约只确认服务端和renderer使用同一函数，不作为实际渲染通过证据。

## 旧auditTest补审（7文件）

以下7文件全部逐行读取，当前版本快照已补入 `core-reviewed-files.csv`。它们没有读取生产Java源码后用字符串匹配来充当行为测试的情况。

| 文件 | 数量与执行边界 | 真实性和限制 |
|---|---|---|
| AuditBaselineGameTests | `academy_baseline`，8项历史差分探针 | 逐项委托AuditGameTests中的同一断言，对冻结0.0.23旧版本展示预期失败；不是8项独立新覆盖。 |
| FollowupBaselineGameTests | `academy_followup_baseline`，14项历史差分探针 | 委托FollowupGameTests，对冻结0.0.24版本展示历史缺陷。与上一命名空间合计22项，明确排除在本轮selected430项及通过数之外；它们不是当前生产版本必须失败的规范。 |
| AuditProbeMod | 0项，只有`@Mod("academy_audit")`入口 | 独立审计JAR的注册入口，无生产逻辑或伪通过断言。 |
| AuditGameTests | `academy_audit`，15项 | 实际施放/放置回调、ray、碰撞和物品账本；取消事件都计数验证，反射轨道炮有被墙拦截及去墙后实际伤害正例。旧mock玩家不能证明普通玩家hurt；ThunderClap项只证明没有服务器LightningBolt/铜/避雷针/苦力怕副作用，不证明视觉包已送达或客户端闪电已渲染。GroundShock重设测试世界RNG得到可比较平移足迹，属于隔离世界夹具，不证明生产随机数状态不变。 |
| FollowupGameTests | `academy_followup`，20项 | 普通ServerPlayer，真实实体事件/物品Pickup回调；Jet使用直接runtime start和手动PlayerTickEvent，证明运动/回调重入，不能冒称自然网络按键启动。资源rollback预期使用生产DynamicSkillRules逐项构建，证明交易保留关系，不是独立数值公式验算。 |
| ProjectileGameTests | `academy_projectiles`，26项 | 实际实体加入、飞行tick、扫掠碰撞、伤害归属/标签、掉落拒绝、手持优先及组件保存；2项等待世界12tick观察真实飞行/移动目标，其余手动tick。保存项真实执行实体save/load，但未经过磁盘或区块卸载，不夸大为整服存档验收；随机Owner替换项验证缺owner分支，不冒称真实退出事件。 |
| MachineUiClientGate | 0个GameTest注解；显式客户端gate为6或14案例 | 真实机器放置/交互、GUI鼠标键盘输入、服务器改名/连接及截图落盘，反射读取实际布局状态。截图存在断言不是像素内容识别。默认关闭，一次性进程以finished退出；volatile pending/serverFailure建立跨线程阶段交接，单进程多轮重复执行不在其契约中。未把此可选gate的案例数混入430项服务器测试。 |

没有发现“比较同一常量/从未调用生产入口却固定成功”的恒真断言。负向测试的证明边界如表明确记录，不能因为标题写了真实/完整就提高证据等级。历史别名及可选客户端gate未被重复计为本轮通过。

### 测试夹具跨用例残留修复

确认缺陷：`FollowupGameTests.player`注册普通ServerPlayer和EmbeddedChannel后不清理，`ProjectileGameTests`共用此helper；每个完成用例仍持有玩家、登录会话及连接缓冲。按根代理追加授权，仅修测试夹具：创建channel后、登录前向该`GameTestHelper.testInfo`注册专属`GameTestListener`；success、failure（含超时）和rerun回调进入同一幂等关闭函数，只有当前PlayerList中仍是同一对象时才remove，并在finally释放channel。没有静态共享玩家表，没有AfterBatch全局一锅清理，不清理其他测试玩家。

接口依据为精确NeoForm的public `GameTestHelper.testInfo`、`GameTestInfo.tick`完成/失败通知及GameTestListener四方法。现有事件listener的finally注销和Jet/Transmission上下文清理继续保留。此修复不新增测试数量；带清理的r10测试JAR已经在Monarch实际执行，430/430全部通过，不使用旧夹具结果替代新实现的运行。

## 最终域内验证状态

已只读核对 [Monarch gt-r1回执](../../evidence/audit-20261025/monarch-gt-r1/result.json)：r10实际430项、0失败、exit 0、未强制终止、进程已退出，包含本域核心/异步传送/开发机余额/成就重生/节点窗口及夹具清理。历史baseline22项继续排除。

[Monarch client-r2实际回执](../../evidence/audit-20261025/monarch-client-r2/academy-deep-audit-client.txt)明确通过共享穿透正负坐标及薄形状、DevEnergy codec/当前nonce/旧nonce/spent/tick不回写、终端已学与未学只读详情真实点击/绘制。最终截图请求去重后的probe另由根封存验证，不把该capture-only变更的待验误写成本域功能尚未运行。SkillTreeGui最终差异已补读，CSV仍标targeted，不冒称本组独占全文件审阅。
