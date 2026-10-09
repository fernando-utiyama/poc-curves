package br.com.poc.application.service;

import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.DadosConstruidosPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.DayCounterCotacao;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.cadastro.ConstrucaoApagada;
import br.com.poc.domain.cadastro.CurvaMercado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApagarConstrucaoServiceTest {

    private static final LocalDate DATA = LocalDate.of(2026, 9, 14);

    @Mock
    private CurvaMercdRepositoryPort curvaRepositoryPort;

    @Mock
    private DadosConstruidosPort dadosConstruidosPort;

    @Mock
    private EventosPort eventosPort;

    private ApagarConstrucaoService service;

    private final CurvaMercado curvaPadrao = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
        null, null, null
    );

    @BeforeEach
    void setUp() {
        service = new ApagarConstrucaoService(curvaRepositoryPort, dadosConstruidosPort, eventosPort);
    }

    @Test
    @DisplayName("Apaga vértices e interpolada da data e publica o evento")
    void apagaConstrucaoDaData() {
        when(curvaRepositoryPort.findByNome("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(dadosConstruidosPort.apagar("DIxPRE", DATA)).thenReturn(new ConstrucaoApagada(278, 3650));

        ConstrucaoApagada res = service.apagar("PRE", DATA);

        assertEquals(278, res.vertices());
        assertEquals(3650, res.pontos());
        verify(eventosPort).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Data sem nada construído gera NAO_ENCONTRADO e não publica evento")
    void dataSemConstrucao() {
        when(curvaRepositoryPort.findByNome("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(dadosConstruidosPort.apagar("DIxPRE", DATA)).thenReturn(new ConstrucaoApagada(0, 0));

        assertThrows(NotFoundException.class, () -> service.apagar("PRE", DATA));
        verify(eventosPort, never()).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Curva inexistente gera NAO_ENCONTRADO sem tocar nos dados")
    void curvaInexistente() {
        when(curvaRepositoryPort.findByNome("XXX")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.apagar("XXX", DATA));
        verifyNoInteractions(dadosConstruidosPort, eventosPort);
    }
}
