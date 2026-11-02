# Inventory of elaboration artefacts and when they are produced

Research for issue #5 (child of map issue #3, "flexible artefact emission for emitrtl Tops").

**Question.** Which **artefacts** (see `GLOSSARY.md`) can a Top built on playground's shared dependencies emit? For each producer, what is the key and file name, which class produces it, when it is added, what it needs, and can it collide across instances?

## Sources and scope

- Every claim below cites source code in the vendored shared dependencies (primary source). Paths are relative to `dependencies/`.
- Revisions read: the submodule pins on branch `chisel7-bump`. That branch is the working checkout that carries `GLOSSARY.md`, `docs/` and the chisel7 stage.
  - rocket-chip `c01e4e79`, chipyard `cb754654`, emitrtl `63b34220`, diplomacy `65089b58`.
  - rocket-chip-blocks `fde70c28`, testchipip `4448e715`, rocket-chip-inclusive-cache `85420cf2`, rocket-chip-fpga-shells `3c66e7e4`.
  - The `mm-master` pins are older. For example, chipyard `9c861da1` has no graphml rename in `ChipyardSubsystemModuleImp`. Re-check the line numbers if the pins move.
- Search: `grep -rn 'ElaborationArtefacts\|PlusArgArtefacts\|graphML'` over all `*.scala` under `dependencies/`, excluding `src/test`. Nested submodules under `chipyard/generators/` were searched too. Only `chipyard/generators/{chipyard,constellation,firechip,tracegen}` are populated.
- **What the build compiles** (`playground_modules.sc` and `build.sc` on `chisel7-bump`):
  - Compiled: rocket-chip, diplomacy, cde, rocket-chip-blocks, testchipip, inclusive-cache, rocket-chip-fpga-shells (`shells`), chipyard `tools/stage` (or `tools/stage-chisel7`), chipyard `tools/tapeout`, and emitrtl.
  - Not compiled: **chipyard's `generators/` tree** (the `chipyard` package, constellation, firechip). The chipyard sites below apply only if a consumer repo compiles those sources itself.

## 1. The registry: `ElaborationArtefacts`

`rocket-chip/src/main/scala/util/GeneratorUtils.scala:5-15`:

```scala
object ElaborationArtefacts {
  var files: Seq[(String, () => String)] = Nil
  def add(extension: String, contents: => String): Unit = {
    files = (extension, () => contents) +: files
  }
  def contains(extension: String): Boolean = ...
}
```

- **Global and JVM-wide.** It is a singleton `object`. Every elaboration in the same JVM appends to it.
- **No `clear` method.** `files` is a public `var`, so a caller can still:
  - clear it with `ElaborationArtefacts.files = Nil`;
  - snapshot it by reading `files` before elaboration and diffing afterwards.
  
  chipyard already reassigns `files` (see `Subsystem.scala:126` below). A per-elaboration clear or snapshot fits the "changes land in emitrtl only" decision.
- **Lazy contents.** `add` takes `contents` by name and stores `() => contents`. The string is built when a writer calls `contents()`, not when `add` runs. Several producers depend on this. Their contents read state that exists only after module elaboration, such as `pathName`, DTS binding evaluation, or constraints added later.
- **Duplicates allowed, newest first.** `add` prepends and never replaces. `contains` is the only duplicate check, and it is opt-in. Only the regmap producer uses it.
- **The key is a file-name suffix, not a kind.** It is called `extension`. Each writer builds the file name as `<prefix>.<key>`.

### Writers (consumers of the registry)

| Writer | File:line | File name | Keys written |
|---|---|---|---|
| rocket-chip `diplomacy.Main` | `rocket-chip/src/main/scala/diplomacy/Main.scala:51` | `<config.mkString("_")>.<key>` | all |
| chipyard `GenerateArtefacts` phase (chisel6) | `chipyard/tools/stage/src/main/scala/phases/GenerateArtefacts.scala:19-21` | `<targetDir>/<longName>.<key>` via `StageUtils.writeOutputFile` (`StageUtils.scala:24`, plain `FileWriter`, overwrites) | all |
| chipyard `GenerateArtefacts` phase (chisel7) | `chipyard/tools/stage-chisel7/src/main/scala/phases/GenerateArtefacts.scala:19` | same | all |
| emitrtl `LazyToplevel.genDiplomacyGraph` | `emitrtl/src/main/scala/Top.scala:239-249` | `<out_dir>/<lazyTop.className>.graphml` | only `"graphml"`; it first does `add("graphml", lazyTop.graphML)` itself |

None of the writers clears the registry or removes duplicate keys. When a key appears twice, each entry is written to the same path in turn. `files` is newest-first, so the **oldest** entry is written last and is the one left on disk.

## 2. Producers in code that playground compiles

"When added" uses two categories:

- **Construction** means the `add` call runs while the `LazyModule` graph is built (`LazyModule(new X)`).
- **Module elaboration** means it runs inside a `LazyModuleImp` or `Module` body, that is, while `lazy val module` is forced.

Because contents are lazy, the content is always computed later, at write time.

### 2.1 Register maps: `<baseHex>.<n>.regmap.json`

- **Site.** `rocket-chip/src/main/scala/tilelink/RegisterRouter.scala:115-126`, in `TLRegisterNode.genRegDescsJson`. It is called from `TLRegisterNode.regmap` at `:66-112`.
- **Producers.** Every TileLink register device that calls `regmap(...)`. That includes `TLRegisterRouter` subclasses and `HasTLControlRegMap` (`:151`).
  - rocket-chip: CLINT, PLIC, Debug, BusErrorUnit, BusBlocker, TileResetCtrl, BootAddrReg, TraceEncoderController and others.
  - rocket-chip-blocks: UART, SPI, SPI flash, GPIO, PWM, I2C, MockAON, WDT and others.
  - testchipip: BlockDevice, ChipIdPin, ClockBlocker and others.
  - inclusive-cache: the `Control` unit and `PhysicalFilter`.
- **When added.** Module elaboration. `regmap` runs in the device's `Impl` body.
- **Needs a handle?** No. It comes free from the registry.
- **Content.**
  - `GenRegDescsAnno.serialize` (`util/Annotations.scala:37-54`) is evaluated **eagerly** into a `val json` before `add`.
  - The device name, `<describe.name>.At<baseHex>`, appears only inside the JSON as `displayName`. It is not in the key.
- **Collision.**
  - The key holds only the base address and a counter. The code loops `while (contains(s"$baseHex.$suffix.regmap.json")) suffix += 1` (`:121-124`).
  - Two devices at the same base never overwrite each other. They get `.0` and `.1` in elaboration order.
  - Because the registry is never cleared, a second elaboration in the same JVM shifts every suffix. A device that was `.0` in the first run becomes `.1`.
  - `baseHex` is `base.toInt.toHexString` (`:118`, also used in the JSON at `Annotations.scala:51`). Bases at or above 2^32 are truncated, so `0x1_0000_1000` is named `0x1000`.
  - Only `address.head` is used. A device with several address sets is named by its first set only.
- **Not producers.** AXI4, APB and AHB register routers (`amba/axi4/RegisterRouter.scala:37`, `amba/apb/RegisterRouter.scala`, `amba/ahb/RegisterRouter.scala`) build the regmap but **add no artefact**. A non-TileLink register device emits no regmap JSON.

### 2.2 TL-to-AXI4 ID map: `<adapterName>.axi4.json`

- **Site.** `rocket-chip/src/main/scala/tilelink/ToAXI4.scala:127-130`, inside `TLToAXI4.Impl`. It runs only when `adapterName` is `Some(n)`.
- **When added.** Module elaboration.
- **Needs a handle?** No.
- **Content.** Eager string. The ID mapping is also printed to stdout.
- **Producers in deps.** `adapterName` defaults to `None` (`:97`), and rocket-chip's own ports never set it. Only fpga-shells sets it:
  - `"mem"`: the VC707, Arty100T, NexysVideo, AXKU040, PolarFire DDR3 and DDR4 MIG wrappers, for example `devices/xilinx/xilinxvc707mig/XilinxVC707MIG.scala:154`.
  - `"pcie-slave"`: `devices/xilinx/xdma/XDMA.scala:24` and `xilinxvc707pciex1/XilinxVC707PCIeX1.scala:41`.
- **Collision.** The key is fixed per name. Two MIG channels both produce `mem.axi4.json`, and the first-added entry wins on disk (section 1).

### 2.3 Subsystem artefacts from `HasDTSImp`: `graphml`, `plusArgs`, `dts`, `json`

- **Site.** `rocket-chip/src/main/scala/subsystem/BaseSubsystem.scala:37-46`, trait `HasDTSImp`. It is mixed into `BaseSubsystemModuleImp` (`:146`).
- **When added.** Module elaboration, in the subsystem `Impl` constructor.
- **Needs a handle?** No for the Top: any Top that contains a `BaseSubsystem` gets these from the registry. Internally they come from `dtsLM` (the subsystem `LazyModule`, `HasDTS` with `BindingScope`, `:31-35`).

| Key | Content | Notes |
|---|---|---|
| `graphml` | `dtsLM.graphML` (lazy) | See section 3. It returns the **root's** whole graph, not the subsystem's. |
| `plusArgs` | `PlusArgArtefacts.serialize_cHeader()` (lazy) | A C header of every PlusArg registered so far in the JVM. See section 4. |
| `dts` | `dtsLM.dts`, a `lazy val` equal to `DTS(bindingTree)` | `bindingTree` forces `BindingScope.eval`. That requires no `LazyModule` scope to be open (`resources/Resources.scala:285`). `println(dtsLM.dts)` at `:45` forces it during elaboration anyway. |
| `json` | `dtsLM.json`, equal to `JSON(bindingTree)` (`resources/JSON.scala:8`) | The same device tree as JSON. |

- **Not registered.** `dtb` (`:33`) is never added; it would need `dtc` on the PATH (`DeviceTree.scala:131-142`).
- **Collision.** All four keys are fixed. Two subsystems in one Top, or two elaborations in one JVM, give duplicate keys, and the first-added entry wins on disk.
- **Top without a subsystem.** A Top that is not a `BaseSubsystem` gets **no DTS, JSON, graphml or plusArgs** from this site. An example is emitrtl's `Adder` harness.
  - DTS for such a Top needs a handle to a `LazyModule` that mixes in `BindingScope` (`Resources.scala:273`).
  - `BindingScope.find` takes the **outermost** scope (`:391-394`).
  - `BindingScope.bindingScopes` (`:398-400`) is a global `ArrayBuffer` that is never cleared or read in deps.

### 2.4 Memory map: `memmap.json`

- **Site.** `rocket-chip/src/main/scala/subsystem/BaseSubsystem.scala:155`, in `BaseSubsystemModuleImp`.
- **When added.** Module elaboration. The `mapping` comes from `dtsLM.collectResourceAddresses` (`:148-152`) and is computed eagerly as a private `val`.
- **Needs a handle?** No. It comes free with a subsystem.
- **Collision.** The key is fixed, and the first-added entry wins on disk.

### 2.5 Unit-test plusargs: `plusArgs`

- **Site.** `rocket-chip/src/main/scala/unittest/UnitTest.scala:25`, trait `UnitTestModule` (a `Module`, not a `LazyModule`).
- **When added.** Module elaboration, once per `UnitTestModule` instance.
- **Collision.** The same `plusArgs` key as 2.3. Every instance adds a duplicate, but each closure serialises the same global `PlusArgArtefacts`, so the content is identical.

### 2.6 Inclusive cache description: `l2.json`

- **Site.** `rocket-chip-inclusive-cache/design/craft/inclusivecache/src/Configs.scala:146`, inside the `SubsystemBankedCoherenceKey` `coherenceManager` closure (`:68`).
- **When added.** **Construction.** The closure is called from `CoherenceManagerWrapper`'s constructor (`rocket-chip/src/main/scala/subsystem/BankedCoherenceParams.scala:65`) while the subsystem `LazyModule` is built.
- **Content.** `l2.module.json` is lazy. It is `InclusiveCache.Impl.json` (`InclusiveCache.scala:183`), which reads `pathName` of directory and banked-store memories (`Directory.scala:143`, `BankedStore.scala:192`, `Scheduler.scala:351`). It is valid **only after module elaboration**, and calling it earlier would force `l2.module`.
- **Needs a handle?** No.
- **Collision.** The key is fixed. One L2 per subsystem, so it collides only across subsystems or elaborations.

### 2.7 FPGA shell constraints and IP scripts (rocket-chip-fpga-shells)

Playground compiles `shells`. These producers fire only for Tops built on a fpga-shells `Shell` or using its IP black boxes.

**Shell constraint files**

- **When added.** Construction. The `add` runs in the constructor of a helper object held as a `val` in the `Shell` `LazyModule`.
- **Content.** Lazy. The closure flattens constraints that overlays add later, so it must be evaluated after elaboration.

| Key | Producer, file:line |
|---|---|
| `shell.sdc` | `SDC` class, `shell/IOShell.scala:72-90`; instantiated in `XilinxShell.scala:55` and `PolarFireShell.scala:30` |
| `shell.xdc` | `XDC` class, `shell/xilinx/XilinxShell.scala:11-15`; instantiated at `:56` |
| `shell.io.pdc` | `IO_PDC`, `shell/microsemi/PolarFireShell.scala:12-16`; instantiated at `:31` |
| `assign.tcl` | `IO_TCL`, `shell/altera/AlteraShell.scala:11-15`; instantiated at `:41` |
| `shell.vivado.tcl` | `XilinxShell`, `shell/xilinx/XilinxShell.scala:59` |
| `debugjtag.vivado.tcl`, `vc707debugjtag.xdc` (x2), `vc707chiplink.vivado.tcl`, `vc707chiplink.xdc`, `old-shell.vivado.tcl` | `shell/xilinx/VC707Shell.scala:64,72,83,132,138,602` (traits on the legacy `VC707Shell` `RawModule`) |
| `vc707chiplink.vivado.tcl`, `vc707chiplink.xdc` | `shell/microsemi/PolarFireEvalKitShell.scala:120,128` |

**Vendor IP generation scripts**

- **When added.** Module elaboration, in the `BlackBox` constructor of each IP wrapper.
- **Keys.** `<moduleName or desiredName>.vivado.tcl`, `.quartus.tcl` or `.libero.tcl`, which is unique per parameterisation. Some keys are fixed (see the collision bullet).
- **Producers.** `ip/xilinx/Xilinx.scala:184,237,296`, `xdma/xdma.scala:238`, `xxv_ethernet.scala:107`, `vc707mig.scala:518`, `vcu118mig.scala:95`, `zcu102mig.scala:93`, `arty100tmig.scala:249`, `nexysvideomig.scala:245`, `alinx_axku040mig/axku040mig.scala:83`, `vc707axi_to_pcie_x1.scala:401`, `ip/altera/alterapll.scala:47`, `datastorm_uniphy.scala:120`, and `ip/microsemi/*` (about 12 sites, such as `polarfire_ccc/PolarFireCCC.scala:66` and `polarfire_ddr4/PolarFireDDR4.scala:98`).
- **Collision.** Fixed keys such as `vcu118mig.vivado.tcl` and `Libero.polarfire_reset.libero.tcl` repeat per instance. The script content is the same per type, so repeats are harmless.

### 2.8 testchipip: `dromajo_params.h` (dormant)

- **Site.** `testchipip/src/main/scala/cosim/Dromajo.scala:46`, `DromajoHelper.addArtefacts(location)`.
- **Status.** Nothing in `dependencies/` calls it. When a design calls it, the timing depends on the caller.
- **Content.** Eager, built from `Parameters`.
- **Collision.** The key is fixed.
- **Note.** `testchipip/.../spi/SimSPIFlash.scala:35` only appends to `PlusArgArtefacts`, so it feeds `plusArgs` (2.3).

### 2.9 emitrtl: `graphml`

- **Site.** `emitrtl/src/main/scala/Top.scala:240`, in `LazyToplevel.genDiplomacyGraph()`.
- **When added.** After elaboration, when the user calls it explicitly. It **needs the `lazyTop` handle**.
- **Writes.** Only the `graphml` keys, so every other registry entry from 2.1 to 2.7 is dropped on this path. That is the root cause noted in #3.
- **Collision.** If the Top contains a `BaseSubsystem`, there are two `graphml` entries. Both have the same content (section 3), so the result is harmless.

## 3. graphML: what a `LazyModule` handle gives you

- `diplomacy/diplomacy/src/diplomacy/lazymodule/LazyModule.scala:157-170`:
  `lazy val graphML: String = parent.map(_.graphML).getOrElse { ...render nodes/edges... }`.
- **Calling `graphML` on any `LazyModule` returns the whole root's graph.** No API returns a subtree graph.
- The renderers `nodesGraphML` and `edgesGraphML` are `private` (`:185`, `:214`). A per-`LazyModule` subgraph needs new code in emitrtl that walks `getChildren` and `getNodes` (`:256-262`, public). The alternative is a patch to diplomacy, which #3 rules out.
- `omitGraphML` (`:143`) prunes empty modules and nodes.
- Content is computed when first read, after the graph is final. Construction or elaboration timing does not matter, provided the root handle is still reachable.
- On the chipyard stage path, `PreElaboration` keeps only `LazyModule(a).module` (`chipyard/tools/stage/src/main/scala/phases/PreElaboration.scala:37`).
  - The handle is still reachable through `module.wrapper`.
  - The registry gets `graphml` only when the Top contains a `BaseSubsystem`.

## 4. Side registry: `PlusArgArtefacts`

- `rocket-chip/src/main/scala/util/PlusArg.scala:93-140`: `private var artefacts: Map[String, PlusArgContainer[_]]`, filled by `PlusArg.apply` and `PlusArg.timeout` (`:78,87`) during module elaboration.
- There is **no clear and no reset**, and the field is private. The only output is `serialize_cHeader()`.
  - A per-elaboration snapshot of `plusArgs` is impossible without reflection.
  - Content can therefore leak across elaborations in one JVM.
  - Keys are PlusArg names, so a duplicate name replaces the old entry rather than repeating it.

## 5. Producers in code playground does not compile (chipyard `generators/`)

| Key | Site | When | Notes |
|---|---|---|---|
| `<parent-or-own suggestedName>.graphml` | `chipyard/generators/chipyard/src/main/scala/Subsystem.scala:126-127`, `ChipyardSubsystemModuleImp` | module elaboration | First **removes every `graphml` entry** (`files = files.filterNot(_._1 == "graphml")`), then re-adds under a named key. This is the only registry mutation in deps. |
| `plusArgs` | `chipyard/generators/chipyard/src/main/scala/unittest/UnitTestSuite.scala:7` | module elaboration | Same as 2.5. |
| `<pllName>.freq-summary` | `chipyard/generators/chipyard/src/main/scala/clocking/DividerOnlyClockGenerator.scala:89` (`SimplePllConfiguration.emitSummaries`) | caller-defined | The only caller is `firechip/chip/src/main/scala/FireSim.scala:43`. |
| `<prefix>noc.graphml`, `noc.adjlist`, `noc.xy`, `noc.edgeprops` | `chipyard/generators/constellation/src/main/scala/noc/NoC.scala:174,182,198,215` | module elaboration | `prepend` uses a NoC name prefix. |

`TLClockSelector.scala` and `TLClockDivider.scala` import `ElaborationArtefacts` but never use it.

## 6. Summary: artefact kinds a selector must name

| Kind | Key pattern | Free from registry? | Added during | Collision risk |
|---|---|---|---|---|
| regmap | `0x<base>.<n>.regmap.json` | yes (TL register devices only) | module elaboration | none within one run (suffix counter); suffix drifts across runs; truncated above 4 GiB |
| axi4 ID map | `<adapterName>.axi4.json` | yes (only if `adapterName` set) | module elaboration | same-name adapters |
| graphml | `graphml` | only via `BaseSubsystem`; otherwise needs the root `LazyModule` | elaboration, or explicit | fixed key; content is always the root graph |
| dts | `dts` | only via `BaseSubsystem`; otherwise needs a `BindingScope` `LazyModule` | module elaboration | fixed key |
| dts json | `json` | same as dts | module elaboration | fixed key |
| memmap | `memmap.json` | only via `BaseSubsystem` | module elaboration | fixed key |
| plusArgs | `plusArgs` | via `BaseSubsystem` or `UnitTestModule` | module elaboration | fixed key; global `PlusArgArtefacts` cannot be cleared |
| l2 | `l2.json` | yes (inclusive-cache config) | **construction** (content valid after elaboration) | fixed key |
| fpga shell constraints | `shell.sdc`, `shell.xdc`, `shell.io.pdc`, `assign.tcl`, `shell.vivado.tcl`, ... | yes (fpga-shells `Shell` Tops) | **construction** (content valid after elaboration) | fixed keys |
| fpga IP tcl | `<module>.{vivado,quartus,libero}.tcl` | yes | module elaboration | some fixed keys; content identical |
| dromajo | `dromajo_params.h` | only if a design calls `DromajoHelper.addArtefacts` | caller-defined | fixed key |

**Implications for #3**

1. **Classify by suffix.** Every key is a file suffix, so a selector can classify entries by suffix, for example `*.regmap.json`, `graphml`, `dts`.
2. **Write after elaboration.** All entries are lazy, and several are valid only after module elaboration. Write them after elaboration, as both existing writers already do.
3. **Per-elaboration isolation is possible.** It can be done in emitrtl by assigning or snapshotting `ElaborationArtefacts.files`, as `Subsystem.scala:126` does. It is **not** possible for `PlusArgArtefacts` or `BindingScope.bindingScopes` without reflection.
4. **Duplicate keys are real.** Examples are `graphml`, `plusArgs` and `mem.axi4.json`. Today, the first-added entry silently wins on disk.
5. **Two kinds need a `LazyModule` handle.** graphML and DTS for a Top without a subsystem need one. graphML for a sub-`LazyModule` needs new code, because `graphML` always returns the root graph.
