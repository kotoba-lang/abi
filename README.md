# kotoba-lang/abi

The versioned, implementation-free contract for the Kotoba execution stack.

`abi` owns only data that must mean the same thing to independently released
projects: WIT worlds, capability names, component-admission envelopes, artifact
identity bindings, and conformance vectors.  It deliberately owns neither a
Wasm engine, policy decision, device driver, scheduler, nor deployment client.

## Stack

```text
kotoba-lang/kotoba + compiler     source language and Component producer
                 │
                 ▼
              abi                WIT + artifact/grant contract
        ┌────────┼─────────┐
        ▼        ▼         ▼
    kototama   aiueos   murakumo
    runtime    authority control plane
```

The only permitted runtime path is:

```text
compiler → signed Component + declared imports
         → kototama validates, links, budgets, and executes
         → aiueos decides and provides each explicitly granted import
         ← murakumo places and observes the workload; it grants no authority
```

## Contents

- [`wit/kotoba-app`](wit/kotoba-app) is the current `kotoba:app/kotoba-app@0.1.0`
  world emitted by the compiler.
- [`wit/aiueos-capability`](wit/aiueos-capability) reserves the provider-facing
  capability vocabulary.  It has no ambient WASI, filesystem, environment,
  network, clock, random, or process interface.
- [`schemas/component-admission-v1.schema.json`](schemas/component-admission-v1.schema.json)
  is the closed hand-off envelope that a runtime must validate before invoking
  an engine linker.
- [`90-docs/adr/2607252600-abi-ownership.edn`](90-docs/adr/2607252600-abi-ownership.edn) records
  ownership and dependency rules.

## Dependency rule

Each consumer pins an ABI release and may generate language bindings from WIT.
No consumer may import another consumer's implementation merely to share a
contract.  In particular, the compiler never imports the aiueos kernel;
Kototama never decides grants; and Murakumo never receives a provider handle or
secret merely because it placed a component.

New shared code earns a separate repository only when it has two or more
independent consumers and can remain below this authority boundary.  Examples
include generated WIT bindings or canonical artifact codecs.  Policy, engine,
and control-plane code stay in their owning repositories.
