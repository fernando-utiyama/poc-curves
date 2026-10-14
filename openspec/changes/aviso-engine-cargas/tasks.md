> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e a spec; não releia): leia só a seção do guia ([`implementacao.md`](implementacao.md)) citada na tarefa; abra só os arquivos que ela manda alterar; não pare para perguntar (deixe `// TODO(revisao): <dúvida>`); termine cada tarefa no "Pronto quando".

## 1. Processor (base comum do aviso)

- [ ] 1.1 [básico] (guia §1) Conferir o `AvisoEngineService`/`AvisoEngineAdapter` da `processor-v0` contra a spec: corpo, cabeçalho `X-Correlation-Id`, sem `Authorization`, repetição (erro de rede, 5xx e 409), `AVISO_ATRASADO`, `CARGA_FALHOU` e contador por fonte; manter, alterar ou criar. Pronto quando `mvn -q compile` passa.
- [ ] 1.2 [básico] (guia §2) Conferir que cada provedor (B3, ANBIMA, Bloomberg) monta `verticesPorCurva` com o que foi **gravado** (inclusive o que o modelo descarta), só para códigos com curva ligada; carga sem código gravado não avisa. Pronto quando `mvn -q compile` passa.
- [ ] 1.3 [forte] (guia §3) `AvisoEngineServiceTest`: sucesso, 5xx até 2xx, 409 repetido, 400 sem repetir, janela esgotada (`CARGA_FALHOU` e contador), falha de uma fonte sem atrasar outra. Pronto quando passa.

## 2. Engine (conferência da quantidade)

- [ ] 2.1 [forte] (guia §4) No tratamento da carga, comparar as linhas lidas da tabela bruta (curva e data-base) com a quantidade avisada do código; diferente = `INSUMO_INVALIDO` com "avisadas N, lidas M", sem construir e sem impedir as outras curvas. Pronto quando `mvn -q compile` passa.
- [ ] 2.2 [forte] (guia §4) Teste do webhook: quantidade igual (constrói) e diferente (falha só aquela curva). Pronto quando passa.

## 3. Fechamento

- [ ] 3.1 [forte] `mvn -q test` no processor e no engine; resumo no fim deste arquivo (o que foi mantido, alterado, criado e os `TODO(revisao)`).
