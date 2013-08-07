package org.fidonet.jftn.scheduler;

import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/6/13
 * Time: 2:48 PM
 */
public class Scheduler {

    private it.sauronsoftware.cron4j.Scheduler scheduler;

    public Scheduler() {
        scheduler = new it.sauronsoftware.cron4j.Scheduler();
    }

    public void start() {
        scheduler.start();
    }

    public void stop() {
        scheduler.stop();
    }

    public String schedule(String cronSchedule, Runnable task) {
        return scheduler.schedule(cronSchedule, task);
    }

    public void deschedule(String taskId) {
        scheduler.deschedule(taskId);
    }

    public void reschedule(String taskId, String cronTemplate) {
        scheduler.reschedule(taskId, cronTemplate);
    }

    public void launch(final Runnable runnable) {
        Task task = new Task() {
            @Override
            public void execute(TaskExecutionContext context) throws RuntimeException {
                runnable.run();
            }
        };
        scheduler.launch(task);
    }
}
