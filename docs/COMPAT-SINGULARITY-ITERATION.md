# 奇点迭代联动 / Singularity Iteration integration

1.0.7 的 IC2 联动在 1.21.1 由奇点迭代（Singularity Iteration，模组 id `mio_icif`）接替。联动不需要编译依赖：本模组通过 Forge 能量（FE）和方块标签与它对接，没有安装奇点迭代时这些功能自动不生效。

1.0.7's IC2 support is succeeded on 1.21.1 by Singularity Iteration (mod id `mio_icif`). There is no compile dependency: AcademyCraft meets it through Forge energy (FE) and a block tag, and everything stays dormant without the mod.

## 换算 / Conversion

1 IF = 4 FE = 1 EU，与 1.0.7 一致（1.0.7：1 IF = 1 EU = 4 RF）。比率来自服务端配置 `machines.fePerIf`（默认 4）；奇点迭代固定 4 FE = 1 EU。所有接口只按整 IF 转移，不会凭空产生或丢失零头。

1 IF = 4 FE = 1 EU, as in 1.0.7 (1 IF = 1 EU = 4 RF). The ratio is the server config `machines.fePerIf` (default 4); Singularity Iteration fixes 4 FE = 1 EU. Every boundary moves whole IF only, so no fraction is created or lost.

## 功能 / Features

| 功能 / Feature | 1.0.7（IC2） | 现在 / Now |
|---|---|---|
| 电网 → 无线网络 / grid → wireless network | `eu_input` | `academy:rf_input` 接在奇点电缆或机器旁 / next to an SI cable or machine |
| 无线网络 → 电网 / wireless network → grid | `eu_output` | `academy:rf_output` 接在奇点电缆或机器旁 / next to an SI cable or machine |
| 合成 / Recipes | IC2 铜导线、储电箱 / IC2 copper cable, BatBox | 加装奇点迭代时新增 `academy:si_rf_input`（能量单元 + 机器框架 + 绝缘中压导线 + 能量转换元件）和 `academy:si_rf_output`（储电盒 + 机器框架 + 绝缘中压导线 + 能量转换元件）；原有配方保留 / with Singularity Iteration loaded, `si_rf_input` (energy unit + machine frame + insulated MV cable + energy convert component) and `si_rf_output` (BatBox + frame + cable + component); the existing recipes stay |
| 电池槽 / Battery slots | IC2 电动物品 / IC2 electric items | 节点、虚数融合器、金属成型机、能力干扰器和三种发电机的电池槽接受任何带 FE 的物品，包括奇点电池和电动工具 / node, imaginary fusor, metal former, ability interferer and the three generators accept any item with FE, including SI batteries and electric tools |
| 电流充能技能 / Charging skill | IC2 物品与方块 / IC2 items and blocks | 任何带 FE 的物品与方块（已有）/ any FE item or block (already present) |
| 电缆电弧 / Cable arcs | 开启能力时 5 格内 IC2 电缆冒电弧 / arcs on IC2 cables within 5 blocks when the ability turns on | 方块标签 `academy:arc_conductors`（默认含奇点迭代的 13 种电缆，可选条目），最多最近的 32 个 / block tag `academy:arc_conductors` (Singularity Iteration's 13 cables as optional entries), nearest 32 at most |

## 注意 / Notes

- 奇点迭代把 FE 方块当作电网的外部端口：`rf_output` 是电源（奇点按计划从它取电），`rf_input` 是蓄电类接收端（只在奇点自己的负载之后收到余量）。它们没有电压等级，不会过压爆炸。/ Singularity Iteration treats FE blocks as foreign ports: `rf_output` is a source it draws from on planned deliveries, `rf_input` a storage-class receiver that only gets the surplus after SI loads. They have no voltage tier and never explode.
- 奇点电缆不接收推送的 FE，所以 `rf_output` 依靠奇点主动取电；直接贴在奇点机器上时 `rf_output` 也会主动推送。/ SI cables take no pushed FE, so `rf_output` relies on the grid drawing from it; placed directly against an SI machine it also pushes.
- 奇点的电动工具和护甲不能对外放电；放进电池槽只会被充电。堆叠数大于 1 的奇点电池不收也不放电。/ SI electric tools and armour cannot be discharged externally; in a battery slot they are only charged. A stack of more than one SI battery neither takes nor gives energy.
- 1.0.7 的 `academy:eu_input` / `academy:eu_output` 没有恢复，由两个 FE 能源桥代替。/ The 1.0.7 ids `eu_input` / `eu_output` are not restored; the two FE bridges replace them.

## 测试 / Testing

```bash
./gradlew runGameTestServer -PsiJar=/path/to/mio_icif-<version>.jar
```

`SingularityIterationCompatGameTests`（批次 `si_compat`）在加载奇点迭代时检查配方、节点与奇点电池的双向转移、堆叠电池、电缆电弧，以及 `rf_output` 经奇点电缆给储电盒供电；未加载时只检查联动保持不生效。已用奇点迭代 0.1.11.2 验证。

`SingularityIterationCompatGameTests` (batch `si_compat`) checks, with the mod loaded, the recipes, node transfers both ways with an SI battery, stacked batteries, cable arcs, and `rf_output` feeding a BatBox through an SI cable; without it, only that the integration stays dormant. Verified with Singularity Iteration 0.1.11.2.
