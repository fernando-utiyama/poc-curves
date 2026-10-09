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

Copiar o `CurvaMercadoService.java` desta pasta por cima do seu (reescrito das suas fotos; não passou por compilação). O que muda:
- **`excluir`** novo, e o campo `dadosConstruidosPort` depois de `eventosPort` (o 5º argumento do construtor do Lombok: testes que montam o service à mão ganham esse argumento);
- **`listar`**: a ordenação ganha o nome como desempate (`tickerIdtfdUnic` e depois `tickerIndcd`), porque o código é opcional e a paginação ficava instável com várias curvas sem código;
- **`alterar`**: `Objects.equals(novoCodigo, atual.codigo())` no lugar de `novoCodigo.equals(...)`, que dava `NullPointerException` em curva legada sem código;
- o resto (criar, consultar, inativar, reativar, validações, auditoria) está como nas suas fotos; os trechos repetidos de provedores e configuração vigente viraram métodos privados (`provedoresDe`, `configuracaoVigenteDe`) e a busca por nome virou `obterCurva`.

Os `...CanonicoState` continuam (sai no item 8); a conferência final é compilar, porque o `CurvaMercadoUseCase` precisa do `void excluir(String nome);`.

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
