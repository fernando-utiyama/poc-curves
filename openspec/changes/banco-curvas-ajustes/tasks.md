## 1. Script

- [x] 1.0 Verificar que `tAnbmaCurvaPrimr` aceita a curva zero da ANBIMA (`CurvaZero_25092026.txt`: 4 curvas, 113 linhas gravadas em SQL Server 2022 sobre o `001_SCRIPT_INICIAL.sql`)
- [x] 1.1 Escrever `scripts/alter-banco-curvas.sql` (alterações numa transação e volta comentada) e testá-lo em SQL Server 2022 sobre o schema do `001_SCRIPT_INICIAL.sql`, inclusive a volta
- [x] 1.1b Retestar o script na versão com `DROP TABLE tCurvaData` em SQL Server 2022, inclusive a volta que recria a tabela (2026-09-28: aplicação, ticker de 22 caracteres, volta idêntica ao `001_SCRIPT_INICIAL.sql` e falha no meio sem aplicar nada)
- [x] 1.2 Levar as alterações ao dono do schema (2026-10-01: incorporadas ao `001_SCRIPT_INICIAL.sql` real, `cTickerBberg` `VARCHAR(50)` e sem `tCurvaData`; o `alter-banco-curvas.sql` fica só para um banco criado com a versão antiga)

## 2. Verificação

- [ ] 2.1 No banco alterado, verificar os cenários da spec `schema-curvas-mercado`: ticker de 22 caracteres e `tCurvaData` removida, com `tDadoCurva` e `tDadoVertcCurva` intactas
- [ ] 2.2 Rodar `openspec validate banco-curvas-ajustes --strict`; verificar que passa
