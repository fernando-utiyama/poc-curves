# Curves v1.2: itens 5, 6 e 7 (excluir curva, apagar construção da data e excluir versão de configuração)

Pacotes estão como `br.com.poc`: troque por `br.com.bradesco`. **Sem trava por nome** (`travarPorNome` não é usado em nenhum arquivo daqui; entra depois, junto com a trava das versões).

Todos os arquivos desta pasta já identificam a curva pelo **nome** (`findByNome`, rotas `/{nome}`).

## Ordem

1. `compartilhado/` (usado pelos itens 5, 6 e 7), em:
   - `ResumoConstrucao.java`, `ConstrucaoApagada.java`, `LinhasPorTabela.java` → `domain/cadastro/`
   - `DadosConstruidosPort.java` → `application/port/out/`
   - `DadosConstruidosPersistenceAdapter.java` → `adapter/out/persistence/`
2. Item 7, `07-excluir-versao/`:
   - `ConfiguracaoCurva.java` → `domain/cadastro/` (substitui; só ganha `comInicioVigencia`)
   - `ConfiguracaoCurvaService.java` → `application/service/` (substitui)
   - `ConfiguracaoCurvaController.java` → `adapter/in/api/rest/controller/` (substitui)
   - `ConfiguracaoCurvaServiceTest.java` → teste do serviço (substitui)
3. Item 5, `05-excluir-curva/`: os três arquivos `.java` (use case e service em `application/`, teste) e os trechos de `TRECHOS.md` (porta e adaptador da curva, códigos de erro, método no controller).
4. Item 6, `06-apagar-construcao/`:
   - `ApagarConstrucaoResponse.java` → `adapter/in/api/rest/dto/`
   - `ApagarConstrucaoService.java` → `application/service/`
   - `CurvaMercadoAcoesController.java` → `adapter/in/api/rest/controller/` (substitui; ganha só o `DELETE /vertices` e o campo `apagarConstrucaoService`)
   - `ApagarConstrucaoServiceTest.java` → teste novo

## Mexer à mão

1. **`CadastroErrorCode`**: acrescentar `VERSAO_EM_USO("Versão em uso por curva construída"),` (item 7) e os dois de `05-excluir-curva/TRECHOS.md` (item 5) antes de `ERRO_INTERNO`. Para dar 409 como os demais conflitos, veja onde o tratador mapeia `CODIGO_EM_USO` e coloque os novos ao lado.
2. **`CodigoAvisoCurva`**: conferir se existe `SEM_CONFIGURACAO`; se não, acrescentar.
3. **`ConfiguracaoCurvaUseCase`**: trocar a assinatura para `ConfiguracaoCurvaResultado excluir(String nomeCurva, Integer versao);` (era `int versao`).
4. **Quem constrói `ConfiguracaoCurvaService` à mão** (outros testes): acrescentar o último argumento, um `DadosConstruidosPort` (mock).

## O que muda no comportamento

- `DELETE /curvas-mercado/{nome}/configuracoes?versao=N` substitui `DELETE .../configuracoes/{versao}`. Sem `versao`, exclui a vigente hoje. **O front precisa trocar a chamada.**
- Qualquer versão pode ser excluída, desde que não haja curva construída na vigência dela (senão 409 `VERSAO_EM_USO`). A vizinha cobre a vigência da excluída; excluir a única devolve o aviso `SEM_CONFIGURACAO`.
- Nova rota `DELETE /curvas-mercado/{nome}`: só exclui a curva sem linhas dela nas tabelas dependentes (construído e dado bruto); senão 409 `CURVA_COM_HISTORICO` com as linhas por tabela.
- Nova rota `DELETE /curvas-mercado/{nome}/{dataBase}/vertices`: apaga os vértices construídos e a interpolada da data, numa transação só; data não construída dá 404.

## Feito pelo usuário (não é v1.2): identificador da curva pelo nome

A troca de `codigo` para `nome` (a PK) no `CurvaMercadoUseCase`, no service, no controller e no repositório já foi feita no repositório real. `08-corrigir-busca-codigo/` e `09-identificador-nome/` ficam só como referência. Resta conferir o `CurvaMercadoAcoesService`: recebe o nome, busca a curva com `findByNome` e repassa `curva.codigo()` ao engine (422 se a curva não tiver código).

## Dado bruto da B3 (itens 3 e 4 do backlog), `10-dado-bruto/`

Substituem arquivos: `BtrsCurvaPrimrResumo.java` (`domain/cadastro/`), `BtrsCurvaPrimrResumoResponse.java` (`adapter/in/api/rest/dto/`) e `BtrsCurvaPrimrRepositoryPort.java` (`application/port/out/`). `TRECHOS.md` traz o que trocar à mão no repositório JPA, no adaptador e no service; `BtrsCurvaPrimrServiceListagemTest.java` é teste novo.

- Sem `de` e `ate`: uma linha por curva, com a última data gravada. Com período: como antes.
- `codigosNaFonte` vira `tickersProvedor`; a query deixa de esconder curvas sem código.
- Sem calendário: os dias úteis sem dado ficaram para a v2 (`docs/backlog-v2.md`).
- Item 1 (`produces` JSON): nos 3 controllers (`BbergCurvaPrimrController`, `AnbmaCurvaPrimrController` e `BtrsCurvaPrimrController`), `@RequestMapping(value = "/api/v1/curvas-mercado", produces = MediaType.APPLICATION_JSON_VALUE)` e importar `org.springframework.http.MediaType`.
- ANBIMA e Bloomberg: a mesma mudança, trocando a tabela (`tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`) e o provedor/produto da consulta de tickers (`ANBIMA`/`MS`, `BLOOMBERG`/`BLC2`).
