# 机器、能源、能力接口与菜单审计

审计基线：`ef1617b`。唯一修改工程：`academycraft-deep-audit-0.0.29-20261025`。本报告在实现修改前建立；2026-10-25 根协调已允许按总台账进入修复。固定本体参考：`AcademyCraft-7b1401c` / `7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9`。本域不运行 Gradle 或游戏，编译与实际 GameTest 结果由根协调汇总。

## 调用链与已确认边界

- `WirelessSystem.onServerTick → WiWorldData.tick → WirelessNet.tick / NodeConn.tick`：节点/机器余额在服务端修改；已有有界数值、重连去重和移除时解绑。SavedData 实例隶属于维度数据存储，未发现本域静态集合持有 ServerLevel。
- `Block.getTicker → BE.tick → EnergyItemHelper / tank / recipe → ContainerData`：全体机器菜单已有增量数据槽，大于 signed-short 的节点/开发机能源拆为双字；不必为 GUI 每 tick 广播完整 BE NBT。
- `Block.onRemove → dropAndClearContents / 主结构清理 → 无线解绑`：常规容器采用服务端一次掉落后清空；开发机代理核对 UUID、类型及旋转后真实占位，防止伪造远程 mainPos 拆除无关机器。
- 开发机固定上游 `TileDeveloper.use/unuse` 仅维护使用者及 GUI 消息，没有骑乘实体、mountEntity 或座椅。当前也只建立开发会话，不应凭任务措辞添加新的骑乘机制；机器删除时学习会话中止由网络域联合验证。
- AIM 干扰器每 10 tick 支付脉冲，机器自身没有每 tick AABB 扫描；`AbilityInterferenceService` 查询已付费源。持续施法中断与维度卸载索引清理由技能/网络域审阅。

## 确定问题台账

行号是基线定位，修改后以符号名为准。没有把仅可疑的第三方错误回调升级为玩家可利用 P0。

| ID | 等级 | 基线证据及触发 | 结果 / 修复方向 | 状态 |
|---|---|---|---|---|
| M01 | P1 | `DevNormalBlockEntity.setEnergy:47` / `DevAdvancedBlockEntity.setEnergy:112`，无线每 tick 注入、开发持续耗能 | 每次余额变动标脏和 `sendBlockUpdated`；GUI 已有 ContainerData，改为合并持久化标记、有限频率世界同步 | 已实现；Monarch 全套中本域测试通过 |
| M02 | P1 | `SolarGenBlockEntity.tick:62`、`BaseNodeBlockEntity.setEnergy:151`、`WindGenBaseBlockEntity.tick:81` | 太阳能变化每 tick 全量广播；节点同一整秒 tick 多个调用重复发包且无变化也标脏；风机净储能不变但电池变动没有持久化标记 | 已实现；Monarch 全套中本域测试通过 |
| M03 | P1 | `VBlocks.VBlock.get:54`，VWMatrix/VNNode/VNGenerator/VNReceiver 构造 `ignoreChunk=true`；`rangeSearch→hasLoadedEndpointWithin→matrix.get`、`unlinkUser→getNode`、`detachNodeOnRemoval→getMatrix` | 发现/解绑/界面路径能访问已卸载端点并同步加载区块；全部虚拟查找先检查已加载，缺端点保留拓扑待加载 | 已实现；Monarch 全套中本域测试通过 |
| M04 | P1 | `WindGenMainBlockEntity.checkFanSpace:238`，跨区块 15×15 净空平面直接 getBlockState；代理/结构查询同类调用 | 活跃风机可读取并加载邻块；净空扫描遇卸载块暂停，不将未知结构当作已破坏；菜单有效性检查也应在 getBlockEntity 前短路 | 已实现；Monarch 全套中本域测试通过 |
| M05 | OPT | `EnergyBridgeOutputBlockEntity.serverTick:36` 每 tick 六方向查询 FE | 使用 NeoForge 21.1.248 BlockCapabilityCache，校验 owner 生存期，拆装/失效后重新查询，保持精确 1 IF = 4 FE | 已实现；Monarch 全套中本域测试通过 |
| M06 | P1 | `PhaseGenBlockEntity.tank:31` / `ImagFusorBlockEntity.tank:43` 普通 FluidTank 直接作为公开流体能力 | 外部 fill/drain 没有 onContentsChanged→setChanged；满能量或闲置机器的流体变动可不落盘；加实际变动持久化回调，模拟操作无标记 | 已实现；Monarch 全套中本域测试通过 |
| M07 | P2 | `SolarGenBlockEntity.tick:43,56`、`getStatus:137`，固定上游 TileSolarGen / TileGeneratorBase | 遮光提前 return，已有储能不能继续给电池；一次充电未限制 100 IF/t；遮光状态仍显示 STRONG。修正后保留 3 / 0.6 IF/t 和 1000 缓冲 | 已实现；Monarch 全套中本域测试通过 |
| M08 | P2 | `SolarGenBlockEntity.loadAdditional:113-116`；IFEnergyStorage 整数视图调用 float机器 setEnergy | 保存的 storedEnergy 小数随后被 if_energy 整数覆盖；FE 整数交易也抹去小数。保存载入和 FE 增减保留小数余量 | 已实现；Monarch 全套中本域测试通过 |
| M09 | P2 | `ImagFusorBlockEntity.tick:131` 用 ceil(剩余容量) 从电池实扣，再 inject 截断 | 例如剩 0.25 IF 却扣 1 IF，丢失 0.75；改为可接受整数额度，测试近满容量守恒 | 已实现；Monarch 全套中本域测试通过 |
| M10 | P1 | `MetalFomerBlockEntity.isActionBlocked:201` 只比较 item；SidedItems:243/269 空槽未按容量截断、负数抽取会 grow | 不同 DataComponents 的产物错误合并；自动化接口边界不满足守恒。复用熔炉的组件与剩余容量规则，负数/模拟/超量输入明确拒绝或返还 | 已实现；Monarch 全套中本域测试通过 |
| M11 | OPT | `WiWorldData.tickNetwork/tickNode` 遍历所有注册图；`isDirty:319` 永远 true；WirelessNet 和 NodeConn 即使0转移仍调用 setEnergy | 所有卸载网络每 tick 仍处理，SavedData 每轮保存重写；区分活跃/休眠工作并只在真实拓扑/缓冲变化标脏，不缩减原版每tick带宽 | 已实现；Monarch 全套中本域测试通过 |
| M12 | P2 | `MetalFormingRecipe.matches:106` 未检查 inputCount；`MetalFomerBlockEntity` 当前任务不核验 RecipeManager 重载 | 数量不足的配方被选中后反复启动/停止；数据重载时旧引用可继续执行。数量纳入匹配，缓存按管理器/配方身份失效 | 已实现；Monarch 全套中本域测试通过 |

补充确认并实现 M13（P1）：开发机代理 setter 在完成身份配置时调用 `invalidateCapabilities`，修复提前缓存 null；`ForgeEnergyView` 加有效性回调，代理/主机移除或 UUID 不再匹配时旧 view 不再读写能源。风机上层代理也校验下层同一主机。`AcademyCraft` 只调整这两个能力适配方法。

跨区块拆除不会强制加载剩余代理。M04 同时加入主机/代理 `onLoad → scheduled tick` 完整性检查；缺邻区块时保留并延后，加载后清理孤儿代理或损坏主机。开发机 NBT 的远程伪造主坐标不会产生跨区块加载或拆除。

## 固定上游数值核对

| 机器 | 固定上游 | 当前基线 |
|---|---|---|
| 节点 basic / standard / advanced | 15000/50000/200000 IF；150/300/900 带宽；9/12/19 范围；5/10/20 连接 | 一致 |
| 矩阵 core L=1..3 | 8L 连接；60L² 带宽；24√L 范围；三个约束板 | 一致，运行值来自真实槽位 |
| 开发机 normal / advanced | 50000/200000 IF；100/300 带宽；20/15 TPS；700/600 CPS | 一致；开发每次刺激的 +1 tick 保留由现有规则说明 |
| 太阳能 | 1000 IF；100 IF/t 传输；3 晴天、0.6 雨天；受日间/遮光限制 | 生成值一致，遮光/充电偏差见 M07 |
| 风机 | 20000 IF；300 IF/t；高度70..160线性7.5..15；8..40支柱；15×15净空每10tick | 数值一致；基座仍每tick重复扫塔，卸载边界见 M04 |
| 相位发电机 | 6000 IF；50 IF/t；每tick100mB×0.5 IF/mB；8000mB罐 | 一致 |
| 虚数熔炉 | 2000 IF；50 IF/t；120tick×12 IF；8000mB罐 | 一致；物品/流体实际扣除受配方检查 |
| 金属成型机 | 3000 IF；50 IF/t；60tick×13.3 IF；5tick搜索 | 一致 |
| 猫引擎 | 2000 IF；200 IF/t；500 IF/t生成 | 一致 |
| AIM | 10000 IF；50 IF/t；范围10..100；每10tick支付range² | 数值一致；上游能量恰好等于花费时直接关闭的旧边界已由本移植有意改为完整预付脉冲 |

审阅了资源下 35 个机器配方（包括 5 个扩展融合和刻蚀圆石）；实际生产机器使用 RecipeManager，`ImagFusorRecipes`/`MFIFRecipes` 是不再参与机器执行的历史兼容表，未据此误报缺失配方。

## 测试审计与修复验收

现有 `EnergyMachineAdversarialGameTests` 验证伪造代理、非法UUID、分数注入和替换解绑；`WindGeneratorGameTests` 使用真实放置/菜单/跨区块结构；`AbilityInterfererGameTests` 验证支付、白名单、移除及能量不足。它们具有实际断言，但不覆盖发包预算、区块卸载不加载、流体能力标脏、太阳能遮光充电或成型机组件拒绝。

`MachineMenuContractTest` / `ImagFusorOutputContractTest` / `EnergyBridgeParityContractTest` 有源码字符串断言，仅作为代码形状检查，不能证明行为；新增 `MachineDeepAuditGameTests` 必须通过真实 BE、Capability、容器数据、持久化标签与存活区块检查，包含正反断言。根协调已完成构建及 Monarch 真实服务端 430/430 项，包含上述新增机器行为测试；源码契约不代替这些运行结果。

详细范围见同目录 `machines-reviewed-files.txt`；每个列出的主源码文件均已读取（重复注册壳也已检查 ticker/onRemove），并沿上述调用链追到接口注册和现有相关测试。

## 本批实现与验证边界

- `setChanged()` 本身不规定“每 10 秒存盘”。本批修复的是重复标记、比较器邻居通知和完整 BE 广播。`markMachineChanged()` 在驻留区块 clean→dirty 时立即调用它；同一脏区块后续保存读取最新字段，未建立延迟 20 tick 才落标记的丢数据窗口。Minecraft 的 ChunkMap 先保存再调用卸载，因此只在卸载回调补标记不可靠。
- NeoForge 21.1.248 `BlockCapabilityCache` 的失效回调不查询世界。拥有者移除/卸载清空相邻缓存；目标身份替换由框架缓存失效刷新。框架 `isLoaded/hasChunk` 可在 FULL 尚未就绪时为真，所有本批机器读取使用 `LoadedMachineChunks` 的 `ServerChunkCache.getChunkNow` 驻留检查；RF 缓存访问前也执行同一检查。
- 无线图按拥有者区块建立索引，Load/Unload 仅增删该区块活跃集合。只初始化绑定时遍历历史图；每 tick 处理活跃拥有者，节点/矩阵实际带宽和顺序仍保持原每 tick 运行。SavedData 仅对拓扑/缓冲修改标脏；NBT 恢复时重建索引，休眠拓扑仍保存。
- `MachineDeepAuditGameTests` 新增 12 项：太阳能遮挡/带宽/小数存读与模拟；风机净零余额的电池保存；外部罐填充/排空标脏；熔炉近满电池守恒；成型机超量/负量/模拟抽取；输入计数/组件拒绝/失效配方；RF 邻居替换与卸载清缓存；开发机 cached-null 与失效 view；远处无线查询不加载；实际无线转移与区块事件暂停/恢复；200 次能源修改的真实 ChunkHolder 广播队列预算及 NBT 末值；SavedData 图恢复和休眠索引。
- 广播预算通过真实 `ServerLevel.sendBlockUpdated → ChunkHolder.changedBlocksPerSection` 正控确认观察点，再断言 200 次开发机余额变动无整块广播排队；不是源码包含字符串测试。GUI ContainerData 原有增量路径保留。
- 卸载测试覆盖实际 BE 卸载回调和 ChunkEvent 处理路径，不强制卸载共享 GameTest 所在区块；完整磁盘保存→物理卸载→重新加载的隔离场景仍由根协调验收。源码通过不等于行为实跑通过。
- 本代理未执行 Gradle、游戏或发布。根协调执行的 Monarch `gt-r1` / r10 为 430/430 通过、59.28 秒、exit 0、forced=false；证据收件位置为 `evidence/audit-20261025/monarch-gt-r1`。`git diff --check` 未报空白错误。

## Vicerach gt-r3 首轮反馈

根协调回收 `evidence/audit-20261025/gt-r3/latest.log` 后，机器域两个失败已定位并修正夹具，实质守恒/存盘断言保持：

- `MatrixNetworkFlowGameTests.privateMatrixInitializesDiscoversTwoNodesAndTransfersBridgeEnergy` 只放矩阵主块，没有建立七个代理。新增的恢复时结构校验正常拆除了不完整机器及其网络，原测试 80 tick 后还对旧对象求和，得到 910 IF。现夹具调用实际 `Matrix.createStructure`，并在求和前断言同一主块与同一网络仍存活；1000 IF 守恒、持久缓冲给空节点供能和满缓冲边界断言均保留。Monarch gt-r1 已通过完整守恒与缓冲断言。
- `MachineDeepAuditGameTests.shadedSolarChargesWithinBandwidthAndPersistsFraction` 在同 tick 放屋顶后立即读取现代异步 skylight，尚未传播的旧光照让面板仍显示发电。上游本身依赖 `canSeeSky(pos.above())`，不把它替换为语义不同的高度图。现 GameTest sequence 先等待真实天空光遮挡条件成立，再在同一回调设置 500 IF/空电池并执行一次机器 tick；继续要求只转移 100 IF、剩余 400 IF、399.6 IF 小数 NBT 和模拟/负量边界。

Vicerach gt-r4 因内存窗口不可用而未启动、零测试；随后由根协调顺序回退 Monarch gt-r1，430/430 全通过，以上两项夹具修正已实际复测通过。
