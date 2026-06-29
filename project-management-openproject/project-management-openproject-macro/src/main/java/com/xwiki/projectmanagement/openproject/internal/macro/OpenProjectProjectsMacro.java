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
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

/**
 * OpenProject macro that lists the latest 5 projects from a configured OpenProject instance, based on user's project
 * rights.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject-projects")
public class OpenProjectProjectsMacro extends AbstractOpenProjectDirectMacro<OpenProjectProjectsMacroParameters>
{
    private static final Integer MAX_PROJECTS = 5;

    private static final String CLASS = "class";

    /**
     * Default constructor.
     */
    public OpenProjectProjectsMacro()
    {
        super("Open Project - Projects",
            "List the 5 latest projects from a configured OpenProject instance, based on user's OpenProject instance "
                + "rights.",
            OpenProjectProjectsMacroParameters.class);
    }

    @Override
    protected List<Block> executeInternal(OpenProjectProjectsMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        List<Project> projects;
        try {
            projects = apiClient.getProjects(null, MAX_PROJECTS, "").getItems();
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException("Failed to retrieve projects from OpenProject.", e);
        }

        return Collections.singletonList(
            new GroupBlock(buildProjectsBlocks(projects), Collections.emptyMap()));
    }

    private List<Block> buildProjectsBlocks(List<Project> projects)
    {
        List<Block> blocks = new ArrayList<>();

        blocks.add(new HeaderBlock(List.of(new WordBlock("Projects")), HeaderLevel.LEVEL2));

        blocks.add(new ParagraphBlock(
            List.of(new WordBlock("Here are the latest projects you have access to in OpenProject:")),
            Collections.singletonMap(CLASS, "text-muted")));

        List<Block> listItems = new ArrayList<>();
        if (projects.isEmpty()) {
            listItems.add(new ListItemBlock(
                List.of(new WordBlock("No projects found.")),
                Collections.emptyMap()));
        } else {
            for (Project project : projects) {
                String name = project.getName();
                String href = project.getSelf() != null ? project.getSelf().getLocation() : "#";

                ResourceReference ref = new ResourceReference(href, ResourceType.URL);

                Block linkBlock = new LinkBlock(
                    List.of(new WordBlock(name)), ref, true);

                listItems.add(new ListItemBlock(
                    List.of(linkBlock),
                    Collections.emptyMap()));
            }
        }

        blocks.add(new BulletedListBlock(listItems, Collections.emptyMap()));
        return blocks;
    }
}
