# 0.0.25 超能力后续审查与修复

基线为完整冻结的 0.0.24-scex-auditfix 源码 ZIP（SHA-256 `3fdb66fe85ac63c3a54b16201f95e4f3d9299043bc14e4a3ceb455f1bc663aac`）。旧目录、源码包与 JAR 保留。
构建沿用 Minecraft 1.21.1、NeoForge 21.1.248、Java 21.0.12、Gradle 9.2.0、ModDevGradle 2.0.141、Parchment 2024.11.17。
协议 v16、能力存档 v4、已有充电输出 2.5 倍与资源身份保持不变。

## 行为与修复范围

| 技能 | 已确认的问题与处理 | 与上游的关系 |
|---|---|---|
| 危险传送 Threatening Teleport | 未命中时，远端掉落被拒绝会丢物并收费。检查远端和玩家身边两次生成结果；全部失败返还弹药、CP、过载、成长和恢复延迟，不授予熟练度。命中后的回收掉落失败只返还物品，攻击仍收费。 | 保留最终 1.12.2 的隔墙选取目标、未命中必定掉落、命中后 30% 回收、消耗及伤害公式；补现代取消事件的处理。 |
| 隔空取物 Psycho Transmission | 拾取被 Pre 事件拒绝后，旧 refundDynamic 只退 CP/过载，残留上限增长。使用 0.0.24 的付款快照回滚第二笔拾取消耗，保留每 tick 维护消耗及回调内额外付款。 | ExtraAcC 扩展，不属于 LambdaInnovation 主仓能力台账。 |
| 隔空取物背包预检 | 旧遍历把空盔甲栏和副手栏算成空位。按实际 Inventory.add 规则，检查主背包空位、现有可合并栈；副手允许合并，损伤物品必须有主背包空位。 | API 适配修复，实际拾取仍经 ItemEntity.playerTouch 和 NeoForge 事件。 |
| 突击喷射 Jet Engine | 旧实现只检查每 tick 的终点，能跨越薄墙。按完整玩家 AABB 沿直线扫过每个方块 VoxelShape 的子盒；检查高度、边界和碰撞查询所需驻留区块，拒绝横向异常长距离扫描。 | 上游使用客户端直接坐标运动；服务器权威实现增加碰撞与边界保护，保留 16 tick / 8 的最终位移、费用和伤害。 |
| 突击喷射生命周期 | 伤害回调同步终止上下文后，旧 tick 又把它写回。继续输出特效/推进状态前检查当前状态身份；重复启动沿用第一次捕获的行走速度。 | 修复服务端实现的重入错误；正常结束、登出、换维度、死亡和停服处理仍由原生命周期入口负责。 |
| 念力投掷 / 念力刺击 | 返还掉落被取消会丢弹药；重建默认 ItemStack 会擦除原组件。按单件复制原栈，保留组件；检查远端/身边生成，全部拒绝时恢复已扣弹药。未命中且无法返还时完整回滚付款；已执行攻击不退伤害费用。 | ExtraAcC 扩展的现代返回物品事务修复。既有命中、伤害、击退、冷却、默认拾取延迟和创造模式弹药类型保留。 |

## 对照源码与 API 依据

- [AcademyCraft 固定上游](https://github.com/LambdaInnovation/AcademyCraft/tree/7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9)：`ThreateningTeleport.java` 与 `JetEngine.scala` 的消费顺序、射线选择、30% 概率和 16/8 运动。该版本是 Minecraft 1.12.2 最终快照。
- [ExtraAcC 固定上游](https://github.com/NullaDev/ExtraAcC-1.12.2-/tree/d66a190e3ae00d9ca8c154b47fca0509d4c171f7)：念力技能与投掷物品来源。原始 projectile 实现与当前即时射线重建仍有表现/物理差异，本轮未宣称复刻所有投射物动力学。
- [NeoForge 1.21.1 GameTest 文档](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)：测试注册、模板、批次和成功断言。新探针单独打包，发行 JAR 不包含 `src/auditTest`。
- 实际 `neoforge-21.1.248-sources.jar` 中的 `ItemEntityPickupEvent.Pre/Post` Javadoc：仅逻辑服务端触发；`setCanPickup(TriState.FALSE)` 阻止拾取，Pre 不使用取消事件接口；允许修改原栈但禁止事件内 `setItem`。沿 `ItemEntity.playerTouch` 核对 Pre → Inventory.add → Post → discard 顺序和部分拾取行为。
- 同版 `Inventory.getFreeSlot/getSlotWithRemainingSpace/add` 与 `Player.hasInfiniteMaterials`：主背包空位、已有副手栈合并、损伤栈和无限材料分支。
- 同版 `CollisionGetter.getBlockCollisions/noCollision`、`BlockCollisions`、`VoxelShape.toAabbs`、`AABB.expandTowards/inflate/clip/contains`：碰撞形状已是世界坐标；搜索包含周围突出形状，未驻留区块可返回空。先验证驻留区块，再做扫描；盒膨胀采用半个身体尺寸与 1e-7 接触容差，允许贴地运动。没有以原版逐轴运动解析替代直线扫掠。
- 同版 `ServerLevel.addFreshEntity` 返回实际实体管理器接纳结果；失败时不能认为物品已经进入世界。ItemStack 单件副本保留组件；两个拒绝分支分别检查返回值。
- 同版 `LivingIncomingDamageEvent` Javadoc 与技能生命周期调用：伤害处理中的同步回调可改变技能上下文。测试通过真正的伤害事件触发生命周期入口，再检查下一次 tick。
- 同版 `GameTestHelper.makeMockServerPlayerInLevel` 标记待删除且固定 `isCreative=true`。本轮探针沿其 EmbeddedChannel/Connection/CommonListenerCookie/PlayerList 建立方式创建普通 ServerPlayer，明确断言生存模式；未执行真实客户端连接。继承的历史探针维持原样。

本轮修改只在已有服务器技能或测试服务器线程执行；没有新增客户端状态、跨线程队列、区块持有票据或持久化字段。单次攻击同步检查物品生成结果；Jet Engine 状态保持既有 16 tick 生命周期与清理事件。

## 验证记录

Vicerach 的真实 NeoForge GameTestServer 已完成 **321/321** 必需用例（301 项继承回归 + 20 项本轮新增），退出码 0，未触发强制终止，全部维度正常保存。普通 ServerPlayer 的旧版负对照为 **14/14 触发预期缺陷**（游戏进程退出码 14，验证器确认预期后退出 0）；失败均来自实际行为断言，无类/方法加载错误。精确产物哈希、命令、回执与独立源码包复建记录见配套 DELIVERY.md。

现有 343 项 JUnit/资源与源码契约检查通过。本轮新增 20 项真实 NeoForge GameTest，覆盖薄墙/斜向运动、高度、贴地、16/8 位移、重入终止、重复速度捕获、物品取消/回退/组件、隔墙命中、取消拾取的成长回滚、额外付款与副手部分拾取。
14 个缺陷断言也针对冻结 0.0.24 运行作为负对照；须区分预期的旧版行为失败与测试加载/夹具失败。
初次新增用例 18/20 通过，两项夹具问题是危险传送未学习技能，以及射针目标低于视线；修正后采用普通 ServerPlayer 重新验证。
测试启动脚本在共享客户端文件锁忙时有界等待五分钟，获取锁后才检查 6 GiB 启动余量；Java 运行仍受 300 秒时限与 2.5 GiB 内存下限约束。最初窗口竞争导致的一次未启动记录保留为 NOT_RUN，不计为玩法失败。

没有执行真实客户端画面、多玩家往返、重连、死亡复活全过程或生产整包兼容验收。GameTest 的同步生命周期回调不能代替上述验收。修改与测试仅用于独立候选，未公开发布或部署生产。
