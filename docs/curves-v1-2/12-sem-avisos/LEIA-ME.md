# Sem avisos (detalhe da curva e configuração)

Só o que muda agora. Pacotes como `br.com.poc`: troque por `br.com.bradesco`. O front por enquanto só mostra o texto do erro: sucesso é `200` simples, e o erro vem com mensagem explicada. Os avisos não eram necessários para o fluxo.

## Arquivos (7, todos inteiros, copiar por cima)

| Arquivo | Onde |
|---|---|
| `CurvaMercadoDetalhada.java` | `domain/cadastro/` |
| `CurvaMercadoDetalhadaResponse.java` | `adapter/in/api/rest/dto/` |
| `CurvaMercadoService.java` | `application/service/` |
| `ConfiguracaoCurvaService.java` | `application/service/` |
| `ConfiguracaoCurvaController.java` | `adapter/in/api/rest/controller/` |
| `ConfiguracaoCurvaUseCase.java` | `application/port/in/usecase/` |
| `ConfiguracaoCurvaServiceTest.java` | `src/test/java/.../application/service/` |

## O que muda

**Detalhe da curva**
- `CurvaMercadoDetalhada` perde o campo `avisos` (só o aviso `CURVA_COM_FILHAS` do `inativar` o preenchia, e ele saiu): ficam `curva`, `provedores` e `configuracaoVigente`.
- `CurvaMercadoDetalhadaResponse` e o JSON do detalhe não trazem mais `avisos: []`.
- `CurvaMercadoService`: as 5 chamadas a `new CurvaMercadoDetalhada(...)` perdem o último argumento.

**Configuração da curva**
- `validar` → `200` vazio (ou o erro explicado); antes devolvia a lista de avisos.
- `criar` → `201` com só a versão criada (`ConfiguracaoCurvaResponse`); antes vinha com os avisos.
- `excluir` → `200` vazio (já era).

## À mão

0. **Antes de tudo**, na `DadoVertcCurvaRepositoryPort`: Shift+F6 em `existeConstrucao` → `existeVerticePorNomeCurvaEPeriodo` (o adaptador e os stubs do teste acompanham). Os arquivos desta pasta já usam o nome novo; sem isso, o `ConfiguracaoCurvaService` e o teste ficam em vermelho.
1. **`ConfiguracaoCurvaUseCase`**: já vem inteiro nesta pasta (`validar` vira `void`, `criar` devolve `ConfiguracaoCurva`, `excluir` recebe `Integer versao`).
2. **Apagar**: `ConfiguracaoCurvaResultado` e `ConfiguracaoCurvaComAvisosResponse`.
3. **Testes** que montam `new CurvaMercadoDetalhada(a, b, c, d)` (4 argumentos): Ctrl+F6 (Change Signature) no construtor do record, ou Alt+Enter em cada um.
4. **Front**: o detalhe da curva não devolve mais `avisos`, e `criar` configuração não devolve `{ configuracao, avisos }`, só a configuração. Confira se algum lugar lê esses campos.
5. **`CurvaMercadoResponse.fromDomain(curva, avisos)`**: recebe `List.of()` do detalhe. Se o `avisos` dele não tiver mais uso, tire junto (Alt+F7).
6. **Opcional, limpeza do validador**: os avisos do `ValidadorParametros` (`MODELO_NAO_NATIVO`, `ORIGEM_INCOMPATIVEL_COM_MODELO`, `MODELO_POR_ORIGEM_SEM_PROVEDOR`) ficaram sem quem os leia; `ValidacaoResultado.avisos()` e os testes que os conferem podem sair.
