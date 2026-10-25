# 单元测试补充深审

日期：2026-10-25。仓库基线：`ef1617b2c13cb9eedf20ca6d11d3085a9471f59a`。

本次全读 `.gradle/unit-review-gaps.txt` 中尚未登记的 55 份 JUnit 文件，逐项区分源码约束、资源约束、生产纯函数、生产状态机和生产解析器。清单见 `test-suite-reviewed-files.txt`。本报告补充已有逐域审计，不将这 55 份文件称为整个测试集，也不以源码字符串匹配代替实际运行行为。

## 已确认并修复的测试问题

| 编号 | 原测试缺口 | 修复与覆盖边界 |
|---|---|---|
| TS-01 | 六个文件内七处 `indexOf(left) < indexOf(right)` 没有确认左 token 存在；删除左 token 后 `-1 < 正索引` 仍成立 | 七处均先断言两端索引非负再比较顺序。仍是源码顺序约束，不证明事件实际发出、UI 实际更新或方法必被执行 |
| TS-02 | `RegularMachineLayoutTest` 的 `left - 20 + 18 <= left` 恒真；另有全由测试常量计算的 sidebar/canvas，以及不使用生产槽位输出的关系式 | 删除无生产依赖的断言。改调用真实 `developerCanvasLeft`、`developerSidebarLeft`、`developerMenuLeft`，检查三种视口内的边界、不重叠和 640 宽独立坐标锚点。实际屏幕控件仍由客户端 probe 验证 |
| TS-03 | `TutorialUnlocksTest` 用 Set 重载测试去重和历史迁移；这两个重载复制真实实现，生产 `PlayerAbilityData` 入口并未委托它们 | 删除仅供测试使用的两个生产 Set 重载；JUnit 保留真实映射。新增 `TutorialUnlockDeepAuditGameTests`，直接取得已加入服务器玩家的能力附件，检查去重、未知/null 项、历史迁移、幂等、保留历史与不虚构默认教程 |
| TS-04 | `LootTableResourceContractTest` 的 `Files.list` 流未关闭 | 使用 try-with-resources；这属于测试资源生命周期问题，不是掉落行为证明 |
| TS-05 | 两张宝箱表只检查存在一个 `"weight": 4`，却声称全部六种因子/三种媒体等权 | 改用 Gson 解码，逐条检查物品类型、唯一 ID、完整物品集合、条数和每条权重 4，并检查对应池的概率。仍不替代真实生成/开箱统计 |

TS-01 涉及以下七组生产 token：

| 测试文件 | 左 token → 右 token |
|---|---|
| `api/event/LegacyObservableEventContractTest.java` | `data.setTerminalInstalled(true)` → `new TerminalEvents.Installed` |
| 同上 | `data.installApp(getAppId())` → `new TerminalEvents.AppInstalled` |
| `api/event/SkillExpMutationCoverageTest.java` | `new AbilityEvents.SkillExpChanged` → `new AbilityEvents.SkillExpAdded` |
| `client/AbilityLoopLifecycleContractTest.java` | `acceptChargingState` → `setChargingState` |
| `client/JadeCompatibilityContractTest.java` | `s = processKeyTags(s)` → `ITALIC2_PAT.matcher` |
| `client/SkillRegistryIntegrityContractTest.java` | `bindEffects();` → `validateBindings();` |
| `terminal/TerminalAppResourceContractTest.java` | `register(ABOUT)` → `register(SETTINGS)` |

已做有界内存反证：读取当前七份对应生产源码，只在内存删掉每组左 token，再执行旧比较表达式；七项旧判断均仍为 true，新双端存在判断均为 false。删除右 token 时新判断同样为 false。对两张实际 JSON，只在内存将第一项权重从 4 改为 9；原物品存在/至少一项权重为 4 的判断仍通过，逐项权重判断拒绝。没有改写生产输入文件，也没有把这一检查冒称为完整 JUnit mutation run。

## 55 份文件逐项结论

下表路径均相对 `src/test/java/com/mohistmc/academy/`。生产纯函数表示调用了实际被生产消费的方法，但不代表服务器事务、实体或网络已运行。

| 文件 | 实际检查类型与作用 | 不能据此声称的覆盖 / 审计结论 |
|---|---|---|
| `BuildProvenanceContractTest.java` | 源码/元数据：版本、来源键、构建信息生成及打包允许名单 | 不是最终 jar 哈希、部署身份或真实运行证明 |
| `advancement/LegacyAdvancementResourceTest.java` | 资源/源码：56 个成就文件、ID 唯一、父文件存在、双语键及桥接入口 | 方法名中的 valid/reachable 过宽；未完整 JSON 解码、检环或验证授予事件，父文件存在不等于整图可达 |
| `api/event/LegacyObservableEventContractTest.java` | 源码：可观察事件形状、可取消事件与变更的文本先后 | TS-01 两处已修；不证明真实事件回调、取消或结算 |
| `api/event/SkillExpMutationCoverageTest.java` | 源码：扫描经验变更入口绕过、实体引用边界及服务事件顺序 | TS-01 已修；不证明经验量、事件次数或持久化 |
| `capability/ExternalEnergyConversionTest.java` | 生产纯函数：4 FE/IF 换算、取整、负数和饱和；生产能量存储消费这些函数 | 不证明第三方存储实际交互及事务守恒 |
| `client/AbilityLoopLifecycleContractTest.java` | 源码 + 生产决策函数：音效所有权、重置与扫描批次约束；ChargingHandshake 接受/重复/乱序/超时分支 | TS-01 已修；只有实际决策函数属于行为测试，其余没有运行声音或整个会话 |
| `client/AuditLanguageParityTest.java` | 资源解析：Gson 读三语 JSON，键、占位符、空值及替换字符检查 | 不证明语言质量或所有 printf 格式语法；资源 pack 与实际 UI 仍另验 |
| `client/ChargePresentationStateTest.java` | 生产状态机：新状态实例接受、时间推进、校正、超时、清除和边界；HUD 使用此状态 | 不证明网络包接收与真实渲染 |
| `client/ClientFatalPathContractTest.java` | 源码/资源：快照边界、退出清理、展示寿命语句及资源存在 | 不等同真实 GUI 生命周期、空数据点击或异常退出 |
| `client/ClientRegistrationBusContractTest.java` | 源码：客户端注册所在总线、13 个菜单关联 | 不等同客户端加载成功、专用服类加载边界或实际打开菜单 |
| `client/FreqTransmitterClientContractTest.java` | 源码：nonce、取消、范围与包不携带 BlockPos 的结构约束 | 未实际触发 Esc、发包或服务端并发响应 |
| `client/HudConfigContractTest.java` | 源码：配置项引用到 HUD/音效/编辑器消费点 | 不证明切换立即生效、屏幕位置或实际静音 |
| `client/JadeCompatibilityContractTest.java` | 源码：可选 Jade 偏移、代理能力、名称、Markdown 处理顺序 | TS-01 已修；不证明装/不装 Jade 的真实启动与叠层结果 |
| `client/LegacyInputContractTest.java` | 源码：旧键位/鼠标常量、阈值与 active 判断 | 不证明真实 KeyMapping、输入焦点或游戏事件行为 |
| `client/LegacyPresentationContractTest.java` | 资源/源码：动画帧存在、循环、声音和渲染入口文本 | 不证明画面帧、时序、远端观察者同步 |
| `client/LegacyPresentationPhase4ContractTest.java` | 资源/源码：客户端限定、粒子预算/寿命语句与 mark/blood 帧资源 | 不证明真实 GL 绘制、粒子数量或生命周期 |
| `client/LocalizationResourceContractTest.java` | 资源/源码：六语指定键和界面翻译键引用 | 多数为字符串检查；不证明 JSON 全结构、翻译正确性或实际文字显示 |
| `client/MachineLoopSoundContractTest.java` | 源码：两种循环声音的 alive、reset、音量和距离分支 | 不证明卸载后音频实际停止或音量可听结果 |
| `client/MediaResourceContractTest.java` | 资源/源码：实际媒体文件大小、三个不同 SHA 和播放/暂停/外部目录路由 | 不证明 Ogg 可解码、歌曲内容或真实播放 |
| `client/ObjTextureUvContractTest.java` | 资源：ImageIO 解析 14 张贴图尺寸，MTL 指令检查 | 不证明像素内容、UV 朝向或模型实际显示 |
| `client/SkillRegistryIntegrityContractTest.java` | 资源解析/源码：图标实际像素与尺寸、技能声明数、重复/缺 effect 验证代码及空入口限制 | TS-01 已修；不运行整个技能注册与施术，不证明伤害或技能效果 |
| `client/SoundResourceContractTest.java` | 资源/源码：匹配音效名对应 Ogg、数量下限、终端播放路由 | 不完整解码声音 JSON/Ogg，不证明实际可听 |
| `client/block/entity/render/MatrixFacingTransformTest.java` | 生产几何函数：反射调用实际朝向角，与 MC BlockModelRotation/PoseStack 的四方向顶点独立比较 | 有独立参考，不是重复同一公式；仍不证明整模型实际绘制 |
| `client/block/entity/render/MatrixRenderContractTest.java` | 资源/源码：OBJ 分组及面数、静态/动态模型可见性、轨道/core 和同步入口 | 不证明完整网格、动画姿态或服务端到客户端同步 |
| `client/block/entity/render/PhaseLiquidVisualMathTest.java` | 生产纯函数：实际距离透明度在 0/25/100 与负数、NaN、无穷的边界 | 不证明实际 shader/blend、光照或液体画面 |
| `client/gui/DevLearningFeedbackContractTest.java` | 源码：拒绝消息、nonce、能量刷新与禁用/课程时间约束 | 不证明实际回应、按钮状态和网络时序 |
| `client/gui/DevMachineLayoutTest.java` | 生产布局函数：格式化与大量视口边界、compact 组合及不重叠 | 有实际生产输出依赖；不证明字体真实尺寸、控件绘制/点击 |
| `client/gui/DevMachineUiContractTest.java` | 源码：分层、布局调用、包/重置/关闭菜单路径 | 不证明交互和真实关闭生命周期 |
| `client/gui/LegacyMachineScreenLayoutContractTest.java` | 源码：页面布局、菜单权威数据、代理页面、密码遮罩和草稿回执关联 | 不证明真实渲染、隐私遮罩像素或草稿包乱序 |
| `client/gui/RegularMachineLayoutTest.java` | 生产布局函数：machine/info/sidebar 和 developer canvas/menu 输出 | TS-02 已修；移除测试自身常量恒真，实际屏幕由 GUI probe 承担 |
| `client/gui/RenderUtilsTextureSamplingContractTest.java` | 资源/源码：图片实际尺寸和共享采样/比例入口 | 不证明渲染结果或资源包重载后的采样 |
| `client/gui/SkillSelectionUiContractTest.java` | 源码：零 CP 可分配判断、旧版坐标及单父边展示约束 | 不实际点选/分配或绘制树 |
| `client/gui/SlotArtworkAlignmentTest.java` | 资源 + 生产布局常量：36 槽位置与真实图片不透明边框比较 | 有独立像素参考；不证明屏幕缩放后的点击/显示 |
| `client/gui/WirelessMachineUiContractTest.java` | 源码：UI 状态、截止时间、分页、认证和实际 gate 的存在/调用文本 | 即使方法名含 realClientGate，也只读取 probe 源码；不能算执行 probe |
| `client/media/ExternalOggInspectorTest.java` | 生产解析器：用最小双页 Ogg 输入流验证 48 kHz/双声道/2 秒及坏头、超长负例 | 页为合成元数据样本，不是完整可解码 Vorbis；未验证 Path 包装入口全部文件限制 |
| `command/LegacyAchievementCommandTest.java` | 生产纯函数：实际旧成就 ID 解析、未知 ID 与 56 项集合 | 不执行 Brigadier 或授予成就 |
| `command/LegacyAimCommandTreeTest.java` | 源码：命令字面量、参数、权限、事件和重置路径 | 注释已区分独立实际 GameTest；源码检查不算执行命令 |
| `config/DynamicSettlementSourceContractTest.java` | 源码扫描：禁止技能绕过动态 CP/OL/经验边界的指定文本模式 | 有用的绕行约束，但不运行原子结算/回滚；注释或新写法可能绕过匹配 |
| `config/DynamicSkillRulesTest.java` | 生产纯函数：倍率、无效数值与旧式/命名空间维度名单 | 不证明完整施术结算与维度加载 |
| `config/LegacyAbilityRulesTest.java` | 生产配置解析/纯函数：1.0.7 默认、坏条目和 CP/OL 曲线端点；数据消费恢复函数 | 不证明完整玩家 tick 的恢复延迟时序 |
| `energy/EnergyBridgeParityContractTest.java` | 源码/资源：桥接方向、带宽入口、两张贴图 SHA、配方/掉落/教程存在 | 不执行真实 FE/IF 转移，也不证明第三方回调守恒 |
| `energy/impl/EnergyBoundaryTest.java` | 生产纯函数：非有限数归一化、上限、饱和加法与比率；网络/节点使用这些方法 | 不证明真实恶意提供者回调或完整网络结算 |
| `gametest/GameTestTemplateIsolationContractTest.java` | 资源解析：读取压缩 NBT，确认模板最低尺寸 64×16×64 | 方法名声称 boundsEveryRealTestFixture 过宽；没有扫描实际 fixture 范围，不是全套世界隔离证明 |
| `terminal/TerminalAppResourceContractTest.java` | 资源/源码：默认 app、注册顺序、实际构建资源 SHA、教程帧 | TS-01 已修；不运行安装、反序列化和启动 app |
| `tutorial/TutorialResourceContractTest.java` | 资源/源码：Markdown 结构、key/image 引用、路径边界、UTF-8、裁剪/缓存/配方/解锁调用 | 不实际执行 Markdown 排版或缓存生命周期 |
| `tutorial/TutorialUnlocksTest.java` | 生产映射：九个条件教程的代表物品与未知物品 | TS-03 已移除复制算法覆盖；去重/迁移改由新增真实附件 GameTest 验证 |
| `world/CreativeInventoryContractTest.java` | 源码/资源：指定注册格式的物品/方块唯一性、代理物品及创造栏生成路径 | 正则仅认识相应声明形式；不实际构造创造栏或 JEI 列表 |
| `world/LootTableResourceContractTest.java` | 资源：26 张表的指定掉落、空代理、Silk/Fortune 文本 | TS-04 已修；不完整解码 loot codec 或实际随机掉落 |
| `world/MachineMenuContractTest.java` | 源码：槽区间、移动 helper、权限/加载身份、数据槽与 nonce | 不实际 shift-click 或证明物品守恒 |
| `world/WindGeneratorSourceContractTest.java` | 源码：柱高/扫描区域、刷新周期、同步、渲染轴和代理名 | 不运行真实风机结构、发电 tick 或模型 |
| `world/block/entity/ImagFusorOutputContractTest.java` | 源码：输出数量、组件相等和容量检查语句 | 不运行真实配方或输出事务 |
| `world/block/entity/MachineOwnershipTest.java` | 生产策略函数：owner/visitor/admin 与旧机器 claim 的 UUID/null 分支 | 不证明机器交互、网络包权限或离线 owner 生命周期 |
| `world/block/entity/MachineStateSanitizerTest.java` | 生产纯函数：损坏持久数值的边界、NaN/无穷清理；加载入口使用此函数 | 不实际读 NBT 或恢复机器/无线拓扑 |
| `world/menu/BaseNodeMenuWordEncodingTest.java` | 生产编码函数：low/high/join 经有符号 short 的往返 | 能发现信息丢失；编码器/解码器配对同错仍可能往返成功，未独立证明线协议字序或发包 |
| `worldgen/WorldgenResourceContractTest.java` | 资源/源码：生成高度/频次、工具等级、全局 loot modifier、实际 JSON 宝箱项/权重/概率 | TS-05 已修；不等同真实生成统计或结构宝箱整条加载链 |

## 生命周期和断言独立性

上述 55 份 JUnit 未发现安装全局 NeoForge 监听器、创建服务器实体或注册跨测试活动会话的代码。状态测试使用新实例；纯函数/资源测试不承担真实服务器清理职责。矩阵朝向测试只在取得的 Method 上设置可访问性，并以 MC 自带旋转为独立参考。已发现的目录流未关闭已修复。没有将“这些 JUnit 不污染状态”扩大为所有 GameTest 都完成生命周期审计。

新增两个教程 GameTest 使用真实已连接玩家和 `AcademyAttachments.PLAYER_ABILITY`，在 finally 中从 PlayerList 移除玩家，不新增生产测试 hook、静态测试会话或全局事件监听器。它们验证运行时附件入口；不声称同时验证磁盘保存/读取、教程通知包或实际屏幕弹出。

资源和源码契约继续保留有用的回归作用。其字符串断言可能被注释、不可达代码或未调用分支满足；表中据此限定结论。没有为凑测试数量保留复制算法、测试局部恒真关系或扩大文字承诺。

## 验证状态

本分工未运行 Gradle 或游戏。静态差异检查通过；上述内存删 token 和单项权重反证已完成。主任务通知 r9 的完整 `check`、`build`、`auditTestJar` 均通过；新增教程 GameTest 已编入，实际运行结果由主任务下一轮 Vicerach 回执补记。构建/源码契约通过和真实游戏断言通过分别记录，不能互相替代。
