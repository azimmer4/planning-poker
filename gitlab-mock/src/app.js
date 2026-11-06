import http from 'node:http';
import { readFileSync } from 'node:fs';

const seedPath = new URL('../seed/issues.json', import.meta.url);

const loadSeed = () => {
  const raw = JSON.parse(readFileSync(seedPath, 'utf8'));
  const data = {};
  for (const [projectId, issues] of Object.entries(raw)) {
    data[projectId] = {};
    for (const issue of issues) {
      data[projectId][issue.iid] = {
        ...issue,
        web_url: `http://gitlab-mock/projects/${projectId}/-/issues/${issue.iid}`,
        notes: [],
      };
    }
  }
  return data;
};

const publicIssue = ({ notes, ...issue }) => issue;

export function createServer({ expectedToken = 'fake-token' } = {}) {
  let data = loadSeed();
  let failNext = false;

  const send = (res, status, body, headers = {}) => {
    res.writeHead(status, { 'Content-Type': 'application/json', ...headers });
    res.end(body === undefined ? '' : JSON.stringify(body));
  };

  const readJson = (req) =>
    new Promise((resolve) => {
      let raw = '';
      req.on('data', (c) => (raw += c));
      req.on('end', () => {
        try {
          resolve(raw ? JSON.parse(raw) : {});
        } catch {
          resolve({});
        }
      });
    });

  const splitLabels = (value) =>
    String(value ?? '')
      .split(',')
      .map((l) => l.trim())
      .filter(Boolean);

  return http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const path = url.pathname;

    if (path.startsWith('/__test/')) {
      if (path === '/__test/state' && req.method === 'GET') return send(res, 200, data);
      if (path === '/__test/reset' && req.method === 'POST') {
        data = loadSeed();
        failNext = false;
        return send(res, 204);
      }
      if (path === '/__test/fail-next' && req.method === 'POST') {
        failNext = true;
        return send(res, 204);
      }
      return send(res, 404, { message: '404 Not Found' });
    }

    if (!path.startsWith('/api/v4/')) return send(res, 404, { message: '404 Not Found' });

    if (req.headers['private-token'] !== expectedToken) {
      return send(res, 401, { message: '401 Unauthorized' });
    }

    const match = path.match(/^\/api\/v4\/projects\/([^/]+)\/issues(?:\/(\d+))?(\/notes)?$/);
    if (!match) return send(res, 404, { message: '404 Not Found' });

    const projectId = decodeURIComponent(match[1]);
    const iid = match[2];
    const isNotes = Boolean(match[3]);
    const project = data[projectId];
    if (!project) return send(res, 404, { message: '404 Project Not Found' });

    const isWrite = req.method === 'PUT' || req.method === 'POST';
    if (isWrite && failNext) {
      failNext = false;
      return send(res, 500, { message: '500 Internal Server Error' });
    }

    if (!iid) {
      if (req.method !== 'GET') return send(res, 405, { message: '405 Method Not Allowed' });
      const state = url.searchParams.get('state');
      const perPage = Math.min(Number(url.searchParams.get('per_page')) || 20, 100);
      const page = Number(url.searchParams.get('page')) || 1;
      const all = Object.values(project).filter((i) => !state || state === 'all' || i.state === state);
      const slice = all.slice((page - 1) * perPage, page * perPage);
      const hasNext = page * perPage < all.length;
      return send(res, 200, slice.map(publicIssue), {
        'X-Next-Page': hasNext ? String(page + 1) : '',
        'X-Page': String(page),
        'X-Per-Page': String(perPage),
        'X-Total': String(all.length),
      });
    }

    const issue = project[iid];
    if (!issue) return send(res, 404, { message: '404 Not found' });

    if (isNotes) {
      if (req.method !== 'POST') return send(res, 405, { message: '405 Method Not Allowed' });
      const body = await readJson(req);
      if (!body.body) return send(res, 400, { error: 'body is missing' });
      const note = { id: issue.notes.length + 1, body: body.body, created_at: new Date().toISOString() };
      issue.notes.push(note);
      return send(res, 201, note);
    }

    if (req.method === 'GET') return send(res, 200, publicIssue(issue));

    if (req.method === 'PUT') {
      const body = await readJson(req);
      const add = splitLabels(body.add_labels ?? url.searchParams.get('add_labels'));
      const remove = splitLabels(body.remove_labels ?? url.searchParams.get('remove_labels'));
      issue.labels = [...issue.labels.filter((l) => !remove.includes(l)), ...add.filter((l) => !issue.labels.includes(l))];
      return send(res, 200, publicIssue(issue));
    }

    return send(res, 405, { message: '405 Method Not Allowed' });
  });
}
