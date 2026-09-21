import { createServer, type Server } from 'node:http';

/**
 * Servidor HTTP mínimo expondo GET /health, para o healthcheck do compose
 * Podman. Sem framework — o feeder não tem (e não precisa ganhar) um
 * servidor web completo só para isto.
 */
export function criarServidorSaude(porta: number): Server {
  const server = createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'UP' }));
      return;
    }
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'NOT_FOUND' }));
  });
  server.listen(porta);
  return server;
}
