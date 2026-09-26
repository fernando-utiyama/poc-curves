## Purpose

Permite investigar em produção o que aconteceu com uma curva: simular a construção sem gravar nada e baixar, numa planilha, a memória de cálculo de cada ponto e de cada prazo interpolado. A mesma planilha é a saída em arquivo das consultas normais. A simulação usa exatamente o mesmo código da construção, de modo que o que ela mostra é o que a construção gravaria.

## ADDED Requirements

### Requirement: Memória de cálculo produzida pelo mesmo código
Construção, simulação, consulta e interpolação SHALL registrar a memória de cálculo num único objeto, preenchido pelos mesmos métodos que calculam os valores; nenhum cálculo SHALL ser refeito só para a memória. Os modelos de construção e de interpolação, nativos ou Groovy, SHALL receber esse objeto e registrar nele as linhas definidas nesta spec. A construção gravada SHALL usar a memória só para o log; a memória não é persistida.

#### Scenario: Mesmo resultado na simulação e na construção
- **WHEN** a `PRE` de `2026-09-14` é simulada e depois construída, sem mudança de insumo, cadastro ou modelo
- **THEN** os valores arredondados da simulação são iguais aos pontos gravados, e os dois `hashPontos` são iguais

### Requirement: Simulação sem gravação
`GET .../simulacao` SHALL executar a leitura do cadastro, o modelo de construção e o arredondamento exatamente como a construção, e, se houver `du` ou `data`, a interpolação desses prazos sobre os pontos simulados. A simulação MUST NOT gravar, apagar ou travar nada, e MUST NOT alterar pontos gravados. Curva sem cadastro SHALL responder 404 e parâmetro inválido, 400. Qualquer outra falha (cadastro inválido, insumo ausente ou inválido, modelo que falhou) SHALL responder 200 com `status` = `ERRO`, o `codigoErro`, a mensagem e toda a memória registrada até a falha. Sem falha, `status` = `OK`. Na simulação, um prazo fora do domínio SHALL ser marcado com a classificação `FORA_DO_DOMINIO` e o motivo, sem falhar os demais.

#### Scenario: Simulação de data sem insumo
- **WHEN** o cliente chama `GET /api/v1/curvas/DPL/2026-09-13/simulacao` para um domingo sem dados
- **THEN** a resposta é 200 com `status` = `ERRO`, `codigoErro` = `INSUMO_AUSENTE`, o cadastro lido e nenhum ponto

#### Scenario: Simulação não grava
- **WHEN** a simulação da `PRE` de uma data sem pontos gravados termina com `status` = `OK`
- **THEN** a data continua sem pontos gravados, e a consulta da curva responde `CURVA_NAO_CONSTRUIDA`

### Requirement: Comparação com os pontos gravados
Se a curva tiver pontos gravados na data, a simulação SHALL comparar cada ponto simulado com o gravado na mesma data, trazendo o valor gravado e a diferença (`valor simulado arredondado − valor gravado`), e SHALL informar o `hashPontos` gravado ao lado do simulado. Pontos que só existem de um dos lados SHALL aparecer com a situação `SO_SIMULADO` ou `SO_GRAVADO`.

#### Scenario: Ponto editado manualmente
- **WHEN** um ponto da `PRE` foi editado pela API e depois a mesma data é simulada
- **THEN** a linha desse ponto mostra a diferença entre o valor da fonte e o valor editado, e os dois `hashPontos` diferem

### Requirement: Estrutura da planilha
A planilha (`.xlsx`) SHALL ter sempre as seis abas abaixo, nesta ordem, com a primeira linha de cabeçalho congelada. Uma aba sem conteúdo aplicável SHALL ter só o cabeçalho. Datas SHALL ser células de data no formato `aaaa-mm-dd`; números SHALL ser células numéricas; célula não aplicável SHALL ficar vazia.

1. **`Resumo`**, com as colunas `Campo` e `Valor`, uma linha por campo: código, nome, data-base, fonte (`GRAVADA` ou `SIMULACAO`), status, código de erro, mensagem de erro, data e hora de geração (ISO-8601 com fuso), `correlationId`, versão do engine (versão do artefato e commit), `estadoScript`, avisos, modelo de construção, interpolador e calendário (nome, mercado quando for calendário, origem `JAVA` ou `GROOVY`, versão e hash cada), fonte, produto e código na fonte da origem, carga registrada (sim ou não), `idCarga` da carga registrada, `idCarga` que gerou os pontos gravados e quantidade avisada para o código na fonte, uma linha por item do cadastro vigente (colunas de `tCurvaMercd` e cada chave do JSON de `cModDado`), quantidade de pontos, `hashPontos`, `hashPontos` gravado (só simulação) e a nota "Valores numéricos limitados a 15 dígitos significativos pelo Excel; a resposta JSON tem a precisão completa".
2. **`Insumos`** (só simulação): `Linha`, `Tabela`, as colunas lidas da tabela bruta (definidas na spec do modelo), `Situacao` (`USADO` ou `DESCARTADO`), `Motivo`.
3. **`Pontos`**: `Data`, `DU`, `DC`, `X` (fração de ano do eixo), `Valor calculado` (antes do arredondamento; só simulação), `Valor`, `Y` (grandeza), `Fator acumulado` e `Fator diario medio` (só `TAXA`), `Valor gravado`, `Diferenca` e `Situacao` (só simulação), seguidas das colunas extras do modelo de construção, na ordem definida na spec do modelo.
4. **`Fluxos`**: linhas de fluxo de caixa registradas pelo modelo de construção (definidas na spec do modelo; vazia para modelos sem fluxo).
5. **`Interpolacao`** (quando houver prazos): `Prazo pedido`, `Data`, `DU`, `DC`, `X`, `Classificacao`, `Data anterior`, `X anterior`, `Y anterior`, `Data posterior`, `X posterior`, `Y posterior`, `W`, `Y calculado`, `Valor calculado`, `Valor`, `Fator acumulado`, `Fator diario medio`, `Motivo`. Para `PONTO` e `FlatValue`, as colunas de vizinhos e `W` ficam vazias; para `FlatForward`, anterior e posterior são os dois pontos do segmento usado.
6. **`Eventos`**: `Ordem`, `Nivel` (`INFO`, `AVISO` ou `ERRO`), `Evento`, `Mensagem`, com os eventos da execução em ordem, incluindo descartes de insumo e o erro que interrompeu a execução.

#### Scenario: Planilha de uma consulta gravada
- **WHEN** o cliente baixa `GET /api/v1/curvas/PRE/2026-09-14/interpolacao?du=21&formato=xlsx`
- **THEN** a planilha tem as seis abas; `Resumo` tem fonte `GRAVADA`; `Pontos` tem os 278 pontos gravados sem as colunas exclusivas da simulação preenchidas; `Insumos` e `Fluxos` só têm cabeçalho; `Interpolacao` tem uma linha para 21 dias úteis com os dois pontos vizinhos e o `W`

#### Scenario: Planilha de uma simulação com erro
- **WHEN** o cliente baixa a simulação da `NTN-B` numa data em que um título tem prazo inconsistente
- **THEN** `Resumo` mostra `status` = `ERRO` e `INSUMO_INVALIDO`, `Insumos` mostra a linha do título com o motivo, e `Eventos` termina com o erro

### Requirement: Pacote de depuração em zip
Com `formato=zip`, as rotas de consulta de pontos, de interpolação e de simulação SHALL responder com um arquivo `.zip` (`application/zip`, nome `{codigo}_{dataBase}_{FONTE}_{AAAAMMDDHHmmss}.zip`) contendo:
- `memoria.xlsx`: a planilha desta spec;
- `memoria.json`: o mesmo conteúdo em JSON, com precisão completa;
- `modelos/{tipo}/{nome}/v{versao}.groovy`: o código-fonte exato de cada script Groovy usado, tirado da memória da instância que executou (o mesmo texto que foi compilado), inclusive com o Blob inacessível;
- `modelos/calendario/{nome}/v{versao}.xlsx`: a planilha de origem, quando o calendário usado foi gerado por importação e a planilha estiver disponível;
- `manifesto.json`: `correlationId`, versão do engine, `estadoScript`, e, para cada modelo usado, tipo, nome, origem, versão e hash; e a lista de arquivos do zip com o SHA-256 de cada um.

Modelos nativos não têm código no pacote; são identificados pelo nome e pela versão do engine. O hash de cada `.groovy` no pacote MUST ser igual ao hash informado na proveniência.

#### Scenario: Curva construída com script Groovy
- **WHEN** o cliente baixa `GET /api/v1/curvas/PRE/2026-09-14/simulacao?formato=zip` enquanto a versão 3 de `LogLinear` está ativa
- **THEN** o zip tem `modelos/interpolacao/LogLinear/v3.groovy`, com SHA-256 igual ao hash da versão 3, e o `manifesto.json` lista o `PRONTA_TS_B3` como `JAVA` com a versão do engine

### Requirement: Mesmo conteúdo em JSON
A simulação em `formato=json` SHALL devolver o mesmo conteúdo da planilha: `resumo`, `insumos`, `pontos`, `fluxos`, `interpolacao` e `eventos`, com os valores em precisão completa (números JSON sem arredondamento além do definido na spec `curve-build-pipeline`). As rotas de consulta e interpolação em `formato=json` mantêm os corpos definidos na spec `curve-engine-api`.

#### Scenario: Simulação em JSON
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14/simulacao`
- **THEN** a resposta tem `resumo.status` = `OK`, 278 itens em `pontos` e os insumos lidos de `tBtrsCurvaPrimr`
