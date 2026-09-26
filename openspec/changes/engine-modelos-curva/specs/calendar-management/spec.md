## Purpose

Permite manter os feriados de um calendário sem deploy: importar uma planilha com a lista de feriados, que o engine converte num script Groovy de calendário versionado, e exportar os feriados de qualquer calendário (nativo ou Groovy) no mesmo formato da importação, para conferir, corrigir e reimportar.

## ADDED Requirements

### Requirement: Calendário por lista
O engine SHALL ter uma base nativa `CalendarioPorLista`, que implementa o contrato de calendário a partir de: nome, mercado, ano inicial e ano final de cobertura e um conjunto de datas de feriado. Sábado e domingo SHALL ser sempre dias não úteis. Uma data fora da cobertura MUST resultar em `MODELO_FALHOU`, informando o calendário, a versão, a data e a cobertura; o calendário MUST NOT supor dia útil fora dela. Pedir o calendário com um mercado diferente do seu MUST resultar em `CADASTRO_INVALIDO`.

#### Scenario: Data fora da cobertura
- **WHEN** um calendário importado cobre de 2001 a 2078, e uma construção precisa contar dias úteis até 2080
- **THEN** a construção falha com `MODELO_FALHOU`, informando a data e a cobertura 2001–2078

### Requirement: Importação da planilha de feriados
`POST /api/v1/calendarios/{nome}/importacao?mercado=&anoInicial=&anoFinal=` SHALL receber um arquivo `.xlsx` (multipart, campo `arquivo`) e exigir o papel `Curvas.ModelosAutor`. A planilha SHALL ter a aba `Feriados` com o cabeçalho `Data` e `Descricao` na primeira linha e uma linha por feriado. A importação MUST ser rejeitada com 400 `PARAMETRO_INVALIDO`, listando cada problema em `detalhes`, quando:
- faltar a aba ou uma das colunas;
- uma célula de `Data` não for data;
- houver datas repetidas;
- uma data estiver fora de `anoInicial`..`anoFinal`;
- `anoFinal` for menor que `anoInicial`, ou a cobertura passar de 150 anos.

Aceita a planilha, o engine SHALL gerar o script Groovy de calendário: uma subclasse de `CalendarioPorLista` com o nome, o mercado, a cobertura e as datas em ordem crescente, por um modelo de texto fixo, de modo que a mesma planilha gere sempre o mesmo script e o mesmo hash. O script SHALL ser gravado como a próxima versão, em `RASCUNHO`, do tipo `calendario` e do nome `{nome}`, junto com a planilha original em `groovy-models/calendario/{nome}/v{versao}.xlsx`. Dali em diante, a versão segue o fluxo normal de validação e ativação. A resposta SHALL trazer nome, mercado, versão, hash, cobertura e quantidade de feriados.

#### Scenario: Feriado decretado
- **WHEN** o operador exporta o `Brazil`/`Settlement`, acrescenta um feriado decretado para a semana seguinte, importa a planilha, valida e ativa a versão
- **THEN** as construções seguintes contam esse dia como não útil, e a proveniência mostra `Brazil` com origem `GROOVY` e a versão importada

#### Scenario: Data repetida
- **WHEN** a planilha tem `2026-12-25` duas vezes
- **THEN** a importação responde 400 citando a data repetida, e nenhuma versão é criada

### Requirement: Validação do calendário importado
Além da validação comum de calendário, a validação de uma versão gerada por importação SHALL conferir que, para todo dia da cobertura, o dia é útil se e somente se não for sábado, domingo nem data da lista.

#### Scenario: Script gerado confere com a planilha
- **WHEN** a versão gerada de uma planilha com 1.000 feriados é validada
- **THEN** a validação percorre toda a cobertura e aprova a versão

### Requirement: Exportação dos feriados
`GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&versao=&formato=` SHALL exigir o papel `Curvas.Leitura` e devolver os feriados do calendário no intervalo: os dias de segunda a sexta que não são úteis. Sem `versao`, o calendário SHALL ser resolvido como numa construção (versão ativa ou nativo). Com `versao`, SHALL usar aquela versão. Em `formato=xlsx`, a planilha SHALL ter a aba `Feriados` no mesmo formato da importação e uma aba `Resumo` com nome, mercado, origem, versão, hash, `estadoScript` e cobertura. Em `formato=json`, o mesmo conteúdo. O intervalo MUST ter no máximo 150 anos, e, para calendário por lista, estar dentro da cobertura; senão, 400 `PARAMETRO_INVALIDO`.

#### Scenario: Ida e volta
- **WHEN** o `Brazil`/`Settlement` nativo é exportado de 2001 a 2100 e a planilha é importada sem alteração com a mesma cobertura
- **THEN** a versão gerada considera úteis exatamente os mesmos dias que o nativo, em toda a cobertura
