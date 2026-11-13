# Chisel 6.7.0 → 7.13.0 migration notes

These notes cover what changes when a design moves from **chisel6** (Chisel 6.7.0, firtool 1.62.1) to **chisel7** (Chisel 7.13.0, firtool 1.149.0). They are written for RTL designers, verification engineers and physical design engineers. Read them for two reasons: so the changes do not surprise you, and so you can decide where a rewrite pays off.

How to select chisel7 in playground is described in the [readme](../readme.md#chisel-7). In short: `USE_CHISEL7=1 make rtl`, then `rm -rf out` whenever you switch versions.

**Sources.** Most items come from the Chisel GitHub release notes: [v7.0.0](https://github.com/chipsalliance/chisel/releases/tag/v7.0.0), whose changelog covers everything since v6.7.0 including the M1/M2/RC pre-releases, and v7.1.0 through [v7.13.0](https://github.com/chipsalliance/chisel/releases/tag/v7.13.0). The Chisel 7.13.0 docs and sources confirm the API shapes used in the examples. PR numbers are given as (#NNNN) so you can read the detail at `https://github.com/chipsalliance/chisel/pull/NNNN`. Items marked *(playground)* come from this repository, not from upstream.

## Contents

1. [Before you switch](#1-before-you-switch)
2. [Changes that can alter generated hardware](#2-changes-that-can-alter-generated-hardware)
3. [Removed and deprecated APIs](#3-removed-and-deprecated-apis)
4. [Verification](#4-verification)
5. [Physical design and backend](#5-physical-design-and-backend)
6. [Build and toolchain](#6-build-and-toolchain)
7. [Using the new features](#7-using-the-new-features)
8. [Open items for playground](#8-open-items-for-playground)

## 1. Before you switch

1. **Clear all deprecation warnings on chisel6 first.** Chisel 7 deleted many APIs that 6.x only deprecated. Compile with `-deprecation` while the old code still builds, and fix every warning.
2. **Expect a different RTL text.** firtool jumps from 1.62.1 to 1.149.0, and several naming rules change (see [§5](#5-physical-design-and-backend)). Do not use text diffs of the Verilog to judge correctness. Use regression simulation, or an equivalence check (yosys eqy, a commercial LEC) between the chisel6 and chisel7 netlists of a block.
3. **Review the items in [§2](#2-changes-that-can-alter-generated-hardware) for every block you own.** They are the only changes that can silently alter function.
4. **Check that asserts and printfs still fire in your simulation flow** ([§4.1](#41-assertions-assumptions-covers-and-printfs-now-live-in-layers)). Outside ChiselSim they are disabled by default.

## 2. Changes that can alter generated hardware

These changes compile without error but can change bit layout, widths or connections. Check each one that applies to your block.

| Change | What happens | What to do |
|---|---|---|
| **Bundle field order** (#4226) | Fields are now ordered strictly by declaration order, including fields inherited from a parent Bundle. | If a Bundle extends another Bundle, check its `asUInt`/`asTypeOf` bit packing and the port order in the emitted Verilog. Anything that reads the packed value (register maps, DMA descriptors, software headers, SV interfaces written by hand) may need updating. |
| **ChiselEnum width** (#4200, #4990) | The emitted width now matches `getWidth`. Width inference now accounts for known-width `Value()` literals. | Build with and without the CLI option `--use-legacy-width` and diff the result, as upstream recommends. The option is temporary. |
| **`.asTypeOf` result is read-only** (#4198, #4168) | `.asTypeOf` no longer returns a Wire. Connecting to its result is deprecated. Casting a literal now gives a literal. | Where you assign into a cast result, write `WireInit(x.asTypeOf(t))`. |
| **`VecInit` connects with `:<>=`** (#4248) | `VecInit` used to choose between `:=` and `<>` based on direction. It now always uses `:<>=`. | Recheck `VecInit` over bidirectional types such as `Decoupled`. |
| **SInt literal width check** (#4786) | A literal whose width is too small for its value is rejected. | To reinterpret raw two's-complement bits, write `0xFF.U(8.W).asSInt`. |
| **`ActualDirection`** (#4205) | A Bundle that mixes unspecified and `Output` fields is no longer treated as bidirectional. | Only matters if your code inspects `DataMirror` directions. |
| **Zero-width extract** (#4445) | Extracting zero bits from a zero-width `UInt` is now legal instead of an error. | Nothing to do. Parametric code may now elaborate where it used to fail. |

## 3. Removed and deprecated APIs

### Removed in 7.0

- **Aspects** (`chisel3.aop`, injecting aspects) (#4417, #4424). Stages that depend on `MaybeAspectPhase` or `MaybeInjectingPhase` no longer compile. *(playground)*: chipyard's `tools/stage-chisel7` already drops them.
- **`chisel3.testers`** (`BasicTester` and friends) (#4746). Port these tests to ChiselSim ([§4.2](#42-testing-chiseltest-and-chiselsim)).
- **Old annotation API** (#4717): `ChiselAnnotation`, `ChiselMultiAnnotation`, their `annotate` methods, the `Circuit` APIs on `ChiselOptions`/`ChiselCircuitAnnotation`, and the ChiselEnum annotations. The replacement API requires you to declare which `InstanceId`s are annotated, so Chisel can check them (#4643). Use `ElaboratedCircuit` instead of the internal `Circuit`. This mostly affects custom annotations written in the rocket-chip style.
- **Legacy SFC emission.** *(playground)*: chipyard's `stage-chisel7` deletes `LegacyFirrtl2.scala` and `--emit-legacy-sfc`. Any flow that runs the design's `.fir` through the old Scala FIRRTL compiler needs a separate path. MacroCompiler (`chipyardTapeout`) is not affected: it reads the memory config that firtool writes with `--repl-seq-mem`, not the design's FIRRTL, and runs in its own JVM on Chisel 3.6.1.

### Deprecated (still compiles, plan to move)

| Deprecated | Replacement | Release |
|---|---|---|
| `BlackBox` | `chisel3.ExtModule`, now out of `experimental` | 7.5 (#5099–#5103) |
| `chisel3.experimental.ExtModule`, `Params` and the ExtModule utility traits | The non-experimental versions | 7.5 |
| Everything in package `firrtl` | Chisel APIs. Open an upstream issue if one is missing. | 7.0 (#4878) |
| `FileUtils`, `BlackBoxSourceHelper`, `BackendCompilationUtilities` | — (slated for removal) | 7.0 (#4754) |
| `ChiselStage.convert` | `ChiselStage.elaborate` | 7.0 (#4816) |
| `Convert` phase | — | 7.11 (#5258) |
| Stage methods taking `AnnotationSeq` | Overloads taking `Seq[Annotation]` | 7.1 (#5030) |
| `@instantiable` on user types; looking up Modules from a `Definition`/`Instance` | `Lookupable.product1`–`product5`, `.toInstance` | 7.0 (#4519) |
| `autoNameRecursively` | `chisel3.withName` | 7.0 (#4799) |
| `Printable.unpack`, `.unpackArgs` | New versions taking an implicit `SourceInfo` | 7.0 (#4858) |
| `unary_-%` on `UInt` | — | 7.0 (#4829) |
| Scala ≤ 2.13.8 | Scala 2.13.18 *(playground default for chisel7)* | 7.12 (#5295) |

The deprecated `BoringUtils` APIs were removed and then restored (#4852, #4873). They still exist in 7.13.

### New warnings

- `<literal>.asUInt(n)` with an `Int` argument now warns, because you probably meant `.W` (#4764).
- Scala 2.13.17 and later warn about the common `val io = IO(new Bundle { ... })` pattern ("will no longer have a structural type"). The warning is harmless for Chisel. Silence it with the scalac option `-Wconf:msg=will no longer have a structural type:s` (7.2).

## 4. Verification

### 4.1 Assertions, assumptions, covers and printfs now live in layers

This is the change most likely to cause a surprise.

- `assert`, `assume`, `cover` and `stop` are placed into Chisel's built-in verification layers (#4323). `printf` is placed into the `Verification` layer (#4506).

  ```
  chisel3.layers.Verification                          (Extract)
  ├── chisel3.layers.Verification.Assert               (Extract)
  │   └── chisel3.layers.Verification.Assert.Temporal  (Inline)    7.1
  ├── chisel3.layers.Verification.Assume               (Extract)
  │   └── chisel3.layers.Verification.Assume.Temporal  (Inline)    7.1
  └── chisel3.layers.Verification.Cover                (Extract)
      └── chisel3.layers.Verification.Cover.Temporal   (Inline)    7.1
  ```

- These layers are always emitted, even when the design has no verification code (#4350). Their directories use lowercase names (#4505).
- An **extract** layer is removed from the main design and written to a bind file named `layers-<circuit>-<layer>[-<child>].sv`. The layer is enabled only if that file is part of the compile. Including a child layer's file also pulls in its parent's file.
- An **inline** layer stays in the design behind `` `ifdef ``. Enable it with a preprocessor define of the form `layer$<Parent>$<Child>`.
- **ChiselSim enables all layers automatically.** Every other flow does not: a chipyard harness, a VCS or Verilator filelist you maintain yourself, or a lint or FPGA run. In those flows, asserts and printfs **silently disappear** unless you do one of the following:
  - Add the `layers-*.sv` bind files to the simulation filelist. This keeps them out of synthesis.
  - Or specialize the layers at firtool time, so the code is inlined as before:

    ```
    -enable-layers=Verification
    -enable-layers=Verification.Assert
    -enable-layers=Verification.Assume
    -enable-layers=Verification.Cover
    ```

    Use `-disable-layers=...` to remove them instead, or `-default-layer-specialization` to set a default. From `ChiselStage`, pass each one with `--firtool-option`.
- *(playground)*: emitrtl's `firrtl2sv` passes none of these options today (`dependencies/emitrtl/src/main/scala/Top.scala`). On chisel7 the verification code therefore ends up in bind files next to the split Verilog. Decide per flow which approach to use; see [§8](#8-open-items-for-playground).

### 4.2 Testing: chiseltest and ChiselSim

- **chiseltest (ucb-bar) does not support Chisel 7.** The upstream replacement is **ChiselSim**, which ships inside the Chisel jar. It drives Verilator or VCS through svsim.
- **7.13 adds a ChiselTest compatibility layer** (#5221). It is a `chiseltest` package inside the Chisel jar, so remove the old chiseltest dependency or the two `chiseltest` packages will clash. It is a thin wrapper over ChiselSim. It differs from the original in these ways:
  - **Auto reset is off by default.** The original chiseltest reset the DUT before each test. Either poke reset yourself, or override `autoResetEnabled`/`resetCycles` in the suite.
  - `fork` is a stub. Tests that depend on concurrent threads need rewriting.
  - Formal support (`Formal`, `BoundedCheck`, `past`) is stubs only.
  - `WriteVcdAnnotation` works. `VerilatorBackendAnnotation` is accepted for compatibility only.

  Use it to get an existing test suite running quickly. Write new tests in ChiselSim.
- **ChiselSim with ScalaTest:**

  ```scala
  import chisel3._
  import chisel3.simulator.scalatest.ChiselSim
  import chisel3.simulator.stimulus.RunUntilSuccess
  import org.scalatest.funspec.AnyFunSpec

  class MyModuleSpec extends AnyFunSpec with ChiselSim {
    describe("MyModule") {
      it("passes data through") {
        simulate(new MyModule) { dut =>   // simulate applies a reset first; simulateRaw does not
          dut.io.in.poke(42.U)
          dut.clock.step()
          dut.io.out.expect(42.U)
        }
      }
      it("self-checks") {
        simulate(new SelfCheckingHarness)(RunUntilSuccess(1000, _.io.success))
      }
    }
  }
  ```

  - Run with waves: `-DemitVcd=1`. Other command-line options include `chiselOpts` and `firtoolOpts`, plus the extra options in `chisel3.simulator.scalatest.Cli`.
  - Each test gets its own directory under the build output.
  - Peek/poke/expect works on Enums, Records, Vecs and generic `Data` (#4824).
  - Reusable stimulus: `ResetProcedure`, `RunUntilFinished`, `RunUntilSuccess`. Stimulus can control clock frequency (#4942).
  - Settings for initial-value randomization, including X (#4835, #4939); plusargs (#4796); waveforms (VCD, FSDB, VPD).
- **Simulator back-ends:**
  - VCS coverage collection (#4793, #4812); `-debug_access` control (7.10).
  - Verilator coverage settings and reports (7.9); `--timing`/`--no-timing` (#4934); parallel builds with `-j` and `--verilate-jobs` (7.2).
  - Verilator now builds with C++17 (7.1).
  - Prebuilt software libraries can be linked into the simulation (7.8).
  - Fixes worth having: svsim memory corruption (7.9), deadlock in long simulations (7.7), FSDB with VCS 2024+ (7.4), BlackBox delayed events under Verilator (7.2).

### 4.3 Test organisation and formal

- **Inline tests** live next to the module and are elaborated as extra public modules (#4629). Select them with `--include-tests-module <glob>` / `--include-tests-name <glob>` (#4826), and run them with ChiselSim (#4946).
- **Markers** `UnitTest`, `FormalTest` (#4635, #4642) and `SimulationTest` (7.4) support test discovery.
- **`SimulationTestHarness`** / `SimulationTestHarnessInterface`, with `ChiselSim.simulateTest` and `Settings.defaultTest` (7.8): a standard shape for self-checking harnesses.
- **Formal:** formal contracts (#4682), `RequireProperty`/`EnsureProperty` (#4681), the LTL `past` operator (7.11), and `Bool.implies` (#4933).
- **FileCheck** (`chisel3.testing.scalatest.FileCheck`): check emitted FIRRTL or Verilog in unit tests (#4749).

### 4.4 Printing and logging

```scala
printf(cf"[%T] %m: count = $count%0d\n")   // %T simulation time, %m hierarchical name, %0d minimum width

val log = SimLog.file(cf"trace_$idx%0d.log")  // printf to a file, with a Printable file name
log.printf(cf"addr=$addr%x data=$data%x\n")
log.flush()
```

- `%m` (#4820), `%T` (#4875), width modifiers such as `%0d` and `%8d` (#4821), and `SimLog` (#4882, #4892, #4895) are all new in 7.0.
- Printfs are in the `Verification` layer, so the rules in [§4.1](#41-assertions-assumptions-covers-and-printfs-now-live-in-layers) apply.

## 5. Physical design and backend

### 5.1 Toolchain and IR

- Chisel 7.13.0 targets **firtool 1.149.0**. Chisel 6.7.0 targeted 1.62.1. *(playground)*: make picks the right firtool from `PATH` and checks its version. emitrtl runs the standalone `firtool` binary.
- The emitted FIRRTL version goes from 4.x to 5.1, 6.0.0 (7.0) and then 7.0.0 (7.13, #5307). A firtool older than the matching one will reject the `.fir`.
- Recheck every firtool option in your scripts against `firtool --help` for 1.149.0.

### 5.2 Names (check SDC, UPF, floorplan and ECO scripts)

Signal and module names can change even where the logic does not. Scripts that match names should be rerun on the chisel7 netlist.

- **Signal names:**
  - An empty module prefix no longer leaves a stray `_` in signal names (7.3, #5057).
  - When a view, often from `FlatIO`, is on the right of a connection, the name prefix now comes from the view's target instead of `view_` (7.6, #5117).
  - Temporaries such as `_GEN_*` and `_T_*` change because of the newer firtool, not because of Chisel.
- **Module names:** new prefixing controls, all opt-in (no change unless you use them):
  - `withModulePrefix("x")`, optionally without the separator (#4487, #4532).
  - `override def localModulePrefix` / `localPrefixAppliesToSelf` / `localModulePrefixUseSeparator` (#4509, #4532).
  - `noModulePrefix` (7.8), `BaseModule.ignoreParentPrefix` (7.12).
  - Auto-generated DedupGroups now respect the prefix (#4545).
- **ExtModule names:** the Verilog module name (defname) can be set separately from `desiredName` (7.12, #5281).

### 5.3 Hierarchy and output layout

- **Inlining and flattening controlled from Chisel:** the `InlineInstanceAllowDedup` trait (#4508), the `chisel3.experimental.inlineInstance` and `inlineInstanceAllowDedup` methods (#4883), and `FlattenInstanceAllowDedup` (7.8). These are useful for removing wrapper levels before synthesis without editing the generator.
- **`circt.OutputDirAnnotation`** places a chosen module's Verilog in its own directory (#4352), for example to hand a hard-macro wrapper to a separate flow.
- `ChiselStage.emitSystemVerilogFile` accepts `--split-verilog` (#4867).
- Layer bind files and directories now appear in the output tree ([§4.1](#41-assertions-assumptions-covers-and-printfs-now-live-in-layers)). Make sure synthesis filelists exclude them and simulation filelists include them.

### 5.4 Memories

- `CIRCTSRAMInterface` (#4494) and `SRAMBlackBox` (#4544) for SRAMs backed by CIRCT. `SRAMDescription` is now optional (#4370).
- Read latency and write latency of FIRRTL memories are exposed as parameters (7.7, #5133).
- `Mem` masked writes with the wrong data type now give a clear error message (#4805).

### 5.5 Metadata and debug information

- Util metadata is no longer emitted by default. Add `--include-util-metadata` to get it back (#4475).
- `--no-source-info` removes source locators from the `.fir` (7.2). This gives cleaner diffs between runs.
- Source locators now print as `file:line`, which is clickable in VS Code (#4249).
- New `circt_debug_*` intrinsics carry Chisel type metadata for debug tools (7.13, #5276).

## 6. Build and toolchain

- **Scala:** 2.13.18 *(playground)*. The `chisel-plugin` is published per exact Scala version, so the Scala version and plugin must match. Scala 3 support is partial and experimental.
- **Binary compatibility:** Chisel 6 jars cannot be mixed with Chisel 7. Rebuild every dependency (rocket-chip, chipyard, diplomacy and others) against Chisel 7. *(playground)*: `builddefs.sc` switches them together on `USE_CHISEL7`.
- **json4s:** Chisel 7 uses `io.github.json4s` 4.1.0 (7.5). *(playground)*: `builddefs.sc` already switches to it. Watch for transitive dependencies that still pull in `org.json4s`.
- **firtool-resolver** 2.1.1 can download a matching firtool at run time. *(playground)*: emitrtl uses the firtool on `PATH`, so this does not apply to `make rtl`. It can apply to ChiselSim tests.
- **New `ChiselStage` options:**
  - `--firtool-option` (#4463) and `--remap-layers` (#4322).
  - `--chisel-output-file` (#4894); `--module` now takes arguments (#4358).
  - `ChiselStage.emitCHIRRTLFile` streams the `.fir` to disk and supports files over 2 GiB (#4232).
- **Scope violations** are now collected and reported together at the end of elaboration, instead of stopping at the first one (#4586).

## 7. Using the new features

None of these are required for the migration. Each item names a pattern you may have today, a Chisel 7 alternative, and the benefit.

### Design (RTL)

- **Debug-only logic → user layers.** Today, debug counters, trace ports and shadow logic are either always built or controlled by a Scala parameter, and a parameter change means re-elaboration. With layers, one elaboration serves both simulation and synthesis. The logic goes into a bind file that synthesis never sees, or behind an `` `ifdef `` (inline layer).

  ```scala
  object Debug extends layer.Layer(layer.LayerConfig.Extract())
  // inside a module
  layer.block(Debug) {
    val cycles = RegInit(0.U(32.W))
    cycles := cycles + 1.U
  }
  ```

  Probes (`chisel3.probe`) and layer-colored probes and wires (#4623, #4857) let a test harness read signals inside a layer without adding ports. `util.Queue` has a variant with a shadow layer (#4589).
- **Popcount comparisons → `PopCount.atLeast`/`equalTo`/`greaterThan`.** `PopCount(x) >= n.U` builds a full adder tree. `PopCount.atLeast(n, x)`, where `x` is a `UInt` or `Iterable[Bool]`, gives smaller and faster logic when `n` is small (#5002).
- **Round-robin arbiter in a control path → reset-initialized `lastGrant`.** `new RRArbiter(gen, n, initLastGrant = true)` (#4903). This removes an X-source from an un-reset register.
- **Mapping a `Valid` payload → `Valid.map`.** `v.map(f)` returns a `Valid[B]` with the same `valid` bit (#4255).
- **Accidental drives → `.readOnly`.** Give a `.readOnly` view to code that must not drive a signal; connections to it are rejected (#4190).
- **Wide fan-in priority logic.** `Mux1H` and `PriorityMux` now check input lengths and report all errors together (#4572, #4609). Unequal-length inputs that used to elaborate now fail.
- **Decoders and enums.** `BitPat` accepts `ChiselEnum` values, and ChiselEnum converts to and from `String` (7.2). Decode tables can name opcodes instead of using raw bit patterns.
- **Clock and reset bookkeeping → domains** (experimental: 7.3, 7.10, 7.11, 7.12). Ports and wires can be associated with a `ClockDomain`, including synchronous and rational relationships, with assert helpers (#5290). This lets the compiler catch clock-domain mix-ups. The API is still changing (`ClockDomain.period` became `synchronousTo` in 7.10), so trial it on one block first.
- **`BlackBox` → `ExtModule`.** New wrappers should use `chisel3.ExtModule`. It can declare requirements (7.9) and a defname separate from `desiredName` (7.12).
- **Ports without implicit clock and reset → `FixedIORawModule`/`FixedIOModule`** (#4874), for wrappers with a fixed, generated interface.

### Verification

- **Testbench `printf` → `SimLog`.** Write per-instance trace files (`SimLog.file(cf"core$hartId%0d.log")`) instead of mixing all output on stdout.
- **Self-checking harnesses → `SimulationTestHarness` + `simulateTest`.** One harness shape works for unit tests and CI, with `RunUntilSuccess`/`RunUntilFinished` as the pass/fail rule.
- **Unit tests next to the RTL → inline tests and markers.** Keep small directed tests with the module, and select them with `--include-tests-module`.
- **SVA written by hand → LTL properties with contracts.** `chisel3.ltl` sequences and properties, `past`, `implies`, formal contracts and `RequireProperty`/`EnsureProperty`. Properties that the simulator cannot handle go into the `Temporal` inline layers, which ChiselSim disables for Verilator automatically.
- **Coverage closure** with ChiselSim's VCS and Verilator coverage support instead of external scripts.

### Physical design

- **Wrapper levels that block optimization → `inlineInstance`/`FlattenInstanceAllowDedup`.** Flatten from the generator instead of with synthesis-tool commands.
- **Separating hard-macro or IP wrappers → `OutputDirAnnotation`.**
- **Name collisions across integrated subsystems → module prefixes.** Use `withModulePrefix` or `localModulePrefix` instead of renaming in post-processing.
- **SRAM macro integration →** `CIRCTSRAMInterface` / `SRAMBlackBox`, with the newly exposed read/write latency parameters.

## 8. Open items for playground

- **Verification layers in emitrtl.** `firrtl2sv` passes no `-enable-layers`/`-disable-layers` options. Decide whether simulation flows include the `layers-*.sv` bind files or specialize the layers, and make sure synthesis filelists leave them out.
- **Known `make rtl` failure.** On chisel7, the bare `ExampleRocketSystem` fails with `public module port must have concrete reset type` (see the [readme](../readme.md#chisel-7)).
- **SRAM macro mapping.** MacroCompiler is still the SRAM mapping step on chisel7, as it is in chipyard's own chisel7 flow. Its input is the `--repl-seq-mem` config written by firtool, and firtool 1.149.0 still provides that option. Run `synthFirrtl2synthSV` followed by `chipyardMacroCompiler` on a chisel7 design to confirm that the 1.149.0 config format still parses.
- **Consumer-repo tests.** Check for `chiseltest` usage, and decide between the 7.13 compatibility layer and a ChiselSim rewrite. Note the auto-reset difference ([§4.2](#42-testing-chiseltest-and-chiselsim)).
- **Before signing off a block,** run an equivalence check or regression between its chisel6 and chisel7 netlists, with special attention to the [§2](#2-changes-that-can-alter-generated-hardware) items.
