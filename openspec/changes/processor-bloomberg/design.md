## Context

O processor tem uma base comum (`processor-v0`, seção 1: rotas, `CargaArquivoService`, Blob, persistência, aviso ao engine) e um provedor por fonte (`ProvedorCargaPort`). A Bloomberg nasce como `BloombergDataLicenseProvedor` provisório (501 `PROVEDOR_NAO_IMPLEMENTADO`) e esta change o implementa. A SOFR usa o membro `S0490Z`, produto `BLC2`, e o engine a lê de `tBbergCurvaPrimr` (`cTickerBberg` = ticker completo, `vPrecoUlt` = taxa zero em % ao ano, sem conversão).

Nada aqui foi implementado ainda: não há código, nem histórico gravado, nem pedido ao Data License feito.

## Goals / Non-Goals

**Goals:** a SOFR chegar a `tBbergCurvaPrimr` por download (pelo orquestrador) ou por upload (pelo front), completa (20 tenores) ou recusada com mensagem.

**Non-Goals:** o aviso ao engine (`aviso-engine-cargas`); o agendamento; B3 e ANBIMA; qualquer coluna ou tabela nova; function intermediária da Bloomberg.

## Decisions

### D1. Acesso direto do processor ao Data License
O processor chama a API do Data License sem function intermediária. **Por quê:** é uma chamada por dia com 20 tickers; uma function a mais só repete credencial, rede e logs. **Alternativa rejeitada:** function dedicada (como a B3 teria): custo de infra e de contrato sem ganho.

### D2. Pedido idempotente por faixa de horário
O identificador do pedido é `sofr` + data-base + 8 primeiros caracteres do SHA-256 dos tickers ordenados + faixa de 30 minutos (Brasília). **Por quê:** o orquestrador repete a cada 10 minutos; sem identificador estável, cada repetição pagaria um pedido novo. Na mesma faixa a chamada reaproveita o pedido em qualquer instância. **Alternativa rejeitada:** um pedido por chamada.

### D3. Campo do valor `PX_MID`, gravado em `vPrecoUlt`
O campo padrão é `PX_MID` (valor do fechamento); a coluna continua `vPrecoUlt`, que é o último valor publicado do dia, e que o modelo da SOFR lê sem conversão. **Por quê:** o mid do fechamento é o último mid; não há razão para mudar a coluna, o modelo nem o CRUD do bruto. O campo é configuração (`processor.bloomberg.campo`). **Risco:** o `PX_MID` pode vir vazio para uma curva gerada; por isso o pedido de teste (tarefa 1.1) confere os 20 tickers antes de implementar.

### D4. Carga completa ou nenhuma
Um ticker sem valor deixa a SOFR com um tenor a menos, que parece normal. Por isso a carga só grava com os 20 tickers com valor; senão, 503 (download) ou 422 (upload), citando o ticker. **Por quê:** o engine (`engine-v1-2`) também recusa a SOFR incompleta, mas é melhor não gravar a carga parcial. **Alternativa rejeitada:** gravar o que veio e deixar o engine recusar.

### D5. Lista dos 20 tickers
`1D`, `1M`, `3M`, `6M`, `9M`, `1Y`, `15M`, `2Y`, `3Y`, `4Y`, `5Y`, `7Y`, `10Y`, `12Y`, `15Y`, `20Y`, `25Y`, `30Y`, `40Y` e `50Y`, no formato `S0490Z <tenor> BLC2 Curncy`. A tarefa do orquestrador manda a lista no corpo (`tickers`); a reserva no código (uma classe, marcada `TODO(retirar)`) só vale quando a chamada vem sem ela.

### D6. Sem `cIdtfdUnic` no `INSERT`
A coluna é `bigint IDENTITY` (script `004`); o `INSERT` não a informa. O script precisa estar aplicado antes da primeira carga.

## Risks / Trade-offs

- [Catálogo e endpoints do Data License ainda não confirmados] → tarefa 1.1 antes de tudo; os pontos `[A CONFIRMAR]` do guia só fecham com a documentação da conta.
- [Credencial e rede não liberadas] → a rota responde 502 `FONTE_INDISPONIVEL` com mensagem clara; sem credencial a SOFR não chega, e não há plano B além do upload do arquivo.
- [`PX_MID` vazio] → ver D3; se vier vazio, volta-se a `PX_LAST` só trocando a configuração.

## Open Questions

1. O upload da Bloomberg também exige os 20 tickers (igual ao download), como a spec diz. Falta só a confirmação.
2. Layout real do arquivo de resposta do Data License (colunas e separador): `[A CONFIRMAR]` com uma resposta real.
