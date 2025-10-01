# 仓颉包管理工具开发者指南

## 开源项目介绍

`cjpm（cangjie package manager）`是编译仓颉项目的构建工具，该工具使用仓颉语言实现，提供项目创建、项目编译、项目运行、单元测试等能力，其中项目支持多模块自动分析依赖，完成顺序构建。其设计目标是简化用户的工作流，提升跨平台开发效率，并提供高度可定制的配置选项。本工具的整体架构如图所示：

![cjfmt架构设计图](cjpm架构.jpg)

## 目录

`cjpm` 源码目录如下所示，其主要功能如注释中所描述。

```
cjpm/src
|-- command       # 命令功能代码
|-- config        # 配置内容代码
|-- implement     # 内部接口实现代码
|-- toml          # toml 文件读写功能代码
`-- main.cj       # 程序主入口
```

## 安装和使用指导

### 构建准备

`cjpm` 需要以下工具来构建：

- `cangjie SDK`
    - 开发者需要下载对应平台的 `cangjie SDK`：若想要编译本地平台产物，则需要的 `SDK` 为当前平台对应的版本；若想要从 `Linux` 平台交叉编译获取 `Windows` 平台的产物，则需要的 `SDK` 为 `Linux` 版本。
    - 然后，开发者需要执行对应 `SDK` 的 `envsetup` 脚本，确保 `SDK` 被正确配置。
    - 若使用 `python` 构建脚本方式编译，可以使用自行编译的 `cangjie SDK`。请参阅 [cangjie SDK](https://gitcode.com/Cangjie/cangjie_build) 了解源码编译 `cangjie SDK` 的方法。
- 与 `cangjie SDK` 配套的 `stdx` 二进制库
    - 开发者需要下载目标平台的 `stdx` 二进制库，或通过 `stdx` 源码编译出对应的 `stdx` 二进制库：若想要编译本地平台产物，则需要的 `stdx` 库为当前平台对应的版本；若想要从 `Linux` 平台交叉编译获取 `Windows` 平台的产物，则需要的 `stdx` 库为 `Windows` 版本。
    - 然后，开发者需要将 `stdx` 二进制库路径配置到环境变量 `CANGJIE_STDX_PATH` 中。若开发者直接下载 `stdx` 二进制库，则路径为解压得到的库目录下的 `static/stdx` 目录；若开发者通过 `stdx` 源码编译，则路径为 `stdx` 编译产物目录 `target` 下对应平台目录下的 `static/stdx` 目录，例如 `Linux-x86` 下为 `target/linux_x86_64_cjnative/static/stdx`。请参阅 [stdx 仓](https://gitcode.com/Cangjie/cangjie_stdx) 获取源码编译 `stdx` 库的方法。
    - 此外，如果开发者想使用 `stdx` 动态库作为二进制库依赖，可以将上述路径配置中的 `static` 改为 `dynamic`。以此方式编译的 `cjpm` 无法独立运行，若想让其能够独立运行，需要将相同的 `stdx` 库路径配置到系统动态库环境变量中。
- 若使用 `python` 构建脚本方式编译，请安装 `python3`

### 构建步骤

#### 构建方式一：使用 `cjpm` 编译

`cjpm` 源码本身是一个 `cjpm` 模块。因此，可以使用 `cangjie SDK` 中的 `cjpm` 编译 `cjpm` 源码。配置完 `SDK` 和 `stdx` 后，开发者可以通过如下命令编译：

```
cd ${WORKDIR}/cangjie-tools/cjpm
cjpm build -o cjpm
```

编译出来的 `cjpm` 可执行文件目录如下：

```
// Linux/macOS
cjpm/target/release/bin
`-- cjpm

// Windows
cjpm/target/release/bin
`-- cjpm.exe
```

目前源码仓中的 `cjpm.toml` 支持编译如下平台的 `cjpm`：

- `Linux/macOS` 的 `x86/arm` 框架；
- `Windows x86`。

此外，若开发者想在 `Linux` 系统中交叉编译 `Windows` 平台产物，可以使用如下命令：

```
cd ${WORKDIR}/cangjie-tools/cjpm
cjpm build -o cjpm --target x86_64-w64-mingw32
```

编译出来的 `cjpm` 可执行文件目录如下：

```
cjpm/target/x86_64-w64-mingw32/release/bin
`-- cjpm.exe
```

#### 构建方式二：使用构建脚本编译

开发者可以使用 `build` 目录下的构建脚本 `build.py` 进行 `cjpm` 构建。若想使用 `build.py` 构建，开发者可以配置 `cangjie SDK` 和 `stdx` 库，也可以使用自行编译的 `cangjie SDK` 和 `stdx`。

构建流程如下：

1. 通过 `cjpm/build` 目录下的构建脚本 `build.py` 编译 `cjpm`：

    ```shell
    cd ${WORKDIR}/cangjie-tools/cjpm/build
    python3 build.py build -t release [--target native]        (for Linux/Windows/macOS)
    python3 build.py build -t release --target windows-x86_64  (for Linux-to-Windows)
    ```

    开发者需要通过 `-t, --build-type` 指定构建产物版本，可选值为 `debug/release`。

    编译成功后，`cjpm` 可执行文件会生成在 `cjpm/dist/` 目录中。

2. 修改环境变量：

    ```shell
    export PATH=${WORKDIR}/cangjie-tools/cjpm/dist:$PATH     (for Linux/macOS)
    set PATH=${WORKDIR}/cangjie-tools/cjpm/dist;%PATH%       (for Windows)
    ```

3. 验证 `cjpm` 是否安装成功：

    ```shell
    cjpm -h
    ```

    如果输出 `cjpm` 的帮助信息，则表示安装成功。

> **注意：**
>
> `Linux/macOS` 系统中，开发者可以将 `runtime` 动态库的路径配置到 `rpath` 中，以使编译出来的 `cjpm` 可以不通过系统动态库环境变量就能找到 `runtime` 动态库：
> - 使用 `cjpm` 编译时，可以通过给 `cjc` 添加编译选项 `--set-runtime-rpath` 使编译出来的 `cjpm` 可以找到当前使用的 `cangjie SDK` 的 `runtime` 动态库。请在 `cjpm.toml` 中将这一编译选项添加到 `compile-option` 配置项中，从而使该编译选项应用于 `cjc` 编译命令中。
> - 使用构建脚本 `build.py` 编译时，可以通过添加参数 `--set-rpath RPATH` 配置 `rpath` 的路径。
> - `cangjie SDK` 中，`runtime` 动态库目录位于 `$CANGJIE_HOME/runtime/lib` 目录，基于运行平台划分成若干子目录，可以使用对应运行平台的库目录用于配置 `rpath`。

### 更多构建选项

`build.py` 的 `build` 功能提供如下额外选项：
- `--target TARGET`: 指定编译目标产物的运行平台，默认值为 `native`，即本地平台，当前仅支持 `linux` 平台上通过 `--target windows-x86_64` 交叉编译 `windows-x86_64` 平台的产物；
- `--set-rpath RPATH`: 指定 `rpath` 路径；
- `-t, --build-type BUILD_TYPE`: 指定构建产物版本，可选值为 `debug/release`；
- `-h, --help`: 打印 `build` 功能的帮助信息。

此外，`build.py` 还提供如下额外功能：

- `install [--prefix PREFIX]`: 将构建产物安装到指定路径，不指定路径时默认为 `cjpm/dist/` 目录；`install` 前需要先正确执行 `build`；
- `clean`: 清除默认路径的构建产物；
- `-h, --help`: 打印 `build.py` 的帮助信息。

## API 和配置说明

`cjpm` 提供以下主要命令和 `toml` 配置文件，用于项目构建和配置管理。

### 命令介绍

执行 `cjpm -h` 后，会打印 `cjpm` 的命令列表，如下所示：

```text
Cangjie Package Manager

Usage:
  cjpm [subcommand] [option]

Available subcommands:
  init             Init a new cangjie module
  check            Check the dependencies
  update           Update cjpm.lock
  tree             Display the package dependencies in the source code
  build            Compile the current module
  run              Compile and run an executable product
  test             Unittest a local package or module
  bench            Run benchmarks in a local package or module
  clean            Clean up the target directory
  install          Install a cangjie binary
  uninstall        Uninstall a cangjie binary

Available options:
  -h, --help       help for cjpm
  -v, --version    version for cjpm

Use "cjpm [subcommand] --help" for more information about a command.
```

常用命令介绍如下：

1. `init` 命令：

    `init` 用于初始化一个新的仓颉模块或者工作空间。初始化模块时，默认在当前文件夹创建配置文件 `cjpm.toml`，并新建 `src` 源码文件夹。初始化工作空间时仅会创建 `cjpm.toml` 文件，默认会扫描该路径下已有的仓颉模块并添加到 `members` 字段中。若已存在 `cjpm.toml` 文件，或源码文件夹内已存在 `main.cj`，则会跳过对应的文件创建步骤。

2. `build` 命令：

    `build` 用于构建当前仓颉项目，执行该命令前会先检查依赖项，检查通过后调用 `cjc` 进行构建。

3. `run` 命令：

    `run` 用于运行当前仓颉项目构建出的二进制产物。`run` 命令会默认执行 `build` 命令的流程，以生成最终用于运行的二进制文件。

4. `clean` 命令：

    `clean` 命令会清除当前仓颉项目的编译产物。

### 配置管理

模块配置文件 `cjpm.toml` 用于配置一些基础信息、依赖项、编译选项等内容，`cjpm` 主要通过这个文件进行解析执行。

配置文件代码如下所示：

```toml
[package] # 单模块配置字段，与 workspace 字段不能同时存在
  cjc-version = "1.0.0" # 所需 `cjc` 的最低版本要求，必需
  name = "demo" # 模块名及模块 root 包名，必需
  description = "nothing here" # 描述信息，非必需
  version = "1.0.0" # 模块版本信息，必需
  compile-option = "" # 额外编译命令选项，非必需
  override-compile-option = "" # 额外全局编译命令选项，非必需
  link-option = "" # 链接器透传选项，可透传安全编译命令，非必需
  output-type = "executable" # 编译输出产物类型，必需
  src-dir = "" # 指定源码存放路径，非必需
  target-dir = "" # 指定产物存放路径，非必需
  package-configuration = {} # 单包配置选项，非必需

[workspace] # 工作空间管理字段，与 package 字段不能同时存在
  members = [] # 工作空间成员模块列表，必需
  build-members = [] # 工作空间编译模块列表，需要是成员模块列表的子集，非必需
  test-members = [] # 工作空间测试模块列表，需要是编译模块列表的子集，非必需
  compile-option = "" # 应用于所有工作空间成员模块的额外编译命令选项，非必需
  override-compile-option = "" # 应用于所有工作空间成员模块的额外全局编译命令选项，非必需
  link-option = "" # 应用于所有工作空间成员模块的链接器透传选项，非必需
  target-dir = "" # 指定产物存放路径，非必需

[dependencies] # 源码依赖配置项，非必需
  coo = { git = "xxx",branch = "dev" } # 导入 `git` 依赖
  doo = { path = "./pro1" } # 导入源码依赖

[test-dependencies] # 测试阶段的依赖配置项，格式和 dependencies 相同，非必需

[script-dependencies] # 构建脚本的依赖配置项，格式和 dependencies 相同，非必需

[replace] # 依赖替换配置项，格式和 dependencies 相同，非必需

[ffi.c] # 导入 C 语言的库依赖，非必需
  clib1.path = "xxx"

[profile] # 命令剖面配置项，非必需
  build = {} # build 命令配置项
  test = {} # test 命令配置项
  bench = {} # bench 命令配置项
  customized-option = {} # 自定义透传选项

[target.x86_64-unknown-linux-gnu] # 后端和平台隔离配置项，非必需
  compile-option = "value1" # 额外编译命令选项，适用于特定 target 的编译流程和指定该 target 作为交叉编译目标平台的编译流程，非必需
  override-compile-option = "value2" # 额外全局编译命令选项，适用于特定 target 的编译流程和指定该 target 作为交叉编译目标平台的编译流程，非必需
  link-option = "value3" # 链接器透传选项，适用于特定 target 的编译流程和指定该 target 作为交叉编译目标平台的编译流程，非必需

[target.x86_64-w64-mingw32.dependencies] # 适用于对应 target 的源码依赖配置项，非必需

[target.x86_64-w64-mingw32.test-dependencies] # 适用于对应 target 的测试阶段依赖配置项，非必需

[target.x86_64-unknown-linux-gnu.bin-dependencies] # 仓颉二进制库依赖，适用于特定 target 的编译流程和指定该 target 作为交叉编译目标平台的编译流程，非必需
  path-option = ["./test/pro0", "./test/pro1"] # 以文件目录形式配置二进制库依赖
[target.x86_64-unknown-linux-gnu.bin-dependencies.package-option] # 以单文件形式配置二进制库依赖
  "pro0.xoo" = "./test/pro0/pro0.xoo.cjo"
  "pro0.yoo" = "./test/pro0/pro0.yoo.cjo"
  "pro1.zoo" = "./test/pro1/pro1.zoo.cjo"
```

其中，常用字段的说明如下：

1. 依赖配置

    - `dependencies` 字段：

        该字段通过源码方式导入依赖的其它仓颉模块，里面配置了当前构建所需要的其它模块的信息。目前，该字段支持本地路径依赖和远程 `git` 依赖。

        ```toml
        [dependencies]
        pro0 = { path = "./pro0" }
        ```

    - `ffi.c` 字段：

        该字段用于进行当前仓颉模块外部依赖 `c` 库的配置。该字段配置了依赖该库所需要的信息，包含库名和路径。

        ```toml
        [ffi.c]
        hello = { path = "./src/" }
        ```

    - `target.[target-name].bin-dependencies` 字段：

        该字段用于导入已编译好的、适用于指定 `target` 的仓颉库产物文件。仓颉二进制依赖需要用 `target` 字段进行平台隔离。

        ```toml
        [target.x86_64-unknown-linux-gnu.bin-dependencies]
            path-option = ["./test/pro0", "./test/pro1"]
        [target.x86_64-unknown-linux-gnu.bin-dependencies.package-option]
            "pro0.xoo" = "./test/pro0/pro0.xoo.cjo"
            "pro0.yoo" = "./test/pro0/pro0.yoo.cjo"
            "pro1.zoo" = "./test/pro1/pro1.zoo.cjo"
        ```

2. 跨平台配置

    `cjpm.toml` 使用 `target` 字段进行跨平台配置。同一项目在不同平台上编译时，会应用不同的配置。

    ```toml
    [target.x86_64-unknown-linux-gnu]
        compile-option = "value1"
        override-compile-option = "value2"
        link-option = "value3"
    [target.x86_64-unknown-linux-gnu.dependencies]
    [target.x86_64-unknown-linux-gnu.test-dependencies]
    [target.x86_64-unknown-linux-gnu.bin-dependencies]
        path-option = ["./test/pro0", "./test/pro1"]
    [target.x86_64-unknown-linux-gnu.bin-dependencies.package-option]
        "pro0.xoo" = "./test/pro0/pro0.xoo.cjo"
        "pro0.yoo" = "./test/pro0/pro0.yoo.cjo"
        "pro1.zoo" = "./test/pro1/pro1.zoo.cjo"
    [target.x86_64-unknown-linux-gnu.bin-dependencies.ffi.c]
        "ctest" = "./test/c"

    [target.x86_64-unknown-linux-gnu.debug]
        [target.x86_64-unknown-linux-gnu.debug.test-dependencies]

    [target.x86_64-unknown-linux-gnu.release]
        [target.x86_64-unknown-linux-gnu.release.bin-dependencies]
    ```

### 其他功能

除了上述命令和配置项之外，`cjpm` 还支持一些其他功能，例如构建脚本、命令扩展等。

若想获取 `cjpm` 所有命令、配置项和附加功能的详细信息，请参阅[《仓颉包管理工具用户指南》](user_guide.md)。

## 相关仓

- [cangjie 仓](https://gitcode.com/Cangjie/cangjie_compiler)
- [stdx 仓](https://gitcode.com/Cangjie/cangjie_stdx)
- [cangjie SDK](https://gitcode.com/Cangjie/cangjie_build)