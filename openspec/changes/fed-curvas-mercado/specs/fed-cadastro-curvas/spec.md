## Purpose

No front `web/fed`, a tela "Cadastro de curvas": o CRUD completo das curvas de mercado da `services/curves` (pesquisa, criação, alteração, inativação, reativação, exclusão, auditoria e provedores da curva), sem ações sobre datas, que ficam na tela Curvas. A curva é identificada pelo **nome** (chave primária); o código é opcional.

## ADDED Requirements

### Requirement: Pesquisa do cadastro
A tela `/cadastro-curvas`, no menu como "Cadastro de curvas", SHALL listar as curvas pela `GET /api/v1/curvas-mercado` da `services/curves`, com os filtros: trecho do nome, código, provedor (`B3`, `ANBIMA`, `Bloomberg` ou todos), dono (trecho), unidade (`TAXA`, `PRECO`, `PONTOS` ou todas) e situação (`ATIVO`, `INATIVO` ou todas; padrão `ATIVO`). Os campos de texto SHALL pesquisar 300 ms depois da última digitação; as listas, na hora. A tabela SHALL mostrar nome, código ("—" quando a curva não tem), provedor (o principal, com os demais ao passar o mouse; "Sem provedor" quando a lista vem vazia), dono, última execução (data-base em `dd/mm/aaaa` e o usuário, ou "Nunca construída"), unidade e situação, paginada de 50 em 50, com o total de curvas encontradas (`totalElementos` da resposta). Clicar numa linha (ou Enter nela) SHALL abrir `/cadastro-curvas/{nome}`; o botão "Nova curva" SHALL abrir `/cadastro-curvas/nova`. A tela MUST NOT ler nem gravar curvas no armazenamento do navegador.

#### Scenario: Filtro por trecho do nome
- **WHEN** o gestor digita `cupom` no filtro de nome
- **THEN** depois de 300 ms o front chama `GET /api/v1/curvas-mercado?nome=cupom&situacao=ATIVO&pagina=0&tamanho=50` e mostra as curvas devolvidas

#### Scenario: Curvas inativas
- **WHEN** o gestor escolhe a situação `INATIVO`
- **THEN** a lista mostra só as curvas inativas, com a situação "Inativa"

#### Scenario: Curva sem código
- **WHEN** a API devolve uma curva com `codigo` nulo
- **THEN** a linha mostra "—" na coluna código e abre normalmente pelo nome

### Requirement: Cadastro de uma curva nova
A tela `/cadastro-curvas/nova` SHALL ter os campos da spec `cadastro-curva-mercado`: nome (obrigatório), código (opcional), unidade, cotação de dias e capitalização (só quando a unidade é `TAXA`; escondidos e enviados nulos nas outras), moeda, país, classificação, classe de ativo, dono, início e fim de vigência. As listas fechadas (unidade, cotação de dias, capitalização) SHALL vir de `GET /api/v1/curvas-mercado/valores`, com os rótulos em pt-BR devolvidos. "Salvar" SHALL chamar `POST /api/v1/curvas-mercado`; com 201, o front SHALL abrir `/cadastro-curvas/{nome}` com o aviso "Curva cadastrada com sucesso!". Em erro, o front SHALL mostrar **a mensagem devolvida pela API** acima do formulário (422 `DADOS_INVALIDOS`, 409 `NOME_EM_USO` ou `CODIGO_EM_USO`), sem apagar o que foi digitado; a API devolve a mensagem completa, e o front não interpreta `detalhes`.

#### Scenario: Nome em uso
- **WHEN** o gestor salva uma curva com o nome `CUPOM LIMPO DE DOLAR` e já existe `Cupom limpo de dólar`
- **THEN** a tela mostra a mensagem do erro `NOME_EM_USO`, e o formulário continua preenchido

#### Scenario: Unidade de preço
- **WHEN** o gestor escolhe a unidade `PRECO`
- **THEN** os campos de cotação de dias e capitalização somem, e o `POST` os envia nulos

### Requirement: Detalhe, alteração, inativação e reativação
A tela `/cadastro-curvas/{nome}` SHALL carregar a curva por `GET /api/v1/curvas-mercado/{nome}` (curva, provedores da curva e configuração vigente, esta só para leitura) e mostrar os campos em modo de leitura. "Editar" SHALL liberar os campos, menos o nome e a situação, que são imutáveis nessa tela; "Salvar" SHALL chamar `PUT /api/v1/curvas-mercado/{nome}`. "Inativar" e "Reativar" SHALL pedir confirmação num modal e chamar `POST .../inativacao` ou `.../reativacao`. "Baixar auditoria" SHALL baixar o JSON de `GET /api/v1/curvas-mercado/{nome}/auditoria` como `auditoria-{nome}.json` (a planilha vem com a parte 2). "Abrir na tela Curvas" SHALL abrir `/curvas?nome={nome}`; "Configuração de cálculo" SHALL abrir `/cadastro-curvas/{nome}/configuracoes` (spec `fed-configuracao-curva`). Nome inexistente (404) SHALL voltar para `/cadastro-curvas` com o aviso "Curva não encontrada". O nome SHALL ser codificado (`encodeURIComponent`) em toda URL, porque tem espaços e acentos.

#### Scenario: Inativar com confirmação
- **WHEN** o gestor clica em "Inativar" na curva `DIxPRE` e confirma
- **THEN** o front chama `POST /api/v1/curvas-mercado/DIxPRE/inativacao`, e a tela mostra a situação "Inativa" e o botão "Reativar"

#### Scenario: Nome bloqueado na edição
- **WHEN** o gestor clica em "Editar"
- **THEN** todos os campos ficam editáveis, menos o nome e a situação

### Requirement: Excluir curva
O detalhe SHALL ter o botão "Excluir curva", com modal de confirmação que cita o nome da curva, chamando `DELETE /api/v1/curvas-mercado/{nome}`. Com 200, o front SHALL voltar para `/cadastro-curvas` com o aviso "Curva excluída". Com 409 `CURVA_COM_HISTORICO` (ainda há vértice construído ou dado bruto), o modal SHALL continuar aberto com a mensagem da API, que explica o que apagar antes.

#### Scenario: Curva com histórico
- **WHEN** o gestor tenta excluir uma curva que tem datas construídas
- **THEN** o modal mostra a mensagem do erro `CURVA_COM_HISTORICO` e a curva continua cadastrada

### Requirement: Provedores da curva
A tela de detalhe SHALL mostrar os provedores da curva (provedor, produto, ticker do provedor, prioridade), em ordem de prioridade, com incluir, alterar (produto, ticker do provedor, prioridade) e excluir por modal, pelas rotas `/api/v1/curvas-mercado/{nome}/provedores[/{idCurvaProvedor}]` da spec `provedor-curva`. Os provedores oferecidos SHALL vir de `GET /api/v1/provedores`. Os erros 409 `PROVEDOR_DUPLICADO` e `PRIORIDADE_EM_USO` SHALL aparecer no modal, sem fechá-lo. O front MUST NOT mostrar avisos de cadastro (a API não os devolve).

#### Scenario: Prioridade repetida
- **WHEN** o gestor inclui um provedor com a prioridade já usada na curva
- **THEN** o modal continua aberto com a mensagem do erro `PRIORIDADE_EM_USO`

### Requirement: Textos e formatos do cadastro
Todos os textos SHALL estar em pt-BR, com acentuação. Datas SHALL ser mostradas em `dd/mm/aaaa` e enviadas em `AAAA-MM-DD`. Erros de gravação SHALL aparecer na própria tela, sem levar à página de erro global.

#### Scenario: Vigência
- **WHEN** a API devolve `inicioVigencia` = `2026-01-01`
- **THEN** a tela mostra `01/01/2026`
