## ADDED Requirements

### Requirement: Rotas no contrato do orquestrador
O `services/processor` SHALL expor, sem autenticação, as rotas abaixo, chamadas pelas tarefas de carga (`carga-download-site` e `carga-data-license`) do orquestrador (execução manual na change `orquestrador-v0-disparo-manual`; agendamento na `orquestrador-curvas`):

| Uso | Rota |
|---|---|
| Download | `GET /api/v1/cargas/{fonte}/download?dataBase={dataBase}` (Bloomberg: `&tickers={tickers}`) |
| Reprocessamento | `GET /api/v1/cargas/{fonte}/reprocessamento?dataBase={dataBase}` |

`{fonte}` SHALL ser `b3`, `anbima` ou `bloomberg`; as três fontes têm as mesmas rotas, e a fonte só escolhe quem obtém e interpreta o arquivo. `tickers` SHALL ser aceito só com `bloomberg` (no reprocessamento, aceito e ignorado); com outra fonte, MUST resultar em 400.

A data SHALL ser `AAAA-MM-DD` e não pode ser futura no horário de Brasília. O download SHALL aceitar data passada (o orquestrador usa isso no reprocessamento com `incluirDownload`): busca na fonte o arquivo daquela data, como no download do dia. As respostas SHALL ser:
- 200 com `{ "idCarga", "dataBase", "hashArquivo", "origem", "verticesPorCodigo", "correlationId" }`, depois do commit da gravação; `origem` é `DOWNLOAD`, `REPROCESSAMENTO` ou `UPLOAD`; `dataBase` é a do conteúdo do arquivo;
- 400 `PARAMETRO_INVALIDO`: fonte desconhecida, data ausente, inválida ou futura, `tickers` fora da regra ou informado para fonte que não é `bloomberg`;
- 404 `ARQUIVO_NAO_ENCONTRADO`: no reprocessamento, nenhum original da fonte e data no Blob;
- 422 `ARQUIVO_INVALIDO`: o arquivo foi obtido, mas é rejeitado inteiro pela validação; nada é gravado no banco, e o original fica arquivado quando a data-base é conhecida (requisito "Identidade da carga e original no Blob");
- 502 `FONTE_INDISPONIVEL`: a fonte respondeu com erro inesperado;
- 503 `ARQUIVO_INDISPONIVEL`: a fonte ainda não publicou o arquivo da data, o conteúdo é de outra data ou, na Bloomberg, o pedido ainda está em andamento ou incompleto;
- 501 `PROVEDOR_NAO_IMPLEMENTADO`: a fonte ainda não tem provedor implementado (rotas já expostas, provedor provisório);
- 503 `SERVICO_INDISPONIVEL`: Blob ou banco fora, ou trava de curva não obtida em 60 segundos.

Todo erro SHALL ter o corpo `{ "codigoErro", "mensagem", "correlationId" }`, sem stack trace. Toda resposta SHALL trazer `X-Correlation-Id`.

#### Scenario: Arquivo do dia ainda não publicado
- **WHEN** o orquestrador chama `/api/v1/cargas/anbima/download?dataBase=2026-09-28` às 18h e a ANBIMA ainda não publicou `ms260928.txt`
- **THEN** a resposta é 503 `ARQUIVO_INDISPONIVEL`, nada é arquivado nem gravado, e o orquestrador tenta de novo na próxima ocorrência

#### Scenario: Carga gravada
- **WHEN** a chamada B3 de `2026-09-14` obtém o `TaxaSwap.txt` do dia e grava as curvas mapeadas
- **THEN** a resposta é 200 com `dataBase` = `2026-09-14`, o `idCarga` e os vértices por código, e só é enviada depois do commit

#### Scenario: Provedor ainda não implementado
- **WHEN** a base comum está implantada e o provedor da ANBIMA ainda é o provisório
- **THEN** as rotas da ANBIMA respondem 501 `PROVEDOR_NAO_IMPLEMENTADO`, sem arquivar nem gravar, e as rotas das fontes já implementadas funcionam

#### Scenario: Data futura
- **WHEN** a rota recebe `dataBase` = amanhã
- **THEN** a resposta é 400 `PARAMETRO_INVALIDO`, sem chamar a fonte

### Requirement: Upload do arquivo
O processor SHALL expor, sem autenticação própria, `POST /api/v1/cargas/{fonte}/upload` (`{fonte}` = `b3`, `anbima` ou `bloomberg`), em `multipart/form-data` com a parte `arquivo` (até 10 MB) e o cabeçalho `X-Usuario` obrigatório (ausente: 400 `PARAMETRO_INVALIDO`). Essas rotas são chamadas só pelo bff (capability `upload-carga-bff`), que autentica. O arquivo enviado SHALL seguir o mesmo caminho do baixado: forma canônica (B3), identidade da carga, original no Blob, parse, validação, gravação e aviso ao engine, com `origem` = `UPLOAD` e o usuário no log. A data-base SHALL ser a do conteúdo; conteúdo com data futura MUST ser rejeitado (422 `ARQUIVO_INVALIDO`). Por fonte:
- B3: aceita o `TaxaSwap.txt` ou o `.ex_` (reconhecido pelo cabeçalho de zip, com no máximo dois níveis e um único `TaxaSwap.txt`);
- ANBIMA: aceita o `ms{AAMMDD}.txt`, com as regras da ANBIMA;
- Bloomberg: aceita o arquivo de resposta do pedido de histórico do Data License; os tickers são os do arquivo, e um ticker sem valor MUST rejeitar o envio (422 `ARQUIVO_INVALIDO`, citando o ticker).

A resposta SHALL ser a mesma das outras rotas, mais o `usuario`. Arquivo vazio, maior que 10 MB ou de formato diferente do da fonte MUST resultar em 422 `ARQUIVO_INVALIDO`. No upload, o original só é arquivado se a data-base puder ser lida do conteúdo; sem ela, a recusa fica só no log, com o usuário e o nome do arquivo.

#### Scenario: Upload do TaxaSwap com a B3 fora
- **WHEN** o download da B3 falha e o operador envia pelo front o `TaxaSwap.txt` de `2026-09-14`
- **THEN** a carga é gravada com `origem` = `UPLOAD` e o usuário, o `idCarga` é o mesmo que o download daquele arquivo teria gerado, e o engine é avisado

#### Scenario: Upload do .ex_
- **WHEN** o operador envia o `TS260914.ex_`
- **THEN** o processor extrai o `TaxaSwap.txt` e segue como no envio do texto, com o mesmo `idCarga`

#### Scenario: Upload sem usuário
- **WHEN** a rota de upload é chamada sem `X-Usuario`
- **THEN** a resposta é 400 `PARAMETRO_INVALIDO`, e nada é arquivado

### Requirement: Obtenção do arquivo B3 por download do site
O processor SHALL baixar `TS{AAMMDD}.ex_` do endereço configurado da B3 (`https://www.b3.com.br/pesquisapregao/download?filelist=TS{AAMMDD}.ex_`), extrair o `TaxaSwap.txt` (o `.ex_` é um zip com outro zip dentro) e convertê-lo à forma canônica da spec `b3-taxaswap-publicacao` (change `conector-b3-webhook-ingest`). Resposta da B3 sem o arquivo (404, corpo vazio ou que não é zip) MUST resultar em 503 `ARQUIVO_INDISPONIVEL`. O leiaute, a conversão de valores e as regras de validação SHALL ser as da spec `b3-carga-processor` (change `processor-carga-b3`): vértice ilegível rejeita o arquivo; campo inválido rejeita só aquele código. No download, data dos vértices diferente da pedida MUST resultar em 503 `ARQUIVO_INDISPONIVEL`, sem arquivar nem gravar.

#### Scenario: Código com campo inválido
- **WHEN** o arquivo do dia tem um valor ilegível num vértice da `DPL`
- **THEN** as demais curvas são gravadas, a `DPL` não entra em `verticesPorCodigo`, e o log da carga cita o código e o motivo

### Requirement: Obtenção do arquivo ANBIMA por download do site
O processor SHALL baixar `ms{AAMMDD}.txt` do endereço configurado (`https://www.anbima.com.br/informacoes/merc-sec/arqs/ms{AAMMDD}.txt`), que é texto Latin-1, campos separados por `@` e vírgula decimal. O processor SHALL localizar a linha de cabeçalho (a que começa com `Titulo@`) e ler as colunas pelo nome. Só entram os títulos com `Titulo` = `NTN-B` e `Codigo SELIC` terminado em `99` (título inteiro); as demais são ignoradas. Para cada título:
- no download, `Data Referencia` (`AAAAMMDD`) MUST ser igual à data pedida, ou a resposta é 503 `ARQUIVO_INDISPONIVEL`;
- `Tx. Indicativas` é a taxa em percentual ao ano, convertida para `BigDecimal` direto do texto; vazia ou `--` grava nula (o engine descarta o título com `SEM_TAXA`);
- o prazo é a quantidade de dias corridos entre a data-base e a `Data Vencimento`, sem ajuste de dia útil. O processor MUST NOT usar calendário: o engine reconstrói o vencimento (`data-base + prazo`), ajusta para dia útil e conta os dias úteis (spec `ntnb-anbima-curve-model`).

Arquivo sem a linha de cabeçalho, sem as colunas usadas, sem nenhuma NTN-B inteira, ou com `Data Vencimento` ilegível MUST ser rejeitado inteiro (422 `ARQUIVO_INVALIDO`).

#### Scenario: Só o título inteiro
- **WHEN** o arquivo traz as NTN-B `760199` e uma NTN-B Principal `760198`
- **THEN** só os títulos `760199` são gravados

#### Scenario: Prazo em dias corridos
- **WHEN** a data-base é `2026-09-28` e o título vence em `2027-05-15` (sábado)
- **THEN** `vVertcCurva` = 229, os dias corridos entre `2026-09-28` e `2027-05-15`, sem ajuste nem calendário

### Requirement: Obtenção dos nós da SOFR pelo Data License
O processor SHALL obter os nós da SOFR por um pedido de histórico (`HistoryRequest`) da API do Bloomberg Data License, com o universo = tickers, o campo do valor configurado (padrão `PX_LAST`) e as datas inicial e final iguais à data-base. Os tickers SHALL vir do parâmetro `tickers` (separados por vírgula). Cada ticker MUST ter até 50 caracteres, só letras, dígitos e espaços simples, e no máximo 100 tickers; os repetidos são removidos. Se a chamada não trouxer `tickers` (ausente ou vazio), o processor SHALL usar a lista de reserva cravada no código (os 20 tickers `S0490Z <tenor> BLC2 Curncy` de `1D` a `50Y` de `cadastros-sugeridos.txt` do orquestrador), registrando no log que a reserva foi usada. A lista de reserva SHALL ficar numa única classe, marcada como temporária, de modo que retirá-la seja apagar a classe e o único uso dela.

O pedido SHALL ter um identificador determinístico, formado pela data-base, pelos 8 primeiros caracteres do SHA-256 da lista ordenada de tickers e pela faixa de horário de Brasília (`processor.bloomberg.faixa-pedido`, padrão 30 minutos). Na mesma faixa, uma nova chamada reaproveita o pedido já feito, em qualquer instância, sem pagar outro. O processor SHALL esperar a resposta por até `processor.bloomberg.espera` (padrão 90 segundos). Sem resposta pronta, ou com algum ticker sem valor na data, a resposta é 503 `ARQUIVO_INDISPONIVEL`, citando o identificador e os tickers sem valor. Ticker que a Bloomberg não reconhece também deixa a carga incompleta: a correção é mudar o parâmetro `tickers` da tarefa.

#### Scenario: Pedido em andamento
- **WHEN** a chamada das 18h10 faz o pedido e a resposta não fica pronta em 90 segundos
- **THEN** a resposta é 503 `ARQUIVO_INDISPONIVEL`; a chamada das 18h20, na mesma faixa, consulta o mesmo pedido em vez de fazer outro

#### Scenario: Chamada sem tickers
- **WHEN** a rota é chamada sem o parâmetro `tickers`
- **THEN** o pedido usa a lista de reserva, e o log registra `TICKERS_RESERVA`

#### Scenario: Tenor sem valor
- **WHEN** a resposta pronta traz valor para 19 dos 20 tickers
- **THEN** a resposta é 503 `ARQUIVO_INDISPONIVEL` citando o ticker sem valor, e nada é gravado; numa faixa seguinte, um pedido novo é feito

### Requirement: Identidade da carga e original no Blob
Para cada arquivo obtido, o processor SHALL calcular `hashArquivo` (SHA-256 hexadecimal minúsculo dos bytes arquivados) e o `idCarga`:

| Fonte | `idCarga` | Original arquivado |
|---|---|---|
| B3 | `B3-TS-{AAAAMMDD}-{12 do hash}` | `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt` (forma canônica, como o conector) |
| ANBIMA | `ANBIMA-MS-{AAAAMMDD}-{12 do hash}` | `anbima/{AAAAMMDD}/cargas/{idCarga}/ms{AAMMDD}.txt` (bytes recebidos) |
| Bloomberg | `BLOOMBERG-BLC2-{AAAAMMDD}-{12 do hash}` | `bloomberg/{AAAAMMDD}/cargas/{idCarga}/{nome do arquivo de resposta}` (bytes recebidos) |

`{AAAAMMDD}` SHALL ser a data-base do conteúdo, nunca a data do download. No download, se o conteúdo não permitir ler a data-base (arquivo rejeitado inteiro), vale a data pedida, para o original rejeitado continuar auditável; no upload, sem data-base legível o original não é arquivado. O original SHALL ser gravado antes de interpretar o conteúdo e de qualquer gravação no banco, com escrita condicional (`If-None-Match: *`); se já existir, é sucesso, sem sobrescrever. Falha do Blob MUST interromper a carga sem gravar no banco (503 `SERVICO_INDISPONIVEL`). O Blob SHALL guardar só o original: nenhum estado, registro ou resultado da carga.

#### Scenario: Mesmo arquivo duas vezes
- **WHEN** o download B3 de `2026-09-14` é chamado duas vezes e a B3 entrega o mesmo arquivo
- **THEN** as duas chamadas têm o mesmo `idCarga`, o original é gravado uma vez, e o banco fica com os mesmos vértices

#### Scenario: Arquivo rejeitado continua auditável
- **WHEN** o download ANBIMA de `2026-09-28` obtém um arquivo sem a linha de cabeçalho
- **THEN** o original fica arquivado em `anbima/20260928/cargas/{idCarga}/`, a resposta é 422 `ARQUIVO_INVALIDO`, e nada é gravado no banco

### Requirement: Reprocessamento pelo original arquivado
As rotas de reprocessamento MUST NOT chamar a fonte. Elas SHALL ler o original mais recente (pela data de criação no Blob) em `{fonte}/{AAAAMMDD}/cargas/` e repetir o parse, a validação, a gravação e o aviso, com `origem` = `REPROCESSAMENTO` e o `idCarga` do original lido. Na Bloomberg, o parâmetro `tickers` é aceito e ignorado: valem os tickers do original. Sem original, a resposta é 404 `ARQUIVO_NAO_ENCONTRADO`, com o prefixo procurado.

#### Scenario: Curva ligada depois da carga
- **WHEN** o cadastro liga uma curva nova ao código `PRE` depois da carga de `2026-09-14`, e o operador executa a tarefa B3 com `dataBase` = `2026-09-14`
- **THEN** o processor relê o original do Blob, grava também a curva nova com o mesmo `idCarga`, e o engine constrói só as curvas que ainda não têm pontos

### Requirement: Gravação nas tabelas brutas
Para cada código da carga, o processor SHALL buscar em `tCurvaPrvdr` as curvas de mercado com `iPrvdrDados` = fonte, `cPrvdrMercd` = produto e `cTickerPrvdr` = código: `B3`/`TS`/código da curva; `ANBIMA`/`MS`/`NTN-B`; `BLOOMBERG`/`BLC2`/membro (primeiro termo do ticker, ex.: `S0490Z`). Código sem curva ligada é ignorado e aparece no log. Numa **única transação** por carga, o processor SHALL travar o registro de cada curva mapeada em `tCurvaMercd` (`UPDLOCK, ROWLOCK`, em ordem crescente do nome, tempo limite de 60 segundos), apagar os vértices da curva e data na tabela da fonte, inserir os novos com `cIdtfdUnic` = `MAX + 1` lido com `UPDLOCK, HOLDLOCK` e conferir as contagens antes do commit. As colunas SHALL ser:

| Tabela | Colunas gravadas | Demais |
|---|---|---|
| `tBtrsCurvaPrimr` | `cTickerIndcd` = curva, `dBaseReft`, `cDiaCorri`, `cDiaUtil`, `vPrecoTx` (como na spec `b3-carga-processor`) | fatores nulos |
| `tAnbmaCurvaPrimr` | `cTickerIndcd` = curva, `dBaseReft`, `vPrecoTx` = taxa indicativa, `vVertcCurva` = prazo em dias corridos até a `Data Vencimento` | — |
| `tBbergCurvaPrimr` | `cTickerIndcd` = curva, `dBaseReft`, `cTickerBberg` = ticker completo, como pedido e publicado, `vPrecoUlt` = valor | nulas |

O processor MUST NOT escrever em `tCurvaMercd` nem em `tCurvaPrvdr`. Uma carga sem nenhum código mapeado responde 200 com `verticesPorCodigo` vazio e não avisa o engine.

#### Scenario: Falha no meio da gravação
- **WHEN** o banco cai depois de gravar a primeira de cinco curvas da carga B3
- **THEN** a transação é desfeita, nenhuma curva fica com dados parciais, e a resposta é 503 `SERVICO_INDISPONIVEL`

#### Scenario: Ticker completo na Bloomberg
- **WHEN** o nó `S0490Z 15M BLC2 Curncy` é gravado
- **THEN** `cTickerBberg` = `S0490Z 15M BLC2 Curncy`, na coluna `VARCHAR(50)` do `001_SCRIPT_INICIAL.sql`

### Requirement: Aviso ao engine depois do commit
Depois do commit de uma carga com algum código gravado, o processor SHALL chamar `POST /api/v1/cargas` do engine (spec `curve-load-trigger`) com `idCarga`, `fonte`, `produto`, `dataBase` e `verticesPorCodigo` (vértices gravados por código na fonte), sem `Authorization` e com o `X-Correlation-Id` da chamada. O aviso MUST NOT segurar a resposta ao orquestrador: roda em segundo plano, repetindo com o mesmo `idCarga` por até 10 minutos (espera de 1 segundo dobrando até 60, tempo limite de 150 segundos por chamada), tratando 409 como repetição. Aos 2 minutos sem 2xx, o log SHALL ter `AVISO_ATRASADO`; esgotada a janela, ou com 4xx diferente de 409, `CARGA_FALHOU` (log de erro e métrica), com `idCarga`, fonte e data-base. A recuperação é o reprocessamento da data.

#### Scenario: Engine fora por 3 minutos
- **WHEN** a carga ANBIMA é gravada e o engine fica fora por 3 minutos
- **THEN** o orquestrador já recebeu o 200, o log tem `AVISO_ATRASADO` aos 2 minutos, e o aviso é aceito quando o engine volta

### Requirement: Correlação, logs e segredos
Cada chamada SHALL usar o `X-Correlation-Id` recebido (ou gerar um UUID) na chamada ao engine e em todo log; as chamadas à B3, à ANBIMA e ao Data License MUST NOT levar esse cabeçalho nem nenhum outro dado interno. Cada carga SHALL registrar em log JSON, no horário de Brasília: fonte, data-base, `idCarga`, origem, caminho do original, códigos gravados com as curvas, ignorados e rejeitados com o motivo, duração por etapa e resultado do aviso. A credencial do Data License SHALL vir do Key Vault; o acesso ao Blob SHALL usar Managed Identity (string de conexão só no ambiente local). Nenhum log MUST conter segredo ou token. Os endereços da B3, da ANBIMA e do Data License SHALL vir da configuração: nenhum parâmetro da chamada monta o endereço de saída além da data e dos tickers validados.

#### Scenario: Endereço não montado pela chamada
- **WHEN** o download com `{fonte}` = `bloomberg` recebe um ticker com `/` ou `?`
- **THEN** a resposta é 400 `PARAMETRO_INVALIDO`, sem nenhuma chamada de saída
