package br.com.poc.application.service;

import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.BtrsCurvaPrimrUseCase;
import br.com.poc.application.port.out.BtrsCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.BtrsCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;
import br.com.poc.domain.evento.EventoCurvaPrimariaEditada;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static br.com.poc.application.service.RegrasCurvaPrimr.*;

/** Dado bruto da B3. */
@Service
@RequiredArgsConstructor
public class BtrsCurvaPrimrService implements BtrsCurvaPrimrUseCase {

    private static final String FONTE = "B3";

    private final BtrsCurvaPrimrRepositoryPort btrsRepositoryPort;
    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final EventosPort eventosPort;

    @Override
    @Transactional(readOnly = true)
    public List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome) {
        String codigoFiltro = textoOuNulo(codigo);
        String nomeFiltro = textoOuNulo(nome);

        if (de == null && ate == null) {
            return btrsRepositoryPort.listarUltimaDataBase(codigoFiltro, nomeFiltro);
        }
        Periodo periodo = Periodo.de(de, ate);
        return btrsRepositoryPort.listarDatasBase(periodo.de(), periodo.ate(), codigoFiltro, nomeFiltro);
    }

    @Override
    @Transactional(readOnly = true)
    public VerticesPrimrDaData<BtrsCurvaPrimr> consultar(String nomeCurva, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nomeCurva);
        return new VerticesPrimrDaData<>(
            btrsRepositoryPort.existeVerticeConstruido(curva.nome(), dataBase),
            btrsRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase));
    }

    @Override
    @Transactional
    public BtrsCurvaPrimr incluir(String nomeCurva, LocalDate dataBase, BtrsCurvaPrimrInput input) {
        validar(input);
        CurvaMercado curva = obterCurva(nomeCurva);
        int quantidadeAntes = btrsRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        BtrsCurvaPrimr salvo = btrsRepositoryPort.salvar(novoVertice(btrsRepositoryPort.proximoId(), curva, dataBase, input));

        publicarEvento(curva, dataBase, "INCLUSAO", null, salvo, quantidadeAntes, quantidadeAntes + 1);
        return salvo;
    }

    @Override
    @Transactional
    public BtrsCurvaPrimr alterar(String nomeCurva, LocalDate dataBase, Integer id, BtrsCurvaPrimrInput input) {
        validar(input);
        CurvaMercado curva = obterCurva(nomeCurva);
        BtrsCurvaPrimr existente = obterVertice(curva, dataBase, id);
        int quantidade = btrsRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        BtrsCurvaPrimr salvo = btrsRepositoryPort.salvar(novoVertice(id, curva, dataBase, input));

        publicarEvento(curva, dataBase, "ALTERACAO", existente, salvo, quantidade, quantidade);
        return salvo;
    }

    @Override
    @Transactional
    public void excluir(String nomeCurva, LocalDate dataBase, Integer id) {
        CurvaMercado curva = obterCurva(nomeCurva);
        BtrsCurvaPrimr existente = obterVertice(curva, dataBase, id);
        int quantidadeAntes = btrsRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        btrsRepositoryPort.excluir(id, curva.nome(), dataBase);

        publicarEvento(curva, dataBase, "EXCLUSAO", existente, null, quantidadeAntes, quantidadeAntes - 1);
    }

    @Override
    @Transactional
    public void excluirData(String nomeCurva, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nomeCurva);
        List<BtrsCurvaPrimr> antes = btrsRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase);

        btrsRepositoryPort.excluirPorNomeCurvaEDataBase(curva.nome(), dataBase);

        publicarEvento(curva, dataBase, "EXCLUSAO_DATA", antes, null, antes.size(), 0);
    }

    private CurvaMercado obterCurva(String nomeCurva) {
        return curvaRepositoryPort.findByNome(nomeCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nomeCurva + " não encontrada"));
    }

    private BtrsCurvaPrimr obterVertice(CurvaMercado curva, LocalDate dataBase, Integer id) {
        return btrsRepositoryPort.findByIdAndNomeCurvaAndDataBase(id, curva.nome(), dataBase)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Vértice " + id + " não encontrado na curva " + curva.nome() + " em " + dataBase));
    }

    private static BtrsCurvaPrimr novoVertice(Integer id, CurvaMercado curva, LocalDate dataBase, BtrsCurvaPrimrInput input) {
        return new BtrsCurvaPrimr(id, curva.nome(), dataBase,
            input.diasCorridos(), input.diasUteis(), input.valor(), input.fatorAcumulado(), input.fatorDia());
    }

    private static void validar(BtrsCurvaPrimrInput input) {
        List<Detalhe> erros = new ArrayList<>();
        obrigatorio("diasCorridos", input.diasCorridos(), erros);
        obrigatorio("diasUteis", input.diasUteis(), erros);
        obrigatorio("valor", input.valor(), erros);
        decimal("valor", input.valor(), 16, 12, erros);
        decimal("fatorAcumulado", input.fatorAcumulado(), 12, 16, erros);
        decimal("fatorDia", input.fatorDia(), 12, 16, erros);
        recusarSeHouverErros(erros);
    }

    private void publicarEvento(CurvaMercado curva, LocalDate dataBase, String operacao,
                                Object antes, Object depois, int quantidadeAntes, int quantidadeDepois) {
        eventosPort.publicarCurvaPrimariaEditada(new EventoCurvaPrimariaEditada(
            MDC.get("correlationId"), FONTE, curva.codigo(), curva.nome(), dataBase, operacao,
            antes, depois, quantidadeAntes, quantidadeDepois, OffsetDateTime.now(BRASILIA)));
    }
}
