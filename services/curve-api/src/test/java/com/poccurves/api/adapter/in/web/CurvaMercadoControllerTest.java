package com.poccurves.api.adapter.in.web;

import com.poccurves.api.application.CurvaDadosService;
import com.poccurves.api.application.CurvaMercadoService;
import com.poccurves.api.config.CurveApiSecurityConfig;
import com.poccurves.api.dto.ApiDtos.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CurvaMercadoController.class)
@Import(CurveApiSecurityConfig.class)
class CurvaMercadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurvaMercadoService curvaMercadoService;

    @MockitoBean
    private CurvaDadosService curvaDadosService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final org.springframework.security.core.GrantedAuthority VIEWER =
            new SimpleGrantedAuthority("ROLE_CURVE_VIEWER");

    @Test
    void listarCurvasRetornaOCatalogoDoServico() throws Exception {
        when(curvaMercadoService.listarTodas()).thenReturn(new CatalogoCurvasResponse(List.of(
                new CurvaMercadoResponse("B3_TAXA_SWAP_DCL", "CURVA_SWAP", "TAXA_JUROS", "BRL",
                        LocalDate.of(2020, 1, 1), "sistema")
        )));

        mockMvc.perform(get("/curvas").with(jwt().authorities(VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curvas[0].tickerIndcd").value("B3_TAXA_SWAP_DCL"));
    }

    @Test
    void obterCurvaRetornaACurvaDoServico() throws Exception {
        when(curvaMercadoService.obterPorTicker("B3_TAXA_SWAP_DCL")).thenReturn(
                new CurvaMercadoResponse("B3_TAXA_SWAP_DCL", "CURVA_SWAP", "TAXA_JUROS", "BRL",
                        LocalDate.of(2020, 1, 1), "sistema"));

        mockMvc.perform(get("/curvas/B3_TAXA_SWAP_DCL").with(jwt().authorities(VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moedaNegoc").value("BRL"));
    }

    @Test
    void obterCurvaRetorna404QuandoTickerNaoExiste() throws Exception {
        when(curvaMercadoService.obterPorTicker("INEXISTENTE"))
                .thenThrow(new NoSuchElementException("Curva de mercado não encontrada para o ticker: INEXISTENTE"));

        mockMvc.perform(get("/curvas/INEXISTENTE").with(jwt().authorities(VIEWER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void obterVerticesRetornaOsPontosDeTDadoCurva() throws Exception {
        when(curvaDadosService.consultarVertices(eq("B3_TAXA_SWAP_DCL"), eq(LocalDate.of(2026, 9, 14))))
                .thenReturn(new CurvaDadosResponse("B3_TAXA_SWAP_DCL", LocalDate.of(2026, 9, 14), List.of(
                        new PontoCurvaDTO(LocalDate.of(2026, 9, 15), new BigDecimal("13.90"))
                )));

        mockMvc.perform(get("/curvas/B3_TAXA_SWAP_DCL/vertices")
                        .param("dataReferencia", "2026-09-14")
                        .with(jwt().authorities(VIEWER)))
                .andExpect(status().isOk())
                // Sem o customizer de serialização de BigDecimal (bean em AppConfig, fora do slice
                // @WebMvcTest), o número volta cru no JSON — 13.90 é lido como 13.9 pelo JsonPath.
                .andExpect(jsonPath("$.pontos[0].valor").value(13.9));
    }

    @Test
    void obterCurvaConstruidaRetornaOsPontosDeTCurvaData() throws Exception {
        when(curvaDadosService.consultarCurvaConstruida(eq("B3_TAXA_SWAP_DCL"), eq(LocalDate.of(2026, 9, 14))))
                .thenReturn(new CurvaDadosResponse("B3_TAXA_SWAP_DCL", LocalDate.of(2026, 9, 14), List.of(
                        new PontoCurvaDTO(LocalDate.of(2026, 9, 15), new BigDecimal("13.90"))
                )));

        mockMvc.perform(get("/curvas/B3_TAXA_SWAP_DCL/curva")
                        .param("dataReferencia", "2026-09-14")
                        .with(jwt().authorities(VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pontos[0].dataVertice").value("2026-09-15"));
    }

    @Test
    void compararCurvasRetornaAResponseDoServico() throws Exception {
        ComparacaoCurvasRequest request = new ComparacaoCurvasRequest(
                LocalDate.of(2026, 9, 14), "B3_TAXA_SWAP_DCL", "B3_TAXA_SWAP_PRE");
        when(curvaDadosService.compararCurvas(any())).thenReturn(new ComparacaoCurvasResponse(
                LocalDate.of(2026, 9, 14), "B3_TAXA_SWAP_DCL", "B3_TAXA_SWAP_PRE", List.of(
                        new ItemComparacaoCurvasDTO(LocalDate.of(2026, 9, 15), new BigDecimal("13.90"), new BigDecimal("14.00"))
                )));

        mockMvc.perform(post("/curvas/comparacao")
                        .with(jwt().authorities(VIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pontos[0].valorA").value(13.9))
                .andExpect(jsonPath("$.pontos[0].valorB").value(14.0));
    }
}
