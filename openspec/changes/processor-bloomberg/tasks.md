> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e a spec; não releia):
> 1. Antes de cada tarefa, leia **só** a seção do guia ([`implementacao.md`](implementacao.md)) que ela cita. Abra só os arquivos que ela manda criar ou alterar.
> 2. A base comum da `processor-v0` (seção 1 dela) já existe, com o `BloombergDataLicenseProvedor` provisório. Não crie segunda versão do que existe.
> 3. Não pare para perguntar: o guia já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 4. Cada tarefa termina com o "Pronto quando" dela (compilar ou o teste passar).

## 1. Dado real antes de codar

- [ ] 1.1 [forte] (guia §2) Pedido de teste ao Data License com os 20 tickers e os campos `PX_MID` e `PX_LAST`; registrar no `design.md` o host, o catálogo, os endpoints, o formato da resposta (amostra de 3 linhas, sem segredo) e se o `PX_MID` vem preenchido para os 20. Pronto quando os `[A CONFIRMAR]` do guia estão preenchidos ou marcados `TODO(revisao)`.
- [ ] 1.2 [forte] Confirmar a decisão do upload (Open Questions do `design.md`: exigir os 20 também no upload, como na spec). Pronto quando o `design.md` diz "confirmado" ou a spec é ajustada.

## 2. Provedor Bloomberg

- [ ] 2.1 [básico] (guia §3) `adapter/out/client/bloomberg/BloombergDataLicenseClient`: token com a credencial do Key Vault, identificador determinístico por faixa, criar e consultar o pedido, espera de até `espera`; 401 e 403 viram 502 com mensagem sobre a credencial. Pronto quando `mvn -q compile` passa.
- [ ] 2.2 [básico] (guia §4) `application/model/leiaute/LeiauteRespostaDataLicense` (Java puro) e `LeiauteRespostaDataLicenseTest` com os 4 casos do guia. Pronto quando o teste passa.
- [ ] 2.3 [forte] (guia §4 e §5) `BloombergDataLicenseProvedor` (`baixar`, `preparar`, `dataBase`, `interpretar`) e `BbergCurvaPrimrInsercao` (`INSERT` sem `cIdtfdUnic`), no lugar do provisório. Pronto quando `mvn -q compile` passa e a rota deixa de responder 501.
- [ ] 2.4 [básico] (guia §6) `TickersSofrReserva` com os 20 tickers e o `TODO(retirar)`, usada só em `CargaController.parametros`, com o log `TICKERS_RESERVA`; acrescentar ao `CargaRotasTest` a chamada sem `tickers`. Pronto quando o teste passa.
- [ ] 2.5 [básico] (guia §7) Bloco `processor.bloomberg` no `application.yml` (sem segredo), ligado ao `@ConfigurationProperties`. Pronto quando a aplicação sobe com o perfil local.

## 3. Testes

- [ ] 3.1 [forte] (guia §8) `BloombergCargaTest` com o Data License falso: pedido novo, pedido existente na mesma faixa, resposta atrasada (503), ticker sem valor (503 e 422), credencial recusada (502) e `TICKERS_RESERVA`. Pronto quando passa.

## 4. Fechamento

- [ ] 4.1 [forte] `mvn -q test` na pasta do processor; resumo no fim deste arquivo: o que foi feito, o que ficou como `TODO(revisao)` e o que depende da conta do Data License.
