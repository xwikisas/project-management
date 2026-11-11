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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
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

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectProjectDetailsProperty;
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
    private static final String TRANSLATION_PREFIX = "openproject.openproject-project-details.labels.";

    private static final String MISSING_PROJECT_HINT = "openproject.openproject-project-details.missingProject";

    private static final String NAME = "name";

    private static final String ID = "id";

    private static final String IDENTIFIER = "identifier";

    private static final String DESCRIPTION = "description";

    private static final String STATUS = "status";

    private static final String CREATED = "created";

    @Inject
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    @Inject
    @Named("openproject")
    private WorkItemPropertyDisplayerManager displayerManager;

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
    protected String getMissingParameterHint(OpenProjectProjectDetailsMacroParameters parameters)
    {
        return StringUtils.isBlank(parameters.getProject()) ? MISSING_PROJECT_HINT : null;
    }

    @Override
    protected List<Block> executeInternal(OpenProjectProjectDetailsMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        Project project = getProject(parameters.getProject(), apiClient);
        return Collections.singletonList(
            new GroupBlock(buildProjectDetailsBlocks(project, parameters.getProperties()), Collections.emptyMap()));
    }

    private List<Block> buildProjectDetailsBlocks(Project project, List<OpenProjectProjectDetailsProperty> properties)
    {
        List<Block> blocks = new ArrayList<>();
        // The properties are displayed in a fixed order, whatever the order in which they were selected.
        for (OpenProjectProjectDetailsProperty property : OpenProjectProjectDetailsProperty.values()) {
            if (properties != null && properties.contains(property)) {
                addPropertyBlocks(blocks, project, property);
            }
        }
        return blocks;
    }

    private void addPropertyBlocks(List<Block> blocks, Project project, OpenProjectProjectDetailsProperty property)
    {
        switch (property) {
            case ID:
                addStringBlock(blocks, ID, String.valueOf(project.getId()));
                break;
            case NAME:
                addNameBlock(blocks, project);
                break;
            case IDENTIFIER:
                addStringBlock(blocks, IDENTIFIER, project.getIdentifier());
                break;
            case DESCRIPTION:
                addDescriptionBlocks(blocks, project);
                break;
            case STATUS:
                addStringBlock(blocks, STATUS, project.getStatus() != null ? project.getStatus().getValue() : null);
                break;
            case CREATED:
            default:
                addCreatedBlock(blocks, project);
                break;
        }
    }

    private void addDescriptionBlocks(List<Block> blocks, Project project)
    {
        // The description is displayed from the HTML rendered by OpenProject, which the API client cleaned of the
        // unsafe content (scripts, event handlers, JavaScript links...), as the work package descriptions, since its
        // raw Markdown source holds HTML entities and Markdown syntax.
        String html = project.getDescriptionHtml();
        if (html == null || html.isBlank()) {
            return;
        }
        List<Block> description = this.displayerManager.displayProperty(WorkItem.KEY_DESCRIPTION, html, Map.of());
        if (!description.isEmpty()) {
            blocks.add(buildLabelValueBlock(DESCRIPTION, List.of()));
            blocks.add(new GroupBlock(description));
        }
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
        String translatedLabel = translate(TRANSLATION_PREFIX + label);
        content.add(new FormatBlock(List.of(new WordBlock(translatedLabel + ": ")), Format.BOLD));
        content.addAll(valueBlocks);
        return new ParagraphBlock(content);
    }

    private Block buildLinkBlock(String label, String url)
    {
        ResourceReference ref = new ResourceReference(url, ResourceType.URL);
        return new LinkBlock(List.of(new WordBlock(label)), ref, true);
    }
}
