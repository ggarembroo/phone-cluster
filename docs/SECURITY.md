# Security model

Phone Cluster is a trusted-LAN developer stand. It is not hardened for an
untrusted network.

## What is exposed

The compute node runs:

```text
ggml-rpc-server -H 0.0.0.0 -p 50053
```

Port `50053` has no authentication. Anyone who can reach it can send compute
work to the node. The RPC protocol is also not encrypted.

The model phone runs:

```text
llama-server --host 127.0.0.1 --port 8080
```

Port `8080` is bound to loopback and is not reachable from the LAN.

## Safe use

- Use a private Wi-Fi network or a hotspot with a strong password.
- Prefer USB tethering when possible.
- Do not port-forward `50053`.
- Do not run this stand on public Wi-Fi.
- Stop the node when you are done.
- Do not put model files or keys in a public repository.

## Threat model

In scope:

- a trusted local network
- two devices owned by the same operator
- a user who wants to inspect and measure local inference

Out of scope:

- hostile LAN users
- internet exposure
- multi-tenant isolation
- model or prompt confidentiality on the node
- side-channel attacks
- production authentication

## Future hardening

A pairing token and an authenticated RPC wrapper are on the roadmap. Until then,
the network boundary is the security boundary.
