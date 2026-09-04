// Copia contracts/events (fonte de verdade do contrato, na raiz do monorepo)
// para uma pasta local ao serviço, na mesma profundidade relativa que o
// Containerfile usa dentro da imagem (sibling de src/dist). Evita duplicar o
// schema à mão — cópia de build, nunca editada, sempre regerada a partir da
// fonte real. Mesma motivação do resource copy em services/common/pom.xml
// para o lado Java.
import { cpSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const origem = join(__dirname, '..', '..', '..', 'contracts', 'events');
const destino = join(__dirname, '..', 'contracts', 'events');

mkdirSync(destino, { recursive: true });
cpSync(origem, destino, { recursive: true });
console.log(`contracts/events sincronizado de ${origem} para ${destino}`);
