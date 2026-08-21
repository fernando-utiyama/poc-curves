## Context

O motor é o único componente que produz números novos. Todo o resto move, guarda ou mostra dado — ele calcula. Isso lhe dá duas responsabilidades que não se parecem: montar curva a partir de instrumentos, o que é caro e acontece uma vez por data; e responder a taxa de um prazo qualquer, o que é barato e acontece o tempo todo, com alguém esperando na tela. A decisão de arquitetura já resolveu isso com o modo híbrido: construir é assíncrono e persistido, interpolar é síncrono e cacheado.

Sobre a base numérica: `libs/curve-kernel` é um **módulo novo e independente deste repositório**. A metodologia já foi validada em outro projeto, e portar dela as ideias, os algoritmos e sobretudo as fixtures e os casos de teste é o caminho — mas o código é daqui, evolui daqui, e nenhum módulo de `poc-curvas` declara dependência de repositório externo.

Sobre a metodologia: hoje ela mora dentro do código. Trocar a forma de montar a curva PRE significa editar Java, buildar, releasar e esperar janela. A abertura proposta é modesta e suficiente: cada definição de curva **aponta para um modelo de construção**. O padrão é um modelo fixo embutido no motor — a curva PRE de DI1 da B3 nasce apontando para o modelo padrão dela e funciona sem que ninguém importe nada. Quem quiser experimentar outra metodologia importa um **script Groovy**, e troca o apontamento **na tela**. Sem build, sem deploy, e com o caminho de volta sendo apontar de novo para o modelo embutido.

Restrições: Java 21, Spring Boot 3.4.x, sem Lombok, `BigDecimal` obrigatório em todo valor com política de arredondamento de mercado, e o motor roda como container sob Podman.

## Goals / Non-Goals

**Goals:**

- Construção determinística e auditável: dado o mesmo insumo, a mesma definição e o mesmo modelo, os vértices são idênticos.
- Metodologia iterável sem release da plataforma: trocar o modelo de uma curva é operação de tela.
- Interpolação síncrona rápida e correta, igual para curva construída e curva importada.
- Falha explícita e nomeada quando o insumo não existe — nunca curva parcial, nunca valor inventado.
- Kernel numérico próprio, puro e testável sem infraestrutura.

**Non-Goals:**

- Cadastrar curva, agendar execução ou ingerir dado — são de outros componentes.
- Recalcular curva importada da B3: ela é transcrita pelo processor e o motor apenas a interpola.
- Oferecer um ambiente de desenvolvimento de modelos completo (IDE, debugger remoto) na POC; o mínimo é conseguir rodar um modelo local sem empacotar.
- Executar modelo em linguagem arbitrária. Groovy, e só.
- Ciclo formal de promoção de modelo com manifesto, versão de SDK e aprovação — desnecessário na POC; ver D3.

## Decisions

### D1 — Kernel puro, motor orquestrador, modelo por cima

Três camadas com responsabilidades distintas:

- **`curve-kernel`** — estruturas e algoritmos numéricos. Sem Spring, sem banco, sem rede, sem estado global. É plataforma, muda devagar, e mudança nele é decisão de engenharia.
- **`curve-engine`** — orquestração: consome evento, resolve definição e modelo, carrega insumo, executa, persiste, publica. Não contém metodologia de curva.
- **modelo de construção** — o que diz *como* aquela curva se monta a partir dos blocos do kernel. Vem embutido em Java por padrão, ou importado em Groovy. É conteúdo, muda rápido, e mudança nele é decisão de quem escreve metodologia.

*Teste da separação*: se um ajuste de metodologia exigir tocar no kernel ou no motor, a fronteira está no lugar errado.

### D2 — Dois tipos de modelo: embutido e Groovy

`BUILTIN` é modelo fixo em Java, versionado junto com o motor, e é o padrão de toda curva suportada. `GROOVY` é script importado, registrado no catálogo e disponível para ser escolhido. Os dois implementam a mesma interface interna de construção e são intercambiáveis do ponto de vista do motor.

*Por que manter o embutido em vez de tudo virar Groovy*: a plataforma precisa funcionar recém-instalada, sem ninguém ter importado nada, e a metodologia oficial de uma curva não deve depender de alguém lembrar de subir um script. O embutido é o piso; o Groovy é a experimentação por cima dele.

### D3 — Trocar de modelo é configuração da definição, não operação de deploy

A definição de curva referencia o modelo. Trocar é apontar para outro, e isso gera uma nova versão da definição — então a mudança fica no histórico e a curva construída antes continua explicada pela versão que valia na época. Voltar atrás é apontar de volta.

*Alternativa considerada*: pacote de modelo versionado, com manifesto, versão de SDK, testes embarcados e um ciclo formal de promoção e reversão. Rejeitada para esta POC — resolve um problema de governança que ainda não existe aqui, e o peso da cerimônia atrapalharia justamente o que se quer provar, que é iterar metodologia rápido. O caminho para isso continua aberto: o catálogo de modelos já guarda checksum e autoria.

### D4 — Contenção do Groovy é requisito, não configuração

Modelo Groovy é código enviado por gente, rodando dentro do serviço que escreve curvas. Ele roda com classloader isolado, lista de permissão de pacotes, proibição de I/O (arquivo, rede, processo), limite de tempo e limite de memória. Violação aborta a execução e falha o run, nomeando a violação.

*Trade-off*: algum modelo legítimo vai esbarrar na contenção. É preferível à alternativa — código arbitrário com acesso irrestrito ao lado do banco de curvas.

### D5 — O modelo recebe insumo pronto e devolve vértices

A superfície que o modelo enxerga é estreita de propósito: estruturas do kernel, os insumos já resolvidos em modo somente-leitura, e um contexto com data de referência e parâmetros da definição. Devolve uma lista de vértices. Não recebe conexão, cliente HTTP nem relógio mutável.

*Consequência útil*: com essa fronteira, a contenção é viável, o gate de insumo faltante roda antes de qualquer código de usuário, e a proveniência sabe exatamente o que entrou.

### D6 — Insumo é resolvido pelo motor, nunca buscado pelo modelo

O motor lê `ponto_dado_mercado`, monta o conjunto de insumos exigido pela definição e o entrega pronto. O modelo não consulta banco. Assim o gate de insumo faltante roda **antes** de qualquer código de usuário, e a proveniência sabe exatamente o que entrou.

### D7 — Insumo faltante falha alto, antes de executar o modelo

Se falta ponto exigido, a construção falha nomeando índice e data, o run vai para `FAILED` e nada é publicado. Nunca interpolar, repetir valor anterior ou aplicar default para completar insumo.

*Distinção que importa*: interpolar **entre vértices de uma curva publicada** é o produto. Interpolar **para tapar buraco de insumo** é fabricar dado. A primeira é requisito; a segunda é proibida.

### D8 — Proveniência inclui o modelo usado

Sem isso, uma curva que mudou de valor sem nenhum insumo ter mudado vira mistério. Com isso, a diferença tem causa nomeada: outro modelo. Para modelo Groovy, a proveniência guarda também o checksum do código executado — o script pode ser reimportado alterado, e o que importa é o que rodou.

### D8b — Validação de consistência é peça própria, e é gate

Entre construir e publicar existe uma etapa de validação. Conceitualmente é outra peça: o motor sabe *montar* a curva; o validador sabe *desconfiar* dela. São competências diferentes e devem poder evoluir em ritmos diferentes.

O mecanismo é um estado a mais: a versão é gravada como `EM_VALIDACAO` — com vértices e procedência, na mesma transação —, os testes rodam, e só então ela é promovida a `PUBLICADA` ou marcada como `REPROVADA`. Nenhum consumidor enxerga versão que não passou pelo gate.

*Por que gate e não relatório posterior*: validar depois de publicar significa que a curva errada já foi consumida. O ponto da validação é impedir, não documentar.

*Por que estado no banco e não checagem em memória antes do commit*: assim o resultado fica persistido e auditável **mesmo quando reprova** — que é exatamente o caso em que alguém vai querer entender o que houve. Uma checagem em memória que aborta a transação não deixa rastro.

*Onde roda*: módulo próprio, empacotado com o motor na POC. Separá-lo em um pod acrescentaria salto de rede e ponto de falha dentro da janela crítica, imediatamente antes da publicação. O módulo é escrito de forma extraível, para que virar serviço seja decisão de operação e não reescrita.

### D8c — O teste que mais pega defeito é a reprecificação

A bateria tem vários testes, mas um deles é qualitativamente diferente: **reprecificar, com a curva construída, os próprios instrumentos que a calibraram, e conferir se volta ao preço observado**. É o teste padrão de mercado para bootstrap, e é o que pega erro de metodologia, de convenção e de calendário de uma vez só — porque todos eles se manifestam como erro de reprecificação.

Os demais testes — arbitragem, monotonicidade de fator de desconto, faixa de taxa, suavidade — pegam defeito grosseiro e são baratos. A reprecificação pega defeito sutil, que é o perigoso.

*Consequência*: o erro máximo de reprecificação é bloqueante e seu limite é declarado por curva, porque a tolerância aceitável depende da convenção do instrumento.

### D8d — Nem todo teste pode reprovar

Bloquear a publicação por variação forte contra o dia anterior significaria não entregar curva no dia de choque de mercado — justamente quando a mesa mais precisa dela. Esse teste, e outros de natureza estatística, emitem **aviso**: a curva é publicada, sinalizada, e o aviso aparece na tela ao lado do número.

A classificação de cada teste — bloqueante ou aviso — é configuração por curva, não constante de código, porque a tolerância depende do mercado que a curva representa.

*Cuidado deliberado*: teste de aviso que ninguém olha é pior que teste nenhum, porque dá falsa sensação de cobertura. Por isso o aviso não fica só no banco — ele acompanha a curva na consulta e na tela.

### D9 — Interpolação opera sobre vértices publicados, não reconstrói

A interpolação síncrona lê a `versao_curva` publicada e interpola entre seus vértices, com o interpolador e a política de extrapolação da definição. Isso a torna barata, determinística e igual para curva construída e curva importada.

*Alternativa considerada*: reconstruir a curva a cada pedido de interpolação. Rejeitada — cara, e devolveria número que não corresponde a nada publicado.

### D10 — Cache com `versionId` na chave

A chave inclui o identificador da versão, então publicar versão nova invalida por construção. Sem TTL adivinhado, sem invalidação manual, sem janela em que a tela mostra a curva antiga.

### D11 — Extrapolação é declarada, nunca implícita

Pedido de prazo fora do intervalo dos vértices segue a política declarada na definição. Se a política é estrita, a resposta é erro nomeando o intervalo disponível e o prazo pedido — não uma extrapolação silenciosa.

### D12 — Console de desenvolvimento de modelo

Para desenvolver um modelo sem subir o motor, existe um ponto de entrada local que carrega um `.groovy` do disco, injeta insumos de fixture e imprime os vértices. É ferramenta de desenvolvimento, desabilitada fora de perfil local.

### D13 — Comparar modelos é o que torna a troca segura

O motor permite construir a mesma curva e a mesma data com dois modelos diferentes e comparar os vértices. Sem isso, trocar de metodologia é aposta; com isso, é medição. A comparação usa o mesmo mecanismo já especificado para confrontar curva construída contra curva importada da B3.

## Risks / Trade-offs

- **Executar código de usuário dentro do serviço que escreve curvas** → contenção obrigatória (D4), autorização de administrador para importar e ativar, e registro de quem fez o quê. Ainda assim é a maior superfície de risco desta mudança, e merece revisão de segurança dedicada antes de qualquer ambiente compartilhado.
- **Kernel reimplementado pode reintroduzir erro já resolvido em outro projeto** → portar junto os casos de teste e as fixtures reais, e exigir reconciliação exata contra a taxa de referência oficial da B3 antes de considerar o kernel pronto. O oráculo é externo e independente das duas implementações.
- **Modelo Groovy com bug publica curva errada sem passar por release** → é o preço de abrir a metodologia. Mitigações: comparação contra o modelo embutido antes de trocar, reconciliação contra a curva oficial da B3 como controle contínuo, autorização de administrador para a troca, e volta ao modelo embutido em uma operação de tela.
- **Mudança na superfície que o modelo enxerga quebra Groovy já importado** → a superfície é deliberadamente estreita (D5) e mudá-la é decisão explícita; a validação na importação executa o modelo contra insumos de amostra, então incompatibilidade aparece na importação e não no meio de um run noturno.
- **`double` vazar do interior do kernel para valor com arredondamento de mercado** → verificação estática no build, além da revisão; a regra é que `double` só existe dentro de numérica iterativa, nunca em valor persistido ou serializado.
- **Bootstrap lento degradar a janela de fechamento** → construção é assíncrona por decisão de arquitetura, então lentidão atrasa a publicação, não a tela; medir o tempo por curva e expô-lo na execução, dentro do orçamento declarado.
- **Validação consumir orçamento demais dentro da janela crítica** → a bateria tem orçamento de tempo próprio, e os testes caros (reprecificação de todos os instrumentos) rodam sobre o conjunto de calibração, não sobre todo o universo de instrumentos.
- **Limites de validação mal calibrados** → limites muito apertados reprovam curva boa e param a publicação; muito frouxos não pegam nada. Mitigação: começar com os testes estruturais e de arbitragem como bloqueantes — cujos limites são objetivos —, e manter os estatísticos como aviso até haver série medida para calibrar.
- **Curva reprovada na véspera do fechamento** → a reprovação é falha explícita e visível, mas não resolve o problema de negócio de a mesa ficar sem curva; o comportamento nesse caso é decisão de Tesouraria e está registrado como questão em aberto.
- **Limite de tempo do sandbox abortar modelo legítimo pesado** → limite configurável por definição de curva, com o valor efetivo registrado no run.

## Migration Plan

1. **Kernel** — `libs/curve-kernel` com estruturas, interpoladores, extrapolação, convenções e arredondamento, testado contra fixtures reais.
2. **Bootstrap e montagem** — `CurveBootstrapper`, `RateHelper` e a montagem da curva PRE a partir de DI1, reconciliada exato contra a taxa de referência oficial da B3.
3. **Motor sem modelo** — construção por evento com a metodologia PRE embutida temporariamente, publicação atômica e proveniência. Fecha o fluxo ponta a ponta.
4. **Interpolação síncrona** — API, seleção de interpolador pela definição, extrapolação declarada e cache Redis.
5. **Modelos** — catálogo, modelo embutido registrado como padrão, importação de Groovy com validação e contenção, e seleção do modelo pela definição da curva.
6. **Prova da tese** — importar um Groovy alternativo para a curva PRE, trocar o apontamento pela tela, comparar o resultado contra o modelo embutido e voltar atrás — com o motor no ar o tempo todo, sem build e sem deploy.

**Rollback**: o motor é sem estado além do offset de consumo. Curvas já publicadas permanecem válidas. Reverter uma metodologia é apontar a definição de volta para o modelo embutido — que é justamente a operação que a mudança introduz.

## Open Questions

- Quem tem autoridade para trocar o modelo de uma curva — o próprio autor, ou exige aprovação de um segundo par? Determina se falta um passo de aprovação antes da troca surtir efeito.
- Trocar o modelo deve reconstruir automaticamente as curvas já publicadas, ou vale só daqui para frente? Reconstruir em massa muda o histórico de forma visível; não reconstruir deixa datas vizinhas com metodologias diferentes.
- O limite de tempo e de memória da contenção deve ser por modelo, por definição de curva ou global?
- A dependência entre curvas (uma curva que precisa de outra já publicada como insumo) entra na POC ou fica para depois? Hoje o desenho a prevê, mas a curva PRE não a exige.
- O console de desenvolvimento de modelo precisa suportar breakpoint em IDE, ou imprimir vértices é suficiente para a POC?
- Quais os limites iniciais de cada teste de validação, e quais começam como bloqueantes? Sem série medida, os estatísticos deveriam nascer como aviso.
- Curva reprovada deve disparar reconstrução automática com o modelo embutido, quando a reprovação veio de um modelo Groovy? Seria um fallback útil, mas mascara o defeito do modelo.
- Um modelo Groovy importado deve poder ser editado na própria tela, ou só substituído por reimportação?
