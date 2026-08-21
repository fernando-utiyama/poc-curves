## Context

O front é a última peça e a que torna a plataforma um produto. Ele é deliberadamente burro: não calcula, não valida regra de domínio, não conhece a topologia do backend. Fala com o `curve-bff` e mais nada.

Duas particularidades moldam o desenho. A primeira é de escopo: **na POC o front é uma aplicação Angular autônoma**. O embarque no shell Liquid como micro-frontend continua sendo o desenho-alvo, mas depende de um contrato de host que ainda não está confirmado, e amarrar a POC a essa incógnita atrasaria o que ela precisa provar. O que se faz agora é barato e preserva a opção: manter o app livre de suposições que dificultariam o embarque depois. A segunda é a **precisão**: valores de mercado chegam como texto numérico justamente porque o `number` de JavaScript não sustenta uma taxa com doze casas. O front pode formatar; não pode calcular.

O conteúdo das telas decorre do desenho: existem duas origens de curva que precisam aparecer lado a lado e comparáveis; existe versionamento com procedência, que precisa ficar ao lado do número em vez de escondido em outra tela; e existe disparo manual de ingestão para os dois tipos de insumo, que é o caminho operacional pedido explicitamente.

## Goals / Non-Goals

**Goals:**

- Fechar o ciclo: da ingestão disparada na tela até a curva exibida com sua procedência.
- Tornar visível a diferença entre a curva construída e a curva oficial da B3.
- Deixar o operador autônomo para disparar consumo de dado e entender o que aconteceu.
- Preservar precisão numérica até a exibição.
- Rodar de forma autônoma, sem host, e ficar pronto para embarcar no shell depois sem reescrita.

**Non-Goals:**

- Implementar regra de domínio, cálculo ou validação de negócio — vem tudo do BFF.
- Falar com `curve-api`, `curve-engine` ou `curve-orchestrator`.
- Integrar com o shell Liquid nesta mudança, ou construí-lo.
- Ser responsivo para uso em celular; é ferramenta de mesa, com telas densas.

## Decisions

### D1 — Angular puro na POC, embarque no shell adiado

O app roda sozinho: artefato estático, autenticação OIDC própria contra o Keycloak local, configuração injetada em tempo de execução.

*Por que adiar o micro-frontend*: o contrato do shell — como o token chega, quais dependências são compartilhadas e em quais versões, como rota profunda e recarga se comportam — está em aberto. Amarrar a POC a essas incógnitas trocaria risco técnico conhecido por risco de integração desconhecido, sem benefício para o que a POC precisa demonstrar.

*O que se paga agora para preservar a opção*: rota base configurável, nenhuma suposição sobre controle da rota raiz, tema por tokens substituíveis e contexto de usuário atrás de uma fonte única. São restrições baratas de manter desde o início e caras de introduzir depois — e sem elas o embarque futuro vira reescrita.

*Alternativa considerada*: já entregar como módulo remoto por Module Federation. Rejeitada por escopo, não por mérito: continua sendo o alvo.

### D2 — Cliente gerado a partir do contrato do BFF

Os tipos e o cliente HTTP são gerados de `contracts/openapi/curve-bff.yaml`. Divergência entre front e BFF vira erro de compilação, não erro em produção.

### D3 — Valores de mercado são `string` no modelo do front

O tipo no front é `string`, não `number`. Formatação para exibição usa biblioteca de decimal ou formatação sobre a string. Não há aritmética no navegador com valor de mercado — nem para calcular diferença: a diferença vem pronta do BFF.

*Por que é decisão e não detalhe*: basta um `parseFloat` para calcular uma variação na tela para a precisão se perder em silêncio, e o número exibido divergir do publicado.

### D4 — Procedência ao lado do número, não em outra tela

O viewer mostra, junto da curva, qual versão está sendo exibida, quando foi publicada, qual execução a gerou e qual modelo a produziu. Enterrar isso em uma tela secundária derrotaria o propósito de ter procedência.

### D5 — Disparo manual é uma tela de primeira classe

Escolher a data, marcar o que consumir — dado individual e/ou curva pronta —, disparar e acompanhar. A tela avisa antes quando a data não é dia de pregão, e mostra o `correlacao_id` de forma copiável, porque é ele que liga a ação ao que aconteceu depois.

### D6 — Ausência de dado tem tratamento visual próprio

`SEM_DADO` não é erro e não pode aparecer em vermelho ao lado de falhas reais. Estado neutro, com o motivo — feriado, ainda não divulgado — e a possibilidade de redisparar.

### D7 — Degradação parcial é estado de tela, não erro

Quando o BFF marca uma seção como indisponível, a tela mostra o restante e sinaliza a seção afetada com o motivo e a opção de tentar de novo. Seção indisponível nunca é renderizada como vazia.

### D8 — Permissão desabilita com explicação, não esconde

Ação que o perfil não permite aparece desabilitada com o motivo. Esconder faz o usuário achar que a funcionalidade não existe e abrir chamado; desabilitar com explicação ensina o modelo de permissão.

### D9 — Comparação é tela, não relatório exportado

Comparar construída contra importada, ou modelo contra modelo, acontece na tela, com tabela de diferenças por prazo e destaque para as maiores. É a tela que transforma "temos duas curvas" em controle diário.

### D10 — Densidade sobre elegância

Telas de mesa mostram muitos números. A prioridade é ler rápido tabela longa e comparar valores, não animação nem espaçamento generoso. Tabelas virtualizadas, tipografia tabular, alinhamento decimal.

## Risks / Trade-offs

- **Embarque futuro no shell virar reescrita** → mitigado pelas restrições estruturais de D1, verificadas por cenário de spec: rota base configurável, tema por tokens e contexto de usuário centralizado.
- **Contrato do shell só ser descoberto tarde** → as perguntas em aberto ficam registradas aqui; quando o embarque entrar em escopo, ele começa por uma integração mínima antes de qualquer outra coisa.
- **Alguém converter valor para `number` para "só fazer uma continha"** → tipagem como `string` e verificação de lint que sinaliza conversão numérica sobre campos de mercado.
- **Curvas B3 com centenas de vértices travarem a tabela** → virtualização de linhas desde o início, não como otimização posterior.
- **Sessão expirar no meio do trabalho** → tratamento padronizado de não autorizado, com reautenticação pelo host quando o shell provê, e mensagem clara quando não.
- **Tela de comparação sugerir que diferença é erro** → a apresentação distingue diferença esperada de divergência relevante, e a POC não fixa limiar sem dado real para calibrá-lo.

## Migration Plan

1. Esqueleto Angular autônomo e cliente gerado do contrato do BFF.
2. Autenticação OIDC contra o Keycloak local e as garantias estruturais de rota, tema e contexto.
3. Catálogo e viewer de curva com vértices e procedência.
4. Disparo manual de ingestão para os dois tipos de insumo, com monitor de execuções.
5. Consulta interpolada.
6. Cadastro de curva e telas de modelo.
7. Comparação.

**Rollback**: artefato estático versionado; reverter é servir a versão anterior. Nenhum estado de domínio é afetado.

## Open Questions

- Quando o embarque no shell entrar em escopo: o shell entrega o token ao micro-frontend, ou o app segue iniciando o fluxo OIDC?
- Quais dependências o shell compartilha, e em quais versões? Determina a configuração de Module Federation.
- O shell impõe biblioteca de componentes e tokens de tema, ou o app traz os seus?
- Como o shell trata rota profunda e recarga de página em uma rota do micro-frontend?
- A tela de comparação deve permitir exportar a tabela de diferenças, e em qual formato?
- Curva intradiária exige atualização automática da tela, ou recarregar manualmente basta para a POC?
