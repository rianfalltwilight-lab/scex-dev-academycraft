# 液态人影实体与固定 AI 审计

固定参考：ExtraAcC `d66a190` 的 `entity/EntityWaterman.java` 与 `ability/telekinesis/skill/LiquidShadow.java`。原实现是 EntityTameable，0.6×1.8、100 HP、移动速度 1、攻击基础 20→30；注册后固定近战、跟随主人（10→2 格）、避水漫游、看玩家、闲视，以及主人受伤/主人攻击/自身受伤目标规则。

原移植为未注册的 Drowned 子类，NoAI 后按向量手动 move，缺完整导航、漫游和自身受击目标规则，也借用溺尸网络类型。此为确定 P1 行为缺口，不是为已有 AI 增加新机制。

本批实现 `academy:liquid_shadow` / `AcademyEntities.LIQUID_SHADOW`，公开 `skill.ability.telekinesis.LiquidShadowEntity extends TamableAnimal`，构造 `(EntityType<? extends LiquidShadowEntity>, Level)`。属性在实体类 MOD 总线注册。固定 vanilla goals 仅在 `registerGoals` 建立，生命周期/攻击回调不增删 goal 集合。原有 NoAI 手动位移和 8 格回主人瞬移删除；跟随使用原版导航及原版到主人附近的碰撞检测传送，传送前检查目的范围驻留区块。

所有目标仍经过主人盟友、原版 canAttack、玩家 canHarmPlayer 与 AcademyDamageHelper 服务端 PvP 规则。没有额外禁止 Boss，也没有跳过原版免疫/伤害事件。实际近战前检查范围和可见路径；CP 为 `5×基础伤害`，必须全额预付，失败终止且不能造成伤害。技能伤害仍走 AcademyDamageHelper；成功经验按基础伤害×0.0001。预付后保护插件取消的尝试仍保持已付费，沿用本分支事务规则。

会话仍由同一 owner UUID 和实体实例共同约束。启动 2000→1000 CP、300→200 overload，持续 1 CP/t 和经验 0.0001，触水终止，冷却 100；退出、换维度、死亡、实体卸载、服务器关闭清理继续保留。实体不保存、不允许 `/summon`，旧存档的精确 tagged NoAI Drowned 迁移过滤继续保留。出生回调后重新检查玩家状态、会话身份、水桶与支付能力，单桶直接替换原槽为空桶，均保留现有修复。

客户端由总协调使用固定上游 `RenderWaterman` 的 ModelZombie/贴图恢复；Monarch client-r2 已完成真实 renderer 调用和截图复核。技能真实按键六 case 验收仍单独进行，不与 renderer probe 混为一项。

新增 `src/auditTest/.../LiquidShadowDeepAuditGameTests` 5 项：真实自然 tick 绕障碍追击并结算；保护主人→自身受击目标切换且 goal 集不变；原版近战入口完整预付/PvP反控；触水结束且不落盘；无敌人时自然跟随与原始属性。现有 Telekinesis/ExtraAdversarial/Movement 的召唤查询改为注册实体；旧 Drowned 迁移正反控保留，单步攻击测试调用实际 doHurtTarget 边界，自然移动测试仍不调用 tick/move。

Vicerach `gt-r3` 已运行实体行为测试。无墙自然追击/付费、攻击 CP 不足、保护主人→自身受击、直接近战预付/PvP 反控、触水清理和空闲跟随通过；两项有墙自然导航失败。实际 NeoForm 源码核验定位到现代 `OwnerHurtTargetGoal.canUse` 传入 `TargetingConditions.DEFAULT`，即使基类 `mustSee=false`，仍先强制选敌视线，水人没有取得玻璃后的主人目标。固定两个主人目标 goal 现仅覆盖 `canAttack` 条件副本的视线检查；目标敌对权限、原版导航、最终近战距离/clearPath/预付保持。测试继续要求自然走动超过 3 格、绕到墙端并成功付费攻击、不可穿玻璃造成伤害，另增加当前目标身份和路径诊断。Monarch gt-r1 / r10 已实际通过这两项隔障导航断言。

同轮旧 `rejectedShadowDamageDoesNotRefundPaidGrowth` 失败属于测试入口与反控错误：它仍仅触发玩家维护事件，且把永久无敌目标当作下游伤害取消。原版会在目标资格阶段拒绝永久无敌目标，不应扣费。现测试先证明该分支零扣费，再使用真实 `LivingIncomingDamageEvent` 对唯一目标取消实际 `doHurtTarget`，要求事件恰好发生一次、100 CP 全额扣除、usage growth 增加、血量不变。监听器在 finally 解除；没有删除付费不退款的实质要求。

根协调执行的 Monarch gt-r1 / r10 为 430/430 通过、59.28 秒、exit 0、forced=false，包含全部液影 AI、移动、伤害取消和生命周期用例。证据收件位置 `evidence/audit-20261025/monarch-gt-r1`。客户端 client-r2 也已通过 renderer/pose 探针并由根查看截图；真实按键六 case 的当前结果另见根总台账。不得以 goals 源码或类名存在代替行为通过。
