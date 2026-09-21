#!/usr/bin/env bash
# Owned by Pair A. Stops all nodes started by start_all.sh.
PID_DIR=".pids"

if [ ! -d "$PID_DIR" ]; then
  echo "No .pids directory found - nothing to stop."
  exit 0
fi

for pidfile in "$PID_DIR"/*.pid; do
  [ -e "$pidfile" ] || continue
  PID=$(cat "$pidfile")
  if kill -0 "$PID" 2>/dev/null; then
    echo "Stopping PID $PID ($(basename "$pidfile"))"
    kill "$PID"
  fi
  rm -f "$pidfile"
done

echo "All nodes stopped."
