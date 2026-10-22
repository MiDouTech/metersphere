async (page) => {
  const upgradeHeaders = { Upgrade: 'websocket', Connection: 'Upgrade', 'Sec-WebSocket-Version': '13',
    'Sec-WebSocket-Key': 'MDEyMzQ1Njc4OWFiY2RlZg==', Origin: 'http://127.0.0.1:15175' };
  const probeUrl = 'http://127.0.0.1:15175/api/functional/case/export/ws?taskId=anonymous-probe';
  const anonymous = await page.request.get(probeUrl, { headers: upgradeHeaders });
  if (anonymous.status() !== 403) throw new Error(`Anonymous upgrade unexpectedly returned ${anonymous.status()}`);
  const foreignOrigin = await page.request.get(probeUrl, { headers: { ...upgradeHeaders, Origin: 'https://foreign.invalid',
    'Sec-WebSocket-Protocol': `ms-case-export-v1, ticket.${'a'.repeat(43)}` } });
  if (foreignOrigin.status() !== 403) throw new Error(`Foreign Origin upgrade unexpectedly returned ${foreignOrigin.status()}`);
  await page.goto('http://127.0.0.1:15175/#/case-management/featureCase?orgId=100001&pId=100001100001');
  const result = await page.evaluate(async () => {
    const headers = { 'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '', 'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
      PROJECT: '100001100001', ORGANIZATION: '100001' };
    const history = await (await fetch('/api/functional/case/export/tasks/history?projectId=100001100001&current=1&pageSize=100', { headers })).json();
    const task = history.data?.list?.find(item => item.state === 'SUCCEEDED');
    if (!task) throw new Error('Run real export E2E first; an owned successful export is required');
    async function issue() {
      const response = await fetch(`/api/functional/case/export/tasks/${task.taskId}/ws-ticket`, { method: 'POST', headers });
      const body = await response.json();
      if (!response.ok || !body.data?.enabled || !body.data?.ticket) throw new Error(`Ticket issuance failed: ${response.status}/${body.code}`);
      return body.data;
    }
    function connect(ticket, port = 15175, taskId = task.taskId) {
      return new Promise(resolve => {
        const path = port === 15175 ? '/api/functional/case/export/ws' : '/functional/case/export/ws';
        const url = `ws://127.0.0.1:${port}${path}?taskId=${encodeURIComponent(taskId)}`;
        const ws = new WebSocket(url, [ticket.protocol, `ticket.${ticket.ticket}`]);
        const result = { opened: false, matched: false, urlContainsCredential: url.includes(ticket.ticket), code: 0 };
        const timeout = setTimeout(() => { ws.close(); resolve({ ...result, timeout: true }); }, 10000);
        ws.onopen = () => { result.opened = ws.protocol === 'ms-case-export-v1'; };
        ws.onmessage = event => {
          const message = JSON.parse(event.data);
          result.matched = message.type === 'EXPORT_TASK' && message.task?.taskId === task.taskId &&
            message.task.projectId === task.projectId && message.task.state === 'SUCCEEDED';
        };
        ws.onclose = event => { clearTimeout(timeout); result.code = event.code; resolve(result); };
        ws.onerror = () => { /* onclose records the rejected handshake without exposing the ticket. */ };
      });
    }
    const ticket = await issue();
    const first = await connect(ticket);
    if (!first.opened || !first.matched || first.code !== 1000 || first.urlContainsCredential) throw new Error('Owned notification did not match durable task state');
    const replay = await connect(ticket);
    if (replay.opened) throw new Error('Single-use ticket replay was accepted');
    const mismatchTicket = await issue();
    const wrongTask = await connect(mismatchTicket, 15175, 'wrong-task');
    const afterWrongTask = await connect(mismatchTicket);
    if (wrongTask.opened || !afterWrongTask.matched) throw new Error('Task binding or invalid-attempt preservation failed');
    // The peer is a separate app process sharing only the database/Redis/object store.
    const crossReplica = await connect(await issue(), 18086);
    if (!crossReplica.matched || crossReplica.code !== 1000) throw new Error('Ticket issued by first replica failed on peer');
    const sharedTicket = await issue();
    const competitors = await Promise.all([connect(sharedTicket), connect(sharedTicket, 18086)]);
    if (competitors.filter(item => item.opened && item.matched).length !== 1 || competitors.filter(item => item.opened).length !== 1)
      throw new Error('Concurrent cross-replica consumption did not admit exactly one connection');
    const independentTickets = await Promise.all([issue(), issue()]);
    const connections = await Promise.all(independentTickets.map(ticket => connect(ticket)));
    if (connections.some(item => !item.matched || item.code !== 1000)) throw new Error('Independent connections lost notifications');
    return { taskId: task.taskId, first, replayRejected: !replay.opened, wrongTaskRejected: !wrongTask.opened,
      invalidAttemptDidNotConsume: afterWrongTask.matched, crossReplica, concurrentSingleWinner: true, multipleConnections: true };
  });
  return { anonymousHandshakeRejected: true, foreignOriginRejected: true, ...result };
}
