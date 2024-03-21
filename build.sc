// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import scalalib._
// Hack
import publish._
// support BSP
import mill.bsp._
// input build.sc from each repositories.
import $file.dependencies.cde.build
import $file.dependencies.`rocket-chip`.common

// Global Scala Version
object ivys {
  val sv = "2.13.12"
  val cv = "6.0.0"
  // the first version in this Map is the mainly supported version which will be used to run tests
  val chiselCrossVersions = Map(
    "5.0.0" -> (ivy"org.chipsalliance::chisel:5.0.0", ivy"org.chipsalliance:::chisel-plugin:5.0.0"),
    "6.0.0" -> (ivy"org.chipsalliance::chisel:6.0.0", ivy"org.chipsalliance:::chisel-plugin:6.0.0")
  )

  val chiseltestCrossVersions = Map(
    "5.0.0" -> ivy"edu.berkeley.cs::chiseltest:5.0.0",
    "6.0.0" -> ivy"edu.berkeley.cs::chiseltest:6.0.0"
  )

  val upickle = ivy"com.lihaoyi::upickle:1.3.15"
  val oslib = ivy"com.lihaoyi::os-lib:0.7.8"
  val pprint = ivy"com.lihaoyi::pprint:0.6.6"
  val utest = ivy"com.lihaoyi::utest:0.7.10"
  val jline = ivy"org.scala-lang.modules:scala-jline:2.12.1"
  val scalatest = ivy"org.scalatest::scalatest:3.2.15"
  val scalatestplus = ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1"
  val scalacheck = ivy"org.scalacheck::scalacheck:1.14.3"
  val scopt = ivy"com.github.scopt::scopt:3.7.1"
  val playjson = ivy"com.typesafe.play::play-json:2.9.4"
  val breeze = ivy"org.scalanlp::breeze:1.1"
  val parallel = ivy"org.scala-lang.modules:scala-parallel-collections_3:1.0.4"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val json4sJackson = ivy"org.json4s::json4s-jackson:4.0.5"
  val scalaReflect = ivy"org.scala-lang:scala-reflect:${sv}"
}

// For modules not support mill yet, need to have a ScalaModule depend on our own repositories.
trait CommonModule extends ScalaModule {
  override def scalaVersion = ivys.sv

  def chiselIvy = Some(ivys.chiselCrossVersions(ivys.cv)._1)

  def chiselPluginIvy = Some(ivys.chiselCrossVersions(ivys.cv)._2)

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)
  override def scalacPluginIvyDeps = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy)

}

object macros extends dependencies.`rocket-chip`.common.MacrosModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip" / "macros"
  def scalaVersion: T[String] = T(ivys.sv)
  def scalaReflectIvy = ivys.scalaReflect
}

object mycde extends dependencies.cde.build.CDE with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
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


// Dummy

object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, inclusivecache, blocks, shells)

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
    PathRef(T.dest)
  }

  def verilog = T {
    os.proc(
      "firtool",
      elaborate().path / s"${lazymodule.split('.').last}.fir",
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
