# Ambiente local

O ambiente sobe inteiro em Podman rootless — sem Docker, sem Azure. Kafka em
modo KRaft, SQL Server 2022, e, no perfil completo, Redis e Keycloak.

## Pré-requisitos

- **Podman >= 5.0** com `podman compose` disponível (via `podman-compose` ou um
  provider externo como o plugin Compose do Docker — qualquer um funciona
  contra o socket do Podman).
- **Windows/macOS**: uma máquina Podman rodando (`podman machine init && podman machine start`).
  No backend WSL (padrão no Windows), a memória é dinâmica — não é fixada por
  `podman machine set --memory`, e sim pelo teto padrão do WSL ou por um
  `.wslconfig` do usuário. Recomendado: **6 GiB disponíveis** para a VM no
  perfil completo (SQL Server sozinho consome ~2 GiB).
- **Linux nativo**: socket do usuário habilitado (`systemctl --user enable --now podman.socket`).
- Rode `scripts/doctor.sh` antes da primeira subida — ele verifica tudo isto e
  aponta exatamente o que falta.

## Portas usadas

| Serviço | Porta host | Uso |
|---|---|---|
| SQL Server | `1433` | conexão JDBC / `sqlcmd` |
| Kafka (listener externo) | `19092` | acesso da máquina host e de testes de integração |
| Redis (perfil completo) | `6379` | cache de interpolação |
| Keycloak (perfil completo) | `8180` | console/admin e endpoints OIDC (mapeia a porta 8080 do container) |
| Keycloak — management (perfil completo) | `9000` | `/health/ready`, `/health/live` |

Nenhuma porta é privilegiada (todas acima de 1024) — requisito do modo
rootless, sem `sudo` e sem `--rootful`.

## Subir e derrubar

```bash
# Perfil completo: Kafka + SQL Server + Redis + Keycloak
deploy/podman/up.sh
deploy/podman/down.sh          # preserva volumes
deploy/podman/down.sh -v       # remove volumes — ambiente descartável

# Perfil reduzido: só Kafka + SQL Server (máquinas com pouca memória)
deploy/podman/up.sh --lite
deploy/podman/down.sh --lite -v
```

No Windows, os mesmos comandos existem como `.ps1`:
`deploy/podman/up.ps1 [-Lite]`, `deploy/podman/down.ps1 [-Lite] [-Volumes]`.

`up.sh`/`up.ps1` não confiam em `depends_on` com `condition: service_healthy`
— o suporte varia entre implementações de `podman compose`. Em vez disso,
fazem espera ativa por prontidão real (query de sanidade no SQL Server,
`kafka-broker-api-versions` no Kafka, `PING` no Redis, `/health/ready` no
Keycloak) antes de seguir para a etapa seguinte. Se qualquer etapa de
inicialização (criação do banco, migração Flyway, bootstrap de tópicos) falhar,
a subida para com erro — nenhum serviço de aplicação deve iniciar contra um
banco ou um catálogo de tópicos em estado inconsistente.

## O que cada subida faz, em ordem

1. Sobe `kafka` e `sqlserver` (e `redis`/`keycloak` no perfil completo) em
   background.
2. Espera cada um responder de verdade — não apenas "container rodando".
3. Cria o banco `curvasdb` se ainda não existir (`sqlserver-init-db`).
4. Aplica as migrações Flyway (`db/migration/V1..V7`) contra `curvasdb`.
5. Cria os tópicos do catálogo (`contracts/events/topics.yaml`) no Kafka —
   o broker sobe com `auto.create.topics.enable=false`, então nenhum tópico
   existe até este passo rodar.

Rodar `up.sh` de novo com o ambiente já no ar é seguro: criação do banco é
condicional, Flyway não reaplica migração já registrada, e a criação de
tópicos usa `--if-not-exists`.

## Senhas e variáveis de ambiente

`MSSQL_SA_PASSWORD` (padrão `CurvasP0c!Local`), `KEYCLOAK_ADMIN` e
`KEYCLOAK_ADMIN_PASSWORD` (padrão `admin`/`admin`) podem ser sobrescritas por
variável de ambiente antes de rodar `up.sh`, ou por um arquivo
`deploy/podman/.env` (git-ignorado). São credenciais de ambiente local
descartável — nunca as reaproveite fora daqui.

## `DOCKER_HOST` para Testcontainers

`scripts/doctor.sh` imprime o socket ativo e a orientação certa para a
plataforma:

- **Linux nativo**: `export DOCKER_HOST=unix:///run/user/<uid>/podman/podman.sock`
- **Windows/macOS (máquina Podman)**: `podman machine start` já expõe um
  socket compatível com a API do Docker via *API forwarding*; a maioria dos
  clientes (incluindo Testcontainers) detecta sozinho. Se não detectar, defina
  `DOCKER_HOST` explicitamente com o socket que o doctor imprimiu. Em modo
  rootless, se o Ryuk (reaper de containers de teste do Testcontainers) falhar
  ao subir, defina `TESTCONTAINERS_RYUK_DISABLED=true`.

## Diagnóstico

```bash
scripts/doctor.sh
```

Verifica: versão mínima do Podman, máquina Podman ativa e com memória
suficiente (medida de dentro da VM, não o campo estático de criação — no
backend WSL esse campo não reflete o teto real), portas do ambiente livres, e
o socket usado para Testcontainers. Sai com código 1 se algo estiver
bloqueante; avisos (memória abaixo do recomendado, porta já em uso por uma
subida anterior) não bloqueiam.
