package br.com.poc.application.service;

import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.AnbmaCurvaPrimrUseCase;
import br.com.poc.application.port.out.AnbmaCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;
import br.com.poc.domain.evento.EventoCurvaPrimariaEditada;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static br.com.poc.application.service.RegrasCurvaPrimr.*;

/**
 * Dado bruto da ANBIMA. Gravar o bruto nunca reconstrói a curva: se a data já foi construída,
 * a correção só vale quando o gestor recalcular na tela Curvas.
 */
@Service
@RequiredArgsConstructor
public class AnbmaCurvaPrimrService implements AnbmaCurvaPrimrUseCase {

    private static final String FONTE = "ANBIMA";

    private final AnbmaCurvaPrimrRepositoryPort anbmaRepositoryPort;
    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final EventosPort eventosPort;

    @Override
    @Transactional(readOnly = true)
    public List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome) {
        String codigoFiltro = textoOuNulo(codigo);
        String nomeFiltro = textoOuNulo(nome);

        // sem período: uma linha por curva, com a última data gravada
        if (de == null && ate == null) {
            return anbmaRepositoryPort.listarUltimaDataBase(codigoFiltro, nomeFiltro);
        }
        Periodo periodo = Periodo.de(de, ate);
        return anbmaRepositoryPort.listarDatasBase(periodo.de(), periodo.ate(), codigoFiltro, nomeFiltro);
    }

    @Override
    @Transactional(readOnly = true)
    public VerticesPrimrDaData<AnbmaCurvaPrimr> consultar(String nomeCurva, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nomeCurva);
        return new VerticesPrimrDaData<>(
            anbmaRepositoryPort.existeVerticeConstruido(curva.nome(), dataBase),
            anbmaRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase));
    }

    @Override
    @Transactional
    public AnbmaCurvaPrimr incluir(String nomeCurva, LocalDate dataBase, AnbmaCurvaPrimrInput input) {
        validar(input);
        CurvaMercado curva = obterCurva(nomeCurva);
        int quantidadeAntes = anbmaRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        AnbmaCurvaPrimr salvo = anbmaRepositoryPort.salvar(novoVertice(anbmaRepositoryPort.proximoId(), curva, dataBase, input));

        publicarEvento(curva, dataBase, "INCLUSAO", null, salvo, quantidadeAntes, quantidadeAntes + 1);
        return salvo;
    }

    @Override
    @Transactional
    public AnbmaCurvaPrimr alterar(String nomeCurva, LocalDate dataBase, Integer id, AnbmaCurvaPrimrInput input) {
        validar(input);
        CurvaMercado curva = obterCurva(nomeCurva);
        AnbmaCurvaPrimr existente = obterVertice(curva, dataBase, id);
        int quantidade = anbmaRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        AnbmaCurvaPrimr salvo = anbmaRepositoryPort.salvar(novoVertice(id, curva, dataBase, input));

        publicarEvento(curva, dataBase, "ALTERACAO", existente, salvo, quantidade, quantidade);
        return salvo;
    }

    @Override
    @Transactional
    public void excluir(String nomeCurva, LocalDate dataBase, Integer id) {
        CurvaMercado curva = obterCurva(nomeCurva);
        AnbmaCurvaPrimr existente = obterVertice(curva, dataBase, id);
        int quantidadeAntes = anbmaRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase).size();

        anbmaRepositoryPort.excluir(id, curva.nome(), dataBase);

        publicarEvento(curva, dataBase, "EXCLUSAO", existente, null, quantidadeAntes, quantidadeAntes - 1);
    }

    @Override
    @Transactional
    public void excluirData(String nomeCurva, LocalDate dataBase) {
        CurvaMercado curva = obterCurva(nomeCurva);
        if (anbmaRepositoryPort.existeVerticeConstruido(curva.nome(), dataBase)) {
            throw dataConstruida(curva.nome(), dataBase);
        }
        List<AnbmaCurvaPrimr> antes = anbmaRepositoryPort.findByNomeCurvaAndDataBase(curva.nome(), dataBase);

        anbmaRepositoryPort.excluirPorNomeCurvaEDataBase(curva.nome(), dataBase);

        publicarEvento(curva, dataBase, "EXCLUSAO_DATA", antes, null, antes.size(), 0);
    }

    private CurvaMercado obterCurva(String nomeCurva) {
        return curvaRepositoryPort.findByNome(nomeCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nomeCurva + " não encontrada"));
    }

    private AnbmaCurvaPrimr obterVertice(CurvaMercado curva, LocalDate dataBase, Integer id) {
        return anbmaRepositoryPort.findByIdAndNomeCurvaAndDataBase(id, curva.nome(), dataBase)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Vértice " + id + " não encontrado na curva " + curva.nome() + " em " + dataBase));
    }

    /** O vértice da ANBIMA (vVertcCurva) é o prazo em dias corridos; o engine converte para dias úteis. */
    private static AnbmaCurvaPrimr novoVertice(Integer id, CurvaMercado curva, LocalDate dataBase, AnbmaCurvaPrimrInput input) {
        return new AnbmaCurvaPrimr(id, curva.nome(), dataBase, input.taxa(), BigDecimal.valueOf(input.prazoDiasCorridos()));
    }

    private static void validar(AnbmaCurvaPrimrInput input) {
        List<Detalhe> erros = new ArrayList<>();
        obrigatorio("prazoDiasCorridos", input.prazoDiasCorridos(), erros);
        if (input.prazoDiasCorridos() != null && input.prazoDiasCorridos() < 1) {
            erros.add(new Detalhe("prazoDiasCorridos", null, input.prazoDiasCorridos().toString(), "prazoDiasCorridos deve ser maior ou igual a 1"));
        }
        decimal("taxa", input.taxa(), 16, 12, erros);
        recusarSeHouverErros(erros);
    }

    private void publicarEvento(CurvaMercado curva, LocalDate dataBase, String operacao,
                                Object antes, Object depois, int quantidadeAntes, int quantidadeDepois) {
        eventosPort.publicarCurvaPrimariaEditada(new EventoCurvaPrimariaEditada(
            MDC.get("correlationId"), FONTE, curva.codigo(), curva.nome(), dataBase, operacao,
            antes, depois, quantidadeAntes, quantidadeDepois, OffsetDateTime.now(BRASILIA)));
    }
}
