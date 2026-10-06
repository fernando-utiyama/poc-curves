Primeira parte do engine: construir, gravar, consultar e interpolar as 7 curvas iniciais (5 da B3, NTN-B e SOFR) com os modelos e calendários nativos, e as rotas básicas que o processor, o `services/curves` e o orquestrador chamam. O guia de implementação e o design estão nesta change ([`implementacao.md`](implementacao.md) e [`design.md`](design.md)) e valem também para a segunda parte, a change `engine-modelos-curva`; cada tarefa abaixo é **uma funcionalidade** a conferir: o que tem de fazer, onde está a regra (spec e seção do guia) e quando está pronta. A ordem desta lista vale sobre a seção 15 do guia. Os "pronto quando" são conferidos **lendo o código**: não rode `mvn test` nem a suíte; `mvn -q compile` só depois de alterar código. Antes da primeira tarefa, se ainda não houver `inspecao-engine.md`, faça a inspeção de [`verificacao.md`](verificacao.md) e pare.

> **Esta change é de conferência: boa parte do engine já foi desenvolvida no repositório real**, muitas vezes com outro nome (por exemplo, `Ponto` ou `Linha` no lugar de `Vertice`, ou em inglês). Esta change já foi aplicada no repositório real numa versão anterior, antes de ser separada em partes e renomeada; por isso há muita coisa feita, com nomes antigos e com peças que hoje estão em outra change (ex.: `engine-modelos-curva`) ou saíram. Cada tarefa SHALL seguir este roteiro:
> 1. **Conferir:** procurar pela **funcionalidade** (o que faz, que tabela lê ou grava, que rota expõe), nunca só pelo nome da spec.
> 2. Decidir uma de três saídas:
>    - **já existe e cumpre a spec → deixar como está** (não reescrever, não "melhorar"; só o nome segue o passo 3);
>    - **existe e diverge → alterar** só o que diverge, no código que já existe;
>    - **não existe → criar**, com os nomes desta change.
> 3. Nunca criar uma segunda versão do que já está pronto. **Nome diferente do desta change é divergência:** renomeie para o nome da change (classe, arquivo, método, campo interno e todas as referências, até compilar), menos o que outro serviço ou o banco já usa (rota exposta, campo de JSON de resposta, coluna, tópico): isso fica como está e vai para a anotação como `nome-real → nome-da-change`.
> 4. Ao marcar a tarefa, anotar ao lado a saída e o arquivo: `[conferido: pronto | alterado | renomeado de X | criado] caminho/Arquivo.java`. Na dúvida se é a mesma coisa, `// TODO(revisao): <dúvida>` e siga.
> 5. Código que a spec atual não pede (sobra da versão anterior): não apagar; anotar como `[sobra] caminho/Arquivo.java` no resumo, para a revisão decidir.

Planilhas (importação, exportação e `formato=xlsx`) **não fazem parte da v0 nem da v1**: ficam para a parte 2 (`curves-operacao-curvas` e `engine-modelos-curva`). Ignore qualquer menção a planilha que tenha sobrado no guia ou no design.

**Escopo:** onde um requisito desta change cita algo da segunda parte (simulação, `formato=xlsx`/`zip`, memória de cálculo, scripts Groovy e Blob, auditoria, origem secundária, curva derivada), isso passa a valer com a change `engine-modelos-curva`. Até lá: só modelos, interpoladores e calendários nativos, proveniência sempre com origem `JAVA` e `estadoScript` = `ATUAL`, construção só pela origem principal, e curva derivada recusada com `CADASTRO_INVALIDO`. O contrato do modelo recebe a `MemoriaCalculo`; nesta parte ela pode ser só o acumulador, sem planilha.

## 1. Convenções financeiras

- [ ] 1.1 **Contagem de tempo e cotação** (spec `curve-build-pipeline`: "Contagem de tempo", "Cotação da taxa", "Arredondamento e precisão"; spec `curve-extension-models`: "Tipos e nomes compatíveis com o QuantLib"; guia §2 e §3). Pronto quando: `DU`/`DC` por `(B, d]` (`2026-09-14` → `2026-09-15` = 1 e 1); 30/360 Bond Basis de `2026-02-28` a `2026-03-31` = 33; 13,9 em 252 DU → fator 1,139; 5 em 90 DC simples 360 → 1,0125; ida e volta taxa→fator→taxa igual depois do arredondamento; `SimpleThenCompounded`/`CompoundedThenSimple` recusados; `nD|nW|nM|nY` → `Period`; `DOWN` ≠ `HALF_UP` em 5,43219876 com 7 casas.

## 2. Calendários

- [ ] 2.1 **Brazil/Settlement e UnitedStates/FederalReserve** (spec `curve-build-pipeline`: "Contagem de tempo"; guia §4). Pronto quando: o `DU` dos 278 vértices da `PRE` do `TaxaSwap.txt` de `2026-09-14` bate com o `cDiaUtil` publicado; feriados americanos de 2026 e 2027 corretos (domingo → segunda, sábado não observado); `advance` e `adjust` nas sete convenções.

## 3. Interpolação

- [ ] 3.1 **Interpoladores, bases e extrapolação** (spec `curve-build-pipeline`: "Base de interpolação", "Interpoladores", "Políticas de extrapolação"; guia §6.1 e §6.2). Pronto quando: as combinações reproduzem as fórmulas 1.4.2 a 1.4.11 do Manual de Curvas B3 em `BigDecimal`; valor exato nos nós; `LogLinear` recusa `y <= 0`; `FlatForward` só com `Discount`; `Disabled` fora do domínio → `PRAZO_FORA_DO_DOMINIO`; `FlatValue` repete o valor.
- [ ] 3.2 **Domínio, classificação e fatores** (spec `curve-build-pipeline`: "Domínio da interpolação", "Fatores só para curvas de taxa"; guia §6.5). Pronto quando: domínio `[B + 1 DU, max(último vértice, B + HORIZONTE)]`; classificação `PONTO`/`INTERPOLADO`/`EXTRAPOLADO_INICIO`/`EXTRAPOLADO_FIM`; curva de um vértice só; fatores com 16 casas a partir do valor arredondado.
- [ ] 3.3 **Dias úteis publicados** (spec `curve-build-pipeline`: "Dias úteis publicados pela fonte ou informados pelo usuário"; guia §6.3). Pronto quando: os quatro cenários do requisito passam, inclusive `CALENDARIO_DIVERGENTE`, e com o calendário certo os valores são idênticos aos do calendário puro.
- [ ] 3.4 **Vértices no mesmo prazo e prazo não positivo** (spec `curve-build-pipeline`: "Pontos no mesmo prazo do eixo"; guia §6.4). Pronto quando: os dois cenários de feriado passam, o de menor data fica, os avisos `PONTO_DESCARTADO_MESMO_PRAZO`/`PONTO_DESCARTADO_PRAZO_NAO_POSITIVO` saem na resposta e no log, e com eixo `Actual360` nada é descartado.

## 4. Cadastro

- [ ] 4.1 **Leitura e validação do cadastro** (spec `curve-build-pipeline`: "Cadastro da curva e itens obrigatórios", "Regras de curva no cadastro, regras de metodologia no modelo"; guia §5). Pronto quando: `cModDado` lido como JSON (sem `tParmConfgCurva`), com chave desconhecida, tipo errado e JSON inválido → `CADASTRO_INVALIDO`; `'ATIVO' + espaços` de `CHAR(20)` lido como ativo; `business252`, `NoFrequency` e mercado errado recusados; um teste por regra de `CADASTRO_INVALIDO`; curva inativa ou fora da vigência dá aviso (`CURVA_INATIVA`, `FORA_DA_VIGENCIA_CURVA`), não erro; o cadastro das 7 curvas (`exemplo-cadastro-7-curvas.txt` da change `curves-cadastro-curvas`) carrega sem erro.

## 5. Modelos de construção

- [ ] 5.1 **B3 pronta** (spec `b3-ready-curve-model`; guia §8.2 e §8.3). Pronto quando: com o `TaxaSwap.txt` de `2026-09-14`, primeiro vértice da `PRE` 13,9000000 e da `DCL` -117,9600000, último da `PRE` em `2060-08-16`, dias úteis = `cDiaUtil`, e o oráculo (cada prazo de vértice devolve o `vPrecoTx` publicado) passa nas 5 curvas.
- [ ] 5.2 **NTN-B por bootstrap** (spec `ntnb-anbima-curve-model`; guia §8.4). Pronto quando: vencimento = data-base + `vVertcCurva` dias corridos ajustado por `Following`, dias úteis pelo calendário; com 4+ títulos, `z_1 = y_1` e soma dos valores presentes = cotação a menos do resíduo; sem troca de sinal → falha; feriado a menos → `CALENDARIO_DIVERGENTE`.
- [ ] 5.3 **SOFR Bloomberg** (spec `sofr-bloomberg-curve-model`; guia §8.5). Pronto quando: 21 tenores mais um `1D` duplicado (idêntico descarta, divergente falha); `15M` → `2027-12-14`; tenor em feriado americano ajustado; membro diferente do código na fonte recusado.

## 6. Construção e gravação

- [ ] 6.1 **Construir e gravar** (spec `curve-build-pipeline`: "Construção grava a curva construída e a curva interpolada", "Curva interpolada gravada", "Reconstrução da mesma data", "Proveniência e hash dos pontos", "Determinismo"; spec `curve-audit-history`; guia §7.2 e §7.6). Pronto quando: a `PRE` grava 278 linhas em `tDadoVertcCurva` e 12.390 em `tDadoCurva` (de `2026-09-15` a `2060-08-16`, fim de semana com o valor da sexta), nada em `tMtrizCurva`; situações `CONSTRUIDA`/`RECONSTRUIDA`/`EXISTENTE`; recálculo apaga e regrava as duas; `hashPontos` igual ao vetor da spec e ao relido do banco; `dBaseReft`/`cUsuarCalc` atualizados em `tCurvaMercd`.
- [ ] 6.2 **Trava e leitura consistente** (spec `curve-build-pipeline`: "Leitura consistente durante gravações"; guia §7.2). Pronto quando: tempo limite só no comando da trava (30 s), estouro → `CONSTRUCAO_EM_ANDAMENTO`, outra falha de banco → erro interno, sem `@Transactional(timeout)` nem `SET LOCK_TIMEOUT`; leituras só em `READ COMMITTED`, sem `NOLOCK`.

## 7. Consulta, interpolação e regravação

- [ ] 7.1 **Consultar e interpolar** (spec `curve-engine-api`: "Consultar pontos gravados", "Interpolar prazos", "Parâmetros comuns"; spec `curve-build-pipeline`: "Interpolação sob demanda a partir dos pontos gravados"; guia §13.1). Pronto quando: lê `tDadoVertcCurva` a cada chamada, sem cache e sem usar `tDadoCurva`; `CURVA_NAO_CONSTRUIDA` sem vértices; `data` aceita qualquer dia corrido; até 5.000 prazos; parâmetro desconhecido → 400; cada linha de `tDadoCurva` é igual à interpolação da API na mesma data.
- [ ] 7.2 **Regravar a interpolada** (spec `curve-engine-api`: "Regravar a curva interpolada"; guia §7.4 e §7.5). Pronto quando: regrava só `tDadoCurva`; sem vértices apaga a interpolada e responde `CURVA_NAO_CONSTRUIDA`; vértice alterado direto no banco → `INTERPOLADA_DESATUALIZADA` até a regravação; falha de interpolação não altera nada.

## 8. Disparos

- [ ] 8.1 **Carga do processor** (spec `curve-load-trigger`: "Webhook de carga concluída", "Construção disparada pela carga", "Conferência da quantidade lida na carga", "Log da carga"; guia §9.1). Pronto quando: constrói pela origem principal só as curvas sem vértices na data, todas as curvas de um mesmo código na fonte; `INSUMO_INCOMPLETO` com 150 de 278 linhas; inativa ou fora da vigência → `IGNORADA`; `EXISTENTE` compara com a fonte (`PONTOS_DIFERENTES_DA_FONTE`) e a comparação que falha → `COMPARACAO_INDISPONIVEL`; retry sem aviso.
- [ ] 8.2 **Construção pela API e da data inteira** (spec `curve-engine-api`: "Construir curva"; spec `curve-load-trigger`: "Construção automática da data", "Dados brutos exigidos na construção"; guia §9.2 e §9.3). Pronto quando: sem linha bruta → `INSUMO_AUSENTE`; `POST /construcoes/{dataBase}` sob demanda com `acionadoPor` = `DATA_INTEIRA`, curvas de provedor em paralelo e derivadas depois, `SEM_INSUMO`, e tempo esgotado mantendo as já concluídas.
- [ ] 8.3 **Situação da data** (spec `curve-engine-api`: "Situação das curvas numa data-base"; guia §9.4). Pronto quando: os dois cenários da spec passam, calculada na hora sem estado guardado, uma curva com tempo esgotado não afeta as outras, 120 curvas em menos de 10 s.

## 9. API e base

- [ ] 9.1 **Rotas, contrato e erros** (spec `curve-engine-api`: "Rotas", "Resolução por código e por nome", "Catálogo de curvas", "Erros padronizados", "Contrato de tipos das respostas", "Correlação de requisições", "Sem autenticação; origem do acionamento e usuário"; guia §1.5 e §13). Pronto quando: cada rota da tabela da spec responde como nos cenários, por código e por nome; um tratador só, com o código da spec, `detalhes` e `correlationId` em cada erro; decimais como string (fator de 16 casas inteiro), enum em caixa diferente recusado, textos pt-BR em `messages.properties` (um teste falha se faltar chave); `X-Correlation-Id` em sucesso e erro; `X-Usuario` opcional, sem autenticação.
- [ ] 9.2 **Valores aceitos e calendário** (spec `curve-engine-api`: "Valores aceitos no cadastro"; spec `calendar-management`: "Exportação dos feriados"; guia §13.4). Pronto quando: `GET /valores-cadastro` bate com o validador (ida e volta) e `GET /calendarios/{nome}?formato=json` devolve os feriados de 2026 dos dois calendários.
- [ ] 9.3 **Fuso e logs** (spec `curve-build-pipeline`: "Datas e horários de Brasília", "Log estruturado da construção"; guia §1.4 e §14.1). Pronto quando: a subida falha com fuso diferente de `America/Sao_Paulo`; 01h30 UTC sai 22h30 `-03:00` do dia anterior; `CONSTRUCAO_CONCLUIDA`, `CONSTRUCAO_FALHOU`, `INSUMO_DESCARTADO`, `CARGA_RECEBIDA` e `CARGA_PROCESSADA` com os campos da spec.

## 10. Fechamento

- [ ] 10.1 **Vetores reais como teste oficial** (guia §0.3). Pronto quando: com `TaxaSwap.txt` em `src/test/resources`, os 5 `hashPontos`, o vetor comum com o curves, o DU dos 278 vértices, a interpolação em `2030-06-10` (`PRE`, `DCL`, `PTX`), os fatores (tolerância `1e-14`) e as 12.390 linhas da `PRE` passam.
- [ ] 10.2 **Remoções decididas e sobras** (guia §13.5). Remover o que o guia §13.5 lista (motor antigo, `domain/calendar/**`, controllers e DTOs antigos), se ainda existir; o que mais sobrar da versão anterior fica e vai para o resumo como `[sobra]`. Pronto quando: `mvn compile` limpo e a busca do §13.5 sem referência.
- [ ] 10.3 Escrever o resumo com a anotação de cada tarefa (`pronto`, `alterado`, `renomeado de X`, `criado`) e as `[sobra]`.
