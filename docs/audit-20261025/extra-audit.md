# ExtraAcC / item / entity 深审计

基线：`ef1617b2c13cb9eedf20ca6d11d3085a9471f59a`。对照：ExtraAcC `d66a190`，AcademyCraft `7b1401c`。本域先读源码、再登记缺陷；运行验证由主任务统一执行，本文不将源码断言当成 GameTest 通过证据。

## 范围与身份

ExtraAcC 上游仅有 `CatAeroHand`（12 个专属技能）与 `CatTelekinesis`（11 个专属技能）。整个固定上游未找到 MentalOut、精神控制或动态 AI goal 注入；不能凭名称新增第三系。`EntityWaterman` 的 AI 在初始化时安装固定目标选择/近战/跟随/漫游任务，不存在需要还原的精神控制并发替换逻辑。

本域覆盖 `world/item`、`world/entity`、`skill/ability/aerohand`、`skill/ability/telekinesis`，以及相关物品/实体注册、Extra 配方及测试。`MagManipEffect`、统一伤害边界与技能消费入口归 core 审计人，载体与持久事务归本域。逐文件清单见 `extra-reviewed-files.txt`。

## 已确认缺陷与修复台账

| ID | 等级 | 基线触发与影响 | 处理 / 验证方向 |
|---|---|---|---|
| EX-01 | P1 | 便携传送器为目的地 addRegionTicket 后直接 getChunk(FULL,true)，同步加载未加载区块 | core 负责有界异步请求、结束清票据、提交前重验物品/绑定/费用；验证未加载成功异步到达及取消不扣款 |
| EX-02 | P1 | Coin.use 忽略实体加入失败仍扣硬币；setThrower 已登记未加入实体，之后永久阻塞投掷 | 加入失败撤销登记且不扣物；测试 EntityJoin 取消与下一次成功 |
| EX-03 | P1 | CoinEntity.remove 依赖同维度在线 thrower，离线/换维度后静态表保留实体及世界引用 | 按保存的 UUID 条件删除；死亡/登出/停服清理与物品结算测试 |
| EX-04 | P1 | VolcanicBall/AirBlade/BomberLance 使用长距离即时 SkillRaycast，完全缺少真实弹体飞行、寿命、增长、阻力与脆块碰撞 | 真实服务器弹体，保留 skill ID；正向延迟命中/衰减与负向墙、基岩、保护取消、卸载边界 |
| EX-05 | P1 | AirCooling 缺下界冷却除以4和非下界灭火；OL=0强制拒绝使纯灭火不可用 | 按上游还原并测试两维度实际 overload/fire |
| EX-06 | P1 | AirJet 强行将竖向速度抬至.28并将既有动量乘.15，上游直接加 look*(2→4) | 恢复完整向量相加，向下施术负断言 |
| EX-07 | P1 | AeroSeparator 即时在玩家半高伤害，上游 EntityVacuum age>=5才首次脉冲并跟随脚底；己方弹体被额外排除 | 还原延迟、脚底中心与全部弹体清理；延迟前无伤害、到时一次有效伤害 |
| EX-08 | P1 | PaperPlane.use 加入实体前已扣CP/加经验；实体加入取消仍付费 | 加入与付款采用现有受控事务模式，失败无费用/经验 |
| EX-09 | P1 | PaperPlane、MagHook 落地回收忽略 ItemEntity 加入失败并立即 discard；取消掉落可永久丢物 | 未交付时保留唯一回收表示并有界重试；取消/恢复各有守恒断言 |
| EX-10 | P1 | LiquidShadow 加入实体回调后，仅重验CP不重验水桶；consumeWaterBucket 无返回值 | 提交时重新匹配水桶及owner状态，失败撤销实体且不扣CP；对抗回调移除桶 |
| EX-11 | P1 | CruiseBomb 用会话内计数与粒子/即时攻击代替真实 EntityBomb 追踪弹 | 逐弹体轨道、锁定、加速、40tick锁定寿命、穿墙阻断与退出统一清理 |
| EX-12 | P1 | PaperDrill 每tick清除受害者 invulnerableTime，且额外命中经验.0005；上游遵守正常伤害冷却 | 移除免伤重置和额外经验，检查有效/免伤两支 |
| EX-13 | P2 | Insulation 普通能力防御经验 amount*.0003，上游*.002；另有未调用helper把熔毁减伤写成电击同值 | 修实际事件入口与废弃误导helper，验证真实 damage 事件 |
| EX-14 | P2 | PerfectPaper 每5tick持纸免费训练.00125，上游仅实际纸飞机/纸装备动作训练 | 移除无源训练；检查持纸不涨与有偿动作会涨 |
| EX-15 | OPT | MagManip tick 对同笔事务 touch/updateRuntime均复制NBT并setDirty（投掷每tick两次） | 在正确性修复后合并实际变化写入，避免为优化改变物品事务语义 |
| EX-16 | P1 | MagManip持久 BlockState 使用 Block.getId/int，世界注册表变化后不稳定；实体与事务均如此 | 增加registry codec持久字段并兼容旧字段，不改已有实体/事务ID |
| EX-17 | P1 | LaserGun 没有钻石级掉落资格；以旧 BlockPos 累积的采掘进度可用于后来替换的方块，BreakEvent 后没有复核 | 已补真实工具资格、方块状态进度指纹与事件后状态/权限复核；新增实际铁矿掉落、取消和回调换基岩三项测试 |
| EX-18 | P1 | PaperDrill/纸甲拆解先删除纸材料，再调用可能被取消的掉落实体加入；失败永久丢纸 | 已改用 PlayerPersisted 内精确纸张欠付数，库存优先、掉落成功才核销，20tick 重试；各有满包取消后保存与一次返还断言 |
| EX-19 | P2 | PsychoTransmission 将原版宽盒选取缩为细射线、满包直接跳过，并增加上游没有的72000tick超时 | 已恢复原版盒选取与满包有偿移到脚底、保留实际拾取事件/所属人/延迟限制、删除虚构超时；宽盒正例与拾取取消负例 |
| EX-20 | P2 | DropItemMagnet 将上游脚底±8盒改成玩家bbox膨胀，提前排除pickupDelay物品并加入.82阻尼/抬高目标 | 已恢复选区与增量速度公式；费用仍按当前入口每tick支付，避免原版松手才结算可绕费的漏洞 |
| EX-21 | P0 | MagManip land 未持有 settling；落点已写入时触发放置事件，回调 recoverMaterial 可在源位置再恢复一份，外层仍保留落点 | 已使整个land提交持有settling，回调恢复/嵌套tick不能产生第二表示；新增真实落地事件重入、取消与成功恢复测试 |
| EX-22 | P1 | MagManip place 事件返回后不核验落点，监听器换块后仍清账，载荷可能写入回调安装的同类BE | 已复核落点状态/BE对象身份、事务与owner/权限；不向回调替换的BE写入载荷，不覆盖回调替换块；新增替换落点后保留收据测试 |
| EX-23 | P2 | 两系 SkillRegistry 将通用课程插入专属中间；Aero专属 bomber/offense、separator/storm 顺序也与固定上游相反 | 已保留字符串ID并恢复CatAeroHand/CatTelekinesis原序；新增真实/aim learn #7、#10、#11指令断言具体技能身份 |
| EX-24 | P2 | PaperArmor/ImagEnergyArmor 缺原版 isBookEnchantable=false | 已恢复book veto；新增真实AnvilMenu对铁甲成功、纸甲/虚相甲拒绝的正反测试，使用创造模式使标签限制不能掩盖book veto |
| EX-25 | P2 | 纸飞机缺上游硬度≤.2脆块穿透，实体高度.25而非.5，1200tick回收替代上游近无限寿命 | 已恢复.5高度、近无限飞行与脆块收获，保留已加载预检；同tick继续射线以阻止脆块后穿过第二硬墙；新增允许/取消/长飞行3项测试 |
| EX-26 | P2 | PsychoSlam 首次伤害在施术当场触发，上游 EntityBonder 在首个实体tick触发；30tick下砸已存在 | 已延后到首tick，新增实际服务器tick断言施术当刻无伤、首段15与30tick第二段30；旧Slam墙/首表面测试同样改为延迟观察 |
| EX-27 | P1 | AirWall/StormCore/PsychoSlam 在遍历活动会话时触发可同步改变该容器的伤害回调 | 已用会话快照+身份复核；Slam先移除旧阶段再触发末段伤害，避免新阶段被旧迭代器移除；仍需事件重入实际运行验证 |

MagManip恢复链已读完整：ServerListener仅登录时调用resume，旧entityChunk确实不随移动更新，原载体已卸载时可以在源位置重建同UUID并通过durable/local claim。不过PersistentEntitySectionManager的knownUuids拒绝同世界同时加入同UUID，BY_OWNER条件claim拒绝另一在线载体；新载体结账删除ledger后，旧载体再次加载会被durable claim拒绝。因此已证实位置可回退和恢复入口有重建，并未证明两份材料都可结算，不改其UUID/ledger协议。CP药水堆叠上游默认64、基线16的差异已恢复，并新增实际库存32+32合并为64、喝后63/空瓶1、半上限拒绝使用测试。

### 追加恢复与移动链核验

- EX-28（P1，已修待运行）：resume先initializeMaterial占用BY_OWNER，再addFreshEntity；加入取消时不释放瞬态claim，形成未加入世界却isAlive的幽灵载体。现在加入失败discard清瞬态claim、保留持久材料收据。真实EntityJoin取消后检查世界及claim均无实体，再放行重试并检查恰好恢复一次。
- EX-29（P1，已修待运行）：Mapped MC Entity.move先做碰撞，后用getBlockState读取落脚块（FULL,true）；碰撞的Level.getChunkForCollisions调用FULL,false仍可在已有pending holder上managedBlock/join。载体的hold、tick前当前区域、实际投掷扫掠均改用LoadedRaycast.isAreaLoaded/getChunkNow预检；冷边界阻止移动并按原材料收据恢复，不创建票据或强制取块。新增实际冷chunk不变/材料归还负例和已加载空间真实位移正例。
- 恢复/放置/结账对源块的检查也使用getChunkNow语义，避免hasChunkAt只证明存在FULL加载请求、仍读取未完成区块。已保留当前单表示协议，不以加载时找不到可见实体作为新的孤儿证明。

## 原版公式映射

以下范围均按熟练度 0→1 线性变化；CP/OL/冷却分别列出。消耗以真实入口验证，不能仅证明数学helper。

| 技能 | CP / OL / 冷却tick | 核心参数 |
|---|---|---|
| volcanic_ball | 40→100 / 40→30 / 20→5 | 伤害10→20，初速.3→.6，阻力.98，age80衰减至.5，尺寸.2每更新+.02 |
| air_blade | 100→150 / 60→40 / 40→30 | 伤害12→18绕甲，速度.8，阻力.99，age40→60衰减至.75，横摆2sin(ageπ/10) |
| air_cooling | 400→1600 / 0 / 300→60 | OL减少200→800，下界除4，其他维度灭火 |
| air_wall | 500→300 / 90→60 / 60→40 | 环每tick扩.2，半径6→9；每2tick伤害10→15，脚底Y-.5至+2.5，清除圈内所有弹体 |
| air_jet | 200→400 / 80→40 / 40→10 | 既有速度加视线方向*(2→4) |
| bomber_lance | 600→900 / 240→160 / 80→60 | 伤害48→72，速度1→1.5，无阻力，age20，击退(10→15)/目标高 |
| offense_armour | 启动600→400 / 80→50 / 40 | 维持1CP/.5OL，伤害8→12，每目标100*高度CP；范围上游中心±2立方 |
| flying | 启动0 / 80→50 / 40 | 每tick16→8CP/.5OL，经验.0001/tick |
| aero_separator | 1200→1800 / 480→360 / 200→120 | 脚底±3立方，5age后40→60 drown伤害，含自身，单次脉冲 |
| storm_core | 3000→2000 / 300→200 / 450→300 | 6格选点；age1–79吸引.5*max(1-distance/10,.2)，age80造成80→120乘同衰减，age100结束 |
| ascending_air | 按免除伤害*(40→20)CP | 摔落伤害cap10→5，经验免除量*.001 |
| airflow | 按免除伤害*(40→20)CP | 上游意外读取ascending熟练度：减伤.75→.95；每20tick且自身熟练度>=.5时50CP补氧 |
| psycho_throwing | 蚀刻400→200 / 30→20 / 40→20 | 普通石CP*1.5；伤害12→16，蚀刻*1.25；加速.1→.15蚀刻*1.5 |
| psycho_needling | 800→400 / 20→10 / 20→10 | 伤害4→8绕甲，无重力/阻力，针回收 |
| psycho_transmission | 启动0 / 5 / 20 | 维持.5CP，拾取(4→2)*距离平方CP/2.5OL；沿视线包围盒正向expand(1,1,1)，满包有偿移到脚底，无固定会话超时 |
| insulation | 100*原伤害CP | 减伤.1→.2，电击*2，熔毁*2.5；经验原伤害*.002 |
| cruise_bomb | 启动200 / 20 / 40 | 维持2→1CP/.05OL，生成/锁定各50CP，最多4/5/6/7/8弹，伤害4→8绕甲 |
| overload_thinking | -1000→-2000CP / 100 / 300→60 | 消耗过载容量恢复CP |
| perfect_paper | 被动 | 纸飞机.001，防具.000001，无持纸挂机训练 |
| psycho_slam | 3000→2000 / 180→120 / 200→100 | 射程4→8，单目标首伤15→20，30tick后2倍伤害 |
| hardened_shell | 启动1500→1000 / 100→50 / 160 | 2CP/tick；受伤每点60→30CP/.1OL完全吸收；使用时迟缓9，结束后迟缓4持续79tick |
| liquid_shadow | 2000→1000 / 300→200 / 100 | 一水桶；1CP/tick；伤害20→30，每次伤害*5CP；100HP水人、固定跟随/攻击/反击AI |
| paper_drill | 启动4000→2000 / 120→80 / 100 | 64纸，1CP/tick，接触100*高度CP、10→15伤害，正常免伤窗口；结束一次归还64纸 |

`EntityFlying` 上游世界更新与实体自身各增加一次 age，故空气弹实际更新寿命为40、20→30、10个服务器tick。不得把注释常量80/40→60/20直接当作真实tick寿命而翻倍。

## 物品、配方与兼容边界

上游25件Extra物品在当前注册中均有对应，保留现有 `academy` 注册身份和旧拼写：`lasor_component`、`lasor_gun`、`teleporter`、`air_jet` 均未更名；`LaserGunItem` / `HandheldTeleporterItem` 只是Java实现类名。23个工作台配方与5个ImagFusor、1个ETCH配方已逐项核对输入、输出数量和1000mB虚相流体需求；其中红石需要2个。`ExtraRecipeExecutionGameTests` 的真实菜单/机器执行覆盖应继续运行，不以JSON文件存在等价于配方可执行。

便携传送上游仅记录主世界XYZ，不支持跨维度；原版 `setPosition` 未提供现代安全/异步承诺。新实现必须遵守本轮禁同步加载约束，显式异步完成与资源提交，不虚构原版跨维度体验。

能量/绑定/开关数据使用组件；应保留编解码身份。RayTwister 39tick隐形刷新本身有界，不存在永久属性modifier叠加。CP药水恢复2000并限制至最大CP一半，喝50tick、空瓶一次；不能恢复上游会重复给瓶的漏洞。共振装甲原版特殊装甲展示防御值0，现代材料0不是凭空漏写护甲。

## 测试真实性与stub

- `AeroBehaviorMathTest` 的摔落cap8→2、分离器蓄力30→18、airflow间隔1→5均不是实际入口使用的原版公式；不能当作机制覆盖。
- `AerohandBehaviorSourceContractTest` 将即时分离器与射线字符串作为合格标准，反而固化缺陷。应改为真实延迟/实体GameTest，源契约仅保留架构禁止项。
- `TelekinesisRulesTest` 中 insulation 与 hardened stance helper 和运行路径不一致；删除无消费者的误导helper或修实际调用，不能只改断言。
- `ExtraAcCompatibilityContractTest` 主要是注册/资源/字符串检查，不能证明实体、伤害、费用、配方运行行为。
- 既有 `AerohandGameTests` 分离器直接调用detonate绕过施术时序，摔落只断言上限没有有效伤害下限；须补正反断言。
- `LiquidShadowMovementGameTests` 使用真实世界tick和具体几何/CP断言，有正反作用；mock owner补发tick事件是测试夹具约束，不能据此称整个场景伪造。
- `MagManipTransactionData` 的 deprecated `proveOrphan/quarantineCarrier/emitRecoveryItem/recover/acknowledgePickup/retryIssued` 等为退役恢复协议入口；需按调用图明确未调用兼容面，不能把恒返回当正常恢复覆盖。现用prepare/activate/settle/resume仍需独立事务测试。

## 验证状态

前批主源码/auditTest由主任务报告编译通过。冻结复核发现EX-21后，主任务明确要求优先修复该P0及EX-22至26；这些追加修改与9项ExtraParityDeepAuditGameTests已落盘，将作为新快照统一编译/运行，不能沿用前批编译成功结论。未自行运行Gradle、游戏或生产环境。文中“已改”只描述落盘实现，“待下一修复批次”不计为已修复；没有将源码字符串断言视为运行证据。

新增 `ExtraDeepAuditGameTests` 27项：硬币加入取消与离线清理2项、空气弹加入回滚/真实飞行/延迟命中/移动躲避/基岩阻挡5项、喷射/降温2项、纸飞机付费取消与持纸不训练2项、水人桶回调1项、分离器延迟单次脉冲1项、钩/飞机掉落取消恢复2项、载体状态注册名恢复1项、巡航弹真实追击与墙阻挡2项、激光矿石收获/取消/换基岩3项、长矛脆块收获/保护取消2项、纸钻/纸甲有据返还2项、宽盒满包传输/拾取取消2项。LiquidShadow固定AI另由专属审计测试覆盖。

已清除数学测试中的虚构分离器蓄力与airflow间隔helper，将实际火山球伤害衰减、摔落cap与绝缘减伤连接到被测试公式。既有满包传输测试更新为材料守恒、真实脚底位置和有偿动作，避免继续固化旧偏差。仍保留注册/资源/源契约测试作为静态补充，不以其替代上述运行测试。

追加 `ExtraParityDeepAuditGameTests` 9项：真实落地放置重入/取消/回调换块3项、真实数字技能指令1项、真实铁砧附魔正反1项、纸飞机脆块/保护/持续飞行3项、念力猛击首tick与30tick二段1项。本域新测试合计36项，尚待集成实跑结论。

CP药水追加测试1项，本域新增GameTest合计37项。EX-21为物品复制P0，已修但仍需本批实际运行证据确认。

追加恢复/边界3项，本域新增GameTest合计40项（ExtraDeep27，ExtraParity13）。该窄补丁已冻结供最终编译/运行。


### 隔离运行反馈（gt-r3）

主任务在Vicerach运行424项，报告12项全域失败；本域2项为 `slamdamagesatfirsttickandthirtiethtick` 与 `coolingextinguishesbutnetheronlycoolsquarter`。证据：`evidence/audit-20261025/gt-r3/latest.log`，对应569/883行。其他本域新旧用例通过；EX-28/29追加的3项尚未进入该快照。

- Slam夹具把1.4高的Cow放在玩家同脚底，水平1.62眼高射线穿过目标上方，真实SkillRaycast拒绝施术；目标已抬高.7，保留真实首tick/30tick断言。
- 下界夹具单独创建ServerPlayer后没有设系和等级，默认最大过载100；setCurrentOverload(200)先夹到100，再减正确的200/4=50，实际50并非公式错误。已设置Aero/Lv5，且新增两维度施术前OL=200断言；仍要求下界结果150且不灭火。固定上游AirCooling.java的200→800和下界/4与生产实现相符。

这两处只修夹具，未放宽行为断言；待下一实际运行复核，不把r3失败记录改写为通过。
