package com.poccurves.processor.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser do cadastro de instrumentos da B3 (schema {@code urn:bvmf.100.02.xsd},
 * mensagem {@code BVBG.028.02} — verificado em arquivo real de produção
 * fornecido pelo usuário, capturado em 2026-08-22).
 * <p>
 * Este dataset é cadastro, não cotação: não tem um campo de "valor de
 * mercado" natural. Escopo desta versão: para instrumentos futuros
 * ({@code InstrmInf > FutrCtrctsInf}, ex. DI1), extrai {@code WrkgDays}
 * (dias úteis até o vencimento, já calculado pela própria B3 na data do
 * relatório) como {@code valor}, com {@code tipoCotacao =
 * "DIAS_UTEIS_VENCIMENTO"} e {@code dataVencimento} preenchido a partir de
 * {@code XprtnDt} — reaproveita um campo real do cadastro em vez de o
 * processor recalcular a data de vencimento por código de mês (regra de
 * calendário B3 não implementada em Java nesta versão). Outros tipos de
 * instrumento (ex. opções, ações — sub-estruturas ainda não verificadas
 * contra arquivo real) são ignorados silenciosamente, não é erro.
 */
public final class Bvbg028CadastroParser implements DatasetParser {

    public static final String TIPO_COTACAO_DIAS_UTEIS_VENCIMENTO = "DIAS_UTEIS_VENCIMENTO";

    @Override
    public String dataset() {
        return "BVBG.028";
    }

    @Override
    public ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate) {
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return new ParseResult.Falha("encoding não suportado: " + encoding, e.toString());
        }

        String textoCompleto = new String(conteudo, charset);
        List<String> fragmentos;
        try {
            fragmentos = ElementosXmlRepetidos.extrair(textoCompleto, "BizGrp");
        } catch (IllegalArgumentException e) {
            return new ParseResult.Falha("XML malformado", e.getMessage());
        }
        if (fragmentos.isEmpty()) {
            return new ParseResult.Falha("nenhum elemento <BizGrp> encontrado no bloco", "");
        }

        List<PontoDadoMercado> pontos = new ArrayList<>();
        DocumentBuilderFactory factory = criarFactory();

        for (String fragmento : fragmentos) {
            Element instrm = extrairInstrm(fragmento, factory);
            if (instrm == null) {
                continue;
            }
            PontoDadoMercado ponto = extrairPonto(instrm);
            if (ponto != null) {
                pontos.add(ponto);
            }
        }

        return new ParseResult.Sucesso(pontos);
    }

    private Element extrairInstrm(String fragmentoBizGrp, DocumentBuilderFactory factory) {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream in = new ByteArrayInputStream(fragmentoBizGrp.getBytes(StandardCharsets.UTF_8))) {
                Document doc = builder.parse(in);
                Element bizGrp = doc.getDocumentElement();
                Element documento = NavegacaoDom.filhoDireto(bizGrp, "Document");
                return NavegacaoDom.filhoDireto(documento, "Instrm");
            }
        } catch (Exception e) {
            return null;
        }
    }

    private PontoDadoMercado extrairPonto(Element instrm) {
        Element futrCtrctsInf = NavegacaoDom.caminho(instrm, "InstrmInf", "FutrCtrctsInf");
        if (futrCtrctsInf == null) {
            // Não é um futuro (ex. opção sobre ação) — fora do escopo desta versão.
            return null;
        }

        String ticker = NavegacaoDom.texto(NavegacaoDom.filhoDireto(futrCtrctsInf, "TckrSymb"));
        if (ticker == null || ticker.isBlank()) {
            return null;
        }

        String dataReferenciaTexto = NavegacaoDom.texto(
                NavegacaoDom.caminho(instrm, "RptParams", "RptDtAndTm", "Dt"));
        String vencimentoTexto = NavegacaoDom.texto(NavegacaoDom.filhoDireto(futrCtrctsInf, "XprtnDt"));
        String diasUteisTexto = NavegacaoDom.texto(NavegacaoDom.filhoDireto(futrCtrctsInf, "WrkgDays"));

        if (dataReferenciaTexto == null || vencimentoTexto == null || diasUteisTexto == null) {
            return null;
        }

        LocalDate dataReferencia;
        LocalDate dataVencimento;
        try {
            dataReferencia = LocalDate.parse(dataReferenciaTexto);
            dataVencimento = LocalDate.parse(vencimentoTexto);
        } catch (DateTimeParseException e) {
            return null;
        }

        BigDecimal diasUteis;
        try {
            diasUteis = ConversorDecimal.paraBigDecimal(diasUteisTexto, '.');
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new PontoDadoMercado(
                "B3",
                dataset(),
                dataReferencia,
                ticker,
                diasUteis,
                TIPO_COTACAO_DIAS_UTEIS_VENCIMENTO,
                dataVencimento
        );
    }

    private static DocumentBuilderFactory criarFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IllegalStateException("falha ao configurar parser XML seguro", e);
        }
        return factory;
    }
}
