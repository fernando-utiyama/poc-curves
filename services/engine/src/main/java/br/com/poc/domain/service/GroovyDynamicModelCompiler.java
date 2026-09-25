package br.com.poc.domain.service;

import br.com.poc.domain.strategy.CurveBuilderStrategy;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public class GroovyDynamicModelCompiler {

    public record CompiledModelResult(
        Class<?> compiledClass,
        CurveBuilderStrategy instance,
        String sha256Hash
    ) {}

    public CompiledModelResult compileAndInstantiate(String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank()) {
            throw new IllegalArgumentException("Conteúdo do script Groovy não pode ser nulo ou vazio");
        }

        // 1. Sandbox de segurança com SecureASTCustomizer
        SecureASTCustomizer secureAST = new SecureASTCustomizer();
        secureAST.setClosuresAllowed(true);
        secureAST.setMethodDefinitionAllowed(true);
        secureAST.setPackageAllowed(true);

        // Bloquear chamadas reflexivas e de sistema que violem RN-05
        secureAST.setReceiversBlackList(List.of(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.URL"
        ));

        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(secureAST);

        try (GroovyClassLoader gcl = new GroovyClassLoader(this.getClass().getClassLoader(), config)) {
            Class<?> clazz = gcl.parseClass(scriptContent);

            if (!CurveBuilderStrategy.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("A classe compilada do script Groovy deve implementar a interface CurveBuilderStrategy");
            }

            Object instanceObj = clazz.getDeclaredConstructor().newInstance();
            CurveBuilderStrategy instance = (CurveBuilderStrategy) instanceObj;

            String hash = calculateSha256(scriptContent);

            return new CompiledModelResult(clazz, instance, hash);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            // Se a causa raiz for SecurityException (gerada pelo SecureASTCustomizer durante a compilação)
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SecurityException se) {
                    throw se;
                }
                cause = cause.getCause();
            }

            if (e.getMessage() != null && e.getMessage().contains("Method calls not allowed on [java.lang.System]")) {
                throw new SecurityException("Violação de segurança: uso de classes de sistema bloqueadas", e);
            }
            throw new IllegalArgumentException("Erro ao compilar ou instanciar script Groovy: " + e.getMessage(), e);
        }
    }

    private String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash-unavailable";
        }
    }
}
