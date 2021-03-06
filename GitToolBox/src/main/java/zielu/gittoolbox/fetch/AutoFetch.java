package zielu.gittoolbox.fetch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbService.DumbModeListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import zielu.gittoolbox.ConfigNotifier;
import zielu.gittoolbox.GitToolBoxApp;
import zielu.gittoolbox.GitToolBoxConfigForProject;
import zielu.gittoolbox.ProjectAware;

public class AutoFetch implements Disposable, ProjectAware {
    private final Logger LOG = Logger.getInstance(getClass());

    private final AtomicLong myLastAutoFetch = new AtomicLong();
    private final AtomicBoolean myActive = new AtomicBoolean();
    private final Project myProject;
    private final MessageBusConnection myConnection;
    private ScheduledExecutorService myExecutor;
    private ScheduledFuture<?> myScheduledTask;
    private int currentInterval;

    private AutoFetch(Project project) {
        myProject = project;
        myConnection = myProject.getMessageBus().connect();
        myConnection.subscribe(ConfigNotifier.CONFIG_TOPIC, new ConfigNotifier.Adapter() {
            @Override
            public void configChanged(Project project, GitToolBoxConfigForProject config) {
                onConfigChange(config);
            }
        });
        myConnection.subscribe(DumbService.DUMB_MODE, new DumbModeListener() {
            @Override
            public void enteredDumbMode() {
                LOG.debug("Enter dumb mode");
                cancelCurrentTask();
            }

            @Override
            public void exitDumbMode() {
                LOG.debug("Exit dumb mode");
                if (myActive.get()) {
                    init();
                }
            }
        });
    }

    private void init() {
        GitToolBoxConfigForProject config = GitToolBoxConfigForProject.getInstance(project());
        if (config.autoFetch) {
            synchronized (this) {
                if (myScheduledTask == null) {
                    currentInterval = config.autoFetchIntervalMinutes;
                    myScheduledTask = scheduleFastTask(config.autoFetchIntervalMinutes);
                } else {
                    LOG.debug("Task already scheduled");
                }
            }
        }
    }

    private void cancelCurrentTask() {
        synchronized (this) {
            if (myScheduledTask != null) {
                LOG.debug("Existing task cancelled");
                myScheduledTask.cancel(false);
                myScheduledTask = null;
            }
        }
    }

    private void onConfigChange(GitToolBoxConfigForProject config) {
        if (config.autoFetch) {
            LOG.debug("Auto-fetch enabled");
            synchronized (this) {
                if (currentInterval != config.autoFetchIntervalMinutes) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Auto-fetch interval or state changed: enabled="
                            + config.autoFetch + ", interval=" + config.autoFetchIntervalMinutes);
                    }

                    cancelCurrentTask();
                    LOG.debug("Existing task cancelled on auto-fetch change");
                    if (currentInterval == 0) {
                        //enable
                        myScheduledTask = scheduleFastTask(config.autoFetchIntervalMinutes);
                    } else {
                        myScheduledTask = scheduleTask(config.autoFetchIntervalMinutes);
                    }
                    currentInterval = config.autoFetchIntervalMinutes;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Auto-fetch interval and state did not change: enabled="
                            + config.autoFetch + ", interval=" + config.autoFetchIntervalMinutes);
                    }
                }
            }
        } else {
            LOG.debug("Auto-fetch disabled");
            synchronized (this) {
                cancelCurrentTask();
                currentInterval = 0;
                LOG.debug("Existing task cancelled on auto-fetch disable");
            }
        }
    }

    private ScheduledFuture<?> scheduleFastTask(long intervalMinutes) {
        LOG.debug("Scheduling fast auto-fetch");
        return myExecutor.scheduleWithFixedDelay(AutoFetchTask.create(this),
            30,
            TimeUnit.MINUTES.toSeconds(intervalMinutes),
            TimeUnit.SECONDS);
    }

    private ScheduledFuture<?> scheduleTask(long intervalMinutes) {
        LOG.debug("Scheduling regular auto-fetch");
        return myExecutor.scheduleWithFixedDelay(AutoFetchTask.create(this),
            intervalMinutes,
            intervalMinutes,
            TimeUnit.MINUTES);
    }

    public static AutoFetch create(Project project) {
        return new AutoFetch(project);
    }

    public Project project() {
        return myProject;
    }

    public boolean isActive() {
        return myActive.get();
    }

    public void updateLastAutoFetchDate() {
        myLastAutoFetch.set(System.currentTimeMillis());
    }

    public long lastAutoFetch() {
        return myLastAutoFetch.get();
    }

    @Override
    public void dispose() {
    }

    @Override
    public void opened() {
        if (myActive.compareAndSet(false, true)) {
            myExecutor = GitToolBoxApp.getInstance().autoFetchExecutor();
            init();
        }
    }

    @Override
    public void closed() {
        if (myActive.compareAndSet(true, false)) {
            myConnection.disconnect();
            cancelCurrentTask();
        }
    }
}
