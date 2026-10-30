# AcademyCraft 0.0.20：节点配置显示与 ExtraAcC 原始材质

本轮从冻结 0.0.19 源码建立独立工作树，保留旧 JAR、源码包与原版对照实例。用户明确说明在节点名称／密码输入后已经按回车保存；本轮不以“没有按 Enter”解释其问题。本文先记录实现和验收方法，最终结果由对应交付报告补全，不把计划数当作通过数。

## 运行组合与变更

Minecraft 1.21.1、NeoForge **21.1.248**、Oracle Java **21.0.12**、Gradle wrapper **9.2.0**。协议为 `academy-1.21.1-payload-v15-data-v4`，客户端与服务端必须同时升级到 0.0.20；能力存档格式仍为 v4。

- 节点名称和密码提交带当前菜单 action token，服务端返回对应的接受／拒绝回执。客户端保留未确认编辑状态，只有当前连接、当前菜单和匹配请求的回执才确认该次修改。
- 节点公开配置增加递增 revision 和“是否设置密码”的布尔状态。菜单打开快照优先于较旧的客户端方块实体镜像；密码明文不随公开状态广播。界面区分公开、已设置、保存中和拒绝反馈。
- 已打开的设备无线列表在后台刷新名称和密码标记；输入密码期间保留当前目标索引。断开／连接仍通过原生产界面和服务端授权路径。
- 服务端菜单槽位操作使用实时管理权限，避免打开时的权限快照在撤权后继续生效。节点接入矩阵的密码访问与节点本身配置管理分开，不能把不同所有者的合法密码连接等同于转移所有权。
- 纳入固定 ExtraAcC 原快照的 42 个原 PNG：25 个物品外观、7 个额外状态纹理、2 个诱导因子和 8 个穿戴护甲层。模型 JSON 按当前格式适配，保留原物品显示变换；虚数护甲的充能层按实际能量状态选择。逐文件路径、尺寸和 SHA-256 见 [资源溯源清单](../src/main/resources/assets/academy/extraacc-provenance.json)。

资源来源为 `NullaDev/ExtraAcC-1.12.2-` 的 `d66a190e3ae00d9ca8c154b47fca0509d4c171f7` 快照。该快照没有 LICENSE，本轮没有逐字导入其 Java 实现；记录原 PNG 来源与模型适配不构成新授权或重新许可，声明与 [NOTICE](../NOTICE) 一致。Liquid Shadow 等实体外观不在本轮 42 PNG 修正范围内。

## 结果状态

| 证据层级 | 本轮最终结果 |
|---|---|
| 编译、JUnit 与资源合同 | 339 项通过，0 失败；包含静态合同，不是玩法完成率 |
| 默认服务端 GameTest | 284 项完成，全部 required 通过；candidate3-test-build.log |
| 原版对照／新版双客户端节点配置 | 以最终 SHA 绑定交付报告为准；分别记录服务器与 A/B 屏幕 |
| 正常停服后原世界重启 | 以最终 SHA 绑定交付报告为准；加载原保存数据而非重建夹具 |
| 原 PNG SHA、实际 baked model／材质与穿戴渲染 | 候选 42 PNG 和五图已通过；最终 JAR 的图审见交付报告 |
| 最终源码包解压重建与 JAR 全条目／整包 SHA 比较 | 以最终交付报告为准；要求独立解包全量重建及二进制相同 |

候选每次实际 Enter 后还要求对应 pendingNameToken／pendingPasswordToken 已清空，且对应编辑 dirty 标记为 false，证明真实回执结束了该次编辑；旧版没有回执字段时仅记 ACK UNAVAILABLE。

旧版没有新增 `hasPasswordConfigured` 或 revision 接口时，兼容门禁将明确记录接口不可用。这证明旧版没有该项可观测状态，**不能算作旧密码认证失败**。普通中文名称 Enter 能否保存、设备列表能否同步、错误／正确密码是否拒绝／接受，分别以真实输入和服务器状态为准。

## 双客户端节点配置门禁

`NodeConfigServerGate`、`NodeConfigClientGate` 和 `NodeConfigGateState` 默认关闭；仅当 `academy.nodeConfigGate=true`、角色分别为 `server/a/b`，且 `academy.nodeConfigRoot` 指向含 `ISOLATED-ACCEPTANCE` 的专用目录时运行。两个客户端使用独立游戏目录、不同的离线 UUID 和真实本地 socket 连接。角色 A 为节点所有者，角色 B 使用附近相位发电机，二者都不是管理员。场景节点为 `(0,81,0)`，设备为 `(3,81,0)`。

| 阶段 | 实际客户端操作与观察 |
|---|---|
| 1 | A 在生产名称输入行键入 `节点-甲20` 并按 Enter；比对服务器名、菜单名与显示值 |
| 2 | A 输入 `口令甲20` 并按 Enter；检查已设密码状态，名称仍保留 |
| 3–4 | B 打开实际无线页，检查名字／密码标记；错误密码拒绝，等待退避后正确密码连接 |
| 5 | A Enter 改名为 `节点-乙20`；B 保持原无线页，等待列表实际更新 |
| 6 | A Enter 改为 `口令乙20`；已建立的设备连接保留 |
| 7–9 | B 点击实际断开图标；旧密码拒绝，新密码接受 |
| 10–11 | A 清空密码并 Enter；B 原列表变为公开，实际断开后点击无密码连接图标重连 |
| 12 | 服务器关闭并重开 A 菜单；测试在每 tick 和真实 Render.Pre 持续把 A 的客户端 BE 镜像设为 `Unnamed`／revision 0，至少观察 3 个真实渲染帧并保持到截图 ACK，确认较新的打开快照仍显示 `节点-乙20` |
| 13 | A Enter 设置 `口令终20`；B 看见密码标记，服务器保存并正常停服 |
| 14 | 重启原服务器世界，仅 A 用同一身份重新登录；检查节点名、密码状态、所有者及既有连接已从磁盘恢复 |

阶段 12 是明确的旧客户端镜像故障注入，不代表每次普通回车都自然产生该延迟。一次注入会被正常服务器 BE 更新覆盖，因此门禁持续维持低 revision 直到截图 ACK，并记录实际渲染帧数。它不调用生产 setNodeName，不修改服务器数据、菜单打开快照或制造更高 revision。名称注入使用生产 `mouseClicked`、`charTyped` 与 Enter 路径，可以验证中英文内容的编码、提交与渲染；不宣称已覆盖 Windows IME 候选确认按键的全部时序。

客户端每阶段至少 30 tick；动作／状态等待上限 120 tick，截图写入可额外等待 240 tick。错误密码之后跨阶段再等待，超过服务器 20 tick 的初始认证退避。单项失败记录后 ACK，继续后续阶段；首次登录若尚未建立 GUI 就超时，要单列启动夹具失败。服务器状态和截图均记录实际结果，不通过回填字段制造“通过”。

协调目录保留 `stage.txt`、`a/b-ack.txt`、`a/b-evidence.txt`、`a/b-failures.txt`、`server-checks.txt` 和每阶段原始 PNG／SHA。重启失败写入独立 `a-restart-failures.txt`，避免继承首轮失败。客户端只在收到相应 `server-stopped.txt`／`restart-stopped.txt` 后退出；提前看到 result 就退出会把主动登出混入正常停服保存，不能作为本次持久化证据。

## 构建与源码归档

在本源码目录执行。下面的缓存路径是本次已准备的依赖缓存；在其他机器复现时须先准备相同固定依赖。`--offline` 不会下载缺失材料。

```powershell
$academyRoot='C:\Users\Monarch\Documents\Codex\2026-08-27\academycraft-ability-rebuild'
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\scripts\generate-build-info.ps1 -EvaluationDate '2026-09-05' -ReleaseFixes 'Node configuration confirmation and original ExtraAcC textures'
.\gradlew.bat -g "$academyRoot\gradle-home" --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks '-Porg.gradle.java.installations.auto-detect=false' '-Porg.gradle.java.installations.auto-download=false' '-Porg.gradle.java.installations.paths=C:\Program Files\Java\jdk-21.0.12' test build runGameTestServer
.\scripts\package-source.ps1 -Destination '.\build\distributions\AcademyCraft-neoforge-1.21.1-0.0.20-rebuilt-source.zip'
.\scripts\verify-source-package.ps1 -ArchivePath '.\build\distributions\AcademyCraft-neoforge-1.21.1-0.0.20-rebuilt-source.zip'
Get-FileHash '.\build\libs\AcademyCraft-neoforge-1.21.1-0.0.20.jar' -Algorithm SHA256
```

所有交付构建必须全量执行并禁用 build cache；0.0.18 曾出现增量匿名类调试参数名与独立全量编译不同，不能忽略单个 class 差异就声称 JAR 字节相同。生成 `BUILD-INFO.txt` 后如有任何归档输入变化，须重新生成、重新全量构建。源码包目标已存在时脚本拒绝覆盖；旧尝试应保留，用新目标名重做。最终独立复建保留包内 BUILD-INFO，在新解压路径使用相同 Oracle 工具链和缓存完整构建，逐 ZIP 条目及整 JAR SHA-256 比较。

## 本次隔离实例命令

以下 prepare 脚本和原始运行 fixture 位于外部审计目录，**不属于源码包**。它们复制固定客户端 profile、依赖和选项到新目录；双客户端服新建 `NodeConfig` 世界，重启阶段沿用同一世界。源码包的离线编译不依赖测试世界。运行前需准备该审计材料，不能只拿源码 ZIP 就声称包含全部客户端 fixture。

```powershell
$academyRoot='C:\Users\Monarch\Documents\Codex\2026-08-27\academycraft-ability-rebuild'
$taskRoot=Join-Path $academyRoot 'work\texture-ownership-0.0.20-20260905'
$source=Join-Path $taskRoot 'source'
$jar=Join-Path $source 'build\libs\AcademyCraft-neoforge-1.21.1-0.0.20.jar'
$runName='node-config-candidate-unique'
& (Join-Path $taskRoot 'evidence\prepare-client-run.ps1') -RunName $runName -JarPath $jar
$run=Join-Path $taskRoot "evidence\$runName"
```

在一个独立终端启动服务器，保留其 supervisor 至自行退出：

```powershell
& (Join-Path $run 'run-node-server.ps1')
```

服务器监听 `127.0.0.1:25621` 后，在另一个终端设置相同 `$academyRoot/$source/$run`，启动两个客户端。此处 `InstanceRoot` 是复制后的 `client` 根目录，`GameDirectoryName` 才是对应玩家的运行目录；profile 位于 `client/versions/neoforge-21.1.248/`，继承 `client/versions/1.21.1/`。

```powershell
$common=@{
    InstanceRoot=(Join-Path $run 'client')
    JavaPath='C:\Program Files\Java\jdk-21.0.12\bin\java.exe'
    AssetRoot=(Join-Path $academyRoot 'gradle-home\caches\neoformruntime\assets')
    QuickPlayMultiplayer='127.0.0.1:25621'
    NodeConfigRoot=(Join-Path $run 'coordination')
    Width=1280
    Height=800
}
$launcher=Join-Path $source 'scripts\run-packaged-client-gate.ps1'
& $launcher @common -PlayerName AcademyGateA -GameDirectoryName game-a -NodeConfigRole a
& $launcher @common -PlayerName AcademyGateB -GameDirectoryName game-b -NodeConfigRole b
```

确认首轮正常停服和客户端退出之后，在服务器终端用同一 `$run` 执行 `run-node-server.ps1 -Restart`。在客户端终端启动 `AcademyGateA`，使用新 `game-a-restart` 目录和 `-NodeConfigRestart`；不要清空服务器世界或创建新的 owner。

```powershell
& $launcher @common -PlayerName AcademyGateA -GameDirectoryName game-a-restart -NodeConfigRole a -NodeConfigRestart
```

Extra 物品材质另建新实例，不能与节点 gate 同时启用同一游戏目录的自动化标志：

```powershell
& (Join-Path $taskRoot 'evidence\prepare-client-run.ps1') -RunName 'extra-textures-candidate-unique' -JarPath $jar -Textures
$textureRun=Join-Path $taskRoot 'evidence\extra-textures-candidate-unique'
& $launcher -InstanceRoot (Join-Path $textureRun 'client') -GameDirectoryName game-textures -PlayerName AcademyTextureGate -JavaPath 'C:\Program Files\Java\jdk-21.0.12\bin\java.exe' -AssetRoot (Join-Path $academyRoot 'gradle-home\caches\neoformruntime\assets') -QuickPlaySingleplayer TextureGate -ExtraItemTextureVisualGate -Width 1280 -Height 800
```

材质 gate 使用客户端 ItemStack 和穿戴 ArmorStand 的实际模型、纹理解析与渲染，记录资源字节、状态变体和截图。它不代表物品的合成、技能支付或服务器玩法重新通过；PNG 解码、模型／sprite 断言及实际画面审阅应分别报告，不把截图存在当作全部视觉保真。

本轮没有部署生产、修改维护候选整包或执行玩家公告。此前 0.0.19 的七依赖兼容结果属于此前精确快照，本轮不未经重跑就升级为最终整包联合验收。
最终产物、完整 SHA-256、运行证据和未验证范围在配套 AcademyCraft-0.0.20-validation-2026-09-05.md 中记录；包内元数据不能预先证明自身 JAR 的后续启动测试。
