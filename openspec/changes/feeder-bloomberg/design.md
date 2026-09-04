## Context

Ver `proposal.md` para a motivação. Fatos reais do código existente, verificados nesta sessão antes de desenhar (não presumidos):

- `Feeder.acquire(params: AcquisitionParams): Promise<AcquisitionResult>` (`src/feeder.ts`) — já retorna uma `Promise`, então já suporta esperar o tempo que for necessário sem mudança de assinatura. `AcquisitionResult` é uma união de três estados: `PUBLISHED`, `NO_DATA`, `FAILED`.
- `EventSource` (`src/envelope.ts`) já inclui `'BLOOMBERG'` — nenhuma mudança de schema de envelope é necessária para declarar a origem.
- `PayloadKind` é `'INDIVIDUAL_QUOTES' | 'READY_CURVE'` — cotações individuais Bloomberg (juros, câmbio, outros insumos) mapeiam para `INDIVIDUAL_QUOTES`, o mesmo tipo já usado por BVBG.086. Nenhum terceiro valor é necessário.
- Todo feeder publica **fragmentos estruturais brutos** (`{"raw": "..."}`, corte por `dividirEmBlocos`/`dividirXmlEmBlocos`) — a extração semântica (campo a campo) acontece depois, em `curve-processor`, por um `DatasetParser` registrado por dataset. Nenhum feeder hoje faz parsing semântico antes de publicar.
- Três pontos de entrada compartilham a mesma construção de registro de feeders via `montarRegistroCompleto(enviar)` (`registro-feeders-completo.ts`): `main.ts` (CLI, um disparo por execução, ninguém espera em tempo real — perfil `feeder-on-demand`), `main-http.ts` (servidor HTTP sempre no ar, é o alvo real de `curve-orchestrator` no disparo manual/agendado da tela) e `azure-function-handler.ts` (equivalente HTTP em produção Azure).
- `docs/extensao-feeders.md` já registra a pergunta em aberto sobre modo de entrega (arquivo/API/stream) sem resposta — respondida agora para Bloomberg (ver proposal.md e D-1 abaixo).

## Goals / Non-Goals

**Goals:**

- Um feeder Bloomberg real, no molde do contrato `Feeder` já existente, cobrindo o fluxo de entrega por arquivo em lote assíncrono (submeter → aguardar → buscar → cortar em blocos → publicar).
- Um modelo de registro genérico o bastante para representar juros, câmbio e outros insumos de curva — sem fixar uma lista de campos Bloomberg reais não confirmados.
- Uso seguro do modo assíncrono: nunca bloqueado atrás de uma chamada HTTP síncrona que o operador espera na tela.

**Non-Goals:**

- Parsing semântico do payload Bloomberg em `curve-processor` (novo `DatasetParser`) — item de acompanhamento no backlog de `curve-processor`, fora desta mudança (ver "Fronteira explícita de escopo" em proposal.md).
- Qualquer verificação contra o Bloomberg real — sem credenciais/ambiente disponíveis nesta sessão (ver D-3 e Risks abaixo).
- BLPAPI/Server API (sessão, subscription) — produto e padrão de integração diferentes; fora de escopo até que o usuário confirme que é esse o produto real em uso.
- LSEG — continua fora de escopo, sem pedido do usuário.
- Autenticação/credenciamento Bloomberg real (certificados, IPs autorizados, etc.) — desenhado como ponto de configuração, não implementado contra um ambiente real.

## Decisions

### D-1 — Bloomberg Data License: pull assíncrono, não stream nem pull imediato

Responde a pergunta em aberto de `docs/extensao-feeders.md`. O padrão real e documentado do Bloomberg Data License (o produto que corresponde à descrição do usuário — "pede e depois lê o arquivo gerado") é: submeter um pedido de dados (arquivo de requisição ou chamada de API), Bloomberg processa em lote fora de banda (minutos a horas), e disponibiliza um arquivo de saída para retirada (SFTP tradicionalmente; também existe uma API HTTP mais nova, "Data License Plus" — qual exatamente será confirmado quando houver acesso real, ver D-3). Isso não é nem o pull imediato de B3/ANBIMA/BCB nem um stream push — é uma terceira categoria: pull em duas fases, com espera no meio.

*Alternativa considerada*: tratar como stream e desenhar um listener/webhook. Rejeitada — Data License não empurra notificação; quem consome tem que perguntar ("o arquivo já está pronto?") até a resposta ser sim. Um webhook get add-on da Bloomberg fica como extensão futura, não o caminho principal.

### D-2 — Sem novo estado no contrato `Feeder`; espera fica dentro do `acquire()`

`AcquisitionResult` continua com três estados (`PUBLISHED`/`NO_DATA`/`FAILED`) — não ganha um quarto estado `PENDING`. O polling (submeter, esperar, checar de novo) acontece inteiramente dentro da implementação de `FeederBloomberg.acquire()`, que só resolve a `Promise` quando o resultado final é conhecido (publicado, sem dado, ou falhou).

*Alternativa considerada*: expor `PENDING` no contrato e empurrar a responsabilidade de repetir a chamada para quem dispara (`curve-orchestrator`). Rejeitada — exigiria mudança em `curve-orchestrator` (`AquisicaoExecutionService`/`DisparoManualService` teriam que aprender a re-perguntar), quebrando a fronteira de escopo desta mudança (só `feeder-marketdata`) e complicando o rastreio de `execucao_curva` (que hoje assume só os três estados terminais). Manter a espera interna ao feeder é mais simples e não vaza a assincronia do Bloomberg para o resto da plataforma.

### D-3 — Não verificado contra Bloomberg real; construído contra fixture

Sem credenciais/ambiente Bloomberg disponíveis nesta sessão (diferente do que foi feito com B3, testado contra a API real ao longo deste projeto). `FeederBloomberg` é implementado e coberto por teste contra uma fixture de resposta simulada (formato de arquivo de saída modelado a partir da documentação pública do Data License, sem inventar campos não documentados), mas **isso não é o mesmo que uma integração verificada**. Documentado aqui como risco, não escondido nem apresentado como resolvido — ver Risks abaixo e a tarefa correspondente em tasks.md, que fecha como "construído, não verificado" em vez de "concluído".

*Alternativa considerada*: adiar toda a implementação até haver acesso real, como o non-goal original de `curves-solution-architecture` sugeria. Rejeitada porque o usuário pediu explicitamente para prosseguir sem acesso agora — o valor de ter a estrutura genérica pronta e testável (mesmo sem verificação real) supera esperar.

### D-4 — Modelo de registro genérico: `RegistroInstrumentoBruto`

O corte estrutural que `FeederBloomberg` publica usa um formato de registro genérico o bastante para juros, câmbio e outros insumos, alinhado ao mesmo espírito de `PontoDadoMercado` (curve-processor) sem replicar seu shape exato (que é specific de curva de juros — `chaveInstrumento`/`tipoCotacao`/`valor`/`dataVencimento`). Nesta camada (aquisição, não persistência), o registro fica como:

```ts
interface RegistroInstrumentoBruto {
  readonly classeAtivo: string;    // ex.: 'JUROS', 'CAMBIO', 'INFLACAO', 'CREDITO' — não é enum fechado
  readonly tipoInstrumento: string; // ex.: 'SWAP', 'FUTURO', 'TITULO' — livre, definido pelo campo real Bloomberg
  readonly ticker: string;          // identificador Bloomberg do instrumento (ex.: campo BB_TICKER)
  readonly campo: string;           // nome do campo/medida Bloomberg (ex.: PX_LAST) — NÃO uma lista fixa, ver abaixo
  readonly valor: string;           // texto bruto, sem conversão numérica aqui — parsing/arredondamento é trabalho do curve-processor
  readonly dataReferencia: string;  // ISO, a data do dado
}
```

`classeAtivo`/`tipoInstrumento`/`campo` são `string` livre, não enums fechados — porque os valores reais dependem de quais campos Bloomberg o pedido real vai pedir, e isso não foi confirmado (D-3). Fixar um enum agora seria inventar um contrato que a API real pode não respeitar. `valor` fica como texto (nunca convertido para número aqui) — decisão consistente com a regra do projeto de nunca fazer parsing semântico no feeder (ver Context acima) e com a regra de precisão do projeto (conversão numérica com política de arredondamento explícita acontece no parser do curve-processor, não aqui).

*Alternativa considerada*: reusar `PontoDadoMercado` diretamente. Rejeitada — esse tipo já tem forma de curva de juros (`dataVencimento` obrigatório, sem `classeAtivo`) e vive em `curve-processor`, não em `feeder-marketdata`; forçar o feeder a montar esse shape seria fazer parsing semântico cedo demais, misturando a fronteira que o resto do serviço já respeita (feeder = estrutural, processor = semântico).

### D-5 — Restrição a CLI/agendado: registro separado, não guarda em tempo de execução

Para impedir que alguém dispare Bloomberg via `POST /acquire` síncrono (`feeder-marketdata-http`, usado por `curve-orchestrator`) e prenda a requisição por horas, `registrarFeedersBloomberg(registro, enviar)` **não** entra em `montarRegistroCompleto()` (a função compartilhada pelos três pontos de entrada). Em vez disso, só `main.ts` chama `registrarFeedersBloomberg` depois de montar o registro completo:

```ts
const registro = montarRegistroCompleto(produtor.enviar);
registrarFeedersBloomberg(registro, produtor.enviar); // só aqui, CLI/agendado
```

`main-http.ts` e `azure-function-handler.ts` continuam só com `montarRegistroCompleto()`. Um disparo HTTP para o dataset Bloomberg cai no caminho já existente e testado de `DatasetNaoSuportadoError` (400, imediato) — falha rápida e nomeada, não um `FAILED` depois de esperar.

*Alternativa considerada*: registrar em todo lugar e checar um limite de tempo curto dentro do `acquire()`, devolvendo `FAILED` com "ainda processando" se estourar via HTTP. Rejeitada — exigiria `FeederBloomberg` saber por qual caminho foi chamado (não há esse dado em `AcquisitionParams` hoje, e adicioná-lo seria a mudança de contrato que D-2 evitou), e o resultado (uma falha só depois de esperar) é pior experiência que a rejeição imediata do registro separado.

## Risks / Trade-offs

- **[Risco] Não verificado contra Bloomberg real** → Mitigação: fixture de teste modelada a partir de documentação pública, comportamento documentado explicitamente como "construído, não verificado" em tasks.md e no proposal — nunca reportado como concluído com confiança que não existe.
- **[Risco] Formato real do arquivo de saída Data License pode divergir do assumido** → Mitigação: parsing do arquivo bruto para `RegistroInstrumentoBruto` fica isolado num único módulo (`feeders/bloomberg-parser.ts`), fácil de trocar quando houver acesso real; o corte estrutural (`dividirEmBlocos`) e a publicação no Kafka não dependem do formato exato.
- **[Risco] Timeout de espera muito curto or muito longo** → Mitigação: timeout configurável via variável de ambiente (ex. `BLOOMBERG_MAX_WAIT_MS`), default generoso (horas) adequado ao modo agendado/batch — nunca usado no caminho HTTP (D-5 já impede isso estruturalmente).
- **[Trade-off] Dado Bloomberg publicado mas sem parser em curve-processor até esse trabalho ser feito à parte** → Aceito conscientemente (D-Non-Goals) — o dado fica em dead-letter por dataset desconhecido até o parser existir, mesmo comportamento já visto e entendido para qualquer feeder novo sem parser correspondente ainda.

## Migration Plan

Aditiva, sem breaking change: novo feeder, novo módulo de registro restrito ao CLI, nenhuma mudança em contrato, schema ou serviço existente. Rollback é remover o registro em `main.ts` (uma linha) — nenhum estado persistido depende disso até o parser de `curve-processor` (fora de escopo) existir.

## Open Questions

- Qual exatamente é o produto Bloomberg em uso — Data License clássico via SFTP, ou "Data License Plus" via HTTP? Determina o transporte real de submissão/retirada. Genuinamente adiável: a interface `FeederBloomberg`/`RegistroInstrumentoBruto` desta mudança não muda dependendo da resposta, só a implementação do transporte dentro dela.
- Quais campos Bloomberg reais (`campo` em `RegistroInstrumentoBruto`) o pedido real vai trazer para juros/câmbio/outros insumos? Não decidido aqui de propósito (D-4) — só resolve quando houver acesso real ou uma lista confirmada pelo usuário.
