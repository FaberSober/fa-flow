package com.faber.api.flow.manage.config;

import com.aizuda.bpm.engine.FlowLongScheduler;
import com.faber.core.config.scheduler.SchedulerStartupGate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

public class FaFlowLongScheduler extends FlowLongScheduler implements SchedulingConfigurer {

    private final SchedulerStartupGate schedulerStartupGate;

    public FaFlowLongScheduler(SchedulerStartupGate schedulerStartupGate) {
        this.schedulerStartupGate = schedulerStartupGate;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(() -> {
            if (schedulerStartupGate.isReady()) {
                remind();
            }
        }, triggerContext ->
                new CronTrigger(getRemindParam().getCron()).nextExecution(triggerContext));
    }
}
