import { describe, expect, it } from 'vitest';
import { urlDownloadB3 } from './nome-arquivo-b3.js';

describe('urlDownloadB3', () => {
  it('monta a URL real do endpoint pesquisapregao para o prefixo PR', () => {
    expect(urlDownloadB3('PR', '2026-08-21')).toBe(
      'https://www.b3.com.br/pesquisapregao/download?filelist=PR260821.zip',
    );
  });

  it('monta a URL real do endpoint pesquisapregao para o prefixo IN', () => {
    expect(urlDownloadB3('IN', '2026-08-21')).toBe(
      'https://www.b3.com.br/pesquisapregao/download?filelist=IN260821.zip',
    );
  });
});
