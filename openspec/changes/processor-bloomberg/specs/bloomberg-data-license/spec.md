## ADDED Requirements

### Requirement: Bloomberg como provedor do endpoint único
A Bloomberg SHALL ser mais um provedor do **endpoint de carga existente**, que recebe o provedor no corpo e o entrega ao caso de uso e ao serviço da fonte. O endpoint MUST NOT ganhar rota nova por fonte: o corpo, as respostas, os erros e o `X-Correlation-Id` são os que o endpoint já tem. A Bloomberg SHALL ter o seu caso de uso e o seu serviço, no molde dos de B3 e ANBIMA. Para a Bloomberg, o corpo SHALL aceitar `tickers` (lista JSON de textos, opcional); ele MUST NOT usar calendário nem converter o valor. O upload do arquivo de resposta SHALL seguir o caminho que o projeto já usa para os uploads das outras fontes.

#### Scenario: Download com tickers
- **WHEN** o orquestrador chama o endpoint de carga com o provedor Bloomberg, `dataBase` = `2026-09-14` e os 20 tickers
- **THEN** o serviço da Bloomberg faz o pedido ao Data License e, com os 20 valores, grava e responde 200 com `verticesPorCurva` = `{ "SOFR": 20 }`

### Requirement: Pedido de histórico ao Data License
O processor SHALL obter os nós da SOFR por um pedido de histórico (`HistoryRequest`) do Bloomberg Data License, com o universo = tickers, o campo do valor configurado (padrão `PX_MID`) e as datas inicial e final iguais à data-base. O pedido SHALL ter um identificador determinístico, formado pela data-base, pelos 8 primeiros caracteres do SHA-256 da lista ordenada de tickers e pela faixa de horário de Brasília (`processor.bloomberg.faixa-pedido`, padrão 30 minutos). Na mesma faixa, uma nova chamada SHALL reaproveitar o pedido já feito, em qualquer instância, sem pagar outro. O processor SHALL esperar a resposta por até `processor.bloomberg.espera` (padrão 90 segundos). Sem resposta pronta, a resposta SHALL ser 503 `ARQUIVO_INDISPONIVEL`, citando o identificador do pedido.

#### Scenario: Pedido em andamento
- **WHEN** a chamada das 18h10 faz o pedido e a resposta não fica pronta em 90 segundos
- **THEN** a resposta é 503 `ARQUIVO_INDISPONIVEL`; a chamada das 18h20, na mesma faixa, consulta o mesmo pedido em vez de fazer outro

#### Scenario: Faixa seguinte
- **WHEN** a chamada das 18h35 chega depois de uma resposta incompleta da faixa das 18h00
- **THEN** o processor faz um pedido novo, com o identificador da faixa das 18h30

### Requirement: Campo do valor
O campo do valor SHALL ser `PX_MID` por padrão (valor do fechamento), configurável em `processor.bloomberg.campo`, e SHALL ser gravado em `vPrecoUlt`, sem conversão nem arredondamento. O processor MUST NOT misturar campos: todos os nós de uma carga SHALL vir do mesmo campo.

#### Scenario: Valor gravado como veio
- **WHEN** o `PX_MID` do `S0490Z 5Y BLC2 Curncy` é `3.850`
- **THEN** `vPrecoUlt` = `3.850` na linha desse ticker

### Requirement: Lista de tickers e carga completa
Os tickers SHALL vir do parâmetro `tickers` do corpo. Cada ticker MUST ter até 50 caracteres, só letras, dígitos e espaços simples, e no máximo 100 tickers; os repetidos são removidos. Se a chamada não trouxer `tickers` (ausente ou vazio), o processor SHALL usar a lista de reserva cravada no código, que SHALL ser os 20 tickers `S0490Z <tenor> BLC2 Curncy` de `1D`, `1M`, `3M`, `6M`, `9M`, `1Y`, `15M`, `2Y`, `3Y`, `4Y`, `5Y`, `7Y`, `10Y`, `12Y`, `15Y`, `20Y`, `25Y`, `30Y`, `40Y` e `50Y`, registrando no log `TICKERS_RESERVA`. A lista de reserva SHALL ficar numa única classe, marcada como temporária, de modo que retirá-la seja apagar a classe e o único uso dela.

A carga SHALL ser **completa ou nenhuma**: com algum ticker pedido sem valor na data, ou que a Bloomberg não reconhece, a resposta SHALL ser 503 `ARQUIVO_INDISPONIVEL` (download) ou 422 `ARQUIVO_INVALIDO` (upload), citando os tickers sem valor, e nada SHALL ser gravado. Ticker com caractere fora da regra MUST resultar em 400 `PARAMETRO_INVALIDO`, sem nenhuma chamada de saída.

#### Scenario: Chamada sem tickers
- **WHEN** a rota é chamada sem o campo `tickers` no corpo
- **THEN** o pedido usa a lista de reserva dos 20 tickers, e o log registra `TICKERS_RESERVA`

#### Scenario: Tenor sem valor
- **WHEN** a resposta pronta traz valor para 19 dos 20 tickers
- **THEN** a resposta é 503 `ARQUIVO_INDISPONIVEL` citando o ticker sem valor, e nada é gravado; numa faixa seguinte, um pedido novo é feito

#### Scenario: Ticker com caractere inválido
- **WHEN** o corpo traz um ticker com `/` ou `?`
- **THEN** a resposta é 400 `PARAMETRO_INVALIDO`, sem nenhuma chamada ao Data License

### Requirement: Upload do arquivo de resposta
O processor SHALL aceitar, no caminho de upload do projeto, (`multipart/form-data`, parte `arquivo`, até 10 MB, `X-Usuario` opcional) o arquivo de resposta do pedido de histórico do Data License. Os tickers SHALL ser os do arquivo. O envio SHALL seguir o mesmo caminho do download (identidade da carga, original no Blob, parse, validação, gravação e aviso ao engine), com `origem` = `UPLOAD` e o usuário no log. Um ticker sem valor, ou um conjunto de tickers diferente dos 20 esperados, MUST rejeitar o envio com 422 `ARQUIVO_INVALIDO`, citando os tickers que faltam e os que sobram. Arquivo vazio, maior que 10 MB ou de formato diferente MUST resultar em 422 `ARQUIVO_INVALIDO`. A data-base SHALL ser a do conteúdo; sem data legível, o original não é arquivado.

#### Scenario: Upload completo
- **WHEN** o operador envia o arquivo de resposta com os 20 tickers de `2026-09-14`
- **THEN** a carga é gravada com `origem` = `UPLOAD` e o usuário, e o engine é avisado

#### Scenario: Upload com tenor a menos
- **WHEN** o arquivo traz 19 tickers
- **THEN** a resposta é 422 `ARQUIVO_INVALIDO` citando o ticker que falta, nada é gravado, e o original fica arquivado

### Requirement: Gravação em `tBbergCurvaPrimr`
Para a carga, o processor SHALL buscar em `tCurvaPrvdr` as curvas de mercado com `iPrvdrDados` = `BLOOMBERG`, `cPrvdrMercd` = `BLC2` e `cTickerPrvdr` = o membro (primeiro termo do ticker, ex.: `S0490Z`). Em uma única transação por carga, SHALL travar a curva em `tCurvaMercd` (`UPDLOCK, ROWLOCK`, tempo limite de 60 segundos), apagar os nós da curva e da data e inserir os novos **sem informar `cIdtfdUnic`** (a coluna é `bigint IDENTITY`, script `004`). As colunas SHALL ser `cTickerIndcd` = nome da curva, `dBaseReft`, `cTickerBberg` = ticker completo como pedido (`VARCHAR(50)`) e `vPrecoUlt` = valor; as demais ficam nulas. O processor MUST NOT escrever em `tCurvaMercd` nem em `tCurvaPrvdr`. Membro sem curva ligada é ignorado e aparece no log; uma carga sem curva mapeada responde 200 com `verticesPorCurva` vazio e não avisa o engine.

#### Scenario: Ticker completo
- **WHEN** o nó `S0490Z 15M BLC2 Curncy` é gravado
- **THEN** `cTickerBberg` = `S0490Z 15M BLC2 Curncy` e `vPrecoUlt` = o valor do `PX_MID`

#### Scenario: SOFR sem curva ligada
- **WHEN** não há curva ligada a `BLOOMBERG`/`BLC2`/`S0490Z` em `tCurvaPrvdr`
- **THEN** a resposta é 200 com `verticesPorCurva` vazio, nada é gravado, e o log cita o membro como ignorado

### Requirement: Identidade e original da carga Bloomberg
O `idCarga` SHALL ser `BLOOMBERG-BLC2-{AAAAMMDD}-{12 primeiros do SHA-256 dos bytes}` e o original SHALL ser arquivado em `bloomberg/{AAAAMMDD}/cargas/{idCarga}/{nome do arquivo de resposta}` (bytes recebidos), antes de interpretar o conteúdo, como na `processor-v0`. `{AAAAMMDD}` é a data-base do conteúdo. O reprocessamento SHALL reler o original mais recente, sem chamar o Data License.

#### Scenario: Reprocessar sem chamar a Bloomberg
- **WHEN** o operador executa a tarefa Bloomberg com uma `dataBase` passada, sem `incluirDownload`
- **THEN** o processor relê o original do Blob e grava os nós, sem nenhuma chamada ao Data License

### Requirement: Credencial, rede e endereço
A credencial do Data License SHALL vir do Key Vault (nunca do `application.yml`) e nenhum log MUST conter segredo ou token. O endereço do Data License SHALL vir da configuração (`processor.bloomberg.base-url`); nenhum parâmetro da chamada monta o endereço de saída além da data e dos tickers validados. As chamadas ao Data License MUST NOT levar o `X-Correlation-Id` nem outro dado interno. Resposta de erro inesperado do Data License SHALL ser 502 `FONTE_INDISPONIVEL`, com a mensagem explicando o que fazer (por exemplo, conferir a credencial e a liberação de rede).

#### Scenario: Credencial inválida
- **WHEN** o Data License recusa a credencial
- **THEN** a resposta é 502 `FONTE_INDISPONIVEL` com uma mensagem que cita a credencial, e nada é gravado
