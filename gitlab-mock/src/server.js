import { createServer } from './app.js';

const port = Number(process.env.PORT ?? 8081);
const expectedToken = process.env.MOCK_EXPECTED_TOKEN ?? 'fake-token';

const server = createServer({ expectedToken });
server.listen(port, '0.0.0.0', () => {
  // Parsed by the backend test resource manager when started with PORT=0
  console.log(`gitlab-mock listening on ${server.address().port}`);
});
