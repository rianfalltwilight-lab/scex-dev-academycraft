# 0.0.26 念力投射物修复

## 输入与范围

从冻结的 0.0.25 源码归档独立建立候选，没有修改旧交付物。目标 Minecraft 1.21.1、NeoForge 21.1.248、Java 21.0.12，Gradle 9.2.0、ModDev 2.0.141、Parchment 2024.11.17。现有模组包含其他版本相关 Mixin；本轮没有新增加载器版本限制，也没有据单一运行样本放宽整个模组的范围。

本轮对照用户提供的 [ExtraAcC](https://github.com/NullaDev/ExtraAcC-1.12.2-/tree/d66a190e3ae00d9ca8c154b47fca0509d4c171f7)，本地 Git 与远端 HEAD 均为 `d66a190e3ae00d9ca8c154b47fca0509d4c171f7`。涉及该仓库的 `PsychoThrowing`、`PsychoNeedling`、`EntityFlying`、`EntityHasOwner`、`EntityCobblestone`、`EntityNeedle` 和对应两个 renderer。它是附属模组行为参考，不能据此声称整个 AcademyCraft 原版复刻已经一致。

## 修复的行为

- 弹药按副手、主手、背包依次选择；刻蚀石的消耗、速度和伤害由真正选中的弹药决定。
- 两技能创建可追踪、可保存的实体，在发射时付款、消耗一件弹药并增加熟练度，之后逐刻飞行和命中。移动目标可以在飞行途中躲开。
- 初速度为 `0.1..0.15`，刻蚀石为其 1.5 倍；每次加速增加同样的方向向量。石块空气阻力 0.99、重力 0.03，针无空气阻力和重力；水中阻力均为 0.8。
- 加速执行 10 个实际游戏刻。缓存的 1.12.2 `World` 字节码先增加实体年龄，ExtraAcC `EntityFlying` 再增加一次；因而旧 `ticksExisted <= 20` 并不是 20 个实际游戏刻。`mcp-1.12.2-srg` 核对了 `vg/T -> Entity/field_70173_aa` 和更新方法映射。
- 伤害在碰撞时结算，保留施法者、直接投射物和技能伤害边界。针通过专属 damage type 同时获得投射物与穿甲标签。石块移除了旧简化实现额外强制施加的击退。
- 碰撞选择真实实体碰撞箱的最近交点，并以墙体截断检测终点。没有使用原版辅助方法默认 0.3 格膨胀，避免薄玻璃后目标被提前命中。火把等非实体方块使用选取轮廓；下界传送门轮廓放行，其实体接触生命周期由原版处理。
- 零硬度可采集方块遵循本模组破坏配置、玩家权限和 NeoForge BreakEvent。回调替换的方块不会被旧状态授权销毁；破坏后重新检测第二道墙，单刻最多处理 16 次交点。
- 发射实体被拒绝或回调替换了弹药时，发射不成立、弹药不被扣除，回滚本次 CP/过载/成长增量。实体入场回调期间还未武装，不能提前造成伤害。
- 命中后返还一件带原组件的物品。掉落实体被取消时尝试归还原施法者背包；背包满或施法者不存在时，实体保存等待返还的物品，每 20 刻重试。已成功发射的费用不会因后续返还失败被退款，且不保留被消费物品栏槽的长期引用。
- 客户端恢复一格大小的旋转圆石外观和灰色交叉针形几何。石块按上游关闭背面剔除，避免生成在眼睛附近时从内部完全不可见。使用现有 Minecraft 圆石模型和现代渲染缓冲，没有新增复制 ExtraAcC 素材。

## API 核对

阅读了 [NeoForge 1.21–1.21.1 实体同步文档](https://docs.neoforged.net/docs/1.21.1/networking/entities/) 的动态数据、生成数据和两端同步要求。动态参数只定义在本模组实体类内，服务端负责伤害、破坏和物品归还，客户端只预测运动与渲染。

官方页面没有涵盖的实现细节，核对实际依赖 `neoforge-21.1.248-sources.jar` 中的 `Projectile`、`ThrowableProjectile`、`ThrowableItemProjectile`、`ProjectileUtil`、`ProjectileImpactEvent`、`EventHooks`、`Entity`、`EntityType`、`ServerEntity`、`ChunkMap`、`Inventory`、`ItemStack` 使用模式、`DamageSource`、`DamageType`、`ClipContext`、`NetherPortalBlock`、`BlockEvent`、`BlockRenderDispatcher`、`RenderType`、`VertexConsumer` 和 `Mth`。

重点核实：投射物碰撞事件取消代表继续飞行；实体同步参数的声明类和序列化器；生成包的 owner id；Entity/Projectile 的 owner UUID 与 NBT 恢复；物品入包对传入堆栈的原地减量；damage source 构造器的直接实体和施法者顺序；实体跟踪范围以区块配置、服务端乘 16 换算；渲染 API 的客户端限制、顶点格式和模型数据参数。向量有限性和长度平方另核对 [JOML 1.10.5 源码与 Javadoc](https://github.com/JOML-CI/JOML/blob/1.10.5/src/org/joml/Vector3fc.java)。

对应源码、旧字节码与映射保留在候选的 `evidence/api`，不进入发行 JAR。

## 验证与边界

本轮已有 343 项 JUnit 通过，Vicerach 的 347 项 GameTest 通过（321 项既有用例、26 项新增投射物用例）。单元测试包括源码/资源契约，不能当作玩法完成率。GameTest 包括新投射物优先级、真实逐刻飞行、移动目标、入场拒绝、事件取消、穿甲与施法者归属、薄墙/最近交点、易碎方块保护、原组件返还和 NBT 往返。精确产物 SHA-256、源码归档复建和逐条目实测对应关系以配套交付记录为准。

Vicerach 首次入场探测约 4.05 GiB 空闲，低于 6 GiB 门槛，早期服务端测试因此回退 Monarch 独立目录。远端恢复到约 11.26 GiB 后，347 项服务端测试和客户端测试回到 Vicerach。运行使用 1.5 GiB 堆、2 个逻辑处理器、已有跨任务占用窗口、5 分钟限时与 2.5 GiB 内存底线。每轮独立世界和证据，未操作现役游戏服务。

客户端门禁限定普通圆石、刻蚀圆石、念力针三项，经过正常按键处理、网络包、服务端技能入口及真实渲染帧；测试用预学习技能与注入按键状态，不是生存解锁或物理键盘验收。`academy-projectiles26-vclient-r04` 三项通过，9 张截图已实际审阅：旋转圆石和灰色交叉针片在飞行中可见，命中后投射物消失并生成对应物品。RTX 4090 Laptop / OpenGL 4.6，正常退出并完成所有维度保存。

三项在熟练度 0.5 下分别消耗 CP 300/450/600、过载 25/37.5/15，伤害 17.5/14/6，熟练度增加 0.0015。发射后早期目标仍为 500 生命，结束为 482.5/486/494，弹药从 4 变为 3 并产生返还物。客户端用例不将返还物外观当作飞行实体证据，另记录飞行实体 ID、位置和速度并离轴拍摄。

早期失败保留为失败：本地首次计数/熟练度/弹道墙面夹具错误；旧最近交点用例未真正发射，改为逐刻飞行后发现并修复默认膨胀范围造成的薄墙命中；远端首次客户端被 1.21.1 无障碍引导阻挡（核对 `Minecraft`/`Options`/`QuickPlay` 源码后在测试配置加入 `onboardAccessibility:false`）。较早截图过于贴近镜头或遮挡针片，不能作为三项完整视觉通过证据，已由 r04 补足。

有意保留的现代保护包括墙体遮挡、事件权限、边界不强制加载区块，以及失主/返还受阻时保存弹药。旧实现会丢失组件、在缺失施法者时直接删除投射物，且碰撞选择可能穿墙，这些旧缺陷不予复刻。针形几何采用正常深度测试，圆石使用现代模型光照；不宣称与旧 OpenGL 画面逐像素一致。

NBT 往返测试不等于真实跨区块卸载、服务器重启或多人重连验证。整包、跨版本加载器组合、多人专用服客户端往返和大规模持续发射压力尚未覆盖。本轮未部署生产或公开发布。新增注册实体要求客户端和服务端配套 0.0.26，协议 v17，能力数据版本仍为 v4。

## 复现

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline --no-daemon --no-configuration-cache --no-build-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' test jar auditTestJar
```

探针由 `auditTestJar` 独立打包，不进入成品。服务端门禁使用 `scripts/run-audit-gametests.ps1`，客户端三项门禁使用 `scripts/run-projectile-client-gate.ps1` 与 `academy.extraSkillVisualGate.cases`。本轮实际 runtime recipe、输入哈希、占用窗口回执和清理回执保留在交付目录 `evidence` 中。旧版负向用例需使用对应版本冻结探针，不将依赖新实体类的探针直接加载到旧版 JAR。
