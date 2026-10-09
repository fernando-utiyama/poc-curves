# Item 6: apagar a curva construída da data, dentro do `CurvaMercadoAcoesService`

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Requer a porta `DadoVertcCurvaRepositoryPort` e o adaptador (pasta `../compartilhado/`).

| Arquivo | O que muda |
|---|---|
| `CurvaMercadoAcoesService.java` | **substitui** o seu: ganha `excluirVertices(nome, dataBase)` e dois campos (`dadoVertcCurvaRepositoryPort`, `eventosPort`); as ações do engine recebem o nome e repassam `curva.codigo()` (curva sem código → 422) |
| `CurvaMercadoAcoesController.java` | **substitui** o seu: `{nome}` nas rotas e o `DELETE /vertices` chamando `service.excluirVertices` (200 sem corpo) |

## Teste: `CurvaMercadoAcoesServiceTest.java` (substitui o seu)

O teste inteiro está nesta pasta. O que mudou em relação ao seu:
- **A curva é montada uma vez**, no `@BeforeEach` (`curva`, com código `PRE` e nome `DIxPRE`), e cada teste só faz `when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva))`. Os stubs antigos de `existsByCodigo(...)` saem: o service agora busca a curva pelo nome, para repassar o código ao engine. Era isso que fazia os 11 testes falharem com `Curva PRE não encontrada`.
- **O nome (`DIxPRE`) e o código (`PRE`) são campos separados:** as chamadas ao service passam o nome, e as chamadas ao `enginePort` esperam o código.
- **Teste novo:** curva sem código gera 422 `DADOS_INVALIDOS` e não chama o engine.
- **Os 3 testes de excluir vértices** ficam no mesmo arquivo, com o prefixo "Excluir vértices" no nome.
- O `@InjectMocks` monta o service pelos 4 mocks (`repositoryPort`, `dadoVertcCurvaRepositoryPort`, `eventosPort`, `enginePort`).
