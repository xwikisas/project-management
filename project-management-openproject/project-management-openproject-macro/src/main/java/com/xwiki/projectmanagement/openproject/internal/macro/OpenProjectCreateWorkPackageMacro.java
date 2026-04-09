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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCreateWorkPackageMacroParameters;

/**
 * OpenProject Create Work Package Macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject-create-work-package")
public class OpenProjectCreateWorkPackageMacro extends AbstractMacro<OpenProjectCreateWorkPackageMacroParameters>
{
    private static final String PROJECT = "project";

    private static final String ASSIGNEE = "assignee";

    private static final String TYPE = "type";

    private static final String STATUS = "status";

    private static final String PRIORITY = "priority";

    private static final String START_DATE = "start_date";

    private static final String DUE_DATE = "due_date";

    private static final String DESCRIPTION = "description";

    private static final String SUBJECT = "subject";

    private static final String LINKS = "_links";

    private static final String EMBEDDED = "_embedded";

    private static final String HREF = "href";

    private static final String VIEW_ACTION = "view";

    private static final String CONNECTION = "connection";

    private static final String INSTANCE = "instance";

    private static final String IDENTIFIER = "identifier";

    private static final String WORK_ITEMS_DISPLAYER = "workItemsDisplayer";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    /**
     * Default constructor.
     */
    public OpenProjectCreateWorkPackageMacro()
    {
        super("Open Project Create Work Package", "Allows to create a work package in Open Project from XWiki.",
            OpenProjectCreateWorkPackageMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(OpenProjectCreateWorkPackageMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        XWikiContext xContext = this.xContextProvider.get();

        if (!xContext.getAction().equals(VIEW_ACTION)) {
            return List.of();
        }

        return createAndReplaceWithDisplayMacro(parameters, context);
    }

    private List<Block> createAndReplaceWithDisplayMacro(OpenProjectCreateWorkPackageMacroParameters parameters,
        MacroTransformationContext context) throws MacroExecutionException
    {
        if (parameters.getOPRequest() == null) {
            throw new MacroExecutionException("OPRequest parameter is required.");
        }

        try {
            JsonNode opRequestParameters = objectMapper.readTree(parameters.getOPRequest());
            String instance = opRequestParameters.get(CONNECTION).asText();

            JsonNode node = createWorkPackage(instance, opRequestParameters);
            String workPackageId = node.path("id").asText();

            Map<String, String> displayParams = new HashMap<>();
            displayParams.put(INSTANCE, instance);
            displayParams.put(IDENTIFIER, workPackageId);
            displayParams.put(WORK_ITEMS_DISPLAYER, "workItemsSingle");
            MacroBlock displayMacro = new MacroBlock("openproject", displayParams, true);

            replaceInDocumentAndSave(context, displayMacro);

            return List.of(displayMacro);
        } catch (JsonProcessingException | ProjectManagementException e) {
            throw new MacroExecutionException("Failed to create the work package.", e);
        }
    }

    private void replaceInDocumentAndSave(MacroTransformationContext context, MacroBlock replacement)
    {
        XWikiContext xContext = this.xContextProvider.get();
        try {
            XWikiDocument document = xContext.getDoc();
            XDOM xdom = document.getXDOM();

            MacroBlock currentMacroBlock = context.getCurrentMacroBlock();
            for (Block block : xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT)) {
                MacroBlock macro = (MacroBlock) block;
                if (macro.getId().equals(currentMacroBlock.getId())
                    && macro.getParameters().equals(currentMacroBlock.getParameters()))
                {
                    macro.getParent().replaceChild(replacement, macro);
                    break;
                }
            }

            document.setContent(xdom);
            xContext.getWiki().saveDocument(document, "Replaced create macro with openproject display macro", xContext);
        } catch (XWikiException e) {
            logger.warn("Failed to save document after creating the work package. "
                + "The work package was created in OpenProject but the macro replacement may not be persisted.", e);
        }
    }

    private JsonNode createWorkPackage(String instance, JsonNode opRequestParameters)
        throws JsonProcessingException, ProjectManagementException
    {
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);

        Map<String, Object> formRequest = createRequestForOpenProjectFormRequest(opRequestParameters);

        JsonNode response = apiClient.getWorkPackagesFormResponse(objectMapper.writeValueAsString(formRequest));

        String commitLink = response.path(LINKS).path("commit").path(HREF).asText();
        JsonNode payload = response.path(EMBEDDED).path("payload");

        return apiClient.createWorkPackage(commitLink, objectMapper.writeValueAsString(payload));
    }

    private Map<String, Object> createRequestForOpenProjectFormRequest(JsonNode opRequestParameters)
    {
        Map<String, Object> formRequest = new HashMap<>();
        Map<String, Object> links = new HashMap<>();

        Map<String, String> linkMappings = new HashMap<>();

        linkMappings.put(PROJECT, opRequestParameters.path(PROJECT).asText());
        linkMappings.put(ASSIGNEE, opRequestParameters.path(ASSIGNEE).asText());
        linkMappings.put(TYPE, opRequestParameters.path(TYPE).asText());
        linkMappings.put(STATUS, opRequestParameters.path(STATUS).asText());
        linkMappings.put(PRIORITY, opRequestParameters.path(PRIORITY).asText());

        for (Map.Entry<String, String> entry : linkMappings.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                links.put(entry.getKey(), Map.of(HREF, entry.getValue()));
            }
        }

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put(START_DATE, opRequestParameters.path(START_DATE).asText());
        fieldMappings.put(DUE_DATE, opRequestParameters.path(DUE_DATE).asText());

        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            if (entry.getValue() != null) {
                formRequest.put(entry.getKey(), entry.getValue());
            }
        }

        if (opRequestParameters.get(DESCRIPTION) != null) {
            formRequest.put(DESCRIPTION, Map.of("raw", opRequestParameters.get(DESCRIPTION).asText()));
        }

        formRequest.put(LINKS, links);
        formRequest.put(SUBJECT, opRequestParameters.path(SUBJECT).asText());

        return formRequest;
    }
}
