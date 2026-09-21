export interface SwapRecord {
  sequencial: string;
  tipoRegistro: string;
  filler: string;
  dataBase: string;
  timestamp: string;
  ticker: string;
  descricao: string;
  diasUteis: number;
  diasCorridos: number;
  taxaRaw: string;
  valor: number;
  flagMercado: "M" | "F";
  prazoFinal: number;
  fonteLayout: string;
}
