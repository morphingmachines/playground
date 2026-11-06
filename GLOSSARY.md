# Playground

Playground is a shared base for Chisel RTL development on rocket-chip and chipyard. It holds the sources, toolchain choices and build conventions that every consumer repo reuses.

## Language

### Repositories

**Playground**:
The shared base repository that consumer repos build on.
_Avoid_: template, framework

**Consumer repo**:
A repository checked out next to playground that builds its RTL on playground.
_Avoid_: project, sibling repo, client repo

### Dependencies

**Shared dependency**:
A source dependency held by playground and available to every consumer repo, such as rocket-chip, diplomacy or cde.
_Avoid_: playground dependency, common dependency

**Local dependency**:
A source dependency held by one consumer repo and used only by it, typically the repo's own IP blocks.
_Avoid_: project IP, private dependency

### Building RTL

**Chisel version**:
The selected toolchain combination: chisel6 (the default) or chisel7. Each one fixes the Chisel library, Scala and firtool versions used together.
_Avoid_: Chisel mode, Chisel 7 toggle

**Top**:
The named design-and-configuration pair that one elaboration produces RTL for.
_Avoid_: target, lazymodule, lazyTop, DUT

**Artefact**:
A non-RTL file emitted by elaborating a Top, such as a register map, diplomacy graph or device tree. Follows chipyard's own usage.
_Avoid_: collateral, artifact, side file

**Artefact kind**:
A category of artefact, such as register map or diplomacy graph, that a Top can request.
_Avoid_: extension, type, suffix

**Artefact selection**:
The artefact kinds requested for one elaboration of a Top, set in the Top or overridden at run time.
_Avoid_: artefact filter, artefact list, selector
