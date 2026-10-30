# 原机器测试与注册类补审（2026-10-25）

主修复批冻结后的只读补审。范围为根协调列出的18个旧测试/驱动文件及6个world注册类；新增路径已并入 machines-reviewed-files.txt。已读注册类完整实现；旧测试按实际断言、测试夹具和全局状态清理核验，特别是2924行 AcademyServerGameTests 不冒称每条分支全部实跑。下面测试的 PASS 必须由根控制的 Vicerach 日志证明。

确认无正常运行时自动开启的测试capability或固定坐标世界修改入口。ChargingOutputGameTests 的4个path case必须设置 academy.chargingOutputGate=true，此flag也启用临时STICK/GOLD_BLOCK FE能力。旧 WindGeneratorGameTests.menuPos 的两个 Unpooled ByteBuf 未释放，经根授权已在两个菜单构造点以 try/finally 释放；断言不变。本域新增 MachineDeepAuditGameTests / LiquidShadowDeepAuditGameTests 无自分配 ByteBuf；ClientSecondaryAuditChecks 的 codec buffer 已在 finally 释放。

|文件|行数|审阅内容|
|---|---:|---|
|`gametest/AbilityInterfererGameTests.java`|107|付费抑制100 IF/10tick、owner/whitelist/creative排除、移除清理和能量不足停机；clearAll仅在隔离测试开头调用，延迟 case 不用静态共享玩家。|
|`gametest/AcademyServerGameTests.java`|2924|按根要求核验全部测试入口/实质失败断言和静态污染点，非逐行全文复核：矩阵/节点/风机/配方/库存/能力/协议/ledger；类仅EMPTY常量，配置PVP在finally恢复，临时NeoForge listeners均有finally unregister，codec buffers有release。Mock/fixture和真实网络证明须区分。|
|`gametest/CatEngineGameTests.java`|102|真实BE500/200/2000数值、消耗守恒、右键独立节点绑定/解除/销毁去边；断言依赖server WiWorldData实际tick。|
|`gametest/ChargingOutputGameTests.java`|99|四条IF/FE item/block路径，1..5等级与21熟练度步长、预付失败/满容量/abort carry；STICK/GOLD测试capability需显式property，ITEMS/BLOCKS finally清理。|
|`gametest/ConcurrentGateState.java`|35|默认property关闭，固定调用方文件名、绝对root与隔离marker检查；读失败不等于业务成功。|
|`gametest/ConcurrentMenuServerGate.java`|116|仅server role显式开启，真实两socket玩家阶段、owner撤销、重放顺序、密码、重启；静态状态为一次性独立进程fixture。|
|`gametest/EnergyMachineAdversarialGameTests.java`|142|伪造远端开发机proxy不破坏无关主机、畸形UUID/小数余额、同坐标替换不继承拓扑、旧无主节点不被普通玩家接管。|
|`gametest/EnergyRepairGameTests.java`|275|真实原版repair menus、mending XP、rename/disenchant与十种能量物品NBT/FE/进度条；menu close finally恢复inventoryMenu/清空fixture。|
|`gametest/FreqTransmitterGameTests.java`|212|请求nonce取消、已授权矩阵连自有节点、外人节点拒绝、空密码仍确认、改密使旧授权终止；真实manager状态，不声称socket往返。|
|`gametest/ImagFusorMenuGameTests.java`|177|实际menu.clicked/quickMove输入去重、无效/输出槽拒绝、1红石等待2红石再加工；精确液体1000/IF1440/输出1，最终close finally。|
|`gametest/MachineOwnershipMigrationGameTests.java`|83|权限0命令拒绝、权限4批量接管无主机器，已有owner不覆盖；操作真实服务端command dispatcher。|
|`gametest/MatrixNetworkFlowGameTests.java`|134|实际INIT/连节点handler与80tick FE→IF守恒，持久buffer空节点/full buffer边界、SSID/改密持久化；自造context只同步执行handler，不是socket测量。|
|`gametest/NetworkAdversarialGameTests.java`|522|核对实质require断言与fixture/cleanup：重绑定单次能量、即时save单边、回调A→B→A无重入重复、菜单身份/重放/owner/距离、nonce九词握手、错误返回NaN/Inf；ByteBuf在finally释放。|
|`gametest/NodeConfigFlowGameTests.java`|192|中文名称/密码codec、public tag不泄露secret、密码更新保留既存link、旧owner库存操作立即撤销；真实handler同步context与NBT，不声称完整网络。|
|`gametest/NodeConfigGateState.java`|34|默认关闭+隔离marker，临时文件后replace防半写；单用途测试根。|
|`gametest/NodeConfigServerGate.java`|147|真实两客户端13阶段+重启阶段，按阶段累计checks/failures并合并client错误，停止回执在ServerStoppedEvent写入。|
|`gametest/PhaseGeneratorVisualGameTests.java`|163|真实fluid/item capability侧面布局2/3/1，错误流体拒绝/存取守恒，工作模型frame动态切换；fixture数据与实际render区分。|
|`gametest/WindGeneratorGameTests.java`|212|真实玩家跨区块放置、proxy能源能力、shift-click限一风扇、运行/遮挡/恢复/破柱停止；menuPos两个Unpooled读缓冲已补try/finally释放，断言保持不变。|
|`world/AcademyBlockEntities.java`|60|逐项BE factory与相应block匹配，deferred supplier不提前.get；代理/主方块注册齐全。|
|`world/AcademyBlocks.java`|151|逐项方块factory/属性/代理/escrow/phase关联，未把非遮挡代理硬改全方块；无重复注册ID。|
|`world/AcademyFluids.java`|22|source/flowing/FluidType三注册一致，PhaseFluid延迟引用不构造循环。|
|`world/AcademyMenus.java`|43|14类菜单注册全部IMenuTypeExtension构造，client/server相同扩展数据入口。|
|`world/AcademyParticles.java`|20|5粒子类型+注册，配合已读ClientModListener provider；无未注册副本引用。|
|`world/AcademySounds.java`|104|声音分类/注册与playSound overload核对，Holder生命周期正确；媒体声音与resource索引吻合。|
