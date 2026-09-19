# Fixtures reais B3/ANBIMA

Recortes reais de arquivos oficiais, para testar parsing e quebra em blocos
sem precisar dos arquivos de produção (inviáveis de versionar por inteiro).

| Arquivo | Origem | Data de captura | Encoding |
|---|---|---|---|
| `b3-curva-pre-20260821_fixture.csv` | Curva pronta DI x PRÉ, endpoint `sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy` (ver seção abaixo) | 2026-08-21 | ISO-8859-1 |
| `TaxaSwap_20260914_fixture.txt` | `TS260914.zip` → `TaxaSwap.txt` (Mercado de Derivativos – Taxas de Mercado para Swaps, ver seção "TaxaSwap" abaixo) | 2026-09-14 | ISO-8859-1 (só ASCII nas linhas capturadas) |

As fixtures `BVBG.086.01_fixture.xml`/`BVBG.028.02_fixture.xml` (Preços de
Referência / Cadastro de instrumentos, formato XML `BizGrp` repetido) foram
removidas — o feeder de arquivo PR/IN e o corte estrutural por elemento XML
(`src/xml-estrutural.ts`) que as consumiam foram removidos: decisão do
usuário, este projeto não fará ingestão do BVBG.

## Curva pronta (`b3-curva-pre-20260821_fixture.csv`)

Desbloqueado nesta sessão: o endpoint real de taxas de referência da B3 não é o mesmo do
`pesquisapregao/download` (que só serve PR/IN/TS, este último confirmado sendo `TaxaSwap.txt`,
não vértices de curva). O real é:

```
https://sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy/Search/GetDownloadFile/{base64}
```

onde `{base64}` codifica o JSON minificado `{"language":"pt-br","date":"YYYY-MM-DD","id":"PRE"}`.
O corpo HTTP é, ele mesmo, uma string Base64 — decodificar uma vez dá o CSV real (ISO-8859-1,
separador `;`, decimal com vírgula, 1 linha de cabeçalho). Verificado ao vivo nesta sessão com
`curl` puro (sem nenhum código do projeto): 274 vértices reais para `PRE`/2026-08-21, arquivo
pequeno o bastante (6.790 bytes decodificados) para versionar por inteiro, igual à fixture da
ANBIMA. Fim de semana (2026-08-22) confirmado como HTTP 200 com corpo vazio — não 404 — mesmo
padrão de "sinal real" já usado pelo feeder B3 de arquivo (ZIP externo vazio).

Implementado em `src/feeders/b3-curva-referencia.ts`, dataset `B3_CURVA_PRE`, `payloadKind:
'READY_CURVE'` — publicação real confirmada no Kafka local e teste de contrato real
(`src/feeders/b3-curva-referencia.contract.ts`) contra o endpoint ao vivo.

**O que ainda falta**: só o código `PRE` foi verificado e implementado. A B3 também publica DIC,
DOL, DOC, DCL, INP e outras curvas no mesmo endpoint (mesmo padrão de URL, `id` diferente) — não
verificadas nem implementadas nesta sessão, ficam como extensão futura do mesmo padrão
(`FeederB3CurvaReferencia` já é parametrizado por `codigoCurva`, pronto para registrar mais
códigos assim que cada um for confirmado individualmente).

## TaxaSwap (`TaxaSwap_20260914_fixture.txt`)

Fonte real das curvas DCL/PTX/INP/DPL (openspec/changes/b3-additional-curves), e do oráculo de
validação cruzada de PRE — arquivo oficial "Mercado de Derivativos – Taxas de Mercado para
Swaps" da B3, layout de largura fixa de 72 colunas confirmado contra a planilha oficial
(`docs/Layout Mercado de Derivativos - Taxas de Mercado para Swaps-atual.zip`, fornecida pelo
usuário). Mesmo endpoint `pesquisapregao/download`, prefixo `TS`:

```
https://www.b3.com.br/pesquisapregao/download?filelist=TS<AAMMDD>.ex_
```

**Extensão real confirmada ao vivo nesta sessão: `.ex_`, não `.zip` nem `.exe`.** A suposição
original (mesma extensão `.zip` de PR/IN) estava errada — `.zip`/`.exe` sempre devolvem um ZIP
vazio de 22 bytes para o prefixo `TS`, mesmo em data de pregão real; só `.ex_` devolve o conteúdo
de verdade (confirmado contra várias datas reais, inclusive a mesma data em que `PR<data>.zip`
funciona normalmente — então não é problema de disponibilidade da data, é a extensão errada). O
corpo da resposta, apesar da extensão incomum na URL, é mesmo um ZIP de verdade.

**Igual a PR/IN, o ZIP baixado É duplamente aninhado** — achado real desta sessão, corrigindo a
suposição inicial (feita antes de qualquer aquisição ao vivo real) de que TS seria um caso
único-nível. Confirmado baixando e inspecionando o arquivo real de produção (2026-09-14) com
`adm-zip`/`unzip -l` puros, fora do código do projeto: o corpo HTTP é um ZIP externo com UMA
entrada nomeada `TS<AAMMDD>.ex_` (o próprio stub self-extracting, cabeçalho MZ), cujo CONTEÚDO é
outro ZIP com a entrada real `TaxaSwap.txt` dentro. `docs/TS260914.exe` (fornecido pelo usuário)
já era o arquivo do NÍVEL INTERNO — não o corpo bruto da resposta HTTP —, o que mascarou a
necessidade do segundo desempacotamento até a primeira aquisição ao vivo real desta sessão: sem
ele, o parser do `curve-processor` recebia os primeiros ~600 bytes do stub SFX (banner "PKSFX CLI
for Windows...") em vez do TXT, e falhava com `PARSE_FAILED` — silenciosamente do lado do
consumidor (mesmo comportamento do `DefaultErrorHandler` do Spring Kafka já diagnosticado em
`ProcessarEnvelopeIngestaoUseCase`), só visível inspecionando a dead-letter topic diretamente.
Dois desempacotamentos, então (`src/feeders/b3-taxa-swap.ts`).

O arquivo real traz **106 códigos de curva diferentes, 278 vértices cada** — esta fixture é um
recorte de **3 vértices reais de cada um dos 5 códigos usados pela plataforma** (`PRE`, `DCL`,
`PTX`, `INP`, `DPL`), extraídos linha a linha de `docs/TaxaSwap.txt` (geração 2026-09-14, mesmas
linhas usadas como oráculo do layout em `B3TaxaSwapParserTest` no `curve-processor`) — sem
nenhum valor inventado ou ajustado.

O feeder não filtra nada — grava o arquivo inteiro em blob e publica **um evento por chamada**,
sob o `dataset` pedido pelo chamador (`B3_TAXA_SWAP_PRE|DCL|PTX|INP|DPL`); quem extrai só o
código de curva relevante é o `B3TaxaSwapParser` do lado do `curve-processor`, um por dataset.
