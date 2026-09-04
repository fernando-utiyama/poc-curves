# Fixtures reais B3 (tarefa 6.1)

Recortes reais dos arquivos oficiais da B3, para testar o corte estrutural
sem precisar dos arquivos de produção (175–800 MB cada, inviável de versionar).

| Arquivo | Origem | Data de captura | Encoding |
|---|---|---|---|
| `BVBG.086.01_fixture.xml` | `PR260821.zip` → `BVBG.086.01_BV000328202608210328000001840237278.xml` (Preços de Referência / negócios) | 2026-08-21 | UTF-8 |
| `BVBG.028.02_fixture.xml` | `IN260821.zip` → `BVBG.028.02_BV000327202608210327117330691294583.xml` (Cadastro de instrumentos) | 2026-08-21 | UTF-8 |
| `b3-curva-pre-20260821_fixture.csv` | Curva pronta DI x PRÉ, endpoint `sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy` (ver seção abaixo) | 2026-08-21 | ISO-8859-1 |

Cada fixture contém os primeiros **5 elementos `<BizGrp>` reais e completos**
do arquivo original (nomes de campo, valores e estrutura idênticos ao
arquivo de produção), com `<TtlNbOfMsg>` ajustado de volta para `5` para
bater com a contagem real da fixture. O arquivo original tinha
`<TtlNbOfMsg>76015</TtlNbOfMsg>` (PR) e `<TtlNbOfMsg>223700</TtlNbOfMsg>` (IN).

Os dois compartilham o mesmo envelope externo — `BizFileHdr > Xchg >
BizGrpDesc` (com `TtlNbOfMsg` declarando a contagem) seguido de `<BizGrp>`
repetido — confirmando que o corte estrutural (D1b do design.md: "este XML
tem N elementos repetidos") é feito pela contagem de `<BizGrp>`, igual para
os dois datasets.

A URL/endpoint real de download por data foi confirmada nesta sessão
(`https://www.b3.com.br/pesquisapregao/download?filelist=<PREFIXO><AAMMDD>.zip`
— `PR`/`IN`) e é usada de verdade em `src/feeders/b3-arquivo-pesquisa-pregao.ts`
e no teste de contrato (`src/feeders/b3-pesquisa-pregao.contract.ts`, tarefa
6.8), que baixa e processa os arquivos reais de produção (175–800 MB) contra
a B3 ao vivo — as fixtures aqui continuam servindo para a suíte padrão, que
não depende de rede.

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
