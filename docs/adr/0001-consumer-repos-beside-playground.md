# Consumer repos live beside playground, not inside chipyard

RTL is developed in consumer repos: separate repositories checked out next to playground. Each one imports playground's `builddefs.sc`, `playground_modules.sc` and `Makefile.include` through `../playground`. We did not follow chipyard's model of adding generators under its `generators/` tree. Many consumer repos share one pinned set of shared dependencies and one toolchain choice, and each keeps its own history, local dependencies and release cadence. Build files stay small (Mill instead of sbt), and setup needs no conda environment or RISC-V toolchain build.

## Consequences

- The relative path is a contract. Playground must be checked out at `../playground` from every consumer repo. Moving or renaming it breaks builds and silently disables the shared git hooks, because `core.hooksPath` is an absolute path.
- A change to a shared dependency or to playground's build files affects every consumer repo at once. Each consumer repo records the playground commit it was tested against in `playground.hash`.
- Chipyard is still used piecemeal (stage, tapeout/MacroCompiler, its Chisel version choices), so chipyard merges remain a periodic chore.
