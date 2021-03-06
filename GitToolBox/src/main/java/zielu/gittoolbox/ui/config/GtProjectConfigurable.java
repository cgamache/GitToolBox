package zielu.gittoolbox.ui.config;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zielu.gittoolbox.GitToolBoxConfigForProject;
import zielu.gittoolbox.ResBundle;

public class GtProjectConfigurable extends GtConfigurableBase<GtPrjForm, GitToolBoxConfigForProject> implements SearchableConfigurable {

    private final Project myProject;

    public GtProjectConfigurable(@NotNull Project project) {
        myProject = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return ResBundle.getString("configurable.prj.displayName");
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Override
    protected GtPrjForm createForm() {
        return new GtPrjForm();
    }

    @Override
    protected GitToolBoxConfigForProject getConfig() {
        return GitToolBoxConfigForProject.getInstance(myProject);
    }

    @Override
    protected void setFormState(GtPrjForm form, GitToolBoxConfigForProject config) {
        form.setAutoFetchEnabled(config.autoFetch);
        form.setAutoFetchInterval(config.autoFetchIntervalMinutes);
    }

    @Override
    protected boolean checkModified(GtPrjForm form, GitToolBoxConfigForProject config) {
        boolean modified = config.isAutoFetchChanged(form.getAutoFetchEnabled());
        modified = modified || config.isAutoFetchIntervalMinutesChanged(form.getAutoFetchInterval());
        return modified;
    }

    @Override
    protected void doApply(GtPrjForm form, GitToolBoxConfigForProject config) throws ConfigurationException {
        config.autoFetch = form.getAutoFetchEnabled();
        config.autoFetchIntervalMinutes = form.getAutoFetchInterval();
        config.fireChanged(myProject);
    }

    @NotNull
    @Override
    public String getId() {
        return "zielu.svntoolbox.prj.config";
    }

    @Nullable
    @Override
    public Runnable enableSearch(String option) {
        return null;
    }
}
