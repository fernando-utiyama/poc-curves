/**
 * Classificação de uma resposta HTTP da fonte de dado, distinguindo "ainda
 * não publicado" de "fonte indisponível". A regra por código de status é
 * uma interpretação genérica e PROVISÓRIA (404 = ainda não publicado, 5xx =
 * fonte com problema) — o comportamento real do endpoint da B3 ainda não foi
 * verificado contra fixture real (tarefa 6.1 do backlog); revisar esta
 * classificação quando essa fixture existir.
 */
export type ResultadoConsultaFonte =
  | { readonly tipo: 'DISPONIVEL' }
  | { readonly tipo: 'NOT_YET_PUBLISHED' }
  | { readonly tipo: 'FONTE_INDISPONIVEL'; readonly detalhe: string };

/**
 * Classifica uma resposta HTTP já recebida da fonte. Não faz nenhuma
 * chamada de rede — só interpreta o status da resposta.
 */
export function classificarRespostaFonte(response: Response): ResultadoConsultaFonte {
  if (response.status === 200) {
    return { tipo: 'DISPONIVEL' };
  }
  if (response.status === 404) {
    return { tipo: 'NOT_YET_PUBLISHED' };
  }
  if (response.status >= 500) {
    return { tipo: 'FONTE_INDISPONIVEL', detalhe: `status ${response.status}` };
  }
  return { tipo: 'FONTE_INDISPONIVEL', detalhe: `status inesperado ${response.status}` };
}
