## Context

A ingestão de curva pronta já existe e é genérica: `imported-curve-ingestion` (curve-processor) roteia por `payloadKind: READY_CURVE`, resolve a `definicao_curva` pelo par (fonte, identificador de curva na origem) e publica sem reinterpolar. Hoje só um par existe (`B3`/`PRE`, via `referenceRatesProxy`). Este design cobre como alimentar esse mesmo pipeline com quatro novos pares (`B3`/`DCL`, `B3`/`PTX`, `B3`/`INP`, `B3`/`DPL`), a partir de uma fonte diferente da já usada para PRE.

Achados reais desta sessão, verificados no arquivo baixado (`docs/TaxaSwap.txt`, 30.481 linhas, 15/09/2026) e no Manual de Curvas da B3 (`docs/Manual de Curvas_V21.pdf`, ver proposal.md):

- O arquivo é de largura fixa, **72 caracteres por linha** (confirmado: `awk '{print length}' | sort -u` retorna um único valor), terminador `\r\n`.
- Contém **106 códigos de curva diferentes**, 278 vértices cada, indo até 12.390 dias úteis (~49 anos) — cobre PRE, DCL, PTX, INP, DPL e muitos outros (IPCA, EURO, ARS, IBOVESPA, etc.) não usados por esta mudança.
- Layout aparente por linha (não confirmado formalmente — ver Riscos): número sequencial, timestamp da rodada, código da curva de 3 letras, descrição truncada, par de contadores de prazo (dias corridos/dias úteis), sinal, valor escalado, flag de um caractere (`F`/`M`, significado não confirmado).
- Os valores de PRE extraídos manualmente do arquivo (`13.90`, `13.80`...) batem visualmente com os vértices já validados de PRE via `referenceRatesProxy` (fixture `b3-curva-pre-20260821_fixture.csv`) — mas isso foi conferência visual, não automatizada.
- O Manual de Curvas confirma que os valores publicados no arquivo já são o resultado final do cálculo da B3 (ela já aplicou Flat Forward 252 / Flat Forward 252 Linear / Interpolação de Preços / Implícita+Flat Forward internamente) — quem consome não recalcula nada, só transcreve.
- O Manual também mostra que os quatro tipos de valor divergem por curva: PRE/DCL são **taxa** (DCL com 2 casas, PRE com 3), PTX é **preço** de câmbio (7 casas, truncado), INP é **pontos de índice** (2 casas). O pipeline de ingestão já trata o valor como decimal opaco por vértice — não precisa saber a semântica, mas o parser precisa aplicar a escala/casas corretas por curva ao decodificar o campo numérico do layout.

## Goals / Non-Goals

**Goals:**
- Publicar DCL, PTX, INP e DPL como curvas reais da plataforma (`definicao_curva` com modo `IMPORTED`), consultáveis pelas mesmas APIs que já servem PRE.
- Validar automaticamente, por oráculo cruzado com PRE, que o layout do `TaxaSwap.txt` foi decodificado corretamente antes de confiar nos outros quatro códigos.
- Isolar o pacote de construção/interpolação de curva do curve-engine (`application/model` → `application/construcao`) sem mudar comportamento, para não seguir acumulando classes não relacionadas no mesmo pacote.

**Non-Goals:**
- Reimplementar as metodologias de cálculo da B3 (Flat Forward 252 Linear, Interpolação de Preços, Implícita+Flat Forward) como modelos `BUILTIN` do curve-engine. Os insumos brutos necessários (Ptax Bacen, IPCA IBGE, prévia/NTN-B Anbima, futuros DDI/DAP/IND) não são ingeridos por este projeto e ficam fora de escopo.
- Migrar a curva PRE, hoje importada via `referenceRatesProxy`, para o `TaxaSwap.txt`. PRE continua vindo de `referenceRatesProxy`; sua extração do `TaxaSwap.txt` nesta mudança serve só como oráculo de validação do parser, não substitui a fonte de produção.
- Cobrir os ~100 demais códigos de curva presentes no mesmo arquivo (IPCA, moedas, outros índices). Ficam para extensão futura, quando/se houver `definicao_curva` cadastrada para eles — o parser já ignora por design qualquer código não mapeado.

## Decisions

**D1. Fonte: `TaxaSwap.txt`, não expandir `referenceRatesProxy` para os 4 códigos novos.**
O Manual de Curvas nomeia esse arquivo como a fonte oficial ("Mercado Derivativos – Taxas de Mercado para Swaps"). O README de fixtures do `function-marketdata` só confirma `PRE` no `referenceRatesProxy` ao vivo — `DPL` e `PTX` nem estão na lista de códigos vistos lá. Uma aquisição só (um arquivo por dia) cobre as quatro curvas, contra quatro chamadas HTTP separadas e não confirmadas no outro endpoint.

**D2. PRE como oráculo de validação do layout, não como saída de produção.**
Como o layout de largura fixa não tem documentação formal no projeto, a extração de PRE do `TaxaSwap.txt` serve para provar que o parser decodifica corretamente prazo e valor antes de confiar em DCL/PTX/INP/DPL — comparando contra os vértices de PRE já validados via `referenceRatesProxy` para a mesma data. Alternativa descartada: confiar direto no layout sem oráculo, arriscando publicar taxa errada silenciosamente (violaria a regra do projeto de nunca fabricar/estimar valor de mercado).

**D3. Reaproveitar 100% o pipeline genérico de curva importada.**
`imported-curve-ingestion` já roteia por `payloadKind`/mapeamento fonte+identificador, sem acoplamento ao código PRE. Nenhuma mudança de requisito nesse capability — só cadastro de quatro `definicao_curva` novas e um novo par (dataset, parser) no lado da aquisição.

**D4. Reorganização de pacote no curve-engine é refactor isolado, sem relação funcional com as curvas novas.**
`CurveBootstrapper`, `Interpolador*`, `RateHelper`, `SplineCubicaNatural`, `PoliticaExtrapolacao*`, `ConvencaoContagemDias`, `RoundingPolicy`, `Vertice`, `CurvaJuros` saem de `application/model` (hoje 40+ classes, misturando modelo de domínio com matemática de construção) para `application/construcao`. Não migra `ModeloCurva`, `VersaoCurva`, `ProcedenciaCurva` nem os demais modelos de domínio — esses ficam em `application/model`. Puramente mecânico: mover classes, ajustar `package`/imports, sem tocar lógica. Feito nesta mudança porque foi pedido junto, mas não depende nem é dependência das curvas novas — pode ser feito e revisado independentemente.

## Risks / Trade-offs

- **[Risco] Layout de largura fixa do `TaxaSwap.txt` não é documentado formalmente em lugar nenhum (nem no Manual de Curvas, nem em nota técnica da B3 localizada nesta sessão).** → Mitigação: D2 (oráculo cruzado com PRE) roda a cada execução, não só em teste — se a extração de PRE do TS divergir da fonte já validada, a execução inteira falha sem publicar nada (ver spec, "Divergência detectada"). Adicionalmente, a tarefa de implementação inclui documentar o layout inferido (posição de cada campo) como comentário/fixture no parser, para futuras curvas.
- **[Risco] Significado da flag de um caractere (`F`/`M`) por vértice não é conhecido.** → Mitigação: não bloqueia esta mudança (os campos usados são só prazo e valor); registrar como questão em aberto e não usar a flag para nenhuma decisão até seu significado ser confirmado.
- **[Risco] Escala/casas decimais do valor variam por curva (taxa vs. preço vs. pontos de índice) e não há indicação disso dentro do próprio arquivo.** → Mitigação: escala e casas decimais por curva ficam explícitas na configuração do parser (não inferidas do conteúdo), com o valor de cada curva conferido manualmente contra a fixture antes de cadastrar a `definicao_curva` correspondente.
- **[Trade-off] Uma aquisição por dia cobrindo 4 curvas cria uma dependência de tudo-ou-nada na leitura do arquivo** (se o parsing do arquivo falhar, nenhuma das 4 falha independentemente na aquisição — só na extração por código, que já é isolada por código conforme spec "Publicação independente por curva"). Aceito porque a extração por código já isola falhas por curva individualmente depois da aquisição.

## Migration Plan

- Migração Flyway aditiva: 4 novos registros em `definicao_curva` (DCL, PTX, INP, DPL), modo `IMPORTED`, fonte `B3`. Sem alteração de schema.
- Novo feeder e parser são aditivos (novo dataset, novo `DatasetParser`) — não tocam nos feeders/parsers existentes de PR/IN/PRE.
- Reorganização de pacote no curve-engine é um commit isolado (mover + ajustar imports), sem mudança de schema nem de contrato de API — build verde é o critério de sucesso.
- Rollback: reverter a migração (remover os 4 registros) e desregistrar o dataset no feeder — sem impacto em PRE nem nos demais dados já publicados.

## Open Questions

- Significado da flag `F`/`M` por vértice no `TaxaSwap.txt` — não bloqueia esta mudança, mas vale confirmar com a B3 ou por engenharia reversa adicional antes de usar o arquivo para mais códigos de curva no futuro.
- Horário de publicação do `TaxaSwap.txt` no dia de pregão (para calibrar o agendamento no curve-orchestrator) — a ser observado empiricamente durante a implementação, como já foi feito para PR/IN/PRE.
