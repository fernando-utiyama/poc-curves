Segunda parte do engine (operação e extensões); a primeira, a construção das 7 curvas, está na change [`engine-construcao-curvas`](../engine-construcao-curvas/tasks.md) e vem antes. Guia de implementação passo a passo (arquivos, assinaturas, código das partes difíceis, SQL, configuração e vetores de teste reais): [`implementacao.md`](../engine-construcao-curvas/implementacao.md). Siga a ordem da seção 15 do guia. Os "verificar" de cada tarefa viram testes escritos ao final, na seção 16 do guia; durante a implementação, cada tarefa termina com `mvn -q compile`.

## 4. Cadastro

- [ ] 4.2b Implementar a origem secundária: `fonte` e `produto` na construção e na simulação (os dois ou nenhum), escolha do provedor da curva em `tCurvaPrvdr` (nenhum ou mais de uma → `CADASTRO_INVALIDO` com as origens cadastradas, `TCEN` recusada), modelo por `MODELOS_POR_ORIGEM` ou `cMotorCalc`, resto do cadastro da curva, aviso `ORIGEM_SECUNDARIA`, origem na proveniência, na memória e no `CURVA_GRAVADA`, e comparação do `EXISTENTE` contra a origem informada; verificar os quatro cenários da spec `curve-build-pipeline`, os dois da `curve-engine-api` e que a carga e a construção da data usam sempre a principal

## 5. Registro de modelos e Groovy

- [ ] 5.1 Criar `RegistroModelos<T>` para construção, interpolação e calendário, com a resolução versão fixada → `ATIVA` no Blob → Java nativo → `CADASTRO_INVALIDO`; verificar cada cenário de resolução da spec `curve-extension-models`
- [ ] 5.2 Criar o adaptador de Blob (`groovy-models/{tipo}/{nome}/v{n}.groovy` imutável com `If-None-Match: *`; `estado.json` com `If-Match`), com cache de estado por instância (`engine.groovy.cache-estado-segundos`, padrão 30), cache de ausência, conferência de hash na carga e a regra de Blob fora da spec `curve-engine-resilience`; verificar com Azurite: duas instâncias do registro no mesmo teste, ativação numa e uso da versão nova na outra após o vencimento do cache, duas ativações concorrentes (uma recebe `ESTADO_SCRIPT_CONCORRENTE`), conteúdo alterado (hash divergente) e Blob parado
- [ ] 5.3 Criar o carregador Groovy com `SecureASTCustomizer` por lista permitida, `TimedInterrupt` (`engine.groovy.timeout-segundos`, padrão 5) e checagem do contrato do tipo, substituindo `GroovyDynamicModelCompiler`; verificar com scripts válido, de tipo errado, com acesso à rede (reprovado) e em laço (interrompido com `MODELO_FALHOU`)
- [ ] 5.4 Implementar os estados `RASCUNHO` → `VALIDADA`/`REPROVADA` → `ATIVA` ↔ `INATIVA` e a validação por tipo (fixture de interpolação, ano corrente de calendário, simulação para construção); verificar cada transição válida e inválida
- [ ] 5.5 Verificar a sobrescrita parcial ponta a ponta: script que estende `LogLinear` e troca só `valorNoSegmento`, e script que estende `Brazil` com um feriado extra, cada um validado, ativado e refletido na interpolação e na contagem de dias

## 6. Memória de cálculo

- [ ] 6.1 Criar `MemoriaCalculo` (resumo, insumos, pontos com colunas extras declaradas pelo modelo, fluxos, prazos, eventos) e fazer pipeline, modelos, interpolação e extrapolação registrarem nela nos mesmos métodos que calculam; verificar que nenhum valor da memória é recalculado fora do caminho de cálculo (teste que compara cada valor da memória com o valor devolvido)

## 8. Pipeline

- [ ] 8.2 Implementar a simulação no mesmo serviço, com a gravação desligada, sem trava, com `status` `OK`/`ERRO`, prazos `FORA_DO_DOMINIO` e comparação com os pontos gravados (`SO_SIMULADO`/`SO_GRAVADO`); verificar que simular e construir dão o mesmo `hashPontos`, que a simulação não grava nada, e a diferença de um ponto editado
- [ ] 8.3b Na consulta e na auditoria, mostrar os dias e fatores gravados em `tDadoVertcCurva` ao lado dos recalculados, com `CALENDARIO_DIVERGENTE`, `CALCULO_GRAVADO_DIVERGENTE` e `SEM_CALCULO_GRAVADO`; verificar um feriado acrescentado ao calendário depois da construção (`CALENDARIO_DIVERGENTE`, valores dos vértices inalterados), uma cotação alterada no cadastro (`CALCULO_GRAVADO_DIVERGENTE`) e uma data com pontos editados à mão


## 9. Carga concluída

- [ ] 9.5 Criar o suporte a curva derivada: leitura das curvas componentes em `tCurvaPrvdr` com o provedor `TCEN`, validações de `CADASTRO_INVALIDO` (componente inexistente, ciclo, papéis diferentes dos do modelo), `curvaComponente(papel)` no contexto de construção, construção em cadeia depois de cada carga, `CURVA_COMPONENTE_NAO_CONSTRUIDA`, e componentes com `hashPontos` na proveniência e em `CURVA_GRAVADA`; verificar com um modelo derivado de teste (Groovy, razão entre duas curvas) os cenários das specs `curve-build-pipeline` e `curve-load-trigger`, uma derivada de derivada, e que nenhuma derivada é reconstruída quando uma curva componente é recalculada

## 10. Auditoria, leitura consistente e segurança

- [ ] 10.1 Emitir `CURVA_GRAVADA` depois do commit de cada construção e reconstrução (com `pontosAnteriores`, `idCarga` quando vier da carga, proveniência e componentes), nunca quando o commit falha, e atualizar `tCurvaMercd.dBaseReft` (sem retroceder) e `cUsuarCalc` na construção; verificar construção, reconstrução de data antiga, recálculo depois de uma edição manual (pontos alterados direto na tabela no teste), commit forçado a falhar (sem evento), e que nada é gravado no Blob
- [ ] 10.2 Criar `GET /api/v1/curvas/{codigo}/{dataBase}/auditoria` (JSON e `xlsx`, montado na hora: `Resumo`, `Pontos`, `Conferencia`, `Insumos`); verificar o cenário da curva editada, uma curva sem pontos e que nada é gravado
- [ ] 10.4 Exigir o cabeçalho `X-Usuario` no envio e na ativação de script (400 `PARAMETRO_INVALIDO` sem ele) e gravá-lo como `autor` e `aprovador` no `estado.json`; verificar envio e ativação com e sem o cabeçalho, e que o engine continua sem Spring Security nem validação de token
- [ ] 10.5 Gravar no `estado.json` quem ativou cada versão (`aprovador`, podendo ser o autor); verificar ativação pelo próprio autor e por outro usuário
- [ ] 10.6 Acessar o Blob por Managed Identity (`DefaultAzureCredential`), com connection string só no perfil local (Azurite); verificar que o perfil de produção não aceita connection string

## 11. Resiliência

- [ ] 11.1 Configurar os tempos limite da tabela da spec `curve-engine-resilience` (pool e comando do banco, trava, Blob, Groovy, requisição e webhook) com os erros correspondentes; verificar com testes que simulam lentidão em cada dependência e confirmam o erro, a transação desfeita e o evento `TEMPO_ESGOTADO`
- [ ] 11.2 Implementar a repetição só de operações idempotentes (3 tentativas, espera exponencial com variação) e o circuit breaker do Blob (5 falhas, 60 segundos aberto); verificar que leitura do Blob com uma falha transitória funciona, que gravação no banco e no `estado.json` não é repetida, e que com o circuito aberto nenhuma chamada ao Blob é feita
- [ ] 11.3 Manter o último estado de script lido com o Blob fora, sem prazo, com aviso limitado a um por minuto; sem estado ou sem a versão em memória, usar o nativo com `ESTADO_SCRIPT_DESCONHECIDO`; informar `estadoScript` na proveniência; ligar a prontidão ao banco e à espera de subida pelo Blob (até `engine.groovy.espera-subida-segundos`, padrão 300); verificar Blob fora por horas durante construções, Blob voltando durante a espera (instância pronta com as versões ativas), Blob fora além da espera (instância pronta com nativos), e o `health` mostrando o Blob `DOWN`
- [ ] 11.4 Emitir os logs `REQUISICAO_CONCLUIDA`, `DEPENDENCIA_CHAMADA`, `DEPENDENCIA_LENTA`, `DEPENDENCIA_FALHOU` e `TEMPO_ESGOTADO`, sem token, connection string, script, corpo inteiro ou listas de pontos; verificar os campos com um appender de teste e uma busca por dados proibidos no log gerado pela suíte
- [ ] 11.5 Publicar as métricas da spec pelo Micrometer; verificar os contadores e histogramas depois de construções com sucesso e com falha

## 12. API

- [ ] 12.3 Criar as rotas `/api/v1/modelos/{tipo}/{nome}` sobre o Blob; verificar envio, validação, ativação, desativação, listagem e `ESTADO_SCRIPT_CONCORRENTE`

## 13. Planilha

- [ ] 13.1 Criar `PlanilhaMemoriaCalculo` com Apache POI (`poi-ooxml`): as seis abas na ordem, cabeçalho congelado, datas exibidas como `dd/mm/aaaa`, números como células numéricas, colunas extras do modelo, nota de precisão no `Resumo`; verificar lendo o arquivo gerado no teste e conferindo abas, cabeçalhos e tipos de célula
- [ ] 13.2 Ligar `formato=xlsx` às rotas de consulta, interpolação e simulação, com `Content-Type`, `Content-Disposition` e nome de arquivo da spec; verificar os dois cenários de planilha da spec `curve-calculation-memory` (consulta gravada da `PRE` com `du=21`, e simulação da `NTN-B` com prazo inconsistente)

## 14. Calendário por planilha e pacote de depuração

- [ ] 14.1 Criar `CalendarioPorLista` (fins de semana, lista, cobertura com `MODELO_FALHOU` fora dela, mercado fixo); verificar data dentro, fora da cobertura e mercado errado
- [ ] 14.2 Criar `POST /api/v1/calendarios/{nome}/importacao`: validação da planilha (aba, colunas, datas, repetidas, cobertura), geração determinística do script, gravação da versão em `RASCUNHO`, sem guardar a planilha; verificar cada rejeição, mesma planilha gerando o mesmo hash, e o cenário do feriado decretado até a ativação
- [ ] 14.3 Validar versões importadas percorrendo toda a cobertura; verificar aprovação de uma planilha correta e reprovação de um script adulterado
- [ ] 14.4 Na exportação de feriados (a rota `json` é da change `engine-construcao-curvas`), acrescentar `formato=xlsx` (aba `Feriados` no formato da importação e aba `Resumo`) e o parâmetro `versao`, para calendários Groovy e por lista; verificar a ida e volta do `Brazil` de 2001 a 2100
- [ ] 14.5 Gravar no build a versão do artefato (`build-info` do `spring-boot-maven-plugin`) e incluí-la na proveniência, no `CURVA_GRAVADA` e no `Resumo`; verificar numa resposta de construção
- [ ] 14.6 Guardar em memória o código-fonte de cada versão compilada e criar `formato=zip` (planilha, JSON, `.groovy` usados, manifesto com SHA-256); verificar o cenário da spec `curve-calculation-memory` (hash do `.groovy` igual ao da proveniência) e um pacote gerado com o Blob fora

## 15. Datas, horários e testes em massa

- [ ] 15.2 Montar a massa de regressão: `TaxaSwap.txt` de todos os pregões de pelo menos 12 meses consecutivos (baixados da B3), com Carnaval, Sexta-feira Santa, Corpus Christi, virada de ano e 20 de novembro, guardados compactados nos recursos de teste; verificar o oráculo das 5 curvas em todos os arquivos
- [ ] 15.3 Criar testes de propriedade com JUnit parametrizado (1.000 casos gerados com semente fixa, sem biblioteca extra) para: ponto preservado em qualquer curva gerada; determinismo; ida e volta taxa→fator→taxa em cada cotação (igual depois do arredondamento cadastrado); monotonicidade de `DU` e `DC`; `advance` e contagem de dias úteis coerentes em qualquer par de datas; verificar com pelo menos 1.000 casos por propriedade
- [ ] 15.4 Criar o teste do `DecimalMath` (`pow`, `ln`, `exp`) contra valores de referência em entradas típicas de curva; verificar erro relativo menor que `1e-14` e o mesmo resultado bit a bit em duas execuções
- [ ] 15.5 Criar testes de bootstrap da NTN-B com títulos sintéticos cuja taxa zero é conhecida por construção (gerar as taxas indicativas a partir de uma curva zero dada e verificar que o bootstrap recupera essa curva dentro da tolerância)

## 16. Verificação final

- [ ] 16.2 Rodar `openspec validate engine-modelos-curva --strict` e a suíte de testes do engine; verificar que tudo passa

## 17. Correções da revisão de 2026-09-30

- [ ] 17.3 Segurança: apagar o `JwtDecoder` próprio, o `mockJwtDecoder` e o `SegurancaConfig` e todo o Resource Server do Spring (o engine não autentica; guia, 13.3); verificar que a aplicação sobe sem emissor nem audiência e que nenhuma classe de segurança sobra no código
- [ ] 17.5 Proveniência: modelo, interpolador e calendário com a origem, a versão e o hash do que o `RegistroModelos` de fato resolveu; verificar o cenário "Proveniência de script Groovy" da spec `curve-audit-history`
- [ ] 17.11 Rodar a suíte inteira e `openspec validate engine-modelos-curva --strict`; verificar, com a saída do Maven colada no relatório, que tudo passa (não marcar esta tarefa sem a saída)
