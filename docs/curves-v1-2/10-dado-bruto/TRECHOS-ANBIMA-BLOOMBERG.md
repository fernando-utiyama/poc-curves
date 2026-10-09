# ANBIMA e Bloomberg: a mesma listagem da B3 (suposto, sem ver os adaptadores)

Pacotes como `br.com.poc`. Os nomes abaixo seguem o padrão da B3; se algum for diferente no seu código (porta, record, projection), ajuste com Alt+Enter. A tabela e as colunas vêm do script do banco.

Usar o `AgregadoPrimr.java` desta pasta (já descrito no `TRECHOS.md`).

## 1. Repositório JPA

- **ANBIMA:** copiar o `AnbmaCurvaPrimrRepository.java` desta pasta por cima do seu (escrito a partir da sua foto). Muda: `ORDER BY` com desempate pelo nome, `listarUltimaData` nova e os parâmetros dos derivados com os nomes `nomeCurva` e `dataBase` (só nomes, a ligação é por posição). Os nomes dos métodos derivados são os que você já tem.
- **Bloomberg:** copiar o `BbergCurvaPrimrRepository.java` desta pasta (mesmas mudanças). Mantido o `reserveNextIdentifier()` (a B3 e a ANBIMA usam `proximoId`) e o derivado `...OrderByDataVctoContrAscIdtfdUnicAsc`.

## 2. Porta, record, DTO e adaptador

| Arquivo | Muda |
|---|---|
| `AnbmaCurvaPrimrRepositoryPort` / `BbergCurvaPrimrRepositoryPort` | acrescentar `List<...Resumo> listarUltimaData(String codigo, String nome);` |
| `AnbmaCurvaPrimrResumo` / `BbergCurvaPrimrResumo` e os `...ResumoResponse` | `codigosNaFonte` → `tickersProvedor` |
| adaptadores | `listarAgregado` e `listarUltimaData` como no `TRECHOS.md` da B3, com `AgregadoPrimr.tickersPorCurva(curvaPrvdrRepositoryPort, "ANBIMA", "MS")` ou `("BLOOMBERG", "BLC2")` e `AnbmaCurvaPrimrResumo::new` ou `BbergCurvaPrimrResumo::new` |
| services | `listarAgregado`: sem `de` e sem `ate`, chama `listarUltimaData`; o resto como no `TRECHOS.md` da B3 |

## 3. O que isto NÃO resolve

O `salvar`, o `toDomain` e as conversões de entidade para domínio mudam de campo em campo entre as fontes. Se o Sonar apontar duplicação nelas, a saída é outra (MapStruct, que o projeto já usa, ou deixar), e preciso ver os adaptadores.
