## 1. Esqueleto do serviço

- [ ] 1.1 Criar o módulo Maven `services/curve-bff` com Java 21, Spring Boot 3.4.x, sem Lombok
- [ ] 1.2 Configurar os clientes HTTP de `curve-api`, `curve-engine` e `curve-orchestrator` a partir dos contratos OpenAPI
- [ ] 1.3 Configurar timeouts individuais por dependência, menores que o timeout total da requisição
- [ ] 1.4 Escrever o `Containerfile` e adicionar o serviço ao compose Podman com healthcheck
- [ ] 1.5 Adicionar o teste de fronteira que falha o build se o serviço declarar driver de banco ou cliente Kafka

## 2. Segurança

- [ ] 2.1 Configurar a validação de token OIDC contra o Keycloak local e, por configuração, contra o IdP corporativo
- [ ] 2.2 Implementar a resolução de identidade e de perfis a partir do token
- [ ] 2.3 Implementar a autorização por operação para os três perfis
- [ ] 2.4 Implementar a indicação, na resposta, de quais ações da tela o usuário não pode executar
- [ ] 2.5 Implementar a credencial de serviço nas chamadas internas, propagando a identidade do usuário como contexto
- [ ] 2.6 Implementar a resposta padronizada de não autorizado para token ausente, expirado ou inválido
- [ ] 2.7 Configurar restrição de origem, limite de tamanho de payload e limitação de taxa por usuário
- [ ] 2.8 Implementar a geração e a propagação do `correlacao_id`

## 3. Contrato

- [ ] 3.1 Escrever `contracts/openapi/curve-bff.yaml` com os recursos por tela
- [ ] 3.2 Declarar no contrato que valores de mercado trafegam como texto numérico
- [ ] 3.3 Declarar no contrato o formato uniforme de paginação, filtro e ordenação
- [ ] 3.4 Declarar no contrato o formato de seção indisponível para degradação parcial
- [ ] 3.5 Implementar o teste de conformidade entre implementação e contrato

## 4. Agregação

- [ ] 4.1 Implementar a execução em paralelo das chamadas às dependências
- [ ] 4.2 Implementar a degradação parcial com seção marcada como indisponível e motivo
- [ ] 4.3 Distinguir seção indisponível de seção legitimamente vazia
- [ ] 4.4 Registrar o tempo de cada origem na resposta agregada
- [ ] 4.5 Implementar a tradução de erro preservando o código de origem

## 5. Telas de consulta

- [ ] 5.1 Implementar o recurso da tela de catálogo
- [ ] 5.2 Implementar o recurso da tela de curva, agregando versão, vértices, procedência, modelo e última execução
- [ ] 5.3 Implementar o recurso de interpolação com um ou vários prazos, na ordem solicitada
- [ ] 5.4 Isolar prazo problemático sem invalidar os demais resultados do lote
- [ ] 5.5 Implementar o recurso da tela de comparação: construída contra importada
- [ ] 5.6 Implementar a comparação entre dois modelos para a mesma curva e data
- [ ] 5.7 Implementar o recurso da tela de execuções com filtros e paginação
- [ ] 5.7.1 Implementar o recurso do painel do dia, com estado, horário limite, tempo restante ou margem e etapa atual por curva
- [ ] 5.7.2 Refletir no painel os estados de curva reprovada na validação e publicada com aviso
- [ ] 5.7.3 Distinguir curva não iniciada de curva com falha
- [ ] 5.8 Implementar o recurso leve de alerta de pendências: grupos abertos, total de mensagens, idade da mais antiga e severidade
- [ ] 5.9 Implementar a listagem de grupos de pendência, ordenada pela falha mais antiga
- [ ] 5.10 Implementar o detalhamento das pendências de um grupo, com `id_evento` e `correlacao_id`

## 6. Telas de ação

- [ ] 6.1 Implementar o disparo manual de ingestão aceitando conjuntos de dado individual e o de curva pronta
- [ ] 6.2 Suportar o disparo dos dois tipos em uma única ação, com acompanhamento por conjunto
- [ ] 6.3 Retornar o `correlacao_id`, ou o da execução já em andamento quando houver
- [ ] 6.4 Informar antecipadamente quando a data não é dia de pregão
- [ ] 6.5 Implementar o backfill de janela de datas, restrito a operador ou administrador
- [ ] 6.6 Implementar o cadastro e a edição de definição de curva, restritos a administrador
- [ ] 6.7 Implementar a gestão de agendamentos, restrita a administrador
- [ ] 6.8 Implementar a importação de modelo Groovy e a troca de modelo de uma curva, restritas a administrador
- [ ] 6.1.1 Encaminhar todo disparo manual na faixa prioritária, inclusive o redisparo de execução travada
- [ ] 6.1.2 Implementar a carga manual de curva: recebe arquivo, valida tipo e tamanho e encaminha sem parsear
- [ ] 6.1.3 Devolver a lista completa de erros de leitura em formato exibível
- [ ] 6.1.4 Devolver os testes reprovados quando a curva carregada não passa no gate
- [ ] 6.1.5 Implementar o download do modelo de carga em CSV e em planilha
- [ ] 6.9 Implementar as ações de reprocessar pendência e reprocessar grupo, restritas a operador ou administrador
- [ ] 6.10 Implementar o descarte de pendência com justificativa obrigatória
- [ ] 6.11 Traduzir a recusa por obsolescência em mensagem exibível, refletindo o novo estado

## 7. Precisão e formato

- [ ] 7.1 Garantir que valores de mercado atravessem o BFF como texto, sem conversão nem formatação
- [ ] 7.2 Adicionar o teste que falha se o BFF fizer aritmética ou arredondamento com valor de mercado
- [ ] 7.3 Implementar paginação uniforme com limite máximo de página aplicado e sinalizado

## 8. Testes

- [ ] 8.1 Testar rejeição de requisição sem token, com token expirado e com assinatura inválida
- [ ] 8.2 Testar a autorização por operação nos três perfis, incluindo tela visível com ação restrita
- [ ] 8.3 Testar que operação não autorizada não chega a chamar a dependência
- [ ] 8.4 Testar a agregação da tela de curva em uma única chamada
- [ ] 8.5 Testar a degradação parcial com o motor indisponível
- [ ] 8.6 Testar que seção indisponível é distinguível de seção vazia
- [ ] 8.7 Testar o disparo manual para os dois tipos de insumo, isolados e em conjunto
- [ ] 8.8 Testar o disparo em data com execução já em andamento e em data que não é pregão
- [ ] 8.9 Testar a preservação de dígitos de ponta a ponta
- [ ] 8.10 Testar a tradução de erro preservando o código de origem
- [ ] 8.11 Testar o contrato OpenAPI contra a implementação
- [ ] 8.12 Testar a fronteira: nenhum acesso a banco ou Kafka
- [ ] 8.13 Testar o recurso de alerta com pendências abertas e com nenhuma pendência
- [ ] 8.14 Testar que o recurso de alerta não carrega a lista de pendências
- [ ] 8.15 Testar que o alerta é consultável por perfil de leitor
- [ ] 8.16 Testar que reprocessar e descartar são recusados para leitor, sem chamar o orquestrador
- [ ] 8.17 Testar que a contagem do alerta diminui após reprocessamento bem-sucedido
- [ ] 8.18 Testar a recusa de descarte sem justificativa
- [ ] 8.19 Testar o painel do dia com curvas publicada, em andamento, em risco, atrasada, reprovada e não iniciada
- [ ] 8.20 Testar que o alerta reflete curva em risco sem existir falha nem pendência
- [ ] 8.21 Testar que o redisparo de ingestão travada usa a faixa prioritária e retorna novo `correlacao_id`
- [ ] 8.22 Testar a carga manual aceita, recusada por perfil e recusada sem justificativa
- [ ] 8.23 Testar que o BFF não parseia o arquivo, apenas valida tipo e tamanho
- [ ] 8.24 Testar o download do modelo nos dois formatos

## 9. Integração

- [ ] 9.1 Rodar o BFF contra os serviços e o Keycloak locais, autenticando com cada um dos três perfis
- [ ] 9.2 Abrir a tela de curva de uma data real e conferir todas as seções preenchidas
- [ ] 9.3 Disparar as duas ingestões pela API do BFF e acompanhar pelo `correlacao_id`
- [ ] 9.4 Documentar em `services/curve-bff/README.md` os perfis, os recursos por tela e o comportamento de degradação
