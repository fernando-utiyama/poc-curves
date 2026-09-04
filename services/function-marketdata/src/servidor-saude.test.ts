import type { Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { criarServidorSaude } from './servidor-saude.js';

describe('servidorSaude', () => {
  let server: Server;
  let porta: number;

  beforeEach(async () => {
    server = criarServidorSaude(0);
    await new Promise((resolve) => server.once('listening', resolve));
    const address = server.address() as AddressInfo;
    porta = address.port;
  });

  afterEach(async () => {
    await new Promise<void>((resolve, reject) => {
      server.close((err) => (err ? reject(err) : resolve()));
    });
  });

  it('GET /health retorna status HTTP 200 e corpo JSON { status: "UP" }', async () => {
    const response = await fetch(`http://localhost:${porta}/health`);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body).toEqual({ status: 'UP' });
  });

  it('GET /rota-que-nao-existe retorna status HTTP 404', async () => {
    const response = await fetch(`http://localhost:${porta}/rota-que-nao-existe`);

    expect(response.status).toBe(404);
  });

  it('Content-Type da resposta de /health é application/json', async () => {
    const response = await fetch(`http://localhost:${porta}/health`);

    expect(response.headers.get('content-type')).toContain('application/json');
  });
});
