package zielu.gittoolbox.status;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResultHandlerAdapter;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitStatusCalculator {
    private final Logger LOG = Logger.getInstance(getClass());

    private final Project myProject;
    private final ProgressIndicator myIndicator;

    private GitStatusCalculator(Project project, ProgressIndicator indicator) {
        myProject = Preconditions.checkNotNull(project);
        myIndicator = Preconditions.checkNotNull(indicator);
    }

    public static GitStatusCalculator create(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        return new GitStatusCalculator(project, indicator);
    }

    public static GitStatusCalculator create(@NotNull Project project) {
        return create(project, new EmptyProgressIndicator());
    }

    public Map<GitRepository, RevListCount> behindStatus(Collection<GitRepository> repositories) {
        Map<GitRepository, RevListCount> result = Maps.newLinkedHashMap();
        for (GitRepository repository : repositories) {
            result.put(repository, behindStatus(repository));
        }
        return result;
    }

    @NotNull
    public RevListCount behindStatus(GitRepository repository) {
        Optional<GitBranchTrackInfo> trackInfo = trackInfoForCurrentBranch(repository);
        if (trackInfo.isPresent()) {
            return behindStatus(repository.getCurrentBranch(), trackInfo.get(), repository);
        }
        return RevListCount.noRemote();
    }

    @NotNull
    public GitAheadBehindCount aheadBehindStatus(@NotNull GitRepository repository) {
        Optional<GitBranchTrackInfo> trackInfo = trackInfoForCurrentBranch(repository);
        if (trackInfo.isPresent()) {
            return aheadBehindStatus(repository.getCurrentBranch(), trackInfo.get(), repository);
        }
        return GitAheadBehindCount.noRemote();
    }

    public GitAheadBehindCount aheadBehindStatus(@NotNull GitRepository repository, @Nullable Hash localHash, @Nullable Hash remoteHash) {
        if (localHash != null && remoteHash != null) {
            return doRevListLeftRight(localHash.asString(), remoteHash.asString(), repository);
        } else {
            return GitAheadBehindCount.noRemote();
        }
    }

    private RevListCount behindStatus(GitLocalBranch currentBranch, GitBranchTrackInfo trackInfo, GitRepository repository) {
        String localName = currentBranch.getName();
        String remoteName = trackInfo.getRemoteBranch().getNameForLocalOperations();
        GitAheadBehindCount count = doRevListLeftRight(localName, remoteName, repository);
        return count.behind;
    }

    private Optional<GitBranchTrackInfo> trackInfoForCurrentBranch(GitRepository repository) {
        GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
        return Optional.ofNullable(trackInfo);
    }

    private GitAheadBehindCount aheadBehindStatus(
        GitLocalBranch localBranch, GitBranchTrackInfo trackInfo, GitRepository repository) {
        String localName = localBranch.getName();
        String remoteName = trackInfo.getRemoteBranch().getNameForLocalOperations();
        return doRevListLeftRight(localName, remoteName, repository);
    }

    @NotNull
    private GitAheadBehindCount doRevListLeftRight(String localRef, String remoteRef, GitRepository repository) {
        final boolean debug = LOG.isDebugEnabled();
        String branches = localRef + "..." + remoteRef;
        final GitLineHandler handler = new GitLineHandler(myProject, repository.getRoot(), GitCommand.REV_LIST);
        handler.addParameters(branches, "--left-right");
        final GitRevListLeftRightCounter counter = new GitRevListLeftRightCounter();
        handler.addLineListener(counter);
        GitTask task = new GitTask(myProject, handler, branches);
        task.setProgressIndicator(myIndicator);
        final AtomicReference<GitAheadBehindCount> result = new AtomicReference<GitAheadBehindCount>();
        if (debug) {
            LOG.debug("Executing count with refs: '" + branches + "'");
        }
        task.execute(true, false, new GitTaskResultHandlerAdapter() {
            @Override
            protected void onSuccess() {
                result.set(GitAheadBehindCount.success(counter.ahead(), counter.aheadTop(), counter.behind(), counter.behindTop()));
            }

            @Override
            protected void onCancel() {
                result.set(GitAheadBehindCount.cancel());
            }

            @Override
            protected void onFailure() {
                result.set(GitAheadBehindCount.failure());
            }
        });
        return Preconditions.checkNotNull(result.get(), "Null rev list left right");
    }
}
