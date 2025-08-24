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
import $file.dependencies.cde.build
import $file.dependencies.diplomacy.common
import $file.dependencies.`rocket-chip`.common

import $file.builddefs

// Global Scala Version
object macros extends dependencies.`rocket-chip`.common.MacrosModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip" / "macros"
  def scalaVersion: T[String] = T(ivys.sv)
  def scalaReflectIvy = ivys.scalaReflect
}


object mycde extends dependencies.cde.build.CDE with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
  def scalaVersion: T[String] = T(ivys.sv)
}

object mydiplomacy extends dependencies.diplomacy.common.DiplomacyModule with CommonModule {
  override def millSourcePath = os.pwd / "dependencies" / "diplomacy" / "diplomacy"
  override def scalaVersion = ivys.sv
  def chiselModule = None
  def chiselPluginJar = None
  def chiselIvy = Some(ivys.chiselCrossVersions(ivys.cv)._1)
  def chiselPluginIvy = Some(ivys.chiselCrossVersions(ivys.cv)._2)
  def sourcecodeIvy = ivys.sourcecode
  def cdeModule = mycde
}

object myrocketchip extends dependencies.`rocket-chip`.common.RocketChipModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip"

  override def scalaVersion = ivys.sv

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(ivys.chiselCrossVersions(ivys.cv)._1)

  def chiselPluginIvy = Some(ivys.chiselCrossVersions(ivys.cv)._2)

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)
  override def scalacPluginIvyDeps = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy)

  def macrosModule = macros

  def hardfloatModule: ScalaModule = myhardfloat

  def diplomacyModule: ScalaModule = mydiplomacy
 
  def cdeModule: ScalaModule = mycde

  def mainargsIvy = ivys.mainargs

  def json4sJacksonIvy = ivys.json4sJackson

}

object inclusivecache extends CommonModule {
  override def millSourcePath =
    os.pwd / "dependencies" / "rocket-chip-inclusive-cache" / "design" / "craft" / "inclusivecache"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object blocks extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-blocks"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object shells extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-fpga-shells"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}

// UCB
object myhardfloat extends ScalaModule with SbtModule with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "berkeley-hardfloat"
  def scalaVersion = ivys.sv

  def chiselIvy = Some(ivys.chiselCrossVersions(ivys.cv)._1)

  def chiselPluginIvy = Some(ivys.chiselCrossVersions(ivys.cv)._2)

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

object testchipip extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "testchipip"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}

//object chipyardAnnotations extends CommonModule with SbtModule {
//  override def millSourcePath = os.pwd / "dependencies" / "chipyard" / "tools" / "stage"
//  override def moduleDeps     = super.moduleDeps ++ Seq(myrocketchip)
//}

//object chipyardTapeout extends CommonModule with SbtModule {
//  override def millSourcePath = os.pwd / "dependencies" / "chipyard" / "tools" / "tapeout"
//  //override def scalaVersion = ivys.sv1 // stuck on chisel3 2.13.10
//  //override def chiselIvy = Some(ivys.chiselCrossVersions(ivys.cv1)._1) // stuck on chisel3 and SFC
//  //override def chiselPluginIvy = Some(ivys.chiselCrossVersions(ivys.cv1)._1)
//  def playjsonIvy = ivys.playjson
//  override def moduleDeps = super.moduleDeps
//}

//object chipyardTapeout extends SbtModule {
//  override def millSourcePath = os.pwd / "dependencies" / "chipyard" / "tools" / "tapeout"
//  override def scalaVersion = ivys.sv1 // stuck on chisel3 2.13.10
//  def chiselIvy = Some(ivys.chiselCrossVersions(ivys.cv1)._1) // stuck on chisel3 and SFC
//  def chiselPluginIvy = Some(ivys.chiselCrossVersions(ivys.cv1)._1)
//  def playjsonIvy = ivys.playjson
//  override def moduleDeps = super.moduleDeps
//}

//object chipyardTapeout extends CommonModule with SbtModule {
//  override def millSourcePath = os.pwd / "dependencies" / "chipyard" / "tools"
//  override def moduleDeps     = super.moduleDeps ++ Seq("com.typesafe.play" %% "play-json" % "2.9.2")
//}


// Dummy

object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(mycde, mydiplomacy, myrocketchip, inclusivecache, blocks, shells)

  // add some scala ivy module you like here.
  override def ivyDeps = Agg(
    ivys.oslib,
    ivys.pprint,
    ivys.mainargs
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
