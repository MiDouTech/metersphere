async (page) => {
  const browser = page.context().browser();
  if (!browser) throw new Error('An isolated browser context is required');
  const context = await browser.newContext();
  try {
    const isolated = await context.newPage();
    await isolated.goto('http://127.0.0.1:15175/#/login/admin');
    await isolated.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
    await isolated.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
    await isolated.getByRole('button', { name: '登录', exact: true }).click();
    await isolated.waitForURL(url => !url.hash.includes('login/admin'));
    const result = await isolated.evaluate(async () => {
      const headers = { 'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '', 'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
        PROJECT: '100001100001', ORGANIZATION: '100001' };
      const history = await (await fetch('/api/functional/case/export/tasks/history?projectId=100001100001&current=1&pageSize=100', { headers })).json();
      const task = history.data?.list?.find(item => item.state === 'SUCCEEDED');
      if (!task) throw new Error('An owned real export is required');
      const ticket = await (await fetch(`/api/functional/case/export/tasks/${task.taskId}/ws-ticket`, { method: 'POST', headers })).json();
      if (!ticket.data?.enabled || !ticket.data?.ticket) throw new Error('Isolated session could not issue ticket');
      const logout = await fetch('/api/signout', { headers });
      if (!logout.ok) throw new Error('Isolated session logout failed');
      const opened = await new Promise((resolve, reject) => {
        const ws = new WebSocket(`ws://127.0.0.1:18086/functional/case/export/ws?taskId=${task.taskId}`,
          [ticket.data.protocol, `ticket.${ticket.data.ticket}`]);
        let accepted = false;
        const timer = setTimeout(() => { ws.close(); reject(new Error('Logout handshake did not terminate')); }, 10000);
        ws.onopen = () => { accepted = true; ws.close(); };
        ws.onclose = () => { clearTimeout(timer); resolve(accepted); };
        ws.onerror = () => { /* onclose is the rejection evidence, without logging credentials. */ };
      });
      if (opened) throw new Error('Logged-out session ticket was accepted by peer');
      return { issuedBeforeLogout: true, peerRejectedAfterLogout: true };
    });
    const originalStillValid = await page.evaluate(async () => {
      const response = await fetch('/api/functional/case/export/tasks/history?projectId=100001100001&current=1&pageSize=10', {
        headers: { 'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '', 'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
          PROJECT: '100001100001', ORGANIZATION: '100001' },
      });
      return response.ok && (await response.json()).code === 100200;
    });
    if (!originalStillValid) throw new Error('Logging out the isolated session invalidated the original session');
    return { ...result, originalSessionStillUsable: true };
  } finally {
    await context.close();
  }
}
