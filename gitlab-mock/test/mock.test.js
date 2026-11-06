import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../src/app.js';

const TOKEN = 'fake-token';
let server;
let base;

before(async () => {
  server = createServer({ expectedToken: TOKEN });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => new Promise((resolve) => server.close(resolve)));

beforeEach(async () => {
  await fetch(`${base}/__test/reset`, { method: 'POST' });
});

const api = (path, { method = 'GET', body, token = TOKEN } = {}) =>
  fetch(`${base}/api/v4${path}`, {
    method,
    headers: {
      ...(token ? { 'PRIVATE-TOKEN': token } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

const state = async () => (await fetch(`${base}/__test/state`)).json();

test('rejects requests without token', async () => {
  const res = await api('/projects/1/issues', { token: null });
  assert.equal(res.status, 401);
});

test('rejects requests with wrong token', async () => {
  const res = await api('/projects/1/issues', { token: 'nope' });
  assert.equal(res.status, 401);
});

test('lists only opened issues of a project', async () => {
  const res = await api('/projects/1/issues?state=opened');
  assert.equal(res.status, 200);
  const issues = await res.json();
  assert.ok(issues.length > 0);
  assert.ok(issues.every((i) => i.state === 'opened'));
  for (const key of ['iid', 'title', 'description', 'web_url', 'labels']) {
    assert.ok(key in issues[0], `missing ${key}`);
  }
});

test('paginates and sets X-Next-Page header', async () => {
  const first = await api('/projects/1/issues?state=opened&per_page=2&page=1');
  assert.equal((await first.json()).length, 2);
  assert.equal(first.headers.get('x-next-page'), '2');

  const last = await api('/projects/1/issues?state=opened&per_page=100&page=1');
  assert.equal(last.headers.get('x-next-page'), '');
});

test('returns 404 for unknown project', async () => {
  const res = await api('/projects/999/issues');
  assert.equal(res.status, 404);
});

test('returns a single issue by iid', async () => {
  const res = await api('/projects/1/issues/1');
  assert.equal(res.status, 200);
  assert.equal((await res.json()).iid, 1);
});

test('returns 404 for unknown issue', async () => {
  const res = await api('/projects/1/issues/9999');
  assert.equal(res.status, 404);
});

test('PUT adds and removes labels', async () => {
  const before = (await (await api('/projects/1/issues/1')).json()).labels;
  assert.ok(before.includes('SP::3'), 'seed issue 1 must carry SP::3');

  const res = await api('/projects/1/issues/1', {
    method: 'PUT',
    body: { add_labels: 'SP::5', remove_labels: 'SP::3' },
  });
  assert.equal(res.status, 200);
  const labels = (await res.json()).labels;
  assert.ok(labels.includes('SP::5'));
  assert.ok(!labels.includes('SP::3'));

  const s = await state();
  assert.ok(s['1']['1'].labels.includes('SP::5'));
});

test('POST notes stores the comment', async () => {
  const res = await api('/projects/1/issues/2/notes', { method: 'POST', body: { body: 'Ergebnis: 5' } });
  assert.equal(res.status, 201);
  assert.equal((await res.json()).body, 'Ergebnis: 5');

  const s = await state();
  assert.deepEqual(s['1']['2'].notes.map((n) => n.body), ['Ergebnis: 5']);
});

test('POST notes requires a body', async () => {
  const res = await api('/projects/1/issues/2/notes', { method: 'POST', body: {} });
  assert.equal(res.status, 400);
});

test('fail-next makes only the next write fail with 500', async () => {
  await fetch(`${base}/__test/fail-next`, { method: 'POST' });
  const failed = await api('/projects/1/issues/2/notes', { method: 'POST', body: { body: 'x' } });
  assert.equal(failed.status, 500);
  const ok = await api('/projects/1/issues/2/notes', { method: 'POST', body: { body: 'x' } });
  assert.equal(ok.status, 201);
});

test('reset restores seed data', async () => {
  await api('/projects/1/issues/2/notes', { method: 'POST', body: { body: 'x' } });
  await fetch(`${base}/__test/reset`, { method: 'POST' });
  const s = await state();
  assert.deepEqual(s['1']['2'].notes, []);
});
