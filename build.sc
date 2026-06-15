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

import $file.dependencies.cde.build
import $file.dependencies.diplomacy.common
import $file.dependencies.`rocket-chip`.common
import $file.dependencies.`berkeley-hardfloat`.common


// Global Scala Version
object macros extends dependencies.`rocket-chip`.common.MacrosModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip" / "macros"
  def scalaVersion: T[String] = T(builddefs.ivys.sv)
  def scalaReflectIvy = builddefs.ivys.scalaReflect
}


object mycde extends dependencies.cde.build.CDE with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
  def scalaVersion: T[String] = T(builddefs.ivys.sv)
}

object mydiplomacy extends dependencies.diplomacy.common.DiplomacyModule with builddefs.CommonModule {
  override def millSourcePath = os.pwd / "dependencies" / "diplomacy" / "diplomacy"
  override def scalaVersion = builddefs.ivys.sv
  def chiselModule = None
  def chiselPluginJar = None
  def chiselIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._1)
  def chiselPluginIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._2)
  def sourcecodeIvy = builddefs.ivys.sourcecode
  def cdeModule = mycde
}

object myrocketchip extends dependencies.`rocket-chip`.common.RocketChipModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip"

  override def scalaVersion = builddefs.ivys.sv

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._1)

  def chiselPluginIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._2)

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)
  override def scalacPluginIvyDeps = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy)

  def macrosModule = macros

  def hardfloatModule: ScalaModule = myhardfloat

  def diplomacyModule: ScalaModule = mydiplomacy
 
  def cdeModule: ScalaModule = mycde

  def mainargsIvy = builddefs.ivys.mainargs

  def json4sJacksonIvy = builddefs.ivys.json4sJackson

}

object inclusivecache extends builddefs.CommonModule {
  override def millSourcePath =
    os.pwd / "dependencies" / "rocket-chip-inclusive-cache" / "design" / "craft" / "inclusivecache"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object blocks extends builddefs.CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-blocks"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object shells extends builddefs.CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-fpga-shells"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}

// UCB
object myhardfloat extends dependencies.`berkeley-hardfloat`.common.HardfloatModule with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "berkeley-hardfloat" / "hardfloat"
  def scalaVersion = builddefs.ivys.sv

  def chiselModule = None

  def chiselPluginJar = None
  
  def chiselIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._1)

  def chiselPluginIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._2)

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)
  override def scalacPluginIvyDeps = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy)
  // remove test dep
  override def allSourceFiles = T(
    super.allSourceFiles().filterNot(_.path.last.contains("Tester")).filterNot(_.path.segments.contains("test"))
  )

  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "edu.berkeley.cs",
    url = "http://chisel.eecs.berkeley.edu",
    licenses = Seq(License.`BSD-3-Clause`),
    versionControl = VersionControl.github("ucb-bar", "berkeley-hardfloat"),
    developers = Seq(
      Developer("jhauser-ucberkeley", "John Hauser", "https://www.colorado.edu/faculty/hauser/about/"),
      Developer("aswaterman", "Andrew Waterman", "https://aspire.eecs.berkeley.edu/author/waterman/"),
      Developer("yunsup", "Yunsup Lee", "https://aspire.eecs.berkeley.edu/author/yunsup/")
    )
  )
}

object testchipip extends builddefs.CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "testchipip"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}


object chipyardAnnotations extends builddefs.CommonModule with SbtModule {
  override def millSourcePath = os.pwd / os.up/ "playground" / "dependencies" / "chipyard" / "tools" / "stage"
  override def moduleDeps     = super.moduleDeps ++ Seq(myrocketchip)
}
 
object chipyardTapeout extends builddefs.CommonModule with SbtModule {
  override def millSourcePath = os.pwd / os.up/ "playground" / "dependencies" / "chipyard" / "tools" / "tapeout"
  override def scalaVersion = builddefs.ivys.sv1 // stuck on chisel3 2.13.10
  override def chiselIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv1)._1) // stuck on chisel3 and SFC
  override def chiselPluginIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv1)._1)
  //def playjsonIvy = ivys.playjson
  def playjsonIvy = ivy"com.typesafe.play::play-json:2.9.2"
  override def ivyDeps = T(super.ivyDeps() ++ Some(playjsonIvy))
  override def moduleDeps = super.moduleDeps
}

object emitrtl extends builddefs.CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "emitrtl"
  override def ivyDeps = T(super.ivyDeps() ++ Agg(builddefs.ivys.scalatest))
  override def moduleDeps = super.moduleDeps ++ Seq(mycde, mydiplomacy, myrocketchip, chipyardAnnotations, chipyardTapeout)
}

// Dummy

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
