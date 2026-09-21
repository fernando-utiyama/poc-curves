import { DefaultAzureCredential } from "@azure/identity";
import { SecretClient } from "@azure/keyvault-secrets";

let client: SecretClient | null = null;

function getVaultUrl(vaultName: string): string {
  return `https://${vaultName}.vault.azure.net`;
}

function getClient(): SecretClient {
  const vaultName = process.env.KEY_VAULT_NAME;

  if (!vaultName) {
    throw new Error(
      "KEY_VAULT_NAME não configurado. Defina a variável de ambiente KEY_VAULT_NAME.",
    );
  }

  if (client && (client as any).__vaultName === vaultName) {
    return client;
  }

  const credential = new DefaultAzureCredential();
  const url = getVaultUrl(vaultName);
  client = new SecretClient(url, credential) as SecretClient & { __vaultName?: string };
  (client as any).__vaultName = vaultName;

  return client;
}

export async function getSecret(name: string): Promise<string | null> {
  if (!name) {
    return null;
  }

  try {
    const sc = getClient();
    const res = await sc.getSecret(name);

    return res.value ?? null;
  } catch (err) {
    console.error("[keyVaultService] Falha ao obter segredo do Key Vault", { name });
    return null;
  }
}

export function clearClientCache(): void {
  client = null;
}
