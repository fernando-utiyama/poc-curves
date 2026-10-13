package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.BtrsCurvaPrimrUseCase;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.BtrsCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BtrsCurvaPrimrController.class)
class BtrsCurvaPrimrControllerTest {

    private static final String BASE = "/api/v1/curvas-mercado";

    private final LocalDate dataBase = LocalDate.of(2026, 9, 14);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BtrsCurvaPrimrUseCase useCase;

    @Test
    @DisplayName("GET primaria-b3 lista as datas com dado bruto, sem paginação")
    void deveListarDatasBase() throws Exception {
        CurvaPrimrDataBase item = new CurvaPrimrDataBase(
            "PRE", "DIxPRE", SituacaoCurva.ATIVO, dataBase, 278L, List.of("PRE"), false);
        when(useCase.listarDatasBase(any(), any(), any(), any())).thenReturn(List.of(item));

        mockMvc.perform(get(BASE + "/primaria-b3")
                .param("de", "2026-09-01")
                .param("ate", "2026-09-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].codigo").value("PRE"))
            .andExpect(jsonPath("$[0].nome").value("DIxPRE"))
            .andExpect(jsonPath("$[0].dataBase").value("2026-09-14"))
            .andExpect(jsonPath("$[0].quantidadeVertices").value(278))
            .andExpect(jsonPath("$[0].tickersProvedor[0]").value("PRE"))
            .andExpect(jsonPath("$[0].curvaConstruida").value(false));
    }

    @Test
    @DisplayName("GET /{nome}/primaria-b3/{dataBase} devolve os vértices e se a data foi construída")
    void deveConsultarVerticesDaData() throws Exception {
        BtrsCurvaPrimr vertice = new BtrsCurvaPrimr(1L, "DIxPRE", dataBase, 1, 1, new BigDecimal("13.900000000000"), null, null);
        when(useCase.consultar("DIxPRE", dataBase))
            .thenReturn(new VerticesPrimrDaData<>(false, List.of(vertice)));

        mockMvc.perform(get(BASE + "/DIxPRE/primaria-b3/2026-09-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.curvaConstruida").value(false))
            .andExpect(jsonPath("$.vertices[0].diasCorridos").value(1))
            .andExpect(jsonPath("$.vertices[0].diasUteis").value(1))
            .andExpect(jsonPath("$.vertices[0].valor").value("13.900000000000"));
    }

    @Test
    @DisplayName("GET /{nome}/primaria-b3/{dataBase} com curva inexistente responde 404")
    void deveResponder404ComCurvaInexistente() throws Exception {
        when(useCase.consultar("NAO-EXISTE", dataBase))
            .thenThrow(new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva NAO-EXISTE não encontrada"));

        mockMvc.perform(get(BASE + "/NAO-EXISTE/primaria-b3/2026-09-14"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{nome}/primaria-b3/{dataBase}/vertices responde 201 com o vértice")
    void deveIncluirVertice() throws Exception {
        BtrsCurvaPrimr vertice = new BtrsCurvaPrimr(1L, "DIxPRE", dataBase, 1, 1, new BigDecimal("13.900000000000"), null, null);
        when(useCase.incluir(eq("DIxPRE"), eq(dataBase), any(BtrsCurvaPrimrInput.class))).thenReturn(vertice);

        mockMvc.perform(post(BASE + "/DIxPRE/primaria-b3/2026-09-14/vertices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "diasCorridos": 1, "diasUteis": 1, "valor": 13.900000000000 }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.valor").value("13.900000000000"));
    }

    @Test
    @DisplayName("PUT /{nome}/primaria-b3/{dataBase}/vertices/{id} responde 200 com o vértice")
    void deveAlterarVertice() throws Exception {
        BtrsCurvaPrimr vertice = new BtrsCurvaPrimr(1L, "DIxPRE", dataBase, 112, 75, new BigDecimal("13.590000000000"), null, null);
        when(useCase.alterar(eq("DIxPRE"), eq(dataBase), eq(1L), any(BtrsCurvaPrimrInput.class))).thenReturn(vertice);

        mockMvc.perform(put(BASE + "/DIxPRE/primaria-b3/2026-09-14/vertices/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "diasCorridos": 112, "diasUteis": 75, "valor": 13.590000000000 }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valor").value("13.590000000000"))
            .andExpect(jsonPath("$.diasCorridos").value(112));
    }

    @Test
    @DisplayName("DELETE /{nome}/primaria-b3/{dataBase}/vertices/{id} responde 200 vazio")
    void deveExcluirVertice() throws Exception {
        mockMvc.perform(delete(BASE + "/DIxPRE/primaria-b3/2026-09-14/vertices/1"))
            .andExpect(status().isOk());

        verify(useCase).excluir("DIxPRE", dataBase, 1L);
    }

    @Test
    @DisplayName("DELETE /{nome}/primaria-b3/{dataBase} apaga só o bruto da data e responde 200 vazio")
    void deveExcluirData() throws Exception {
        mockMvc.perform(delete(BASE + "/DIxPRE/primaria-b3/2026-09-14"))
            .andExpect(status().isOk());

        verify(useCase).excluirData("DIxPRE", dataBase);
    }
}
