# Conferência dos endpoints já desenvolvidos (engine e curves)

Objetivo: saber o que já existe no repositório real antes de aplicar `engine-construcao-curvas` e `curves-cadastro-curvas`, para marcar cada funcionalidade como pronta, alterar ou criar.

## O que mandar (uma foto ou print por item, em cada repositório)

1. **Rotas:** rode na raiz do serviço (PowerShell) e fotografe a saída:

   ```powershell
   Get-ChildItem src\main\java -Recurse -Filter *.java |
     Select-String -Pattern '@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)' |
     ForEach-Object { "{0}:{1}  {2}" -f $_.Filename, $_.LineNumber, $_.Line.Trim() }
   ```

2. **Classes:** a lista de arquivos (mostra serviços, portas e nomes antigos com `Ponto`/`Linha`):

   ```powershell
   Get-ChildItem src\main\java -Recurse -Filter *.java -Name
   ```

3. **Configuração:** as chaves de primeiro nível do `application.yml` (sem valores de segredo), só para ver se já há `curves.engine.url` e afins.

Se for mais fácil, o Swagger aberto (`/swagger-ui.html`) com todos os grupos expandidos substitui o item 1.

## O que vou comparar

### engine (`/api/v1`)

| Rota esperada | Tarefa |
|---|---|
| `POST /cargas` | 8.1 |
| `POST /construcoes/{dataBase}` | 8.2 |
| `GET /curvas?nome=` | 9.1 |
| `GET /curvas/situacao?dataBase=` | 8.3 |
| `GET /valores-cadastro` | 9.2 |
| `POST /curvas/{codigo}/{dataBase}/construcao` | 8.2 |
| `GET /curvas/{codigo}/{dataBase}` | 7.1 |
| `GET /curvas/{codigo}/{dataBase}/interpolacao` | 7.1 |
| `POST /curvas/{codigo}/{dataBase}/interpolada` | 7.2 |
| `GET /curvas/por-nome/{dataBase}` e `.../interpolacao` | 9.1 |
| `GET /calendarios/{nome}` | 9.2 |
| `GET /curvas/{codigo}/{dataBase}/pontos` (nova, change `fed-curvas-mercado`) | — |

### curves (`/api/v1`)

| Rota esperada | Tarefa |
|---|---|
| `GET/POST /curvas-mercado`, `GET/PUT /curvas-mercado/{codigo}` | 2.1, 2.2 |
| `POST /curvas-mercado/{codigo}/inativacao` e `/reativacao` | 2.1 |
| `GET /curvas-mercado/{codigo}/auditoria` | 1.2 |
| `GET/POST /curvas-mercado/{codigo}/provedores`, `PUT/DELETE .../{idCurvaProvedor}`, `GET /curvas-mercado/provedores` | 3.1 |
| `GET/POST /curvas-mercado/{codigo}/configuracoes`, `.../vigente`, `.../validacao`, `DELETE .../{versao}` | 4.1 |
| `GET /curvas-mercado/valores` | 4.2 |
| CRUD dos brutos B3, ANBIMA e Bloomberg (rotas atuais, quaisquer que sejam) e `/dados-mercado/{provedor}...` | 5.1 |
| `POST .../{codigo}/{dataBase}/construcao`, `/interpolada`, `GET .../vertices`, `/pontos`, `/interpolacao` | 6.1 |

Com isso devolvo a tabela marcada (existe / existe com outro nome / falta) e ajusto as tarefas.
