## Why

A SOFR (a sétima das 7 curvas) é a única que depende da Bloomberg, e o `services/processor` vai acessá-la **direto**, pela API do Data License, sem function intermediária. A `processor-v0` cobre as três fontes numa change só; B3 e ANBIMA ficaram com outros desenvolvedores, e esta change separa **só a Bloomberg**, para uma pessoa levar de ponta a ponta: o pedido ao Data License, a lista dos 20 tickers, o campo do valor, o upload do arquivo de resposta, a gravação em `tBbergCurvaPrimr` e as credenciais.

O contrato comum do processor (o endpoint único de carga com o provedor no corpo, respostas, erros, identidade da carga, original no Blob, reprocessamento e log) continua na `processor-v0` e **não** é repetido aqui; esta change só acrescenta o que é da Bloomberg e substitui as partes da Bloomberg dessa change.

## What Changes

- **Provedor Bloomberg (`BloombergDataLicenseProvedor`):** pedido de histórico (`HistoryRequest`) com os 20 tickers `S0490Z <tenor> BLC2 Curncy`, identificador determinístico por faixa de 30 minutos, espera de até 90 s, 503 quando o pedido não está pronto ou algum ticker vem sem valor.
- **Campo do valor `PX_MID`** (valor do fechamento), gravado em `vPrecoUlt`; configurável.
- **Reserva dos 20 tickers** quando a chamada não traz `tickers` (temporária, numa única classe).
- **Upload** do arquivo de resposta do Data License (rota do bff), com os tickers do arquivo.
- **Gravação** em `tBbergCurvaPrimr` sem informar `cIdtfdUnic` (script `004`), com o ticker completo em `cTickerBberg`.
- **Credencial** do Data License no Key Vault, saída de rede só para o host configurado.
- **Conferência de dado real:** pedido de teste com `PX_LAST` e `PX_MID` para confirmar que o `PX_MID` vem preenchido para os 20 tickers.

Fica para outra change: o aviso ao engine (`aviso-engine-cargas`, vale para as três fontes) e o agendamento (orquestrador).

## Capabilities

### New Capabilities
- `bloomberg-data-license`: obtenção, validação e gravação dos nós da SOFR pelo Data License, e o upload do arquivo de resposta.

### Modified Capabilities
<!-- Nenhuma em openspec/specs. Substitui as partes da Bloomberg da spec `carga-arquivos-processor` (change `processor-v0`), que continua valendo para o contrato comum, a B3 e a ANBIMA. -->

## Impact

- **processor:** `adapter/out/client/bloomberg/` (cliente, leiaute da resposta, reserva dos tickers), `BbergCurvaPrimrInsercao`, `BloombergDataLicenseProvedor`, configuração `processor.bloomberg`.
- **Banco:** nenhuma mudança além dos scripts `004` (já commitado) para o `IDENTITY`; `tBbergCurvaPrimr` não ganha coluna nova.
- **engine:** a `engine-v1-2` valida os mesmos 20 tenores; sem mudança aqui.
- **orquestrador e bff:** a tarefa `carga-data-license` e a rota de upload já estão especificadas nas changes deles; nada muda.
- **Dependência externa:** conta do Data License (catálogo, endpoints e credencial) e liberação de rede; sem isso a SOFR não chega.
