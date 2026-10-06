## Purpose

No front `web/fed`, a tela "Dados de mercado" para consultar e manter os vértices brutos de B3, ANBIMA e Bloomberg por provedor, ticker e data e enviar o arquivo original de uma fonte quando o download falha.

## ADDED Requirements

### Requirement: Filtros da tela
A tela `/dados-mercado`, no menu do cabeçalho como "Dados de mercado", SHALL ter os filtros:
- **Provedor:** `B3`, `ANBIMA` ou `Bloomberg` (as fontes com vértices brutos), padrão `B3`;
- **Ticker:** os códigos na fonte do provedor escolhido, de `GET /api/v1/dados-mercado/{provedor}/tickers`, recarregados a cada troca de provedor; cada opção mostra o código e as curvas ligadas;
- **Data de referência:** `dd/mm/aaaa`, padrão hoje em Brasília.

"Consultar" SHALL chamar `GET /api/v1/dados-mercado/{provedor}?codigoNaFonte=&dataBase=` e mostrar, para cada curva ligada ao ticker, o código e o nome da curva, se já está construída e a tabela dos vértices com os campos do provedor (spec `vertices-brutos-provedor`), mais a quantidade de vértices e os avisos. Sem ticker escolhido, "Consultar" fica desabilitado.

#### Scenario: Tickers dependentes do provedor
- **WHEN** o gestor troca o provedor de `B3` para `ANBIMA`
- **THEN** a lista de tickers passa a ter só `NTN-B`, e a tabela anterior é limpa

#### Scenario: Consulta da PRE
- **WHEN** o gestor escolhe `B3`, o ticker `PRE` e `14/09/2026`, e clica em "Consultar"
- **THEN** a tela mostra cada curva ligada ao `PRE` com os seus 278 vértices (data do vértice, dias corridos, dias úteis e valor)

### Requirement: Manutenção dos vértices
Em cada curva da consulta, a tela SHALL oferecer "Incluir vértice", "Editar" e "Excluir" por linha, e "Excluir todos da data", por modais, com os campos do provedor, pelas rotas da spec `vertices-brutos-provedor`. As exclusões SHALL pedir confirmação. Depois de cada gravação, a tela SHALL recarregar a curva e mostrar os avisos devolvidos; 422 `DADOS_INVALIDOS` SHALL aparecer junto dos campos, no modal, sem fechá-lo. Com o aviso `CURVA_JA_CONSTRUIDA`, a tela SHALL lembrar que a correção só vale num recálculo e oferecer o link "Recalcular a curva" para a tela Curvas, `/curvas?codigo={codigo}`.

#### Scenario: Correção de um valor já construído
- **WHEN** o gestor altera um valor da `PRE` de `14/09/2026`, que já está construída
- **THEN** a tela mostra o aviso `CURVA_JA_CONSTRUIDA` e o link "Recalcular a curva", que abre `/curvas?codigo=PRE`

#### Scenario: Campo inválido
- **WHEN** o gestor salva um vértice da B3 sem dias úteis
- **THEN** o modal continua aberto, com a mensagem no campo dias úteis

### Requirement: Envio do arquivo da fonte
A tela SHALL ter o bloco "Enviar arquivo da fonte", com o provedor (`B3`, `ANBIMA` ou `Bloomberg`) e o arquivo, sem campo de data: a data-base vem do conteúdo do arquivo. "Enviar" SHALL chamar `POST /api/v1/cargas/upload` do bff (`multipart/form-data` com `fonte` e `arquivo`, capability `upload-carga-bff` da change `processor-v0`). Antes do envio, a tela SHALL avisar que um arquivo da mesma data substitui os vértices brutos já gravados daquela fonte. Com sucesso, a tela SHALL mostrar a data-base lida (`dd/mm/aaaa`), o identificador da carga e os vértices gravados por código, e posicionar os filtros nesse provedor e nessa data. Em erro, SHALL mostrar a mensagem e o código devolvidos (por exemplo, `ARQUIVO_INVALIDO`, `PROVEDOR_NAO_IMPLEMENTADO`), sem a página de erro global. O botão fica desabilitado durante o envio.

#### Scenario: TaxaSwap enviado
- **WHEN** o gestor envia o `TaxaSwap.txt` de `2026-09-14` com o provedor `B3`
- **THEN** a tela mostra "Data-base: 14/09/2026", o `idCarga` e os vértices por código, e os filtros passam a `B3` e `14/09/2026`

#### Scenario: Arquivo rejeitado
- **WHEN** o processor responde 422 `ARQUIVO_INVALIDO`
- **THEN** a tela mostra a mensagem e o código, e nada muda nos filtros

### Requirement: Tempo de espera e corpo de arquivo
O envio do arquivo da fonte SHALL esperar até 130 segundos; as demais chamadas mantêm o tempo limite padrão de 3 segundos do `fed`. Requisições com arquivo MUST NOT receber `Content-Type: application/json`: o navegador define o `multipart/form-data`.

#### Scenario: Envio do arquivo da Bloomberg
- **WHEN** o envio leva 50 segundos para responder
- **THEN** o front espera e mostra o resultado, sem erro de tempo esgotado

### Requirement: Textos e formatos
Todos os textos SHALL estar em pt-BR, com acentuação. Datas SHALL ser mostradas em `dd/mm/aaaa` e enviadas em `AAAA-MM-DD`; decimais, recebidos como texto, SHALL ser mostrados com vírgula decimal, sem perder casas, e digitados com vírgula ou ponto.

#### Scenario: Taxa da NTN-B
- **WHEN** a API devolve a taxa `"5.541500000000"`
- **THEN** a tabela mostra `5,541500000000`
