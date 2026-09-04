import { v4 as uuidv4 } from 'uuid';
import { DATASET_ANBIMA_MERCADO_SECUNDARIO } from './feeders/anbima-mercado-secundario.js';
import type { Faixa } from './feeder.js';
import { criarProdutorKafkaReal } from './kafka-producer-real.js';
import { DatasetNaoSuportadoError } from './registro-feeders.js';
import { montarRegistroCompleto } from './registro-feeders-completo.js';
import { registrarFeedersBloomberg } from './registro-feeders-bloomberg.js';
import { criarServidorSaude } from './servidor-saude.js';

/**
 * `AcquisitionParams.source` é só metadado de log/rastreio aqui — cada
 * `Feeder` já declara sua própria origem no envelope que publica (ex.:
 * `FeederB3ArquivoPesquisaPregao` sempre usa `'B3'`, nunca confia no que o
 * chamador passou), então um mapeamento simples por nome de dataset é
 * suficiente; não precisa vir do `RegistroFeeders` (que só resolve
 * dataset -> Feeder, não dataset -> fonte).
 */
function origemPorDataset(dataset: string): 'B3' | 'ANBIMA' | 'BLOOMBERG' {
  if (dataset === 'BLOOMBERG_JUROS_CAMBIO') return 'BLOOMBERG';
  return dataset === DATASET_ANBIMA_MERCADO_SECUNDARIO ? 'ANBIMA' : 'B3';
}

/**
 * Adaptador de container (tarefa 5.1): lê parâmetros de ambiente, executa
 * uma aquisição via o núcleo do feeder, loga o resultado estruturado e sai
 * com o código de saída correspondente — 0 para `PUBLISHED`/`NO_DATA`
 * (ambos são término normal: dado publicado, ou fonte respondeu que não há
 * dado ainda), 1 para `FAILED` (falha real, para o orquestrador de
 * agendamento — cron/K8s Job/o que for — perceber e agir).
 * <p>
 * Modo de disparo: "pull agendado" — este processo roda uma aquisição por
 * execução e termina; quem decide QUANDO rodar (agendamento, retentativa em
 * nível de orquestração) é responsabilidade de fora deste adaptador. Ver a
 * pergunta em aberto em docs/extensao-feeders.md ("Modo de entrega") — não
 * presume um modo de streaming/push.
 * <p>
 * O servidor de saúde (`criarServidorSaude`, tarefa 5.4) sobe primeiro e
 * fica de pé durante toda a execução da aquisição, para um orquestrador
 * poder checar liveness enquanto a aquisição (que pode levar segundos a
 * poucos minutos, em datasets reais de centenas de MB) ainda está em
 * andamento; encerra depois que a aquisição termina, antes do processo sair.
 */

interface ParametrosAmbiente {
  readonly dataset: string;
  readonly referenceDate: string;
  readonly faixa: Faixa;
  readonly correlationId: string;
  readonly kafkaBootstrapServers: string;
  readonly healthPort: number;
}

function lerParametrosObrigatorios(): ParametrosAmbiente {
  const dataset = process.env['ACQUISITION_DATASET'];
  const referenceDate = process.env['ACQUISITION_REFERENCE_DATE'];
  const faixa = process.env['ACQUISITION_FAIXA'] as Faixa | undefined;
  const kafkaBootstrapServers = process.env['KAFKA_BOOTSTRAP_SERVERS'];

  const faltando: string[] = [];
  if (!dataset) faltando.push('ACQUISITION_DATASET');
  if (!referenceDate) faltando.push('ACQUISITION_REFERENCE_DATE');
  if (!faixa) faltando.push('ACQUISITION_FAIXA');
  if (!kafkaBootstrapServers) faltando.push('KAFKA_BOOTSTRAP_SERVERS');
  if (faltando.length > 0) {
    throw new Error(`variáveis de ambiente obrigatórias ausentes: ${faltando.join(', ')}`);
  }

  return {
    dataset: dataset as string,
    referenceDate: referenceDate as string,
    faixa: faixa as Faixa,
    correlationId: process.env['ACQUISITION_CORRELATION_ID'] ?? uuidv4(),
    kafkaBootstrapServers: kafkaBootstrapServers as string,
    healthPort: Number(process.env['HEALTH_PORT'] ?? '8090'),
  };
}

async function main(): Promise<number> {
  const params = lerParametrosObrigatorios();
  const servidorSaude = criarServidorSaude(params.healthPort);
  const produtor = await criarProdutorKafkaReal(params.kafkaBootstrapServers);

  try {
    const registro = montarRegistroCompleto(produtor.enviar);
    registrarFeedersBloomberg(registro, produtor.enviar);
    const feeder = registro.resolver(params.dataset);

    const resultado = await feeder.acquire({
      source: origemPorDataset(params.dataset),
      dataset: params.dataset,
      referenceDate: params.referenceDate,
      faixa: params.faixa,
      correlationId: params.correlationId,
    });

    console.log(
      JSON.stringify({
        nivel: 'info',
        mensagem: 'aquisição concluída',
        correlationId: params.correlationId,
        dataset: params.dataset,
        referenceDate: params.referenceDate,
        resultado,
      }),
    );

    return resultado.kind === 'FAILED' ? 1 : 0;
  } finally {
    await produtor.desconectar();
    servidorSaude.close();
  }
}

main()
  .then((codigoSaida) => {
    process.exitCode = codigoSaida;
  })
  .catch((erro) => {
    const mensagem = erro instanceof DatasetNaoSuportadoError ? erro.message : String(erro);
    console.error(
      JSON.stringify({
        nivel: 'error',
        mensagem: 'falha não tratada na aquisição',
        erro: mensagem,
      }),
    );
    process.exitCode = 1;
  });
