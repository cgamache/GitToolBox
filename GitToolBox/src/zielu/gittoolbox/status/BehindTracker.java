package zielu.gittoolbox.status;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.repo.GitRepository;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import zielu.gittoolbox.cache.PerRepoInfoCache;
import zielu.gittoolbox.cache.PerRepoStatusCacheListener;
import zielu.gittoolbox.cache.RepoInfo;
import zielu.gittoolbox.compat.Notifier;
import zielu.gittoolbox.ui.StatusMessages;
import zielu.gittoolbox.util.GtUtil;

public class BehindTracker extends AbstractProjectComponent {
    private final Logger LOG = Logger.getInstance(getClass());
    private final AtomicBoolean myActive = new AtomicBoolean();
    private final Map<GitRepository, RepoInfo> myState = new ConcurrentHashMap<GitRepository, RepoInfo>();

    private MessageBusConnection myConnection;

    public BehindTracker(Project project) {
        super(project);
    }

    public static BehindTracker getInstance(@NotNull Project project) {
        return project.getComponent(BehindTracker.class);
    }

    @Override
    public void initComponent() {
        myConnection = myProject.getMessageBus().connect();
        myConnection.subscribe(PerRepoInfoCache.CACHE_CHANGE, new PerRepoStatusCacheListener() {
            @Override
            public void stateChanged(@NotNull RepoInfo info,
                                     @NotNull GitRepository repository) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("State changed ["+GtUtil.name(repository)+"]: " + info);
                }
                if (myActive.get()) {
                    onStateChange(repository, info);
                }
            }
        });
    }

    private Optional<String> prepareMessage() {
        Map<GitRepository, RevListCount> statuses = Maps.newHashMap();
        for (Entry<GitRepository, RepoInfo> entry : myState.entrySet()) {
            RepoInfo value = entry.getValue();
            Optional<GitAheadBehindCount> count = value.count;
            if (count.isPresent() && count.get().status() == Status.Success) {
                statuses.put(entry.getKey(), count.get().behind);
            }
        }
        if (!statuses.isEmpty()) {
            return Optional.of(StatusMessages.getInstance().prepareBehindMessage(statuses));
        } else {
            return Optional.absent();
        }
    }

    private void showNotification() {
        Optional<String> message = prepareMessage();
        if (message.isPresent() && myActive.get()) {
            Notifier.getInstance(myProject).notifySuccess(message.get());
        }
    }

    private void onStateChange(@NotNull GitRepository repository, @NotNull RepoInfo info) {
        RepoInfo previousInfo = myState.put(repository, info);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Info update ["+GtUtil.name(repository)+"]: " + previousInfo + " > " + info);
        }
        if (previousInfo != null) {
            if (previousInfo.status.sameRemoteBranch(info.status) &&
                !previousInfo.status.sameRemoteHash(info.status)) {
                showNotification();
            }
        }
    }

    @Override
    public void disposeComponent() {

    }

    @Override
    public void projectOpened() {
        myActive.compareAndSet(false, true);
    }

    @Override
    public void projectClosed() {
        if (myActive.compareAndSet(true, false)) {
            myConnection.disconnect();
            myState.clear();
        }
    }
}