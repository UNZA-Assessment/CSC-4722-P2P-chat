const state = {
  selectedNode: null,
  nodes: [],
  messages: [],
  scores: {},
  leader: 'Unknown',
  tokenHolder: 'Unknown',
  leaderId: null,
  tokenHolderId: null,
  lastAction: 'Idle',
};

const nodeSelect = document.getElementById('nodeSelect');
const nodeList = document.getElementById('nodeList');
const chatMessages = document.getElementById('chatMessages');
const scoreboard = document.getElementById('scoreboard');
const clusterStatus = document.getElementById('clusterStatus');
const leaderValue = document.getElementById('leaderValue');
const tokenValue = document.getElementById('tokenValue');
const nodeCountValue = document.getElementById('nodeCountValue');
const systemSummary = document.getElementById('systemSummary');
const chatForm = document.getElementById('chatForm');
const messageInput = document.getElementById('messageInput');

function setClusterStatus(healthyCount, total) {
  if (healthyCount === total && total > 0) {
    clusterStatus.textContent = `All ${healthyCount}/${total} nodes healthy`;
    clusterStatus.className = 'status-pill';
  } else if (healthyCount > 0) {
    clusterStatus.textContent = `${healthyCount}/${total} nodes healthy`;
    clusterStatus.className = 'status-pill warning';
  } else {
    clusterStatus.textContent = 'Cluster unavailable';
    clusterStatus.className = 'status-pill error';
  }
}

function renderNodeOptions() {
  nodeSelect.innerHTML = '';

  if (state.nodes.length === 0) {
    const option = document.createElement('option');
    option.value = '';
    option.textContent = 'No live nodes discovered';
    nodeSelect.appendChild(option);
    state.selectedNode = null;
    nodeSelect.value = '';
    return;
  }

  state.nodes.forEach((node) => {
    const option = document.createElement('option');
    option.value = String(node.id);
    option.textContent = `Node ${node.id} · port ${node.port}`;
    nodeSelect.appendChild(option);
  });

  if (!state.nodes.some((node) => String(node.id) === String(state.selectedNode))) {
    state.selectedNode = state.nodes[0]?.id ?? null;
  }

  if (state.selectedNode !== null) {
    nodeSelect.value = String(state.selectedNode);
  }
}

function renderNodeList() {
  nodeList.innerHTML = '';

  state.nodes.forEach((node) => {
    const item = document.createElement('div');
    item.className = 'node-card';
    if (node.id === state.leaderId) {
      item.classList.add('leader-node');
    }
    if (node.id === state.tokenHolderId) {
      item.classList.add('token-node');
    }

    const meta = document.createElement('div');
    meta.className = 'node-meta';
    meta.innerHTML = `<strong>Node ${node.id}</strong><span>port ${node.port}</span>`;

    const controls = document.createElement('div');
    controls.style.display = 'flex';
    controls.style.alignItems = 'center';
    controls.style.gap = '10px';
    controls.style.flexWrap = 'wrap';

    const badge = document.createElement('span');
    badge.className = node.healthy ? 'health-badge' : 'health-badge dead';
    badge.textContent = node.healthy ? 'Healthy' : 'Down';

    const roleRow = document.createElement('div');
    roleRow.style.display = 'flex';
    roleRow.style.gap = '6px';
    roleRow.style.flexWrap = 'wrap';

    if (node.id === state.leaderId) {
      const leaderBadge = document.createElement('span');
      leaderBadge.className = 'health-badge';
      leaderBadge.textContent = 'Leader';
      roleRow.appendChild(leaderBadge);
    }

    if (node.id === state.tokenHolderId) {
      const tokenBadge = document.createElement('span');
      tokenBadge.className = 'health-badge warning';
      tokenBadge.textContent = 'Token';
      roleRow.appendChild(tokenBadge);
    }

    const switchWrap = document.createElement('label');
    switchWrap.className = 'switch';
    const toggle = document.createElement('input');
    toggle.type = 'checkbox';
    toggle.checked = node.enabled !== false;
    toggle.addEventListener('change', (event) => {
      const enabled = event.target.checked;
      node.enabled = enabled;
      state.lastAction = `${enabled ? 'Enabled' : 'Disabled'} node ${node.id}`;
      renderSummary();
    });

    const slider = document.createElement('span');
    slider.className = 'slider';
    switchWrap.append(toggle, slider);

    controls.append(badge, roleRow, switchWrap);
    item.append(meta, controls);
    nodeList.appendChild(item);
  });
}

function renderMessages() {
  chatMessages.innerHTML = '';

  state.messages.forEach((message) => {
    const el = document.createElement('div');
    el.className = `message ${message.mine ? 'mine' : ''}`;
    el.innerHTML = `
      <div class="meta">Node ${message.nodeId} · ${message.when}</div>
      <div>${message.text}</div>
    `;
    chatMessages.appendChild(el);
  });

  chatMessages.scrollTop = chatMessages.scrollHeight;
}

function renderScores() {
  scoreboard.innerHTML = '';
  const entries = Object.entries(state.scores).sort((a, b) => b[1] - a[1]);

  entries.forEach(([name, score]) => {
    const row = document.createElement('div');
    row.className = 'score-row';
    row.innerHTML = `<span>${name}</span><strong>${score}</strong>`;
    scoreboard.appendChild(row);
  });
}

function renderSummary() {
  const healthyNodes = state.nodes.filter((n) => n.healthy).length;
  const enabledNodes = state.nodes.filter((n) => n.enabled !== false).length;
  const summaryEntries = [
    `Healthy nodes: ${healthyNodes}/${state.nodes.length}`,
    `Enabled nodes: ${enabledNodes}`,
    `Selected node: ${state.selectedNode ?? 'None'}`,
    `Leader: ${state.leader}`,
    `Token holder: ${state.tokenHolder}`,
    `Last action: ${state.lastAction}`,
  ];

  systemSummary.innerHTML = summaryEntries
    .map((entry) => `<li>${entry}</li>`)
    .join('');
}

function updateMeta() {
  leaderValue.textContent = state.leader;
  tokenValue.textContent = state.tokenHolder;
  nodeCountValue.textContent = String(state.nodes.length);
  renderSummary();
}

async function refreshSystemState() {
  try {
    const data = await fetchJson('/api/cluster-state');
    if (data && data.leader) {
      state.leader = data.leader;
    }
    if (data && data.tokenHolder) {
      state.tokenHolder = data.tokenHolder;
    }
    if (data && Number.isInteger(data.leaderId)) {
      state.leaderId = data.leaderId;
    } else {
      state.leaderId = null;
    }
    if (data && Number.isInteger(data.tokenHolderId)) {
      state.tokenHolderId = data.tokenHolderId;
    } else {
      state.tokenHolderId = null;
    }
    if (data && data.scores) {
      state.scores = data.scores;
    } else {
      state.scores = {};
    }
  } catch (error) {
    console.error('Failed to refresh system state', error);
    state.leader = 'Unknown';
    state.tokenHolder = 'Unknown';
    state.leaderId = null;
    state.tokenHolderId = null;
    state.scores = {};
  }

  renderScores();
  renderNodeList();
  updateMeta();
}

async function refreshMessages() {
  try {
    const data = await fetchJson('/api/messages');
    state.messages = (data.messages || []).map((message) => ({
      nodeId: message.senderId,
      text: message.text,
      when: 'live',
      mine: false,
    }));
    renderMessages();
  } catch (error) {
    console.error('Failed to refresh messages', error);
  }
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json();
}

function computeLeader() {
  const enabledHealthyNodes = state.nodes.filter((node) => node.enabled !== false && node.healthy);
  if (enabledHealthyNodes.length === 0) {
    return 'Unknown';
  }
  return `Node ${enabledHealthyNodes[enabledHealthyNodes.length - 1].id}`;
}

async function refreshCluster() {
  try {
    const data = await fetchJson('/api/cluster');
    state.nodes = (data.nodes || []).map((node) => ({
      ...node,
      enabled: node.enabled !== false,
    }));

    const selectedStillValid = state.nodes.some((node) => String(node.id) === String(state.selectedNode));
    if (!selectedStillValid) {
      state.selectedNode = state.nodes[0]?.id ?? null;
    }

    state.leader = computeLeader();
    const healthyCount = state.nodes.filter((node) => node.healthy).length;
    setClusterStatus(healthyCount, state.nodes.length);
    renderNodeList();
    renderNodeOptions();
    if (state.selectedNode !== null) {
      nodeSelect.value = String(state.selectedNode);
    }
    await refreshSystemState();
    await refreshMessages();
  } catch (error) {
    console.error(error);
    state.nodes = [];
    state.selectedNode = null;
    state.leader = 'Unknown';
    state.tokenHolder = 'Unknown';
    renderNodeList();
    renderNodeOptions();
    setClusterStatus(0, 0);
    updateMeta();
  }
}

async function triggerElection() {
  if (state.selectedNode === null || Number.isNaN(Number(state.selectedNode))) {
    state.lastAction = 'No live node selected';
    updateMeta();
    return;
  }

  const nodeId = Number(state.selectedNode);
  state.lastAction = `Election triggered on node ${nodeId}`;

  try {
    await fetchJson('/api/election', {
      method: 'POST',
      body: JSON.stringify({ nodeId, type: 'ELECTION' }),
    });
    state.leader = `Node ${nodeId}`;
    updateMeta();
  } catch (error) {
    console.error(error);
  }
}

async function requestToken() {
  if (state.selectedNode === null || Number.isNaN(Number(state.selectedNode))) {
    state.lastAction = 'No live node selected';
    updateMeta();
    return;
  }

  const nodeId = Number(state.selectedNode);
  state.lastAction = `Token request on node ${nodeId}`;

  try {
    const payload = {
      nodeId,
      token_holder: nodeId,
      scores: state.scores,
    };
    await fetchJson('/api/token', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    state.tokenHolder = `Node ${nodeId}`;
    updateMeta();
  } catch (error) {
    console.error(error);
  }
}

async function sendMessage(event) {
  event.preventDefault();
  const text = messageInput.value.trim();
  if (!text) return;

  if (state.selectedNode === null || Number.isNaN(Number(state.selectedNode))) {
    state.lastAction = 'No live node selected';
    updateMeta();
    return;
  }

  const nodeId = Number(state.selectedNode);
  const payload = {
    nodeId,
    text,
    lamport: 1,
    vector: [1, 0, 0],
  };

  try {
    await fetchJson('/api/chat', {
      method: 'POST',
      body: JSON.stringify(payload),
    });

    state.messages.push({
      nodeId,
      text,
      when: new Date().toLocaleTimeString(),
      mine: true,
    });

    state.lastAction = `Message sent from node ${nodeId}`;
    messageInput.value = '';
    renderMessages();
    updateMeta();
  } catch (error) {
    console.error(error);
  }
}

nodeSelect.addEventListener('change', (event) => {
  const nextValue = event.target.value;
  state.selectedNode = nextValue === '' ? null : Number(nextValue);
  updateMeta();
});

document.getElementById('healthBtn').addEventListener('click', refreshCluster);
document.getElementById('electionBtn').addEventListener('click', triggerElection);
document.getElementById('tokenBtn').addEventListener('click', requestToken);
chatForm.addEventListener('submit', sendMessage);

state.nodes = [];
renderNodeList();
renderNodeOptions();
renderScores();
renderMessages();
updateMeta();
refreshCluster();
setInterval(refreshCluster, 2500);
