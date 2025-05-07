package com.xwiki.projectmanagement.livadata.displayer;

import org.xwiki.component.annotation.Role;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Generates a html structure for a desired Work Item property.
 */
@Role
public interface ProjectManagementPropertyDisplayer
{
    String display(WorkItem workItem);
}
