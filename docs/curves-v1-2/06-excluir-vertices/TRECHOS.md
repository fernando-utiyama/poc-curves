# Item 6: apagar a curva construída da data, dentro do `CurvaMercadoAcoesService`

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Requer a porta `DadoVertcCurvaRepositoryPort` e o adaptador (pasta `../compartilhado/`).

| Arquivo | O que muda |
|---|---|
| `CurvaMercadoAcoesService.java` | **substitui** o seu: ganha `excluirVertices(nome, dataBase)` e dois campos (`dadoVertcCurvaRepositoryPort`, `eventosPort`); as ações do engine recebem o nome e repassam `curva.codigo()` (curva sem código → 422) |
| `CurvaMercadoAcoesController.java` | **substitui** o seu: `{nome}` nas rotas e o `DELETE /vertices` chamando `service.excluirVertices` (200 sem corpo) |

## Testes: acrescentar no seu `CurvaMercadoAcoesServiceTest`

Mocks novos no teste (o construtor ganha dois argumentos, nesta ordem: `curvas`, `engine`, `dadoVertcCurvaRepositoryPort`, `eventosPort`):

```java
@Mock
private DadoVertcCurvaRepositoryPort dadoVertcCurvaRepositoryPort;

@Mock
private EventosPort eventosPort;
```

```java
@Test
@DisplayName("Apagar construção: apaga vértices e interpolada da data e publica o evento")
void apagaConstrucaoDaData() {
    CurvaMercado curva = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
        null, null, null
    );
    LocalDate data = LocalDate.of(2026, 9, 14);
    when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
    when(dadoVertcCurvaRepositoryPort.excluirPorNomeCurvaEDataBase("DIxPRE", data)).thenReturn(true);

    service.excluirVertices("DIxPRE", data);

    verify(dadoVertcCurvaRepositoryPort).apagar("DIxPRE", data);
    verify(eventosPort).publicarCadastroAlterado(any());
}

@Test
@DisplayName("Apagar construção: data sem nada construído gera NAO_ENCONTRADO e não publica evento")
void apagarDataSemConstrucao() {
    CurvaMercado curva = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
        null, null, null
    );
    LocalDate data = LocalDate.of(2026, 9, 14);
    when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
    when(dadoVertcCurvaRepositoryPort.excluirPorNomeCurvaEDataBase("DIxPRE", data)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> service.excluirVertices("DIxPRE", data));
    verify(eventosPort, never()).publicarCadastroAlterado(any());
}

@Test
@DisplayName("Apagar construção: curva inexistente gera NAO_ENCONTRADO sem tocar nos dados")
void apagarCurvaInexistente() {
    when(curvaRepositoryPort.findByNome("XXX")).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.excluirVertices("XXX", LocalDate.of(2026, 9, 14)));
    verifyNoInteractions(dadoVertcCurvaRepositoryPort, eventosPort);
}
```

Use os nomes de mock que já estão no seu teste (o da curva pode se chamar `curvas` ou `curvaRepositoryPort`). Os testes das ações do engine que faziam stub de `existsByCodigo(...)` passam a fazer `findByNome("DIxPRE")` devolvendo uma curva com código.
