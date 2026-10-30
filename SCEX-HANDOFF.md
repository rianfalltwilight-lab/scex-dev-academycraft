# SCEX 私有研发交接

项目：AcademyCraft。源码快照日期：2026-10-30。

新加坡 shadow22 通过 GitHub 克隆接手；Monarch 后续承担生产职责。

本仓库保存当前可维护源码、资源与随工程提供的构建输入，包含原工作树的未提交内容。原 Git 历史和历史验收资料仍保留在 Monarch；来源见 `SCEX-SOURCE.json`。

以工程原 README、Gradle 配置和构建说明为准。保留既有 LICENSE / NOTICE / 来源记录。生产世界、玩家数据、凭据、运行副本和依赖缓存不属于本仓库。

本次验证覆盖源码收件、提交和远端克隆；不将仓库上传等同于重新构建通过或生产验收。

交接说明：

- Existing private repository documented as rianfalltwilight-lab/scex-academycraft. Candidate points to this actual source root despite the directory's old 0.0.29 name.
- Candidate source contains the post-delivery c:is_magic academy:ability_magic fix, source fingerprint 689f32a5260c6e8eae64f2d444cb114e77395ff8a6230eca6285b7dd98eae874; do not archive an earlier Git tag instead of current files.
- Retain src assets, Gradle wrapper, scripts, docs, NOTICE/LICENSE and BUILD-INFO. Dependencies are Maven based; commented example libs paths are not active dependencies.
