export type ModoOrigem = 'BOOTSTRAPPED' | 'IMPORTED';
export type OrigemPublicacao = 'CALCULADA' | 'IMPORTADA' | 'CARREGADA';
export type EstadoCurvaCatalogo = 'RASCUNHO' | 'ATIVA' | 'APOSENTADA';
export type EstadoPainel = 'NAO_INICIADA' | 'EM_ANDAMENTO' | 'EM_RISCO' | 'ATRASADA' | 'PUBLICADA' | 'PUBLICADA_COM_AVISO' | 'REPROVADA';
export type MomentoCurva = 'ABERTURA' | 'INTRADIARIO' | 'FECHAMENTO';
export type EstadoVersaoCurva = 'EM_VALIDACAO' | 'PUBLICADA' | 'PUBLICADA_COM_AVISO' | 'SUBSTITUIDA' | 'REPROVADA';
export type EstadoExecucao = 'PENDENTE' | 'EXECUTANDO' | 'CONSTRUINDO' | 'CONCLUIDA' | 'SEM_DADO' | 'FALHOU' | 'EM_RISCO' | 'ATRASADA';
export type SeveridadeDlq = 'NORMAL' | 'ALERTA' | 'CRITICA';
export type EstadoGrupoDlq = 'ABERTO' | 'EM_REPROCESSAMENTO' | 'RESOLVIDO' | 'DESCARTADO' | 'OBSOLETO';
export type TipoModeloCalculo = 'BUILTIN' | 'GROOVY';
export type EstadoModelo = 'ATIVO' | 'DESABILITADO';
export type DatasetB3 = 'PR_DI1' | 'BVBG_086' | 'BVBG_028' | 'TAXAS_REFERENCIA';

export interface ItemPainelDoDiaDTO {
  codigoCurva: string;
  nomeCurva: string;
  modoOrigem: ModoOrigem;
  origemPublicacao?: OrigemPublicacao;
  estado: EstadoPainel;
  horarioLimite: string;
  tempoRestanteOuMargem?: string;
  etapaAtual?: string;
  horarioPrevisto?: string;
  versaoVigente?: number;
  avisosValidacao?: string[];
  testesReprovados?: string[];
}

export interface PainelDoDiaResponse {
  dataReferencia: string;
  itens: ItemPainelDoDiaDTO[];
}

export interface AlertasSumarioResponse {
  gruposPendentesDlq: number;
  totalMensagensDlq: number;
  idadeMaisAntigaMinutos: number;
  severidadeDlq?: SeveridadeDlq;
  curvasEmRisco: number;
  curvasAtrasadas: number;
}

export interface ItemCatalogoDTO {
  codigo: string;
  nome: string;
  moeda: string;
  modoOrigem: ModoOrigem;
  estado: EstadoCurvaCatalogo;
  versaoVigente: number;
  modeloApontado: string;
}

export interface CatalogoResponse {
  itens: ItemCatalogoDTO[];
  totalItens: number;
  pagina: number;
  tamanho: number;
}

export interface LimiteValidacaoDTO {
  testeId: string;
  classificacao: 'BLOQUEANTE' | 'AVISO';
  habilitado: boolean;
  limiteMinimo?: string;
  limiteMaximo?: string;
}

export interface DefinicaoCurvaDTO {
  codigo: string;
  nome: string;
  moeda: string;
  modoOrigem: ModoOrigem;
  estado: EstadoCurvaCatalogo;
  versaoNumero: number;
  contagemDias: 'DU_252' | 'ACT_360' | 'ACT_365';
  calendario: 'B3_ANBIMA' | 'CORRIDO';
  interpolador: 'FLAT_FORWARD' | 'LINEAR' | 'LOG_LINEAR' | 'LOG_CUBIC' | 'NATURAL_CUBIC_SPLINE' | 'MONOTONIC_CONVEX' | 'FLAT_FORWARD_LINEAR';
  politicaExtrapolacao: 'STRICT' | 'FLAT_RATE' | 'FLAT_FORWARD' | 'FLAT_FORWARD_LINEAR';
  politicaArredondamento: 'TRUNCATE_8' | 'TRUNCATE_12' | 'HALF_UP_8' | 'HALF_UP_12';
  modeloApontado?: string;
  horarioLimitePublicacao: string;
  orcamentoPorEtapaMinutos?: Record<string, number>;
  janelaBloqueioMinutos?: number;
  limitesValidacao?: LimiteValidacaoDTO[];
  vinculosFonte?: string[];
  dependencias?: string[];
}

export interface VerticeCurvaDTO {
  prazoDiasUteis: number;
  prazoDiasCorridos?: number;
  dataVencimento?: string;
  taxa: string; // Preservada como string exata
  fatorDesconto?: string; // Preservada como string exata
}

export interface ProcedenciaCurvaDTO {
  publicadoEm: string;
  execucaoId?: string;
  correlationId?: string;
  modeloNome?: string;
  modeloChecksum?: string;
  arquivoOrigem?: string;
  loteIngestaoId?: string;
  carregadoPor?: string;
  justificativa?: string;
}

export interface ExecucaoResumoDTO {
  id: string;
  correlationId?: string;
  estado: string;
  tipoDisparo: string;
  disparadoPor?: string;
  duracaoSegundos?: number;
}

export interface ItemValidacaoDTO {
  testeId: string;
  nomeTeste: string;
  classificacao: 'BLOQUEANTE' | 'AVISO';
  resultado: 'APROVADO' | 'REPROVADO' | 'NAO_APLICAVEL';
  medidaObservada?: string;
  limiteAplicado?: string;
  mensagem?: string;
}

export interface ResultadoValidacaoCurvaDTO {
  statusGeral: 'APROVADA' | 'APROVADA_COM_AVISOS' | 'REPROVADA';
  itens: ItemValidacaoDTO[];
}

export interface CurvaViewerResponse {
  codigoCurva: string;
  nomeCurva: string;
  dataReferencia: string;
  momento: MomentoCurva;
  versao: number;
  isVersaoCorrente?: boolean;
  estadoVersao: EstadoVersaoCurva;
  origemPublicacao?: OrigemPublicacao;
  vertices: VerticeCurvaDTO[];
  procedencia?: ProcedenciaCurvaDTO;
  ultimaExecucao?: ExecucaoResumoDTO;
  validacao?: ResultadoValidacaoCurvaDTO;
  secaoDegradada?: Record<string, string>;
}

export interface ItemInterpolacaoDTO {
  prazoDiasUteis: number;
  taxa?: string;
  fatorDesconto?: string;
  status: 'VERTICE_EXATO' | 'INTERPOLADO' | 'EXTRAPOLADO' | 'ERRO_FORA_INTERVALO';
  erroMensagem?: string;
}

export interface InterpolacaoResponse {
  codigoCurva: string;
  versaoUtilizada: number;
  interpoladorUtilizado?: string;
  resultados: ItemInterpolacaoDTO[];
}

export interface InterpolacaoRequest {
  dataReferencia: string;
  versao?: number;
  momento?: MomentoCurva;
  prazosDiasUteis: number[];
  interpoladorAlternativo?: string;
}

export interface DisparoIngestaoRequest {
  dataReferencia: string;
  datasets: DatasetB3[];
}

export interface DisparoIngestaoResponse {
  correlationId: string;
  status: 'INICIADO' | 'JA_EM_ANDAMENTO' | 'NAO_EH_PREGAO';
  mensagem?: string;
  dataReferencia: string;
  execucaoId?: string;
}

export interface BackfillRequest {
  dataInicio: string;
  dataFim: string;
  datasets: DatasetB3[];
  limiteConcorrencia?: number;
}

export interface BackfillStatusResponse {
  id: string;
  totalDias: number;
  concluidos: number;
  semDado: number;
  falhas: number;
  pendentes: number;
  status: 'EM_ANDAMENTO' | 'CONCLUIDO' | 'INTERROMPIDO';
}

export interface ItemExecucaoDTO {
  id: string;
  correlationId: string;
  alvo: string;
  dataReferencia: string;
  momento?: string;
  tipoDisparo: string;
  faixa?: string;
  disparadoPor?: string;
  estado: EstadoExecucao;
  etapaAtual?: string;
  duracaoSegundos?: number;
  tentativa?: number;
  causaFalha?: string;
  disparadoEm: string;
}

export interface ExecucoesResponse {
  itens: ItemExecucaoDTO[];
  totalItens: number;
  pagina: number;
  tamanho: number;
}

export interface GrupoPendenciaDlqDTO {
  grupoId: string;
  motivo: string;
  fonte: string;
  conjuntoDados: string;
  dataReferencia: string;
  totalMensagens: number;
  primeiraFalhaEm: string;
  ultimaFalhaEm: string;
  detalheRepresentativo?: string;
  estado: EstadoGrupoDlq;
}

export interface PendenciasDlqGruposResponse {
  grupos: GrupoPendenciaDlqDTO[];
}

export interface ItemPendenciaDlqDTO {
  id: string;
  idEvento: string;
  correlationId: string;
  topicoOrigem: string;
  particao: number;
  offset: number;
  falhouEm: string;
  motivo: string;
  detalhe?: string;
  tentativas?: number;
}

export interface PendenciasDlqDetalheResponse {
  grupoId: string;
  itens: ItemPendenciaDlqDTO[];
}

export interface ErroLinhaCargaDTO {
  linha: number;
  coluna: string;
  valorEncontrado?: string;
  mensagem: string;
}

export interface CargaManualErrosResponse {
  codigoErro: string;
  mensagem: string;
  errosLinha: ErroLinhaCargaDTO[];
}

export interface CargaManualResponse {
  correlationId: string;
  versaoCurvaId: string;
  numeroVersao: number;
  estadoPublicacao: 'PUBLICADA' | 'REPROVADA';
  testesReprovados?: string[];
}

export interface ItemDiferencaComparacaoDTO {
  prazoDiasUteis: number;
  taxaA?: string;
  taxaB?: string;
  diferencaTaxaBps?: string;
  fatorDescontoA?: string;
  fatorDescontoB?: string;
  status: 'COINCIDENTE' | 'PRESENTE_APENAS_EM_A' | 'PRESENTE_APENAS_EM_B';
}

export interface ComparacaoResponse {
  dataReferencia: string;
  rotuloCurvaA?: string;
  rotuloCurvaB?: string;
  diferencas: ItemDiferencaComparacaoDTO[];
}

export interface ModeloDTO {
  id: string;
  nome: string;
  tipo: TipoModeloCalculo;
  estado: EstadoModelo;
  checksum: string;
  descricao?: string;
  autor?: string;
  criadoEm?: string;
}

export interface ModelosResponse {
  modelos: ModeloDTO[];
}
