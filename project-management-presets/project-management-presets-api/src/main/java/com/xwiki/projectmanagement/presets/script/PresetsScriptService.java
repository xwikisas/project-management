package com.xwiki.projectmanagement.presets.script;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.presets.PresetsManager;
import com.xwiki.projectmanagement.script.ProjectManagementScriptService;

/**
 * Script Service exposing utility methods for handling Filter Presets.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Component
@Named(ProjectManagementScriptService.ROLE_HINT + "." + PresetsScriptService.ROLE_HINT)
@Singleton
public class PresetsScriptService implements ScriptService
{
    /**
     * The HINT for this script service.
     */
    public static final String ROLE_HINT = "presets";

    @Inject
    private PresetsManager manager;

    /**
     * @return the next
     */
    public int getNextPresetId()
    {
        return manager.getNextId();
    }
}
