package com.poccurves.api;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.poccurves.api.config.CurveApiSecurityConfig;
import com.poccurves.api.adapter.in.web.CurvaConsultaController;
import com.poccurves.api.adapter.in.web.DefinicaoCurvaController;
import com.poccurves.api.application.DefinicaoCurvaService;
import com.poccurves.api.application.CurvaConsultaService;
import com.poccurves.api.dto.ApiDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DefinicaoCurvaController.class, CurvaConsultaController.class})
@Import(CurveApiSecurityConfig.class)
class CurveApiOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefinicaoCurvaService definicaoCurvaService;

    @MockitoBean
    private CurvaConsultaService curvaConsultaService;

    @Test
    void deveEstarEmConformidadeComOContratoOpenAPI() throws Exception {
        when(definicaoCurvaService.listarDefinicoes(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new CatalogoDefinicoesResponse(Collections.emptyList(), 0, 0, 0));

        String specPath = "../../contracts/openapi/curve-api.yaml";

        mockMvc.perform(get("/curvas/definicoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(specPath));
    }
}
