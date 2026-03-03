package com.xwiki.projectmanagement.openproject.store;

import java.util.Arrays;

import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.objects.BaseObject;

public class WorkPackageLink
{
    /**
     * The String reference of the class defining the object which contains an OIDC configuration.
     */
    public static final String CLASS_FULLNAME = "OpenProject.Code.WorkPackageLink";

    /**
     * The local reference of the configuration class.
     */
    public static final LocalDocumentReference CLASS_REFERENCE = new LocalDocumentReference(Arrays.asList(
        "OpenProject", "Code"), "WorkPackageLink");

    public static final String FIELD_PROJECT = "project";

    public static final String FIELD_WORK_PACKAGE = "workPackage";

    public static final String FIELD_INSTANCE = "instance";

    private final BaseObject xobject;

    public WorkPackageLink(BaseObject xobject)
    {
        this.xobject = xobject;
    }

    public String getProject()
    {
        return this.xobject.getStringValue(FIELD_PROJECT);
    }

    public void setProject(String project)
    {
        this.xobject.setStringValue(FIELD_PROJECT, project);
    }

    public String getWorkPackage()
    {
        return this.xobject.getStringValue(FIELD_PROJECT);
    }

    public void setWorkPackage(String workPackage)
    {
        this.xobject.setStringValue(FIELD_PROJECT, workPackage);
    }

    public String getInstance()
    {
        return this.xobject.getStringValue(FIELD_WORK_PACKAGE);
    }

    public void setInstance(String instance)
    {
        this.xobject.setStringValue(FIELD_INSTANCE, instance);
    }
}
