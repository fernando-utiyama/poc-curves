/**
 * Tamanho de bloco padrão, calibrado com arquivos reais da B3 (tarefa 6.7.7),
 * não estimado — respondendo à pergunta aberta em
 * openspec/changes/curves-solution-architecture/design.md ("Qual o tamanho
 * de bloco adequado para os arquivos reais da B3? Precisa ser medido, não
 * estimado.").
 *
 * Medição feita em 2026-08-22 sobre o pregão de 2026-08-21, a partir do
 * arquivo real fornecido pelo usuário (fora do controle de versão):
 *   - PR260821.zip > BVBG.086.01_BV000328...840237278.xml:
 *     175.506.347 bytes, 76.015 elementos <BizGrp> => ~2.309 bytes/elemento
 *   - IN260821.zip > BVBG.028.02_BV000327...117330691294583.xml:
 *     800.282.039 bytes, 223.700 elementos <BizGrp> => ~3.577 bytes/elemento
 *
 * O Kafka local (deploy/podman/compose.core.yaml) não sobrescreve
 * `message.max.bytes`, então vale o padrão do broker: 1.048.576 bytes (1 MiB)
 * por mensagem. O bloco de elementos XML vira uma string dentro do campo
 * `payload` do envelope JSON — escapar aspas e caracteres de controle do XML
 * para JSON soma overhead; reservamos 30% de margem para esse escaping mais
 * os demais campos do envelope (eventId, correlationId, timestamps etc.).
 *
 * Cálculo: (1.048.576 * 0.7) / 3.577 bytes/elemento (o pior caso medido,
 * IN/BVBG.028) ≈ 205 elementos. Arredondado para baixo para manter margem de
 * segurança adicional sobre elementos individuais maiores que a média.
 */
export const TAMANHO_BLOCO_PADRAO = 150;
