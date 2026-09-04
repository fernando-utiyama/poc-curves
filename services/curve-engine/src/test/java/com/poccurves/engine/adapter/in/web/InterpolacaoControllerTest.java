package com.poccurves.engine.adapter.in.web;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.engine.application.InterpolacaoService;
import com.poccurves.engine.config.CurveEngineSecurityConfig;
import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterpolacaoController.class)
@Import(CurveEngineSecurityConfig.class)
class InterpolacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterpolacaoService interpolacaoService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testInterpolarSucesso() throws Exception {
        InterpolacaoResponse mockResponse = new InterpolacaoResponse(
                "PRE", 3, "LINEAR", List.of(new ItemInterpolacaoResultado(21, "0.10", null, "VERTICE_EXATO", null))
        );

        when(interpolacaoService.interpolar(eq("PRE"), any())).thenReturn(Optional.of(mockResponse));

        InterpolacaoRequest request = new InterpolacaoRequest(LocalDate.of(2026, 8, 21), null, null, List.of(21));

        mockMvc.perform(post("/curvas/PRE/interpolacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoCurva").value("PRE"))
                .andExpect(jsonPath("$.versaoUtilizada").value(3))
                .andExpect(jsonPath("$.resultados[0].status").value("VERTICE_EXATO"));
    }

    @Test
    void testInterpolarNaoEncontrado() throws Exception {
        when(interpolacaoService.interpolar(eq("INVALIDA"), any())).thenReturn(Optional.empty());

        InterpolacaoRequest request = new InterpolacaoRequest(LocalDate.of(2026, 8, 21), null, null, List.of(21));

        mockMvc.perform(post("/curvas/INVALIDA/interpolacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }
}
