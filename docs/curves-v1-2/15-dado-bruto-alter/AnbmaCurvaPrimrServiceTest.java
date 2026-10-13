package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.AnbmaCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnbmaCurvaPrimrServiceTest {

    @Mock
    private AnbmaCurvaPrimrRepositoryPort anbmaRepositoryPort;

    @Mock
    private CurvaMercdRepositoryPort curvaRepositoryPort;

    @Mock
    private EventosPort eventosPort;

    private AnbmaCurvaPrimrService service;

    private final LocalDate dataBase = LocalDate.of(2026, 9, 14);

    private final CurvaMercado curva = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, null, null, "BRL", "BR", null, null, SituacaoCurva.ATIVO,
        LocalDate.of(2026, 1, 1), null, null, null, null, null, null);

    private final AnbmaCurvaPrimr vertice = new AnbmaCurvaPrimr(
        7L, "DIxPRE", dataBase, new BigDecimal("10.500000000000"), BigDecimal.valueOf(229), null, null);

    private final CurvaPrimrDataBase dataBaseListada = new CurvaPrimrDataBase(
        null, "TBD-DIXPRE", SituacaoCurva.ATIVO, LocalDate.of(2026, 9, 14), 278L, List.of("PRE"), false);

    @BeforeEach
    void setUp() {
        service = new AnbmaCurvaPrimrService(anbmaRepositoryPort, curvaRepositoryPort, eventosPort);
    }

    @Test
    @DisplayName("Consultar: curva encontrada pelo nome devolve os vértices e se a data foi construída")
    void consultarPorNome() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.existeVerticeConstruido("DIxPRE", dataBase)).thenReturn(true);
        when(anbmaRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));

        VerticesPrimrDaData<AnbmaCurvaPrimr> res = service.consultar("DIxPRE", dataBase);

        assertTrue(res.curvaConstruida());
        assertEquals(List.of(vertice), res.vertices());
    }

    @Test
    @DisplayName("Consultar: curva inexistente responde 404")
    void consultarCurvaInexistente() {
        when(curvaRepositoryPort.findByNome("NAO-EXISTE")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.consultar("NAO-EXISTE", dataBase));
        verifyNoInteractions(anbmaRepositoryPort);
    }

    @Test
    @DisplayName("Incluir: grava com o nome da curva (o id vem do banco), e publica o evento")
    void incluirGrava() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of());
        when(anbmaRepositoryPort.salvar(any())).thenReturn(vertice);

        AnbmaCurvaPrimr salvo = service.incluir("DIxPRE", dataBase, new AnbmaCurvaPrimrInput(229, new BigDecimal("10.500000000000"), null, null));

        assertEquals(vertice, salvo);
        verify(eventosPort).publicarCurvaPrimariaEditada(any());
    }

    @Test
    @DisplayName("Incluir: sem prazo responde 422 e nada é gravado")
    void incluirSemCamposObrigatorios() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.incluir("DIxPRE", dataBase, new AnbmaCurvaPrimrInput(null, null, null, null)));

        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
        verifyNoInteractions(anbmaRepositoryPort, curvaRepositoryPort, eventosPort);
    }

    @Test
    @DisplayName("Incluir: valor com mais de 12 casas responde 422 (o bruto não é arredondado)")
    void incluirValorComCasasDemais() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.incluir("DIxPRE", dataBase, new AnbmaCurvaPrimrInput(229, new BigDecimal("10.1234567890123"), null, null)));

        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Incluir: prazo menor que 1 responde 422")
    void incluirPrazoZero() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.incluir("DIxPRE", dataBase, new AnbmaCurvaPrimrInput(0, BigDecimal.ONE, null, null)));

        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
        verifyNoInteractions(anbmaRepositoryPort, curvaRepositoryPort, eventosPort);
    }

    @Test
    @DisplayName("Incluir: a taxa é opcional e grava nula")
    void incluirSemTaxa() {
        AnbmaCurvaPrimr semTaxa = new AnbmaCurvaPrimr(7L, "DIxPRE", dataBase, null, BigDecimal.valueOf(229), null, null);
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of());
        when(anbmaRepositoryPort.salvar(any())).thenReturn(semTaxa);

        AnbmaCurvaPrimr salvo = service.incluir("DIxPRE", dataBase, new AnbmaCurvaPrimrInput(229, null, null, null));

        assertNull(salvo.taxa());
        verify(eventosPort).publicarCurvaPrimariaEditada(any());
    }

    @Test
    @DisplayName("Alterar: vértice de outra data responde 404")
    void alterarVerticeInexistente() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.findByIdAndNomeCurvaAndDataBase(99L, "DIxPRE", dataBase)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.alterar("DIxPRE", dataBase, 99L,
            new AnbmaCurvaPrimrInput(30, BigDecimal.ONE, null, null)));
        verify(anbmaRepositoryPort, never()).salvar(any());
    }

    @Test
    @DisplayName("Alterar: grava o vértice com o mesmo id e publica o evento")
    void alterarGrava() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.findByIdAndNomeCurvaAndDataBase(7L, "DIxPRE", dataBase)).thenReturn(Optional.of(vertice));
        when(anbmaRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));
        when(anbmaRepositoryPort.salvar(any())).thenReturn(vertice);

        AnbmaCurvaPrimr salvo = service.alterar("DIxPRE", dataBase, 7L, new AnbmaCurvaPrimrInput(229, new BigDecimal("10.500000000000"), null, null));

        assertEquals(vertice, salvo);
        verify(eventosPort).publicarCurvaPrimariaEditada(any());
    }

    @Test
    @DisplayName("Excluir vértice: apaga pelo id, nome e data")
    void excluirVertice() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.findByIdAndNomeCurvaAndDataBase(7L, "DIxPRE", dataBase)).thenReturn(Optional.of(vertice));
        when(anbmaRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));

        service.excluir("DIxPRE", dataBase, 7L);

        verify(anbmaRepositoryPort).excluir(7L, "DIxPRE", dataBase);
    }

    @Test
    @DisplayName("Excluir data: apaga todos os vértices brutos dela, sem olhar a curva construída")
    void excluirData() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(anbmaRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));

        service.excluirData("DIxPRE", dataBase);

        verify(anbmaRepositoryPort).excluirPorNomeCurvaEDataBase("DIxPRE", dataBase);
        verify(anbmaRepositoryPort, never()).existeVerticeConstruido(any(), any());
    }

    @Test
    @DisplayName("Sem período, devolve a última data de cada curva, inclusive a sem código")
    void semPeriodoUsaUltimaData() {
        when(anbmaRepositoryPort.listarUltimaDataBase(null, null)).thenReturn(List.of(dataBaseListada));

        List<CurvaPrimrDataBase> res = service.listarDatasBase(null, null, " ", "");

        assertEquals(List.of(dataBaseListada), res);
        verify(anbmaRepositoryPort, never()).listarDatasBase(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Filtros de código e nome chegam sem espaços nas pontas")
    void filtrosAparados() {
        when(anbmaRepositoryPort.listarUltimaDataBase("PRE", "DIxPRE")).thenReturn(List.of(dataBaseListada));

        service.listarDatasBase(null, null, " PRE ", " DIxPRE ");

        verify(anbmaRepositoryPort).listarUltimaDataBase("PRE", "DIxPRE");
    }

    @Test
    @DisplayName("Com período, devolve todas as datas dele")
    void comPeriodoUsaAgregado() {
        LocalDate de = LocalDate.of(2026, 9, 1);
        LocalDate ate = LocalDate.of(2026, 9, 30);
        when(anbmaRepositoryPort.listarDatasBase(de, ate, null, null)).thenReturn(List.of(dataBaseListada));

        List<CurvaPrimrDataBase> res = service.listarDatasBase(de, ate, null, null);

        assertEquals(List.of(dataBaseListada), res);
        verify(anbmaRepositoryPort, never()).listarUltimaDataBase(any(), any());
    }

    @Test
    @DisplayName("Só com 'ate', começa 30 dias antes")
    void soAteComecaTrintaDiasAntes() {
        LocalDate ate = LocalDate.of(2026, 9, 30);
        when(anbmaRepositoryPort.listarDatasBase(ate.minusDays(30), ate, null, null)).thenReturn(List.of());

        service.listarDatasBase(null, ate, null, null);

        verify(anbmaRepositoryPort).listarDatasBase(ate.minusDays(30), ate, null, null);
    }

    @Test
    @DisplayName("Início depois do fim é recusado")
    void inicioDepoisDoFim() {
        assertThrows(InvalidInputException.class,
            () -> service.listarDatasBase(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null, null));
        verifyNoInteractions(anbmaRepositoryPort);
    }

    @Test
    @DisplayName("Período maior que 366 dias é recusado")
    void periodoMuitoLongo() {
        assertThrows(InvalidInputException.class,
            () -> service.listarDatasBase(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30), null, null));
        verifyNoInteractions(anbmaRepositoryPort);
    }
}
}
