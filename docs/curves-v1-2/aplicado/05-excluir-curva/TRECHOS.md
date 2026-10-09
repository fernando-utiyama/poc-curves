# Item 5: excluir curva de mercado, dentro do `CurvaMercadoService` (trechos para acrescentar)

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Não há service nem use case novos: a exclusão fica ao lado de criar, alterar, inativar e reativar. Não precisa de porta nova: a recusa vem do banco.

## 1. `CurvaMercadoUseCase`

```java
void excluir(String nome);
```

## 2. `CurvaMercdRepositoryPort` e `CurvaMercdPersistenceAdapter`

```java
// porta
void excluir(String nome);

// adaptador
@Override
public void excluir(String nome) {
    repository.deleteById(nome);   // o id da entidade é cTickerIndcd (o nome)
    repository.flush();            // a violação de chave estrangeira aparece aqui, não no commit
}
```

## 3. `CurvaMercadoService`

Copiar o `CurvaMercadoService.java` desta pasta por cima do seu (reescrito das suas fotos; não passou por compilação). O que muda:
- **`excluir`** novo. Apaga as configurações, os provedores e a curva; se o banco recusar por chave estrangeira (ainda há vértice construído ou dado bruto), o `DataIntegrityViolationException` vira 409 `CURVA_COM_HISTORICO` com a mensagem "A curva ainda tem dados vinculados (vértices construídos ou dado bruto dos provedores). Apague as datas construídas e o dado bruto antes, ou use a inativação". Tudo volta atrás (a transação desfaz). O construtor continua com 4 argumentos;
- **`listar`**: ordena por código e desempata pelo nome, porque o código é opcional e a paginação ficava instável;
- **`alterar`**: `Objects.equals(novoCodigo, atual.codigo())` no lugar de `novoCodigo.equals(...)`, que dava `NullPointerException` em curva sem código;
- **`inativar`**: sem o aviso `CURVA_COM_FILHAS` (consulta ao provedor `TCEN`), porque curva derivada não entra nesta versão;
- o resto (criar, consultar, reativar, validações, auditoria) está como nas suas fotos; os trechos repetidos viraram `provedoresDe`, `configuracaoVigenteDe` e `obterCurva`.

O `CurvaMercadoUseCase` precisa do `void excluir(String nome);`. A auditoria e o detalhe passam a levar `CurvaProvedor` e `ConfiguracaoCurva` direto (pasta `../11-sem-canonico/`).

## 4. `CadastroErrorCode` (antes de `ERRO_INTERNO`; mapear para 409 junto de `CODIGO_EM_USO`)

```java
CURVA_COM_HISTORICO("Curva com histórico"),
```

## 5. `CurvaMercadoController`

Copiar o `CurvaMercadoController.java` desta pasta por cima do seu (`adapter/in/api/rest/controller/`). Muda:
- `DELETE /{nome}` novo (item 5);
- `inativar`, `reativar` e `auditoria`: `{codigo}` vira `{nome}` e `@PathVariable String nome`;
- `auditoria` (item 8): sem `ResponseEntity<?>`. O JSON devolve `CurvaAuditoria` e a planilha é o método `auditoriaXlsx` (`params = "formato=xlsx"`); `formato` que não seja `json` nem `xlsx` continua dando 400.
- O resto (listar, consultar, criar, alterar) está como estava.

## 6. Testes: acrescentar no seu `CurvaMercadoServiceTest`

Sem métodos auxiliares: cada teste monta o que usa. Nomes do seu teste: `repositoryPort` (curva), `configuracaoRepositoryPort`, `curvaPrvdrRepositoryPort`, `eventosPort` e `service`. As versões são excluídas pelo `configuracaoRepositoryPort` (id `Long`) e a curva pelo `repositoryPort` (nome).

Imports que podem faltar: `org.springframework.dao.DataIntegrityViolationException`, `static org.mockito.Mockito.doThrow`, `never`, `verifyNoInteractions`, e de `domain.cadastro` os tipos de `ParametrosCalculo` (Alt+Enter).

```java
@Test
@DisplayName("Excluir: curva nunca construída sai com provedores e configurações")
void excluiCurvaSemHistorico() {
    LocalDateTime dUltAtulz = LocalDateTime.of(2026, 3, 30, 14, 30);
    CurvaMercado existente = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), dUltAtulz,
        LocalDate.of(2026, 3, 29), "ENGINE_SVC", null
    );
    ParametrosCalculo params = new ParametrosCalculo(
        BaseInterpolacao.Discount, DayCounter.Business252, Frequency.Annual,
        "Brazil", "Settlement", BusinessDayConvention.Following,
        Extrapolacao.Disabled, Extrapolacao.Disabled, "10Y", 4, ModoArredondamento.HALF_UP,
        null, null, null, Map.of());
    ConfiguracaoCurva versao1 = new ConfiguracaoCurva(1L, "DIxPRE", 1, "TAXA_SWAP_B3", "Linear", params, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));
    ConfiguracaoCurva versao2 = new ConfiguracaoCurva(2L, "DIxPRE", 2, "TAXA_SWAP_B3", "Linear", params, LocalDate.of(2026, 3, 1), null);
    CurvaProvedor provedorB3 = new CurvaProvedor(10L, "DIxPRE", "B3", "TS", "PRE", 1);

    when(repositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(existente));
    when(configuracaoRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of(versao1, versao2));
    when(curvaPrvdrRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of(provedorB3));

    service.excluir("DIxPRE");

    verify(configuracaoRepositoryPort).excluir(1L);
    verify(configuracaoRepositoryPort).excluir(2L);
    verify(curvaPrvdrRepositoryPort).excluir(10L, "DIxPRE");
    verify(repositoryPort).excluir("DIxPRE");
    verify(eventosPort).publicarCadastroAlterado(any());
}

@Test
@DisplayName("Excluir: o banco recusa (ainda há construído ou dado bruto) e o service responde CURVA_COM_HISTORICO")
void excluirRecusaCurvaComHistorico() {
    CurvaMercado existente = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 3, 30, 14, 30),
        LocalDate.of(2026, 3, 29), "ENGINE_SVC", null
    );
    when(repositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(existente));
    doThrow(new DataIntegrityViolationException("FK_tCurvaMercd_tDadoVertcCurva")).when(repositoryPort).excluir("DIxPRE");

    BusinessException ex = assertThrows(BusinessException.class, () -> service.excluir("DIxPRE"));

    assertEquals(CadastroErrorCode.CURVA_COM_HISTORICO.getCode(), ex.getErrorCode());
    verify(eventosPort, never()).publicarCadastroAlterado(any());
}

@Test
@DisplayName("Excluir: curva inexistente gera NAO_ENCONTRADO")
void excluirCurvaInexistente() {
    when(repositoryPort.findByNome("XXX")).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.excluir("XXX"));
    verifyNoInteractions(configuracaoRepositoryPort, eventosPort);
}
```

A ordem do `CurvaProvedor` usada é id, nome da curva, provedor, produto, ticker, prioridade; se o seu record tiver outra ordem, ajuste.

## 7. Curva derivada

Não existe na curves (confirmado em 09/10/2026), então a exclusão não checa "é componente de outra". Quando a derivada existir (provedor `TCEN`), entram o aviso no `inativar` e o 409 `CURVA_COMPONENTE` no `excluir`.
