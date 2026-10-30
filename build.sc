// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.scalafmt.ScalafmtModule
//import mill.scalalib.SbtModule
import mill.scalalib._
import mill.scalalib.publish._
// Hack
// support BSP
import mill.bsp._
// input build.sc from each repositories.

import $file.builddefs

builddefs.ivys.checkOutDir()

import $file.playground_modules

// Shared modules (myrocketchip, emitrtl, ...) live in playground_modules.sc so consumer repos reuse them
object root extends RootModule with playground_modules.PlaygroundModules {
  override def playgroundDir = os.pwd

  object shells extends builddefs.CommonModule with SbtModule {
    override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-fpga-shells"
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
  }

  object playground extends builddefs.CommonModule {
    override def moduleDeps = super.moduleDeps ++ Seq(mycde, mydiplomacy, myrocketchip, inclusivecache, blocks, shells)

    // add some scala ivy module you like here.
    override def ivyDeps = Agg(
      builddefs.ivys.oslib,
      builddefs.ivys.pprint,
      builddefs.ivys.mainargs
    )

    def lazymodule: String = "freechips.rocketchip.system.ExampleRocketSystem"

    def configs: String = "playground.PlaygroundConfig"

    def elaborate = T {
      mill.util.Jvm.runSubprocess(
        finalMainClass(),
        runClasspath().map(_.path),
        forkArgs(),
        forkEnv(),
        Seq(
          "--dir",
          T.dest.toString,
          "--lm",
          lazymodule,
          "--configs",
          configs
        ),
        workingDir = os.pwd
      )
      println(s" Elaborate done: ${T.dest}")
      PathRef(T.dest)
    }

    def chiselAnno = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get
    }

    def verilog = T {
      os.proc(
        "firtool",
        elaborate().path / s"${lazymodule.split('.').last}.fir",
       // s"--annotation-file=${chiselAnno().path}",
        "--disable-annotation-unknown",
        "-O=debug",
        "--split-verilog",
        "--preserve-values=named",
        "--output-annotation-file=mfc.anno.json",
        s"-o=${T.dest}"
      ).call(T.dest)
      PathRef(T.dest)
    }
  }
}
