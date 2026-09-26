## 1. Pontos

- [ ] 1.1 Criar `GET .../pontos?de=&ate=` e `GET .../pontos/{dataBase}` (dados gravados e `hashPontos`); verificar que o `hashPontos` é igual ao do engine para a mesma data, com o vetor de teste da spec `curve-build-pipeline` (valor na forma canônica, independente das 12 casas lidas do banco)
- [ ] 1.2 Criar `PUT .../pontos/{dataBase}`: valor como string decimal, arredondamento pela configuração vigente (sem ela, grava como enviado com `SEM_CONFIGURACAO`), transação com `UPDLOCK, ROWLOCK` na linha de `tCurvaMercd` esperando dentro do tempo limite da requisição, gravação só da diferença (`UPDATE`, `INSERT`, `DELETE`), releitura com conferência do `hashPontos` e desfazimento se divergir, `SEM_MUDANCA` sem escrita, aviso `VALOR_ARREDONDADO`, sem tocar `dBaseReft` e `cUsuarCalc`; verificar os cenários da spec (edição de um valor atualizando uma linha só, ponto retirado da lista, valor com casas a mais, lista igual à gravada sem escrita, conferência divergente simulada desfazendo tudo, curva digitada numa data sem construção, construção em andamento com SQL Server de teste)
- [ ] 1.3 Implementar as recusas de `PONTOS_INVALIDOS` só por consistência de banco (lista vazia, data ou valor malformado, data repetida, valor fora de `DECIMAL(28,12)`) e os avisos de regra de negócio da tabela da spec, com a checagem de feriados pela exportação de calendário do engine (token de serviço, 10 s); verificar uma recusa por regra de banco e, gravando com aviso, ponto em feriado, fim de semana, antes da data-base, preço não positivo, sem configuração e engine fora; taxa negativa sem aviso
- [ ] 1.4 Criar `DELETE .../pontos/{dataBase}`; verificar que a consulta do engine passa a responder `CURVA_NAO_CONSTRUIDA` e que uma construção posterior grava os pontos da fonte
- [ ] 1.5 Verificar a preferência: carga chegando depois da edição (engine devolve `EXISTENTE`), recálculo depois da leitura (edição grava por cima), sem `If-Match`
- [ ] 1.6 Emitir o log `PONTOS_EDITADOS` (nível `AVISO`, usuário, operação, origem, `idLote`, quantidades e `hashPontos` antes e depois, horário de Brasília), sem auditoria; verificar os campos

## 2. Planilha de pontos

- [ ] 2.1 Criar a exportação (aba única `Pontos` com `Curva`, `DataBase`, `DataPonto` e `Valor`; valor numérico até 15 dígitos significativos e texto acima, limites de 50 códigos, 366 dias e 100.000 pontos); verificar a exportação de uma semana de duas curvas e o valor da `PTX` com 7 casas
- [ ] 2.2 Criar a importação com a semântica da spec (cada par curva/data-base como lista completa, pares ausentes intocados, sem exclusão de data inteira) e as mesmas recusas e avisos do `PUT`; verificar que exportar e importar sem editar dá zero mudanças, e a correção de um vértice em 5 datas
- [ ] 2.3 Criar os modos `SIMULACAO` e `APLICACAO` (transação única, travas em ordem alfabética de nome, 120 segundos, nada aplicado se houver erro, `idLote` no log), com gravação pela diferença com conferência do `hashPontos` por par, resposta em JSON ou planilha marcada (`Resultado`, `ValorGravado`, `Resumo`, `Exclusoes`); verificar os cenários da spec

## 3. Verificação

- [ ] 3.1 Com o engine e o curves apontando para o mesmo SQL Server de teste: construir a `PRE` pelo engine, editar um ponto pelo curves, interpolar pelo engine com o valor novo, e confirmar que um webhook de carga não sobrescreve e que um recálculo forçado sim
- [ ] 3.2 Rodar `openspec validate curves-dado-curva --strict` e a suíte do serviço; verificar que tudo passa
