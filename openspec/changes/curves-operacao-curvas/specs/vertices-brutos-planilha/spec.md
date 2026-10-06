## ADDED Requirements

### Requirement: Rotas da planilha de vértices brutos
Sobre as rotas `/dados-mercado` da change `curves-cadastro-curvas` (spec `vertices-brutos-provedor`), o serviço SHALL expor:

| Rota | Uso |
|---|---|
| `GET /dados-mercado/{provedor}/{codigo}/{dataBase}/planilha` | planilha `.xlsx` com os vértices da curva na data (vazia, só com o cabeçalho, se não houver) |
| `POST /dados-mercado/{provedor}/{codigo}/{dataBase}/planilha?modo=SIMULACAO\|APLICACAO&formato=` | importar a planilha (`multipart/form-data`, parte `arquivo`, até 10 MB) |

Toda importação em `APLICACAO` SHALL emitir o `CURVA_PRIMARIA_EDITADA` da spec `vertices-brutos-provedor`, com operação `PLANILHA`.

#### Scenario: Planilha baixada
- **WHEN** o gestor baixa a planilha da `PRE` de `2026-09-14` pela B3
- **THEN** recebe um `.xlsx` com a aba `Vertices`, uma linha por vértice gravado e a coluna `id`

### Requirement: Planilha de vértices brutos
A planilha SHALL ter uma aba `Vertices`, com as colunas `id` e os campos editáveis do provedor (requisito "Provedores e campos dos vértices brutos"), uma linha por vértice; números SHALL ser células numéricas, e célula vazia é valor nulo. A importação SHALL tratar a planilha como a lista completa da curva na data: linha com `id` existente é alteração (ou `SEM_MUDANCA`, se nada mudou), linha sem `id` é inclusão, e vértice gravado cujo `id` não aparece na planilha é exclusão. `id` de outra curva ou data é erro da linha.

Com `modo=SIMULACAO`, o serviço SHALL validar a planilha inteira e devolver, sem gravar nada, as mudanças por linha (`INCLUSAO`, `ALTERACAO`, `EXCLUSAO`, `SEM_MUDANCA`), os erros (linha, coluna, motivo) e os avisos que a lista resultante terá. Com `modo=APLICACAO`, SHALL aplicar todas as mudanças numa única transação, com a trava da curva; se houver qualquer erro, MUST NOT aplicar nada e SHALL responder 422 `DADOS_INVALIDOS` com os mesmos erros. Em qualquer modo, `formato=xlsx` SHALL devolver a planilha enviada com a coluna `Resultado` acrescentada; sem `formato`, a resposta é JSON. Arquivo que não é `.xlsx`, sem a aba `Vertices` ou sem as colunas do provedor responde 422 `DADOS_INVALIDOS`.

#### Scenario: Simular antes de aplicar
- **WHEN** o gestor importa em `SIMULACAO` a planilha da `PRE` de `2026-09-14` com um valor alterado e uma linha nova
- **THEN** a resposta lista uma `ALTERACAO`, uma `INCLUSAO` e as demais `SEM_MUDANCA`, e nada é gravado

#### Scenario: Erro numa linha
- **WHEN** a planilha aplicada tem uma linha com `valor` não numérico
- **THEN** a resposta é 422 com o erro da linha e da coluna `valor`, e nenhum vértice é gravado ou excluído
