package com.dsh.stepcounter;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Периодический фоновый замер датчика.
 *
 * Зачем: шаги за день считаются не по «одному числу», а по разнице между
 * замерами в журнале. Если замеров мало, вся пачка шагов делится между днями
 * пропорционально времени — и утренние шаги уезжают во вчерашний день.
 * Отсюда были жалобы «у вас на тысячи меньше, чем в Google Fit».
 *
 * Замер раз в 15 минут делает разбивку точной (ошибка — минуты, а не часы)
 * и не требует постоянного уведомления, в отличие от foreground-службы.
 */
public class SyncJobService extends JobService {

    static final int JOB_ID = 7311;
    private static final long PERIOD_MS = 15 * 60 * 1000L;

    @Override
    public boolean onStartJob(JobParameters params) {
        final JobParameters p = params;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    SensorSync.sync(SyncJobService.this);
                    StepWidget.refresh(SyncJobService.this);
                    ok = true;
                } catch (Exception ignored) {
                } finally {
                    jobFinished(p, !ok);
                }
            }
        }, "sync-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // перезапустить, если система прервала
    }

    /** Поставить задание в очередь. Вызывается при каждом запуске приложения и после загрузки. */
    static void schedule(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            if (js.getPendingJob(JOB_ID) != null) return;

            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, SyncJobService.class))
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            js.schedule(job);
        } catch (Exception ignored) {
        }
    }
}
