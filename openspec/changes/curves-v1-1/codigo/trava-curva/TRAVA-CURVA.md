# Trava da curva nas versões de configuração

Evita versão duplicada quando duas pessoas criam ou excluem versão da mesma curva ao mesmo tempo: a segunda espera a primeira terminar.

O `ConfiguracaoCurvaService.java` desta pasta (um nível acima) já chama `curvaRepositoryPort.travarPorNome(curva.nome())` no `criar` e no `excluir`. Nos três arquivos abaixo, **acrescente só o método** (não substitua o arquivo):

## 1. `CurvaMercdRepositoryPort` (application/port/out)

```java
void travarPorNome(String nome);
```

## 2. `CurvaMercdRepository` (adapter/out/persistence/repository) — o repositório JPA que o adaptador usa

```java
@Query(value = "SELECT cTickerIndcd FROM dbo.tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = :nome",
       nativeQuery = true)
@QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "60000"))
String travarPorNome(@Param("nome") String nome);
```

Imports: `org.springframework.data.jpa.repository.Query`, `org.springframework.data.jpa.repository.QueryHints`, `jakarta.persistence.QueryHint`, `org.springframework.data.repository.query.Param` (os que faltarem).

## 3. `CurvaMercdPersistenceAdapter` (adapter/out/persistence)

```java
@Override
public void travarPorNome(String nome) {
    repository.travarPorNome(nome);   // use o nome do campo do repositório que o adaptador já tem
}
```

## Testes

Nada muda: o método é `void` e o mock ignora a chamada. Só se algum teste do serviço usar `verifyNoMoreInteractions(curvaRepositoryPort)`, acrescente antes `verify(curvaRepositoryPort).travarPorNome(any());`.
