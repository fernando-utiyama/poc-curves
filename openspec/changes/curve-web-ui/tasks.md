## 1. Esqueleto do app

- [ ] 1.1 Criar `web/curve-web-ui` com Angular 18+, TypeScript estrito, lint e formatação
- [ ] 1.2 Gerar tipos e cliente HTTP a partir de `contracts/openapi/curve-bff.yaml`
- [ ] 1.3 Configurar a rota base como parâmetro de configuração, sem suposição sobre a rota raiz
- [ ] 1.4 Definir o tema por tokens substituíveis, sem cor ou tipografia fixada em componente
- [ ] 1.5 Implementar a fonte única e trocável de contexto de usuário
- [ ] 1.6 Escrever o `Containerfile` servindo o artefato estático e adicionar o app ao compose Podman
- [ ] 1.7 Injetar em tempo de execução a URL do BFF e a configuração de identidade, sem fixar no build

## 2. Autenticação

- [ ] 2.1 Implementar o fluxo OIDC contra o Keycloak local
- [ ] 2.2 Anexar o token a todas as chamadas ao BFF
- [ ] 2.3 Retornar à rota pretendida após o login
- [ ] 2.4 Implementar o tratamento de sessão expirada com reautenticação e retomada
- [ ] 2.5 Preservar o conteúdo de formulário em preenchimento durante a reautenticação
- [ ] 2.6 Implementar a resolução de perfis para habilitar e desabilitar ações

## 3. Fundações de tela

- [ ] 3.1 Implementar o tratamento uniforme de carregamento, erro e degradação parcial
- [ ] 3.2 Distinguir visualmente seção indisponível de seção legitimamente vazia, com motivo e ação de repetir
- [ ] 3.3 Implementar a exibição de ação desabilitada com o motivo, para operação sem permissão
- [ ] 3.4 Implementar o tipo `string` para valores de mercado e a formatação apenas para exibição
- [ ] 3.5 Adicionar a regra de lint que falha o build em conversão numérica de campo de valor de mercado
- [ ] 3.6 Implementar a tabela virtualizada com tipografia tabular e alinhamento decimal
- [ ] 3.7 Implementar paginação, filtro e ordenação alinhados ao contrato do BFF

## 4. Painel do dia

- [ ] 4.1 Implementar o painel do dia como tela inicial, com uma linha por curva ativa
- [ ] 4.2 Exibir estado, horário limite, tempo restante ou margem e etapa atual
- [ ] 4.3 Destacar curva em risco antes de qualquer falha, e curva atrasada de forma distinta de falha
- [ ] 4.4 Exibir curva reprovada na validação com acesso aos testes que falharam e à versão vigente
- [ ] 4.5 Exibir curva publicada com aviso sem sugerir falha
- [ ] 4.6 Exibir curva não iniciada com o horário previsto
- [ ] 4.7 Oferecer o redisparo a partir do painel, sujeito ao perfil

## 5. Catálogo e cadastro

- [ ] 5.1 Implementar a tela de catálogo com filtro, paginação e modo de origem visível
- [ ] 5.2 Implementar a navegação do catálogo para a tela de curva
- [ ] 5.3 Implementar o formulário de cadastro e edição com todos os campos da definição
- [ ] 5.4 Exibir o aviso de que salvar cria uma nova versão da definição
- [ ] 5.5 Implementar a escolha do modelo de construção, entre embutido e Groovy importado
- [ ] 5.6 Desabilitar o campo de modelo para definição importada, com a explicação
- [ ] 5.7 Exibir erro de validação do backend junto ao campo correspondente quando identificável

- [ ] 5.8 Incluir no cadastro o horário limite, o orçamento por etapa, a janela de bloqueio, os limites de validação e a classificação de cada teste

## 6. Viewer de curva

- [ ] 6.1 Implementar o gráfico e a tabela de vértices
- [ ] 6.2 Implementar os seletores de data de referência, momento de curva e versão
- [ ] 6.3 Implementar a seleção por instante, com indicação clara de que não é a versão corrente
- [ ] 6.4 Exibir a procedência junto dos dados: versão, publicação, execução e modelo
- [ ] 6.5 Exibir, para curva importada, o arquivo de origem e o lote, indicando que não houve cálculo
- [ ] 6.6 Informar explicitamente a ausência de curva publicada na data escolhida
- [ ] 6.7 Exibir o resultado da validação de consistência junto da curva, com avisos destacados
- [ ] 6.8 Exibir versão reprovada com os testes que falharam e a indicação de que não foi publicada

## 7. Consulta interpolada

- [ ] 7.1 Implementar a entrada de um prazo ou de uma lista de prazos
- [ ] 7.2 Exibir taxa, fator de desconto e a versão de curva usada
- [ ] 7.3 Sinalizar ponto extrapolado
- [ ] 7.4 Exibir o erro nomeado sob política estrita sem invalidar os demais prazos consultados

## 8. Ingestão e monitoramento

- [ ] 8.1 Implementar a tela de disparo manual com seleção de data e do que consumir
- [ ] 8.2 Permitir marcar os conjuntos de dado individual e o de curva pronta, isolados ou juntos
- [ ] 8.3 Avisar antes do disparo quando a data não é dia de pregão
- [ ] 8.4 Exibir o `correlacao_id` de forma copiável e acompanhar o progresso por conjunto
- [ ] 8.5 Exibir a execução já em andamento em vez de criar outra
- [ ] 8.6 Implementar o backfill com data inicial e final, progresso e interrupção
- [ ] 8.7 Implementar o monitor de execuções com filtros por curva, data, estado e `correlacao_id`
- [ ] 8.8 Dar tratamento visual próprio à ausência de dado, distinto de falha, com o motivo
- [ ] 8.9 Implementar o redisparo a partir do monitor
- [ ] 8.10 Implementar o indicador global de pendência de dead-letter, presente em todas as telas
- [ ] 8.11 Implementar a atualização periódica do indicador, sem ação do usuário
- [ ] 8.12 Ocultar o indicador quando não houver pendência aberta, sem contador zerado permanente
- [ ] 8.13 Destacar visualmente pendência aberta há mais tempo que o limite configurado
- [ ] 8.14 Sinalizar estado desconhecido quando a consulta do alerta falhar, em vez de exibir zero
- [ ] 8.15 Implementar a tela de pendências agrupadas, ordenada pela falha mais antiga
- [ ] 8.16 Implementar a expansão do grupo com `id_evento`, `correlacao_id` e detalhe da falha
- [ ] 8.17 Implementar a navegação da pendência para a execução correspondente
- [ ] 8.18 Implementar as ações de reprocessar pendência e grupo, com o estado refletido sem recarga manual
- [ ] 8.19 Implementar o descarte com justificativa obrigatória bloqueada na tela quando vazia
- [ ] 8.20 Exibir a recusa por obsolescência com a explicação de que sobrescreveria dado mais novo
- [ ] 8.21 Desabilitar as ações de correção para perfil de leitor, com o motivo visível

## 9. Carga manual de curva

- [ ] 9.1 Implementar a tela de carga com seleção de curva, data, momento, arquivo e justificativa
- [ ] 9.2 Oferecer o download do modelo em CSV e em planilha na própria tela
- [ ] 9.3 Bloquear a submissão enquanto a justificativa estiver vazia
- [ ] 9.4 Listar todos os erros de leitura com linha, coluna e motivo, deixando claro que nada foi publicado
- [ ] 9.5 Exibir os testes reprovados quando a curva carregada não passa no gate, indicando a versão vigente
- [ ] 9.6 Desabilitar a submissão para perfil de leitor, com o motivo visível
- [ ] 9.7 Marcar a origem carregada no painel do dia, no viewer, no histórico e na procedência
- [ ] 9.8 Exibir autor e justificativa junto de uma versão carregada

## 10. Modelos e comparação

- [ ] 10.1 Implementar a listagem de modelos com tipo e estado
- [ ] 10.2 Implementar a importação de modelo Groovy, exibindo o resultado da validação
- [ ] 10.3 Exibir o motivo da recusa quando o script não compila ou não produz vértices
- [ ] 10.4 Implementar o apontamento de uma curva para outro modelo, restrito a administrador
- [ ] 10.5 Implementar a comparação construída contra importada, com tabela de diferenças por prazo
- [ ] 10.6 Implementar a comparação modelo contra modelo
- [ ] 10.7 Sinalizar prazos presentes em apenas uma das curvas, sem valor inventado
- [ ] 10.8 Destacar as maiores diferenças sem sugerir que diferença é necessariamente erro

## 11. Testes

- [ ] 11.1 Testar o fluxo de autenticação e o retorno à rota pretendida
- [ ] 11.2 Testar sessão expirada com retomada e preservação de formulário
- [ ] 11.3 Testar a exibição de ação desabilitada por perfil, nos três perfis
- [ ] 11.4 Testar a preservação de dígitos na exibição de valores com muitas casas decimais
- [ ] 11.5 Testar que a regra de lint falha em conversão numérica de valor de mercado
- [ ] 11.6 Testar o desempenho da tabela com centenas de vértices
- [ ] 11.7 Testar degradação parcial e a distinção entre seção indisponível e vazia
- [ ] 11.8 Testar o disparo manual para os dois tipos de insumo, isolados e juntos
- [ ] 11.9 Testar o aviso de data que não é dia de pregão e o caso de execução já em andamento
- [ ] 11.10 Testar a exibição de ausência de dado como estado próprio no monitor
- [ ] 11.11 Testar a comparação com prazos não coincidentes
- [ ] 11.12 Testar que o indicador aparece quando surge pendência e some quando todas são resolvidas
- [ ] 11.13 Testar a resolução parcial: contagem diminui e o indicador permanece
- [ ] 11.14 Testar o destaque de pendência envelhecida
- [ ] 11.15 Testar que falha na consulta do alerta não é exibida como ausência de pendência
- [ ] 11.16 Testar o agrupamento na tela com milhares de mensagens do mesmo motivo
- [ ] 11.17 Testar reprocessamento de grupo, falha em novo reprocessamento e recusa por obsolescência
- [ ] 11.18 Testar o bloqueio de descarte sem justificativa
- [ ] 11.19 Testar as ações desabilitadas para perfil de leitor na tela de pendências
- [ ] 11.20 Testar o painel do dia nos seis estados possíveis de curva
- [ ] 11.21 Testar que curva em risco aparece destacada sem existir falha
- [ ] 11.22 Testar o redisparo pelo painel e a exibição das duas tentativas vinculadas
- [ ] 11.23 Testar a exibição de aviso de validação sem sugerir falha, e de versão reprovada
- [ ] 11.24 Testar que a compilação falha quando o contrato do BFF remove um campo usado
- [ ] 11.25 Testar a fronteira: nenhuma chamada a serviço de domínio
- [ ] 11.26 Testar que rota base configurável e tokens de tema funcionam sem alteração de código

- [ ] 11.27 Testar a carga manual bem-sucedida, com erros por linha e reprovada no gate
- [ ] 11.28 Testar o bloqueio da submissão sem justificativa e para perfil de leitor
- [ ] 11.29 Testar o download do modelo nos dois formatos, com o leiaute da curva
- [ ] 11.30 Testar que a origem carregada aparece no painel, no viewer, no histórico e na procedência

## 12. Integração

- [ ] 12.1 Rodar o app no compose Podman contra o BFF e o Keycloak locais
- [ ] 12.2 Executar o fluxo completo pela tela: disparar a ingestão de uma data, acompanhar a execução e ver a curva publicada
- [ ] 12.3 Comparar na tela a curva construída contra a importada da mesma data
- [ ] 12.4 Importar um modelo Groovy, apontar a curva para ele e comparar contra o modelo embutido
- [ ] 12.5 Provocar uma falha permanente de propósito, ver o alerta aparecer, corrigir a causa, reprocessar pela tela e confirmar que o alerta desaparece sozinho
- [ ] 12.6 Documentar em `web/curve-web-ui/README.md` as telas, a configuração em tempo de execução e o que falta para embarcar no shell
