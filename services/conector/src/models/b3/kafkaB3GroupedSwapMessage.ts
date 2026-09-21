export interface KafkaSwapVertex {
  diasCorridos: number | null;
  diasUteis: number | null;
  valor: number | null;
  fatorDiario: number | null;
  fatorAcumulado: number | null;
}

export interface KafkaGroupedSwapValues {
  ticker: string;
  refDate: string;
  vertices: KafkaSwapVertex[];
}

export interface KafkaGroupedSwapMessage {
  values: KafkaGroupedSwapValues;
}
