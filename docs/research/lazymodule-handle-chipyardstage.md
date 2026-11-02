# Retaining the LazyModule handle on the ChipyardStage path

Research for issue #4 (child of #3). Question: how can emitrtl keep a reference to the Top's `LazyModule` when it elaborates through `ChipyardStage`, so that graphML (and any other artefact that needs the handle) can be produced, without patching vendored chipyard or rocket-chip?

## Answer (TL;DR)

**Recommended: read the handle back from `ChipyardStage.execute`'s return value.** There is no need to replace or work around `PreElaboration`.

- `PreElaboration` drops the `LazyModule`, but it returns `LazyModule(a).module`. That is a `LazyModuleImpLike`, and it carries a public back-reference `val wrapper: LazyModule`.
- chisel's `Elaborate` phase emits `DesignAnnotation(dut)` for the elaborated top.
- That annotation survives every later phase on both chisel 6.7.0 and 7.13.0, and `Stage.execute` returns it.

So emitrtl can do this:

```scala
import chisel3.stage.DesignAnnotation
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyModuleImpLike}

val annos = (new ChipyardStage).execute(annoArgs, Seq.empty)
val lmHandle: Option[LazyModule] = annos.collectFirst {
  // type pattern, not extractor: DesignAnnotation has 1 field on chisel6, 2 on chisel7
  case d: DesignAnnotation[_] => d.design
}.collect { case imp: LazyModuleImpLike => imp.wrapper }
lmHandle.foreach(lm => os.write.over(os.Path(out_dir) / s"$outFileName.graphml", lm.graphML))
```

This is about 10 lines, lives entirely in `dependencies/emitrtl/src/main/scala/Top.scala` (`SynthToplevel.chipyardAnno`), and needs no change to any phase ordering. graphML is read after `execute` returns, so it is read after elaboration by construction.

This was not compiled or run in this research. The worktree had no submodules checked out. Every claim below comes from reading source.

## Evidence

Paths are in the vendored trees as checked out in the main playground working copy. `tools/stage` and `tools/stage-chisel7` differ only in the SFC/legacy-FIRRTL2 option, and in `MaybeAspectPhase`/`MaybeInjectingPhase` being absent from chisel7's `ChipyardChiselStage` (`diff -r`). `PreElaboration`, `GenerateFirrtlAnnos`, `GenerateArtefacts` and `Checks` are byte-identical.

1. **PreElaboration returns the Imp, not the LazyModule.** `dependencies/chipyard/tools/stage{,-chisel7}/src/main/scala/phases/PreElaboration.scala:32-40` builds `gen = () => topMod.getConstructor(classOf[Parameters]).newInstance(config) match { case a: RawModule => a; case a: LazyModule => LazyModule(a).module }` and prepends `ChiselGeneratorAnnotation(gen)`.
2. **The Imp points back to its LazyModule.** `dependencies/diplomacy/diplomacy/src/diplomacy/lazymodule/LazyModuleImp.scala:15-18`: `sealed trait LazyModuleImpLike extends RawModule { val wrapper: LazyModule ... }`. The concrete classes are `LazyModuleImp(val wrapper: LazyModule)` (:136) and `LazyRawModuleImp(val wrapper: LazyModule)` (:147).
3. **Elaborate emits DesignAnnotation.**
   - chisel 6.7.0 `chisel3/stage/phases/Elaborate.scala:52-54`: `Builder.build(Module(gen()), context)` then `Seq(ChiselCircuitAnnotation(elaboratedCircuit), DesignAnnotation(dut))`.
   - chisel 7.13.0 (`src/main/scala/chisel3/stage/phases/Elaborate.scala` @ tag `v7.13.0`): `DesignAnnotation(dut, layers = ...)`. Its definition is `case class DesignAnnotation[DUT <: RawModule](design: DUT, layers: Seq[chisel3.layer.Layer] = Seq.empty)` (`ChiselAnnotations.scala:457`).
   - The field name `design` is stable across both versions. The extractor arity is not, so use a type pattern.
4. **Nothing downstream removes it.**
   - `Convert` passes through non-circuit annotations (`case a => Some(a)`).
   - `circt.stage.phases.CIRCT` returns `annotations` unchanged for `CIRCTTarget.CHIRRTL`, which `ChipyardChiselStage.run` always appends (chisel6 `CIRCT.scala:131-133`, chisel7 `:140-142`).
   - `firrtl.options.phases.WriteOutputAnnotations.transform` filters `Unserializable` only from what it *writes*, and returns the full `annotations` (chisel6 and chisel7 `WriteOutputAnnotations.scala`, last line of `transform`).
   - `GenerateFirrtlAnnos` and `GenerateArtefacts` both `return annotations`.
   - `firrtl.options.Stage.execute` is `transform(shell.parse(args, annotations))`, and it returns the phase output.
5. **graphML is a lazy val that walks to the root.** `LazyModule.scala:157`: `lazy val graphML: String = parent.map(_.graphML).getOrElse { ... }`. It is safe to read after elaboration from any node.
6. **Today emitrtl discards the result.** `dependencies/emitrtl/src/main/scala/Top.scala:165-168` `chipyardAnno()` calls `(new ChipyardStage).execute(annoArgs, Seq.empty)` and ignores the return value. `LazyToplevel.genDiplomacyGraph` (:239-249) uses `lazyTop`, a *different* instance that the chipyard path never elaborates. That is the root cause behind #4.

## Downstream contract (must be preserved by any route)

The macro-compiler/firtool flow in `SynthToplevel` reads two files:

- `${out_dir}/${outFileName}.fir`, written by the nested `ChipyardChiselStage`. It depends on the `ChiselOutputFileAnnotation(longName)` that `TransformAnnotations` adds.
- `${out_dir}/${outFileName}.anno.json`, written by `GenerateFirrtlAnnos`. It serializes every annotation that is not `Unserializable`, `TargetDirAnnotation` or `CustomFileEmission` (`GenerateFirrtlAnnos.scala:20-29`).

`GenerateFirrtlAnnos` and `GenerateArtefacts` need only these:

- `StageOptions.targetDir` (`--target-dir`)
- `ChipyardOptions.longName`, from `--name`/`OutputBaseNameAnnotation`, or else from the top package plus the config class names
- `ElaborationArtefacts.files`, for `GenerateArtefacts`

Every `ChipyardOption` annotation is `Unserializable`, so none of them reach `anno.json`. `chipyardAnno` then merges `anno.json` with a `ModuleHierarchyAnnotation` (`addHierarchy2AnnoFile`), which `firtool --repl-seq-mem` consumes in `synthFirrtl2synthSV`. The recommended route touches none of this.

## Routes evaluated

| Route | chisel6 | chisel7 | Cost | Verdict |
|---|---|---|---|---|
| **(d) Harvest `DesignAnnotation` from `execute`'s return** | yes | yes | ~10 lines in `chipyardAnno` | **Recommended** |
| (d') ChipyardStage subclass plus a capture phase before `GenerateArtefacts` | yes | yes | ~40 lines, copies chipyard's target list | Use only if graphml must be written by `GenerateArtefacts` |
| (a) emitrtl replacement for `PreElaboration` | yes | yes | ~60 lines, copies the PM target list and PreElaboration body | Works, but redundant given (d) |
| (b) Inject a pre-built `ChiselGeneratorAnnotation` | **fails** | **fails** | n/a | Not viable without (a) |
| (c) Plain `ChiselStage` plus manual `GenerateFirrtlAnnos`/`GenerateArtefacts` | yes | yes | ~40 lines, re-creates `TransformAnnotations` and the CHIRRTL target | Works, but forks the pipeline |

### (d) Harvest from `execute` (recommended)

- **How it works:** see the TL;DR.
- **Phase ordering:** none. It runs after `execute`, so elaboration is complete.
- **Limits:**
  - The handle exists only when `--top-module` names a `LazyModule` subclass. If the top is a `RawModule` (for example `top.TestHarness` wrapping a LazyModule), `DesignAnnotation.design` is not a `LazyModuleImpLike`, so the result is `None`. Routes (a), (b) and (c) have the same limit unless they build the generator themselves.
  - The file is written by emitrtl, not by `GenerateArtefacts`. Choose the name explicitly. `GenerateArtefacts` would use `${longName}.graphml`, and the current `genDiplomacyGraph` uses `${lazyTop.className}.graphml`.
  - Tops that mix in rocket-chip `HasDTSImp` already register `"graphml"` in `ElaborationArtefacts` (`rocket-chip/src/main/scala/subsystem/BaseSubsystem.scala:40`), so `GenerateArtefacts` already writes `${longName}.graphml` for them. Writing to the same name only rewrites identical content, because `graphML` resolves to the root.
- **Generality:** the same handle serves any other handle-dependent artefact, for example `getNodes`/`getChildren` for `showModuleComposition`.

### (d') Subclass `ChipyardStage`, add a capture phase

- **How it works:**
  - `ChipyardStage` is a non-final class with an overridable `run`. Only `invalidates` is `final` (`ChipyardStage.scala:37-66`).
  - An emitrtl subclass can copy the `PhaseManager` target list and add a phase that is `prerequisites = Seq(Dependency[ChipyardChiselStage])` and `optionalPrerequisiteOf = Seq(Dependency[GenerateArtefacts])`.
  - That phase reads `DesignAnnotation` and calls `ElaborationArtefacts.add("graphml", lm.graphML)`.
- **Phase ordering:** the capture phase must come after `ChipyardChiselStage`, because `Elaborate` runs inside it, and before `GenerateArtefacts`. `ElaborationArtefacts.add` takes `contents: => String` (`rocket-chip/src/main/scala/util/GeneratorUtils.scala:8`), so evaluation is deferred to `GenerateArtefacts` either way.
- **Cost:** the copied target list drifts whenever chipyard changes it, and the two stage variants already differ. Only worth it if graphml must come out of `GenerateArtefacts` with `${longName}` naming.

### (a) emitrtl-owned replacement for `PreElaboration`

- **How it works:**
  - The `PreElaboration` target is hard-coded in `ChipyardStage.run`, so replacing it needs the (d') subclass anyway.
  - The replacement phase reuses `HasChipyardStageUtils.getConfig` and `chipyard.stage.phases.TargetDirKey`, both public.
  - Its generator does `val lm = LazyModule(a); ElaborationArtefacts.add("graphml", lm.graphML); lm.module`, or stashes `lm` in a holder.
- **Phase ordering:**
  - The replacement must run after `Checks`, and before `ChipyardChiselStage` because `Elaborate` is nested inside it.
  - Upstream relies on `optionalPrerequisiteOf = Elaborate`. That edge is meaningless in the outer PM, because `Elaborate` is not a node there. Ordering currently falls out of target-list order.
  - A replacement should declare `optionalPrerequisiteOf = Seq(Dependency[ChipyardChiselStage])` explicitly.
- **Cost:** it duplicates the PreElaboration body and the PM target list. It also gives no capability beyond (d), because the handle that (d) recovers is the same object.

### (b) Supply a pre-built `ChiselGeneratorAnnotation`

- **Why it fails:**
  - `chipyard.stage.phases.Checks` requires exactly one `TopModuleAnnotation` and one `ConfigsAnnotation` (`Checks.scala:38-40`).
  - `PreElaboration` is an unconditional target, so it always prepends its own generator.
  - `Elaborate` `flatMap`s over *every* `ChiselGeneratorAnnotation` (chisel6 `Elaborate.scala:36-37`, chisel7 :39-40). The result is two elaborations and two circuits.
  - `WriteOutputAnnotations` then throws `"Multiple circuit annotations found--only 1 is supported"`.
- **Viability:** none without removing `PreElaboration` from the PM, which turns it into (a).
- **Caveat on the generator idea:** a self-built generator such as `() => lazyTop.module` would bypass chipyard's config assembly (`getConfig(configs).alterPartial { case TargetDirKey => ... }`). `lazyTop` would then elaborate with emitrtl's own `Parameters`, not the `--legacy-configs` ones.

### (c) Plain `ChiselStage`, then run only `GenerateFirrtlAnnos` and `GenerateArtefacts`

- **How it works:**
  - The emitrtl code builds `ChiselGeneratorAnnotation` itself, keeping the handle.
  - It adds `ChiselOutputFileAnnotation(longName)`, `TargetDirAnnotation`, `OutputBaseNameAnnotation` and `CIRCTTargetAnnotation(CHIRRTL)`.
  - It runs `new chipyard.stage.ChipyardChiselStage` (public, `final class`) or `circt.stage.ChiselStage`.
  - It then calls `.transform` on `GenerateFirrtlAnnos` and `GenerateArtefacts` directly. Their `prerequisites` are only enforced by a `PhaseManager`, not by a direct `transform` call.
- **Phase ordering:** `GenerateFirrtlAnnos` must see the post-elaboration annotations. Graph capture has to happen before `GenerateArtefacts` if it goes through `ElaborationArtefacts`.
- **Cost:**
  - It re-creates `TransformAnnotations`, and `Checks` if wanted, plus config assembly.
  - It quietly diverges from chipyard's pipeline. For example, `MaybeAspectPhase`/`MaybeInjectingPhase` are present only in the chisel6 `ChipyardChiselStage`, and `circt.stage.ChiselStage` has a different target list.
  - It has the same config caveat as (b).

## Open points for the implementer

- Choose the graphml filename. `${outFileName}.graphml` matches the other chipyard artefacts.
- Decide whether `genDiplomacyGraph`/`showModuleComposition` should take the recovered handle instead of the separately constructed `lazyTop` on the synth path.
- Run a smoke test on both `USE_CHISEL7` unset and set. Expect a non-empty graphml and an unchanged `.fir`/`.anno.json`.
