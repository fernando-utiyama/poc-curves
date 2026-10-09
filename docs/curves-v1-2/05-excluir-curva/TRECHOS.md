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
CURVA_COMPONENTE("Curva é componente de outra curva"),
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
    description = "Exclui a curva com provedores e configurações. Recusa curva já construída (use a inativação) "
        + "e curva que é componente de outra. Não apaga o dado bruto dos provedores")
public ResponseEntity<Void> excluir(@PathVariable String nome) {
    excluirUseCase.excluir(nome);
    return ResponseEntity.noContent().build();
}
```

Imports: `br.com.poc.application.port.in.usecase.ExcluirCurvaMercadoUseCase` (os de `DeleteMapping`/`PathVariable` já vêm do `org.springframework.web.bind.annotation.*`).

## 5. Conferir amanhã (08/10/2026)

Curva derivada provavelmente **ainda não existe** na curves. A regra "é componente de outra" (`buscarCurvasProvedor("TCEN", null, codigo)` no `ExcluirCurvaMercadoService`) supõe que a derivada guarda, em `tCurvaPrvdr`, o provedor `TCEN` com o código da curva componente em `cCodigoNaFonte`. Conferir se isso existe:
- se **não existir**: a consulta devolve sempre vazio e nunca bloqueia, o que é inofensivo. Pode ficar como está, ou tirar o bloqueio, o `CURVA_COMPONENTE` e o teste `recusaCurvaComponente` até a derivada existir;
- se **existir** de outro jeito: me mande como é gravada que eu ajusto a consulta.
