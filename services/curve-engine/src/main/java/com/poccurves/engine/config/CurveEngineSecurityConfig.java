package com.poccurves.engine.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mesmo emissor Keycloak/realm `curvas` e mesmo extrator de authorities de
 * {@code CurveApiSecurityConfig} (services/curve-api) — curve-engine também expõe
 * porta diretamente no compose (8083), então não pode depender só do curve-bff
 * estar na frente para barrar acesso não autorizado. A rota que importa isto de
 * verdade é {@code POST /api/v1/modelos/validar-groovy}: executar um script
 * arbitrário e persisti-lo como modelo de precificação é uma ação de admin.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class CurveEngineSecurityConfig {

    private final ObjectMapper objectMapper;

    public CurveEngineSecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/metrics").permitAll()

                        // Despacho de construção (D1d): chamada interna serviço-a-serviço do
                        // curve-orchestrator, que não tem SecurityConfig nem emite JWT — mesmo
                        // modelo de confiança de rede interna já usado no resto da plataforma
                        // (orchestrator->function-marketdata, orchestrator->curve-processor, também
                        // sem autenticação). Exigir JWT aqui quebraria a chamada em tempo real.
                        .requestMatchers(HttpMethod.POST, "/api/v1/construcoes").permitAll()

                        // Importar/validar script Groovy é a única ação restrita a administrador —
                        // executa código arbitrário e, se válido, persiste como modelo de precificação.
                        .requestMatchers(HttpMethod.POST, "/api/v1/modelos/validar-groovy").hasRole("CURVE_ADMIN")

                        // Todo o resto (interpolação, listagem e comparação de modelos) é
                        // leitura/cálculo — acessível a qualquer perfil autenticado.
                        .anyRequest().hasAnyRole("CURVE_VIEWER", "CURVE_OPERATOR", "CURVE_ADMIN")
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                                    "codigo", "NAO_AUTORIZADO",
                                    "mensagem", "Token de autenticação ausente, expirado ou inválido.")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                                    "codigo", "ACESSO_NEGADO",
                                    "mensagem", "O perfil do usuário atual não possui permissão para executar esta ação.")));
                        })
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new GrantedAuthoritiesExtractor());
        return converter;
    }

    /** Mesma lógica de extração de `CurveApiSecurityConfig.GrantedAuthoritiesExtractor` — mesmo emissor JWT, mesmas claims. */
    public static class GrantedAuthoritiesExtractor implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new HashSet<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(role)));
                }
            }

            List<String> directRoles = jwt.getClaimAsStringList("roles");
            if (directRoles != null) {
                for (String role : directRoles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(role)));
                }
            }

            List<String> groups = jwt.getClaimAsStringList("groups");
            if (groups != null) {
                for (String group : groups) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(group)));
                }
            }

            if (authorities.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"));
            }

            return authorities;
        }

        private String normalizeRole(String rawRole) {
            String clean = rawRole.toUpperCase().replace("ROLE_", "");
            if ("VIEWER".equals(clean) || "LEITOR".equals(clean)) return "CURVE_VIEWER";
            if ("OPERATOR".equals(clean) || "OPERADOR".equals(clean)) return "CURVE_OPERATOR";
            if ("ADMIN".equals(clean) || "ADMINISTRADOR".equals(clean)) return "CURVE_ADMIN";
            return clean;
        }
    }
}
