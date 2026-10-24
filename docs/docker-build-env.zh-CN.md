# Docker 构建环境（192.168.5.90 / DS920+）

在群晖 NAS 上用 Docker 复用已有的 Android 模拟器镜像，搭建本项目（qbitremote）的
完整构建环境。**无需拉取任何新镜像**，全部复用机器上已存在的 `android-emu:36`。

本文记录的是**实测通过**的配置，不是设想。当前版本已于 2026-10-23 按新挂载约束
（挂载 `/volume2/docker/android` 整目录 + 子目录外挂）重建容器并重新跑通全链路。

---

## 1. 结论速览

| 项目 | 值 |
| --- | --- |
| 主机 | `192.168.5.90`（群晖 DS920+，Docker 走 `/usr/local/bin/docker`） |
| 复用镜像 | `android-emu:36`（6.8 GB，`linux/amd64`，镜像内已含 JDK 17.0.20.1） |
| 容器名 | `android-build` |
| 容器内工作目录 | `/volume2/docker/android/project/qbitremote` |
| 单测 `testDebugUnitTest` | **4 分 50 秒**，306 个用例全通过（首次基线） |
| Lint `lintDebug` | **3 分 52 秒**，0 error / 1 warning |
| Debug 构建 `assembleDebug` | **1 分 57 秒**，产出 24 MB APK |
| 增量（无改动）`assembleDebug` | **12 秒** |
| 一条命令跑全套（容器内缓存已预热） | `testDebugUnitTest lintDebug assembleDebug` 合计 **3 分 31 秒**（Gradle 9.8.1，`--no-daemon`） |
| 单元测试 | **334 个，0 失败 / 0 错误 / 0 跳过**（69 个测试类 XML）——含种子列表筛选功能的 28 个新用例 |
| Lint | **0 error，1 warning**（仅 `OldTargetApi`，见 §6） |
| 产物 | `/volume2/docker/android/project/qbitremote/app/build/outputs/apk/debug/app-debug.apk`（25,405,442 字节） |

**结论：环境可用于后续所有验证**（单测 / lint / debug 构建），改动代码后 scp 推送即可，
无需重建容器。

---

## 2. 为什么复用 `android-emu:36`

机器上共 19 个镜像，但只有 `android-emu:36` 带 Android SDK。它是唯一能承担构建的镜像：

- 自带 JDK 17.0.20.1（Ubuntu build）、`curl`、`unzip`、`tar`、`python3 3.12.3`；
  **没有 `git` 和 `rsync`**。
- `/opt/android-sdk` 里只有 `cmdline-tools`、`emulator`、`licenses`、`platform-tools`、
  `system-images`——**没有 `platforms/` 与 `build-tools/`**，两者由 Gradle 首次构建时
  经 sdkmanager 自动下载（见 §4）。

其余镜像（moviepilot、openlist、qbittorrent、transmission、emby、homepage、ubuntu:24.04 等）
只是 linuxserver 系应用镜像，不含构建链，不予考虑。

### SDK 版本的坑

项目 `app/build.gradle.kts` 要求 `compileSdk = 37`、`minSdk = 26`、`targetSdk = 36`，
但 **sdkmanager 中不存在 `platforms;android-37` 这个包**——实际包名是
`platforms;android-37.0`（revision 2）；AGP 9.4.1 默认 build-tools 是 `36.0.0`。

解决办法不是重建镜像，而是**把 `platforms` 与 `build-tools` 目录外挂到宿主机**（见 §3），
让 sdkmanager 的自动安装结果直接落盘持久化。

---

## 3. 容器配置（实测可用）

### 3.1 宿主机预置目录

```bash
mkdir -p /volume2/docker/android/{project/qbitremote,gradle-home,platforms,build-tools}
chmod 777 /volume2/docker/android/{project/qbitremote,gradle-home,platforms,build-tools}
```

### 3.2 创建容器

关键约束：**只挂宿主机 `/volume2/docker/android` 这一个目录本身**，其余卷都是它下面的
子目录（因此三个 mount 的宿主侧路径全部以 `/volume2/docker/android/` 开头）：

```bash
docker run -d --name android-build \
  --workdir /volume2/docker/android/project/qbitremote \
  -v /volume2/docker/android:/volume2/docker/android \
  -v /volume2/docker/android/platforms:/opt/android-sdk/platforms \
  -v /volume2/docker/android/build-tools:/opt/android-sdk/build-tools \
  -e GRADLE_USER_HOME=/volume2/docker/android/gradle-home \
  -e ANDROID_HOME=/opt/android-sdk \
  -e ANDROID_SDK_ROOT=/opt/android-sdk \
  -e LANG=C.UTF-8 \
  -e DEBIAN_FRONTEND=noninteractive \
  --entrypoint bash \
  android-emu:36 -lc 'sleep infinity'
```

最终 `HostConfig.Binds`（实测）：

```
/volume2/docker/android:/volume2/docker/android
/volume2/docker/android/platforms:/opt/android-sdk/platforms
/volume2/docker/android/build-tools:/opt/android-sdk/build-tools
```

要点：

- **挂 android 整目录本身**：`project/qbitremote` 与 `gradle-home` 都在它下面，
  容器内外路径完全一致（`/volume2/docker/android/...`），排查时不用做路径换算。
- `platforms` / `build-tools` 单独外挂（容器内仍是 `/opt/android-sdk/{platforms,build-tools}`），
  Gradle 触发的 SDK 自动安装才能持久化到宿主机。
- `GRADLE_USER_HOME` 外挂；当前 `gradle-home` **1.4 GB**，换容器不用重下依赖。
- `ANDROID_HOME` 与 `ANDROID_SDK_ROOT` 两个都设，兼容不同 AGP 版本。
- `--entrypoint bash` + `sleep infinity` 常驻，构建一律 `docker exec` 进入；
  **不要用镜像默认的 `/usr/local/bin/entry.sh`**，它会拉起模拟器长期占 CPU。
- 重启策略为默认 `no`：NAS 重启后需手动 `docker start android-build`。

### 3.3 进入容器

```bash
docker exec -it android-build bash -lc 'cd /volume2/docker/android/project/qbitremote && bash'
```

---

## 4. 构建与测试

在容器内工作目录（宿主机 `/volume2/docker/android/project/qbitremote`）执行：

```bash
./gradlew --no-daemon testDebugUnitTest    # 4m50s，334 个单测
./gradlew --no-daemon lintDebug            # 3m52s，0 error / 1 warning
./gradlew --no-daemon assembleDebug        # 1m57s，24 MB debug APK
./gradlew --no-daemon assembleDebug        # 12s（无改动增量）
```

### SDK 自动安装行为（实测日志）

首次构建时 AGP 通过 sdkmanager 自动补装缺失的 SDK 组件，**直接写入外挂目录**：

```
Checking the license for package Android SDK Build-Tools 36 in /opt/android-sdk/licenses
License for package Android SDK Build-Tools 36 accepted.
Installing Android SDK Build-Tools 36 in /opt/android-sdk/build-tools/36.0.0
...
Checking the license for package Android SDK Platform 37.0 in /opt/android-sdk/licenses
Installing Android SDK Platform 37.0 in /opt/android-sdk/platforms/android-37.0
```

落盘后宿主机占用：`platforms` 153 MB、`build-tools` 147 MB、`gradle-home` 1.4 GB。
镜像自带 `licenses/` 已含所需授权，**不需要人工 `sdkmanager --licenses`**。

### 工具链版本（由项目自身决定，环境只需满足）

| 组件 | 版本 | 来源 |
| --- | --- | --- |
| JDK | 17.0.20.1（Ubuntu build） | 镜像自带 |
| Gradle | 9.8.1 | `gradle/wrapper/gradle-wrapper.properties`，wrapper 自动下载并校验 |
| AGP | 9.4.1 | 根 `build.gradle.kts` |
| Kotlin + Compose 插件 | 2.4.21 | 根 `build.gradle.kts` |
| Build-Tools | 36.0.0 | sdkmanager 首次构建时自动装到外挂目录 |
| Platform | `android-37.0`（rev 2） | 同上 |

构建日志里的正常噪声（不必处理）：

- `Unable to strip the following libraries ... libandroidx.graphics.path.so` —— 缺 NDK strip 工具，AGP 直接原样打包，不影响运行。
- `Deprecated Gradle features were used in this build, making it incompatible with Gradle 10` —— Gradle 9.8.1 对 AGP 9.x 的常规提示。
- 单测编译期的 `ExperimentalCoroutinesApi` opt-in 警告 —— 测试代码既有问题，不阻塞。

---

## 5. 代码同步方式

容器直接挂载宿主机目录，宿主机上**没有 `.git`**，也**没有 `git`/`rsync` 可执行文件**，
只能从开发机推文件。

**必须加 `-O`（走旧版 scp 协议）**：NAS 的 sshd 未启用 sftp 子系统，
直接 `scp -r` 会报 `subsystem request failed on channel 0` / `Connection closed`。

```powershell
# 在 D:\项目\qbitremote 下
scp -O -r app gradle licenses huyk@192.168.5.90:/volume2/docker/android/project/qbitremote/
scp -O build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat lint.xml `
      huyk@192.168.5.90:/volume2/docker/android/project/qbitremote/
```

同步清单：`app`、`build.gradle.kts`、`settings.gradle.kts`、`gradle`、`gradle.properties`、
`gradlew`、`gradlew.bat`、`lint.xml`、`licenses`。
**不要同步** `keystore.properties`、`.git`、`dist`、`tmp`、`.agent-teams`。

### 两个必踩的坑

1. **`gradlew` 的 CRLF**：从 Windows scp 过去后首行是 `#!/bin/sh\r`，
   直接执行报 `./gradlew: cannot execute: required file not found`（exit 127）。
   同步后执行一次转换：

   ```bash
   docker exec android-build bash -lc \
     'cd /volume2/docker/android/project/qbitremote && sed -i "s/\r$//" gradlew gradlew.bat && chmod +x gradlew'
   ```

2. **首次 wrapper 下载**：`gradle-home` 为空时，wrapper 会从 services.gradle.org
   下载 `gradle-9.8.1-bin.zip`（约 1 分钟，视出口带宽），并把 `GRADLE_USER_HOME`
   撑到约 1.4 GB。之后复用。

---

## 6. 已知限制

- **只能做 debug 构建**。release 需要签名配置（`keystore.properties` + keystore 文件），
  按项目硬性规定签名身份固定在 owner 的 Windows 机器上，不应转移到 NAS。
  可在 NAS 上跑 `assembleRelease` 但产物未签名，仅用于验证 R8 配置能否通过——这一步 CI 已经在做。
- **Lint 的 1 个 warning**：`app/build.gradle.kts` 的 `targetSdk = 36` 触发 `OldTargetApi`
  （因为 `compileSdk = 37`）。这是项目现状，不是环境问题。
- **容器 root 身份写文件**：`docker exec` 默认是 root，构建产物所有者是 `root:root`；
  宿主机侧同步来的源码是 `1026/users`。混合读写不会报错，但宿主机上直接用 `huyk`
  改文件后要留意权限（`chmod -R a+rwX` 可一次性放开）。
- 镜像 `android-emu:36` 内含 4.3 GB 的 `system-images`，构建用不上。若将来要精简，
  可以据此裁一个新镜像，但当前刻意不裁，以免破坏已有模拟器用途。

---

## 7. 日常命令速查

```bash
# 看容器状态（重启 NAS 后要手动 start）
docker ps --filter name=android-build
docker start android-build

# 跑全套校验（约 11 分钟；建议分开跑便于定位）
docker exec android-build bash -lc 'cd /volume2/docker/android/project/qbitremote && ./gradlew --no-daemon testDebugUnitTest'
docker exec android-build bash -lc 'cd /volume2/docker/android/project/qbitremote && ./gradlew --no-daemon lintDebug'
docker exec android-build bash -lc 'cd /volume2/docker/android/project/qbitremote && ./gradlew --no-daemon assembleDebug'

# 精确校验命令（一条命令判环境是否仍可用）
docker exec android-build bash -lc 'cd /volume2/docker/android/project/qbitremote && ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug'

# 取产物到宿主机
docker cp android-build:/volume2/docker/android/project/qbitremote/app/build/outputs/apk/debug/app-debug.apk /volume2/docker/android/

# 清理 Gradle 缓存（下次构建会重新下载，约 +6 分钟）
rm -rf /volume2/docker/android/gradle-home/caches
```
