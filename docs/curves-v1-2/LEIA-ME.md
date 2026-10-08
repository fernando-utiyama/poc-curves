# Curves v1.2: itens 5, 6 e 7 (excluir curva, apagar construção da data e excluir versão de configuração)

Pacotes estão como `br.com.poc`: troque por `br.com.bradesco`. **Sem trava por nome** (`travarPorNome` não é usado em nenhum arquivo daqui; entra depois, junto com a trava das versões).

## Ordem

1. `compartilhado/` (usado pelos itens 5, 6 e 7), em:
   - `ResumoConstrucao.java`, `ConstrucaoApagada.java`, `LinhasPorTabela.java` → `domain/cadastro/`
   - `DadosConstruidosPort.java` → `application/port/out/`
   - `DadosConstruidosPersistenceAdapter.java` → `adapter/out/persistence/`
2. Item 7, `07-excluir-versao/`:
   - `ConfiguracaoCurva.java` → `domain/cadastro/` (substitui; só ganha `comInicioVigencia`)
   - `ConfiguracaoCurvaService.java` → `application/service/` (substitui)
   - `ConfiguracaoCurvaController.java` → `adapter/in/api/rest/controller/` (substitui)
   - `ConfiguracaoCurvaServiceTest.java` → teste do serviço (substitui)
3. Item 5, `05-excluir-curva/`: os três arquivos `.java` (use case e service em `application/`, teste) e os trechos de `TRECHOS.md` (porta e adaptador da curva, códigos de erro, método no controller).
4. Item 6, `06-apagar-construcao/`:
   - `ApagarConstrucaoResponse.java` → `adapter/in/api/rest/dto/`
   - `ApagarConstrucaoService.java` → `application/service/`
   - `CurvaMercadoAcoesController.java` → `adapter/in/api/rest/controller/` (substitui; ganha só o `DELETE /vertices` e o campo `apagarConstrucaoService`)
   - `ApagarConstrucaoServiceTest.java` → teste novo

5. Correção da busca por código, `08-corrigir-busca-codigo/CurvaMercdRepository.java` → `adapter/out/persistence/repository/` (substitui; os métodos por código e nome ganham `@Query`, sem mudar as assinaturas).

## Mexer à mão

1. **`CadastroErrorCode`**: acrescentar `VERSAO_EM_USO("Versão em uso por curva construída"),` (item 7) e os dois de `05-excluir-curva/TRECHOS.md` (item 5) antes de `ERRO_INTERNO`. Para dar 409 como os demais conflitos, veja onde o tratador mapeia `CODIGO_EM_USO` e coloque `VERSAO_EM_USO` ao lado; se não achar, mande a foto do tratador.
2. **`CodigoAvisoCurva`**: conferir se existe `SEM_CONFIGURACAO` (a spec de vértices já o usa). Se não existir, acrescentar.
3. **`ConfiguracaoCurvaUseCase`**: trocar a assinatura para `ConfiguracaoCurvaResultado excluir(String codigoCurva, Integer versao);` (era `int versao`).
4. **Quem constrói `ConfiguracaoCurvaService` à mão** (outros testes): acrescentar o último argumento, um `DadosConstruidosPort` (mock).

## O que muda no comportamento

- `DELETE /curvas-mercado/{codigo}/configuracoes?versao=N` substitui `DELETE .../configuracoes/{versao}`. Sem `versao`, exclui a vigente hoje. **O front precisa trocar a chamada.**
- Qualquer versão pode ser excluída, desde que não haja curva construída na vigência dela (senão 409 `VERSAO_EM_USO`). A vizinha cobre a vigência da excluída; excluir a única devolve o aviso `SEM_CONFIGURACAO`.
- Nova rota `DELETE /curvas-mercado/{codigo}`: só exclui a curva sem linhas dela nas tabelas dependentes (construído e dado bruto); senão 409 `CURVA_COM_HISTORICO` com as linhas por tabela.
- Nova rota `DELETE /curvas-mercado/{codigo}/{dataBase}/vertices`: apaga os vértices construídos e a interpolada da data, numa transação só; data não construída dá 404.

## Dado bruto (item 1 do backlog), nos 3 controllers

Em `BbergCurvaPrimrController`, `AnbmaCurvaPrimrController` e `BtrsCurvaPrimrController`, trocar

```java
@RequestMapping("/api/v1/curvas-mercado")
```

por

```java
@RequestMapping(value = "/api/v1/curvas-mercado", produces = MediaType.APPLICATION_JSON_VALUE)
```

e importar `org.springframework.http.MediaType`. O Swagger passa a mostrar `application/json`.

Os itens 2, 3 e 4 do dado bruto precisam do use case, do service e do repositório de uma das fontes (a B3, por exemplo).
