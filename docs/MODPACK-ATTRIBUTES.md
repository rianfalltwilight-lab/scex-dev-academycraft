# 属性 / Attributes

本模组的属性都可以用普通的属性修饰符调整（装备、药水效果、Curios、Apotheosis 词缀、KubeJS 等）。所有倍率类属性的基础值为 1（100%）；修饰符用 `add_value` 加 0.2 就是 +20%。

All attributes take ordinary attribute modifiers (gear, effects, Curios, Apotheosis affixes, KubeJS). Every multiplier has base 1 (100%); an `add_value` modifier of 0.2 means +20%.

| 属性 / Attribute | 谁有 / Who | 基础 / Base | 作用 / Effect |
|---|---|---|---|
| `academy:skill_damage` | 所有生物 / every living entity | 1 | 该实体造成的技能伤害 / ability damage it deals |
| `academy:skill_resistance` | 所有生物 / every living entity | 0 | 受到的技能伤害 ×(1 − 值)；负值为易伤 / incoming ability damage × (1 − value); negative = vulnerable |
| `academy:skill_power` | 所有生物 / every living entity | 0 | 每次技能命中先加上这么多伤害，再乘各种倍率 / flat damage added to every ability hit before the multipliers |
| `academy:max_cp` | 玩家 / players | 1 | CP 上限（等级基础、用量增长与课程加成）/ max CP (level base, usage growth and courses) |
| `academy:cp_regen` | 玩家 / players | 1 | CP 回复速度 / CP recovery |
| `academy:max_overload` | 玩家 / players | 1 | 过载上限 / max overload |
| `academy:overload_recovery` | 玩家 / players | 1 | 过载恢复速度 / overload recovery |
| `academy:cp_cost` | 玩家 / players | 1 | 所有技能的 CP 消耗，低于 1 更省 / every skill's CP cost; below 1 is cheaper |
| `academy:cooldown` | 玩家 / players | 1 | 所有技能冷却，低于 1 更短（至少 1 tick）/ every cooldown; below 1 is shorter (at least one tick) |
| `academy:proficiency_gain` | 玩家 / players | 1 | 熟练度与升级进度的获取 / proficiency and level-gauge gain |

```mcfunction
/attribute @s academy:max_cp modifier add mypack:ring 0.25 add_value
/attribute @s academy:cooldown modifier add mypack:ring -0.15 add_value
```

资源属性每 tick 读取一次，修改后立即生效。服务端配置的倍率（`skill.cpCostMultiplier`、`skill.cooldownMultiplier` 等）和平衡档位会再乘在上面。

Resource attributes are read every tick, so changes apply at once. The server config multipliers and the balance profile multiply on top.
