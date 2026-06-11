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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
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
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectDetailsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

/**
 * OpenProject macro that retrieves and displays the details of a specific project.
 *
 * @version $Id$
 * @since 1.3
 */
@Component
@Singleton
@Named("openproject-project-details")
public class OpenProjectProjectDetailsMacro extends AbstractMacro<OpenProjectProjectDetailsMacroParameters>
{
    private static final String DATE_FORMAT = "MMM d, yyyy";

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
    public OpenProjectProjectDetailsMacro()
    {
        super("OpenProject - Project Details",
            "Displays the details of a specific project from a configured OpenProject instance.",
            null, OpenProjectProjectDetailsMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(OpenProjectProjectDetailsMacroParameters parameters, String content,
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

        Project project = fetchProject(instanceToUse, Integer.valueOf(parameters.getProject()));
        return Collections.singletonList(
            new GroupBlock(buildProjectBlocks(project),
                Collections.emptyMap()
            ));
    }

    private Project fetchProject(String instance, Integer projectId) throws MacroExecutionException
    {
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        if (apiClient == null) {
            throw new MacroExecutionException(
                String.format("No OpenProject connection found for instance [%s].", instance));
        }

        try {
            return apiClient.getProject(projectId);
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException(
                String.format("Failed to retrieve project [%s] from OpenProject.", projectId), e);
        }
    }

    private List<Block> buildProjectBlocks(Project project)
    {
        List<Block> blocks = new ArrayList<>();

        Linkable self = project.getSelf();
        if (self != null && !self.getValue().isBlank()) {
            blocks.add(new HeaderBlock(
                List.of(buildLinkBlock(self.getValue(), self.getLocation())),
                HeaderLevel.LEVEL2
            ));
        }

        blocks.addAll(buildProjectDetailBlocks(project));

        return blocks;
    }

    private List<Block> buildProjectDetailBlocks(Project project)
    {
        List<Block> blocks = new ArrayList<>();

        if (project.getId() != null) {
            blocks.add(buildLabelValueBlock("ID", List.of(new WordBlock(String.valueOf(project.getId())))));
        }

        String identifier = project.getIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            blocks.add(buildLabelValueBlock("Identifier", List.of(new WordBlock(identifier))));
        }

        String description = project.getDescription();
        if (description != null && !description.isBlank()) {
            blocks.add(buildLabelValueBlock("Description", List.of(new WordBlock(description))));
        }

        Linkable status = project.getStatus();
        if (status != null && !status.getValue().isBlank()) {
            blocks.add(buildLabelValueBlock("Status",
                List.of(buildLinkBlock(status.getValue(), status.getLocation()))));
        }

        if (project.getCreatedAt() != null) {
            String dateStr = new SimpleDateFormat(DATE_FORMAT).format(project.getCreatedAt());
            blocks.add(buildLabelValueBlock("Created", List.of(new WordBlock(dateStr))));
        }

        return blocks;
    }

    private Block buildLabelValueBlock(String label, List<Block> valueBlocks)
    {
        List<Block> content = new ArrayList<>();
        content.add(new FormatBlock(List.of(new WordBlock(label + ": ")), Format.BOLD));
        content.addAll(valueBlocks);
        return new ParagraphBlock(content);
    }

    private Block buildLinkBlock(String label, String url)
    {
        ResourceReference ref = new ResourceReference(url, ResourceType.URL);
        return new LinkBlock(List.of(new WordBlock(label)), ref, true);
    }
}
