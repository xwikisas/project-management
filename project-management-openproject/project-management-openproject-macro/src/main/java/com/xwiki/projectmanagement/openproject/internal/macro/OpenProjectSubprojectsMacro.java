/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.BulletedListBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.internal.InstanceResolver;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSubprojectsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

/**
 * OpenProject macro that retrieves and displays the subprojects of a specific project.
 *
 * @version $Id$
 * @since 1.3
 */
@Component
@Singleton
@Named("openproject-subprojects")
public class OpenProjectSubprojectsMacro extends AbstractMacro<OpenProjectSubprojectsMacroParameters>
{
    private static final String CLASS = "class";

    private static final String OPENPROJECT_SUBPROJECTS_FILTER = "[{\"parent_id\":{\"operator\":\"=\","
        + "\"values\":[\"%s\"]}}]";

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private LicenseChecker licenseChecker;

    @Inject
    private InstanceResolver instanceResolver;

    /**
     * Default constructor.
     */
    public OpenProjectSubprojectsMacro()
    {
        super("OpenProject - Subprojects",
            "Displays the subprojects of a specific project from a configured OpenProject instance.",
            null, OpenProjectSubprojectsMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(OpenProjectSubprojectsMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        List<Block> licenseBlock = licenseChecker.getMissingLicenseBlock(context);
        if (!licenseBlock.isEmpty()) {
            return licenseBlock;
        }

        String instanceToUse = instanceResolver.resolve(parameters);

        List<Block> warningBlock = userTokenChecker.getWarningBlock(instanceToUse);
        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }

        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instanceToUse);
        if (apiClient == null) {
            throw new MacroExecutionException(
                String.format("No OpenProject connection found for instance [%s].", instanceToUse));
        }

        Integer projectId = Integer.valueOf(parameters.getProject());
        try {
            Project parentProject = apiClient.getProject(projectId);
            String filters =
                String.format(OPENPROJECT_SUBPROJECTS_FILTER, projectId);
            List<Project> subprojects = apiClient.getProjects(null, parameters.getCount(), filters).getItems();

            return Collections.singletonList(
                new GroupBlock(buildSubprojectsBlocks(parentProject, subprojects),
                    Collections.singletonMap(CLASS, "openproject-subprojects"))
            );
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException(
                String.format("Failed to retrieve subprojects for project [%s].", projectId), e);
        }
    }

    private List<Block> buildSubprojectsBlocks(Project parentProject, List<Project> subprojects)
    {
        List<Block> blocks = new ArrayList<>();

        Linkable parentSelf = parentProject.getSelf();
        String parentName = parentProject.getName();
        if (parentSelf != null && !parentSelf.getLocation().isBlank()) {
            blocks.add(new HeaderBlock(
                List.of(buildLinkBlock(parentName, parentSelf.getLocation())),
                HeaderLevel.LEVEL2
            ));
        } else {
            blocks.add(new HeaderBlock(List.of(new WordBlock(parentName)), HeaderLevel.LEVEL2));
        }

        if (subprojects.isEmpty()) {
            blocks.add(new ParagraphBlock(
                List.of(new WordBlock("No subprojects found.")),
                Collections.singletonMap(CLASS, "text-muted")
            ));
        } else {
            List<Block> items = new ArrayList<>();
            for (Project subproject : subprojects) {
                Linkable subSelf = subproject.getSelf();
                items.add(new ListItemBlock(
                    List.of(buildLinkBlock(subproject.getName(), subSelf.getLocation()))
                ));
            }
            blocks.add(new BulletedListBlock(items));
        }

        return blocks;
    }

    private Block buildLinkBlock(String label, String url)
    {
        ResourceReference ref = new ResourceReference(url, ResourceType.URL);
        return new LinkBlock(List.of(new WordBlock(label)), ref, true);
    }
}
