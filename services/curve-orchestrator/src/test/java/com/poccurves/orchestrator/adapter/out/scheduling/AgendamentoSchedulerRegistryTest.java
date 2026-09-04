package com.poccurves.orchestrator.adapter.out.scheduling;

import com.poccurves.orchestrator.application.DisparoAgendadoExecutor;
import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Field;
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
}
