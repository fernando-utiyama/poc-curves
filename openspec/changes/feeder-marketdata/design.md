## Context

Os feeders são a borda da plataforma com o mundo externo. Em produção rodam como Azure Functions em Node.js disparadas pelo `curve-orchestrator`; na POC rodam como containers Node sob Podman, disparados da mesma forma. A fonte da POC é a B3, cujos dados chegam por três caminhos diferentes: arquivos ZIP de preços de referência (PR), arquivos XML no padrão BVBG (086 para preços e ajustes, 028 para cadastro de instrumentos) e um endpoint de proxy que devolve JSON com as taxas de referência oficiais, com o pedido codificado em Base64 na URL.

As idiossincrasias desses endpoints são conhecidas — encoding ISO-8859-1, headers de browser esperados, decimal com vírgula, pedido codificado em Base64 na URL — e estão documentadas em `docs/`. O feeder é implementação nova em TypeScript, escrita contra essa documentação e contra fixtures reais gravadas.

Restrições: o feeder não acessa banco, não normaliza para o modelo de domínio, e não decide se a curva deve ser construída. Ele adquire, verifica e publica.

## Goals / Non-Goals

**Goals:**

- Um contrato de feeder que Bloomberg e LSEG possam implementar depois sem mexer no `curve-processor`.
- Aquisição confiável dos datasets B3 necessários para construir a curva PRE, com falha explícita e diagnóstico útil.
- Mesmo código de negócio rodando como Azure Function e como container local.
- Suíte de testes que roda offline, contra fixtures reais gravadas.

**Non-Goals:**

- Normalizar, validar semanticamente ou persistir o dado — isso é `curve-processor`.
- Implementar Bloomberg e LSEG.
- Agendar a si mesmo: quem agenda é o `curve-orchestrator`.
- Fazer parsing financeiro em TypeScript; o feeder publica o payload bruto e deixa a interpretação para o processor, que é Java.

## Decisions

### D1 — O feeder publica bruto, não normalizado

O payload do evento carrega o conteúdo adquirido praticamente como veio da fonte (texto ou XML, com o encoding declarado), acompanhado de metadados: URL de origem, tamanho, hash, timestamp de aquisição. Normalizar é responsabilidade do `curve-processor`.

*Alternativa considerada*: o feeder já entregar normalizado. Rejeitada — colocaria conhecimento de domínio financeiro em três linguagens diferentes (uma por fonte) e faria cada correção de parsing exigir redeploy do feeder. Com o bruto no tópico, corrigir um parser é reprocessar do offset.

### D1b — Dois tipos de insumo, declarados no evento

O feeder adquire duas naturezas distintas de conteúdo e declara qual é qual em `payloadKind`:

- **`INDIVIDUAL_QUOTES`** — dado por instrumento (PR, BVBG.086, BVBG.028). Alimenta a curva construída.
- **`READY_CURVE`** — a curva já pronta, vértice a vértice, vinda do endpoint de Taxas de Referência da B3. Alimenta a curva importada.

A distinção é declarada, não inferida: o consumidor decide o caminho de processamento por `payloadKind` e `dataset`, sem inspecionar o conteúdo. O feeder continua não interpretando nada — ele sabe *o que pediu*, e é isso que declara.

*Por que no feeder e não no processor*: só o feeder sabe qual endpoint chamou. Deixar o processor adivinhar se o payload é lista de contratos ou lista de vértices seria heurística de formato — exatamente o que D1 do processor rejeita.

### D1c — Quebra em blocos, com corte estrutural

O feeder divide o conteúdo adquirido em blocos de registros e publica um evento por bloco. O corte é sobre a estrutura do container — elementos repetidos de um XML, linhas de um arquivo texto —, sem interpretar o que os campos significam.

*A fronteira que isso preserva*: o feeder passa a conhecer **formato de container**; não conhece **domínio financeiro**. A regra que impede a erosão: nenhum código do feeder pode saber o que é taxa, vencimento ou contrato. Toda conversão numérica, derivação de chave de instrumento e política de arredondamento continua no processor, em Java.

*Por que blocos e não uma mensagem por registro*: os arquivos diários da B3 trazem todos os instrumentos negociados. Registro a registro seriam centenas de milhares de mensagens por arquivo, com o overhead por mensagem dominando o processamento. Bloco de algumas centenas de registros mantém a mensagem pequena, a contagem administrável, e limita o raio de um registro defeituoso.

*O tamanho do bloco precisa ser medido*, não estimado — depende do tamanho real dos registros de cada dataset.

*O que a quebra custa*: a atomicidade do arquivo. Volta por `totalBlocos` no envelope, e é o processor que consolida.

### D1d — A faixa vem do disparo, não da decisão do feeder

O disparo informa em qual faixa publicar: rotina, prioritária ou massa. O feeder obedece.

*Por quê*: quem sabe a urgência é quem disparou — agendamento é rotina, tela é prioritária, carga histórica é massa. Se o feeder decidisse, precisaria de heurística, e heurística de urgência erra justamente no dia atípico.

### D2 — `eventId` determinístico por conteúdo

`loteId = uuidv5(source + dataset + referenceDate + sha256(conteúdo adquirido))` e `eventId = uuidv5(loteId + sequencia)`. Reexecutar o feeder para a mesma data com o mesmo conteúdo produz o mesmo `loteId` e os mesmos `eventId`, e o consumidor idempotente ignora a repetição. Se o conteúdo da fonte mudou, tudo muda e o reprocessamento acontece de propósito.

*Alternativa considerada*: UUID aleatório por execução. Rejeitada — transferiria toda a responsabilidade de deduplicação para o consumidor, que não tem como distinguir "reenvio do mesmo dado" de "dado corrigido pela fonte".

### D3 — Três resultados distintos, nunca confundidos

O feeder termina em exatamente um de três estados: `PUBLISHED` (dado adquirido e publicado), `NO_DATA` (a fonte respondeu corretamente que não há publicação para aquela data — feriado, dia não útil, dado ainda não divulgado) ou `FAILED` (a fonte falhou, respondeu inválido ou estourou o timeout). `NO_DATA` **não** é falha e não deve alarmar; `FAILED` é falha e retenta.

*Por que importa*: hoje esses dois casos se confundem, e o resultado é ou alarme falso todo feriado, ou falha real silenciada como "sem dado".

### D4 — Retentativa no feeder é só para falha de transporte

Timeout, erro de rede e HTTP 5xx são retentados com backoff exponencial (3 tentativas, base 2s, com jitter). HTTP 4xx e resposta estruturalmente inválida não são retentados — são reportados como `FAILED` permanente para o orquestrador decidir.

### D5 — Núcleo puro, bordas finas

A lógica fica em um núcleo puro (`acquire(source, dataset, referenceDate) → Result`), sem dependência de Azure nem de Kafka. Dois adaptadores finos consomem esse núcleo: um handler de Azure Function e um `main` de container. Testar o núcleo não exige nem Azure nem broker.

*Alternativa considerada*: escrever direto contra o SDK de Azure Functions. Rejeitada — a POC roda em Podman, e um feeder que só existe dentro do runtime da Azure não seria testável aqui.

### D6 — Cliente B3 com fixtures gravadas como padrão de teste

Toda resposta real usada em teste é gravada em `test/fixtures/` com data, URL e encoding. A suíte roda offline contra elas. Existe um único teste marcado como opcional (`@contract`, fora do build padrão) que chama a B3 real e serve para detectar mudança de contrato da fonte.

*Trade-off aceito*: fixture envelhece. Mitigação: o teste de contrato opcional é rodado deliberadamente quando se suspeita de mudança, e o resultado atualiza a fixture.

### D7 — Encoding e decimal explícitos na borda

Arquivos da B3 vêm em ISO-8859-1 com decimal por vírgula. O feeder declara o encoding no metadado do evento e **não converte números** — publica o texto como veio. Converter é do processor, que tem a política de arredondamento.

### D8 — Calendário de pregão vem da fonte, não de palpite

Para decidir se uma data deveria ter publicação, o feeder usa o calendário B3 com os feriados reais, exposto ao worker como dado de configuração e mantido em sincronia com o calendário do `curve-kernel`. Não infere "é fim de semana logo não há dado" a partir do dia da semana apenas.

## Risks / Trade-offs

- **Endpoints da B3 mudam sem aviso e sem versionamento** → cliente isolado atrás de uma interface, fixtures gravadas, teste de contrato opcional. Quando muda, muda em um arquivo.
- **Payload bruto no tópico aumenta o volume do Kafka** → retenção de 7 dias no tópico raw; arquivos diários da B3 são pequenos o suficiente para essa janela.
- **`NO_DATA` mal classificado esconde falha real** → os três estados são requisito de spec com cenário de teste próprio, e o orquestrador registra `NO_DATA` de forma visível na tela de execuções, não como sucesso silencioso.
- **Conhecimento da fonte se espalhar entre TypeScript (feeder) e Java (processor)** → mitigado por D1: o feeder não interpreta, só adquire; o conhecimento financeiro fica só no Java.
- **Azure Function e container divergirem** → mitigado por D5: os dois adaptadores são casca fina sobre o mesmo núcleo, e o teste cobre o núcleo.
- **Fixtures envelhecem e o teste passa contra uma realidade que não existe mais** → o teste de contrato opcional é a válvula; documentar quando rodá-lo.

## Migration Plan

1. Implementar o núcleo `acquire` com o cliente B3 e as fixtures, testado offline.
2. Adicionar o publicador Kafka com envelope validado contra o schema de `contracts/`.
3. Adicionar o adaptador de container e subi-lo no compose Podman.
4. Adicionar o adaptador de Azure Function (não implantado na POC, mas compilado e testado).
5. Integrar com o `curve-orchestrator` para disparo e reporte de progresso.

**Rollback**: o feeder é sem estado; reverter é voltar a imagem anterior. Dado já publicado no tópico permanece válido e reprocessável.

## Open Questions

- Bloomberg e LSEG entregam por pull agendado, por API sob demanda ou por stream? Se houver push, o contrato de feeder precisa de um segundo modo de acionamento.
- O ambiente corporativo exige proxy autenticado para sair para a internet? Afeta a configuração do cliente HTTP em produção, não na POC.
- Qual a janela de divulgação da B3 por dataset? Determina o horário do agendamento e o tempo até declarar `NO_DATA` em vez de esperar.
