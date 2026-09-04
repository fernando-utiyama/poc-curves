package com.poccurves.api.config;

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
 * Fechado durante a auditoria desta sessão: o serviço não tinha nenhuma
 * dependência de segurança e aceitava POST/PUT em /curvas/definicoes sem
 * autenticação nenhuma — o cabeçalho `X-User` era só um rótulo opcional para
 * log de auditoria, não uma verificação de identidade ou permissão.
 * <p>
 * Espelha exatamente o padrão real e já testado de `BffSecurityConfig`
 * (services/curve-bff) — mesmo emissor Keycloak/realm `curvas`, mesmo
 * extrator de authorities a partir do JWT — porque curve-api valida o JWT
 * de forma independente (defesa em profundidade): o serviço expõe a porta
 * 8082 diretamente no `compose.yaml`, então não pode depender só do
 * curve-bff estar na frente para barrar acesso não autorizado.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class CurveApiSecurityConfig {

    private final ObjectMapper objectMapper;

    public CurveApiSecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/metrics").permitAll()

                        // Cadastro/atualização de definição de curva: só Administrador —
                        // mesma regra já declarada (mas nunca aplicada) no lado do curve-bff.
                        .requestMatchers(HttpMethod.POST, "/curvas/definicoes").hasRole("CURVE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/curvas/definicoes/*").hasRole("CURVE_ADMIN")

                        // Todo o resto (consultas, comparação, interpolação, modelo de carga)
                        // é leitura/cálculo — acessível a qualquer perfil autenticado.
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

    /** Mesma lógica de extração de `BffSecurityConfig.GrantedAuthoritiesExtractor` — mesmo emissor JWT, mesmas claims. */
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
