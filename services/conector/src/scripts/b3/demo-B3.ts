import { createHash } from "crypto";
import { mkdir, readFile, writeFile } from "fs/promises";
import path from "path";
import { extractSwapTextFromEx } from "../../services/b3/b3ExtractionService";
import {
  buildBlobDateFolder,
  buildSwapExFileName,
  downloadSwapExFileForDate,
} from "../../services/b3/b3DownloadService";

const DEFAULT_DATE = "2026-09-08";
const DEFAULT_FIXTURE = "test/b3/fixtures/TS260908.ex_";
const B3_BASE_URL = "https://www.b3.com.br/pesquisapregao/download";
const ERROR_SCENARIOS = new Set([
  "invalid-date",
  "missing-fixture",
  "download-failure",
  "corrupt-zip",
  "empty-file",
  "integrity",
]);

function readArgument(name: string): string | undefined {
  const prefix = `--${name}=`;
  return process.argv.slice(2).find((argument) => argument.startsWith(prefix))?.slice(prefix.length);
}

function parseDate(value: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);

  if (!match) {
    throw new Error("Use --date=YYYY-MM-DD.");
  }

  const [, year, month, day] = match;
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), 12));

  if (
    date.getUTCFullYear() !== Number(year) ||
    date.getUTCMonth() !== Number(month) - 1 ||
    date.getUTCDate() !== Number(day)
  ) {
    throw new Error("Data inválida. Use --date=YYYY-MM-DD.");
  }

  return date;
}

function sha256(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

async function main(): Promise<void> {
  const liveDownload = process.argv.includes("--live");
  const scenario = readArgument("scenario");

  if (scenario && !ERROR_SCENARIOS.has(scenario)) {
    throw new Error(
      `Cenário inválido. Use um destes: ${Array.from(ERROR_SCENARIOS).join(", ")}.`,
    );
  }

  const dateValue = scenario === "invalid-date"
    ? "2026-02-30"
    : readArgument("date") ?? DEFAULT_DATE;
  const requestedDate = parseDate(dateValue);
  const fixtureValue = scenario === "missing-fixture"
    ? "test/b3/fixtures/arquivo-inexistente.ex_"
    : readArgument("fixture") ?? DEFAULT_FIXTURE;
  const fixturePath = path.resolve(process.cwd(), fixtureValue);

  let fileName: string;
  let dateFolder: string;
  let source: Buffer;

  if (scenario === "download-failure") {
    throw new Error("Falha de download simulada para QA.");
  }

  if (liveDownload) {
    const downloaded = await downloadSwapExFileForDate(requestedDate);
    fileName = downloaded.fileName;
    dateFolder = buildBlobDateFolder(downloaded.date);
    source = downloaded.buffer;
  } else {
    fileName = buildSwapExFileName(requestedDate);
    dateFolder = buildBlobDateFolder(requestedDate);
    source = await readFile(fixturePath);
  }

  if (scenario === "corrupt-zip") {
    source = Buffer.from("arquivo ZIP corrompido");
  } else if (scenario === "empty-file") {
    source = Buffer.alloc(0);
  }

  const sourceUrl = `${B3_BASE_URL}?filelist=${fileName}`;
  const content = extractSwapTextFromEx(source);
  const contentBuffer = Buffer.from(content, "latin1");

  const blobName = `b3/${dateFolder}/TaxaSwap.txt`;
  const localBlobPath = path.resolve(process.cwd(), ".demo", "blob", "curvas", blobName);
  await mkdir(path.dirname(localBlobPath), { recursive: true });
  await writeFile(localBlobPath, contentBuffer);

  if (scenario === "integrity") {
    await writeFile(localBlobPath, Buffer.from("conteúdo alterado"));
  }

  const savedBuffer = await readFile(localBlobPath);
  const sourceHash = sha256(contentBuffer);
  const savedHash = sha256(savedBuffer);

  if (!contentBuffer.equals(savedBuffer)) {
    throw new Error("Falha de integridade: o arquivo salvo difere do conteúdo extraído.");
  }

  console.log("\n=== DEMO B3 -> BLOB STORAGE ===");
  console.log(`Modo: ${liveDownload ? "download real da B3" : "fixture local (sem rede)"}`);
  console.log(`URL B3: ${sourceUrl}`);
  console.log(`Arquivo recebido: ${fileName} (${source.length} bytes)`);
  console.log(`Entrada extraída: TaxaSwap.txt (${contentBuffer.length} bytes)`);
  console.log(`Container: curvas`);
  console.log(`Blob: ${blobName}`);
  console.log(`Espelho local: ${localBlobPath}`);
  console.log(`SHA-256 extraído: ${sourceHash}`);
  console.log(`SHA-256 salvo:    ${savedHash}`);
  console.log("Integridade: OK (conteúdo extraído = conteúdo salvo)");
}

main().catch((error) => {
  console.error("\nDEMO B3 FALHOU");
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
