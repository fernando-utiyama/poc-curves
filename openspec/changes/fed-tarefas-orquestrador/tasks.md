> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e a spec; não releia):
> 1. Primeiro, ache a tela de tarefas que já existe (o componente com a lista, o CRUD e o botão de executar) e o serviço que ela usa; abra só esses arquivos e o que eles importam.
> 2. Ajuste no lugar, com o menor diff, seguindo o estilo do que existe (design D1). Não reescreva nem crie outra tela.
> 3. Não pare para perguntar. Se algo não encaixar, deixe `// TODO(revisao): <dúvida>` e siga.

## 1. Execução com data

- [ ] 1.1 No serviço de tarefas, a execução com `dataBase` (opcional, `AAAA-MM-DD`) e `incluirDownload`, sem `Authorization`, com tempo limite de 130 s (design D2 e D3); verificar com o build do projeto
- [ ] 1.2 Na execução das tarefas de download, o campo de data opcional e a opção "Baixar de novo da fonte" (só com data), com o botão desabilitado enquanto executa; verificar no navegador os cenários "Sem data" e "Com data passada"
- [ ] 1.3 O resultado na tela (situação em pt-BR, data-base `dd/mm/aaaa`, `idCarga`, detalhe) e o 409/400 na própria tela; verificar no navegador "Arquivo ainda não recebido" e "Já em execução"

## 2. Alertas

- [ ] 2.1 Consulta `GET /api/v1/alertas` do dia e o quadro de alertas onde couber na tela que existe, com "Nenhum alerta hoje" e falha silenciosa sem a rota; verificar no navegador com e sem alertas

## 3. Fechamento

- [ ] 3.1 Build do projeto e `openspec validate fed-tarefas-orquestrador --strict`; conferir que o CRUD de tarefas continua igual
