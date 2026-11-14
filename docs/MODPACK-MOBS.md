# 生物释放技能 / Mobs casting skills

非玩家生物可以释放实现了 `MobCastable` 的技能。用 `/academy mob supported` 查看当前支持哪些技能；不支持的技能可以写进配置，但 AI 会跳过它们。生物释放不消耗 CP、不涨熟练度。

Non-player mobs can cast skills whose effect implements `MobCastable`. `/academy mob supported` lists them; unsupported skills may be configured but the AI skips them. Mob casts cost no CP and grant no proficiency.

## 支持的技能 / Supported skills

共 30 个技能。蓄力技能先进入蓄力阶段（结果 `CHARGING`），蓄满后自动释放并进入冷却；蓄力期间生物死亡、被移除或换维度会中止。/ 30 skills. Charge skills first enter a charge phase (Result `CHARGING`) and release on their own when it ends, then the cooldown starts; the charge aborts if the mob dies, is removed or changes dimension.

| 能力 / Ability | 技能 / Skill | 蓄力 / Charge (ticks) | 备注 / Notes |
|---|---|---|---|
| 电击使 / Electromaster | `arc_gen` | — | 不会电出鱼 / never fishes |
| | `thunder_bolt`, `thunder_clap` | —, 60 | |
| | `railgun` | 20 | 用虚拟硬币发射，不消耗也不掉落弹药 / fires a virtual coin; no ammunition taken or dropped |
| | `body_intensify` | 40 | 给自己上增益 / buffs the caster |
| | `jet_engine` | — | 冲刺，受 `movementSkills` 控制 / dash, gated by `movementSkills` |
| 原子崩坏 / Meltdowner | `electron_bomb`, `ray_barrage` | — | |
| | `meltdowner` | 40 | |
| | `scatter_bomb` | 80 | |
| | `electron_missile` | 80..200（按熟练度 / by proficiency） | |
| 矢量操作 / Vector manipulation | `dir_shock`, `dir_blast` | 15 | |
| | `ground_shock` | 5 | |
| | `plasma_cannon` | 60..30（按熟练度 / by proficiency） | |
| | `blood_retro` | 20 | |
| | `vec_accel` | 10 | 冲刺，受 `movementSkills` 控制 / dash, gated by `movementSkills` |
| 空间移动 / Teleporter | `flesh_ripping` | 10 | |
| | `mark_teleport` | — | 传送到目标附近的安全落脚点，受 `movementSkills` 控制 / teleports to a safe grounded spot near the target, gated by `movementSkills` |
| 气流操控 / Aerohand | `air_blade`, `bomber_lance`, `volcanic_ball`, `air_wall` | — | |
| | `aero_separator` | 5 | 生物不会被自己的气流伤害 / no self-damage for mobs |
| | `air_jet` | — | 冲刺，受 `movementSkills` 控制 / dash, gated by `movementSkills` |
| 念动力 / Telekinesis | `psycho_needling`, `psycho_throwing` | — | 虚拟弹丸，不掉物品 / virtual projectiles, no item drops |
| | `psycho_slam` | — | |
| | `cruise_bomb` | — | 会话持续 100 tick，冷却至少 100 tick / the session lasts 100 ticks, cooldown at least 100 |
| | `storm_core` | — | |

防御类、被动类和需要玩家界面的技能（如 `vec_reflection`、`vec_deviation`、`location_teleport`、`penetrate_teleport`）不支持生物释放。/ Defensive, passive and player-UI skills (e.g. `vec_reflection`, `vec_deviation`, `location_teleport`, `penetrate_teleport`) are not mob-castable.

生物施放从不掉落物品（没有虚拟针、电鱼、投掷方块等掉落物）。/ Mob casts never drop items (no needles, fish or thrown blocks left behind).

## 单个生物：附加数据 / One mob: the attachment

附加数据 `academy:mob_ability` 记录技能 id 到熟练度（0..1），以及冷却结束的游戏时间。/ The `academy:mob_ability` attachment maps skill id to proficiency (0..1) and stores cooldown end times.

```mcfunction
/summon zombie ~ ~ ~ {"neoforge:attachments":{"academy:mob_ability":{skills:{thunder_bolt:0.8f,arc_gen:0.5f}}}}
```

只在附加数据里的技能使用默认值：权重 1，距离 0..16，施放间隔 40 tick，需要视线。/ Skills only in the attachment use defaults: weight 1, range 0..16, interval 40 ticks, line of sight required.

## 整个实体类型：数据映射 / A whole entity type: the data map

文件 / file: `data/<namespace>/data_maps/entity_type/mob_skills.json`。AcademyCraft 本身不带任何条目。/ AcademyCraft ships no entries.

| 字段 / Field | 默认 / Default | 说明 / Meaning |
|---|---|---|
| `skills[].skill` | 必填 / required | `academy:volcanic_ball` 或 / or `volcanic_ball` |
| `skills[].proficiency` | 0.5 | 0..1；生物附加数据里有同一技能时以附加数据为准 / the mob's attachment value wins when present |
| `skills[].weight` | 1 | > 0，加权随机 / weighted random pick |
| `skills[].min_range` / `max_range` | 0 / 16 | 与目标的距离（格）必须在此范围内 / distance to the target in blocks |
| `skills[].cooldown_ticks` | 0 | 每个生物额外的最短冷却（与技能冷却取较大值）/ extra per-mob minimum cooldown (the longer one applies) |
| `cast_interval` | 40 | 两次尝试之间的 tick / ticks between attempts (1..72000) |
| `needs_line_of_sight` | true | 需要看见目标 / needs to see the target |

```json
{
  "values": {
    "minecraft:blaze": {
      "skills": [ { "skill": "academy:volcanic_ball", "proficiency": 0.6, "max_range": 20 } ]
    },
    "mypack:storm_lord": {
      "cast_interval": 30,
      "skills": [
        { "skill": "academy:thunder_bolt", "proficiency": 1.0, "weight": 3, "min_range": 4, "max_range": 24 },
        { "skill": "academy:arc_gen", "weight": 2, "max_range": 10 },
        { "skill": "academy:storm_core", "proficiency": 0.8, "max_range": 6, "cooldown_ticks": 400 }
      ]
    }
  }
}
```

`/reload` 会更新数据，但只对之后加入世界的生物添加 AI 目标；已在世界中的生物若原本没有技能，需要重新加载区块或用 `/academy mob skill` 设置。/ `/reload` updates the values, but the AI goal is only added to mobs that join a level afterwards; a mob already loaded without skills needs a chunk reload or `/academy mob skill ... set`.

## AI 行为 / Goal behaviour

生物加入世界（服务端）时，如果其类型有数据映射条目或附加数据列有技能，就添加一次 `AbilityCastGoal`（优先级 2，重复加入不会重复添加）。该目标不占用任何 goal flag，不会阻塞移动、近战或远程目标。

When a mob joins a level server-side and its type has a data-map entry or its attachment lists skills, `AbilityCastGoal` is added once (priority 2; rejoining does not add another). It holds no goal flags, so movement, melee and ranged goals keep running.

- 只在 `getTarget()` 存活时运行；目标在最大距离内时看向目标。/ Runs only while `getTarget()` is alive; looks at the target when within the largest `max_range`.
- 每 `cast_interval` tick 尝试一次（首次随机延迟最多半个间隔）：在距离范围内、支持、已启用、不在冷却的技能中按权重随机选一个，调用 `AcademyMobAbilities.cast(mob, id, CastOptions.at(target).withProficiency(p))`。/ Every `cast_interval` ticks (first attempt staggered by up to half an interval) it picks a weighted random skill that is in range, supported, enabled and off cooldown, and casts it at the target.
- 生物蓄力期间不会再开始其他技能；数据映射里的 `cooldown_ticks` 只作用于瞬发技能，蓄力技能释放时使用技能本身的冷却。/ While a mob is charging it starts no other skill; the data map's `cooldown_ticks` only applies to instant skills — a charge skill gets the skill's own cooldown on release.
- 没有可用技能、没有视线或施放失败时 10 tick 后重试；超出每维度每 tick 的施放预算时 1..5 tick 后重试。/ With nothing usable, no line of sight or a failed cast it retries after 10 ticks; when the per-dimension budget is spent, after 1..5 ticks.

## 命令 / Commands

权限等级 2。施放者/实体必须是非玩家的生物实体，其余会提示并跳过。/ Permission level 2. Casters/entities must be living non-player entities; others are reported and skipped.

| 命令 / Command | 作用 / Effect | 返回 / Returns |
|---|---|---|
| `/academy mob cast <casters> <skill> [proficiency] [target]` | 立即释放；不填熟练度用附加数据里的值（没有则 `NOT_KNOWN`），不填目标用生物当前目标。每个施放者显示结果（`CAST`、`UNSUPPORTED`、`ON_COOLDOWN`、`BUSY`…）/ Cast now; without proficiency the stored one is used (`NOT_KNOWN` if none), without target the mob's current target. Shows the Result per caster | 成功次数 / successful casts |
| `/academy mob skill <entities> set <skill> <proficiency>` | 写入附加数据，并立刻给生物添加 AI 目标 / Write the attachment and attach the AI goal at once | 实体数 / entities set |
| `/academy mob skill <entities> remove <skill>` | 移除技能及其冷却 / Remove a skill and its cooldown | 移除数 / entities changed |
| `/academy mob skill <entities> clear` | 清除全部技能和冷却 / Clear all skills and cooldowns | 清除的技能数 / skills cleared |
| `/academy mob cooldown <entities> clear` | 清除冷却 / Clear cooldowns | 清除的冷却数 / cooldowns cleared |
| `/academy mob query <entity>` | 是否有 AI 目标、是否有数据映射条目、各技能熟练度、剩余冷却及来源 / Goal attached, data-map entry, each skill's proficiency, cooldown left and source | 有效技能数 / effective skills |
| `/academy mob supported` | 列出支持生物释放的技能 / List mob-castable skills | 技能数 / skill count |

## 配置 / Config `[mobs]`（服务端 / server）

| 键 / Key | 默认 / Default | 说明 / Meaning |
|---|---|---|
| `destroyBlocks` | false | 生物技能能否破坏方块 / whether mob skills may break blocks |
| `sameTypeFriendly` | true | 生物技能不伤害同类型生物 / mob skills spare mobs of the same type |
| `castsPerTick` | 20 | 每维度每 tick 最多施放数，超出返回 `BUSY` / casts per dimension per tick; more return `BUSY` |
| `minCooldownTicks` | 10 | 生物施放后的最低冷却 / minimum cooldown after any mob cast |
| `movementSkills` | true | 允许会移动施放者的技能（`jet_engine`、`vec_accel`、`air_jet`、`mark_teleport`）/ allow skills that move the caster |

实体类型标签 `academy:immobile_casters`（默认空）里的生物不会被技能移动，即使 `movementSkills` 为 true。/ Mobs in the entity type tag `academy:immobile_casters` (empty by default) are never moved by their skills, even with `movementSkills` true.

技能级的 `enabled`、伤害和冷却调整同样作用于生物。/ Per-skill `enabled`, damage and cooldown tuning apply to mobs too.

## 破坏方块 / Griefing

生物技能只有在以下全部允许时才破坏方块：`mobs.destroyBlocks`、`mobGriefing` 游戏规则（经 NeoForge `EntityMobGriefingEvent`）、`skill.destroyBlocks` 及其维度白名单、该技能的 `destroy_blocks` 调整。

Mob skills break blocks only when all of these allow it: `mobs.destroyBlocks`, the `mobGriefing` gamerule (through NeoForge's `EntityMobGriefingEvent`), `skill.destroyBlocks` with its dimension allowlist, and the skill's `destroy_blocks` tuning.

## 事件 / Event

`AbilityEvents.MobSkillCast`（NeoForge 事件总线，可取消）在每次生物施放前触发，字段 `caster`、`skill`、`proficiency`、`target`（可为 null）。取消后结果为 `CANCELLED`。蓄力技能在开始蓄力时触发一次。

`AbilityEvents.MobSkillCast` (NeoForge event bus, cancellable) fires before every mob cast with `caster`, `skill`, `proficiency` and `target` (nullable). Cancelling it makes the result `CANCELLED`. For charge skills it fires once, when the charge starts.

KubeJS / 其他模组也可直接调用 / other mods may call `AcademyMobAbilities.cast(entity, "academy:thunder_bolt", CastOptions.at(target).withProficiency(0.8f))`.

## 结果 / Results

`AcademyMobAbilities.Result`：`CAST`（已释放）、`CHARGING`（开始蓄力，之后自动释放）、`FAILED`（技能自身判定失败，如没有目标）、`UNKNOWN_SKILL`、`UNSUPPORTED`、`DISABLED`、`NOT_KNOWN`、`ON_COOLDOWN`、`ALREADY_CHARGING`（该生物正在蓄力）、`BUSY`（超出每 tick 预算）、`CANCELLED`（事件被取消）、`INVALID_CASTER`。`AcademyMobAbilities.isCharging(mob)` / `abortCharge(mob)` 可查询或中止蓄力。

`CAST`, `CHARGING` (charge started, releases on its own), `FAILED` (the skill itself declined, e.g. no target), `UNKNOWN_SKILL`, `UNSUPPORTED`, `DISABLED`, `NOT_KNOWN`, `ON_COOLDOWN`, `ALREADY_CHARGING`, `BUSY` (per-tick budget spent), `CANCELLED` (event cancelled), `INVALID_CASTER`. Use `AcademyMobAbilities.isCharging(mob)` / `abortCharge(mob)` to query or abort a charge.

## 扩展 / Adding a skill

在技能效果类上实现 `MobCastable`：`mobCast(context)` 释放；可选 `mobCooldownTicks`、`mobChargeTicks`（> 0 即蓄力技能）、`mobChargeTick`（返回 false 中止，不进入冷却）、`mobChargeAborted`。`MobCastContext` 提供 `damage(base)`（应用数据包伤害调整；`academy:skill_damage` 属性在造成伤害时再结算）、`mayDestroyBlocks()`、`mayMoveCaster()` 和 `at(ticks)`（按当前位置重新瞄准目标）。

Implement `MobCastable` on the skill effect: `mobCast(context)` releases; optional `mobCooldownTicks`, `mobChargeTicks` (> 0 makes it a charge skill), `mobChargeTick` (return false to abort without a cooldown) and `mobChargeAborted`. `MobCastContext` offers `damage(base)` (datapack damage tuning; the `academy:skill_damage` attribute is applied when the damage lands), `mayDestroyBlocks()`, `mayMoveCaster()` and `at(ticks)` (re-aims at the target from the caster's current position).
