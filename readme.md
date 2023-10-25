# playground

## Introduction
This is a template repository for those who want to develop RTL based on rocket-chip and even chipyard, being able to edit all sources from chisel environments without publish them to local ivy.
You can add your own submodule in `build.sc`.  
For more information please visit [Mill documentation](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html)
after adding your own code, you can add your library to playground dependency, and re-index Intellij to add your own library.


## Quick Start

To use this repo as your Chisel development environment, simply follow the steps.

Ubuntu 20.04 packages needed:
``` bash
$ sudo apt-get install openjdk-11-jdk device-tree-compiler verilator gtkwave
```

Download `firtool` from [release](https://github.com/llvm/circt/releases/tag/firtool-1.57.0), unzip and add to PATH.
```bash
$ export PATH=<firtool-download-path>/circt-full-shared-linux-x64/firtol-1.56.1/bin:$PATH
```

Clone the code
```bash
  $ git clone https://github.com/madhava-c/playground.git
  $ cd playground
  $ git submodule update --init
```

### Did it work?
You should now have a working chisel development environment that can generate verilog.
```bash
  $ make rtl
```
The above command should generate verilog code for Rocketchip with `freechips.rocketchip.system.DefaultConfig` in the path `./out/playground/verilog.dest`.


```bash
cd playground # entry your project directory
vim build.sc
```

```scala
// build.sc

// Original
object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, inclusivecache, blocks, rocketdsputils, shells, firesim, boom, chipyard, chipyard.fpga, chipyard.utilities, mychiseltest)
  ...
}

// Remove unused dependences, e.g.,
object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(mychiseltest)
  ...
}
```


## IDE support
For mill use
```bash
mill mill.bsp.BSP/install
```
then open by your favorite IDE, which supports [BSP](https://build-server-protocol.github.io/) 

## Why not Chipyard

1. ~~Building Chisel and FIRRTL from sources, get rid of any version issue. You can view Chisel/FIRRTL source codes from IDEA.~~
1. No more make+sbt: Scala dependencies are managed by mill -> bsp -> IDEA, minimal IDEA indexing time.
1. flatten git submodule in dependency, get rid of submodule recursive update.

So generally, this repo is the fast and cleanest way to start your Chisel project codebase.


