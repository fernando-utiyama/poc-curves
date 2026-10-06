# Inspeção do engine (v1) — só leitura

**Objetivo:** dizer, com prova, o que do `tasks.md` já existe, o que diverge e o que falta, e se o contrato com o processor e a curves bate. O resultado volta para a revisão, que ajusta as tarefas.

**Regras**
1. **Não altere código nem o `tasks.md`.** Não rode `mvn test` nem a suíte; não compile.
2. Procure pela funcionalidade (o que faz, que tabela lê ou grava, que rota expõe), não pelo nome da spec.
3. **Toda linha precisa de prova:** `Arquivo.java:linha` ou um trecho de até 3 linhas. Sem prova, o status é `não sei`; nunca deduza.
4. Responda preenchendo as tabelas abaixo, nesta ordem, num arquivo `inspecao-engine.md` na raiz do repositório. Seja curto: uma linha por item.

Status: `pronto` (existe e cumpre) · `diverge` (existe, mas difere: diga em quê) · `falta` · `não sei`.

## A. Funcionalidades (uma linha por tarefa do `tasks.md`)

| Tarefa | Status | Prova | O que diverge ou falta |
|---|---|---|---|
| 1.1 Contagem de tempo e cotação | | | |
| 2.1 Calendários | | | |
| 3.1 Interpoladores, bases e extrapolação | | | |
| 3.2 Domínio, classificação e fatores | | | |
| 3.3 Dias úteis publicados | | | |
| 3.4 Vértices no mesmo prazo | | | |
| 4.1 Leitura e validação do cadastro | | | |
| 5.1 B3 pronta | | | |
| 5.2 NTN-B por bootstrap | | | |
| 5.3 SOFR Bloomberg | | | |
| 6.1 Construir e gravar (`tDadoVertcCurva` + `tDadoCurva`) | | | |
| 6.2 Trava e leitura consistente | | | |
| 7.1 Consultar e interpolar | | | |
| 7.2 Regravar a interpolada | | | |
| 8.1 Carga do processor | | | |
| 8.2 Construção pela API e da data inteira | | | |
| 8.3 Situação da data | | | |
| 9.1 Rotas, contrato e erros | | | |
| 9.2 Valores aceitos e calendário | | | |
| 9.3 Fuso e logs | | | |
| 10.1 Vetores reais como teste | | | |

## B. Rotas expostas (`/api/v1`)

Liste **todas** as rotas que o código expõe hoje (método, caminho, classe:linha) e marque as esperadas:

| Rota esperada | Existe? | Método e caminho reais | Classe:linha |
|---|---|---|---|
| `POST /cargas` | | | |
| `POST /construcoes/{dataBase}` | | | |
| `GET /curvas?nome=` | | | |
| `GET /curvas/situacao?dataBase=` | | | |
| `GET /valores-cadastro` | | | |
| `POST /curvas/{codigo}/{dataBase}/construcao?forcarRecalculo=&fonte=&produto=` | | | |
| `GET /curvas/{codigo}/{dataBase}` | | | |
| `GET /curvas/{codigo}/{dataBase}/interpolacao?du=&data=` | | | |
| `POST /curvas/{codigo}/{dataBase}/interpolada` | | | |
| `GET /curvas/por-nome/{dataBase}` e `.../interpolacao` | | | |
| `GET /calendarios/{nome}` | | | |
| `GET /curvas/{codigo}/{dataBase}/pontos` (nova) | | | |
| Outras rotas que existem e não estão acima | | | |

## C. Contrato com quem chama o engine

Para cada item, copie o que o código faz (nome real do campo, cabeçalho ou formato).

| Item | Esperado | No código (prova) | Bate? |
|---|---|---|---|
| Corpo de `POST /cargas` (do processor) | `idCarga`, `fonte`, `produto`, `dataBase`, `verticesPorCodigo` | | |
| Cabeçalho do usuário | `X-Usuario`, opcional, nulo sem ele | | |
| Cabeçalho de correlação | `X-Correlation-Id`, recebido ou gerado, em toda resposta | | |
| Autenticação | nenhuma (sem Spring Security, sem token) | | |
| Resposta da construção | `codigo`, `nome`, `dataBase`, `situacao`, `quantidadePontos`, `hashPontos`, `duracaoMs`, `avisos` | | |
| Resposta da consulta | `codigo`, `dataBase`, `hashPontos`, `avisos`, lista de vértices com `data`, `valor`, `diasUteis`, `diasCorridos`, `dias30360`, `fatorAcumulado`, `fatorDiario` | nome real da lista: | |
| Resposta da interpolação | `prazos[]` com `pedido`, `data`, `du`, `dc`, `valor`, `classificacao`, fatores | | |
| Formato de erro | campo do código, mensagem, `detalhes`, `correlationId` | nomes reais dos campos: | |
| Decimais | string, sem expoente | | |
| `avisos` | `{ codigo, mensagem, detalhes }`, lista vazia quando não há | | |

## D. Banco compartilhado com a curves e o processor

| Item | Esperado | No código (prova) | Bate? |
|---|---|---|---|
| Lê `cModDado` de `tConfgCurva` como JSON | chaves da tabela da spec `curve-build-pipeline` | | |
| Origem principal | menor `cPriorCsumo` em `tCurvaPrvdr` | | |
| Curva ativa | `trim(cSitReg)` = `ATIVO` | | |
| Escreve em `tCurvaMercd` | só `dBaseReft` e `cUsuarCalc` | | |
| Grava vértices | `tDadoVertcCurva` (com `cDiaUtil`) | | |
| Grava interpolada | `tDadoCurva`, um por dia corrido | | |
| NTN-B | `vVertcCurva` em dias corridos, sem calendário no processor | | |
| `hashPontos` | SHA-256 de `AAAA-MM-DD;valor` canônico, separados por `\n` | | |

## E. Nomes divergentes

| Nome no código | Nome na change | Onde |
|---|---|---|

## F. Sobras (existem e a spec atual não pede)

| Arquivo | O que faz |
|---|---|

## G. Dúvidas

Uma linha por dúvida, com a prova que gerou a dúvida.
