# /academy 命令参考 / Command reference

需要权限等级 2。`<targets>` 是实体选择器；非玩家实体会被跳过并提示（生物支持以后再加）。技能可写 `railgun` 或 `academy:railgun`。所有修改只在服务端进行，会发出公开的能力事件并立即同步。返回值可用于 `execute store result`。

Permission level 2. `<targets>` is an entity selector; non-player entities are skipped with a message (mob support comes later). Skills accept `railgun` or `academy:railgun`. Every change is server-side, posts the public ability events and syncs at once. Return values work with `execute store result`.

| 命令 / Command | 作用 / Effect | 返回 / Returns |
|---|---|---|
| `/academy learn <targets> <skill> [respectPrerequisites]` | 学习技能；技能不属于当前类别时失败，提示先用 `/academy category`。`true` 时检查等级和前置。/ Learn a skill; fails if it belongs to another category. `true` checks level and prerequisites. | 新学会的玩家数 / players who learned it |
| `/academy unlearn <targets> <skill>` | 遗忘技能（保留熟练度，清除预设槽位）/ Unlearn (proficiency kept, preset slots cleared) | 玩家数 / players changed |
| `/academy learn_all <targets>` | 学会当前类别全部技能 / Learn every skill of the current category | 新学会的技能总数 / skills learned |
| `/academy category <targets> <category>` | 切换类别：保留等级，清除已学技能、熟练度、预设、冷却（同 `/aim cat`）/ Change category: level kept; learned skills, exp, presets, cooldowns cleared | 玩家数 / players changed |
| `/academy category <targets> clear` | 清除类别并重置能力数据 / Clear the category and reset ability data | 玩家数 / players changed |
| `/academy level <targets> <1..5>` | 设置等级 / Set the level | 玩家数 / players changed |
| `/academy proficiency <targets> <skill> (set\|add) <value>` | 熟练度，限制在 0..1，需已学会 / Proficiency, clamped 0..1, skill must be learned | 玩家数 / players applied |
| `/academy cp <targets> (set\|add) <value>` / `cp <targets> fill` | CP，限制在 0..最大值 / CP, clamped 0..max | 玩家数 / players applied |
| `/academy overload <targets> (set\|add) <value>` | 过载，限制在 0..最大值 / Overload, clamped 0..max | 玩家数 / players applied |
| `/academy overload <targets> unlock` | 解除过载锁定 / Lift the overload lockout | 被解锁的玩家数 / players unlocked |
| `/academy cooldown <targets> clear [skill]` | 清除全部或单个冷却 / Clear all or one cooldown | 清除的冷却数 / cooldowns cleared |
| `/academy cooldown <targets> set <skill> <ticks>` | 精确设置冷却（替换更长的旧值）/ Set a cooldown exactly (replaces a longer one) | 玩家数 / players applied |
| `/academy active <targets> (on\|off)` | 开关能力激活（off 会先结束蓄力和特殊模式）/ Toggle ability activation (off ends charges and special modes first) | 玩家数 / players changed |
| `/academy query <target>` | 显示类别、等级、进度、CP、过载、锁定、激活、技能熟练度、冷却 / Show category, level, progress, CP, overload, lock, active, skills, cooldowns | 等级 / level |
| `/academy debug damage <attacker> <target> <skill> <raw>` | 显示技能伤害各倍率 / Print the skill damage breakdown | `(int)` 最终伤害 / final damage |

例 / Example: `execute store result score @s ac_level run academy query @s`
