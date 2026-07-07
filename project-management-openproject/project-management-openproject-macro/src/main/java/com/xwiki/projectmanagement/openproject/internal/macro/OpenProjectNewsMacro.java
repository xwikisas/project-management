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
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.HorizontalLineBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectNewsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.News;

/**
 * OpenProject macro that retrieves and displays news from a specific OpenProject instance.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Singleton
@Named("openproject-news")
public class OpenProjectNewsMacro extends AbstractOpenProjectDirectMacro<OpenProjectNewsMacroParameters>
{
    private static final String CLASS = "class";

    private static final String TEXT_MUTED_CLASS = "text-muted";

    private static final String PROJECT_FILTER = "[{\"project_id\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]";

    @Inject
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    /**
     * Default constructor.
     */
    public OpenProjectNewsMacro()
    {
        super("OpenProject - Project News",
            "Retrieves and displays the latest news from a specific project on a configured OpenProject instance.",
            OpenProjectNewsMacroParameters.class);
    }

    @Override
    protected List<Block> executeInternal(OpenProjectNewsMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        List<News> newsList = fetchNews(apiClient, parameters.getProject(), parameters.getCount());
        return Collections.singletonList(new GroupBlock(buildNewsBlocks(newsList), Collections.emptyMap()));
    }

    private List<News> fetchNews(OpenProjectApiClient apiClient, String project, int count)
        throws MacroExecutionException
    {
        String filters = "";
        if (project != null && !project.isBlank()) {
            filters = String.format(PROJECT_FILTER, project);
        }

        try {
            return apiClient.getNews(null, count, filters).getItems();
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException("Failed to retrieve news from OpenProject.", e);
        }
    }

    private List<Block> buildNewsBlocks(List<News> newsList)
    {
        List<Block> blocks = new ArrayList<>();

        if (newsList.isEmpty()) {
            blocks.add(new ParagraphBlock(List.of(new WordBlock("No news found.")),
                Collections.singletonMap(CLASS, TEXT_MUTED_CLASS)));
            return blocks;
        }

        for (int index = 0; index < newsList.size(); index++) {
            if (index > 0) {
                blocks.add(new HorizontalLineBlock());
            }
            blocks.add(buildNewsItemBlock(newsList.get(index)));
        }

        return blocks;
    }

    private Block buildNewsItemBlock(News news)
    {
        List<Block> itemBlocks = new ArrayList<>();
        itemBlocks.add(buildHeaderBlock(news));
        itemBlocks.add(buildAuthorAndDateBlock(news));
        itemBlocks.add(addSummaryBlock(news));
        return new GroupBlock(itemBlocks, Collections.emptyMap());
    }

    private Block buildHeaderBlock(News news)
    {
        Block newsTitleBlock = buildLinkBlock(news.getTitle(), news.getSelf().getLocation());
        Linkable projectLink = news.getProjectLink();
        if (projectLink == null || projectLink.getValue().isBlank()) {
            return new HeaderBlock(List.of(newsTitleBlock), HeaderLevel.LEVEL3);
        }
        Block projectReferenceBlock = buildLinkBlock(projectLink.getValue(), projectLink.getLocation());
        return new HeaderBlock(List.of(projectReferenceBlock, new WordBlock(": "), newsTitleBlock),
            HeaderLevel.LEVEL3);
    }

    private Block buildAuthorAndDateBlock(News news)
    {
        String dateFormat = wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy");
        String dateStr = new SimpleDateFormat(dateFormat).format(news.getCreatedAt());

        return new ParagraphBlock(List.of(buildAuthorLinkBlock(news.getAuthor()), new WordBlock(" · " + dateStr)),
            Collections.singletonMap(CLASS, TEXT_MUTED_CLASS));
    }

    private Block buildAuthorLinkBlock(Linkable author)
    {
        return buildLinkBlock(author.getValue(), author.getLocation());
    }

    private Block buildLinkBlock(String label, String url)
    {
        ResourceReference ref = new ResourceReference(url, ResourceType.URL);
        return new LinkBlock(List.of(new WordBlock(label)), ref, true);
    }

    private Block addSummaryBlock(News news)
    {
        String summary = news.getSummary();
        return new ParagraphBlock(List.of(new WordBlock(summary)));
    }
}
