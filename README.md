# IO Stress Test

[![CI](https://github.com/tmyroadctfig/io-stress-test/actions/workflows/ci.yml/badge.svg)](https://github.com/tmyroadctfig/io-stress-test/actions/workflows/ci.yml)

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
| `--canary` | No | Drop an EICAR test file into each worker directory and verify it is intact after the test. Exits with code `1` if any canary is missing or altered. |

### Worker Types

| Type | Behaviour |
|---|---|
| `read` | 50% sequential full-file reads, 50% random-seek chunk reads |
| `listing` | Recursive directory listings, picks random subdirectories each iteration |
| `write` | Writes random-sized binary files in a UUID directory structure: `{dir}/abc/def/abcdef12-….bin` |
| `read+list` | Interleaves reads and directory listings in each thread. Default 50/50 mix; override with `read+list[75]` for 75% reads / 25% listings (any value 0–100). |

Worker output files (created by `write` workers) and corpus files (created by `--corpus`) are **always deleted** after the test. Existing files in directories pointed to by `read`, `listing`, and `read+list` workers are **never modified**.

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

### Detecting antivirus interference with `--canary`

Add `--canary` to any run to drop an [EICAR test file](https://www.eicar.org/download-anti-malware-testfile/) into each worker directory before the test. After the test completes the tool checks each file is byte-for-byte intact. If AV software on the server quarantines or replaces a file during the run, the summary will show what was found and the process exits with code `1`:

```bat
java -jar io-stress-test-1.0.0.jar ^
  --duration=2h ^
  --canary ^
  --corpus=\\fileserver\evidence\corpus:500 ^
  --worker=read:10:\\fileserver\evidence\corpus ^
  --worker=write:10:\\fileserver\output
```

Example output when AV interferes:

```
├──────────────────────────────────────────────────────────────────────────────────┤
│ CANARY (EICAR)                                                                   │
├──────────────────────────────────────────────────────────────────────────────────┤
│  ✓  \\fileserver\evidence\corpus   OK                                            │
│  ✗  \\fileserver\output            ALTERED                                       │
│       found: "This file has been quarantined by antivirus software."             │
└──────────────────────────────────────────────────────────────────────────────────┘
```

The EICAR file is deleted during cleanup regardless of the result. The tool uses the standard [EICAR test string](https://www.eicar.org/download-anti-malware-testfile/) — it is harmless and universally recognised by AV products as a safe test marker.

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

After cleanup, a results table shows throughput and latency percentiles for each operation type:

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                           IO STRESS TEST — RESULTS                                │
├───────────────────────────────────────────────────────────────────────────────────┤
│ PHASE         DURATION    STATUS                                                   │
│ Setup         3.241s      OK                                                       │
│ Test          1h 00m 03s  OK                                                       │
│ Cleanup       12.544s     OK                                                       │
├───────────────────────────────────────────────────────────────────────────────────┤
│ OPERATION                 OPS       BYTES         RATE    AVG μs    p95 μs    p99 μs│
├───────────────────────────────────────────────────────────────────────────────────┤
│ Sequential Read     1,234,567  456.78 GiB  572.81 MiB/s       234       512       890│
│ Random Read        12,345,678  789.01 GiB  988.76 MiB/s        12        38        67│
│ Dir Listing         2,345,678   3.45 MiB          N/A          43       120       245│
│ Write                 123,456   45.67 GiB   57.13 MiB/s     1,234     2,890     3,456│
└───────────────────────────────────────────────────────────────────────────────────┘
```

If any IO errors occurred, each distinct error type appears as an indented sub-row with a count and a representative message (file paths are replaced with `<test-directory>`):

```
│ Sequential Read       5,885   34.75 GiB  572.81 MiB/s     8,121     4,223     5,103│
│   ↳  22,796x  FileSystemException: <test-directory>: The network path was not found │
│   ↳      1x  IOException: The network path was not found                            │
```

**Live dashboard metrics:**
- **OPS/S** — operations completed per second, measured over the last 500 ms refresh interval
- **THROUGHPUT** — MB/s read or written over the last refresh interval; `N/A` for directory listings

**Final summary metrics:**
- **OPS** — total operations completed over the full test
- **BYTES** — total bytes transferred (reads and writes); entry count for directory listings
- **RATE** — average transfer rate over the test duration (bytes ÷ test duration); `N/A` for directory listings
- **AVG μs** — mean operation latency in microseconds, across all operations of that type for the full test run
- **p95 μs** — 95th-percentile latency: 95% of operations completed faster than this value. A p95 significantly higher than AVG indicates occasional slow outliers — often caused by SMB reconnects, GC pauses, or server load spikes
- **p99 μs** — 99th-percentile latency: the worst 1% of operations. A large gap between p95 and p99 suggests rare but severe stalls — worth investigating if the workload is latency-sensitive
- **ERRORS** — count of IO errors per operation type, broken down by exception class and message prefix

## Tips for Reproducing Server Bottlenecks

- **Increase thread counts gradually** to find the threshold where errors first appear or latency spikes
- **Use two separate share paths** for reads and writes to simulate a real e-discovery topology (evidence share → output share)
- **Run multiple instances** of the tool simultaneously from different clients to simulate concurrent users
- **Watch p95/p99 latency** — a large gap between AVG and p99 often indicates intermittent server resource exhaustion before errors start appearing
- **Use `--canary`** to rule out AV interference early — silent quarantining can cause write errors and latency spikes that look like network or storage problems

## License

Apache License 2.0 — see [LICENSE](LICENSE).
