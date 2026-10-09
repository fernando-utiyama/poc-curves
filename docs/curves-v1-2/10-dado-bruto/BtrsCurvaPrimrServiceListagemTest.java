package br.com.poc.application.service;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.port.out.BtrsCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.CurvaPrimrDataGravada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BtrsCurvaPrimrServiceListagemTest {

    @Mock
    private BtrsCurvaPrimrRepositoryPort btrsRepositoryPort;

    @Mock
    private CurvaMercdRepositoryPort curvaRepositoryPort;

    @Mock
    private CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;

    @Mock
    private EventosPort eventosPort;

    private BtrsCurvaPrimrService service;

    private final CurvaPrimrDataGravada resumo = new CurvaPrimrDataGravada(
        null, "TBD-B3-DIXPRE", SituacaoCurva.ATIVO, LocalDate.of(2026, 9, 14), 278L, List.of("PRE"), false);

    @BeforeEach
    void setUp() {
        service = new BtrsCurvaPrimrService(btrsRepositoryPort, curvaRepositoryPort, curvaPrvdrRepositoryPort, eventosPort);
    }

    @Test
    @DisplayName("Sem período, devolve a última data de cada curva, inclusive a sem código")
    void semPeriodoUsaUltimaData() {
        when(btrsRepositoryPort.listarUltimaDataGravada(null, null)).thenReturn(List.of(resumo));

        List<CurvaPrimrDataGravada> res = service.listarDatasGravadas(null, null, " ", "");

        assertEquals(List.of(resumo), res);
        verify(btrsRepositoryPort, never()).listarDatasGravadas(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Filtros de código e nome chegam sem espaços nas pontas")
    void filtrosAparados() {
        when(btrsRepositoryPort.listarUltimaDataGravada("PRE", "DIxPRE")).thenReturn(List.of(resumo));

        service.listarDatasGravadas(null, null, " PRE ", " DIxPRE ");

        verify(btrsRepositoryPort).listarUltimaDataGravada("PRE", "DIxPRE");
    }

    @Test
    @DisplayName("Com período, devolve todas as datas dele")
    void comPeriodoUsaAgregado() {
        LocalDate de = LocalDate.of(2026, 9, 1);
        LocalDate ate = LocalDate.of(2026, 9, 30);
        when(btrsRepositoryPort.listarDatasGravadas(de, ate, null, null)).thenReturn(List.of(resumo));

        List<CurvaPrimrDataGravada> res = service.listarDatasGravadas(de, ate, null, null);

        assertEquals(List.of(resumo), res);
        verify(btrsRepositoryPort, never()).listarUltimaDataGravada(any(), any());
    }

    @Test
    @DisplayName("Só com 'ate', começa 30 dias antes")
    void soAteComecaTrintaDiasAntes() {
        LocalDate ate = LocalDate.of(2026, 9, 30);
        when(btrsRepositoryPort.listarDatasGravadas(ate.minusDays(30), ate, null, null)).thenReturn(List.of());

        service.listarDatasGravadas(null, ate, null, null);

        verify(btrsRepositoryPort).listarDatasGravadas(ate.minusDays(30), ate, null, null);
    }

    @Test
    @DisplayName("Início depois do fim é recusado")
    void inicioDepoisDoFim() {
        assertThrows(InvalidInputException.class,
            () -> service.listarDatasGravadas(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null, null));
        verifyNoInteractions(btrsRepositoryPort);
    }

    @Test
    @DisplayName("Período maior que 366 dias é recusado")
    void periodoMuitoLongo() {
        assertThrows(InvalidInputException.class,
            () -> service.listarDatasGravadas(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30), null, null));
        verifyNoInteractions(btrsRepositoryPort);
    }
}
