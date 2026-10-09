# Item 5: trechos para acrescentar (não substituem arquivo)

## 1. `CurvaMercdRepositoryPort` (`application/port/out`)

```java
void excluir(String nome);
```

## 2. `CurvaMercdPersistenceAdapter` (`adapter/out/persistence`)

```java
@Override
public void excluir(String nome) {
    repository.deleteById(nome);   // o id da entidade é cTickerIndcd (o adaptador já usa existsById com ele)
}
```

## 3. `CadastroErrorCode` (acrescentar antes de `ERRO_INTERNO`; mapear para 409 junto de `CODIGO_EM_USO`)

```java
CURVA_COM_HISTORICO("Curva com histórico"),
```

## 4. `CurvaMercadoController` (`adapter/in/api/rest/controller`)

Campo novo, ao lado de `useCase`:

```java
private final ExcluirCurvaMercadoUseCase excluirUseCase;
```

Método novo, depois do `reativar`:

```java
@DeleteMapping("/{nome}")
@Operation(summary = "Excluir curva de mercado",
    description = "Exclui a curva com provedores e configurações. Recusa curva com construído ou dado bruto "
        + "(use a inativação). Não apaga o dado bruto dos provedores")
public ResponseEntity<Void> excluir(@PathVariable String nome) {
    excluirUseCase.excluir(nome);
    return ResponseEntity.noContent().build();
}
```

Imports: `br.com.poc.application.port.in.usecase.ExcluirCurvaMercadoUseCase` (os de `DeleteMapping`/`PathVariable` já vêm do `org.springframework.web.bind.annotation.*`).

## 5. Curva derivada

Não existe na curves (confirmado em 09/10/2026), então não há bloqueio "é componente de outra". Quando a derivada existir (provedor `TCEN` em `tCurvaPrvdr`), acrescentar o 409 `CURVA_COMPONENTE`.
