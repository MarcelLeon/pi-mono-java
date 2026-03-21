# Benchmark Results Template

Use this template when publishing benchmark evidence in PRs/releases.

## Environment
- Date:
- Machine (CPU/RAM):
- OS:
- Java (`java -version`):
- Maven (`mvn -version`):
- Git commit:

## Commands
```bash
./scripts/benchmark_smoke.sh
```

## Result Summary
| Step | Status | Duration | Notes |
|---|---|---:|---|
| Compile |  |  |  |
| Session Unit Test |  |  |  |
| Spring Example Tests |  |  |  |
| CLI Smoke |  |  |  |

## Raw Logs
- Report markdown:
- Log directory:

## Interpretation
- Is this run better/worse than baseline?
- Any known noise (network, background load, JDK changes)?
- Follow-up actions:
