## Why

A POC hoje só ingere B3, a fonte de dados desenhada desde o início. `curves-solution-architecture` já previa Bloomberg como uma segunda fonte de mercado no desenho-alvo, mas deixava em aberto se a entrega dele seria por arquivo, API ou stream — questão nunca respondida porque nenhuma curva além de PRE/DI1 exigia isso ainda. O usuário agora pede Bloomberg como fonte real, especificamente no padrão que ele já viu funcionar em outro contexto: pedir um arquivo e, depois, ler o arquivo gerado — o padrão real de entrega em lote do Bloomberg Data License (DL), assíncrono por natureza, bem diferente do pull síncrono imediato que `Feeder.acquire()` assume hoje para B3/ANBIMA/BCB.

## What Changes

- Adiciona `FeederBloomberg`, implementando o contrato `Feeder` já existente (`acquire(params): Promise<AcquisitionResult>`, sem mudança de assinatura) — cobrindo o fluxo real de Bloomberg Data License por arquivo (não a API de sessão BLPAPI, que é outro produto e outro padrão). O caráter assíncrono (submeter pedido → aguardar prontidão → buscar arquivo gerado → parsear) fica encapsulado inteiramente dentro do `acquire()` do feeder novo — a Promise já suportava esperar o tempo que for preciso, então não há mudança de contrato.
- **Restrição real de uso, não um novo estado no contrato**: um `acquire()` que pode levar minutos a horas não é apropriado para o caminho de disparo manual instantâneo (`function-marketdata-http`, chamado por `curve-orchestrator` numa requisição HTTP que o operador espera na tela). O feeder Bloomberg só se registra para o modo CLI/agendado (`main.ts`, já desenhado para "uma aquisição por execução, ninguém espera em tempo real" — ver `docs/extensao-feeders.md`); se disparado via HTTP síncrono, devolve `FAILED` nomeando que a fonte é assíncrona e precisa do modo agendado. **Nenhuma mudança em `curve-orchestrator` é necessária** — ele já trata `FAILED` como um resultado terminal válido.
- Adiciona um modelo de dado de instrumento genérico (classe de ativo, tipo de instrumento, ticker, campo/medida, valor, data), não amarrado à forma DI1-específica dos feeders B3 — pensado para servir juros, câmbio e outros insumos de curva que o Bloomberg tipicamente fornece, sem fixar de antemão uma lista de campos Bloomberg reais (são milhares; a lista fica configurável, não hardcoded).
- **Não verificado contra o Bloomberg real**: sem credenciais/ambiente Bloomberg disponíveis nesta sessão, ao contrário do que foi feito com B3 (testado contra a API real da B3 ao longo deste projeto). A integração é construída e coberta por teste com fixtures, mas essa limitação fica documentada como risco conhecido, não escondida.
- Atualiza `curves-solution-architecture/design.md`: remove Bloomberg do non-goal de feeders não implementados e responde a pergunta em aberto sobre o modo de entrega (arquivo, assíncrono) — já aplicado nesta sessão, antes deste proposal, para os dois documentos não ficarem contraditórios entre si.

## Capabilities

### New Capabilities

- `feeder-bloomberg`: o feeder Bloomberg — submissão de pedido, polling/espera de arquivo pronto (dentro do próprio `acquire()`, sem mudar o contrato `Feeder`), download, parsing para o modelo de instrumento genérico, publicação no envelope de evento já existente (`source: BLOOMBERG`, já previsto desde `curves-solution-architecture` D2). Registrado só para o modo CLI/agendado, não para o disparo manual instantâneo via HTTP.

### Modified Capabilities

<!-- Nenhuma capability existente de openspec/specs/ muda requisito — function-marketdata ainda não tem specs formais publicadas nesta árvore (mudança em andamento, não arquivada); a extensão do contrato de feeder é tratada como capability nova (`feeder-async-acquisition`), não como alteração de uma spec existente. -->

## Impact

- `services/function-marketdata/src/feeder.ts`: nenhuma mudança — `Feeder`/`AcquisitionParams`/`AcquisitionResult` continuam exatamente como estão.
- `services/function-marketdata/src/registro-feeders*.ts`: registro do novo feeder Bloomberg, restrito ao caminho CLI/agendado — mecanismo exato (registro separado vs. guarda no handler HTTP) é decisão de design, ver design.md.
- Novo diretório `services/function-marketdata/src/feeders/bloomberg-*.ts` (submissão, polling, parsing) + fixtures de teste (arquivo de resposta Bloomberg simulado, já que não há acesso real).
- `contracts/events/envelope.schema.json`: `source: BLOOMBERG` já é um valor previsto (D2 de `curves-solution-architecture`) — nenhuma mudança de schema esperada aqui, só o novo `dataset` value real para o(s) instrumento(s) Bloomberg.
- `openspec/changes/curves-solution-architecture/design.md`: non-goal e pergunta em aberto sobre Bloomberg atualizados (já aplicado).
- **Fronteira explícita de escopo**: esta mudança cobre só `function-marketdata` — aquisição e publicação do dado bruto (`marketdata.*.v1`), como o nome do serviço e o pedido original definem. `curve-processor` precisará de um parser novo (mesmo padrão de `Bvbg086PricRptParser`/`DatasetParserRegistry` já existente) para transformar o payload bruto Bloomberg em `ponto_dado_mercado` — isso é um item de acompanhamento no backlog de `curve-processor`, não desta mudança; sem ele, o dado Bloomberg publicado fica em `marketdata.*.v1`/dead-letter (dataset desconhecido) até o parser existir, mesmo comportamento já visto e documentado para qualquer dataset novo sem parser registrado. `curve-engine`/`curve-orchestrator` não mudam.
