# IO Stress Test

A Java command-line tool for stress-testing disk and network file systems under e-discovery-style workloads. Designed to reproduce bottlenecks observed when evidence-processing software performs concurrent reads, folder listings, and binary writes against Windows Server SMB shares.

## Prerequisites

- Java 11 or later

## Quick Start

Download the latest `io-stress-test-*.jar` from [Releases](../../releases), then run:

**Linux / macOS**
```bash
java -jar io-stress-test-1.0.0.jar \
  --duration=30m \
  --file-size-min=1KiB \
  --file-size-max=256MiB \
  --corpus=/mnt/share/corpus:200 \
  --worker=read:10:/mnt/share/corpus \
  --worker=listing:5:/mnt/share/corpus \
  --worker=write:10:/mnt/share/output
```

**Windows (cmd)**
```bat
java -jar io-stress-test-1.0.0.jar ^
  --duration=30m ^
  --file-size-min=1KiB ^
  --file-size-max=256MiB ^
  --corpus=C:\work\corpus:200 ^
  --worker=read:10:C:\work\corpus ^
  --worker=listing:5:C:\work\corpus ^
  --worker=write:10:C:\work\output
```

**Windows against a network share (UNC path)**
```bat
java -jar io-stress-test-1.0.0.jar ^
  --duration=2h ^
  --file-size-min=1KiB ^
  --file-size-max=1GiB ^
  --corpus=\\server\evidence\corpus:500 ^
  --worker=read:10:\\server\evidence\corpus ^
  --worker=listing:5:\\server\evidence\corpus ^
  --worker=write:10:\\server\output
```

## Building from Source

Requires Java 11+ and the included Gradle wrapper:

```bash
./gradlew shadowJar
# Output: build/libs/io-stress-test-*.jar
```

## CLI Reference

```
Usage: iostress [-hV] [--corpus=<corpus>] --duration=<duration>
                [--file-size-max=<size>] [--file-size-min=<size>]
                --worker=<type:threads:dir> [--worker=...]...
```

| Option | Required | Description |
|---|---|---|
| `--duration` | Yes | Test duration. Formats: `2h`, `30m`, `1h30m`, `10s` |
| `--worker` | Yes (repeatable) | Worker spec: `type:threads:directory` — see Worker Types below |
| `--corpus` | No | Synthetic corpus: `directory:fileCount` — creates files before test, deletes after |
| `--file-size-min` | No | Minimum synthetic file size (default: `1KiB`) |
| `--file-size-max` | No | Maximum synthetic file size (default: `1GiB`) |

### Worker Types

| Type | Behaviour |
|---|---|
| `read` | 50% sequential full-file reads, 50% random-seek chunk reads |
| `listing` | Recursive directory listings, picks random subdirectories each iteration |
| `write` | Writes random-sized binary files in a UUID directory structure: `{dir}/abc/def/abcdef12-….bin` |

Worker output files (created by `write` workers) and corpus files (created by `--corpus`) are **always deleted** after the test. Existing files in directories pointed to by `read` and `listing` workers are **never modified**.

### Size Format

Sizes accept IEC or SI suffixes: `1KiB`, `512MiB`, `1GiB`, `100MB`, `2GB`.

## Example Scenarios

### Reproduce an e-discovery workload against an existing share

Test against files already present on a network share, writing output to a separate location:

```bash
java -jar io-stress-test-1.0.0.jar \
  --duration=2h \
  --file-size-min=1KiB \
  --file-size-max=1GiB \
  --worker=read:16:/mnt/evidence \
  --worker=listing:4:/mnt/evidence \
  --worker=write:8:/mnt/output
```

### Synthetic load test (no existing data needed)

The tool creates a corpus of random files before the test begins, runs the test, then deletes everything:

```bash
java -jar io-stress-test-1.0.0.jar \
  --duration=1h \
  --file-size-min=64KiB \
  --file-size-max=512MiB \
  --corpus=/mnt/share/corpus:500 \
  --worker=read:10:/mnt/share/corpus \
  --worker=listing:5:/mnt/share/corpus \
  --worker=write:10:/mnt/share/output
```

### Windows Server SMB share (the primary use case)

Test an SMB share with a synthetic corpus, simulating concurrent evidence reads and extractions:

```bat
java -jar io-stress-test-1.0.0.jar ^
  --duration=2h ^
  --file-size-min=1KiB ^
  --file-size-max=1GiB ^
  --corpus=\\fileserver\evidence\corpus:500 ^
  --worker=read:10:\\fileserver\evidence\corpus ^
  --worker=listing:5:\\fileserver\evidence\corpus ^
  --worker=write:10:\\fileserver\output
```

### Light local sanity check

**Linux / macOS**
```bash
java -jar io-stress-test-1.0.0.jar \
  --duration=30s \
  --file-size-min=4KiB \
  --file-size-max=1MiB \
  --corpus=/tmp/corpus:50 \
  --worker=read:4:/tmp/corpus \
  --worker=listing:2:/tmp/corpus \
  --worker=write:4:/tmp/output
```

**Windows**
```bat
java -jar io-stress-test-1.0.0.jar ^
  --duration=30s ^
  --file-size-min=4KiB ^
  --file-size-max=1MiB ^
  --corpus=C:\Temp\corpus:50 ^
  --worker=read:4:C:\Temp\corpus ^
  --worker=listing:2:C:\Temp\corpus ^
  --worker=write:4:C:\Temp\output
```

## Understanding the Output

### Live Dashboard

During the test, a live ANSI dashboard shows:

```
┌────────────────────────────────────────────────────────────────────────────┐
│  IO STRESS TEST                                                 [ RUNNING ]│
├────────────────────────────────────────────────────────────────────────────┤
│  Elapsed: 00:05:23  Rem: 01:54:37  [████░░░░░░░░░░░░░░░░░░]    4.5%       │
├────────────────────────────────────────────────────────────────────────────┤
│  OPERATION               OPS/S   THROUGHPUT    TOTAL OPS  ERRORS           │
│  Sequential Read       1,234.5    234.5 MB/s      421,234       0          │
│  Random Read           9,876.1   1023.4 MB/s    3,362,109       0          │
│  Dir Listing           2,345.6          N/A       798,312       0          │
│  Write                   156.2     42.1 MB/s       53,109       0          │
├────────────────────────────────────────────────────────────────────────────┤
│  TYPE       THDS   DIRECTORY                           OPS                 │
│  read       10     /mnt/evidence                       3,783,343           │
│  listing    4      /mnt/evidence                         798,312           │
│  write      8      /mnt/output                           53,109            │
└────────────────────────────────────────────────────────────────────────────┘
  Press Ctrl+C to stop early
```

### Final Summary

After cleanup, a results table shows latency percentiles for each operation type:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          IO STRESS TEST — RESULTS                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│ PHASE         DURATION    STATUS                                                 │
│ Setup         3.241s      OK                                                     │
│ Test          1h 00m 03s  OK                                                     │
│ Cleanup       12.544s     OK                                                     │
├──────────────────────────────────────────────────────────────────────────────────┤
│ OPERATION                 OPS       BYTES    AVG μs    p50 μs    p95 μs    p99 μs│
├──────────────────────────────────────────────────────────────────────────────────┤
│ Sequential Read     1,234,567  456.78 GiB       234       198       512       890│
│ Random Read        12,345,678  789.01 GiB        12         9        38        67│
│ Dir Listing         2,345,678   3.45 MiB         43        31       120       245│
│ Write                 123,456   45.67 GiB     1,234     1,012     2,890     3,456│
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Metrics key:**
- **OPS/S** — operations per second at the last dashboard refresh interval
- **THROUGHPUT** — MB/s read or written at the last refresh interval
- **AVG/p50/p95/p99 μs** — operation latency in microseconds (cumulative over the full test)
- **ERRORS** — count of IO errors encountered (the error type is OS-reported, e.g. access denied, network timeout)

## Tips for Reproducing Server Bottlenecks

- **Increase thread counts gradually** to find the threshold where errors first appear or latency spikes
- **Use two separate share paths** for reads and writes to simulate a real e-discovery topology (evidence share → output share)
- **Run multiple instances** of the tool simultaneously from different clients to simulate concurrent users
- **Watch p95/p99 latency** — a large gap between p50 and p99 often indicates intermittent server resource exhaustion before errors start appearing

## License

Apache License 2.0 — see [LICENSE](LICENSE).
