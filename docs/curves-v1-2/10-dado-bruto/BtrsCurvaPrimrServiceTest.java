package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.BtrsCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.BtrsCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaMercado;
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
class BtrsCurvaPrimrServiceTest {

    @Mock
    private BtrsCurvaPrimrRepositoryPort btrsRepositoryPort;

    @Mock
    private CurvaMercdRepositoryPort curvaRepositoryPort;

    @Mock
    private EventosPort eventosPort;

    private BtrsCurvaPrimrService service;

    private final LocalDate dataBase = LocalDate.of(2026, 9, 14);

    private final CurvaMercado curva = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, null, null, "BRL", "BR", null, null, SituacaoCurva.ATIVO,
        LocalDate.of(2026, 1, 1), null, null, null, null, null, null);

    private final BtrsCurvaPrimr vertice = new BtrsCurvaPrimr(
        7, "DIxPRE", dataBase, 1, 1, new BigDecimal("13.900000000000"), null, null);

    @BeforeEach
    void setUp() {
        service = new BtrsCurvaPrimrService(btrsRepositoryPort, curvaRepositoryPort, eventosPort);
    }

    @Test
    @DisplayName("Consultar: curva encontrada pelo nome devolve os vértices e se a data foi construída")
    void consultarPorNome() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(btrsRepositoryPort.existeVerticeConstruido("DIxPRE", dataBase)).thenReturn(true);
        when(btrsRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));

        VerticesPrimrDaData<BtrsCurvaPrimr> res = service.consultar("DIxPRE", dataBase);

        assertTrue(res.curvaConstruida());
        assertEquals(List.of(vertice), res.vertices());
    }

    @Test
    @DisplayName("Consultar: curva inexistente responde 404")
    void consultarCurvaInexistente() {
        when(curvaRepositoryPort.findByNome("NAO-EXISTE")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.consultar("NAO-EXISTE", dataBase));
        verifyNoInteractions(btrsRepositoryPort);
    }

    @Test
    @DisplayName("Incluir: grava com o próximo id e o nome da curva, e publica o evento")
    void incluirGrava() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(btrsRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of());
        when(btrsRepositoryPort.proximoId()).thenReturn(7);
        when(btrsRepositoryPort.salvar(vertice)).thenReturn(vertice);

        BtrsCurvaPrimr salvo = service.incluir("DIxPRE", dataBase,
            new BtrsCurvaPrimrInput(1, 1, new BigDecimal("13.900000000000"), null, null));

        assertEquals(vertice, salvo);
        verify(eventosPort).publicarCurvaPrimariaEditada(any());
    }

    @Test
    @DisplayName("Incluir: sem dias úteis e sem valor responde 422 e nada é gravado")
    void incluirSemCamposObrigatorios() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.incluir("DIxPRE", dataBase, new BtrsCurvaPrimrInput(1, null, null, null, null)));

        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
        verifyNoInteractions(btrsRepositoryPort, curvaRepositoryPort, eventosPort);
    }

    @Test
    @DisplayName("Incluir: valor com mais de 12 casas responde 422 (o bruto não é arredondado)")
    void incluirValorComCasasDemais() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.incluir("DIxPRE", dataBase, new BtrsCurvaPrimrInput(1, 1, new BigDecimal("13.1234567890123"), null, null)));

        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Alterar: vértice de outra data responde 404")
    void alterarVerticeInexistente() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(btrsRepositoryPort.findByIdAndNomeCurvaAndDataBase(99, "DIxPRE", dataBase)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.alterar("DIxPRE", dataBase, 99,
            new BtrsCurvaPrimrInput(1, 1, BigDecimal.ONE, null, null)));
        verify(btrsRepositoryPort, never()).salvar(any());
    }

    @Test
    @DisplayName("Excluir vértice: apaga pelo id, nome e data")
    void excluirVertice() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(btrsRepositoryPort.findByIdAndNomeCurvaAndDataBase(7, "DIxPRE", dataBase)).thenReturn(Optional.of(vertice));
        when(btrsRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));

        service.excluir("DIxPRE", dataBase, 7);

        verify(btrsRepositoryPort).excluir(7, "DIxPRE", dataBase);
    }

    @Test
    @DisplayName("Excluir data: apaga todos os vértices brutos dela, sem olhar a curva construída")
    void excluirData() {
        when(curvaRepositoryPort.findByNome("DIxPRE")).thenReturn(Optional.of(curva));
        when(btrsRepositoryPort.findByNomeCurvaAndDataBase("DIxPRE", dataBase)).thenReturn(List.of(vertice));

        service.excluirData("DIxPRE", dataBase);

        verify(btrsRepositoryPort).excluirPorNomeCurvaEDataBase("DIxPRE", dataBase);
        verify(btrsRepositoryPort, never()).existeVerticeConstruido(any(), any());
    }
}
