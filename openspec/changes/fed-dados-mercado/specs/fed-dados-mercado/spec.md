## Purpose

No front `web/fed`, a tela "Dados de mercado" para listar e manter os vértices brutos de B3, ANBIMA e Bloomberg pelos CRUDs que já existem na `services/curves`, e enviar o arquivo original de uma fonte quando o download falha.

## ADDED Requirements

### Requirement: Listagem do bruto por provedor
A tela `/dados-mercado`, no menu do cabeçalho como "Dados de mercado", SHALL ter os filtros:
- **Provedor:** `B3`, `ANBIMA` ou `Bloomberg`, padrão `B3`;
- **Período:** data inicial e final (`dd/mm/aaaa`), padrão os últimos 30 dias até hoje em Brasília;
- **Curva:** trecho do código ou do nome (opcional).

"Consultar" SHALL chamar a listagem geral do provedor na curves (`GET /api/v1/curvas-mercado/primaria-b3`, `/primaria-anbima` ou `/primaria-bloomberg`, com `de`, `ate`, `codigo`, `nome`, `pagina` e `tamanho`) e mostrar uma linha por curva e data-base com bruto: código, nome, data-base, quantidade de vértices, códigos na fonte e se a curva já está construída, paginada de 50 em 50. Trocar de provedor SHALL limpar a listagem e os vértices abertos.

#### Scenario: Bruto da B3 no período
- **WHEN** o gestor escolhe `B3`, o período de `01/09/2026` a `30/09/2026` e clica em "Consultar"
- **THEN** a tabela mostra uma linha por curva e data-base com bruto da B3 no período, inclusive a `PRE` de `14/09/2026` com 278 vértices

#### Scenario: Troca de provedor
- **WHEN** o gestor troca o provedor de `B3` para `ANBIMA`
- **THEN** a listagem e os vértices abertos são limpos até a próxima consulta

### Requirement: Vértices de uma curva numa data
Clicar numa linha da listagem SHALL abrir os vértices daquela curva naquela data-base (`GET /api/v1/curvas-mercado/{nome}/primaria-{provedor}/{dataBase}`), numa tabela com os campos do provedor como a curves os devolve e se a curva já está construída.

#### Scenario: Vértices da PRE
- **WHEN** o gestor clica na linha da `PRE` de `14/09/2026` da B3
- **THEN** a tela mostra os 278 vértices com data do vértice, dias corridos, dias úteis e valor

### Requirement: Manutenção dos vértices
Na tabela de vértices, a tela SHALL oferecer "Incluir vértice", "Editar" e "Excluir" por linha, e "Excluir todos da data", por modais, com os campos de entrada do provedor, pelas rotas do CRUD do provedor (`POST .../vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}` e `DELETE /curvas-mercado/{nome}/primaria-{provedor}/{dataBase}`). As exclusões SHALL pedir confirmação. Depois de cada gravação, a tela SHALL recarregar os vértices e a linha da listagem; em erro, o modal SHALL continuar aberto com a mensagem da API. "Excluir todos da data" com a data já construída responde 409 com a mensagem explicada (apagar antes a curva construída na tela Curvas). Com a curva já construída, a tela SHALL lembrar que a correção só vale num recálculo e oferecer o link "Recalcular a curva" para a tela Curvas, `/curvas?nome={nome}`.

#### Scenario: Correção de um valor já construído
- **WHEN** o gestor altera um valor da `PRE` de `14/09/2026`, que já está construída
- **THEN** a tela mostra o link "Recalcular a curva", que abre `/curvas?nome=PRE`

#### Scenario: Campo inválido
- **WHEN** o gestor salva um vértice da B3 sem dias úteis e a curves responde 422
- **THEN** o modal continua aberto, com a mensagem da API

### Requirement: Envio do arquivo da fonte
A tela SHALL ter o bloco "Enviar arquivo da fonte", com o provedor (`B3`, `ANBIMA` ou `Bloomberg`) e o arquivo, sem campo de data: a data-base vem do conteúdo do arquivo. "Enviar" SHALL chamar `POST /api/v1/cargas/upload` do bff (`multipart/form-data` com `fonte` e `arquivo`, capability `upload-carga-bff` da change `processor-v0`). Antes do envio, a tela SHALL avisar que um arquivo da mesma data substitui os vértices brutos já gravados daquela fonte. Com sucesso, a tela SHALL mostrar a data-base lida (`dd/mm/aaaa`), o identificador da carga e os vértices gravados por código, e posicionar os filtros nesse provedor, com o período terminando nessa data. Em erro, SHALL mostrar a mensagem e o código devolvidos (por exemplo, `ARQUIVO_INVALIDO`, `PROVEDOR_NAO_IMPLEMENTADO`), sem a página de erro global. O botão fica desabilitado durante o envio.

#### Scenario: TaxaSwap enviado
- **WHEN** o gestor envia o `TaxaSwap.txt` de `2026-09-14` com o provedor `B3`
- **THEN** a tela mostra "Data-base: 14/09/2026", o `idCarga` e os vértices por código, e os filtros passam a `B3` com o período até `14/09/2026`

#### Scenario: Arquivo rejeitado
- **WHEN** o processor responde 422 `ARQUIVO_INVALIDO`
- **THEN** a tela mostra a mensagem e o código, e nada muda nos filtros

### Requirement: Tempo de espera e corpo de arquivo
O envio do arquivo da fonte SHALL esperar até 130 segundos; as demais chamadas mantêm o tempo limite padrão de 3 segundos do `fed`. Requisições com arquivo MUST NOT receber `Content-Type: application/json`: o navegador define o `multipart/form-data`.

#### Scenario: Envio do arquivo da Bloomberg
- **WHEN** o envio leva 50 segundos para responder
- **THEN** o front espera e mostra o resultado, sem erro de tempo esgotado

### Requirement: Textos e formatos
Todos os textos SHALL estar em pt-BR, com acentuação. Datas SHALL ser mostradas em `dd/mm/aaaa` e enviadas em `AAAA-MM-DD`. Decimais SHALL ser mostrados com vírgula decimal, sem fazer conta no front e sem perder as casas que vierem (a B3 devolve texto; ANBIMA e Bloomberg podem devolver número), e digitados com vírgula ou ponto, enviados com ponto.

#### Scenario: Taxa da NTN-B
- **WHEN** a API devolve a taxa `5.5415`
- **THEN** a tabela mostra `5,5415`
