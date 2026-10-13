# Curves v1.2

O que **ainda falta aplicar** está em duas pastas, nesta ordem (a `15` só depois dos ALTERs `002` e `004` no banco):

| Pasta | O que é |
|---|---|
| `14-dado-bruto/` | dado bruto das 3 fontes (B3, ANBIMA e Bloomberg), completo: persistência, services, use cases, controllers (rotas por nome, sem avisos), DTOs e testes; guia em `14-dado-bruto/LEIA-ME.md` |
| `15-dado-bruto-alter/` | o mesmo dado bruto com os ALTERs: id `bigint IDENTITY` nas 3 fontes (`004`) e título e vencimento na ANBIMA (`002`); guia em `15-dado-bruto-alter/LEIA-ME.md` |

`aplicado/` guarda o que você já fez (excluir curva, excluir vértices, excluir versão, identificador pelo nome, sem canônico, porta do construído, sem avisos, limpeza do cadastro em `aplicado/13-limpeza/`), só como referência. Não precisa copiar de novo.

O backlog da versão está em `docs/backlog-curves-v1-2.md`.
