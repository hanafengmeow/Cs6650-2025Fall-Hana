## CS6650 Assignment 1

### Design Architecture Diagram
<img width="1070" height="722" alt="image" src="https://github.com/user-attachments/assets/64ca17fd-d55f-4f0c-b143-19e7f56a5a5f" />


### Server
Java WebSocket chat server with an HTTP health endpoint.

#### Requirements
- Java 17+
- Maven 3.8+
- Ports: WebSocket 8080, HTTP 8081

#### Build
From `assignment1/ws_hana`:
```bash
mvn -q -DskipTests -pl server -am package
```
Artifact: `server/target/server-1.0.0.jar`

#### Run
```bash
cd server
java -jar target/server-1.0.0.jar
```

On start:
- WebSocket: `ws://localhost:8080/chat/{roomId}`
- Health: `http://localhost:8081/health`

Health check:
```bash
curl -s http://localhost:8081/health
```

Message format (JSON) expected by the server:
- Fields: `userId` (numeric 1–100000), `username` (3–20 alphanumeric), `message` (1–500 chars), `timestamp` (ISO-8601), `messageType` (`TEXT|JOIN|LEAVE`).

### Client Part 1 (Warmup)

Basic load client: multiple threads each send a fixed number of messages; prints Little’s Law prediction vs actual.

#### Requirements
- Java 17+
- Maven 3.8+
- Server running at `ws://localhost:8080/chat/{roomId}`

#### Build
From `assignment1/ws_hana`:
```bash
mvn -q -DskipTests -pl client-part1 -am package
```
Artifact: `client-part1/target/client-part1-1.0.0.jar`

#### Run
Pass system properties before `-jar`:
```bash
java \
  -DTHREADS=32 \
  -DMESSAGE_PER_THREAD=1000 \
  -DWS_BASE=ws://localhost:8080/chat/ \
  -DROOM_ID=1 \
  -DACK_TIMEOUT_MS=2000 \
  -DCONNECT_TIMEOUT_SECONDS=10 \
  -jar client-part1/target/client-part1-1.0.0.jar
```

Key properties (with defaults):
- `THREADS` (32), `MESSAGE_PER_THREAD` (1000)
- `WS_BASE` (`ws://localhost:8080/chat/`), `ROOM_ID` (1)
- `ACK_TIMEOUT_MS` (2000), `CONNECT_TIMEOUT_SECONDS` (10)

Output: theoretical prediction, actual throughput, success/fail counts, connections/reconnections, and comparison.


### Client Part 2 (Main Phase + Analysis)

High-throughput client with producer/worker pipeline, CSV logging, console analysis, and throughput PNG charts.

#### Requirements
- Java 17+
- Maven 3.8+
- Server running at `ws://localhost:8080/chat/{roomId}`

#### Build
From `assignment1/ws_hana`:
```bash
mvn -q -DskipTests -pl client-part2 -am package
```
Artifact: `client-part2/target/client-part2-1.0.0.jar`

#### Run (example)
```bash
java \
  -DWS_BASE=ws://localhost:8080/chat/ \
  -DTOTAL=50000 \
  -DROOMS=20 \
  -DSENDERS=32 \
  -DQUEUE_CAP=50000 \
  -DACK_TIMEOUT_MS=60000 \
  -DBUCKET_SEC=10 \
  -DCSV_DIR=client-part2/results \
  -DCSV_PREFIX=run \
  -DCHART_DIR=client-part2/charts \
  -DPREDICT_RTT_MS=50 \
  -DCONNECT_TIMEOUT_SECONDS=10 \
  -jar client-part2/target/client-part2-1.0.0.jar
```

Key properties (defaults):
- `WS_BASE` (`ws://localhost:8080/chat/`), `TOTAL` (500000), `ROOMS` (20)
- `SENDERS` (max(8, CPU cores × 4)), `QUEUE_CAP` (50000)
- `ACK_TIMEOUT_MS` (60000), `CONNECT_TIMEOUT_SECONDS` (10)
- `BUCKET_SEC` (10), `CSV_DIR` (`client-part2/results`), `CSV_PREFIX` (`run`)
- `CHART_DIR` (`client-part2/charts`)
- `PREDICT_RTT_MS` (50), `CONNECTION_OVERHEAD_MS` (5)
- Optional: `PREDICT_K_PER_ROOM` (600), `SUMMARY_PATH` (override summary CSV path)

Outputs:
- CSV per run: `client-part2/results/run_YYYYMMDD_HHMMSS.csv`
  - Columns: `timestamp,messageType,latencyMs,statusCode,roomId`
- Summary: `client-part2/summary.csv` (append one line per run; override with `-DSUMMARY_PATH=...`)
- Chart PNG: `client-part2/charts/throughput_<runId>_S<SENDERS>_Q<QUEUE_CAP>_TO<ACK_TIMEOUT_S>s.png`

Console prints:
- Success/fail/retries/connections/reconnections, wall time, throughput
- Little’s Law predicted vs actual
- Latency stats (mean, p50, p95, p99, min, max)
- Per-room throughput, per-type counts, per-time-bucket throughput



