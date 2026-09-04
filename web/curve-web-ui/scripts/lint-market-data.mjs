import { readFileSync, readdirSync, statSync } from 'fs';
import { join, resolve } from 'path';

const srcDir = resolve('src');

function getAllFiles(dir, fileList = []) {
  const files = readdirSync(dir);
  for (const file of files) {
    const filePath = join(dir, file);
    if (statSync(filePath).isDirectory()) {
      getAllFiles(filePath, fileList);
    } else if (filePath.endsWith('.ts') && !filePath.endsWith('.spec.ts') && !filePath.endsWith('.test.ts')) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

const forbiddenPatterns = [
  {
    regex: /parseFloat\s*\(\s*(?:item|vertice|resultado|dto|ponto|curva|dado)?\.?(?:taxa|fatorDesconto|valor|cotacao|taxaA|taxaB)/i,
    description: 'Uso proibido de parseFloat em campo de valor de mercado (taxa/fatorDesconto/valor/cotacao)'
  },
  {
    regex: /Number\s*\(\s*(?:item|vertice|resultado|dto|ponto|curva|dado)?\.?(?:taxa|fatorDesconto|valor|cotacao|taxaA|taxaB)/i,
    description: 'Uso proibido de Number() em campo de valor de mercado'
  },
  {
    regex: /\+\s*(?:item|vertice|resultado|dto|ponto|curva|dado)\.(?:taxa|fatorDesconto|valor|cotacao|taxaA|taxaB)/i,
    description: 'Uso proibido de unário + em campo de valor de mercado'
  }
];

const files = getAllFiles(srcDir);
let errorsCount = 0;

for (const file of files) {
  const content = readFileSync(file, 'utf-8');
  const lines = content.split('\n');

  lines.forEach((line, index) => {
    // Ignora comentários
    const trimmed = line.trim();
    if (trimmed.startsWith('//') || trimmed.startsWith('/*') || trimmed.startsWith('*')) {
      return;
    }

    for (const pattern of forbiddenPatterns) {
      if (pattern.regex.test(line)) {
        console.error(`[LINT ERRO] ${file}:${index + 1}: ${pattern.description}`);
        console.error(`   > ${line.trim()}`);
        errorsCount++;
      }
    }
  });
}

if (errorsCount > 0) {
  console.error(`\nFalha de lint: ${errorsCount} violações de aritmética/conversão numérica de valor de mercado encontradas.`);
  process.exit(1);
} else {
  console.log(`Lint de valor de mercado concluído com sucesso (${files.length} arquivos analisados).`);
  process.exit(0);
}
