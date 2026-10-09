## Purpose

No front `web/fed`, a tela das versões da configuração de cálculo de uma curva (`tConfgCurva`): listar, validar, criar e excluir versões, para o gestor manter o modelo, o interpolador e os parâmetros sem planilha nem Swagger.

## ADDED Requirements

### Requirement: Lista de versões
A tela `/cadastro-curvas/{nome}/configuracoes` SHALL listar as versões por `GET /api/v1/curvas-mercado/{nome}/configuracoes` (da mais nova para a mais antiga), com versão, modelo de construção, interpolador, início e fim de vigência (`dd/mm/aaaa`; fim vazio = "Em aberto") e uma marca na versão vigente hoje. Clicar numa linha SHALL mostrar os parâmetros dela (JSON formatado, só leitura). O botão "Voltar" SHALL abrir o detalhe da curva.

#### Scenario: Duas versões
- **WHEN** a curva tem a versão 1 (até `25/09/2026`) e a versão 2 (em aberto)
- **THEN** a tabela mostra a 2 primeiro, e a vigente hoje está marcada

### Requirement: Nova versão
O botão "Nova versão" SHALL abrir um formulário com modelo de construção, interpolador, início de vigência e parâmetros (caixa de texto em JSON, já preenchida com os parâmetros da versão mais nova quando houver). Modelo e interpolador SHALL ser listas de `GET /api/v1/curvas-mercado/valores` (com os nativos; um nome de script que não esteja na lista também é aceito digitado). "Validar" SHALL chamar `POST .../configuracoes/validacao` e mostrar os erros devolvidos, sem gravar. "Salvar" SHALL chamar `POST .../configuracoes`; com 201, a lista é recarregada com o aviso "Versão criada". Em erro (422 `DADOS_INVALIDOS`, por exemplo chave desconhecida ou início no passado), o formulário SHALL mostrar a mensagem da API e manter o que foi digitado. JSON malformado SHALL ser apontado no front, sem chamar a API.

#### Scenario: Chave escrita errada
- **WHEN** o gestor valida parâmetros com a chave `EXTRAPOLACAO_FINAL`
- **THEN** a tela mostra a mensagem 422 da API citando a chave, e nada é gravado

#### Scenario: Início no passado
- **WHEN** o gestor salva uma versão com início anterior a hoje
- **THEN** a tela mostra a mensagem do erro `DADOS_INVALIDOS` e o formulário continua preenchido

### Requirement: Excluir versão
Cada linha SHALL ter "Excluir", com modal que cita a versão, chamando `DELETE /api/v1/curvas-mercado/{nome}/configuracoes?versao={n}`. Com 200, a lista é recarregada com o aviso "Versão excluída". Com 409 `VERSAO_EM_USO` (há curva construída na vigência da versão), o modal SHALL continuar aberto com a mensagem da API, que explica quais datas apagar antes.

#### Scenario: Versão em uso
- **WHEN** o gestor exclui a versão que tem datas construídas na vigência
- **THEN** o modal mostra a mensagem `VERSAO_EM_USO` e a versão continua na lista

### Requirement: Textos e formatos da configuração
Todos os textos SHALL estar em pt-BR, com acentuação. Datas SHALL ser mostradas em `dd/mm/aaaa` e enviadas em `AAAA-MM-DD`. Erros SHALL aparecer na própria tela, sem levar à página de erro global.

#### Scenario: Início de vigência
- **WHEN** a API devolve `inicioVigencia` = `2026-10-01`
- **THEN** a tela mostra `01/10/2026`
