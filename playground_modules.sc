// Shared module definitions for playground and every repo that builds against it.
//
// Usage from a consumer repo's build.sc:
//   import $file.^.playground.{builddefs => playground_build}
//   import $file.^.playground.playground_modules
//   playground_build.ivys.checkOutDir()
//   object root extends RootModule with playground_modules.PlaygroundModules {
//     object mymodule extends playground_build.CommonModule { ... moduleDeps ++ Seq(myrocketchip) }
//   }
//
// RootModule keeps the module names top-level (`mill myrocketchip.compile`, not
// `mill root.myrocketchip.compile`) and out/ paths unchanged.
import mill._
import mill.scalalib._
import mill.scalalib.publish._

import $file.builddefs
import $file.dependencies.cde.{build => cde_build}
import $file.dependencies.diplomacy.{common => diplomacy_common}
import $file.dependencies.`rocket-chip`.{common => rocketchip_common}
import $file.dependencies.`berkeley-hardfloat`.{common => hardfloat_common}

trait PlaygroundModules extends Module {

  // Every consumer repo is checked out next to playground
  def playgroundDir: os.Path = os.pwd / os.up / "playground"
  def playgroundDeps: os.Path = playgroundDir / "dependencies"

  object macros extends rocketchip_common.MacrosModule with SbtModule {
    override def millSourcePath = playgroundDeps / "rocket-chip" / "macros"
    def scalaVersion: T[String] = T(builddefs.ivys.sv)
    def scalaReflectIvy = builddefs.ivys.scalaReflect
  }

  object mycde extends cde_build.CDE with PublishModule {
    override def millSourcePath = playgroundDeps / "cde" / "cde"
    def scalaVersion: T[String] = T(builddefs.ivys.sv)
  }

  object mydiplomacy extends diplomacy_common.DiplomacyModule with builddefs.CommonModule {
    override def millSourcePath = playgroundDeps / "diplomacy" / "diplomacy"
    override def scalaVersion = builddefs.ivys.sv
    def chiselModule = None
    def chiselPluginJar = None
    def chiselIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._1)
    def chiselPluginIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv)._2)
    def sourcecodeIvy = builddefs.ivys.sourcecode
    def cdeModule = mycde
  }

  object myrocketchip extends rocketchip_common.RocketChipModule with SbtModule {

    override def millSourcePath = playgroundDeps / "rocket-chip"

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

  // UCB
  object myhardfloat extends hardfloat_common.HardfloatModule with PublishModule {
    override def millSourcePath = playgroundDeps / "berkeley-hardfloat" / "hardfloat"
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

  object inclusivecache extends builddefs.CommonModule {
    override def millSourcePath =
      playgroundDeps / "rocket-chip-inclusive-cache" / "design" / "craft" / "inclusivecache"
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
  }

  object blocks extends builddefs.CommonModule with SbtModule {
    override def millSourcePath = playgroundDeps / "rocket-chip-blocks"
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
  }

  object testchipip extends builddefs.CommonModule with SbtModule {
    override def millSourcePath = playgroundDeps / "testchipip"
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
  }

  object chipyardAnnotations extends builddefs.CommonModule with SbtModule {
    override def millSourcePath =
      playgroundDeps / "chipyard" / "tools" / (if (builddefs.ivys.useChisel7) "stage-chisel7" else "stage")
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
  }

  object chipyardTapeout extends builddefs.CommonModule with SbtModule {
    override def millSourcePath = playgroundDeps / "chipyard" / "tools" / "tapeout"
    override def scalaVersion = builddefs.ivys.sv1 // stuck on chisel3 2.13.10
    override def chiselIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv1)._1) // stuck on chisel3 and SFC
    override def chiselPluginIvy = Some(builddefs.ivys.chiselCrossVersions(builddefs.ivys.cv1)._1)
    def playjsonIvy = ivy"com.typesafe.play::play-json:2.9.2"
    override def ivyDeps = T(super.ivyDeps() ++ Some(playjsonIvy))
  }

  object emitrtl extends builddefs.CommonModule with SbtModule {
    override def millSourcePath = playgroundDeps / "emitrtl"
    // Chisel-version-specific sources (e.g. VerilatorSimulator), selected by USE_CHISEL7
    override def sources = T.sources {
      super.sources() :+ PathRef(
        millSourcePath / "src" / "main" / (if (builddefs.ivys.useChisel7) "scala-chisel7" else "scala-chisel6")
      )
    }
    override def ivyDeps = T(super.ivyDeps() ++ Agg(builddefs.ivys.scalatest))
    override def moduleDeps =
      super.moduleDeps ++ Seq(mycde, mydiplomacy, myrocketchip, chipyardAnnotations)

    object test extends SbtModuleTests with TestModule.ScalaTest
  }
}
