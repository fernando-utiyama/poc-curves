## Context

- O cadastro que o engine lê está descrito na spec `curve-build-pipeline` do change `engine-modelos-curva`: curva em `tCurvaMercd` (código em `cTickerIdtfdUnic`, nome em `cTickerIndcd`, unidade e cotação), origem em `tCurvaPrvdr` (menor `cPriorCsumo`), configuração vigente em `tConfgCurva` (uma por data) e parâmetros em JSON em `tConfgCurva.cModDado`.
- O processor (change `conector-b3-webhook-ingest`) grava os dados brutos sob cada curva ligada em `tCurvaPrvdr` ao código da fonte.
- O schema não pode mudar nesta fase. `tCurvaMercd.cTickerIndcd` é a PK e é referenciada por todas as FKs; `tCurvaPrvdr.cldtfdUnic` é `int NOT NULL` sem identity nem sequência; `tConfgCurva.cldtfdConfg` é identity.
- `services/curves` ainda será transcrito das fotos do sistema real. O CRUD de provedores está em andamento por outro dev no mesmo serviço.

## Goals / Non-Goals

**Goals:**
- Criar e manter tudo o que o engine e o processor precisam ler, sem SQL manual.
- Nunca deixar o cadastro num estado que o engine não consiga ler: uma configuração vigente por data, parâmetros válidos, versões passadas intactas.
- Edição em lote segura por planilha.

**Non-Goals:**
- CRUD de provedores (outro dev).
- CRUD dos pontos (`tDadoCurva`): change `curves-dado-curva`.
- Mudança de schema, tópico novo ou tabela nova.

## Decisions

### D1. Nome imutável, código alterável
`cTickerIndcd` (nome) é a PK referenciada por `tCurvaPrvdr`, `tConfgCurva`, `tDadoCurva` e todas as tabelas brutas. Renomear exigiria atualizar todas as FKs, então o nome é imutável. O código (`cTickerIdtfdUnic`) é só identificador de rota e pode mudar. Como o schema não garante unicidade do código, o serviço garante (código único, nome único depois de normalizado), o que evita o `CODIGO_DUPLICADO` e o `NOME_AMBIGUO` do engine.

### D2. Sem exclusão física de curva
A curva pode ter pontos, brutos e configurações dependentes. Inativar (`cSitReg`) preserva o histórico; excluir fisicamente fica fora do serviço.

### D3. Configuração só por versão nova, vigente a partir de hoje
Alterar uma versão que já começou mudaria o resultado de um reprocessamento de data antiga, e o engine depende da vigência para ser reprodutível. Por isso versões não se alteram: cria-se uma nova, com início hoje ou depois, e o serviço fecha a anterior na mesma transação. A primeira versão pode começar no passado, porque ainda não existe construção feita com outra configuração. Excluir só vale para a última versão ainda não iniciada. O resultado é sempre uma sequência contínua, com uma e só uma configuração por data.

### D4. Validação de parâmetros igual à do engine, com modelos como aviso
Os parâmetros seguem a tabela da spec do engine (chaves, tipos, valores e combinações), para que o engine não encontre `CADASTRO_INVALIDO` depois. Os nomes de modelo não são bloqueados: um script Groovy pode criar um modelo novo sem deploy, e só o engine sabe o que está ativo. Nome fora dos nativos gera o aviso `MODELO_NAO_NATIVO`, e a simulação do engine confirma antes da produção.

### D5. `idLigacao` sem sequência
`tCurvaPrvdr.cldtfdUnic` não tem geração automática, e o schema não pode mudar. O serviço lê `MAX + 1` com `UPDLOCK, HOLDLOCK` na mesma transação da inserção, o que serializa inserções simultâneas. Uma sequência no banco é o alvo ideal quando o schema puder mudar.

### D6. Concorrência otimista por curva
Curva, ligações e configurações são editadas juntas, muitas vezes por pessoas diferentes. O `ETag` da curva cobre os três, e toda alteração exige `If-Match`. É um hash do conteúdo, e não o `dUltAtulz`: o `datetime` do SQL Server tem precisão de cerca de 3 ms, e o hash deixa de fora os campos que o engine grava (`dBaseReft`, `cUsuarCalc`), para uma construção não invalidar a edição de ninguém. A planilha guarda esse `ETag` na coluna `Controle`, então uma importação de planilha antiga não sobrescreve uma alteração feita depois da exportação.

### D7. Planilha como estado desejado, com simulação
A importação trata cada curva listada como estado completo desejado (curva, ligações e configurações), e compara com o banco:
- exportar e importar sem editar dá zero mudanças;
- apagar uma linha de ligação a exclui;
- editar uma versão existente é erro, porque versões não se alteram.

A simulação devolve a própria planilha marcada linha a linha, e a aplicação é uma transação única: ou todo o lote entra, ou nada entra.

**Alternativa rejeitada:** coluna de ação por linha (incluir, alterar, excluir). É mais fácil de errar, e uma planilha exportada e reimportada não seria automaticamente neutra.

### D8. Auditoria no Blob, como no engine
Sem tabela nova, a auditoria segue o padrão do engine: registro imutável por alteração, com estado anterior e novo, em `auditoria-cadastro/{nome}/`. A pasta é pelo nome, que é imutável, para o histórico sobreviver a uma troca de código. Se o Blob estiver fora, a alteração segue, e o registro fica no log até ser regravado. Alterações de um mesmo lote de planilha compartilham o `idLote`.

### D9. Avisos de coerência com engine e processor, sem bloquear
O serviço sinaliza curva sem ligação, origem incompatível com o modelo de construção nativo e modelo não nativo, mas não bloqueia, porque são estados válidos durante uma configuração em etapas. O engine e o processor continuam sendo quem rejeita no uso.

### D10. Efeito do cadastro no engine
Inativar a curva, ou deixar a data-base fora da vigência dela (`dInicVgcia`..`dValidAte`), faz a carga não construir a curva automaticamente naquela data. O usuário ainda pode construí-la pela rota de construção do engine, que responde com aviso. Os pontos já gravados continuam consultáveis. A edição manual dos pontos é do change `curves-dado-curva`, não do cadastro.

### D11. Valores aceitos vêm do engine
Quem sabe o que é aceito no cálculo é o engine, e os modelos crescem com scripts Groovy sem deploy. Por isso o engine expõe os valores aceitos (`GET /api/v1/valores-cadastro`), gerados dos próprios enums do validador, e o curves os repassa em `GET /api/v1/curvas-mercado/valores`, acrescidos dos provedores. O front, a aba `Valores` e as listas suspensas da planilha usam só essa rota. O Swagger declara os `enum` fixos para quem integra por API. Com o engine fora, o curves responde com a cópia embutida e aviso, e um teste de contrato impede que essa cópia divirja do engine.

**Alternativa rejeitada:** só `enum` no Swagger. Não mostra os modelos Groovy ativos nem as regras de combinação, e o front acabaria repetindo as listas.

### D12. Painel no curves, situação das construções pelo engine
O painel é tela do gestor, e o gestor trabalha no curves (cadastro e pontos). O curves já sabe o cadastro, a última data publicada e os pontos gravados; falta o que só o engine sabe: se a carga chegou, a última tentativa, o erro e o `hashPontos` da última construção. O engine expõe isso numa rota só de leitura, e o curves compõe a situação. Comparar o `hashPontos` gravado com o da última construção revela a edição manual sem nenhum registro extra, e comparar o `idCarga` da carga registrada com o da última construção revela a republicação sem recálculo. Com o engine fora, o painel mostra o que o curves tem, com aviso, e nunca falha.

O atraso depende do horário em que cada provedor costuma publicar, configurado por provedor no serviço (`curves.painel.horario-esperado.{provedor}`), sem tabela nova. **Alternativas rejeitadas:** painel no engine (misturaria tela de gestão com cálculo e exigiria que o engine lesse o cadastro para a tela); o curves ler o registro de cargas direto no Blob (acoplamento ao formato interno do engine).

## Risks / Trade-offs

- **Regras de parâmetros duplicadas entre curves e engine.** → A spec do engine é a fonte; os testes do curves usam os mesmos casos da tabela. Uma chave nova no engine exige atualizar os dois.
- **`MAX + 1` com trava serializa inserções de ligações.** → O volume é baixo (dezenas de ligações), e a trava dura só a transação.
- **Importação grande trava muitas linhas numa transação.** → Limite de 1.000 curvas e 5 MB; a simulação roda sem trava.
- **Painel depende do engine para a situação das construções.** → Com o engine fora, mostra cadastro, última data publicada e pontos, com `SITUACAO_INDISPONIVEL` e aviso; uma chamada por consulta, com tempo limite de 10 segundos.
- **Serviço ainda não transcrito.** → A change define comportamento, não estrutura; a implementação se encaixa no código real quando ele existir.

## Migration Plan

1. Entra ID: criar o papel `Curvas.Cadastro` e atribuí-lo a quem cadastra; atribuir `Curvas.Leitura` do engine à identidade gerenciada do curves, que chama o engine para os valores aceitos, a situação do painel e os calendários.
2. Blob: acesso de escrita do serviço à pasta `auditoria-cadastro/`, por Managed Identity.
3. Deploy do serviço. As curvas do poc criadas por SQL passam a ser mantidas pela API. As linhas sem código (`B3_TAXA_SWAP_*`) ficam invisíveis nas rotas.
4. **Rollback:** voltar o deploy; os dados gravados continuam válidos para o engine.

## Open Questions

- Com o CRUD de provedores (outro dev): os identificadores dos provedores precisam ser exatamente `B3`, `ANBIMA` e `BLOOMBERG`, que o engine e o processor usam; e a relação entre `tCurvaPrvdr.cPrvdrMercd` (produto) e `tPrvdrDadoMercd.cProdt`, se o produto da ligação precisa existir no provedor.
