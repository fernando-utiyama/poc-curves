## Context

O `curve-api` é a superfície de domínio: cadastra o que uma curva é, e devolve o que foi publicado. É o componente mais simples da plataforma em mecânica — quase todo o trabalho é leitura — e um dos mais delicados em contrato, porque é através dele que o versionamento e a procedência viram resposta utilizável.

Dois fatos do desenho moldam esta API. Primeiro, **publicação nunca sobrescreve**: existe versão corrente, existem versões substituídas, e existe a pergunta "o que valia naquele instante". Segundo, **existem duas origens de curva** — construída e importada — que compartilham modelo de dados e devem compartilhar contrato de consulta, mas diferem no que a procedência registra.

O cadastro também mudou de natureza: a definição de curva agora carrega modo de origem, modelo de construção apontado e dependências. Alterar isso não é editar um registro; é criar uma versão nova da definição, porque as curvas já publicadas precisam continuar explicadas pela configuração que valia quando foram construídas.

## Goals / Non-Goals

**Goals:**

- Contrato de consulta uniforme entre curva construída e curva importada.
- Tornar acessíveis, por API, as respostas que o versionamento e a procedência tornaram possíveis.
- Cadastro que permita configurar uma curva inteira sem tocar em código, com validação que pegue incoerência no cadastro e não na construção noturna.
- Precisão preservada até o cliente.

**Non-Goals:**

- Calcular qualquer coisa. Interpolação é delegada ao motor; construção não passa por aqui.
- Autenticar usuário final ou aplicar regra de tela — é do BFF.
- Escrever curva, vértice ou procedência.
- Ser API pública para fora da plataforma.

## Decisions

### D1 — Editar definição cria versão, nunca altera a vigente

Toda alteração de definição gera `versao_definicao_curva` nova, com vigência. A anterior permanece, e as curvas publicadas sob ela continuam explicadas.

*Alternativa considerada*: `UPDATE` na definição com auditoria em tabela separada. Rejeitada — a procedência aponta para a versão da definição, então a versão precisa ser entidade de primeira classe, não um registro de log paralelo.

### D2 — Três formas de endereçar uma versão de curva, com padrão explícito

Sem parâmetro devolve a versão publicada corrente. Com identificador de versão devolve exatamente aquela. Com `asOf` devolve a que estava publicada no instante informado. A resposta **sempre** identifica qual versão foi usada, mesmo quando o cliente não pediu nenhuma.

*Por que a resposta sempre carrega a versão*: sem isso, dois clientes que consultam com segundos de diferença durante uma republicação recebem números diferentes sem ter como saber por quê.

### D3 — Interpolação é delegada, não reimplementada

O `curve-api` expõe o recurso de curva interpolada, mas chama o motor. Reimplementar interpolação aqui criaria uma segunda fonte de verdade numérica — o pior tipo de duplicação em um sistema de curvas.

*Trade-off*: acrescenta um salto de rede e acopla a disponibilidade da consulta interpolada à do motor. Aceito; a alternativa é divergência numérica silenciosa entre dois caminhos.

### D4 — Validação de coerência acontece no cadastro

Modo de origem compatível com os vínculos de fonte, interpolador e política existentes, modelo existente e habilitado, dependências sem ciclo. Tudo verificado ao salvar.

*Por quê*: o custo de descobrir uma incoerência de cadastro é muito menor às 10h, com o operador na tela, do que às 19h30, com a curva não saindo.

### D5 — Números como texto no JSON

Taxa, fator, cotação e VNA são serializados como string. O número JSON é IEEE 754 na prática de todo cliente JavaScript, e taxa com doze casas não sobrevive a isso.

*Trade-off*: o cliente precisa converter na borda. É explícito e uma vez só, contra perda silenciosa de dígito em todo lugar.

### D6 — Comparação de curvas é recurso de primeira classe

Comparar a curva construída com a importada da B3 para a mesma data é a operação que dá sentido a manter as duas. Ela vive aqui porque é leitura pura sobre vértices publicados, e retorna, por prazo, os dois valores, a diferença e a sinalização de prazos presentes em apenas uma das curvas.

### D7 — Vértices sempre paginados, mas com ordenação estável

Curvas B3 têm centenas de vértices. A listagem pagina por padrão, ordenada por prazo crescente, e a ordenação é estável entre páginas mesmo durante republicação, porque a página é sempre resolvida contra uma versão fixa.

### D8 — OpenAPI é o contrato, validado em teste

O contrato vive em `contracts/openapi/curve-api.yaml` e um teste verifica que a implementação corresponde a ele. O BFF é gerado ou validado contra o mesmo arquivo, o que evita a deriva clássica entre API e consumidor.

## Risks / Trade-offs

- **Consulta interpolada depende do motor estar no ar** → a resposta distingue "curva não existe" de "serviço de interpolação indisponível", para que a tela mostre a coisa certa; consultas não interpoladas continuam funcionando.
- **Cadastro versionado confunde quem espera editar um registro** → a API deixa explícito, na resposta, qual versão foi criada e a partir de qual; a tela apresenta isso como histórico, não como surpresa.
- **`asOf` mal compreendido devolve curva "errada" para quem esperava a atual** → a resposta sempre identifica a versão usada e a razão da seleção.
- **Comparação entre curvas com conjuntos de prazos diferentes** → prazos presentes em apenas uma são sinalizados explicitamente, nunca preenchidos por interpolação — completar a comparação com número interpolado seria inventar a diferença.
- **Números como string quebram cliente que esperava número** → é decisão consciente e documentada no contrato desde a primeira versão.
- **Paginação de vértices durante republicação** → a página é resolvida contra uma versão fixa, então o cliente nunca mistura vértices de duas versões.

## Migration Plan

1. Consulta de curva publicada e listagem de vértices — já entrega valor sozinho, sobre dado que o processor e o motor publicam.
2. Cadastro versionado de definição, com validações de coerência.
3. Histórico de versões e procedência.
4. Curva interpolada delegada ao motor.
5. Comparação entre curvas.
6. Contrato OpenAPI fechado e teste de conformidade.

**Rollback**: o serviço é quase todo leitura; reverter é voltar a imagem. Definições já criadas permanecem válidas, porque o versionamento não destrói estado anterior.

## Open Questions

- Deve existir exclusão de definição de curva, ou apenas aposentadoria? Excluir quebraria a procedência das curvas publicadas sob ela.
- A comparação entre curvas deve aceitar tolerância configurável para sinalizar apenas diferenças relevantes, ou reportar tudo e deixar o cliente filtrar?
- Consultas devem expor limite de intervalo de datas para evitar varredura acidental de anos inteiros?
- O cadastro precisa de um passo de aprovação antes de a nova versão da definição entrar em vigor, ou salvar já vale?
- A API deve expor exportação em massa (CSV) da curva, ou isso é responsabilidade do BFF para uso de tela?
