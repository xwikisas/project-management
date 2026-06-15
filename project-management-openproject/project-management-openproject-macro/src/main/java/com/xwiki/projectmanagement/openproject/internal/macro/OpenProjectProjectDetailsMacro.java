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
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectDetailsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

/**
 * OpenProject macro that retrieves and displays the details of a specific project.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Singleton
@Named("openproject-project-details")
public class OpenProjectProjectDetailsMacro
    extends AbstractOpenProjectDirectMacro<OpenProjectProjectDetailsMacroParameters>
{
    private static final String NAME = "name";

    private static final String ID = "ID";

    private static final String IDENTIFIER = "Identifier";

    private static final String DESCRIPTION = "Description";

    private static final String STATUS = "Status";

    private static final String CREATED = "Created";

    @Inject
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    /**
     * Default constructor.
     */
    public OpenProjectProjectDetailsMacro()
    {
        super("OpenProject - Project Details",
            "Displays the details of a specific project from a configured OpenProject instance.",
            OpenProjectProjectDetailsMacroParameters.class);
    }

    @Override
    protected List<Block> executeInternal(OpenProjectProjectDetailsMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        Project project = fetchProject(apiClient, Integer.valueOf(parameters.getProject()));
        return Collections.singletonList(
            new GroupBlock(buildProjectDetailsBlocks(project), Collections.emptyMap()));
    }

    private Project fetchProject(OpenProjectApiClient apiClient, Integer projectId) throws MacroExecutionException
    {
        try {
            return apiClient.getProject(projectId);
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException(
                String.format("Failed to retrieve project [%s] from OpenProject.", projectId), e);
        }
    }

    private List<Block> buildProjectDetailsBlocks(Project project)
    {
        List<Block> blocks = new ArrayList<>();

        addStringBlock(blocks, ID, String.valueOf(project.getId()));
        addNameBlock(blocks, project);
        addStringBlock(blocks, IDENTIFIER, project.getIdentifier());
        addStringBlock(blocks, DESCRIPTION, project.getDescription());
        addStringBlock(blocks, STATUS, project.getStatus().getValue());
        addCreatedBlock(blocks, project);

        return blocks;
    }

    private void addNameBlock(List<Block> blocks, Project project)
    {
        Linkable self = project.getSelf();
        if (self != null && !self.getValue().isBlank()) {
            blocks.add(buildLabelValueBlock(NAME, List.of(buildLinkBlock(self.getValue(), self.getLocation()))));
        }
    }

    private void addStringBlock(List<Block> blocks, String label, String value)
    {
        if (value != null && !value.isBlank()) {
            blocks.add(buildLabelValueBlock(label, List.of(new WordBlock(value))));
        }
    }

    private void addCreatedBlock(List<Block> blocks, Project project)
    {
        if (project.getCreatedAt() != null) {
            String dateFormat = wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy");
            String dateStr = new SimpleDateFormat(dateFormat).format(project.getCreatedAt());
            blocks.add(buildLabelValueBlock(CREATED, List.of(new WordBlock(dateStr))));
        }
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
