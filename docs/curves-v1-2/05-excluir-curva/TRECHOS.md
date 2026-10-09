# Item 5: excluir curva de mercado, dentro do `CurvaMercadoService` (trechos para acrescentar)

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Não há service nem use case novos: a exclusão fica ao lado de criar, alterar, inativar e reativar. Requer `DadosConstruidosPort` e `LinhasPorTabela` (pasta `../compartilhado/`).

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
}
```

## 3. `CurvaMercadoService`

Campo novo, **depois de `eventosPort`** (o construtor do Lombok segue a ordem dos campos; testes que montam o service à mão ganham o 5º argumento):

```java
private final DadosConstruidosPort dadosConstruidosPort;
```

Import: `br.com.poc.application.port.out.DadosConstruidosPort` (`LinhasPorTabela` já vem de `domain.cadastro.*`).

Método novo, depois do `reativar`:

```java
@Override
@Transactional
public void excluir(String nome) {
    CurvaMercado curva = repositoryPort.findByNome(nome)
        .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nome + " não encontrada"));

    List<LinhasPorTabela> dependentes = dadosConstruidosPort.dependentes(curva.nome());
    if (!dependentes.isEmpty()) {
        Object[] detalhes = dependentes.stream()
            .map(d -> new Detalhe("nome", null, nome, d.linhas() + " linha(s) em " + d.tabela()
                + "; apague antes (construído pelo delete da data, dado bruto pelas rotas primaria-*) ou use a inativação"))
            .toArray();
        throw new BusinessException(CadastroErrorCode.CURVA_COM_HISTORICO, detalhes);
    }

    configuracaoRepositoryPort.findByNomeCurva(curva.nome())
        .forEach(c -> configuracaoRepositoryPort.excluir(c.id()));
    curvaPrvdrRepositoryPort.findByNomeCurva(curva.nome())
        .forEach(p -> curvaPrvdrRepositoryPort.excluir(p.idCurvaProvedor(), curva.nome()));
    repositoryPort.excluir(curva.nome());

    publicarEvento(curva.codigo(), curva.nome(), "EXCLUSAO", curva, null);
}
```

Conferir: a assinatura de `curvaPrvdrRepositoryPort.excluir(...)` (supus `excluir(idCurvaProvedor, nomeCurva)`) e o nome do getter do id do `CurvaProvedor`.

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

## 6. Teste

`CurvaMercadoServiceExcluirTest.java` (nesta pasta), teste novo; os 3 casos: exclui sem histórico, recusa com construído ou bruto, curva inexistente.

## 7. Curva derivada

Não existe na curves (confirmado em 09/10/2026), então a exclusão não checa "é componente de outra". O `inativar` já avisa `CURVA_COM_FILHAS` por meio do provedor `TCEN`; quando a derivada existir, o `excluir` pode usar a mesma consulta e responder 409 `CURVA_COMPONENTE`.
