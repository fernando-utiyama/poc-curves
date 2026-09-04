import {
  app,
  type HttpHandler,
  type HttpRequest,
  type HttpResponseInit,
  type InvocationContext,
} from '@azure/functions';
import { v4 as uuidv4 } from 'uuid';
import { DATASET_ANBIMA_MERCADO_SECUNDARIO } from './feeders/anbima-mercado-secundario.js';
import type { Faixa } from './feeder.js';
import { criarProdutorKafkaReal, type ProdutorKafkaReal } from './kafka-producer-real.js';
import { DatasetNaoSuportadoError } from './registro-feeders.js';
import { montarRegistroCompleto } from './registro-feeders-completo.js';

/** Ver o mesmo mapeamento e a mesma justificativa em `main.ts`. */
function origemPorDataset(dataset: string): 'B3' | 'ANBIMA' {
  return dataset === DATASET_ANBIMA_MERCADO_SECUNDARIO ? 'ANBIMA' : 'B3';
}

/**
 * Handler de Azure Function (tarefa 5.2) sobre o mesmo núcleo de `main.ts` —
 * mesma montagem de registro de feeders (`montarRegistroCompleto`),
 * nenhuma regra de aquisição própria (docs/extensao-feeders.md). Só troca a
 * forma de receber parâmetros (corpo JSON da requisição HTTP, não variáveis
 * de ambiente) e a forma de responder (`HttpResponseInit`, não código de
 * saída de processo).
 * <p>
 * Cria um novo produtor Kafka por invocação — Azure Functions pode
 * reciclar/escalar instâncias livremente, então não há um lugar seguro para
 * manter uma conexão persistente entre invocações sem o SDK de bindings
 * gerenciar isso (fora do escopo desta versão).
 */

interface CorpoRequisicaoAquisicao {
  readonly dataset?: string;
  readonly referenceDate?: string;
  readonly faixa?: Faixa;
  readonly correlationId?: string;
  readonly kafkaBootstrapServers?: string;
}

/** Fábrica do produtor Kafka — injetável em teste para não depender de um broker real. */
export type FabricaProdutorKafka = (bootstrapServers: string) => Promise<ProdutorKafkaReal>;

/**
 * Constrói o handler HTTP, com a fábrica do produtor Kafka injetável
 * (padrão: `criarProdutorKafkaReal`, o cliente kafkajs real). Retorna o tipo
 * concreto `(request, context) => Promise<HttpResponseInit>` — mais estreito
 * que `HttpHandler` (que aceita `HttpResponseInit | HttpResponse`) — para
 * quem chama (e testa) poder acessar `jsonBody` sem type narrowing; ainda
 * assim estruturalmente compatível com `HttpHandler` para `app.http(...)`.
 */
export function criarAcquireHandler(
  fabricaProdutor: FabricaProdutorKafka = criarProdutorKafkaReal,
): (request: HttpRequest, context: InvocationContext) => Promise<HttpResponseInit> {
  return async (request: HttpRequest, context: InvocationContext): Promise<HttpResponseInit> => {
    let corpo: CorpoRequisicaoAquisicao;
    try {
      corpo = (await request.json()) as CorpoRequisicaoAquisicao;
    } catch {
      return { status: 400, jsonBody: { erro: 'corpo da requisição não é um JSON válido' } };
    }

    const faltando: string[] = [];
    if (!corpo.dataset) faltando.push('dataset');
    if (!corpo.referenceDate) faltando.push('referenceDate');
    if (!corpo.faixa) faltando.push('faixa');
    if (!corpo.kafkaBootstrapServers) faltando.push('kafkaBootstrapServers');
    if (faltando.length > 0) {
      return {
        status: 400,
        jsonBody: { erro: `campos obrigatórios ausentes: ${faltando.join(', ')}` },
      };
    }

    const correlationId = corpo.correlationId ?? uuidv4();
    context.log(
      `iniciando aquisição: dataset=${corpo.dataset} referenceDate=${corpo.referenceDate} correlationId=${correlationId}`,
    );

    const produtor = await fabricaProdutor(corpo.kafkaBootstrapServers as string);
    try {
      const registro = montarRegistroCompleto(produtor.enviar);

      let feeder;
      try {
        feeder = registro.resolver(corpo.dataset as string);
      } catch (erro) {
        if (erro instanceof DatasetNaoSuportadoError) {
          return { status: 400, jsonBody: { erro: erro.message } };
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
      return { status, jsonBody: { correlationId, resultado } };
    } finally {
      await produtor.desconectar();
    }
  };
}

export const acquireHandler: HttpHandler = criarAcquireHandler();

app.http('acquire', {
  methods: ['POST'],
  authLevel: 'function',
  handler: acquireHandler,
});
