---
status: proposed
---

# Artefact emission is independent of the elaboration path

A Top can be elaborated through the plain `ChiselStage` path (`LazyToplevel`) or the `ChipyardStage` path (`SynthToplevel`), and today the two emit different artefacts. Both fill rocket-chip's global `ElaborationArtefacts` registry. `ChipyardStage`'s `GenerateArtefacts` writes every key but has no graphML, because `PreElaboration` keeps only `LazyModule(a).module`. `LazyToplevel.genDiplomacyGraph()` writes only graphML and drops the rest, such as regmaps. We decided that artefact emission is a capability of emitrtl's `Toplevel`, separate from the elaboration path. Every elaboration writes exactly the **artefact selection** for its Top to one shared layout, and only emitrtl changes. Vendored chipyard and rocket-chip get no patches.

Map and decision detail: [Wayfinder: flexible artefact emission for emitrtl Tops](https://github.com/morphingmachines/playground/issues/3).

## Decisions

### Selection

- An artefact selection is a list of **artefact kinds**. The kinds are a closed set: `Regmap`, `GraphML`, `Dts`, `DtsJson`, `MemMap`, `PlusArgs`, `Axi4IdMap`, `L2`, `FpgaShell` and `FpgaIp`. Each kind is defined by the registry-key suffixes it matches. `ByPattern(glob)` covers producers outside the set, such as consumer-repo IP.
- An empty list selects everything: all registry entries plus whole-Top graphML. The list is additive only, so there is no "all except X".
- A Top sets its selection with `override def artefactKinds = Seq(...)`. No trait sugar (`WithGraphML` and similar) is provided.
- A kind that is requested but not produced only logs a warning. Examples are `Dts` on a Top without a `BaseSubsystem`, and `Regmap` with no TileLink register router.
- emitrtl selects only. It does not synthesise artefacts that are missing from the registry.
- graphML covers the whole Top only. `LazyModule.graphML` always renders the root, and its renderers are private.

### Run-time override

- The environment variable `EMITRTL_ARTEFACTS` overrides the selection, for example `EMITRTL_ARTEFACTS=regmap make rtl TARGET=CE`.
  - A non-empty value replaces the Top's `artefactKinds`.
  - An unset or empty value falls back to `artefactKinds`, because a Makefile `?=` exports an empty value.
- The syntax is a comma-separated list of kebab-case kind names, matched case-insensitively: `regmap`, `graphml`, `dts`, `dts-json`, `mem-map`, `plus-args`, `axi4-id-map`, `l2`, `fpga-shell`, `fpga-ip`. Patterns need an explicit `glob:` prefix, as in `glob:*.l2.json`. emitrtl never infers a pattern from glob characters.
- An unknown name fails the run before elaboration, and the error lists the valid names.
- The variable applies to every Top elaborated in the JVM. The legacy shims ignore it.

### Output layout

- Artefacts are written to `<out_dir>/artefacts/<outFileName>.<registry key>`, flat, on both paths, for example `CeTop.graphml` and `CeTop.0x2000000.0.regmap.json`. `out_dir` is unchanged.
- Regmap keys are used verbatim. Rocket-chip truncates bases at or above 4 GiB (`base.toInt`). This is a known limit and is not fixed here.
- Duplicate keys within one elaboration are handled as follows:
  - emitrtl's root graphML replaces any registry `graphml` entries.
  - Duplicates with identical content are written once.
  - Duplicates with different content are all written, with a warning. A counter goes before the last extension: `CeTop.mem.axi4.json`, `CeTop.mem.axi4.1.json`, and `CeTop.dts`, `CeTop.1.dts`.

### Emission and isolation

- Emission is automatic. `chisel2firrtl()` and `chipyardAnno()` write the selection after elaboration, so consumer `Top.scala` files need no change.
- The graphML handle is `def rootLazyModule: Option[LazyModule]`:
  - `Toplevel` returns `None`, so a graphML request only warns.
  - `LazyToplevel` returns `Some(lazyTop)`. This also covers `WithLazyModuleDUT`.
  - On the `ChipyardStage` path, the handle is the stage's own instance. emitrtl matches `DesignAnnotation` by type in `ChipyardStage.execute`'s result and follows its design's `.wrapper`. The annotation has 1 field on chisel6 and 2 on chisel7, which is why it is matched by type. This works only when `--top-module` is a `LazyModule` class. The reflection build stays, so the Top is built twice. That cost is documented.
- The registry is drained (`ElaborationArtefacts.files = Nil`) after writing, not before. Draining before would drop entries added while the Top is built, such as `l2.json`. Draining after also stops one Top's artefacts leaking into the next and resets regmap counter drift.
- A pure `Artefacts` object does the matching, duplicate removal, naming, writing and draining. It can be unit-tested without elaborating a Top. The traits only gather inputs and call it.
- On the `ChipyardStage` path, the order is: the stage runs and writes today's `<out_dir>/<outFileName>.<key>` files, then emitrtl writes `artefacts/`, then emitrtl drains the registry.

### Legacy names

`LazyToplevel`, `SynthToplevel`, `genDiplomacyGraph()` and `chipyardAnno()` keep their names and keep writing today's paths. `genDiplomacyGraph()` writes `lazyTop.graphML` straight to `<out_dir>/<className>.graphml` and does not touch the registry. Deprecation comes later. Nothing in ceTile or in playground's build reads artefact files today, so the new layout breaks no consumer.

## Interface sketch

```scala
// emitrtl/Artefacts.scala: pure, unit-testable without elaboration
sealed trait ArtefactKind
object ArtefactKind {
  case object Regmap extends ArtefactKind; case object GraphML extends ArtefactKind
  /* Dts, DtsJson, MemMap, PlusArgs, Axi4IdMap, L2, FpgaShell, FpgaIp */
  final case class ByPattern(glob: String) extends ArtefactKind
  def parse(s: String): Either[String, Seq[ArtefactKind]] // "regmap,glob:*.l2.json"; Left lists valid names
}
object Artefacts {
  def emit(entries: Seq[(String, () => String)], root: Option[LazyModule],
           kinds: Seq[ArtefactKind], dir: os.Path, prefix: String): Seq[os.Path]
  def drain(): Unit // ElaborationArtefacts.files = Nil
}

trait Toplevel {
  def artefactKinds: Seq[ArtefactKind] = Nil
  final def artefactSelection: Seq[ArtefactKind] =
    sys.env.get("EMITRTL_ARTEFACTS").filter(_.nonEmpty)
      .map(ArtefactKind.parse(_).fold(e => throw new IllegalArgumentException(e), identity))
      .getOrElse(artefactKinds)
  def rootLazyModule: Option[LazyModule] = None
  def chisel2firrtl() = { /* elaborate as today */; emitArtefacts() }
  protected def emitArtefacts(root: Option[LazyModule] = rootLazyModule) = {
    Artefacts.emit(ElaborationArtefacts.files, root, artefactSelection,
                   os.Path(out_dir) / "artefacts", outFileName)
    Artefacts.drain()
  }
}
trait LazyToplevel  { override def rootLazyModule = Some(lazyTop) }
trait SynthToplevel { def chipyardAnno() = { val annos = stage.execute(...); emitArtefacts(wrapperFrom(annos)) } }
```

`artefactSelection` is validated before elaboration starts.

## Considered options

- **Getting the handle on the `ChipyardStage` path.** We rejected injecting a pre-built `ChiselGeneratorAnnotation`, because it trips `Multiple circuit annotations found`. Replacing `PreElaboration` or subclassing the stage would work, but it would copy chipyard's phase pipeline into emitrtl.
- **Run-time channel.** We rejected program args, because `Toplevel` cannot see `App.args` and every consumer `main` would have to change. We rejected a `-D` system property, because it is awkward to pass from a Makefile.
- **Draining before elaboration.** We rejected this, because it loses artefacts that are added while the Top is built.

## Consequences

- Building two `LazyModule`s before elaborating either one is unsupported. The first drain would take the second Top's construction-time entries.
- `PlusArgArtefacts` and `BindingScope.bindingScopes` cannot be cleared, so plusArgs leak across Tops in one JVM.
- It is unconfirmed whether the forked `runMain` JVM under the mill 0.11.4 server sees an `EMITRTL_ARTEFACTS` value that changed after the server started. The acceptance runs must check this. The fallback is `mill -i`.
- The design must hold on both chisel6 (`tools/stage`) and chisel7 (`tools/stage-chisel7`).

## Acceptance

The design is accepted when it holds on the consumer repo [ceTile](https://github.com/morphingmachines/ceTile), branch `chisel7-bump` (baseline `bde4c83`), built with `USE_CHISEL7` set (Chisel 7.13.0, firtool 1.149.0):

1. `make rtl TARGET=CE` (`LazyToplevel` path) and `make synthrtl TARGET=CE` (`ChipyardStage` path) each emit the `CeTop` graphML and every TileLink regmap JSON that `CeTop` produces.
2. For one Top and one selection, both paths produce the same set of artefact files, by name and content, under `artefacts/`.
3. Requesting a subset of kinds at run time, for example `EMITRTL_ARTEFACTS=regmap`, emits exactly that subset.
4. Elaborating `CE` and then `CERoCCIO` in one JVM leaves no artefact from `CE` in the output for `CERoCCIO`.
5. ceTile's `src/main/scala/Top.scala` compiles unchanged. `genDiplomacyGraph()` and `chipyardAnno()` still write today's paths, and `synthFirrtl2synthSV()` and `chipyardMacroCompiler(...)` still succeed.
6. Steps 1 to 5 are repeated for `TARGET=CERoCCIO` on both paths.
