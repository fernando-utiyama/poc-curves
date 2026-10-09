package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.EngineIndisponivelException;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.DadoVertcCurvaRepositoryPort;
import br.com.poc.application.port.out.EnginePort;
import br.com.poc.application.port.out.EnginePort.RespostaEngine;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.DayCounterCotacao;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.cadastro.CurvaMercado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurvaMercadoAcoesServiceTest {

    @Mock
    private CurvaMercdRepositoryPort repositoryPort;

    @Mock
    private DadoVertcCurvaRepositoryPort dadoVertcCurvaRepositoryPort;

    @Mock
    private EventosPort eventosPort;

    @Mock
    private EnginePort enginePort;

    @InjectMocks
    private CurvaMercadoAcoesService service;

    private final LocalDate dataBase = LocalDate.of(2026, 3, 27);
    private final String nome = "DIxPRE";
    private final String codigo = "PRE";
    private final String cid = "test-cid-123";

    private CurvaMercado curva;

    @BeforeEach
    void setUp() {
        curva = new CurvaMercado(
            codigo, nome, Unidade.TAXA, DayCounterCotacao.Business252,
            CompoundingCotacao.Compounded, "BRL", "BR", null, null,
            SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
            LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
            null, null, null
        );
    }

    @Test
    @DisplayName("Deve lançar 404 NotFoundException quando curva não existir em qualquer operação")
    void deveLancarNotFoundExceptionQuandoCurvaNaoExistir() {
        when(repositoryPort.findByNome("XYZ")).thenReturn(Optional.empty());

        NotFoundException ex1 = assertThrows(NotFoundException.class,
            () -> service.construir("XYZ", dataBase, true, null, null, cid));
        assertEquals(CadastroErrorCode.NAO_ENCONTRADO.getCode(), ex1.getErrorCode());

        NotFoundException ex2 = assertThrows(NotFoundException.class,
            () -> service.regravarInterpolada("XYZ", dataBase, cid));
        assertEquals(CadastroErrorCode.NAO_ENCONTRADO.getCode(), ex2.getErrorCode());

        NotFoundException ex3 = assertThrows(NotFoundException.class,
            () -> service.consultarVertices("XYZ", dataBase, cid));
        assertEquals(CadastroErrorCode.NAO_ENCONTRADO.getCode(), ex3.getErrorCode());

        NotFoundException ex4 = assertThrows(NotFoundException.class,
            () -> service.interpolar("XYZ", dataBase, "du=252", cid));
        assertEquals(CadastroErrorCode.NAO_ENCONTRADO.getCode(), ex4.getErrorCode());

        verifyNoInteractions(enginePort);
    }

    @Test
    @DisplayName("Deve lançar 422 quando a curva não tem código (o engine só a acha pelo código)")
    void deveLancarErroQuandoCurvaSemCodigo() {
        CurvaMercado semCodigo = new CurvaMercado(
            null, "TBD-B3-DIXPRE", Unidade.TAXA, DayCounterCotacao.Business252,
            CompoundingCotacao.Compounded, "BRL", "BR", null, null,
            SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
            LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
            null, null, null
        );
        when(repositoryPort.findByNome("TBD-B3-DIXPRE")).thenReturn(Optional.of(semCodigo));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.construir("TBD-B3-DIXPRE", dataBase, true, null, null, cid));

        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
        verifyNoInteractions(enginePort);
    }

    @Test
    @DisplayName("Deve lançar 400 InvalidInputException quando apenas fonte for informada na construção")
    void deveLancarInvalidInputExceptionQuandoFonteSemProduto() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));

        InvalidInputException ex = assertThrows(InvalidInputException.class,
            () -> service.construir(nome, dataBase, false, "B3", null, cid));

        assertEquals(CadastroErrorCode.PARAMETRO_INVALIDO.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Informe fonte e produto juntos"));
        verifyNoInteractions(enginePort);
    }

    @Test
    @DisplayName("Deve lançar 400 InvalidInputException quando apenas produto for informado na construção")
    void deveLancarInvalidInputExceptionQuandoProdutoSemFonte() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));

        InvalidInputException ex = assertThrows(InvalidInputException.class,
            () -> service.construir(nome, dataBase, false, null, "DI1", cid));

        assertEquals(CadastroErrorCode.PARAMETRO_INVALIDO.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Informe fonte e produto juntos"));
        verifyNoInteractions(enginePort);
    }

    @Test
    @DisplayName("Deve construir com sucesso e repassar resposta quando fonte e produto forem ambos nulos")
    void deveConstruirComSucessoSemFonteEProduto() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        RespostaEngine mockResposta = new RespostaEngine(200, "{\"status\":\"ok\"}");
        when(enginePort.construir(codigo, dataBase, true, null, null, cid)).thenReturn(mockResposta);

        RespostaEngine resposta = service.construir(nome, dataBase, true, null, null, cid);

        assertEquals(200, resposta.status());
        assertEquals("{\"status\":\"ok\"}", resposta.corpoJson());
        verify(enginePort).construir(codigo, dataBase, true, null, null, cid);
    }

    @Test
    @DisplayName("Deve construir com sucesso e repassar resposta quando fonte e produto forem ambos informados")
    void deveConstruirComSucessoComFonteEProduto() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        RespostaEngine mockResposta = new RespostaEngine(200, "{\"construida\":true}");
        when(enginePort.construir(codigo, dataBase, false, "B3", "DI1", cid)).thenReturn(mockResposta);

        RespostaEngine resposta = service.construir(nome, dataBase, false, "B3", "DI1", cid);

        assertEquals(200, resposta.status());
        assertEquals("{\"construida\":true}", resposta.corpoJson());
        verify(enginePort).construir(codigo, dataBase, false, "B3", "DI1", cid);
    }

    @Test
    @DisplayName("Deve regravar interpolada com sucesso e repassar resposta")
    void deveRegravarInterpoladaComSucesso() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        RespostaEngine mockResposta = new RespostaEngine(200, "{\"regravada\":true}");
        when(enginePort.regravarInterpolada(codigo, dataBase, cid)).thenReturn(mockResposta);

        RespostaEngine resposta = service.regravarInterpolada(nome, dataBase, cid);

        assertEquals(200, resposta.status());
        assertEquals("{\"regravada\":true}", resposta.corpoJson());
        verify(enginePort).regravarInterpolada(codigo, dataBase, cid);
    }

    @Test
    @DisplayName("Deve consultar vértices com sucesso e repassar resposta")
    void deveConsultarVerticesComSucesso() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        RespostaEngine mockResposta = new RespostaEngine(200, "{\"vertices\":[]}");
        when(enginePort.consultarVertices(codigo, dataBase, cid)).thenReturn(mockResposta);

        RespostaEngine resposta = service.consultarVertices(nome, dataBase, cid);

        assertEquals(200, resposta.status());
        assertEquals("{\"vertices\":[]}", resposta.corpoJson());
        verify(enginePort).consultarVertices(codigo, dataBase, cid);
    }

    @Test
    @DisplayName("Deve lançar 400 InvalidInputException quando queryString de interpolação não conter du nem data")
    void deveLancarInvalidInputExceptionQuandoInterpolarSemDuNemData() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));

        InvalidInputException ex1 = assertThrows(InvalidInputException.class,
            () -> service.interpolar(nome, dataBase, null, cid));
        assertEquals(CadastroErrorCode.PARAMETRO_INVALIDO.getCode(), ex1.getErrorCode());
        assertTrue(ex1.getMessage().contains("Informe ao menos um du ou uma data"));

        InvalidInputException ex2 = assertThrows(InvalidInputException.class,
            () -> service.interpolar(nome, dataBase, "param=123", cid));
        assertEquals(CadastroErrorCode.PARAMETRO_INVALIDO.getCode(), ex2.getErrorCode());
        assertTrue(ex2.getMessage().contains("Informe ao menos um du ou uma data"));

        verifyNoInteractions(enginePort);
    }

    @Test
    @DisplayName("Deve interpolar com sucesso quando query string contiver du")
    void deveInterpolarComSucessoComDu() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        RespostaEngine mockResposta = new RespostaEngine(200, "{\"taxa\":0.12}");
        when(enginePort.interpolar(codigo, dataBase, "du=252", cid)).thenReturn(mockResposta);

        RespostaEngine resposta = service.interpolar(nome, dataBase, "du=252", cid);

        assertEquals(200, resposta.status());
        assertEquals("{\"taxa\":0.12}", resposta.corpoJson());
        verify(enginePort).interpolar(codigo, dataBase, "du=252", cid);
    }

    @Test
    @DisplayName("Deve interpolar com sucesso quando query string contiver data")
    void deveInterpolarComSucessoComData() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        RespostaEngine mockResposta = new RespostaEngine(200, "{\"taxa\":0.13}");
        when(enginePort.interpolar(codigo, dataBase, "data=2026-12-01", cid)).thenReturn(mockResposta);

        RespostaEngine resposta = service.interpolar(nome, dataBase, "data=2026-12-01", cid);

        assertEquals(200, resposta.status());
        assertEquals("{\"taxa\":0.13}", resposta.corpoJson());
        verify(enginePort).interpolar(codigo, dataBase, "data=2026-12-01", cid);
    }

    @Test
    @DisplayName("Deve propagar 503 EngineIndisponivelException quando engine estiver indisponível")
    void devePropagarEngineIndisponivelException() {
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        when(enginePort.construir(eq(codigo), eq(dataBase), any(), any(), any(), eq(cid)))
            .thenThrow(new EngineIndisponivelException("Engine indisponível"));

        EngineIndisponivelException ex = assertThrows(EngineIndisponivelException.class,
            () -> service.construir(nome, dataBase, false, null, null, cid));

        assertEquals(CadastroErrorCode.ENGINE_INDISPONIVEL.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Excluir vértices: apaga vértices e interpolada da data e publica o evento")
    void excluiVerticesDaData() {
        LocalDate data = LocalDate.of(2026, 9, 14);
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        when(dadoVertcCurvaRepositoryPort.excluirPorNomeCurvaEDataBase(nome, data)).thenReturn(true);

        service.excluirVertices(nome, data);

        verify(dadoVertcCurvaRepositoryPort).excluirPorNomeCurvaEDataBase(nome, data);
        verify(eventosPort).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Excluir vértices: data sem nada construído gera NAO_ENCONTRADO e não publica evento")
    void excluirVerticesDataSemConstrucao() {
        LocalDate data = LocalDate.of(2026, 9, 14);
        when(repositoryPort.findByNome(nome)).thenReturn(Optional.of(curva));
        when(dadoVertcCurvaRepositoryPort.excluirPorNomeCurvaEDataBase(nome, data)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.excluirVertices(nome, data));
        verify(eventosPort, never()).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Excluir vértices: curva inexistente gera NAO_ENCONTRADO sem tocar nos dados")
    void excluirVerticesCurvaInexistente() {
        when(repositoryPort.findByNome("XXX")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.excluirVertices("XXX", LocalDate.of(2026, 9, 14)));
        verifyNoInteractions(dadoVertcCurvaRepositoryPort, eventosPort);
    }
}
