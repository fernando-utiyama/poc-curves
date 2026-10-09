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

Os `...CanonicoState` continuam (saem no item 8). O `CurvaMercadoUseCase` precisa do `void excluir(String nome);`.

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

Não existe na curves (confirmado em 09/10/2026), então a exclusão não checa "é componente de outra". Quando a derivada existir (provedor `TCEN`), entram o aviso no `inativar` e o 409 `CURVA_COMPONENTE` no `excluir`.
