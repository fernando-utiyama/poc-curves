import axios from "axios";
import { readFile } from "fs/promises";
import path from "path";

function validateSwapUrl(value: string): string {
  let parsedUrl: URL;

  try {
    parsedUrl = new URL(value);
  } catch {
    throw new Error("[b3Service] B3_SWAP_URL inválida.");
  }

  if (parsedUrl.protocol === "https:") {
    return parsedUrl.toString();
  }

  throw new Error("[b3Service] B3_SWAP_URL deve usar HTTPS.");
}

export async function fetchSwapFile(): Promise<string> {
  if (process.env.B3_LOCAL_FILE_ENABLED === "true") {
    const localFile = process.env.B3_SWAP_LOCAL_FILE;

    if (!localFile) {
      throw new Error("[b3Service] Variável de ambiente B3_SWAP_LOCAL_FILE não configurada.");
    }

    const content = await readFile(path.resolve(process.cwd(), localFile), "utf8");

    if (!content.trim()) {
      throw new Error("[b3Service] Arquivo local B3 está vazio.");
    }

    return content;
  }

  const configuredUrl = process.env.B3_SWAP_URL;

  if (!configuredUrl) {
    throw new Error("[b3Service] Variável de ambiente B3_SWAP_URL não configurada.");
  }

  const url = validateSwapUrl(configuredUrl);

  const response = await axios.get<string>(url, {
    responseType: "text",
    timeout: 30000,
    headers: {
      Accept: "text/plain",
    },
  });

  if (response.status !== 200) {
    throw new Error(`[b3Service] Falha ao buscar arquivo B3. Status: ${response.status}`);
  }

  if (!response.data || !response.data.trim()) {
    throw new Error("[b3Service] Arquivo retornado pela B3 está vazio.");
  }

  return response.data;
}
