import { describe, expect, it } from 'vitest';
import { type BlobUploader, montarCaminhoBlob } from './blob-storage.js';

describe('montarCaminhoBlob', () => {
  it('montarCaminhoBlob("2026-09-14", "TaxaSwap.txt") retorna "2026-09-14/TaxaSwap.txt"', () => {
    const caminho = montarCaminhoBlob('2026-09-14', 'TaxaSwap.txt');

    expect(caminho).toBe('2026-09-14/TaxaSwap.txt');
  });
});

describe('BlobUploader (contrato com fake inline)', () => {
  it('registra container, caminho do blob e conteúdo via implementação fake', async () => {
    interface ChamadaGravacao {
      readonly container: string;
      readonly caminhoBlob: string;
      readonly conteudo: Buffer;
    }

    const chamadas: ChamadaGravacao[] = [];

    const fakeUploader: BlobUploader = {
      async gravar(container: string, caminhoBlob: string, conteudo: Buffer): Promise<void> {
        chamadas.push({ container, caminhoBlob, conteudo });
      },
    };

    const conteudoMock = Buffer.from('conteudo de teste');
    await fakeUploader.gravar('b3', '2026-09-14/TaxaSwap.txt', conteudoMock);

    expect(chamadas).toHaveLength(1);
    expect(chamadas[0]).toEqual({
      container: 'b3',
      caminhoBlob: '2026-09-14/TaxaSwap.txt',
      conteudo: conteudoMock,
    });
  });
});
