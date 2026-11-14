# 技能数据包调整 / Skill tuning datapacks

每个技能一个文件：`data/<命名空间>/academy_skills/<技能id>.json`，文件名即技能 id（如 `railgun`）。任何命名空间都会读取；同一技能在多个命名空间都有文件时按顺序叠加：先 `academy`，再按命名空间字母序。倍率相乘，后面的文件写出的字段覆盖前面的，任一文件禁用即禁用。
`/reload` 生效，并自动同步到客户端（技能树、等级与前置判断一致）。所有字段都可省略，省略即用内置值。

One file per skill: `data/<namespace>/academy_skills/<skill_id>.json`; the file name is the skill id (e.g. `railgun`). Every namespace is read; files for the same skill stack in order — `academy` first, then the other namespaces alphabetically. Multipliers multiply, a later file's explicit fields replace earlier ones, and a skill disabled in any file is disabled.
Applied on `/reload` and synced to clients (skill tree, level and prerequisite checks agree). Every field is optional; an absent field keeps the built-in value.

## 平衡档位 / Balance profile

服务端配置 `balance.profile`：`MODERN`（默认）或 `LEGACY`。层次从下到上：

1. 代码里的 1.0.7 数值；
2. 档位层 `data/<命名空间>/academy_balance/<modern|legacy>/skills/<技能id>.json`（格式与 `academy_skills` 相同）；只使用当前档位的文件，切换档位立即生效，不需要 `/reload`；
3. `academy_skills/` 覆盖文件；
4. 服务端配置 `skill.overrides` 与全局倍率。

`LEGACY` 档位不附带文件，即 1.0.7 数值。`MODERN` 档位的文件随各现代化阶段加入。整合包可以用同路径的文件替换或补充档位层。

Server config `balance.profile`: `MODERN` (default) or `LEGACY`. Layers, bottom to top: (1) the 1.0.7 numbers in code; (2) the profile layer `data/<namespace>/academy_balance/<modern|legacy>/skills/<skill_id>.json` (same format as `academy_skills`) — only the configured profile's files are used, and switching applies at once without `/reload`; (3) the `academy_skills/` overrides; (4) the server config `skill.overrides` lines and the global multipliers. `LEGACY` ships no files, i.e. the 1.0.7 numbers; `MODERN` files arrive with the modernization phases. Packs may replace or add profile files at the same paths.

## 全局倍率 / Global multipliers

| 配置 / Config | 默认 / Default | 作用 / Effect |
|---|---|---|
| `skill.skillDamageMultiplier` | 1.0 | 所有技能伤害 / every skill's damage |
| `skill.cooldownMultiplier` | 1.0 | 所有技能冷却（玩家与生物）/ every cooldown, players and mobs |
| `skill.cpCostMultiplier` | 1.0 | 所有技能的 CP 消耗 / every CP cost |

## 字段 / Fields

| 字段 Field | 类型 Type | 说明 Meaning |
|---|---|---|
| `enabled` | bool | `false` 禁用技能（不能学习、不能使用） / disables the skill (cannot be learned or used) |
| `damage` | float ≥ 0 | 伤害倍率 / damage multiplier |
| `cp` | float ≥ 0 | CP 消耗倍率 / CP cost multiplier |
| `overload` | float ≥ 0 | 过载倍率 / overload multiplier |
| `exp` | float ≥ 0 | 熟练度增长倍率 / proficiency gain multiplier |
| `cooldown` | float ≥ 0 | 冷却倍率（`0` 为无冷却） / cooldown multiplier (`0` = no cooldown) |
| `destroy_blocks` | bool | `false` 禁止破坏方块 / stops block destruction |
| `level` | int 1..5 | 解锁等级 / unlock level |
| `prerequisites` | array | **替换**前置列表；项为 `{"skill": "<id>", "proficiency": 0..1}` 或 `{"any_level": 1..5}`（任一该等级技能已学） / **replaces** the prerequisite list; entries are `{"skill": "<id>", "proficiency": 0..1}` or `{"any_level": 1..5}` (any learned skill of that level) |
| `tree_x`, `tree_y` | number | 技能树坐标 / skill tree position |
| `base_cp`, `base_overload` | float ≥ 0 | 基础消耗（只影响使用注册表基础消耗的技能；动态消耗技能用 `cp`/`overload` 倍率） / base costs (only for skills that pay the registry base cost; dynamic-cost skills use the `cp`/`overload` multipliers) |

规则 / Rules:
- 与服务端配置 `skill.overrides`（如 `railgun.cp=0.5`，也支持 `railgun.cooldown=2`）叠加：倍率相乘；`enabled`/`destroy_blocks` 任一方为 `false` 即为 `false`。
  Combined with the server config `skill.overrides` lines (e.g. `railgun.cp=0.5`; `railgun.cooldown=2` also works): multipliers multiply; `enabled`/`destroy_blocks` are `false` if either side says `false`.
- 未知技能 id、未知前置技能、非法数值会在日志中警告并忽略；倍率小于 0 取 0，`level` 限制在 1..5，`proficiency` 限制在 0..1。
  Unknown skill ids, unknown prerequisite skills and invalid values are logged and ignored; negative multipliers become 0, `level` is clamped to 1..5, `proficiency` to 0..1.
- 冷却倍率只作用于新开始的冷却；已存档或同步的剩余冷却不会被重复缩放。`/academy cooldown set` 设置的是原始 tick 值。
  The cooldown multiplier applies when a cooldown starts; saved or synced remaining cooldowns are not rescaled. `/academy cooldown set` sets raw ticks.

## 示例 / Examples

超电磁炮 CP 消耗减半、冷却翻倍 / Railgun costs half CP and has twice the cooldown —
`data/academy/academy_skills/railgun.json`:

```json
{ "cp": 0.5, "cooldown": 2.0 }
```

电磁牵引改为 3 级技能，只需电弧激发熟练度 50% / Magnet movement becomes a level 3 skill that only needs Arc Generation at 50% —
`data/academy/academy_skills/mag_movement.json`:

```json
{
  "level": 3,
  "prerequisites": [ { "skill": "arc_gen", "proficiency": 0.5 } ]
}
```

## 不可调整 / Not tunable here

射程、持续时间、具体伤害公式等技能内部数值仍写在各技能实现中，只能通过上面的倍率间接调整。
Range, duration and per-skill formulas stay in each skill's code; only the multipliers above reach them.
