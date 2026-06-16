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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectMembersMacroParameters;
import com.xwiki.projectmanagement.openproject.model.User;

/**
 * OpenProject macro that retrieves and displays the members of a specific project, grouped by role.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Singleton
@Named("openproject-project-members")
public class OpenProjectProjectMembersMacro
    extends AbstractOpenProjectDirectMacro<OpenProjectProjectMembersMacroParameters>
{
    private static final String CLASS = "class";

    private static final String MEMBERS_FILTER = "[{\"project\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]";

    private static final String VIEW_ALL_MEMBERS_URL = "%s/projects/%s/members";

    /**
     * Default constructor.
     */
    public OpenProjectProjectMembersMacro()
    {
        super("OpenProject - Project Members",
            "Displays the members of a specific project from a configured OpenProject instance, grouped by role.",
            OpenProjectProjectMembersMacroParameters.class);
    }

    @Override
    protected List<Block> executeInternal(OpenProjectProjectMembersMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        String project = parameters.getProject();
        String filters = String.format(MEMBERS_FILTER, project);
        List<User> members;
        try {
            members = apiClient.getMemberships(null, parameters.getCount(), filters).getItems();
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException(
                String.format("Failed to retrieve members for project [%s].", project), e);
        }

        String serverUrl = getOpenProjectConfiguration().getConnection(instance).getServerURL();
        String viewAllUrl = String.format(VIEW_ALL_MEMBERS_URL, serverUrl, project);

        return Collections.singletonList(
            new GroupBlock(buildMembersBlocks(members, viewAllUrl), Collections.emptyMap()));
    }

    private List<Block> buildMembersBlocks(List<User> members, String viewAllUrl)
    {
        List<Block> blocks = new ArrayList<>();

        if (members.isEmpty()) {
            blocks.add(new ParagraphBlock(List.of(new WordBlock("No members found.")),
                Collections.singletonMap(CLASS, "text-muted")));
        } else {
            Map<String, List<Linkable>> membersByRole = groupByRole(members);
            for (Map.Entry<String, List<Linkable>> entry : membersByRole.entrySet()) {
                blocks.add(new HeaderBlock(List.of(new WordBlock(entry.getKey())), HeaderLevel.LEVEL3));
                List<Block> inlineMembers = new ArrayList<>();
                List<Linkable> roleMembers = entry.getValue();
                for (int i = 0; i < roleMembers.size(); i++) {
                    Linkable member = roleMembers.get(i);
                    inlineMembers.add(buildLinkBlock(member.getValue(), member.getLocation()));
                    if (i < roleMembers.size() - 1) {
                        inlineMembers.add(new SpecialSymbolBlock(','));
                        inlineMembers.add(new SpaceBlock());
                    }
                }
                blocks.add(new ParagraphBlock(inlineMembers));
            }
        }

        blocks.add(buildViewAllMembersButton(viewAllUrl));

        return blocks;
    }

    private Map<String, List<Linkable>> groupByRole(List<User> members)
    {
        Map<String, List<Linkable>> result = new LinkedHashMap<>();
        for (User user : members) {
            Linkable self = user.getSelf();
            List<Linkable> roles = user.getRoles();
            if (roles == null || roles.isEmpty()) {
                result.computeIfAbsent("Unknown", k -> new ArrayList<>()).add(self);
            } else {
                for (Linkable role : roles) {
                    result.computeIfAbsent(role.getValue(), k -> new ArrayList<>()).add(self);
                }
            }
        }
        return result;
    }

    private Block buildViewAllMembersButton(String url)
    {
        Block icon = new FormatBlock(Collections.emptyList(), Format.NONE,
            Collections.singletonMap(CLASS, "fa fa-users"));
        ResourceReference ref = new ResourceReference(url, ResourceType.URL);
        return new LinkBlock(List.of(icon, new SpaceBlock(), new WordBlock("View all members")), ref, false,
            Collections.singletonMap(CLASS, "btn btn-link"));
    }

    private Block buildLinkBlock(String label, String url)
    {
        ResourceReference ref = new ResourceReference(url, ResourceType.URL);
        return new LinkBlock(List.of(new WordBlock(label)), ref, true);
    }
}
