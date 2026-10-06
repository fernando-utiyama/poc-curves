## Context

- **`web/fed`**: mesmo padrão da change `fed-curvas-mercado` (tabelas do Liquid, modais, aviso por `history.state`, serviço por recurso). O `request.interceptor` força `Content-Type: application/json` e 3 s de tempo limite; a change `fed-curvas-mercado` cria o `HttpContextToken` `TEMPO_LIMITE_MS`, que esta change reaproveita. O proxy só tem `/api` → curves.
- **`services/curves`**: já tem as entidades `BtrsCurvaPrimrEntity`, `AnbmaCurvaPrimrEntity` e `BbergCurvaPrimrEntity` e o CRUD por curva da B3 (`curva-primaria-b3`, rotas `/linhas`). O projeto usa POI na auditoria.
- **Upload**: a rota `POST /api/v1/cargas/upload` é do bff (change `processor-v0`, `upload-carga-bff`), que repassa ao processor (sem autenticação na v0 e na v1; `X-Usuario` só quando vier). A data-base vem do conteúdo do arquivo.
- **Tickers**: `tCurvaPrvdr` liga cada código na fonte (`cTickerPrvdr`) a uma ou mais curvas; os feeders gravam o bruto por curva (`cTickerIndcd`), uma cópia por curva ligada.

## Goals / Non-Goals

**Goals:**
- Uma API única de bruto para os três provedores, no lugar de uma por provedor.
- A tela filtra pelo que o gestor conhece (provedor, ticker da fonte, data), e a manutenção acontece por curva, que é como o bruto está gravado.

**Non-Goals:**
- Reprocessar ou baixar de novo pela tela (é do orquestrador, change `orquestrador-v0-disparo-manual`).
- Recalcular a curva pela tela de dados de mercado (só o link para a tela Curvas, `/curvas?codigo={codigo}`, change `fed-curvas-mercado`).
- Outras fontes (CME, LCH, LSEG, Treasury) e a curva zero da ANBIMA.

## Decisions

### D1. API por provedor, código na fonte e data; manutenção por curva
Consulta por `{provedor}?codigoNaFonte=&dataBase=`, que devolve todas as curvas ligadas; gravação por `{provedor}/{codigo}/{dataBase}/vertices`. **Por quê:** um código ligado a duas curvas tem duas cópias do bruto, e editar uma não deve mexer na outra sem o gestor ver. **Alternativa rejeitada:** gravar pelo código na fonte em todas as curvas ligadas de uma vez, que esconderia a duplicação.

### D2. Backend na parte 1 da curves
A rota genérica `/dados-mercado/{provedor}` está na change `curves-cadastro-curvas` (spec `vertices-brutos-provedor`, tarefas 5.x), sobre os CRUDs dos três provedores que já existem; esta change é só o front.

### D3. Uma regra de gravação para as três tabelas
`VerticeBrutoService` com uma estratégia por provedor (`VerticeBrutoB3`, `VerticeBrutoAnbima`, `VerticeBrutoBloomberg`) que sabe os campos, a validação de coluna, os avisos e a ordem; a parte comum (trava da curva, `MAX + 1`, `CURVA_SEM_PROVEDOR`, `CURVA_JA_CONSTRUIDA`) fica no serviço. Os nomes de tabela vêm da estratégia, nunca da requisição.

### D4. Planilha como lista completa com `id`
A mesma semântica da planilha de cadastro (`cadastro-curvas-planilha`): `id` presente é alteração, ausente é inclusão, `id` gravado que falta é exclusão; simulação e aplicação de uma vez. **Alternativa rejeitada:** importar só inclusões, que obrigaria a apagar à mão antes de corrigir em lote.

### D5. Upload pelo bff, com destino próprio no proxy
`proxy.conf.js` ganha `/api/v1/cargas` → bff (`PROXY_BFF_TARGET`), declarado antes de `/api`, para o envio passar pelo bff, como manda a `upload-carga-bff`. O `request.interceptor` não põe `Content-Type` quando o corpo é `FormData`.

## Risks / Trade-offs

- [Gestor corrige uma curva e espera que a outra ligada ao mesmo código mude] → a tela mostra todas as curvas do código lado a lado, cada uma com o seu botão.
- [Aplicar a planilha apaga vértices que o gestor esqueceu de incluir] → simulação obrigatória antes, contagem de exclusões e aviso antes de aplicar.
- [Upload de um dia já carregado substitui o bruto] → aviso antes do envio; o original continua no Blob e pode ser reprocessado.
- [Correção do bruto não muda a curva já construída] → aviso `CURVA_JA_CONSTRUIDA` e link para recalcular.

## Migration Plan

1. Implantar a curves com as rotas `/dados-mercado` (change `curves-cadastro-curvas`).
2. Configurar no `fed` o destino do bff e implantar a tela. Rollback: voltar o deploy do `fed`; as rotas da curves ficam sem uso.
