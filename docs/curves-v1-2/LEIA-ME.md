> **Regra para todos os endpoints:** sucesso simples (200 ok) e erro com mensagem completa, porque o front por enquanto só mostra o texto do erro. Ver `docs/backlog-curves-v1-2.md`.

# Curves v1.2: itens 5, 6 e 7 (excluir curva, apagar construção da data e excluir versão de configuração)

Pacotes estão como `br.com.poc`: troque por `br.com.bradesco`. **Sem trava por nome** (`travarPorNome` não é usado em nenhum arquivo daqui; entra depois, junto com a trava das versões).

Todos os arquivos desta pasta já identificam a curva pelo **nome** (`findByNome`, rotas `/{nome}`). O que mais muda por causa disso (controllers do bruto, ações do engine e chamadas do front) está em `docs/backlog-curves-v1-2.md`, seção "Identificador da curva = nome".

## Ordem

1. `compartilhado/` (usado pelos itens 5, 6 e 7), em:
   - `DadoVertcCurvaRepositoryPort.java` → `application/port/out/`
   - `DadoVertcCurvaPersistenceAdapter.java` → `adapter/out/persistence/`
2. Item 7, `07-excluir-versao/`:
   - `ConfiguracaoCurva.java` → `domain/cadastro/` (substitui; só ganha `comInicioVigencia`)
   - `ConfiguracaoCurvaService.java` → `application/service/` (substitui)
   - `ConfiguracaoCurvaController.java` → `adapter/in/api/rest/controller/` (substitui)
   - `ConfiguracaoCurvaServiceTest.java` → teste do serviço (substitui)
3. Item 5, `05-excluir-curva/`: **dentro do `CurvaMercadoService`** (sem service novo): os trechos de `TRECHOS.md` (use case, porta e adaptador da curva, método e campo no service, código de erro, método no controller) o `CurvaMercadoController.java` completo (com `{nome}`, o delete e a auditoria tipada) e os 3 testes para colar no seu `CurvaMercadoServiceTest` (seção 6 do `TRECHOS.md`).
4. Item 6, `06-excluir-vertices/`: **dentro do `CurvaMercadoAcoesService`** (sem service novo). `CurvaMercadoAcoesService.java` (`application/service/`) e `CurvaMercadoAcoesController.java` (`adapter/in/api/rest/controller/`) substituem os seus; os testes para colar no seu `CurvaMercadoAcoesServiceTest` estão no `TRECHOS.md` da pasta.

## Mexer à mão

1. **`CadastroErrorCode`**: acrescentar `VERSAO_EM_USO("Versão em uso por curva construída"),` (item 7) e os dois de `05-excluir-curva/TRECHOS.md` (item 5) antes de `ERRO_INTERNO`. Para dar 409 como os demais conflitos, veja onde o tratador mapeia `CODIGO_EM_USO` e coloque os novos ao lado.
2. **`ConfiguracaoCurvaUseCase`**: trocar a assinatura para `void excluir(String nomeCurva, Integer versao);` (era `ConfiguracaoCurvaResultado excluir(String, int)`).
2. **Quem constrói `ConfiguracaoCurvaService` à mão** (outros testes): acrescentar o último argumento, um `DadoVertcCurvaRepositoryPort` (mock).

## O que muda no comportamento

- `DELETE /curvas-mercado/{nome}/configuracoes?versao=N` substitui `DELETE .../configuracoes/{versao}`. Sem `versao`, exclui a vigente hoje. **O front precisa trocar a chamada.**
- Qualquer versão pode ser excluída, desde que não haja curva construída na vigência dela (senão 409 `VERSAO_EM_USO`). A vizinha cobre a vigência da excluída; excluir a única só a remove (a curva não constrói até ganhar outra).
- Nova rota `DELETE /curvas-mercado/{nome}`: só exclui a curva sem linhas dela nas tabelas dependentes (construído e dado bruto); senão 409 `CURVA_COM_HISTORICO`: quem recusa é o banco, pelas chaves estrangeiras (construído, dado bruto), e o service explica o que fazer.
- Nova rota `DELETE /curvas-mercado/{nome}/{dataBase}/vertices`: apaga os vértices construídos e a interpolada da data, numa transação só. Responde 200 sem corpo; data não construída dá 404.

## Feito pelo usuário (não é v1.2): identificador da curva pelo nome

A troca de `codigo` para `nome` (a PK) no `CurvaMercadoUseCase`, no service, no controller e no repositório já foi feita no repositório real. `09-identificador-nome/` fica só como referência. O `CurvaMercadoAcoesService` (recebe o nome e repassa `curva.codigo()` ao engine; curva sem código → 422) está na pasta `06-excluir-vertices/`, junto com o apagar da construção.

## Dado bruto (itens 3, 4 e 9 do backlog), `10-dado-bruto/`

Começar por `EQUALIZAR.md`: deixa os três repositórios, a entidade da B3 e a projection com a mesma forma (B3, ANBIMA e Bloomberg).

Substituem arquivos: `BtrsCurvaPrimrRepository.java`, `AnbmaCurvaPrimrRepository.java` e `BbergCurvaPrimrRepository.java` (`adapter/out/persistence/repository/`), `CurvaPrimrResumo.java` (`domain/cadastro/`), `CurvaPrimrResumoResponse.java` (`adapter/in/api/rest/dto/`) e `BtrsCurvaPrimrRepositoryPort.java` (`application/port/out/`). `TRECHOS.md` traz o que trocar à mão no repositório JPA, no adaptador e no service; `BtrsCurvaPrimrServiceListagemTest.java` é teste novo.

- Sem `de` e `ate`: uma linha por curva, com a última data gravada. Com período: como antes.
- `codigosNaFonte` vira `tickersProvedor`; a query deixa de esconder curvas sem código.
- Sem calendário: os dias úteis sem dado ficaram para a v2 (`docs/backlog-v2.md`).
- Item 1 (`produces` JSON): nos 3 controllers (`BbergCurvaPrimrController`, `AnbmaCurvaPrimrController` e `BtrsCurvaPrimrController`), `@RequestMapping(value = "/api/v1/curvas-mercado", produces = MediaType.APPLICATION_JSON_VALUE)` e importar `org.springframework.http.MediaType`.
- ANBIMA e Bloomberg: `TRECHOS-ANBIMA-BLOOMBERG.md`, escrito por analogia com a B3 (os adaptadores delas não foram vistos). O `AgregadoPrimr.java` tira da listagem a duplicação entre as três fontes (item 9 do backlog).

## Renomeado: `codigoNaFonte` → `tickerProvedor` (provedor da curva)

É o `cTickerPrvdr` da `tCurvaPrvdr`. No repositório da curves, trocar com Shift+F6 (renomear), para a IDE acompanhar os usos: o campo do record `CriarCurvaProvedorRequest` e do `CriarCurvaProvedorInput`, o record de domínio `CurvaProvedor` (`codigoNaFonte()` → `tickerProvedor()`), o parâmetro do `buscarCurvasProvedor(provedor, produto, tickerProvedor)`, o campo da `CurvaPrvdrEntity` (a coluna fica no `@Column`), as respostas e o filtro `?tickerProvedor=` das rotas `GET /curvas-mercado/provedores` e `/dados-mercado/{provedor}`. O `AgregadoPrimr` e o `CurvaMercadoService` desta pasta já usam o nome novo. O `CurvaProvedor` do **engine** é outro record e não muda.

## Removido por falta de uso (feito pelo usuário)

- `CurvaPrvdrRepositoryPort.findByProvedor(String provedor)` e a implementação no adaptador: só o `inativar` do `CurvaMercadoService` o usava (aviso `CURVA_COM_FILHAS` do provedor `TCEN`), que saiu porque curva derivada não entra nesta versão. Quando a derivada existir, o método volta junto com o aviso.

## Sem "canônico" (item 8), `11-sem-canonico/`

Os dois records passam a carregar o próprio domínio, e o `...CanonicoState` deixa de existir:
- `CurvaAuditoria.java` e `CurvaMercadoDetalhada.java` (`domain/cadastro/`) **substituem** os seus: `List<CurvaProvedor> provedores` e `ConfiguracaoCurva`/`List<ConfiguracaoCurva>` no lugar dos `...CanonicoState`; sai o `curvaProvedores()`, alias de `provedores()`.
- `07-excluir-versao/ConfiguracaoCurva.java` já vem sem `toCanonicoState()`; no `CurvaProvedor`, apagar o `toCanonicoState()`.
- `CurvaAuditoriaExcelGenerator.java` (`adapter/in/api/rest/excel/`): **arquivo inteiro**, substitui o seu. Muda os dois imports, os dois laços (`auditoria.provedores()` e `auditoria.configuracoes()`) e a coluna `parametros`, que passa a sair como o JSON de `cModDado` (via `JsonMapper` do Jackson 3), em vez do `toString()` do record.
- Depois, apagar `ConfiguracaoCanonicoState` e `CurvaProvedorCanonicoState`.
- **Atenção ao JSON do front:** os DTOs de resposta (`CurvaMercadoDetalhadaResponse`, `CurvaProvedorResponse`, `ConfiguracaoCurvaResponse`) montam o JSON a partir desses tipos. Trocar o tipo de origem pode acrescentar campos (`id`, `nomeCurva`) ou mudar nomes: confira o JSON no Swagger depois.
- **Vistos e confirmados:** `ConfiguracaoCanonicoState` e `CurvaProvedorCanonicoState` são subconjuntos do `ConfiguracaoCurva` e do `CurvaProvedor` (sem `id`/`nomeCurva` no primeiro, sem `nomeCurva` no segundo) e `Comparable` por `versao` e `idCurvaProvedor`. Os repositórios já devolvem nessa ordem, então o `compareTo` não é necessário; se algum lugar ordena uma lista com ele (Alt+F7 em `compareTo`), troque por `Comparator.comparing(...)`.
- **`CurvaMercadoDetalhadaResponse`** (`11-sem-canonico/`, substitui o seu): `provedores` vira `List<CurvaProvedorResponse>` e `configuracaoVigente` vira `ConfiguracaoCurvaResponse`, convertidos no `fromDomain`. O `CurvaProvedorResponse` não muda (já era feito do `CurvaProvedor`).
- **Mudança no JSON dos provedores do detalhe:** antes, `provedores` trazia só `tickerProvedor`, `idCurvaProvedor`, `prioridade`, `produto` e `provedor` (o state); agora traz também `curva` (o nome), como a rota dos provedores já devolvia. Se o front rejeita campo a mais, é o único ponto.
- **Conferir:** o `ConfiguracaoCurvaResponse.fromDomain` e o `CurvaMercadoResponse.fromDomain(curva, avisos)` precisam existir com essas assinaturas (o do `CurvaMercadoResponse` está na sua foto; o da configuração eu não vi, mas a rota de configurações já o usa).
