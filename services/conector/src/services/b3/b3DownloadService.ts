import axios from "axios";
import { readFile } from "fs/promises";
import path from "path";

const DEFAULT_BASE_URL = "https://www.b3.com.br/pesquisapregao/download";
const B3_TIME_ZONE = "America/Sao_Paulo";

export type DownloadedSwapExFile = {
  buffer: Buffer;
  fileName: string;
  date: Date;
};

function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

function getB3DateParts(date: Date): { year: string; month: string; day: string } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: B3_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);

  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((item) => item.type === type)?.value ?? "";

  return {
    year: part("year"),
    month: part("month"),
    day: part("day"),
  };
}

function toB3Date(date: Date): Date {
  const { year, month, day } = getB3DateParts(date);

  return new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), 12));
}

/**
 * Monta o nome do arquivo `.ex_` da B3 a partir de uma data.
 * Formato: `TS{yy}{mm}{dd}.ex_` (ex.: 2026-09-08 -> `TS260908.ex_`).
 */
export function buildSwapExFileName(date: Date = new Date()): string {
  const { year, month, day } = getB3DateParts(date);
  const yy = year.slice(-2);

  return `TS${yy}${month}${day}.ex_`;
}

/**
 * Pasta de destino no Blob (`b3/{yyyyMMdd}`) derivada da data.
 */
export function buildBlobDateFolder(date: Date = new Date()): string {
  const { year, month, day } = getB3DateParts(date);

  return `${year}${month}${day}`;
}

function validateHttpsUrl(value: string): string {
  let parsed: URL;

  try {
    parsed = new URL(value);
  } catch {
    throw new Error("[b3DownloadService] URL de download inválida.");
  }

  if (parsed.protocol !== "https:") {
    throw new Error("[b3DownloadService] URL de download deve usar HTTPS.");
  }

  return parsed.toString();
}

function buildDownloadUrl(baseUrl: string, fileName: string): string {
  let parsed: URL;

  try {
    parsed = new URL(baseUrl);
  } catch {
    throw new Error("[b3DownloadService] URL de download inválida.");
  }

  parsed.searchParams.set("filelist", fileName);
  return validateHttpsUrl(parsed.toString());
}

async function readLocalFile(): Promise<Buffer> {
  const localFile = process.env.B3_SWAP_EX_LOCAL_FILE;

  if (!localFile) {
    throw new Error(
      "[b3DownloadService] Variável de ambiente B3_SWAP_EX_LOCAL_FILE não configurada.",
    );
  }

  const buffer = await readFile(path.resolve(process.cwd(), localFile));

  if (buffer.length === 0) {
    throw new Error("[b3DownloadService] Arquivo local .ex_ está vazio.");
  }

  return buffer;
}

/**
 * Baixa o arquivo `.ex_` (auto-extrator ZIP) diretamente da B3.
 *
 * @param fileName Nome do arquivo `.ex_` (default: derivado da data atual).
 */
export async function downloadSwapExFile(fileName?: string): Promise<Buffer> {
  const resolvedFileName = fileName ?? buildSwapExFileName();

  if (process.env.B3_LOCAL_FILE_ENABLED === "true") {
    return readLocalFile();
  }

  const baseUrl = process.env.B3_SWAP_EX_URL ?? DEFAULT_BASE_URL;
  const url = buildDownloadUrl(baseUrl, resolvedFileName);

  const response = await axios.get<ArrayBuffer>(url, {
    responseType: "arraybuffer",
    timeout: 60000,
  });

  if (response.status !== 200) {
    throw new Error(
      `[b3DownloadService] Falha ao baixar arquivo .ex_ da B3. Status: ${response.status}`,
    );
  }

  const buffer = Buffer.from(response.data);

  if (buffer.length === 0) {
    throw new Error("[b3DownloadService] Arquivo .ex_ retornado pela B3 está vazio.");
  }

  return buffer;
}

/**
 * Baixa o arquivo `.ex_` da data pedida (default: hoje, no fuso da B3).
 * Não há fallback para dias anteriores: se a B3 ainda não publicou o
 * arquivo do dia, o erro é propagado imediatamente.
 */
export async function downloadSwapExFileForDate(
  date: Date = new Date(),
): Promise<DownloadedSwapExFile> {
  const requestedDate = toB3Date(date);
  const fileName = buildSwapExFileName(requestedDate);

  return {
    buffer: await downloadSwapExFile(fileName),
    fileName,
    date: requestedDate,
  };
}
