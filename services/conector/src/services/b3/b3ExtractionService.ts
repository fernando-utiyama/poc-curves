import { inflateRawSync } from "zlib";

/**
 * Arquivos `.ex_` da B3 (endpoint pesquisapregao/download) são executáveis
 * auto-extratores PKSFX (PKWARE) — um PE Windows com um ZIP padrão anexado.
 * Em vez de "virar .exe e rodar", localizamos o ZIP embutido e inflamos a
 * entrada desejada diretamente, sem executar o binário.
 */

const EOCD_SIGNATURE = 0x06054b50; // PK\x05\x06
const ZIP64_EOCD_SIGNATURE = 0x06064b50; // PK\x06\x06
const CENTRAL_DIR_SIGNATURE = 0x02014b50; // PK\x01\x02
const LOCAL_HEADER_SIGNATURE = 0x04034b50; // PK\x03\x04
const COMPRESSION_STORED = 0;
const COMPRESSION_DEFLATE = 8;
const ZIP64_SENTINEL_16 = 0xffff;
const ZIP64_SENTINEL_32 = 0xffffffff;

type ZipEntry = {
  fileName: string;
  compressionMethod: number;
  compressedSize: number;
  uncompressedSize: number;
  localHeaderOffset: number;
};

function ensureRange(
  buffer: Buffer,
  offset: number,
  length: number,
  description: string,
): void {
  if (
    !Number.isSafeInteger(offset) ||
    !Number.isSafeInteger(length) ||
    offset < 0 ||
    length < 0 ||
    offset > buffer.length - length
  ) {
    throw new Error(`[b3ExtractionService] ZIP inválido: ${description} fora dos limites.`);
  }
}

function findEocdOffset(buffer: Buffer): number {
  // O EOCD tem no mínimo 22 bytes; o comentário final pode ter até 65535 bytes.
  const minOffset = Math.max(0, buffer.length - (22 + 0xffff));

  if (buffer.length < 22) {
    throw new Error("[b3ExtractionService] ZIP inválido: EOCD não encontrado no arquivo .ex_.");
  }

  for (let offset = buffer.length - 22; offset >= minOffset; offset -= 1) {
    if (buffer.readUInt32LE(offset) === EOCD_SIGNATURE) {
      return offset;
    }
  }

  throw new Error("[b3ExtractionService] ZIP inválido: EOCD não encontrado no arquivo .ex_.");
}

function readCentralDirectory(buffer: Buffer): ZipEntry[] {
  const eocdOffset = findEocdOffset(buffer);

  ensureRange(buffer, eocdOffset, 22, "registro EOCD");

  const totalEntries = buffer.readUInt16LE(eocdOffset + 10);
  const centralDirSize = buffer.readUInt32LE(eocdOffset + 12);
  const centralDirOffset = buffer.readUInt32LE(eocdOffset + 16);

  if (
    totalEntries === ZIP64_SENTINEL_16 ||
    centralDirSize === ZIP64_SENTINEL_32 ||
    centralDirOffset === ZIP64_SENTINEL_32
  ) {
    throw new Error(
      `[b3ExtractionService] ZIP64 não suportado: registro ${ZIP64_EOCD_SIGNATURE.toString(16)} encontrado.`,
    );
  }

  // Prefixo do auto-extrator antes do ZIP (o PE). Corrige offsets relativos.
  const prefix = eocdOffset - centralDirSize - centralDirOffset;

  ensureRange(buffer, prefix + centralDirOffset, centralDirSize, "diretório central");

  const entries: ZipEntry[] = [];
  let cursor = prefix + centralDirOffset;

  for (let index = 0; index < totalEntries; index += 1) {
    ensureRange(buffer, cursor, 46, "cabeçalho do diretório central");

    const compressionMethod = buffer.readUInt16LE(cursor + 10);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const uncompressedSize = buffer.readUInt32LE(cursor + 24);
    const fileNameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const localHeaderOffset = buffer.readUInt32LE(cursor + 42) + prefix;
    const recordLength = 46 + fileNameLength + extraLength + commentLength;

    ensureRange(buffer, cursor, recordLength, "registro do diretório central");

    if (
      compressedSize === ZIP64_SENTINEL_32 ||
      uncompressedSize === ZIP64_SENTINEL_32 ||
      buffer.readUInt16LE(cursor + 8) === ZIP64_SENTINEL_16
    ) {
      throw new Error("[b3ExtractionService] ZIP64 não suportado para a entrada do arquivo .ex_.");
    }

    const fileName = buffer.toString("utf8", cursor + 46, cursor + 46 + fileNameLength);

    entries.push({
      fileName,
      compressionMethod,
      compressedSize,
      uncompressedSize,
      localHeaderOffset,
    });

    cursor += recordLength;
  }

  return entries;
}

function inflateEntry(buffer: Buffer, entry: ZipEntry): Buffer {
  ensureRange(buffer, entry.localHeaderOffset, 30, "cabeçalho local");

  if (buffer.readUInt32LE(entry.localHeaderOffset) !== LOCAL_HEADER_SIGNATURE) {
    throw new Error("[b3ExtractionService] ZIP inválido: cabeçalho local ausente.");
  }

  const fileNameLength = buffer.readUInt16LE(entry.localHeaderOffset + 26);
  const extraLength = buffer.readUInt16LE(entry.localHeaderOffset + 28);
  const dataStart = entry.localHeaderOffset + 30 + fileNameLength + extraLength;
  ensureRange(buffer, dataStart, entry.compressedSize, "dados comprimidos");
  const compressedData = buffer.subarray(dataStart, dataStart + entry.compressedSize);

  let content: Buffer;

  if (entry.compressionMethod === COMPRESSION_STORED) {
    content = Buffer.from(compressedData);
  } else if (entry.compressionMethod === COMPRESSION_DEFLATE) {
    content = inflateRawSync(compressedData);
  } else {
    throw new Error(
      `[b3ExtractionService] Método de compressão não suportado: ${entry.compressionMethod}.`,
    );
  }

  if (content.length !== entry.uncompressedSize) {
    throw new Error("[b3ExtractionService] ZIP inválido: tamanho extraído divergente.");
  }

  return content;
}

/**
 * Extrai o conteúdo texto do arquivo `.ex_` da B3.
 *
 * @param source Conteúdo binário do arquivo `.ex_` baixado da B3.
 * @param targetFileName Nome opcional da entrada a extrair; por padrão a
 *   primeira entrada `.txt` do ZIP.
 */
export function extractSwapTextFromEx(
  source: Buffer,
  targetFileName?: string,
): string {
  if (!Buffer.isBuffer(source) || source.length === 0) {
    throw new Error("[b3ExtractionService] Arquivo .ex_ vazio ou inválido.");
  }

  const entries = readCentralDirectory(source);

  if (entries.length === 0) {
    throw new Error("[b3ExtractionService] Nenhuma entrada encontrada no ZIP embutido.");
  }

  const entry = targetFileName
    ? entries.find((item) => item.fileName.toLowerCase() === targetFileName.toLowerCase())
    : entries.find((item) => item.fileName.toLowerCase().endsWith(".txt")) ?? entries[0];

  if (!entry) {
    throw new Error(
      `[b3ExtractionService] Entrada '${targetFileName ?? ".txt"}' não encontrada no arquivo .ex_.`,
    );
  }

  const content = inflateEntry(source, entry).toString("latin1");

  if (!content.trim()) {
    throw new Error("[b3ExtractionService] Conteúdo extraído do arquivo .ex_ está vazio.");
  }

  return content;
}
