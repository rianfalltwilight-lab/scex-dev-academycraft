# 0.0.24 能力一致性与边界修复

本轮基线是 0.0.23-scex-wireless 的冻结源码包；旧目录和旧产物保留。对照上游为
`LambdaInnovation/AcademyCraft@7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9`，即 Minecraft 1.12.2 最终快照。
构建使用 Minecraft 1.21.1、NeoForge 21.1.248、Java 21.0.12、Gradle 9.2.0、ModDevGradle 2.0.141、Parchment 2024.11.17。
协议仍为 v16，能力存档仍为 v4；既有充电输出 2.5 倍调整保留。

## 修复与上游关系

| 路径 | 结果 | 一致性说明 |
|---|---|---|
| Shift Teleport | 经 ItemStack.useOn 进入 NeoForge 单块/多块放置事件与快照回滚。远端放置失败仍掉落一个物品；两个掉落实体均被拒绝时返还物品与实际资源扣款、成长和恢复延迟。 | 保留最终 1.12.2 的远端掉落语义，补现代事件事务。 |
| Thunder Clap | 只向追踪目标区块的客户端发送原版闪电生成包；服务端不注册或 tick 闪电实体。 | 恢复最终 1.12.2 的 effectOnly 闪电。现代 visualOnly 单独使用仍触发避雷针和铜块变化，因此不能仅把参数改为 true。能力自身伤害公式保留。 |
| Penetrate Teleport | 保存最后一个真正可容纳玩家完整碰撞箱的位置；不会提交第二堵墙或距离上限之外的未检查采样点。 | 修复继承自旧扫描器的落点错误，保持 0.8 步长与穿墙阶段、距离/CP 限制。 |
| Flashing / Mark Teleport | 负坐标采用 floor 映射；完整碰撞箱检查世界高度、边界、驻留区块及碰撞；Mark 释放时复查服务器保存的标记。 | 保留空中传送及旧坐标偏移，不增加地面要求。 |
| Railgun | 剩余能量恰为零时停止；中心和侧线检查区块、边界、交互权限和 BreakEvent 后方块身份；破坏失败不继续穿透；反射目标被墙体截断。 | 零能量修复属于现有现代阻挡实现的错误；上游 RangedRayDamage 的整体实体/方块处理顺序并非完全相同。反射截断匹配上游 Raytrace.traceLiving。 |
| Ground Shock | 方块坐标使用 floor，正负坐标平移不改变足迹；事件回调替换的方块不被旧状态覆盖；不为波前读取强制加载新区块。 | 修复旧 int 截断错误；保留上游 rotateAroundY(90) 实际按弧度计算的五路形状。 |
| ExtraAcC handheld teleporter | 已保存位置重新检查完整身体碰撞；仅加载最多 16 个目标区块，临时票据在 finally 中释放；失败不扣珍珠和能源。 | 同类审查的附加内容修复，不计入 LambdaInnovation 上游能力一致性。 |

## API 核对

- [NeoForge 1.21.1 GameTest 文档](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)：注解、模板命名空间、运行与成功断言。
- [Gradle 9.2.0 SourceSet](https://docs.gradle.org/9.2.0/dsl/org.gradle.api.tasks.SourceSet.html) 与 [Jar](https://docs.gradle.org/9.2.0/dsl/org.gradle.jvm.tasks.Jar.html)：探针独立编译与输出，发行 JAR 只包含 main。
- 对于版本文档未充分说明的行为，读取实际依赖中的 NeoForge CommonHooks、BlockEvent、GameTestHooks，以及同版映射后的 ItemStack、BlockItem、LightningBolt、ClientPacketListener、ClientboundAddEntityPacket、ChunkMap、ServerChunkCache、ServerPlayer、BlockPos 和 CollisionGetter。
- 放置事件与技能提交均在服务器线程；预览调用只检查已驻留区块。闪电包由原版客户端主线程处理，客户端实体按原版生命周期自行消失。
- NeoForge 运行范围沿用基线锁定；本轮没有足够兼容证据放宽含 Mixin 的整个项目范围。

## 验证与边界

本轮在 Vicerach 的真实 NeoForge GameTestServer 完成 **301/301** 必需用例（286 项基线 + 15 项新增），正常退出并保存全部维度。
现有 JUnit **343/343** 通过（102 个套件，无失败、错误或跳过；含静态资源/源码检查）。
第一轮新增用例中地面震击测试缺少着地状态，14/15 通过；补齐该技能前置条件后，15/15 和完整 301/301 均通过。
本轮未执行真实客户端视觉、多玩家往返或生产整包验收。

构建/现有 JUnit、独立探针 GameTest、实际客户端表现和整包运行属于不同证据层级。
精确执行结果及候选 SHA-256 见同目录交付回执；不得用静态检查数量推算全部能力完成率。

新增对抗用例位于 `src/auditTest`，通过 `auditTestJar` 构建，仅装入隔离运行目录。
`scripts/run-audit-gametests.ps1` 复用 Vicerach 冻结引擎与缓存，受窗口锁、6 GiB 起始可用内存、2.5 GiB 下限和 300 秒时限约束。
发行 JAR 不包含这些新探针。基线已存在的历史 GameTest 不在本轮重构范围。

本轮是针对放置事务、闪电服务端副作用、传送落点和射线边界的修复与扩展审查；不声称所有技能、客户端特效、多人重连或整包兼容已完整验收。未部署生产、未公开发布。
