import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * Drivers e ORMs de banco de dados conhecidos — nenhum deve aparecer em
 * dependencies nem devDependencies do feeder. O feeder publica em Kafka;
 * ele nunca deve acessar banco diretamente (fronteira de arquitetura).
 */
const PACOTES_BANIDOS = [
  'pg',
  'pg-promise',
  'mysql',
  'mysql2',
  'mssql',
  'tedious',
  'sqlite3',
  'better-sqlite3',
  'mongodb',
  'mongoose',
  'sequelize',
  'typeorm',
  'prisma',
  '@prisma/client',
  'knex',
  'drizzle-orm',
];

describe('fronteira: feeder não pode declarar dependência de banco de dados', () => {
  it('package.json não declara nenhum driver/ORM de banco conhecido', () => {
    const __dirname = dirname(fileURLToPath(import.meta.url));
    const packageJsonPath = join(__dirname, '..', 'package.json');
    const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf-8')) as {
      dependencies?: Record<string, string>;
      devDependencies?: Record<string, string>;
    };

    const todasDependencias = {
      ...(packageJson.dependencies ?? {}),
      ...(packageJson.devDependencies ?? {}),
    };

    const encontrados = PACOTES_BANIDOS.filter((pacote) => pacote in todasDependencias);

    expect(encontrados).toEqual([]);
  });
});
