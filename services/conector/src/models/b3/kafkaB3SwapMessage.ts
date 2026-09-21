export interface KafkaSwapValues {
  ticker: string;
  refDate: string;
  diasCorridos: number;
  diasUteis: number;
  valor: number;
  fatorDiario: number;
  fatorAcumulado: number;
}

export interface KafkaSwapMessage {
  values: KafkaSwapValues;
}
