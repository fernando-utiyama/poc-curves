# Limpeza do cadastro (CurvaMercadoService, ConfiguracaoCurvaService e use case)

Sem mudança de comportamento nem de rota: só tira código sobrando e repartido em métodos. Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Os 3 arquivos são inteiros, copiar por cima (partem das versões já aplicadas em `aplicado/12-sem-avisos/`).

| Arquivo | Onde |
|---|---|
| `CurvaMercadoService.java` | `application/service/` |
| `ConfiguracaoCurvaService.java` | `application/service/` |
| `CurvaMercadoUseCase.java` | `application/port/in/usecase/` |

## O que mudou

**`CurvaMercadoService`**
- **Sem `if (porta != null)`:** as três portas entram pelo construtor e nunca são nulas (`buscarProvedoresPorNomes`, `consultarAuditoria`, `excluir`, `provedoresDe`, `configuracaoVigenteDe` e o bloco do `alterar`).
- **Valores aceitos vêm dos enums:** `Arrays.toString(Unidade.values())` (e `DayCounterCotacao`, `CompoundingCotacao`) no lugar das listas escritas à mão nas mensagens.
- **`detalhar` agora é privado** e saiu o overload `listar` de 6 argumentos.
- **`alterar` mais curto:** a conferência "alterar a curva não invalida uma versão vigente ou futura" virou o método privado `validarCoerenciaComConfiguracoes`.

**`ConfiguracaoCurvaService`**
- Sem o `obterProvedores` (chama a porta direto, sem checar nulo).
- `criar` mais curto: as regras do início da nova versão viraram `validarInicioDaVersao` (mesmas mensagens).
- `validarCamposBasicos` sem repetição: `validarTexto(campo, valor, máximo, erros)` serve a `modeloConstrucao` e `interpolador`.

**`CurvaMercadoUseCase`**: sai o `default listar(...)` de 6 argumentos.

## Conferir

1. **Testes que montam o service com portas `null`**: sem os `if`, o `NullPointerException` aparece. Com `@Mock`/`@InjectMocks` não há problema.
2. **Quem chamava `listar` com 6 argumentos** (Alt+F7 no overload antes de colar): passa a usar o de 8, com `null, null` para provedor e dono.
3. **Teste de `alterar`** que confere a mensagem de valores aceitos: ela continua `Valores aceitos: [...]` com os mesmos nomes, na ordem do enum.
