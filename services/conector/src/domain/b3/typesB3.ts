export type ParsedB3Line = {
  sequencial: string;
  complementoTransacao: string;
  tipoRegistro: string;
  dataBase: string;
  codigoCurva: string;
  codigoTaxa: string;
  rawCurveCode: string;
  curveCode: string;
  descricao: string;
  diasCorridos: number | null;
  diasUteis: number | null;
  valor: number | null;
  sinalTaxa: string | null;
  flagMercado: string | null;
  codigoVertice: number | null;
  linhaOriginal: string;
  headerChunk: string;
};

export type NormalizedCurveType = {
  knownType: boolean;
  ticker: string | null;
  curveType: string | null;
  assetClass: string | null;
  sourceCode: string | null;
};

export type ProcessedB3Record = ParsedB3Line & NormalizedCurveType;

export type LoggerLike = {
  warn?: (...args: any[]) => void;
  info?: (...args: any[]) => void;
  error?: (...args: any[]) => void;
};
