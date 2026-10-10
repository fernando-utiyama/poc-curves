Primeira parte do curves. Guia: [`implementacao.md`](implementacao.md). Cada tarefa abaixo é **uma funcionalidade** a conferir: o que tem de fazer, onde está a regra (spec e seção do guia) e quando está pronta. A ordem desta lista vale sobre a seção 13 do guia. Os "pronto quando" são conferidos **lendo o código**: não rode `mvn test` nem a suíte; `mvn -q compile` só depois de alterar código. Antes da primeira tarefa, se ainda não houver `inspecao-curves.md`, faça a inspeção de [`verificacao.md`](verificacao.md) e pare.

> **Esta change é de conferência: boa parte do curves já foi desenvolvida no repositório real**, muitas vezes com outro nome (por exemplo, `Ponto` ou `Linha` no lugar de `Vertice`, ou em inglês). Esta change já foi aplicada no repositório real numa versão anterior, antes de ser separada em partes e renomeada; por isso há muita coisa feita, com nomes antigos e com peças que hoje estão em outra change (ex.: cliente do engine, vértices manuais, `/primaria-b3`) ou saíram. Cada tarefa SHALL seguir este roteiro:
> 1. **Conferir:** procurar pela **funcionalidade** (o que faz, que tabela lê ou grava, que rota expõe), nunca só pelo nome da spec.
> 2. Decidir uma de três saídas:
>    - **já existe e cumpre a spec → deixar como está** (não reescrever, não "melhorar"; só o nome segue o passo 3);
>    - **existe e diverge → alterar** só o que diverge, no código que já existe;
>    - **não existe → criar**, com os nomes desta change.
> 3. Nunca criar uma segunda versão do que já está pronto. **Nome diferente do desta change é divergência:** renomeie para o nome da change (classe, arquivo, método, campo interno e todas as referências, até compilar), menos o que outro serviço ou o banco já usa (rota exposta, campo de JSON de resposta, coluna, tópico): isso fica como está e vai para a anotação como `nome-real → nome-da-change`.
> 4. Ao marcar a tarefa, anotar ao lado a saída e o arquivo: `[conferido: pronto | alterado | renomeado de X | criado] caminho/Arquivo.java`. Na dúvida se é a mesma coisa, `// TODO(revisao): <dúvida>` e siga.
> 5. Código que a spec atual não pede (sobra da versão anterior): não apagar; anotar como `[sobra] caminho/Arquivo.java` no resumo, para a revisão decidir.

**Escopo:** CRUD da curva de mercado (com a listagem de provedores, dono e última execução), dos provedores da curva e da configuração de cálculo (com os valores aceitos); os vértices brutos dos três provedores para a tela Dados de mercado; e o repasse ao engine para a tela Curvas, única chamada ao engine nesta parte. Planilha do cadastro, painel, vértices manuais, planilha de vértices e origens secundárias são da change `curves-operacao-curvas`. Sem autenticação na v0 e na v1. Planilhas (importação, exportação e `formato=xlsx`) **não fazem parte da v0 nem da v1**: ficam para a parte 2 (`curves-operacao-curvas` e `engine-modelos-curva`). Ignore qualquer menção a planilha que tenha sobrado no guia ou no design.

**Blocos por modelo.** A seção 0 é **[básico]**: só a casca do que é novo nesta versão (rotas, DTOs, portas e cliente HTTP), com molde no projeto e verificação objetiva; rode-a com um **modelo barato** até a PAUSA 0.5 e pare. As seções 1 a 7 são **[forte]** (conferência do que já existe e regras): abra uma sessão nova com o **modelo forte** e rode `/opsx-apply` de novo; ele segue da primeira tarefa desmarcada, começando por conferir o diff da seção 0.

## 0. Bloco básico (modelo barato)

- [ ] 0.1 [básico] (guia §16.2) `EnginePort`, `RespostaEngine` e `EngineHttpClient` exatamente como o código do guia (`curves.engine.url` sem padrão, 120/60/30 s, `X-Usuario` quando vier, `X-Correlation-Id`, sem `Authorization`), `EngineIndisponivelException` e o código `ENGINE_INDISPONIVEL` (503) no enum de erros e em `messages.properties`, no molde das exceções que já existem; verificar com `mvn -q compile`
- [ ] 0.2 [básico] (guia §16.3) `CurvaMercadoAcoesAPI` + `CurvaMercadoAcoesController` com as cinco rotas da spec `acoes-curva-mercado`, chamando um `CurvaMercadoAcoesService` cujos métodos só repassam ao `EnginePort` (as validações e a conversão de erro ficam para a tarefa 6.1); verificar com `mvn -q compile`
- [x] 0.3 [básico] Substituída na v1.2 (guia §11): sem rotas `/dados-mercado`; o bruto fica nos CRUDs por provedor
- [ ] 0.4 [básico] (guia §2.2) Na listagem `GET /curvas-mercado`: os parâmetros `provedor` e `dono`, o record da resposta `{ itens, pagina, tamanho, total }` e os campos novos do item (`provedores`, `dono`, `ultimaExecucao`) e o campo `dono` (`cPprioDado`) na entrada e na saída da curva; a consulta que preenche os campos novos fica para a tarefa 2.2; verificar com `mvn -q compile`
- [ ] 0.5 **PAUSA (troca para o modelo forte):** pronto quando `mvn compile` passa e o Swagger mostra as rotas novas; escrever o resumo (arquivos criados e alterados, `TODO(revisao)`) e parar.

## 1. Base

- [ ] 1.1 [forte] **Erros, correlação, fuso e contrato de tipos** (spec `cadastro-curva-mercado`: "Sem autenticação e erros", "Contrato de tipos para o front", "Horário e log"; guia §1.3 a §1.5). Pronto quando: um tratador só, com o código da spec, `detalhes` e `correlationId` em cada erro (um teste por código); `X-Correlation-Id` em toda resposta; subida falha com fuso diferente de `America/Sao_Paulo`; decimais como string, datas `AAAA-MM-DD`, `avisos` sempre presente, espaços das colunas `CHAR` aparados; textos pt-BR em `messages.properties`; nenhuma rota exige token, `X-Usuario` opcional.
- [ ] 1.2 [forte] **Auditoria do cadastro** (spec `cadastro-curva-mercado`: "Auditoria do cadastro"; guia §2.5). Pronto quando: `CADASTRO_ALTERADO` sai depois do commit de toda alteração, com estado anterior e novo, e não sai quando o commit falha; `GET .../{codigo}/auditoria` em JSON montado na hora; nada no Blob.

## 2. Curva de mercado

- [ ] 2.1 [forte] **CRUD da curva** (spec `cadastro-curva-mercado`: "Campos da curva de mercado", "Rotas da curva de mercado", "Última gravação vence"; guia §2). Pronto quando: os cenários da spec passam (criação da DIxPRE, nome que colide, nome em uso por curva sem código, `inicioVigencia` ausente → 422 antes de comparar datas, preço com cotação, renomear recusado, inativação, duas alterações em que a última vence); `dono` gravado em `cPprioDado`; `dBaseReft`, `cUsuarCalc` e as colunas sem uso nunca escritos.
- [ ] 2.2 [forte] **Listagem** (completa a casca da 0.4) (spec `cadastro-curva-mercado`: "Resposta da listagem"; guia §2.2). Pronto quando: resposta `{ itens, pagina, tamanho, total }`; filtros nome, código, unidade, situação, `provedor` e `dono`; cada item com `provedores` (ordem de prioridade, numa consulta só por página), `dono` e `ultimaExecucao` (nulo sem `dBaseReft`); os dois cenários do requisito passam.

## 3. Provedores da curva

- [ ] 3.1 [forte] **CRUD dos provedores da curva** (spec `provedor-curva`: "Campos do provedor da curva", "Rotas dos provedores da curva", "Curvas componentes como provedor", "Avisos de coerência com o engine"; guia §3). Pronto quando: os cenários da spec passam (provedor existente, unicidade por curva e por prioridade, mesmo código em várias curvas, `PUT` que troca o provedor → 422, `TCEN` sem ciclo, `CURVA_COM_FILHAS`, `CURVA_SEM_ORIGEM`, `ORIGEM_INCOMPATIVEL_COM_MODELO`); `idCurvaProvedor` por `MAX + 1` com trava.

## 4. Configuração de cálculo

- [ ] 4.1 [forte] **Validação e versões** (spec `configuracao-calculo-curva`: "Campos da configuração", "Vigência sem sobreposição e sem buraco", "Rotas da configuração", "Coerência entre curva e configuração"; guia §4). Pronto quando: um teste por regra de parâmetro, com os mesmos casos da spec do engine; `cModDado` em JSON compacto até 1.024 caracteres; os cenários de vigência passam (troca a partir de amanhã, correção retroativa, desistência de versão futura, vigente numa data antiga); mudança de unidade que invalida a configuração recusada; aviso `MODELO_NAO_NATIVO`.
- [ ] 4.2 [forte] **Valores aceitos** (spec `configuracao-calculo-curva`: "Valores aceitos para o front, o Swagger e a planilha"; guia §5). Pronto quando: `GET /curvas-mercado/valores` servido pelo próprio serviço (sem engine, sem cache), com `rotulo` e `descricao` de cada valor, e o Swagger lista os valores de cada chave de `parametros`.

## 5. Vértices brutos dos provedores (tela Dados de mercado)

- [ ] 5.1 [forte] **Dado bruto dos três provedores** (spec `vertices-brutos-provedor`; guia §11 → `docs/curves-v1-2/10-dado-bruto/`). Pronto quando: rotas por nome nas três fontes, sem avisos; listagem com a última data por curva sem período; 409 `DATA_CONSTRUIDA` ao apagar a data construída; `CURVA_PRIMARIA_EDITADA` publicado; os cenários da spec passam.

## 6. Repasse ao engine (tela Curvas)

- [ ] 6.1 [forte] **Cinco rotas de repasse** (completa a casca da 0.1 e 0.2) (spec `acoes-curva-mercado`; guia §16). Pronto quando: construir/recalcular, regravar a interpolada, vértices, pontos e interpolar repassam ao engine com `X-Correlation-Id`, `X-Usuario` quando vier e sem `Authorization`; 2xx volta sem alteração, 4xx no formato de erro da curves, tempo esgotado/rede/5xx → 503 `ENGINE_INDISPONIVEL`; curva inexistente → 404 e parâmetros inválidos → 400 sem chamar o engine; query string da interpolação intacta.

## 7. Fechamento

- [ ] 7.2 [forte] **Sobras**. O que sobrar da versão anterior e a spec atual não pede (ex.: `EnginePort`/cache de valores, rotas de vértices manuais, 412/428) fica e vai para o resumo como `[sobra]`.
- [ ] 7.3 [forte] Escrever o resumo com a anotação de cada tarefa (`pronto`, `alterado`, `renomeado de X`, `criado`) e as `[sobra]`.
