package br.com.poc.application.service;

import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.BbergCurvaPrimrUseCase;
import br.com.poc.application.port.out.BbergCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import br.com.poc.domain.cadastro.BbergCurvaPrimrInput;
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

/** Dado bruto da Bloomberg. */
@Service
@RequiredArgsConstructor
public class BbergCurvaPrimrService implements BbergCurvaPrimrUseCase {

    private static final String FONTE = "BLOOMBERG";

    private final BbergCurvaPrimrRepositoryPort bbergRepositoryPort;
    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final EventosPort eventosPort;

    @Override
    @Transactional(readOnly = true)
    public List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome) {
        String codigoFiltro = textoOuNulo(codigo);
        String nomeFiltro = textoOuNulo(nome);

        if (de == null && ate == null) {
            return bbergRepositoryPort.listarUltimaDataBase(codigoFiltro, nomeFiltro);
        }
        Periodo periodo = Periodo.de(de, ate);
        return bbergRepositoryPort.listarDatasBase(periodo.de(), periodo.ate(), codigoFiltro, nomeFiltro);
    }

    @Override
    @Transactional(readOnly = true)
    public VerticesPrimrDaData<BbergCurvaPrimr> consultar(String nomeCurva, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nomeCurva);
        return new VerticesPrimrDaData<>(
            bbergRepositoryPort.existeVerticeConstruido(curva.nome(), dataBase),
            bbergRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase));
    }

    @Override
    @Transactional
    public BbergCurvaPrimr incluir(String nomeCurva, LocalDate dataBase, BbergCurvaPrimrInput input) {
        validar(input);
        CurvaMercado curva = obterCurva(nomeCurva);
        int quantidadeAntes = bbergRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        BbergCurvaPrimr salvo = bbergRepositoryPort.salvar(novoVertice(bbergRepositoryPort.proximoId(), curva, dataBase, input));

        publicarEvento(curva, dataBase, "INCLUSAO", null, salvo, quantidadeAntes, quantidadeAntes + 1);
        return salvo;
    }

    @Override
    @Transactional
    public BbergCurvaPrimr alterar(String nomeCurva, LocalDate dataBase, Integer id, BbergCurvaPrimrInput input) {
        validar(input);
        CurvaMercado curva = obterCurva(nomeCurva);
        BbergCurvaPrimr existente = obterVertice(curva, dataBase, id);
        int quantidade = bbergRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        BbergCurvaPrimr salvo = bbergRepositoryPort.salvar(novoVertice(id, curva, dataBase, input));

        publicarEvento(curva, dataBase, "ALTERACAO", existente, salvo, quantidade, quantidade);
        return salvo;
    }

    @Override
    @Transactional
    public void excluir(String nomeCurva, LocalDate dataBase, Integer id) {
        CurvaMercado curva = obterCurva(nomeCurva);
        BbergCurvaPrimr existente = obterVertice(curva, dataBase, id);
        int quantidadeAntes = bbergRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        bbergRepositoryPort.excluir(id, curva.nome(), dataBase);

        publicarEvento(curva, dataBase, "EXCLUSAO", existente, null, quantidadeAntes, quantidadeAntes - 1);
    }

    @Override
    @Transactional
    public void excluirData(String nomeCurva, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nomeCurva);
        List<BbergCurvaPrimr> antes = bbergRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase);

        bbergRepositoryPort.excluirPorNomeCurvaEDataBase(curva.nome(), dataBase);

        publicarEvento(curva, dataBase, "EXCLUSAO_DATA", antes, null, antes.size(), 0);
    }

    private CurvaMercado obterCurva(String nomeCurva) {
        return curvaRepositoryPort.findByNome(nomeCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nomeCurva + " não encontrada"));
    }

    private BbergCurvaPrimr obterVertice(CurvaMercado curva, LocalDate dataBase, Integer id) {
        return bbergRepositoryPort.findByIdAndNomeCurvaAndDataBase(id, curva.nome(), dataBase)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Vértice " + id + " não encontrado na curva " + curva.nome() + " em " + dataBase));
    }

    private static BbergCurvaPrimr novoVertice(Integer id, CurvaMercado curva, LocalDate dataBase, BbergCurvaPrimrInput input) {
        return new BbergCurvaPrimr(id, curva.nome(), dataBase,
            input.precoLiquidacao(), input.precoMedio(), input.precoUltimo(), input.diaVencimento(),
            input.dataLiquidacaoFinanceira(), input.tickerBloomberg().trim(), input.formaLiquidacao(),
            input.dataVencimentoContrato(), input.dataUltimoNegocio());
    }

    private static void validar(BbergCurvaPrimrInput input) {
        List<Detalhe> erros = new ArrayList<>();
        obrigatorio("tickerBloomberg", input.tickerBloomberg(), erros);
        if (input.tickerBloomberg() != null && input.tickerBloomberg().length() > 50) {
            erros.add(new Detalhe("tickerBloomberg", null, input.tickerBloomberg(), "tickerBloomberg deve ter até 50 caracteres"));
        }
        obrigatorio("precoUltimo", input.precoUltimo(), erros);
        decimal("precoUltimo", input.precoUltimo(), 16, 12, erros);
        decimal("precoLiquidacao", input.precoLiquidacao(), 16, 12, erros);
        decimal("precoMedio", input.precoMedio(), 16, 12, erros);
        if (input.formaLiquidacao() != null && input.formaLiquidacao().length() > 20) {
            erros.add(new Detalhe("formaLiquidacao", null, input.formaLiquidacao(), "formaLiquidacao deve ter até 20 caracteres"));
        }
        recusarSeHouverErros(erros);
    }

    private void publicarEvento(CurvaMercado curva, LocalDate dataBase, String operacao,
                                Object antes, Object depois, int quantidadeAntes, int quantidadeDepois) {
        eventosPort.publicarCurvaPrimariaEditada(new EventoCurvaPrimariaEditada(
            MDC.get("correlationId"), FONTE, curva.codigo(), curva.nome(), dataBase, operacao,
            antes, depois, quantidadeAntes, quantidadeDepois, OffsetDateTime.now(BRASILIA)));
    }
}
