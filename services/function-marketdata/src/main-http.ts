import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { v4 as uuidv4 } from 'uuid';
import { DATASET_ANBIMA_MERCADO_SECUNDARIO } from './feeders/anbima-mercado-secundario.js';
import type { Faixa } from './feeder.js';
import { criarProdutorKafkaReal } from './kafka-producer-real.js';
import { DatasetNaoSuportadoError } from './registro-feeders.js';
import { montarRegistroCompleto } from './registro-feeders-completo.js';

/** Ver o mesmo mapeamento e a mesma justificativa em `main.ts`/`azure-function-handler.ts`. */
function origemPorDataset(dataset: string): 'B3' | 'ANBIMA' {
  return dataset === DATASET_ANBIMA_MERCADO_SECUNDARIO ? 'ANBIMA' : 'B3';
}

/**
 * Adaptador HTTP local, sempre de pé — mesmo contrato de requisição/resposta
 * do handler de Azure Function real (`azure-function-handler.ts`: POST
 * /acquire, mesmo corpo JSON, mesmo formato de resposta), servido por um
 * `http.Server` puro do Node em vez do runtime de Azure Functions. Existe
 * porque o adaptador de container (`main.ts`) roda uma aquisição por
 * execução e termina ("pull agendado", docs/extensao-feeders.md) — não dá
 * um alvo estável para o curve-orchestrator chamar. Este adaptador dá esse
 * alvo localmente (stack Podman), sem depender do Azure Functions Core
 * Tools; trocar a URL base por uma Azure Function implantada de verdade não
 * muda o contrato, só o host.
 */

interface CorpoRequisicaoAquisicao {
  readonly dataset?: string;
  readonly referenceDate?: string;
  readonly faixa?: Faixa;
  readonly correlationId?: string;
}

function lerCorpo(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let dados = '';
    req.on('data', (chunk) => {
      dados += chunk;
    });
    req.on('end', () => resolve(dados));
    req.on('error', reject);
  });
}

async function tratarAcquire(req: IncomingMessage, res: ServerResponse, kafkaBootstrapServers: string): Promise<void> {
  let corpo: CorpoRequisicaoAquisicao;
  try {
    const texto = await lerCorpo(req);
    corpo = JSON.parse(texto) as CorpoRequisicaoAquisicao;
  } catch {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ erro: 'corpo da requisição não é um JSON válido' }));
    return;
  }

  const faltando: string[] = [];
  if (!corpo.dataset) faltando.push('dataset');
  if (!corpo.referenceDate) faltando.push('referenceDate');
  if (!corpo.faixa) faltando.push('faixa');
  if (faltando.length > 0) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ erro: `campos obrigatórios ausentes: ${faltando.join(', ')}` }));
    return;
  }

  const correlationId = corpo.correlationId ?? uuidv4();
  const produtor = await criarProdutorKafkaReal(kafkaBootstrapServers);
  try {
    const registro = montarRegistroCompleto(produtor.enviar);

    let feeder;
    try {
      feeder = registro.resolver(corpo.dataset as string);
    } catch (erro) {
      if (erro instanceof DatasetNaoSuportadoError) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ erro: erro.message }));
        return;
      }
      throw erro;
    }

    const resultado = await feeder.acquire({
      source: origemPorDataset(corpo.dataset as string),
      dataset: corpo.dataset as string,
      referenceDate: corpo.referenceDate as string,
      faixa: corpo.faixa as Faixa,
      correlationId,
    });

    const status = resultado.kind === 'FAILED' ? 502 : 200;
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ correlationId, resultado }));
  } finally {
    await produtor.desconectar();
  }
}

function iniciarServidor(porta: number, kafkaBootstrapServers: string) {
  const server = createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'UP' }));
      return;
    }
    if (req.method === 'POST' && req.url === '/acquire') {
      tratarAcquire(req, res, kafkaBootstrapServers).catch((erro) => {
        console.error(
          JSON.stringify({ nivel: 'error', mensagem: 'falha não tratada em /acquire', erro: String(erro) }),
        );
        if (!res.headersSent) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ erro: 'falha interna' }));
        }
      });
      return;
    }
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'NOT_FOUND' }));
  });
  server.listen(porta);
  return server;
}

function main(): void {
  const porta = Number(process.env['PORT'] ?? '8091');
  const kafkaBootstrapServers = process.env['KAFKA_BOOTSTRAP_SERVERS'];
  if (!kafkaBootstrapServers) {
    throw new Error('variável de ambiente obrigatória ausente: KAFKA_BOOTSTRAP_SERVERS');
  }

  iniciarServidor(porta, kafkaBootstrapServers);
  console.log(JSON.stringify({ nivel: 'info', mensagem: `servidor HTTP de aquisição de pé na porta ${porta}` }));
}

main();
