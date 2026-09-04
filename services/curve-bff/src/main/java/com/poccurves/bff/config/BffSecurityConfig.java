package com.poccurves.bff.config;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.bff.dto.BffDtos.ErroResposta;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BffSecurityConfig {

    private final ObjectMapper objectMapper;

    // Origens permitidas configuráveis, nunca "*" — corrigido na auditoria
    // desta sessão: "*" combinado com allowCredentials(true) permitia
    // qualquer origem enviar credenciais, o que o próprio task 2.7 do
    // backlog já dizia estar restrito, mas não estava. Padrão cobre o
    // curve-web-ui local (porta 4200, ver deploy/podman/compose.yaml).
    @Value("${cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    public BffSecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos de infraestrutura e saúde
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/metrics").permitAll()

                        // Ações restritas a Administrador
                        .requestMatchers(HttpMethod.POST, "/api/v1/curvas/*/definicao").hasRole("CURVE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/curvas/*/definicao").hasRole("CURVE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/modelos/importar").hasRole("CURVE_ADMIN")
                        // Regra fantasma, encontrada na auditoria desta sessão: não existe
                        // nenhum controller/endpoint /api/v1/modelos/trocar — a troca de
                        // modelo de uma curva não foi implementada. Mantida (inofensiva,
                        // Spring nunca casa uma rota inexistente) para o dia em que a
                        // funcionalidade real for construída, mas NÃO é evidência de que
                        // ela existe — tasks.md desta mudança foi corrigido para não
                        // reivindicar isso como feito.
                        .requestMatchers(HttpMethod.POST, "/api/v1/modelos/trocar").hasRole("CURVE_ADMIN")

                        // Ações restritas a Operador ou Administrador
                        .requestMatchers(HttpMethod.POST, "/api/v1/acoes/disparo-manual").hasAnyRole("CURVE_OPERATOR", "CURVE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/acoes/backfill").hasAnyRole("CURVE_OPERATOR", "CURVE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/acoes/carga-manual").hasAnyRole("CURVE_OPERATOR", "CURVE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pendencias/dlq/reprocessar").hasAnyRole("CURVE_OPERATOR", "CURVE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pendencias/dlq/descartar").hasAnyRole("CURVE_OPERATOR", "CURVE_ADMIN")

                        // Consultas acessíveis a qualquer autenticado (Leitor, Operador, Administrador)
                        .requestMatchers("/api/v1/**").hasAnyRole("CURVE_VIEWER", "CURVE_OPERATOR", "CURVE_ADMIN")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ErroResposta erro = new ErroResposta(
                                    "NAO_AUTORIZADO",
                                    "Token de autenticação ausente, expirado ou inválido."
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(erro));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ErroResposta erro = new ErroResposta(
                                    "ACESSO_NEGADO",
                                    "O perfil do usuário atual não possui permissão para executar esta ação."
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(erro));
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

    public static class GrantedAuthoritiesExtractor implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new HashSet<>();

            // 1. Roles padrão de realm_access (Keycloak)
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(role)));
                }
            }

            // 2. Claim "roles" direta
            List<String> directRoles = jwt.getClaimAsStringList("roles");
            if (directRoles != null) {
                for (String role : directRoles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(role)));
                }
            }

            // 3. Claim "groups"
            List<String> groups = jwt.getClaimAsStringList("groups");
            if (groups != null) {
                for (String group : groups) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(group)));
                }
            }

            // Default: se autenticado mas sem perfil explícito, concede ROLE_CURVE_VIEWER
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Correlation-ID", "Content-Disposition"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
