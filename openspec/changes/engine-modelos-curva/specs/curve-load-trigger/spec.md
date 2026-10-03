## Purpose

Garante que uma curva só é construída automaticamente depois que o dado bruto da sua origem foi gravado por completo. O processor grava cada carga numa única transação e, depois do commit, avisa o engine por webhook, com a quantidade de vértices por código; e o orquestrador pede a construção automática de uma data inteira, que cobre as curvas derivadas e serve de rede de segurança. O engine constrói na hora as curvas que dependem da carga, confere se leu exatamente a quantidade avisada e não guarda nenhum registro próprio da carga: o que ele precisa saber depois está no banco (dados brutos e pontos) e no log.

## ADDED Requirements

### Requirement: Construção em cadeia das curvas derivadas
Depois de processar as curvas de uma carga, na mesma requisição, o engine SHALL construir cada curva derivada (spec `curve-build-pipeline`), ativa e dentro da vigência, que ainda não tem pontos na data e cujas curvas componentes têm todas pontos gravados na data. Uma derivada construída pode liberar outra, que a tem como componente: o processo SHALL repetir até não haver mais derivada a construir, na ordem das dependências. A cadeia segue as regras da carga: nunca recalcula, uma falha não impede as demais, e cada resultado entra na resposta do webhook e no log. Uma derivada que já tem pontos SHALL ser comparada como as demais: se os pontos que as curvas componentes atuais produziriam forem diferentes dos gravados, ela recebe o aviso `PONTOS_DIFERENTES_DA_FONTE`.

Recalcular ou editar à mão uma curva componente MUST NOT reconstruir a derivada: ela fica com os pontos da construção anterior, e a diferença aparece na comparação com as curvas componentes atuais (painel do `services/curves`). Recalcular a derivada é ação do usuário, por `POST .../construcao?forcarRecalculo=true`.

#### Scenario: Derivada construída depois das curvas componentes
- **WHEN** existe uma curva derivada com componentes `DIxPRE` (carga B3) e `NTN-B` (carga ANBIMA), e a carga B3 chega antes da ANBIMA
- **THEN** a derivada não é construída na carga B3, e é construída na carga ANBIMA, logo depois da `NTN-B`, com as curvas componentes e os seus `hashPontos` na proveniência

#### Scenario: Curva componente recalculada depois
- **WHEN** a `DIxPRE` de uma data é recalculada depois da construção da derivada, com valores diferentes
- **THEN** a derivada não é reconstruída, e a sua simulação passa a dar pontos diferentes dos gravados

