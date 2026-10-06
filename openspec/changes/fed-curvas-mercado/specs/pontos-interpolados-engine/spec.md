## Purpose

No engine, ler os pontos da curva interpolada gravados em `tDadoCurva` (um por dia corrido) para uma curva e data-base, para o front mostrar exatamente o que os consumidores leem do banco.

## ADDED Requirements

### Requirement: Pontos interpolados gravados
O engine SHALL expor `GET /api/v1/curvas/{codigo}/{dataBase}/pontos?de=AAAA-MM-DD&ate=AAAA-MM-DD`, que devolve, a partir de `tDadoCurva` da curva (`cTickerIndcd` = nome) e da data-base (`dBaseReft`), código, nome, data-base, a quantidade total de pontos gravados, o `hashPontos` dos vértices atuais e a lista de pontos no intervalo, em ordem de data, cada um com a data (`dVertcReft`) e o valor (`vPrecoTx`, decimal como string na escala gravada). Sem `de` e `ate`, a lista SHALL trazer todos os pontos gravados; `de` posterior a `ate`, ou data inválida, responde 400 `PARAMETRO_INVALIDO`. A rota SHALL só ler: MUST NOT recalcular, interpolar nem gravar.

Sem vértices da curva na data em `tDadoVertcCurva`, a resposta SHALL ser 404 `CURVA_NAO_CONSTRUIDA`. Com vértices e sem pontos gravados, ou com pontos que não conferem com a interpolação dos vértices atuais, a resposta SHALL ser 200 com o que estiver gravado e o aviso `INTERPOLADA_DESATUALIZADA`. A autenticação, a correlação e o formato de erro seguem a spec `curve-engine-api`.

#### Scenario: Pontos da PRE
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14/pontos`
- **THEN** a resposta traz 12.390 pontos, de `2026-09-15` a `2060-08-16`, e os de `2026-09-19` e `2026-09-20` com o mesmo valor de `2026-09-18`

#### Scenario: Intervalo de datas
- **WHEN** o cliente chama a rota com `de=2026-09-15&ate=2026-09-21`
- **THEN** a lista traz os 7 pontos do intervalo, e a quantidade total continua 12.390

#### Scenario: Curva ainda não construída
- **WHEN** o cliente pede os pontos da `DCL` numa data sem construção
- **THEN** a resposta é 404 com `CURVA_NAO_CONSTRUIDA`
