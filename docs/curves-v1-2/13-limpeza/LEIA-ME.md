# Limpeza do cadastro (CurvaMercadoService, ConfiguracaoCurvaService e use case)

Sem mudança de comportamento nem de rota: só tira código sobrando e repartido em métodos. Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Os 7 arquivos são inteiros, copiar por cima (partem das versões já aplicadas em `aplicado/12-sem-avisos/`).

| Arquivo | Onde |
|---|---|
| `CurvaMercadoService.java` | `application/service/` |
| `ConfiguracaoCurvaService.java` | `application/service/` |
| `CurvaMercadoUseCase.java` | `application/port/in/usecase/` |
| `CurvaMercado.java` | `domain/cadastro/` |
| `CurvaProvedorService.java` | `application/service/` |
| `CurvaPorOrigem.java` | `domain/cadastro/` |
| `CurvaProvedorUseCase.java` | `application/port/in/usecase/` (deduzido do service: confira os imports dos inputs) |

## O que mudou

**`CurvaMercadoService`**
- **Sem `if (porta != null)`:** as três portas entram pelo construtor e nunca são nulas (`buscarProvedoresPorNomes`, `consultarAuditoria`, `excluir`, `provedoresDe`, `configuracaoVigenteDe` e o bloco do `alterar`).
- **Valores aceitos vêm dos enums:** `Arrays.toString(Unidade.values())` (e `DayCounterCotacao`, `CompoundingCotacao`) no lugar das listas escritas à mão nas mensagens.
- **`detalhar` agora é privado** e saiu o overload `listar` de 6 argumentos.
- **`inativar` e `reativar` em 3 linhas:** usam o novo `CurvaMercado.comSituacao(situacao, atualizadoEm)` em vez de copiar os 18 argumentos do construtor. **Pequena mudança de comportamento:** o `reativar` passa a devolver o detalhe completo (provedores e configuração vigente), como o `inativar` já fazia; antes devolvia o detalhe vazio.
- **`alterar` mais curto:** a conferência "alterar a curva não invalida uma versão vigente ou futura" virou o método privado `validarCoerenciaComConfiguracoes`.

**`ConfiguracaoCurvaService`**
- Sem o `obterProvedores` (chama a porta direto, sem checar nulo).
- `criar` mais curto: as regras do início da nova versão viraram `validarInicioDaVersao` (mesmas mensagens).
- `validarCamposBasicos` sem repetição: `validarTexto(campo, valor, máximo, erros)` serve a `modeloConstrucao` e `interpolador`.

**`CurvaMercado`** (record): ganha `comSituacao`; o resto é o que você já tem (construtor de 17 argumentos sem dono e `normalizarNome` iguais).

**`CurvaMercadoUseCase`**: sai o `default listar(...)` de 6 argumentos.

## Conferir

1. **Testes que montam o service com portas `null`**: sem os `if`, o `NullPointerException` aparece. Com `@Mock`/`@InjectMocks` não há problema.
2. **Quem chamava `listar` com 6 argumentos** (Alt+F7 no overload antes de colar): passa a usar o de 8, com `null, null` para provedor e dono.
3. **Teste de `alterar`** que confere a mensagem de valores aceitos: ela continua `Valores aceitos: [...]` com os mesmos nomes, na ordem do enum.

## `CurvaMercdPersistenceAdapter.listar` (à mão, sem mudar a assinatura)

Troque o `listar` inteiro por este (cada filtro vira uma `Specification` pequena que devolve `null` quando o parâmetro não vem, e o `allOf` ignora os nulos):

```java
@Override
public Page<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao,
                                 String provedor, String dono, Pageable pageable) {
    Specification<CurvaMercdEntity> filtro = Specification.allOf(
        igual("tickerIdtfdUnic", codigo),
        igual("tpoVlr", unidade),
        igual("sitReg", situacao),
        contem("tickerIndcd", nome),
        contem("pprioDado", dono),
        comProvedor(provedor));
    return repository.findAll(filtro, pageable).map(this::toDomain);
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

Imports: `org.springframework.data.jpa.domain.Specification`, `jakarta.persistence.criteria.Subquery` e `java.util.Locale`; saem `Predicate`, `Root`, `CriteriaQuery`, `CriteriaBuilder` e `ArrayList`, se ficarem sem uso. Mudança única de comportamento: `toLowerCase(Locale.ROOT)` (sem isso, num servidor com locale turco, "I" vira "ı" e o filtro erra).

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

**Hexagonal (feito)**
- O service não importa mais `adapter.out` nem `adapter.in`: o `SpringDataProvedorRepository` virou o `ProvedorRepositoryPort` (já existe, com `existsById`; troque só o tipo e o nome do campo no construtor do `CurvaProvedorServiceTest`).
- Novo record de domínio `CurvaPorOrigem(codigo, nome, prioridade)` (em `domain/cadastro/`) no lugar do `CurvaProvedorCurvaResponse`. O use case devolve `List<CurvaPorOrigem>`.
- **À mão no controller:** no endpoint de listar por origem, `.map(c -> new CurvaProvedorCurvaResponse(c.codigo(), c.nome(), c.prioridade()))` (ou `fromDomain` no response, se preferir). Se a ordem dos campos do seu response for outra, ajuste. O tipo de `prioridade` é `Integer`, como está em `CurvaProvedor`.
