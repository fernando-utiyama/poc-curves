import {
  failed,
  noData,
  published,
  type AcquisitionParams,
  type AcquisitionResult,
  type Feeder,
} from '../../feeder.js';
import type { EnviarMensagem } from '../../kafka-publisher.js';
import { publicarBloco } from '../../kafka-publisher.js';
import { dividirEmBlocos } from '../../blocos.js';
import { TAMANHO_BLOCO_PADRAO } from '../../tamanho-bloco.js';
import { calcularLoteId, calcularEventId } from '../../lote-id.js';
import { verificarConteudoNaoVazio } from '../../integridade.js';
import {
  submeterPedido,
  aguardarArquivoPronto,
  buscarArquivo,
  FalhaSubmissaoError,
  TempoEsperaExcedidoError,
  FalhaBuscaArquivoError,
  type ConfigClienteDataLicense,
} from './cliente-data-license.js';
import { parsearArquivoBloomberg } from './parser.js';
import type { RegistroInstrumentoBruto, PedidoDataLicense } from './tipos.js';

const ENCODING_PADRAO = 'utf-8';

export interface ConfigFeederBloomberg {
  readonly clienteConfig: ConfigClienteDataLicense;
  readonly instrumentos: readonly string[];
  readonly campos: readonly string[];
}

/**
 * Feeder para o fluxo Bloomberg Data License por arquivo — submeter pedido,
 * aguardar geração em lote (pode levar minutos a horas na Bloomberg real),
 * buscar o arquivo pronto, cortar em blocos e publicar. Implementa `Feeder`
 * sem nenhuma mudança de contrato: a espera assíncrona acontece inteira
 * dentro de `acquire()`, que só resolve quando o resultado final é
 * conhecido (nunca um estado "pendente").
 * <p>
 * NÃO VERIFICADO CONTRA A BLOOMBERG REAL — sem credenciais/ambiente
 * disponíveis nesta sessão. Construído e testado só contra fixture
 * simulada (ver feeder-bloomberg.test.ts e fixtures/bloomberg-arquivo-fixture.csv).
 * Quando houver acesso real, o primeiro passo é confirmar o formato real
 * do arquivo de saída e o transporte real (ver cliente-data-license.ts).
 * <p>
 * Restrito ao caminho CLI/agendado (main.ts) — nunca registrado no caminho
 * HTTP síncrono (main-http.ts/azure-function-handler.ts), porque a espera
 * pode levar horas e prenderia uma requisição HTTP que um operador aguarda
 * na tela.
 */
export class FeederBloomberg implements Feeder {
  constructor(
    private readonly enviar: EnviarMensagem,
    private readonly config: ConfigFeederBloomberg,
  ) {}

  async acquire(params: AcquisitionParams): Promise<AcquisitionResult> {
    const pedido: PedidoDataLicense = {
      instrumentos: this.config.instrumentos,
      campos: this.config.campos,
      referenceDate: params.referenceDate,
    };

    let idPedido: string;
    try {
      ({ idPedido } = await submeterPedido(pedido, this.config.clienteConfig));
    } catch (erro) {
      if (erro instanceof FalhaSubmissaoError) {
        return failed('falha ao submeter pedido à Bloomberg', erro.message);
      }
      throw erro;
    }

    let status: 'PRONTO' | 'SEM_DADO';
    try {
      status = await aguardarArquivoPronto(idPedido, this.config.clienteConfig);
    } catch (erro) {
      if (erro instanceof TempoEsperaExcedidoError) {
        return failed(`tempo máximo de espera excedido para o pedido ${idPedido}`, erro.message);
      }
      if (erro instanceof Error) {
        return failed(`Bloomberg reportou erro no pedido ${idPedido}`, erro.message);
      }
      throw erro;
    }

    if (status === 'SEM_DADO') {
      return noData(`Bloomberg não retornou dado para os instrumentos pedidos em ${params.referenceDate}`);
    }

    let conteudo: Buffer;
    try {
      conteudo = await buscarArquivo(idPedido, this.config.clienteConfig);
    } catch (erro) {
      if (erro instanceof FalhaBuscaArquivoError) {
        return failed(`falha ao buscar arquivo pronto do pedido ${idPedido}`, erro.message);
      }
      throw erro;
    }

    const naoVazio = verificarConteudoNaoVazio(conteudo);
    if (!naoVazio.valido) {
      return failed('arquivo Bloomberg retornou conteúdo vazio', naoVazio.motivo);
    }

    let registros: RegistroInstrumentoBruto[];
    try {
      registros = parsearArquivoBloomberg(conteudo, ENCODING_PADRAO);
    } catch (erro) {
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      return failed('estrutura de arquivo Bloomberg inesperada', mensagem);
    }

    if (registros.length === 0) {
      return noData(`arquivo Bloomberg do pedido ${idPedido} não continha nenhum registro`);
    }

    const blocos = dividirEmBlocos(registros, TAMANHO_BLOCO_PADRAO);
    const loteId = calcularLoteId('BLOOMBERG', params.dataset, params.referenceDate, conteudo);
    const producedAt = new Date().toISOString();

    for (const bloco of blocos) {
      await publicarBloco(
        {
          envelope: {
            eventId: calcularEventId(loteId, bloco.sequencia),
            correlationId: params.correlationId,
            source: 'BLOOMBERG',
            dataset: params.dataset,
            referenceDate: params.referenceDate,
            producedAt,
            schemaVersion: '1.0',
            payloadKind: 'INDIVIDUAL_QUOTES',
            loteId,
            sequencia: bloco.sequencia,
            totalBlocos: bloco.totalBlocos,
            payload: {
              idPedido,
              encoding: ENCODING_PADRAO,
              records: bloco.registros.map((registro) => ({ raw: JSON.stringify(registro) })),
            },
          },
          faixa: params.faixa,
        },
        this.enviar,
      );
    }

    return published(loteId, blocos.length);
  }
}
