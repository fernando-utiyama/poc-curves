# Limpeza do cadastro (CurvaMercadoService, ConfiguracaoCurvaService e use case)

Sem mudança de rota: tira código sobrando, reparte em métodos e ajusta a arquitetura. Mudam de comportamento só o `reativar` (devolve o detalhe completo), a listagem de curvas (sem paginação) e o `validar` da configuração (confere o início de vigência). Pacotes como `br.com.poc`: troque pelo pacote do seu repositório. Os 20 arquivos são inteiros, copiar por cima (partem das versões já aplicadas em `aplicado/`); `CurvaMercadoAcoesUseCase` é novo.

| Arquivo | Onde |
|---|---|
| `CurvaMercadoService.java` | `application/service/` |
| `ConfiguracaoCurvaService.java` | `application/service/` |
| `CurvaMercadoUseCase.java` | `application/port/in/usecase/` |
| `CurvaMercado.java` | `domain/cadastro/` |
| `CurvaProvedorService.java` | `application/service/` |
| `CurvaMercadoAcoesController.java` | `adapter/in/api/rest/controller/` (ganha `@Operation` nas rotas e passa a chamar o use case, não o service) |
| `CurvaProvedorUseCase.java` | `application/port/in/usecase/` (deduzido do service: confira os imports dos inputs) |
| `ValidadorParametros.java` | `domain/cadastro/` (sem avisos) |
| `ValidadorParametrosTest.java` | teste do validador (substitui) |
| `CurvaMercdRepositoryPort.java` | `application/port/out/` (porta inteira, sem paginação e sem `findAllValidas`) |
| `CurvaMercadoController.java` | `adapter/in/api/rest/controller/` (reconstruído das suas fotos: `listar` sem paginação; o resto como está) |
| `CurvaMercadoResponse.java` | `adapter/in/api/rest/dto/` (reconstruído das suas fotos, sem o campo `avisos`) |
| `CurvaMercadoDetalhadaResponse.java` | `adapter/in/api/rest/dto/` (chama `fromDomain(d.curva())`) |
| `CurvaProvedorController.java` | `adapter/in/api/rest/controller/` (reconstruído das suas fotos; rotas por `{nome}`, sem avisos) |
| `CurvaProvedorCurvaResponse.java` | `adapter/in/api/rest/dto/` (o seu, sem o `codigo`) |
| `RespostaEngine.java` | `domain/cadastro/` (o record que estava dentro do `EnginePort`, movido para fora: `status` e `corpoJson`) |
| `EnginePort.java` | `application/port/out/` (sem o record aninhado; devolve o `RespostaEngine` de domínio) |
| `ConfiguracaoCurvaRepositoryPort.java` | `application/port/out/` (sem `findModeloConstrucaoVigente`; reconstruída da sua foto) |
| `CurvaMercadoAcoesUseCase.java` | `application/port/in/usecase/` (novo) |
| `CurvaMercadoAcoesService.java` | `application/service/` (implementa o use case; parte do que está em `aplicado/06-excluir-vertices/`) |

## O que mudou

**`CurvaMercadoService`**
- **Sem `if (porta != null)`:** as três portas entram pelo construtor e nunca são nulas (`buscarProvedoresPorNomes`, `consultarAuditoria`, `excluir`, `provedoresDe`, `configuracaoVigenteDe` e o bloco do `alterar`).
- **Sem paginação:** `listar` devolve `List<CurvaMercado>` (6 filtros, sem `pagina` e `tamanho`); sem `Page`, `Pageable` nem `Sort` no use case e no service. A ordem (código e, no desempate, o nome) fica no adaptador.
- **Valores aceitos vêm dos enums:** `Arrays.toString(Unidade.values())` (e `DayCounterCotacao`, `CompoundingCotacao`) no lugar das listas escritas à mão nas mensagens.
- **`detalhar` agora é privado** e saíram os overloads antigos de `listar`.
- **`inativar` e `reativar` em 3 linhas:** usam o novo `CurvaMercado.comSituacao(situacao, atualizadoEm)` em vez de copiar os 18 argumentos do construtor. **Pequena mudança de comportamento:** o `reativar` passa a devolver o detalhe completo (provedores e configuração vigente), como o `inativar` já fazia; antes devolvia o detalhe vazio.
- **`alterar` mais curto:** saiu a conferência "alterar a curva não invalida uma versão vigente ou futura" (`ValidadorParametros` contra cada versão de configuração). Era regra de negócio, o banco não exige; a versão nova continua validada contra a curva no `ConfiguracaoCurvaService.criar`.

**`ConfiguracaoCurvaService`**
- **`validar` agora confere o início de vigência** como o `criar` (depois da versão anterior, não no passado): antes respondia 200 e o `criar` recusava em seguida.
- Sem o `obterProvedores` (chama a porta direto, sem checar nulo).
- `criar` mais curto: as regras do início da nova versão viraram `validarInicioDaVersao` (mesmas mensagens).
- `validarCamposObrigatorios` sem repetição: `validarTexto(campo, valor, máximo, erros)` serve a `modeloConstrucao` e `interpolador`.

**`CurvaMercado`** (record): ganha `comSituacao`; o resto é o que você já tem (construtor de 17 argumentos sem dono e `normalizarNome` iguais).

**`CurvaMercadoUseCase`**: `listar` sem paginação; saem os `default listar(...)` antigos.

**Ações da curva (hexagonal)**: o controller chamava o `CurvaMercadoAcoesService` direto e usava o `EnginePort.RespostaEngine` (tipo da porta de saída). Agora há o `CurvaMercadoAcoesUseCase`, e o `RespostaEngine` saiu de dentro do `EnginePort` para `domain/cadastro/` (no IDE: F6, "Move Inner Class to Upper Level", que corrige as referências). A porta, o use case e o controller usam o mesmo tipo: o service só repassa o que o engine devolve, sem converter. Os testes não mudam (`RespostaEngine resposta = service.construir(...)` continua valendo).

## Conferir

1. **Testes que montam o service com portas `null`**: sem os `if`, o `NullPointerException` aparece. Com `@Mock`/`@InjectMocks` não há problema.
2. **Quem chamava `listar`** com `pagina` e `tamanho` (Alt+F7 antes de colar): o `CurvaMercadoController` desta pasta já chama `useCase.listar(...)` com 6 filtros e devolve o array. Apague `CurvasMercadoPaginadaResponse` (e o `Page` do controller) se ficar sem uso. **O front** que lê o formato de página (conteúdo e totais) precisa ler a lista direto.
3. **Teste de `alterar`** que confere a mensagem de valores aceitos: ela continua `Valores aceitos: [...]` com os mesmos nomes, na ordem do enum.
4. **Outros chamadores de `ValidadorParametros.validar`** (Alt+F7): a assinatura mudou (sem `modeloConstrucao` e sem a lista de provedores). Aqui só o `ConfiguracaoCurvaService` chama; se aparecer outro no seu repo, o compilador acusa.
5. **Sobras dos avisos** (`AvisoCurva`, `CodigoAvisoCurva` e as constantes `NATIVE_*` do validador): ficam por enquanto, porque as curvas primárias ainda os usam; a exclusão está no fim do `LEIA-ME.md` da `14-dado-bruto`.
6. **`ConfiguracaoCurvaServiceTest`**: o service perdeu o `CurvaPrvdrRepositoryPort` (4 portas no construtor) e o teste de `validar` passa a mockar `findUltimaVersao`.
7. **Código de país**: a normalização para maiúsculas não foi aplicada; confirmar com o Copilot se o engine lê `cPaisInstt` antes de fazer.

## `avisos` fora do `CurvaMercadoResponse`

Nenhum chamador preenchia os avisos (só o de curva derivada, que saiu). O `CurvaMercadoResponse` perde o campo `avisos` e o `fromDomain(c, avisos)`: ficam `fromDomain(c)` e `fromDomain(c, provedores)`; o construtor de 12 argumentos continua. O JSON de `GET /curvas-mercado` e do detalhe deixa de trazer `avisos: []`.

- **Conferir (Alt+F7):** testes (`CurvaMercadoControllerWebTest` etc.) e o front que leiam `avisos` da curva; chamadas antigas de `fromDomain(c, List.of())`, que agora passariam a ser lidas como `provedores` (compila, mas confira o que cada uma quer); `AvisoCurva` e `CodigoAvisoCurva` saem no fim da `14-dado-bruto`.

## Validação da configuração (`POST /api/v1/curvas-mercado/{nome}/configuracoes/validacao`)

É a rota do `ConfiguracaoCurvaController` (`aplicado/12-sem-avisos/`) que chama `ConfiguracaoCurvaService.validar`, sem gravar nada: 200 vazio se a versão está correta, ou o erro explicado. O `validar` passa a conferir **também o início de vigência**, como o `criar` (depois da versão anterior, não no passado e não antes do início da curva), então o 200 dessa rota vale só se o `criar` for aceitar.

**`ValidadorParametros` sem avisos:** os avisos de cálculo (`MODELO_NAO_NATIVO`, `ORIGEM_INCOMPATIVEL_COM_MODELO`, `MODELO_POR_ORIGEM_SEM_PROVEDOR`) não tinham mais quem os lesse. Saem `ValidacaoResultado.avisos`, `avisosDeModelo`, `avisosDeProvedor` e `origemIncompativel`. A assinatura fica `validar(interpolador, parametros, unidade, compounding)`: sem `modeloConstrucao` e sem a lista de provedores, que só serviam aos avisos. Com isso o `ConfiguracaoCurvaService` deixa de buscar os provedores da curva a cada validação e perde o `CurvaPrvdrRepositoryPort` (o construtor passa a ter 4 portas: quem monta o service à mão, como o `ConfiguracaoCurvaServiceTest`, tira esse argumento).

- **Teste:** `ValidadorParametrosTest` sem o caso do aviso e sem as asserções de `avisos()`.

- **Teste:** no `ConfiguracaoCurvaServiceTest`, o teste de `validar` precisa mockar `configuracaoRepositoryPort.findUltimaVersao(nome)` (vazio, `Optional.empty()`, se não importa) e ganha um caso: início de vigência igual ou anterior ao da última versão devolve `DADOS_INVALIDOS`.
- **Spec antiga:** a `configuracao-calculo-curva` da v1 (linha da rota) ainda diz `/{codigo}/...` e "devolvendo erros e avisos"; vale a rota acima, por nome e sem avisos. A spec não foi alterada.

## Contrato da listagem sem paginação (substitui o que a v1 e a v1.1 definiram)

`GET /api/v1/curvas-mercado?nome=&codigo=&unidade=&situacao=&provedor=&dono=` responde **200 com o array JSON** das curvas que atendem aos filtros (os itens são os mesmos de antes, com `provedores`, `dono` e `ultimaExecucao`). Sem `pagina`, `tamanho`, `total` nem envelope; ordenado por código, desempate pelo nome. A tela de cadastro (`fed-curvas-mercado`) mostra o total como a quantidade de itens. As specs da v1 e da v1.1 (`cadastro-curva-mercado`, `listagem-curvas-v1-1`) ficam como estavam: a regra vale a partir daqui.

## `CurvaMercdRepositoryPort` e `CurvaMercdPersistenceAdapter` (à mão)

**Porta:** copiar o `CurvaMercdRepositoryPort.java` desta pasta por cima do seu. Os dois `listar` com `Pageable` (o abstrato e o `default` de 4 filtros) viram um só, `List<CurvaMercado> listar(nome, codigo, unidade, situacao, provedor, dono)`, e saem os imports de `Page` e `Pageable`. Sai também o `findAllValidas()`: só o `listarPorOrigem` o usava, e agora ele não consulta as curvas (apague a implementação no adaptador; confirme com Alt+F7 que está com zero usos). Reconstruída da sua foto: os outros métodos estão como lá.

**`listar` do adaptador:** troque o `listar` inteiro por este (cada filtro vira uma `Specification` pequena que devolve `null` quando o parâmetro não vem, e o `allOf` ignora os nulos):

```java
@Override
public List<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao,
                                 String provedor, String dono) {
    Specification<CurvaMercdEntity> filtro = Specification.allOf(
        igual("tickerIdtfdUnic", codigo),
        igual("tpoVlr", unidade),
        igual("sitReg", situacao),
        contem("tickerIndcd", nome),
        contem("pprioDado", dono),
        comProvedor(provedor));
    Sort ordem = Sort.by("tickerIdtfdUnic").ascending().and(Sort.by("tickerIndcd").ascending());
    return repository.findAll(filtro, ordem).stream().map(this::toDomain).toList();
}

private static Specification<CurvaMercdEntity> igual(String atributo, String valor) {
    if (!temTexto(valor)) {
        return null;
    }
    return (root, query, cb) -> cb.equal(root.get(atributo), valor);
}

private static Specification<CurvaMercdEntity> contem(String atributo, String texto) {
    if (!temTexto(texto)) {
        return null;
    }
    String padrao = "%" + texto.toLowerCase(Locale.ROOT).trim() + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get(atributo)), padrao);
}

/** Curvas que têm o provedor ligado em tCurvaPrvdr. */
private static Specification<CurvaMercdEntity> comProvedor(String provedor) {
    if (!temTexto(provedor)) {
        return null;
    }
    return (root, query, cb) -> {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        var p = subquery.from(CurvaPrvdrEntity.class);
        subquery.select(cb.literal(1));
        subquery.where(
            cb.equal(p.get("nomeCurva"), root.get("tickerIndcd")),
            cb.equal(cb.trim(p.get("provedor")), provedor.trim())
        );
        return cb.exists(subquery);
    };
}

private static boolean temTexto(String texto) {
    return texto != null && !texto.isBlank();
}
```

Imports: `org.springframework.data.domain.Sort`, `org.springframework.data.jpa.domain.Specification`, `jakarta.persistence.criteria.Subquery` e `java.util.Locale`; saem `Predicate`, `Root`, `CriteriaQuery`, `CriteriaBuilder` e `ArrayList`, se ficarem sem uso. Mudança única de comportamento: `toLowerCase(Locale.ROOT)` (sem isso, num servidor com locale turco, "I" vira "ı" e o filtro erra).

## `CurvaProvedorService` (provedor da curva)

**O que sai (343 → ~170 linhas)**
- Todo o bloco `TCEN` (`validarCurvaProvedorTcen`, `verificarCicloTcen`, `detectarCiclo`): curva derivada não existe nesta versão, e o provedor `TCEN` não está cadastrado, então o `existsById` já respondia 404 antes dessas regras. Volta junto com a derivada.
- Os avisos (`gerarAvisos`, `isOrigemCompativelComModelo` com os nomes de modelo escritos no código): mesmo critério da configuração. `criar` e `alterar` devolvem só o `CurvaProvedor`, e `excluir` é `void`.
- O segundo construtor, o `@Autowired(required = false)` e o `ConfiguracaoCurvaRepositoryPort`, que só existiam para os avisos (agora `@RequiredArgsConstructor` com 4 portas).
- O `findByIdAndNomeCurva` repetido no `alterar` e no `excluir` virou `obterProvedorDaCurva`; as três validações de campos usam `recusarSeHouverErros`.

**À mão**
1. **Apagar `CurvaProvedorResultado`**. No `CurvaProvedorController`: `criar` devolve `201` com `CurvaProvedorResponse.fromDomain(...)`, `alterar` devolve `200` com o response, e `excluir` devolve `200` vazio.
2. **Ficou sem uso**: `ConfiguracaoCurvaRepositoryPort.findModeloConstrucaoVigente` (porta, adaptador e repositório JPA) e os códigos `CURVA_SEM_ORIGEM` e `ORIGEM_INCOMPATIVEL_COM_MODELO` do `CodigoAvisoCurva`. Confirme com Alt+F7 antes de apagar.
3. **Testes**: `CurvaProvedorServiceTest` monta o service com 4 mocks; saem os testes de TCEN e de avisos, e os que liam `resultado.curvaProvedor()` passam a ler o retorno direto. `CurvaProvedorControllerWebTest` também perde os avisos.
4. **Front**: as respostas de provedor da curva não trazem mais `avisos`.

**`listarPorOrigem`:** devolve as próprias ligações (`List<CurvaProvedor>`) ordenadas por prioridade, com `nullsLast` para ligação antiga sem prioridade não dar `NullPointerException`. Não consulta mais as curvas (antes carregava todas as válidas só para achar o código), então não há porta nova. **O `codigo` saiu da resposta** da rota `GET /curvas-mercado/provedores`: a curva se identifica pelo nome.

**Hexagonal (feito)**
- O service não importa mais `adapter.out` nem `adapter.in`: o `SpringDataProvedorRepository` virou o `ProvedorRepositoryPort` (já existe, com `existsById`; troque só o tipo e o nome do campo no construtor do `CurvaProvedorServiceTest`).
- O use case devolve `List<CurvaProvedor>` (domínio) no lugar do `CurvaProvedorCurvaResponse`, sem classe nova.
- **À mão no controller:** no endpoint de listar por origem, `.map(c -> new CurvaProvedorCurvaResponse(c.nomeCurva(), c.prioridade()))`, sem o campo `codigo` (tire o `codigo` do record do response). O tipo de `prioridade` é `Integer`, como está em `CurvaProvedor`.

## `CurvaProvedorController` (arquivo inteiro, das suas fotos)

Copiar por cima do seu, junto com o `CurvaProvedorCurvaResponse`. Muda:
- nas 4 rotas com curva, `{codigo}` vira `{nome}` (e o `@PathVariable String nome`); `listarPorOrigem` continua em `/api/v1/curvas-mercado/provedores`;
- o `@Tag` fica "Gestão de provedores da curva de mercado (tCurvaPrvdr)" (sai "e curvas componentes");
- `criar` devolve `201` com `CurvaProvedorResponse`, `alterar` `200` com o response e `excluir` `200` vazio: sem `CurvaProvedorResultado` nem `CurvaProvedorComAvisosResponse`;
- `listarPorOrigem` converte `CurvaProvedor` em `CurvaProvedorCurvaResponse(nome, prioridade)`: o `codigo` saiu da resposta. O `CurvaProvedorCurvaResponse` desta pasta é o seu (da foto: `@JsonInclude(ALWAYS)`, `nome`, `prioridade`) sem o `codigo`; confira os 2 usos do `codigo()` antes de colar.
- O `validarParametros` ficou como estava, mas repete o que `@Validated`, `@NotBlank` e `@Positive` já fazem nos parâmetros: pode ser que um deles sobre.

Depois apague `CurvaProvedorResultado` e `CurvaProvedorComAvisosResponse` (Alt+F7: devem ficar sem uso) (o `AvisoCurva` sai no fim da `14-dado-bruto`).

**`ConfiguracaoCurvaRepositoryPort`:** o `findModeloConstrucaoVigente` (que só servia aos avisos de origem do provedor) sai da porta, e a implementação sai do adaptador e do repositório JPA. Na sua foto ele aparece com 1 uso: veja qual é (deve ser o `CurvaProvedorServiceTest` antigo ou o service antes da limpeza) e apague junto. O `findByNomeCurvaEVersao` aparece com 0 usos; não mexi, mas pode sair também.

## Testes e adaptador a ajustar

- **`CurvaMercdPersistenceAdapter`:** apague o `findAllValidas()` (a porta não o tem mais, e o `@Override` não compila) e, com 0 usos, o `findByTickerIdtfdUnicIdNotNull` do repositório JPA.
- **`ConfiguracaoCurvaRepositoryPort`:** apague o `findModeloConstrucaoVigente` na porta, no adaptador e no repositório JPA (fica com 0 usos depois do `CurvaProvedorServiceTest`).
- **`CurvaProvedorServiceTest`:** sem o import, o `@Mock` e o mock da `ConfiguracaoCurvaRepositoryPort` (4 portas no service); sem os testes de TCEN e de avisos; em `listarPorOrigem`, sem mockar `findAllValidas`.
- **`ConfiguracaoCurvaServiceTest`:** construtor com 4 portas; o teste de `validar` mocka `findUltimaVersao`.
- **`CurvaMercadoServiceTest`:** `listar` devolve `List`; o `excluir` por histórico segue com `DataIntegrityViolationException`.
- **`CurvaMercadoAcoesServiceTest`:** o tipo de `resposta` passa a `var`.

**`CurvaMercadoControllerWebTest`** (primeiro teste):

```java
@Test
void deveListarCurvas() throws Exception {
    CurvaMercado curva = new CurvaMercado( ...igual ao que já tem... );

    when(useCase.listar(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(curva));

    mockMvc.perform(get("/api/v1/curvas-mercado")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codigo").value("PRE"))
        .andExpect(jsonPath("$[0].nome").value("DIxPRE"))
        .andExpect(jsonPath("$[0].situacao").value("ATIVO"));
}
```

**`CurvaProvedorControllerWebTest`** (excluir e por origem; criar e alterar seguem o mesmo padrão: sem `avisos`, o `when` devolve `CurvaProvedor`):

```java
@Test
@DisplayName("DELETE /api/v1/curvas-mercado/{nome}/provedores/{id} exclui o provedor")
void deveExcluirComSucesso() throws Exception {
    mockMvc.perform(delete("/api/v1/curvas-mercado/PRE/provedores/1"))
        .andExpect(status().isOk());

    verify(useCase).excluir("PRE", 1L);
}

@Test
@DisplayName("GET /api/v1/curvas-mercado/provedores lista curvas por origem")
void deveListarCurvasPorOrigem() throws Exception {
    CurvaProvedor p1 = new CurvaProvedor(1L, "DIxPRE", "B3", "TS", "PRE", 1);
    CurvaProvedor p2 = new CurvaProvedor(2L, "DI_MERCADO", "B3", "TS", "PRE", 2);
    when(useCase.listarPorOrigem("B3", "TS", "PRE")).thenReturn(List.of(p1, p2));

    mockMvc.perform(get("/api/v1/curvas-mercado/provedores")
            .param("provedor", "B3")
            .param("produto", "TS")
            .param("tickerProvedor", "PRE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nome").value("DIxPRE"))
        .andExpect(jsonPath("$[0].prioridade").value(1))
        .andExpect(jsonPath("$[1].nome").value("DI_MERCADO"))
        .andExpect(jsonPath("$[1].prioridade").value(2));
}
```

Imports a apagar depois: `PageImpl`, `eq`, `AvisoCurva`, `CodigoAvisoCurva` (este fica até a `14`), `CurvaProvedorResultado` e `CurvaProvedorCurvaResponse`, nos testes em que não forem mais usados. `CurvaProvedor` (ordem: id, nome da curva, provedor, produto, ticker, prioridade) e `org.mockito.Mockito.verify` entram.

**`CurvaProvedorServiceTest`** (service com 4 portas; as stubs que o service novo não chama saem, senão o Mockito estrito acusa `UnnecessaryStubbingException`):

- **Apague** `cenarioCicloTresCurvasRecusado` (TCEN), `cenarioOrigemIncompativelComModelo` e todo teste que fale de aviso, de TCEN ou de `findModeloConstrucaoVigente`.
- Nos testes de `criar` e `alterar` que ficarem: `CurvaProvedor resultado = service.criar(...)`, sem `resultado.curvaProvedor()` nem `avisos()`, e sem os stubs de `findByNomeCurva` e `findModeloConstrucaoVigente`.

```java
@Test
@DisplayName("Scenario: Quais curvas recebem o PRE da B3")
void cenarioListarCurvasPorOrigem() {
    CurvaProvedor cp1 = new CurvaProvedor(1L, "DIxPRE", "B3", "TS", "PRE", 1);
    CurvaProvedor cp2 = new CurvaProvedor(2L, "DI_MERCADO", "B3", "TS", "PRE", 2);
    when(curvaPrvdrRepositoryPort.buscarCurvasProvedor("B3", "TS", "PRE")).thenReturn(List.of(cp2, cp1));

    List<CurvaProvedor> lista = service.listarPorOrigem("B3", "TS", "PRE");

    assertEquals(2, lista.size());
    assertEquals("DIxPRE", lista.get(0).nomeCurva());   // ordenada por prioridade
    assertEquals(1, lista.get(0).prioridade());
    assertEquals("DI_MERCADO", lista.get(1).nomeCurva());
    assertEquals(2, lista.get(1).prioridade());
}

@Test
@DisplayName("Scenario: Excluir o último provedor da curva")
void cenarioUltimoProvedorExcluido() {
    CurvaProvedor cp = new CurvaProvedor(1L, "DIxPRE", "B3", "TS", "PRE", 1);
    when(curvaRepositoryPort.findByNome("PRE")).thenReturn(Optional.of(curvaPre));
    when(curvaPrvdrRepositoryPort.findByIdAndNomeCurva(1L, "DIxPRE")).thenReturn(Optional.of(cp));

    service.excluir("PRE", 1L);

    verify(curvaPrvdrRepositoryPort).excluir(1L, "DIxPRE");
}
```

