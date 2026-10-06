## Purpose

No front `web/fed`, a tela "Cadastro de curvas": o CRUD completo das curvas de mercado da `services/curves` (pesquisa, criação, alteração, inativação, reativação, auditoria e provedores da curva), sem ações sobre datas, que ficam na tela Curvas.

## ADDED Requirements

### Requirement: Pesquisa do cadastro
A tela `/cadastro-curvas`, no menu como "Cadastro de curvas", SHALL listar as curvas pela `GET /api/v1/curvas-mercado` da `services/curves`, com os filtros: trecho do nome, código, provedor (`B3`, `ANBIMA`, `Bloomberg` ou todos), dono (trecho), unidade (`TAXA`, `PRECO`, `PONTOS` ou todas) e situação (`ATIVO`, `INATIVO` ou todas; padrão `ATIVO`). Os campos de texto SHALL pesquisar 300 ms depois da última digitação; as listas, na hora. A tabela SHALL mostrar nome, código, provedor (o principal, com os demais ao passar o mouse; "Sem provedor" quando a lista vem vazia), dono, última execução (data-base em `dd/mm/aaaa` e o usuário, ou "Nunca construída"), unidade e situação, paginada de 50 em 50, com o total de curvas encontradas (`totalElementos` da resposta). Clicar numa linha (ou Enter nela) SHALL abrir `/cadastro-curvas/{codigo}`; o botão "Nova curva" SHALL abrir `/cadastro-curvas/nova`. A tela MUST NOT ler nem gravar curvas no armazenamento do navegador.

#### Scenario: Filtro por trecho do nome
- **WHEN** o gestor digita `cupom` no filtro de nome
- **THEN** depois de 300 ms o front chama `GET /api/v1/curvas-mercado?nome=cupom&situacao=ATIVO&pagina=0&tamanho=50` e mostra as curvas devolvidas

#### Scenario: Curvas inativas
- **WHEN** o gestor escolhe a situação `INATIVO`
- **THEN** a lista mostra só as curvas inativas, com a situação "Inativa"

### Requirement: Cadastro de uma curva nova
A tela `/cadastro-curvas/nova` SHALL ter os campos da spec `cadastro-curva-mercado`: código, nome, unidade, cotação de dias e capitalização (só quando a unidade é `TAXA`; escondidos e enviados nulos nas outras), moeda, país, classificação, classe de ativo, dono, início e fim de vigência. As listas fechadas (unidade, cotação de dias, capitalização) SHALL vir de `GET /api/v1/curvas-mercado/valores`, com os rótulos em pt-BR devolvidos. "Salvar" SHALL chamar `POST /api/v1/curvas-mercado`; com 201, o front SHALL abrir `/cadastro-curvas/{codigo}` com o aviso "Curva cadastrada com sucesso!". Erros SHALL aparecer junto do campo apontado em `detalhes` (422 `DADOS_INVALIDOS`) ou do código e do nome (409 `CODIGO_EM_USO`, `NOME_EM_USO`), sem apagar o que foi digitado.

#### Scenario: Nome em uso
- **WHEN** o gestor salva uma curva com o nome `CUPOM LIMPO DE DOLAR` e já existe `Cupom limpo de dólar`
- **THEN** o campo nome mostra a mensagem do erro `NOME_EM_USO`, e o formulário continua preenchido

#### Scenario: Unidade de preço
- **WHEN** o gestor escolhe a unidade `PRECO`
- **THEN** os campos de cotação de dias e capitalização somem, e o `POST` os envia nulos

### Requirement: Detalhe, alteração, inativação e reativação
A tela `/cadastro-curvas/{codigo}` SHALL carregar a curva por `GET /api/v1/curvas-mercado/{codigo}` (curva, provedores da curva e configuração vigente, esta só para leitura) e mostrar os campos em modo de leitura. "Editar" SHALL liberar os campos, menos o nome e a situação, que são imutáveis nessa tela; "Salvar" SHALL chamar `PUT /api/v1/curvas-mercado/{codigo}`. "Inativar" e "Reativar" SHALL pedir confirmação num modal e chamar `POST .../inativacao` ou `.../reativacao`. "Baixar auditoria" SHALL baixar o JSON de `GET /api/v1/curvas-mercado/{codigo}/auditoria` como `auditoria-{codigo}.json` (a planilha vem com a parte 2). "Abrir na tela Curvas" SHALL abrir `/curvas?codigo={codigo}`. Código inexistente (404) SHALL voltar para `/cadastro-curvas` com o aviso "Curva não encontrada".

#### Scenario: Inativar com confirmação
- **WHEN** o gestor clica em "Inativar" na `PRE` e confirma
- **THEN** o front chama `POST /api/v1/curvas-mercado/PRE/inativacao`, e a tela mostra a situação "Inativa" e o botão "Reativar"

#### Scenario: Nome bloqueado na edição
- **WHEN** o gestor clica em "Editar"
- **THEN** todos os campos ficam editáveis, menos o nome e a situação

### Requirement: Provedores da curva
A tela de detalhe SHALL mostrar os provedores da curva (provedor, produto, código na fonte, prioridade), em ordem de prioridade, com incluir, alterar (produto, código na fonte, prioridade) e excluir por modal, pelas rotas da spec `provedor-curva`. Os provedores oferecidos SHALL vir de `GET /api/v1/provedores`. Os avisos da resposta (por exemplo, `CURVA_SEM_ORIGEM` e `ORIGEM_INCOMPATIVEL_COM_MODELO`) SHALL ser mostrados depois da gravação; os erros 409 `PROVEDOR_DUPLICADO` e `PRIORIDADE_EM_USO`, no modal, sem fechá-lo.

#### Scenario: Prioridade repetida
- **WHEN** o gestor inclui um provedor com a prioridade já usada na curva
- **THEN** o modal continua aberto com a mensagem do erro `PRIORIDADE_EM_USO`

#### Scenario: Curva sem origem
- **WHEN** o gestor exclui o único provedor da curva
- **THEN** a tela mostra o aviso `CURVA_SEM_ORIGEM` devolvido pela API

### Requirement: Textos e formatos do cadastro
Todos os textos SHALL estar em pt-BR, com acentuação. Datas SHALL ser mostradas em `dd/mm/aaaa` e enviadas em `AAAA-MM-DD`. Erros de gravação SHALL aparecer na própria tela, sem levar à página de erro global.

#### Scenario: Vigência
- **WHEN** a API devolve `inicioVigencia` = `2026-01-01`
- **THEN** a tela mostra `01/01/2026`
