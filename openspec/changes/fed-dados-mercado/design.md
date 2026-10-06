## Context

- **`web/fed`**: mesmo padrão da change `fed-curvas-mercado` (tabelas do Liquid, modais, serviço por recurso, `lerErro`). O `request.interceptor` força `Content-Type: application/json` e 3 s de tempo limite; a change `fed-curvas-mercado` cria o `HttpContextToken` `TEMPO_LIMITE_MS`, que esta change reaproveita. O proxy só tem `/api` → curves.
- **`services/curves`** (v1, já implantada): os CRUDs do bruto dos três provedores existem, com a mesma forma de rota (relatório `docs/inspecao/inspecao-curves.md` do poc): listagem geral `GET /curvas-mercado/primaria-{b3|anbima|bloomberg}`, consulta `GET /curvas-mercado/{codigo}/primaria-{p}/{dataBase}`, `POST/PUT/DELETE .../vertices[/{id}]` e `DELETE .../{dataBase}`. A B3 devolve decimais como texto; ANBIMA e Bloomberg, como número.
- **Upload**: a rota `POST /api/v1/cargas/upload` é do bff (change `processor-v0`, `upload-carga-bff`). A data-base vem do conteúdo do arquivo.

## Goals / Non-Goals

**Goals:** a tela de bruto e o upload com o backend que já existe, sem mudança na curves.

**Non-Goals (v2):** rota unificada `/dados-mercado/{provedor}` e filtro por ticker da fonte; planilha dos vértices brutos; reprocessar ou baixar de novo pela tela (orquestrador); recalcular pela tela (só o link para a tela Curvas).

## Decisions

### D1. Usar os CRUDs `primaria-*` como estão
Um serviço no `fed` com o provedor como parâmetro monta o caminho (`primaria-b3`, `primaria-anbima`, `primaria-bloomberg`). **Por quê:** os três CRUDs têm a mesma forma de rota e já estão implantados; a rota unificada fica para a v2 sem bloquear a tela. **Alternativa rejeitada:** esperar a rota unificada.

### D2. Campos de cada provedor lidos dos DTOs reais
As colunas e os campos dos modais seguem os DTOs de resposta e de entrada que a curves já tem para cada provedor (a B3 está no guia; ANBIMA e Bloomberg o agente confere no código da curves ou no Swagger). **Por quê:** o contrato é o que está implantado; inventar nomes quebraria a gravação.

### D3. Decimais exibidos sem conta
O front só troca ponto por vírgula na exibição e vírgula por ponto no envio; nunca faz conta nem arredonda. Número vindo como JSON numérico é convertido com `String(valor)`.

### D4. Upload pelo bff, com destino próprio no proxy
`proxy.conf.js` ganha `/api/v1/cargas` → bff (`PROXY_BFF_TARGET`), declarado antes de `/api`. O `request.interceptor` não põe `Content-Type` quando o corpo é `FormData`.

## Risks / Trade-offs

- [ANBIMA e Bloomberg devolvem número, e o JavaScript pode mostrar menos casas em valores longos] → aceito na v1 (exibição); a curves passa a devolver texto na v2.
- [Upload de um dia já carregado substitui o bruto] → aviso antes do envio; o original continua no Blob e pode ser reprocessado.
- [Correção do bruto não muda a curva já construída] → lembrete e link para recalcular.

## Migration Plan

Configurar no `fed` o destino do bff e implantar a tela. Rollback: voltar o deploy do `fed`.
