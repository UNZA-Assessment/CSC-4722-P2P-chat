#!/usr/bin/env bash
set -e

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <NODE_ID> <TOTAL_NODES> <PORT_BASE>"
  echo "Example: $0 1 10 8000"
  exit 1
fi

NODE_ID="$1"
TOTAL_NODES="$2"
PORT_BASE="$3"

if [[ -f P2P_PEERS.env ]]; then
  source P2P_PEERS.env
fi

./start_all.sh "$TOTAL_NODES" "$PORT_BASE" "$NODE_ID"
