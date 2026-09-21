import { describe, expect, it } from 'vitest';
import { montarRecords } from './registros-payload.js';
import type { Bloco } from './blocos.js';

describe('montarRecords', () => {
  it('decodifica cada registro do bloco em { raw: texto }, na ordem original', () => {
    const bloco: Bloco<Buffer> = {
      sequencia: 1,
      totalBlocos: 1,
      registros: [
        Buffer.from('<BizGrp>a</BizGrp>', 'utf-8'),
        Buffer.from('<BizGrp>b</BizGrp>', 'utf-8'),
      ],
    };

    const records = montarRecords(bloco, 'utf-8');

    expect(records).toEqual([{ raw: '<BizGrp>a</BizGrp>' }, { raw: '<BizGrp>b</BizGrp>' }]);
  });

  it('decodifica corretamente caracteres UTF-8 multi-byte (acentuação real de campo de texto livre)', () => {
    const bloco: Bloco<Buffer> = {
      sequencia: 1,
      totalBlocos: 1,
      registros: [Buffer.from('<Desc>Petróleo Petrobrás S.A.</Desc>', 'utf-8')],
    };

    const records = montarRecords(bloco, 'utf-8');

    expect(records).toEqual([{ raw: '<Desc>Petróleo Petrobrás S.A.</Desc>' }]);
  });

  it('decodifica ISO-8859-1 corretamente, diferente de UTF-8 para os mesmos bytes acentuados', () => {
    const bufferLatin1 = Buffer.from('café', 'latin1');
    const bloco: Bloco<Buffer> = { sequencia: 1, totalBlocos: 1, registros: [bufferLatin1] };

    const records = montarRecords(bloco, 'ISO-8859-1');

    expect(records).toEqual([{ raw: 'café' }]);
  });

  it('lança erro quando o conteúdo não é válido no encoding declarado', () => {
    const bytesInvalidosEmUtf8 = Buffer.from([0xff, 0xfe, 0x00, 0x01]);
    const bloco: Bloco<Buffer> = {
      sequencia: 1,
      totalBlocos: 1,
      registros: [bytesInvalidosEmUtf8],
    };

    expect(() => montarRecords(bloco, 'utf-8')).toThrow();
  });
});
