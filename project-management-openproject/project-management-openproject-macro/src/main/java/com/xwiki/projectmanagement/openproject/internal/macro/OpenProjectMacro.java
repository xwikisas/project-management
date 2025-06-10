package com.xwiki.projectmanagement.openproject.internal.macro;

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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * Open project macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject")
public class OpenProjectMacro extends AbstractProjectManagementMacro<OpenProjectMacroParameters>
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private DocumentAccessBridge documentAccessBridge;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    /**
     * Default constructor.
     */
    public OpenProjectMacro()
    {
        super("Open Project", "Retrieve work items from open project.", null, OpenProjectMacroParameters.class);
    }

    @Override
    public void processParameters(OpenProjectMacroParameters parameters)
    {
        addToSourceParams(parameters, "client", "openproject");

        String instance = parameters.getInstance();
        if (instance == null || instance.isEmpty()) {
            return;
        }
        addToSourceParams(parameters, "instance", instance);
    }

    private void addToSourceParams(OpenProjectMacroParameters parameters, String key, String value)
    {
        String sourceParameters = parameters.getSourceParameters();
        if (sourceParameters == null || sourceParameters.isEmpty()) {
            parameters.setSourceParameters(String.format("%s=%s", key, value));
        } else {
            parameters.setSourceParameters(String.format("%s&%s=%s", sourceParameters, key, value));
        }
    }

    @Override
    public List<Block> execute(OpenProjectMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        XWikiContext xContext = this.xContextProvider.get();
        if (xContext.getAction().equals("view")) {
            String connectionName = parameters.getInstance();
            try {
                String token = openProjectConfiguration.getTokenForCurrentConfig(connectionName);
                if (token == null || token.isEmpty()) {
                    String redirectUrl = xContext.getWiki().getURL(documentAccessBridge.getCurrentDocumentReference(),
                        xContext);
                    openProjectConfiguration.createNewToken(connectionName, redirectUrl);
                }
            } catch (AuthenticationException e) {
                throw new MacroExecutionException(e.getMessage());
            }
        }
        return super.execute(parameters, content, context);
    }
}
