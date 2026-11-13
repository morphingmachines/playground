# playground

A shared base for Chisel RTL development on rocket-chip and chipyard sources. Shared dependencies are git submodules under `dependencies/` and are built from source with [Mill](https://mill-build.org), so you can edit them directly without publishing to a local ivy repository.

## Prerequisites

Ubuntu packages:
```bash
sudo apt-get install openjdk-11-jdk device-tree-compiler verilator gtkwave
```

`firtool` from the [CIRCT releases](https://github.com/llvm/circt/releases). The version must match the Chisel version you build with:

| Chisel | Scala | firtool |
|---|---|---|
| 6.7.0 (default) | 2.13.12 | [1.62.1](https://github.com/llvm/circt/releases/tag/firtool-1.62.1) |
| 7.13.0 (`USE_CHISEL7`) | 2.13.18 | [1.149.0](https://github.com/llvm/circt/releases/tag/firtool-1.149.0) |

Unpack it next to the playground checkout (`<workspace>/firtool-1.62.1/bin/firtool`). Make puts the matching version first on `PATH` automatically. Set `FIRTOOL_BIN=<dir>` to use a different location. `make rtl` checks that the `firtool` it finds is the expected version.

## Quick start

```bash
git clone https://github.com/morphingmachines/playground.git
cd playground
git submodule update --init
make rtl
```

This elaborates `freechips.rocketchip.system.ExampleRocketSystem` with `playground.PlaygroundConfig` (rocket-chip `DefaultConfig` plus the inclusive L2 cache) and writes split Verilog to `out/playground/verilog.dest`.

## Make targets

| Target | Action |
|---|---|
| `make compile` | Compile all modules |
| `make rtl` | Elaborate and run firtool |
| `make console` | Scala REPL with the `playground` classpath |
| `make bsp` | Generate BSP config for IDEs |
| `make clean` | Remove `out/` |

## Chisel 7

Chisel 6.7.0 is the default. Setting the `USE_CHISEL7` environment variable switches to Chisel 7.13.0, matching chipyard's `USE_CHISEL7` option. For what changes for RTL, verification and physical design, see the [Chisel 7 migration notes](docs/chisel7-migration.md).

```bash
USE_CHISEL7=1 make rtl
```

- Only presence matters: `USE_CHISEL7=` (empty) also selects Chisel 7, in both make and mill.
- To use it for every build, add `export USE_CHISEL7 := 1` to the `Makefile`. This only applies when building through `make`.
- When the Chisel version changes, make stops this repo's mill server, because a running server keeps the old version. Calling `./mill` directly skips that, so run `./mill shutdown` yourself before changing `USE_CHISEL7`.
- `out/` holds builds for one Chisel version only. After switching versions, run `rm -rf out`. The build stops with an error if `out/chisel-version` does not match.

Known issue: with Chisel 7 and firtool 1.149.0, `make rtl` on the bare `ExampleRocketSystem` fails with `public module port must have concrete reset type`. This happens because the `io.aggregator_*` clock-group resets are not driven by a harness.

## Using playground from a consumer repo

Your RTL lives in a consumer repo: its own repository, checked out next to playground. A consumer repo reuses playground's toolchain, its shared dependencies (rocket-chip, diplomacy, cde, ...) and its make targets:

```
<workspace>/
├── playground/           # this repo: shared deps, builddefs.sc, playground_modules.sc, Makefile.include, mill
├── firtool-1.62.1/       # found automatically by Makefile.include
├── firtool-1.149.0/
└── myrepo/
    ├── build.sc
    ├── Makefile
    ├── playground.hash   # playground commit this consumer repo was tested against
    ├── dependencies/     # local dependencies (your own IP blocks)
    └── src/main/scala/   # your Chisel sources, src/test/scala for tests
```

### build.sc

Import playground's build definitions and shared modules. Then declare your own modules on top of them:

```scala
import $file.^.playground.{builddefs => playground_build}
import $file.^.playground.playground_modules

playground_build.ivys.checkOutDir()   // refuse an out/ built with the other Chisel version

object root extends RootModule with playground_modules.PlaygroundModules {

  // a local dependency under myrepo/dependencies/
  object myip extends playground_build.CommonModule with SbtModule {
    override def millSourcePath = os.pwd / "dependencies" / "MyIP"
    override def moduleDeps = super.moduleDeps ++ Seq(mycde, mydiplomacy, myrocketchip, emitrtl)
  }

  // the consumer repo itself
  object myrepo extends playground_build.CommonModule with SbtModule {
    override def millSourcePath = os.pwd
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, testchipip, inclusivecache, emitrtl, myip)

    object test extends SbtModuleTests with TestModule.ScalaTest {
      override def ivyDeps = super.ivyDeps() ++ Agg(playground_build.ivys.scalatest)
    }
  }
}
```

- `playground_build.CommonModule` sets the Scala version and the Chisel library and compiler plugin, following `USE_CHISEL7`.
- `PlaygroundModules` provides `macros`, `mycde`, `mydiplomacy`, `myrocketchip`, `myhardfloat`, `inclusivecache`, `blocks`, `testchipip`, `chipyardAnnotations`, `chipyardTapeout` and `emitrtl`, all built from `../playground/dependencies`.
- `RootModule` keeps the module names at the top level, as in `mill myrepo.compile`.
- Optionally mix in `ScalafmtModule` and `ScalafixModule` so that `make lint` and the pre-commit hook work.

### Makefile

Use playground's mill launcher and include its shared targets:

```make
project = myrepo
TARGET ?= MyTop
MILL = ./../playground/mill

-include ./../playground/Makefile.include

rtl: check-firtool ## Generate Verilog into ./generated_sv_dir
	$(MILL) $(project).runMain $(project).rtlMain $(TARGET)

test: ## Run Chisel tests
	$(MILL) $(project).test
```

`Makefile.include` provides `check-firtool`, `check-verilator`, `lint`, `lint-test`, `console`, `scaladoc`, `clean`, and `install-hooks`/`check-hooks`, which route git hooks to `playground/git-hooks`. It also handles switching Chisel version and picking the matching firtool.

### Elaboration entry point

`emitrtl` wraps elaboration and firtool. A main object picks a top and config by name, then runs the flow:

```scala
package myrepo

import emitrtl._
import org.chipsalliance.cde.config.Config
import org.chipsalliance.diplomacy.lazymodule.LazyModule

object rtlMain extends App with LazyToplevel {
  val str       = if (args.length == 0) "" else args(0)
  var configStr = ""
  val lazyTop = str match {
    case "MyTop" =>
      configStr = "myrepo:MyConfig"
      LazyModule(new MyTop()(new Config(new MyConfig)))
    case _ => throw new Exception("Unknown Module Name!")
  }

  override def configString = configStr
  showModuleComposition(lazyTop)   // print the LazyModule hierarchy
  chisel2firrtl()                  // elaborate to .fir
  firrtl2sv()                      // firtool -> split SystemVerilog
  genDiplomacyGraph()              // .graphml of the diplomacy graph
}
```

Output goes to `generated_sv_dir/<top>.<config>/`, and the SystemVerilog goes to `chisel_gen_rtl/` inside it. Mix in `emitrtl.SynthToplevel` for the synthesis flow, which adds memory macro handling through chipyard's MacroCompiler. Tests can extend `emitrtl.VerilatorSimulator` to run ChiselSim on Verilator with VCD traces.

### Day-to-day

```bash
cd myrepo
make rtl TARGET=MyTop        # elaborate + firtool
make test                    # unit tests
make lint                    # scalafmt + scalafix
make install-hooks           # once per clone: lint on commit
USE_CHISEL7=1 make rtl       # same flow on Chisel 7 (rm -rf out when switching)
```

To change a shared dependency such as rocket-chip, edit it in `playground/dependencies/` and rebuild the consumer repo. Nothing needs to be published. A consumer repo can record the playground commit it was tested against in a `playground.hash` file.

## Changing playground itself

Add a module to `build.sc` that depends on what you need, and trim `playground.moduleDeps` to the dependencies you use:

```scala
object playground extends builddefs.CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(mycde, mydiplomacy, myrocketchip, inclusivecache, blocks, shells)
  ...
}
```

To change what `make rtl` elaborates, edit `lazymodule` and `configs` in the `playground` object. See the [Mill documentation](https://mill-build.org) for build syntax.

## IDE support

```bash
make bsp   # or: ./mill -i mill.bsp.BSP/install
```

Then open the repository in any IDE that supports [BSP](https://build-server-protocol.github.io/), for example IntelliJ or VS Code with Metals.

## Why not build inside chipyard

Playground reuses chipyard pieces (stage, tapeout/MacroCompiler, its Chisel version choice) without making your consumer repo live inside chipyard.

1. One shared base, many consumer repos: each is its own repository next to playground and reuses its shared dependencies, build definitions, make targets and git hooks.
2. Mill instead of sbt: dependencies flow through Mill and BSP to the IDE, so indexing is fast.
3. Flat submodules under `dependencies/`: `git submodule update --init` is enough, with no recursive update.
4. Light setup: a JDK, firtool and Verilator. No conda environment, RISC-V toolchain build or FireSim.
