#!/usr/bin/env python3
import json
import os
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import urllib.request

ROOT = os.path.dirname(__file__)
HOST = '0.0.0.0'
PORT = 8080
BASE_PORT = 8000
MAX_NODE_ID = 9


def discover_nodes():
    nodes = []
    for node_id in range(MAX_NODE_ID + 1):
        port = BASE_PORT + node_id
        healthy = False
        try:
            req = urllib.request.Request(f'http://localhost:{port}/api/health', method='GET')
            with urllib.request.urlopen(req, timeout=1.2) as response:
                healthy = response.status == 200
        except Exception:
            healthy = False
        nodes.append({
            'id': node_id,
            'port': port,
            'healthy': healthy,
            'enabled': healthy,
        })
    return nodes


def send_json(response, payload, status=200):
    body = json.dumps(payload).encode('utf-8')
    response.send_response(status)
    response.send_header('Content-Type', 'application/json')
    response.send_header('Access-Control-Allow-Origin', '*')
    response.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
    response.send_header('Access-Control-Allow-Headers', 'Content-Type')
    response.send_header('Content-Length', str(len(body)))
    response.end_headers()
    response.wfile.write(body)


def fetch_json(url, timeout=1.2):
    try:
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.loads(response.read().decode('utf-8', 'replace'))
    except Exception:
        return {}


def send_text(response, text, status=200):
    data = text.encode('utf-8')
    response.send_response(status)
    response.send_header('Content-Type', 'text/plain; charset=utf-8')
    response.send_header('Access-Control-Allow-Origin', '*')
    response.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
    response.send_header('Access-Control-Allow-Headers', 'Content-Type')
    response.send_header('Content-Length', str(len(data)))
    response.end_headers()
    response.wfile.write(data)


def proxy_post(target_url, payload):
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(target_url, data=data, headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=2) as response:
            return response.status, response.read().decode('utf-8', 'replace')
    except Exception:
        return 503, '{"error":"unavailable"}'


def summarize_cluster_state():
    all_states = []
    aggregated_scores = {}
    for node_id in range(MAX_NODE_ID + 1):
        port = BASE_PORT + node_id
        state = fetch_json(f'http://localhost:{port}/api/state', timeout=1.0)
        if not state:
            continue
        all_states.append(state)
        for key, value in (state.get('scores') or {}).items():
            # Each node exposes the same token-carried snapshot. Summing all
            # node snapshots would count every score once per live node.
            aggregated_scores[key] = max(aggregated_scores.get(key, 0), int(value))

    if not all_states:
        return {
            'leader': 'Unknown',
            'tokenHolder': 'Unknown',
            'leaderId': None,
            'tokenHolderId': None,
            'scores': {},
        }

    leader_counts = {}
    token_counts = {}
    for state in all_states:
        leader_id = state.get('leaderId')
        token_id = state.get('tokenHolderId')
        if leader_id is not None:
            leader_counts[leader_id] = leader_counts.get(leader_id, 0) + 1
        if token_id not in (None, -1):
            token_counts[token_id] = token_counts.get(token_id, 0) + 1

    leader_id = max(leader_counts.items(), key=lambda item: item[1])[0] if leader_counts else None
    token_id = max(token_counts.items(), key=lambda item: item[1])[0] if token_counts else None

    return {
        'leader': f'Node {leader_id}' if leader_id is not None else 'Unknown',
        'tokenHolder': f'Node {token_id}' if token_id is not None else 'Unknown',
        'leaderId': leader_id,
        'tokenHolderId': token_id,
        'scores': aggregated_scores,
    }


class UIHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)

        if path == '/api/cluster':
            send_json(self, {'nodes': discover_nodes()})
            return

        if path == '/api/cluster-state':
            send_json(self, summarize_cluster_state())
            return

        if path == '/api/health':
            node_id = int(query.get('nodeId', ['0'])[0])
            port = BASE_PORT + node_id
            try:
                req = urllib.request.Request(f'http://localhost:{port}/api/health', method='GET')
                with urllib.request.urlopen(req, timeout=1.5) as response:
                    send_json(self, {'nodeId': node_id, 'port': port, 'healthy': response.status == 200})
                    return
            except Exception:
                send_json(self, {'nodeId': node_id, 'port': port, 'healthy': False}, status=503)
                return

        if path == '/':
            self.path = '/index.html'
        return super().do_GET()

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get('Content-Length', '0'))
        body = self.rfile.read(length) if length else b''

        if path == '/api/chat':
            data = json.loads(body.decode('utf-8')) if body else {}
            node_id = int(data.get('nodeId', 0))
            payload = {
                'sender_id': node_id,
                'text': data.get('text', ''),
                'lamport': int(data.get('lamport', 1)),
                'vector': data.get('vector', [1, 0, 0]),
            }
            status, result = proxy_post(f'http://localhost:{BASE_PORT + node_id}/api/chat', payload)
            send_json(self, {'status': status, 'result': result})
            return

        if path == '/api/election':
            data = json.loads(body.decode('utf-8')) if body else {}
            node_id = int(data.get('nodeId', 0))
            msg_type = data.get('type', 'ELECTION')
            payload = {'type': msg_type, 'sender_id': node_id}
            status, result = proxy_post(f'http://localhost:{BASE_PORT + node_id}/api/election', payload)
            send_json(self, {'status': status, 'result': result})
            return

        if path == '/api/token':
            data = json.loads(body.decode('utf-8')) if body else {}
            node_id = int(data.get('nodeId', 0))
            payload = {
                'token_holder': data.get('token_holder', node_id),
                'scores': data.get('scores', {}),
            }
            status, result = proxy_post(f'http://localhost:{BASE_PORT + node_id}/api/token', payload)
            send_json(self, {'status': status, 'result': result})
            return

        send_text(self, 'Not Found', 404)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def log_message(self, format, *args):
        return


if __name__ == '__main__':
    os.chdir(ROOT)
    server = ThreadingHTTPServer((HOST, PORT), UIHandler)
    print(f'Serving P2P UI on http://localhost:{PORT}')
    server.serve_forever()
