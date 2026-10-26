# Benchmarks

This stand reports only runs that actually happened on a device pair.

## Protocol

For every row, record:

```text
date
model
quantization
file size
phone A / SoC / RAM / Android
phone B / SoC / RAM / Android
transport (Wi-Fi band or USB tethering)
llama.cpp version
threads
context size
n_gpu_layers
prompt
n_predict
prompt tokens/s
decoded tokens/s
peak memory
notes
```

The in-app **Bench** button sends this fixed request:

```json
{
  "prompt": "The capital of France is",
  "n_predict": 32,
  "temperature": 0,
  "stream": false
}
```

It reads `timings.predicted_per_second` from the local `llama-server` response.
Results are single-run and noisy. For a publishable number, run five times and
report the median.

## Reference runs

No public numbers are posted until a real device run is captured. This is
intentional. If you run the stand, open a PR with the table row and the exact
configuration.

## What not to compare

- one phone against two phones with different models
- Wi-Fi runs against USB-tether runs without labeling the transport
- a small model that fits on one phone against a split run of the same model
- an in-app bench against a different prompt or `n_predict`
