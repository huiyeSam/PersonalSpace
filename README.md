# PersonalSpace — Minecraft 1.12.2 Port

[中文说明](#中文说明)

An unofficial Minecraft Forge 1.12.2 port and modified fork of [GTNewHorizons/PersonalSpace](https://github.com/GTNewHorizons/PersonalSpace). The upstream project targets Minecraft 1.7.10. This fork is not an official GT New Horizons release.

- Mod version: `1.0.93-mc1.12.2`
- Minecraft: `1.12.2`
- Forge: `14.23.5.2860`
- Java: JDK 8
- Gradle: `4.9`; ForgeGradle: `3.0.197`
- MCP mappings: `snapshot_20171003-1.12`

PersonalSpace provides personal, customizable dimensions. This source snapshot contains the 1.12.2 migration and subsequent modifications, including world configuration, GUI and preview changes. It preserves the upstream Java package and mod identity.

## Attribution and license

Original project: [GTNewHorizons/PersonalSpace](https://github.com/GTNewHorizons/PersonalSpace). Original author credited by the mod: **eigenraven**, together with upstream contributors.

This fork retains the upstream **GNU Lesser General Public License version 3**. See [LICENSE](LICENSE) for LGPL v3 and [COPYING](COPYING) for the incorporated GPL v3 terms. Existing authorship and copyright notices must be preserved. Do not present upstream code as exclusively authored by the fork maintainer.

This export was prepared from a local working directory without Git metadata. The precise upstream base commit and local change history have not been verified. This is a current source snapshot, not a reconstruction of upstream Git history.

## AI tool disclosure

The Forge 1.12.2 porting work and all fork-specific code changes were written entirely using generative AI. This statement applies only to this fork's contributions, not to inherited upstream code or its authors. It does not change upstream attribution or the project's license.

Generated code may contain errors. An offline build succeeded for version 1.0.93; this is not a claim of complete manual review or in-game testing.

## Building

Install JDK 8 and use Gradle 4.9, or the included Gradle wrapper. Ensure `JAVA_HOME` points to JDK 8.

The current build uses local compile-only dependencies. Obtain these files from their respective official project distribution channels and put them in `libs/`:

- `ae2-uel-v0.56.7-dev.jar` — AE2 Unofficial Extended Life development JAR; a runtime or empty API JAR is not a substitute.
- `CodeChickenLib-1.12.2-3.2.3.358-universal.jar`

Third-party JARs, Minecraft binaries and Gradle caches are deliberately not redistributed in this repository.

```sh
./gradlew build
```

Windows: `gradlew.bat build`. A first build normally requires network access to retrieve Gradle, ForgeGradle, Forge and mapping dependencies. For a previously populated local cache, use `./gradlew --offline build`.

Output: `build/libs/personalspace-1.0.93-mc1.12.2.jar`.

The original local working directory previously produced this JAR successfully. This portable export has not been independently rebuilt in a clean environment. Successful compilation is not evidence of complete in-game testing; back up worlds before testing.

## Runtime dependencies

Install the compatible CodeChickenLib runtime alongside the mod. AE2 integration uses compile-only APIs; consult the mod's dependency declarations for the runtime setup. Source build dependencies are not bundled into the mod JAR.

## 中文说明

本项目是 [GTNewHorizons/PersonalSpace](https://github.com/GTNewHorizons/PersonalSpace) 的非官方 Minecraft 1.12.2 移植与修改版，不是 GTNH 官方发布版本。保留原作者 eigenraven 和上游贡献者署名，并沿用 LGPL-3.0 许可证，不另行改为 MIT 等许可证。

### AI 工具使用声明

本分支的 Forge 1.12.2 移植代码及该分支新增、修改的代码，均完全使用生成式 AI 编写。此声明仅适用于本分支的贡献，不适用于继承的上游代码，也不改变原作者署名或项目许可证。

生成的代码可能存在错误。1.0.93 已通过离线构建，但不代表已完成人工审查或完整的游戏内测试。

### 构建与发布说明

当前版本为 `1.0.93-mc1.12.2`。构建需要 JDK 8、Gradle 4.9，以及放在 `libs/` 中的上述两个依赖文件。执行 `./gradlew build`；依赖缓存齐全时可以执行 `./gradlew --offline build`。

本源码包不包含第三方依赖、构建缓存、历史备份和游戏存档。它来自没有 Git 元数据的本地工作目录，因此暂未确认对应的上游 commit。GitHub Fork： [huiyeSam/PersonalSpace](https://github.com/huiyeSam/PersonalSpace)。
