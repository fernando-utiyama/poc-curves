-- ============================================================================
-- V16__curve_orchestrator_agendamento.sql
-- Criacao da tabela de agendamentos (tarefa 6.1 do backlog curve-orchestrator)
-- e vinculo de execucao_curva ao agendamento que a disparou (tarefa 6.8,
-- consulta de agendamentos com ultima execucao e resultado).
-- ============================================================================

CREATE TABLE agendamento_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    definicao_curva_id UNIQUEIDENTIFIER NULL,
    conjunto_dados NVARCHAR(50) NULL,
    momento_curva NVARCHAR(20) NOT NULL,
    faixa NVARCHAR(20) NOT NULL,
    expressao_horario NVARCHAR(100) NOT NULL,
    fuso_horario NVARCHAR(50) NOT NULL,
    janela_tentativa_minutos INT NOT NULL,
    intervalo_tentativa_segundos INT NOT NULL,
    ativo BIT NOT NULL DEFAULT 1,
    criado_por NVARCHAR(100) NOT NULL,
    criado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    atualizado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_agendamento_curva PRIMARY KEY (id),
    CONSTRAINT fk_agendamento_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT ck_agendamento_curva_momento_curva CHECK (momento_curva IN ('ABERTURA', 'INTRADIA', 'FECHAMENTO')),
    CONSTRAINT ck_agendamento_curva_faixa CHECK (faixa IN ('ROTINA', 'PRIORITARIA', 'MASSA')),
    CONSTRAINT ck_agendamento_curva_janela_positiva CHECK (janela_tentativa_minutos > 0),
    CONSTRAINT ck_agendamento_curva_intervalo_positivo CHECK (intervalo_tentativa_segundos > 0)
);

-- Mesma dualidade de alvo ja existente em execucao_curva (definicao_curva_id vs.
-- conjunto_dados) -- ver V3__execucao_curva.sql, que tambem nao impoe XOR a nivel
-- de banco, validacao fica na camada Java (AgendamentoService).

ALTER TABLE execucao_curva ADD agendamento_id UNIQUEIDENTIFIER NULL;
ALTER TABLE execucao_curva ADD CONSTRAINT fk_execucao_curva_agendamento
    FOREIGN KEY (agendamento_id) REFERENCES agendamento_curva (id);

-- Fronteira de escrita do curve-orchestrator (tarefa 1.2) -- GRANT previsto desde
-- V13__curve_orchestrator_credencial_restrita.sql, aplicado agora que a tabela existe.
GRANT SELECT, INSERT, UPDATE ON agendamento_curva TO curve_orchestrator_app;
