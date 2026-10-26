# Architecture

Phone Cluster is a two-process system on two Android phones.

```text
Model phone                              Compute node
┌──────────────────────────┐            ┌──────────────────────────┐
│ Android app              │            │ Android app              │
│  ├─ native llama-server  │            │  └─ native ggml-rpc-server│
│  ├─ WebView chat         │            │                          │
│  └─ model file           │            │                          │
└────────────┬─────────────┘            └────────────┬─────────────┘
             │                                       │
             └──────── ggml-rpc over TCP ────────────┘
                         host:50053
```

## Roles

### Model phone

The model phone owns the GGUF file and starts upstream `llama-server` with:

```text
--rpc <node>:50053
--host 127.0.0.1
--port 8080
-ngl 99
```

`llama-server` builds the model graph, registers the remote backend, and exposes
the local chat UI. The WebView loads only `127.0.0.1:8080`.

### Compute node

The node starts upstream `ggml-rpc-server` with:

```text
-H 0.0.0.0
-p 50053
-t <threads>
```

It offers its CPU backend to the model phone. It never receives the model file.

## Pairing

Pairing is intentionally simple:

1. The host resolves its current IPv4 address and appends port `50053`.
2. The user copies that address to the model phone.
3. The model phone verifies the address is non-empty and contains a port.
4. `llama-server` opens a TCP connection to the node.

There is no account, discovery service, or broker. The address is the pairing
artifact.

## Process control

Both roles are native Android processes launched by the app:

- binaries are packaged as Android native libraries and executed from
  `ApplicationInfo.nativeLibraryDir`
- stdout and stderr are merged and streamed into the in-app log
- stopping a role calls `Process.destroy()` and waits before returning

No root, Termux, or PC is required.

## Data flow

Only tensor work and RPC control messages cross the local network. The model file
stays on the model phone. The node does not write model data to disk.

## Deliberate boundaries

This stand uses upstream `llama.cpp` behavior only. It does not contain:

- a custom transport
- hidden-state or logits-free communication
- draft-model acceleration
- an automatic layer scheduler
- remote model transfer
- authentication or encryption above TCP
