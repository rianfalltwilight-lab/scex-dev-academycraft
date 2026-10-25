# AcademyCraft + ExtraAcademy 0.0.30 原生兼容修复

基线：`v0.0.29-scex-deep-audit` / `c8f4f4bbde675b1b5d84b85da2eca349b9761c3a`。
版本：`0.0.30-scex-native-compat`；Minecraft 1.21.1、Java 21。

## 玩家可见变化

- 空气使的飞行使用独立的 NeoForge `CREATIVE_FLIGHT` 临时属性修饰符。退出技能只撤销本技能授权；其他来源仍允许飞行时保持飞行。创作／旁观模式及死亡、重生、跨维和登出清理有独立回归。
- 风暴之翼结束时根据当前有效飞行权限清理状态。其他来源仍授予飞行时保持状态；其授权已失效时不会因启动时的快照残留飞行。
- 四件共振装甲可通过原版附魔台和铁砧附魔，并遵循头、胸、腿、脚的原版物品标签。保留不损耗行为；纸装甲和虚数装甲继续禁止附魔。
- F1 同时隐藏充能 HUD 和终端安装进度，再按 F1 可恢复显示。
- 设置、技能槽和安装提示使用翻译组件；设置状态颜色使用独立布尔值。中、英、日三语各新增 32 个词条，日语预设标签保持在按钮宽度内。长按键提示按槽宽截断，悬停提示保留完整绑定名称。

注册 ID、技能数值、装备耐久政策、存档 v4、`payload-v19-data-v4` 协议及 4 FE = 1 IF 换算保持不变。本批次未实施物品电量组件迁移、网络拆包、机器自动化或界面控件重构。

## 验证与证据

构建固定 NeoForge 21.1.248 / Java 21.0.12.1+1，运行声明仍为 `[21.1,21.2)`。本轮没有执行其他 NeoForge 小版本或完整第三方整包兼容矩阵。

| 层级 | 本轮覆盖 |
|---|---|
| JUnit | 342 项，包含行为测试和已有源码／资源契约；不作为完整玩法覆盖率 |
| 实际 GameTestServer | 443 项：原选择范围 430 项加原生兼容 13 项；其中新增飞行 9 项、附魔 4 项 |
| 真实客户端 | 3 个服务端属性广播阶段、3 个 F1 绘制阶段、3 种语言各 2 个界面，共 12 项行为检查；保留 9 张截图 |
| 产物身份 | 主 JAR、独立探针 JAR、源码树和源码 ZIP 的 SHA-256；最终打包与受测 JAR 的解压条目对比 |

附魔测试调用真实 `EnchantmentMenu` / `AnvilMenu`，验证等级和青金石消耗、正确／错误装备部位、原生保存读取及受伤不损耗。使用普通生存玩家，等待登录保护结束后执行实际受伤检查。

飞行测试通过实际属性修饰符和玩家生命周期验证。客户端测试在集成服务端线程调用生产技能逻辑，在客户端只读取实际收到的属性和飞行状态，不直接写入客户端飞行属性。F1 经程序调用真实键盘处理器；语言检查执行实际资源重载和生产界面绘制。这些检查不代表物理键盘输入或生存解锁流程的人工游玩验收。

Vicerach 预检可用内存约 0.3 GiB，低于既有运行器 6 GiB 门槛，本轮回退 Monarch；使用独立平坦测试世界，服务端与客户端串行运行，堆内存 1536 MiB、2 个 CPU 工作线程。测试不接触生产世界。

最终通过状态、截图复核、准确哈希、正常退出和清理记录以 `evidence/native-compat-0.0.30/final-validation.json` 及其引用文件为准。独立探针使用 `src/auditTest`，不得安装到正常客户端／服务端。

## 构建与发行

```powershell
.\scripts\generate-build-info.ps1
.\gradlew.bat --offline --no-daemon --no-configuration-cache --console=plain --max-workers=2 check build auditTestJar
```

`--offline` 要求所需依赖已经缓存。源码清单与哈希由 `scripts/source-files.ps1` 统一定义；打包通过 `scripts/package-source.ps1`，读取验证通过 `scripts/verify-source-package.ps1`。

GitHub 使用既有私密仓库 `rianfalltwilight-lab/scex-academycraft`。交付仅包含主 JAR、对应源码 ZIP 和 `SHA256SUMS.txt`；保留先前发布。GitHub 归档不代表已经部署服务端或玩家更新器。
