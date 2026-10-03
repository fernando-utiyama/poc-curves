## ADDED Requirements

### Requirement: Rota de upload no bff
O bff SHALL expor `POST /api/v1/cargas/upload`, autenticada, em `multipart/form-data` com as partes `fonte` (`B3`, `ANBIMA` ou `BLOOMBERG`) e `arquivo` (até 10 MB), permitida só ao perfil de operação, o mesmo das outras ações de operação do bff. O bff SHALL repassar o arquivo à rota `POST /api/v1/cargas/{fonte}/upload` do processor, com a fonte em minúsculas (spec `carga-arquivos-processor`), pelo endereço configurado, com o cabeçalho `X-Usuario` = usuário autenticado e o `X-Correlation-Id` da requisição (recebido ou gerado), sem repassar o token. O bff MUST NOT interpretar nem guardar o arquivo. A resposta e o código HTTP do processor SHALL voltar ao front sem alteração; processor fora ou tempo esgotado (120 segundos) SHALL resultar em 503 `SERVICO_INDISPONIVEL`.

Sem autenticação, 401; sem o perfil, 403; `fonte` fora da lista, arquivo ausente ou maior que 10 MB, 400 `PARAMETRO_INVALIDO`, sem chamar o processor.

#### Scenario: Upload repassado com o usuário
- **WHEN** o operador autenticado envia o `ms260928.txt` com `fonte` = `ANBIMA`
- **THEN** o bff chama `POST /api/v1/cargas/anbima/upload` do processor com `X-Usuario` = o usuário e o `X-Correlation-Id`, sem `Authorization`, e devolve a resposta do processor

#### Scenario: Usuário sem perfil
- **WHEN** um usuário sem o perfil de operação chama a rota
- **THEN** a resposta é 403, e o processor não é chamado

### Requirement: Tela de carga manual de arquivo
O front SHALL ter a tela "Carga manual de arquivo", visível só ao perfil de operação, com: a escolha da fonte ("B3 – Taxas de swap", "ANBIMA – Mercado secundário (NTN-B)", "Bloomberg – SOFR"), a escolha do arquivo e o botão "Enviar". Todos os textos SHALL estar em pt-BR. Enquanto envia, o botão fica desabilitado. Com sucesso, a tela SHALL mostrar a data-base (`dd/mm/aaaa`), o identificador da carga, a origem, o usuário e os vértices gravados por código. Com erro, SHALL mostrar a mensagem do processor e o código do erro, sem detalhes técnicos. A tela SHALL avisar, antes do envio, que um arquivo da mesma data substitui os dados brutos já gravados daquela fonte.

#### Scenario: Envio com sucesso
- **WHEN** o operador envia o `TaxaSwap.txt` de `2026-09-14` com a fonte B3
- **THEN** a tela mostra "Data-base: 14/09/2026", o identificador da carga e os vértices por código

#### Scenario: Arquivo rejeitado
- **WHEN** o processor responde 422 `ARQUIVO_INVALIDO`
- **THEN** a tela mostra a mensagem em pt-BR e o código `ARQUIVO_INVALIDO`, e o operador pode escolher outro arquivo
