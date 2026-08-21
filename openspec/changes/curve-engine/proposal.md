## Why

O motor de cálculo é o coração da plataforma: é ele que transforma contratos individuais — DI1 e afins — em uma curva utilizável, e é ele que responde quando alguém pede a taxa de um prazo que não é vértice. Hoje ele existe como um pod que "constrói curvas e interpola quando necessário", mas sem contrato escrito: não está definido quando ele constrói, o que faz quando falta insumo, o que exatamente publica, nem como responde para uma curva que não foi ele quem produziu.

Há um segundo problema, mais estrutural: **a metodologia de curva vive dentro do código do motor**. Mudar como uma curva é montada é mudança de código, com build, release e janela de deploy. Esta mudança abre a porta: cada curva aponta para um **modelo de construção**, que por padrão é um **modelo fixo embutido** — a curva PRE de DI1 da B3 nasce apontando para o modelo padrão dela — mas pode ser trocado **pela tela** por um **modelo Groovy importado**, sem recompilar nem redeployar nada.

Esta mudança especifica o motor no **modo híbrido** — construção assíncrona e persistida, interpolação síncrona e cacheável — sobre um **kernel numérico próprio** deste repositório, e com **modelo de construção selecionável por curva** por cima.

## What Changes

- **`curve-kernel` como subprojeto novo e independente**: `libs/curve-kernel`, biblioteca pura em Java 21, sem Spring, sem banco, sem rede. Contém estruturas de curva, interpoladores, políticas de extrapolação, bootstrap, `RateHelper`, convenções B3/ANBIMA (calendário com feriados reais, DU/252, ACT/360, ACT/365), arredondamento com truncamento de primeira classe, e o reconciliador contra a taxa de referência oficial da B3. **Nenhuma dependência de repositório externo** — a metodologia validada em outro projeto é referência de ideia, não origem de import.
- **Modelos de construção embutidos**: a metodologia de cada curva suportada existe como modelo fixo em Java dentro do motor, registrado no catálogo de modelos. É o padrão, funciona sem que ninguém importe nada, e cobre a curva PRE de DI1 da B3.
- **Modelos Groovy importados**: um script Groovy pode ser importado, registrado no catálogo e escolhido por uma definição de curva. A troca é feita na tela, apontando a definição para outro modelo — sem build, sem deploy.
- **Contenção do Groovy**: classloader isolado, sem I/O de arquivo, rede ou processo, com limite de tempo e de memória. O modelo recebe os insumos já resolvidos e devolve vértices; não consulta banco nem abre conexão.
- **Construção assíncrona por evento**: o motor consome `curve.build.requested.v1`, resolve a definição, sua versão e o modelo que ela aponta, carrega os insumos e publica os vértices produzidos.
- **Gate de insumo faltante**: ausência de qualquer insumo exigido interrompe a construção nomeando índice e data; o run vai para `FAILED` e nada é publicado.
- **Validação de consistência como gate**: entre construir e publicar entra uma peça própria que submete a curva aos testes padrão de mercado — reprecificação dos instrumentos de calibração, ausência de arbitragem, monotonicidade dos fatores de desconto, faixas plausíveis de taxa, suavidade dos forwards e variação contra o dia anterior. A versão nasce em `EM_VALIDACAO` e só é promovida a `PUBLICADA` se os testes bloqueantes passarem.
- **Bloqueante versus aviso**: violação de arbitragem e erro de reprecificação são defeito e impedem a publicação. Variação forte contra o dia anterior pode ser mercado — publica com aviso registrado, porque bloquear ali seria não entregar curva justamente no dia de choque.
- **Publicação atômica e versionada**: `versao_curva` nova, `vertice_curva` e `procedencia_curva` na mesma transação, com o **modelo que produziu a curva** registrado na proveniência; versão anterior da mesma data vira `SUPERSEDED`.
- **Interpolação síncrona sob demanda**: prazo arbitrário sobre uma versão publicada, com o interpolador e a política de extrapolação declarados na definição, e cache Redis por `(curva, data, versão, prazo, interpolador)`.
- **Interpolação para as duas origens de curva**: o motor interpola tanto curvas construídas quanto curvas importadas da B3 — em ambos os casos sobre vértices já publicados, sem reconstruir nada.
- **Determinismo verificável**: mesmo modelo + mesmos insumos + mesma versão de definição ⇒ vértices idênticos, valor a valor.
- **Comparação entre modelos**: a mesma curva construída pelo modelo embutido e por um modelo Groovy pode ser comparada vértice a vértice, que é o que torna experimentar metodologia útil em vez de arriscado.
- **Reconciliação como teste de primeira classe**: a curva construída é comparada contra a curva oficial da B3 da mesma data, com o oráculo arredondado pela mesma política de produção e comparação exata.

## Capabilities

### New Capabilities

- `curve-kernel`: biblioteca numérica própria — estruturas de curva, interpoladores, extrapolação, bootstrap e `RateHelper`, convenções B3/ANBIMA, política de arredondamento, reconciliador contra oráculo oficial; pureza (sem Spring, banco ou rede) e regra de precisão decimal.
- `curve-model-registry`: catálogo de modelos de construção, modelos embutidos como padrão, importação e validação de modelos Groovy, seleção do modelo por definição de curva, contenção da execução, e registro do modelo usado em cada curva publicada.
- `curve-construction`: consumo do pedido de construção, resolução de definição, versão e modelo escolhido, carregamento e validação de insumos, execução do modelo, gate de insumo faltante, determinismo e dependência entre curvas.
- `curve-validation`: bateria de testes de consistência da curva construída — reprecificação dos instrumentos de calibração, ausência de arbitragem, monotonicidade dos fatores de desconto, faixas de taxa, suavidade, cobertura de vértices e variação contra o dia anterior —, classificação em bloqueante e aviso, persistência do resultado e promoção ou reprovação da versão.
- `curve-publication`: publicação atômica de versão, vértices e proveniência para curvas `BOOTSTRAPPED`, estado inicial `EM_VALIDACAO`, promoção após o gate, numeração incremental, `SUBSTITUIDA` da anterior, emissão de `curve.published.v1` e recusa de publicar para definições `IMPORTED`.
- `curve-interpolation`: API síncrona de interpolação em prazo arbitrário sobre versão publicada, seleção do interpolador e da extrapolação pela definição, comportamento fora do intervalo de vértices, cache por versão e paridade entre curvas construídas e importadas.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo módulo de biblioteca**: `libs/curve-kernel/` — Java 21 puro, JUnit 5 + AssertJ, sem Spring.
- **Novo serviço**: `services/curve-engine/` em Java 21 / Spring Boot 3.4.x, sem Lombok, rodando como pod (container sob Podman na POC).
- **Nova dependência de runtime**: Groovy, para execução de modelos importados. É a única linguagem adicional admitida no motor, e apenas dentro da contenção de modelo.
- **Depende de** `curves-solution-architecture` (modelo de dados, catálogo de modelos, contratos de evento) e do dado produzido por `curve-processor`.
- **Escreve** em `versao_curva`, `vertice_curva`, `procedencia_curva` e `validacao_curva` — exclusivamente para definições `BOOTSTRAPPED` — e em `modelo_curva`. Lê `definicao_curva`, `versao_definicao_curva` e `ponto_dado_mercado`.
- **Expõe** API HTTP interna de interpolação, de catálogo de modelos e de importação de modelo Groovy, consumida pelo `curve-bff`; não é exposta ao navegador.
- **Usa** Redis para cache de interpolação, com chave contendo o identificador de versão.
- **Superfície de segurança nova**: executar código enviado por usuário exige contenção real — classloader isolado, ausência de I/O, limite de tempo e de memória — e autorização restrita a administrador para importar modelo e para trocar o modelo de uma curva.
- **Fora de escopo**: cadastro de curva (é `curve-api`), agendamento (é `curve-orchestrator`), ingestão (é `curve-processor`).
