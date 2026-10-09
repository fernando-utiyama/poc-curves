package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.DadoVertcCurvaRepositoryPort;
import br.com.poc.application.port.out.EnginePort;
import br.com.poc.application.port.out.EnginePort.RespostaEngine;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.evento.EventoCadastroAlterado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ações da curva numa data-base. A curva é identificada pelo nome (PK). As que vão ao engine usam o código da
 * curva (o engine a acha por ele): curva sem código não pode ser construída nem consultada no engine.
 * Apagar a construção da data é feito aqui mesmo, nas tabelas do engine (só apagar, nunca gravar).
 */
@Service
@RequiredArgsConstructor
public class CurvaMercadoAcoesService {

    private final CurvaMercdRepositoryPort curvas;
    private final EnginePort engine;
    private final DadoVertcCurvaRepositoryPort dadoVertcCurvaRepositoryPort;
    private final EventosPort eventosPort;

    public RespostaEngine construir(String nome, LocalDate dataBase, Boolean forcar, String fonte, String produto, String cid) {
        String codigo = codigoDaCurva(nome);
        if ((fonte == null) != (produto == null)) {
            throw parametroInvalido("Informe fonte e produto juntos.");
        }
        return engine.construir(codigo, dataBase, forcar, fonte, produto, cid);
    }

    public RespostaEngine regravarInterpolada(String nome, LocalDate dataBase, String cid) {
        return engine.regravarInterpolada(codigoDaCurva(nome), dataBase, cid);
    }

    public RespostaEngine consultarVertices(String nome, LocalDate dataBase, String cid) {
        return engine.consultarVertices(codigoDaCurva(nome), dataBase, cid);
    }

    public RespostaEngine interpolar(String nome, LocalDate dataBase, String queryString, String cid) {
        String codigo = codigoDaCurva(nome);
        if (queryString == null || !(queryString.contains("du=") || queryString.contains("data="))) {
            throw parametroInvalido("Informe ao menos um du ou uma data.");
        }
        return engine.interpolar(codigo, dataBase, queryString, cid);
    }

    /** Apaga os vértices construídos e a interpolada da data, numa transação. A data volta a ficar não construída. */
    @Transactional
    public void apagarConstrucao(String nome, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nome);

        if (!dadoVertcCurvaRepositoryPort.apagar(curva.nome(), dataBase)) {
            throw new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Curva " + nome + " não construída na data " + dataBase);
        }

        eventosPort.publicarCadastroAlterado(new EventoCadastroAlterado(
            UUID.randomUUID(),
            curva.codigo(),
            curva.nome(),
            "CONSTRUCAO",
            "EXCLUSAO",
            OffsetDateTime.now(),
            null,
            dataBase,
            null
        ));
    }

    private CurvaMercado obterCurva(String nome) {
        return curvas.findByNome(nome)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nome + " não encontrada"));
    }

    /** Curva inexistente = 404; curva sem código = 422 (cadastro legado que o engine não enxerga). */
    private String codigoDaCurva(String nome) {
        CurvaMercado curva = obterCurva(nome);
        if (curva.codigo() == null || curva.codigo().isBlank()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, new Object[]{
                new Detalhe("nome", null, nome, "Curva sem código não pode ser construída nem consultada no engine; cadastre o código")
            });
        }
        return curva.codigo();
    }

    private RuntimeException parametroInvalido(String msg) {
        return new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO, msg);
    }
}
