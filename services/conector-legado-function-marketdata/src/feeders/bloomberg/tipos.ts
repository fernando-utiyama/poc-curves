/**
 * Registro genérico de instrumento adquirido via Bloomberg — não amarrado a
 * uma classe de ativo específica (serve juros, câmbio, e outros insumos de
 * curva). classeAtivo/tipoInstrumento/campo são texto livre, não enum
 * fechado, porque os valores reais Bloomberg dependem de qual campo real foi
 * pedido e isso não foi confirmado ainda contra a fonte real (ver design.md
 * da mudança feeder-bloomberg, D-4). `valor` fica como texto — nunca
 * convertido para número nesta camada (conversão numérica com política de
 * arredondamento é trabalho de curve-processor, não do feeder).
 */
export interface RegistroInstrumentoBruto {
  readonly classeAtivo: string;
  readonly tipoInstrumento: string;
  readonly ticker: string;
  readonly campo: string;
  readonly valor: string;
  readonly dataReferencia: string;
}

/** Parâmetros de um pedido de dados Bloomberg Data License. */
export interface PedidoDataLicense {
  readonly instrumentos: readonly string[];
  readonly campos: readonly string[];
  readonly referenceDate: string;
}
