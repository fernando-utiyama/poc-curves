package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.CurvaMercadoAcoesUseCase;
import br.com.poc.application.port.in.usecase.CurvaMercadoEngineResultado;
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

/** Ações da curva numa data-base. Curva sem código não vai ao engine (422); apagar a construção só apaga. */
@Service
@RequiredArgsConstructor
public class CurvaMercadoAcoesService implements CurvaMercadoAcoesUseCase {

    private final CurvaMercdRepositoryPort curvas;
    private final EnginePort engine;
    private final DadoVertcCurvaRepositoryPort dadoVertcCurvaRepositoryPort;
    private final EventosPort eventosPort;

    @Override
    public CurvaMercadoEngineResultado construir(String nome, LocalDate dataBase, Boolean forcar, String fonte, String produto, String cid) {
        String codigo = codigoDaCurva(nome);
        if ((fonte == null) != (produto == null)) {
            throw parametroInvalido("Informe fonte e produto juntos.");
        }
        RespostaEngine r = engine.construir(codigo, dataBase, forcar, fonte, produto, cid);
        return new CurvaMercadoEngineResultado(r.status(), r.corpoJson());
    }

    @Override
    public CurvaMercadoEngineResultado regravarInterpolada(String nome, LocalDate dataBase, String cid) {
        RespostaEngine r = engine.regravarInterpolada(codigoDaCurva(nome), dataBase, cid);
        return new CurvaMercadoEngineResultado(r.status(), r.corpoJson());
    }

    @Override
    public CurvaMercadoEngineResultado consultarVertices(String nome, LocalDate dataBase, String cid) {
        RespostaEngine r = engine.consultarVertices(codigoDaCurva(nome), dataBase, cid);
        return new CurvaMercadoEngineResultado(r.status(), r.corpoJson());
    }

    @Override
    public CurvaMercadoEngineResultado interpolar(String nome, LocalDate dataBase, String queryString, String cid) {
        String codigo = codigoDaCurva(nome);
        if (queryString == null || !(queryString.contains("du=") || queryString.contains("data="))) {
            throw parametroInvalido("Informe ao menos um du ou uma data.");
        }
        RespostaEngine r = engine.interpolar(codigo, dataBase, queryString, cid);
        return new CurvaMercadoEngineResultado(r.status(), r.corpoJson());
    }

    /** Apaga os vértices construídos e a interpolada da data, numa transação. A data volta a ficar não construída. */
    @Override
    @Transactional
    public void excluirVertices(String nome, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nome);

        if (!dadoVertcCurvaRepositoryPort.excluirPorNomeCurvaEDataBase(curva.nome(), dataBase)) {
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
