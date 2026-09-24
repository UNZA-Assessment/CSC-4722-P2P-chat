#!/usr/bin/env bash
# Owned by Pair A. Launches N nodes locally on ports PORT_BASE..PORT_BASE+N-1.
# Usage: P2P_PEERS='8000=192.168.1.10,8001=192.168.1.11' ./start_all.sh [N] [PORT_BASE] [NODE_ID]
set -e

N="${1:-3}"
PORT_BASE="${2:-8000}"
NODE_ID="${3:-}"
OUT_DIR="out"
LOG_DIR="logs"
PID_DIR=".pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

echo "Compiling..."
javac -d "$OUT_DIR" $(find src -name "*.java")

# Clean up anything left running on our port range from a previous run
START_NODE=0
END_NODE=$((N - 1))
if [ -n "$NODE_ID" ]; then
  if (( NODE_ID < 0 || NODE_ID >= N )); then
    echo "NODE_ID must be between 0 and $((N - 1))"
    exit 1
  fi
  START_NODE="$NODE_ID"
  END_NODE="$NODE_ID"
fi

for ((i=START_NODE; i<=END_NODE; i++)); do
  PORT=$((PORT_BASE + i))
  PID=$(lsof -ti tcp:"$PORT" 2>/dev/null || true)
  if [ -n "$PID" ]; then
    echo "Port $PORT in use by PID $PID - killing it first"
    kill -9 "$PID" 2>/dev/null || true
  fi
done

if [ -n "$NODE_ID" ]; then
  echo "Starting node $NODE_ID on port $((PORT_BASE + NODE_ID))"
else
  echo "Starting $N nodes on ports $PORT_BASE..$((PORT_BASE + N - 1))"
fi
for ((i=START_NODE; i<=END_NODE; i++)); do
  java -cp "$OUT_DIR" Node "$i" "$PORT_BASE" "$N" > "$LOG_DIR/node_$i.log" 2>&1 &
  echo $! > "$PID_DIR/node_$i.pid"
  echo "  node $i -> port $((PORT_BASE + i)) (pid $!), log: $LOG_DIR/node_$i.log"
done

echo "Done. Tail all logs with: tail -f $LOG_DIR/node_*.log"
echo "Stop everything with: ./kill_all.sh"
