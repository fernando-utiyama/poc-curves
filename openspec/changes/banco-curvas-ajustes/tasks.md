## 1. Script

- [x] 1.1 Escrever `scripts/alter-banco-curvas.sql` (alterações numa transação, `GRANT` da sequência e volta comentada) e testá-lo em SQL Server 2022 sobre o schema da `V22`, inclusive a volta
- [ ] 1.2 Pedir ao dono do schema a execução do script no banco ainda sem uso, com a confirmação de que nenhum outro sistema depende de `cTickerBberg` com `CHAR(20)` nem de `FK_tDadoCurva_tCurvaData`, e o `GRANT` com o login do gravador Bloomberg; registrar a resposta no design

## 2. Schema de teste

- [ ] 2.1 Ajustar `db/h2/schema.sql` com as mesmas alterações; verificar que a suíte dos serviços que usam o H2 continua passando

## 3. Verificação

- [ ] 3.1 No banco alterado, verificar os cenários da spec `schema-curvas-mercado`: ticker de 22 caracteres, duas cargas concorrentes com ids distintos, data da curva diária fora dos vértices aceita e curva inexistente recusada
- [ ] 3.2 Rodar `openspec validate banco-curvas-ajustes --strict`; verificar que passa
