# SCEX AcademyCraft 1.21.1

> 本仓库由 **Space Creator EX（SCEX）服务器**维护。Minecraft 1.21.1 / NeoForge 重建、测试和公开版整理主要由 **OpenAI Codex** 在维护者监督下完成，是明确标注的 AI / Vibe Coding 项目。详见 [AI 参与开发声明](AI-GENERATED.md)。

这是 AcademyCraft 的 SCEX 非官方维护与重建版本，目标是在 Minecraft 1.21.1 + NeoForge 上恢复 1.12.2 最终可玩版本的超能力、机器、无线能源网络、终端、UI 与表现。

- 老版行为基准：[`LambdaInnovation/AcademyCraft`](https://github.com/LambdaInnovation/AcademyCraft)，Minecraft 1.12.2 最终源码快照；
- 1.21.1 起点：[`MohistMC/AcademyCraft`](https://github.com/MohistMC/AcademyCraft)；
- 附加内容行为参考：[`NullaDev/ExtraAcC-1.12.2-`](https://github.com/NullaDev/ExtraAcC-1.12.2-)，固定于 `master@d66a190e3ae00d9ca8c154b47fca0509d4c171f7`；
- 本仓库不是 LambdaInnovation、MohistMC、NeoForge 或《某科学的超电磁炮》的官方发行版。

## 当前状态

当前开发版本为 **0.0.32-scex-automation-ui**。本轮补齐发电机分面物品自动化和标准流体容器，接入原生配方缓存与机器材料标签，并为设置、技能槽和成型模式接入原生控件、焦点、键盘操作及旁白。客户端与服务端继续使用 **payload-v20-data-v4** 协议，发行版本应配套安装；能力存档保持 v4。构建固定 **NeoForge 21.1.248 / Java 21**，加载范围为 Minecraft 1.21.1 对应的 NeoForge 21.1 系列；其他 21.1 小版本尚未逐一运行验证。0.0.29 的历史审计记录见 [深度自检台账](docs/audit-20261025/DEEP-AUDIT.md)。

本轮变更和验收范围见 [0.0.32 自动化与交互说明](docs/AUTOMATION-UI-0.0.32.md)，前一批数据迁移见 [0.0.31 物品数据与同步说明](docs/DATA-SYNC-0.0.31.md)。本仓库包含 ExtraAcC 行为参考的 25 个物品、23 个专属技能和 29 个配方；这些数量是盘点范围，不能当作玩法或视觉完成率。测试结果应以配套交付报告中的精确 JAR SHA-256、证据路径及已验证层级为准。本次源码修改和隔离测试不代表已经公开发布或部署生产。

ExtraAcC 原仓库固定快照没有 LICENSE。本轮按用户指定的原始资源来源，纳入 **43 个逐字节保留的 PNG**：25 个物品外观、7 个额外状态纹理、2 个诱导因子、8 个穿戴护甲层和 1 个水人皮肤；物品模型 JSON 按 1.21.1 格式适配并保留原显示变换，没有逐字导入其 Java 实现。逐文件来源与 SHA-256 见 [资源溯源清单](src/main/resources/assets/academy/extraacc-provenance.json)，权利声明见 [NOTICE](NOTICE)；来源记录不代表获得新的授权或重新许可。液态分身已注册独立实体，采用上游水人皮肤与僵尸式模型动画。

## 运行与安装

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1 系列；本轮构建与实测固定 21.1.248 |
| Java | 21 |
| AcademyCraft | 0.0.32-scex-automation-ui |
| JEI / Jade | 可选，不是硬前置 |

使用交付目录中的主 JAR `AcademyCraft-neoforge-1.21.1-0.0.32-scex-automation-ui.jar`，同时放入客户端与服务端的 `mods` 目录；带 `audit-tests` 后缀的独立探针包仅供测试。不要与其他使用 `academy` 模组 ID 的 AcademyCraft JAR 同时安装。旧物品在读取时迁移，升级前保留世界和玩家数据备份；回退到不认识新组件的旧版本时，应恢复升级前备份。准确 SHA-256 以配套 Release 和本轮验证回执为准。

## 构建与测试

```powershell
.\scripts\generate-build-info.ps1
.\gradlew.bat --no-daemon check build auditTestJar
```

Linux/macOS 使用 `./gradlew`。成品输出到 `build/libs/`；带 `audit-tests` 后缀的 JAR 仅用于隔离验证，不安装到正常客户端/服务端。实际 GameTest 和客户端验证按台账复用 Vicerach 隔离环境。交付构建固定 Java 工具链、缓存与独立源码包复建步骤见 [0.0.20 验收说明](docs/ACCEPTANCE-0.0.20.md)。不同证据层级及其限制见 [docs/TESTING.md](docs/TESTING.md)。

---

![](https://raw.githubusercontent.com/MohistMC/AcademyCraft/master/blob/logo.png)

A Minecraft mod about superability. The inspiration of AcademyCraft comes from [A Certain Scientific Railgun (とある科学の超電磁砲)](https://en.wikipedia.org/wiki/A_Certain_Scientific_Railgun) but the mod content is not limited of the background.

Original: https://github.com/LambdaInnovation/AcademyCraft

## Development planning:
 Prioritize functional implementation, then consider beautification (UI, skill effects, etc.)  
 优先实现功能，再考虑美化(UI, 技能特效等等)


Issue(Idea, Bug) Submission
============

Please go to [Issues](https://github.com/MohistMC/AcademyCraft/issues) and submit a new ticket.

Commands
========

Ability administration requires permission level 2. `/aim` targets the executing player and
`/aimp <players>` targets online players or selectors. Use `/aim help` or `/aimp <players> help` in game.
The 1.0.7-compatible library includes `cat`, `catlist`, `learn`, `unlearn`, `learn_all`, `learned`,
`skills`, `level`, `exp`, `fullcp`, `cd_clear`, `maxout`, `reset`, and developer-mode controls.
Category and skill arguments accept either their stable ID or the copyable `#index` printed by `catlist`/`skills`.

能力管理指令要求权限等级 2。`/aim` 作用于执行者，`/aimp <玩家或选择器>` 作用于指定在线玩家。
请在游戏内输入 `/aim help` 查看完整语法。该指令库复刻了 1.0.7 的能力系、技能、等级、熟练度、
CP、冷却、重置与调试模式管理功能。
能力系与技能参数既可使用稳定 ID，也可直接使用 `catlist`/`skills` 输出的 `#索引`。

### Legacy machine ownership migration / 遗留机器所有权迁移

Versions that predate machine ownership, command-placed blocks, and damaged saves can contain
ownerless nodes, Matrices, Ability Interferers, or Wind Generator main blocks. They are never
silently assigned to the first visitor. A permission-level-2 administrator can use:

- `/acmigrate ownership scan [radius]` — count protected and ownerless machines in loaded chunks.
- `/acmigrate ownership claim <x> <y> <z> [player]` — claim one ownerless machine.
- `/acmigrate ownership claim_nearby <radius> [player]` — migrate all ownerless protected machines
  in the loaded area without overwriting existing owners. Radius is limited to 128 blocks.

升级前版本、命令放置或损坏存档可能留下没有所有者的节点、矩阵、能力干扰器或风力发电机主机。
系统不会把它们静默分配给第一个访客。权限等级 2 的管理员可先使用
`/acmigrate ownership scan [半径]` 盘点，再用 `claim` 认领单台，或用
`claim_nearby` 将已加载范围内的无主机器批量分配给指定玩家；已有所有者永远不会被覆盖，
扫描也不会强制加载区块。

Test evidence is classified in [docs/TESTING.md](docs/TESTING.md). Static source/resource contract
tests are regression hints and are never reported as gameplay-parity passes.

## License

本仓库的 SCEX / AI 辅助修改以“修改版”身份发布，不改变上游代码和素材的权利归属。来源、固定 commit 与复用素材范围见 [NOTICE](NOTICE)。以下为沿用的上游许可说明；这不是法律意见。

All versions of AcademyCraft are licensed under [GPLv3](http://www.gnu.org/licenses/gpl.html).

And all versions of AcademyCraft are additionally licensed as following:

Prohibits any person, company, business, organization, etc. from selling AcademyCraft and its contents in any form, including but not limited to paid downloads (including but not limited to various legal currencies, virtual currency, game token, etc.) AcademyCraft's items, the sale of AcademyCraft ability within the game, etc.

Lambda Innovation retains the copyright, the right of authorship, the ownership, etc. of AcademyCraft, regardless of all agreements, and any provision that requires these rights or a part of them is deemed invalid.

Lambda Innovation reserves the right of final interpretation and reserves the right to deny all agreements to revoke all authorizations.

所有版本的AcademyCraft使用[GPLv3](http://www.gnu.org/licenses/gpl.html)协议。

并且所有版本的AcademyCraft同时附加有以下版权限制：

禁止任何个人、公司、企业、组织等以任何形式出售 AcademyCraft 及其内容，包括但不限于付费下载(包括但不限于各种法定货币、虚拟货币、虚拟币、游戏代币等)，游戏内出售 AcademyCraft 物品，游戏内出售 AcademyCraft 能力等。

LambdaInnovation对于AcademyCraft的著作权、署名权、拥有权、版权等无视一切协议而保留，任何要求这些权利或其中一部分的条款均视为无效。

LambdaInnovation保留最终解释权，并保留否定一切协议撤销一切授权的权利。

## Modpack permission

Yes. >)

## Regarding Toaru Magic Index

Many people have been asking questions about whether or how much the mod will be related to
the original story _A Certain Magic Index_. Our answer is that although AC is based on the
_Railgun_, which is a spinoff of _Index_, the mod will only focus on the science side of
the story, and thus just loosely related to _Index_.

The mod is dedicated to build an interesting experience evolved around the idea of **superability**,
that's really everything.
