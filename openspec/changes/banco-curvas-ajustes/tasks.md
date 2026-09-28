## 1. Script

- [x] 1.0 Verificar que `tAnbmaCurvaPrimr` aceita a curva zero da ANBIMA (`CurvaZero_25092026.txt`: 4 curvas, 113 linhas gravadas em SQL Server 2022 sobre o `001_SCRIPT_INICIAL.sql`)
- [x] 1.1 Escrever `scripts/alter-banco-curvas.sql` (alterações numa transação e volta comentada) e testá-lo em SQL Server 2022 sobre o schema do `001_SCRIPT_INICIAL.sql`, inclusive a volta
- [ ] 1.1b Retestar o script na versão com `DROP TABLE tCurvaData` em SQL Server 2022, inclusive a volta que recria a tabela
- [ ] 1.2 Pedir ao dono do schema a execução do script no banco ainda sem uso, com a confirmação de que nenhum outro sistema depende de `cTickerBberg` com `CHAR(20)` nem de `tCurvaData`; registrar a resposta no design

## 2. Schema de teste

- [ ] 2.1 Ajustar `db/h2/schema.sql` com as mesmas alterações; verificar que a suíte dos serviços que usam o H2 continua passando

## 3. Verificação

- [ ] 3.1 No banco alterado, verificar os cenários da spec `schema-curvas-mercado`: ticker de 22 caracteres e `tCurvaData` removida, com `tDadoCurva` e `tDadoVertcCurva` intactas
- [ ] 3.2 Rodar `openspec validate banco-curvas-ajustes --strict`; verificar que passa
