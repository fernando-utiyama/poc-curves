package com.poccurves.orchestrator.adapter.out.scheduling;
import com.poccurves.orchestrator.application.model.Agendamento;
import com.poccurves.orchestrator.application.model.Faixa;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.usecase.DisparoAgendadoExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgendamentoSchedulerRegistryTest {

    private ThreadPoolTaskScheduler taskScheduler;
    private DisparoAgendadoExecutor executor;
    private AgendamentoSchedulerRegistry registry;

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        executor = mock(DisparoAgendadoExecutor.class);
        registry = new AgendamentoSchedulerRegistry(taskScheduler, executor);
    }

    @AfterEach
    void tearDown() {
        taskScheduler.shutdown();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, ?> getMap(AgendamentoSchedulerRegistry reg) throws Exception {
        Field f = AgendamentoSchedulerRegistry.class.getDeclaredField("agendamentos");
        f.setAccessible(true);
        return (ConcurrentHashMap<UUID, ?>) f.get(reg);
    }

    @Test
    void registrarDuasVezesComOMesmoIdSoAgendaUmaVez() throws Exception {
        Agendamento a = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );

        registry.registrar(a);
        ConcurrentHashMap<UUID, ?> map1 = getMap(registry);
        assertThat(map1).hasSize(1);
        Object future1 = map1.get(a.id());

        registry.registrar(a); // Registra de novo
        ConcurrentHashMap<UUID, ?> map2 = getMap(registry);
        assertThat(map2).hasSize(1);
        assertThat(map2.get(a.id())).isSameAs(future1);
    }

    @Test
    void cancelarRemoveDoMap() throws Exception {
        Agendamento a = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        registry.registrar(a);
        assertThat(getMap(registry)).hasSize(1);

        registry.cancelar(a.id());
        assertThat(getMap(registry)).isEmpty();
    }

    @Test
    void reagendarCancelaOAntigoERegistraONovo() throws Exception {
        Agendamento a = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        registry.registrar(a);
        Object future1 = getMap(registry).get(a.id());

        a.editar("0 0 13 * * ?", "America/Sao_Paulo", 30, 60, Faixa.ROTINA);
        registry.reagendar(a);

        Object future2 = getMap(registry).get(a.id());
        assertThat(future1).isNotSameAs(future2);
        assertThat(getMap(registry)).hasSize(1);
    }

    @Test
    void reconciliarRegistraCancelaEReagendaConformeOCatalogo() throws Exception {
        Agendamento mantidoSemMudanca = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        Agendamento seraDesativado = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 13 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        Agendamento teraHorarioAlterado = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 14 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );

        registry.registrar(mantidoSemMudanca);
        registry.registrar(seraDesativado);
        registry.registrar(teraHorarioAlterado);
        assertThat(getMap(registry)).hasSize(3);
        Object futureMantido = getMap(registry).get(mantidoSemMudanca.id());
        Object futureAlterado = getMap(registry).get(teraHorarioAlterado.id());

        Agendamento novoNaProximaReconciliacao = Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 15 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        teraHorarioAlterado.editar("0 30 14 * * ?", "America/Sao_Paulo", 30, 60, Faixa.ROTINA);

        registry.reconciliar(List.of(mantidoSemMudanca, teraHorarioAlterado, novoNaProximaReconciliacao));

        ConcurrentHashMap<UUID, ?> mapFinal = getMap(registry);
        assertThat(mapFinal).hasSize(3);
        assertThat(mapFinal).doesNotContainKey(seraDesativado.id());
        assertThat(mapFinal.get(mantidoSemMudanca.id())).isSameAs(futureMantido);
        assertThat(mapFinal.get(teraHorarioAlterado.id())).isNotSameAs(futureAlterado);
        assertThat(mapFinal).containsKey(novoNaProximaReconciliacao.id());
    }
}
