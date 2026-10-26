# Third-party software

## llama.cpp and ggml

Phone Cluster packages two executables built from upstream
[llama.cpp](https://github.com/ggml-org/llama.cpp):

- `llama-server`
- `ggml-rpc-server`

llama.cpp and ggml are MIT-licensed. The binaries in
`app/src/main/jniLibs/arm64-v8a` are built with:

```text
build 11530, commit a518119d3
Android NDK 27.1
arm64-v8a
android-28
static linking
GGML_RPC=ON
```

The source and license are available in the upstream repositories. Rebuild them
with `scripts/build-binaries.sh`.

## Models

No model weights are distributed by this repository. Each model has its own
license. Check the model card before redistributing a GGUF file.

## Android

The app uses only the Android platform SDK. It does not bundle analytics, ad
SDKs, or a cloud client.
