# Contributing

Keep the stand small.

## Good first contributions

- a measured run on a new phone pair
- a reproducible connectivity failure
- a smaller implementation of an existing behavior
- a model preset with a memory estimate
- a test for the pairing/address parser

## Rules

1. No model files, APKs, keys, or generated build output in a PR.
2. Keep native binaries buildable from upstream `llama.cpp`.
3. Do not add a hidden optimization to this repository. This is a reference
   stand, not the production engine.
4. If you change behavior, update `docs/ARCHITECTURE.md` or
   `docs/BENCHMARKS.md` in the same PR.
5. Include the exact device configuration for any performance claim.

Open an issue before a large change. Small fixes can go straight to a PR.

## Local checks

```bash
./gradlew :app:assembleDebug
```

`app/build/outputs/apk/debug/app-debug.apk` must build with the binaries already
tracked in `app/src/main/jniLibs/arm64-v8a`.

## Commit style

Use short conventional commits:

```text
feat: add pairing screen
fix: stop the node before restart
docs: record a two-phone run
```
