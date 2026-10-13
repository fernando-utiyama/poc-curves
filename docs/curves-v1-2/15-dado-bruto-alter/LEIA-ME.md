# Dado bruto com os ALTERs (id IDENTITY e título/vencimento da ANBIMA)

**Só aplique esta pasta depois da `14-dado-bruto` e depois que os ALTERs estiverem aplicados no banco**: o `004` (`cIdtfdUnic` das primárias vira `bigint IDENTITY`) e o `002` (`cTpoInstt` e `dVctoContr` na `tAnbmaCurvaPrimr`), em `db/migration/`. Sem eles, a aplicação não sobe ou não grava. A `14` fica como está (id `Integer` gerado por `proximoId()`).

Pacotes como `br.com.poc`: troque pelo pacote do seu repositório. Os arquivos são **inteiros** (copiar por cima dos da `14`, já aplicados); só entram aqui os que mudam.

## 1. Id IDENTITY (ALTER `004`), nas 3 fontes

- Entidades: `Long idtfdUnic` com `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Repositórios: `JpaRepository<..., Long>`, sem a query `proximoId`, ids `Long`.
- Portas e adaptadores: sem `proximoId()`, ids `Long`.
- Services: o `incluir` grava com id `null` (o banco gera), e `alterar`, `excluir` e `obterVertice` usam `Long`.
- Use cases e controllers: ids `Long`. Nos `...VerticeResponse` (que são os seus), `Integer id` vira `Long id` (no JSON continua número).
- `BtrsCurvaPrimr` (domínio): `Long id`. Teste `BtrsCurvaPrimrServiceTest`: sem o mock de `proximoId`, e o `salvar` com `any()`.

| Arquivos | Onde |
|---|---|
| `Btrs/Anbma/BbergCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` |
| `Btrs/Anbma/BbergCurvaPrimrRepository.java` | `adapter/out/persistence/repository/` |
| `Btrs/Anbma/BbergCurvaPrimrPersistenceAdapter.java` | `adapter/out/persistence/` |
| `Btrs/Anbma/BbergCurvaPrimrRepositoryPort.java` | `application/port/out/` |
| `Btrs/Anbma/BbergCurvaPrimrUseCase.java` | `application/port/in/usecase/` |
| `Btrs/Anbma/BbergCurvaPrimrService.java` | `application/service/` |
| `Btrs/Anbma/BbergCurvaPrimrController.java` | `adapter/in/api/rest/controller/` |
| `BtrsCurvaPrimr.java` | `domain/cadastro/` |
| `BtrsCurvaPrimrServiceTest.java` (com os testes de listagem, que ficam só nele) | `src/test/java/...` |

**À mão:** no record `BbergCurvaPrimr` (é o seu, não está aqui), `Integer id` vira `Long id`. Qualquer outro lugar que use o id da primária como `Integer` (Alt+F7 no `proximoId` antes de apagar; deve ficar com 0 usos).

## 2. ANBIMA com título e vencimento (ALTER `002`)

- Entidade: `cTpoInstt` (`tpoInstt`) e `dVctoContr` (`dataVctoContr`), opcionais.
- Domínio `AnbmaCurvaPrimr` (este arquivo **substitui o seu**, reconstruído da sua foto): `id`, `nomeCurva`, `dataBase`, `taxa`, `vertice`, mais `tipoInstrumento` e `vencimento`.
- `AnbmaCurvaPrimrInput`: `tipoInstrumento` e `vencimento` opcionais, gravados como vierem (sem regra de consistência com `prazoDiasCorridos`; só o limite de 50 caracteres do tipo, que é do banco). O `AnbmaCurvaPrimrVerticeRequest.java` desta pasta (parte do da `14`, com o `prazoDiasCorridos`) já traz os dois campos, com `@Schema`, e o `toDomain()` com 4 argumentos: copie por cima do seu.
- No **seu** `AnbmaCurvaPrimrVerticeResponse`: o `vencimento` passa a ser `v.vencimento()` (e, se for nulo, `dataBase + prazo` como antes), e ganha `tipoInstrumento` (`v.tipoInstrumento()`).
- Adaptador e service: passam os dois campos novos.

| Arquivos | Onde |
|---|---|
| `AnbmaCurvaPrimr.java`, `AnbmaCurvaPrimrInput.java` | `domain/cadastro/` |
| `AnbmaCurvaPrimrVerticeRequest.java` | `adapter/in/api/rest/dto/` |

(a entidade, o adaptador e o service da ANBIMA já vêm na tabela da seção 1, com as duas mudanças juntas.)

## O que muda para o front

- O JSON dos vértices da ANBIMA ganha `tipoInstrumento`, e o `vencimento` passa a ser o gravado. No incluir e no alterar, o corpo aceita `tipoInstrumento` e `vencimento` (opcionais).
- Nada mais muda para o front (o `id` continua número).

## Conferir

- **Acessores do `AnbmaCurvaPrimr`**: o record acima segue a ordem da sua foto (`id, nomeCurva, dataBase, taxa, vertice`) com os dois campos no fim. Se o seu tiver outra ordem, o erro aparece no `novoVertice` do service e no `toDomain` do adaptador.
- **Processor**: ele também grava nessas tabelas. Com `IDENTITY`, quem insere não pode mais informar o `cIdtfdUnic`. Confirme como o processor gera o id antes de aplicar o `004`.
- **Tesouro (`003`)**: não há dado bruto do Tesouro na curves; fica fora desta pasta.

Os 3 services desta pasta já chamam o `EventoCurvaPrimariaEditada` com 10 argumentos, sem o `correlationId` (ver o "Por último (14)" da `14`).

## Testes dos 3 services (com os ALTERs)

`BtrsCurvaPrimrServiceTest`, `AnbmaCurvaPrimrServiceTest` e `BbergCurvaPrimrServiceTest` desta pasta são os da `14` com o id `Long`: o `incluir` não mocka o `proximoId()` (não existe mais) e o `salvar` usa `any()`. No da ANBIMA, o record e o input têm os 2 campos novos (`tipoInstrumento` e `vencimento`), passados como `null`. Copie os 3 por cima dos da `14`.



## Testes dos 3 controllers (com os ALTERs)

Os `...CurvaPrimrControllerTest` desta pasta são os da `14` com o id `Long` (`eq(1L)`, `1L` nos vértices). No da ANBIMA, o `AnbmaCurvaPrimr` tem os 2 campos novos (`null, null`). Copie os 3 por cima dos da `14`.


Os `AnbmaCurvaPrimrVerticeResponse` e `BbergCurvaPrimrVerticeResponse` desta pasta são os da `14` (decimal em texto) com o id `Long`; no da ANBIMA, o `vencimento` é o gravado (e, se for nulo, data-base mais o prazo) e há o `tipoInstrumento`. Substituem o que o "Por último (17)" da `14` mandou copiar.
