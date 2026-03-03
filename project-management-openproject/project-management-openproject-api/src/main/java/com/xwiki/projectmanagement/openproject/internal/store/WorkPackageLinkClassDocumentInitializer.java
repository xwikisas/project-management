package com.xwiki.projectmanagement.openproject.internal.store;

import java.util.Arrays;

import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xwiki.projectmanagement.openproject.store.WorkPackageLink;

public class WorkPackageLinkClassDocumentInitializer extends AbstractMandatoryClassInitializer
{
    public static final LocalDocumentReference CLASS_REFERENCE = new LocalDocumentReference(Arrays.asList(
        "OpenProject", "Code"), "WorkPackageLink");

    public WorkPackageLinkClassDocumentInitializer()
    {
        super(CLASS_REFERENCE, "Open Project Page Link Class");
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addTextField(WorkPackageLink.FIELD_PROJECT, "OpenProject Project ID", 20);
        xclass.addTextField(WorkPackageLink.FIELD_WORK_PACKAGE, "OpenProject Work Package ID", 20);
        xclass.addTextField(WorkPackageLink.FIELD_INSTANCE, "Open Project Instance Name", 40);
    }
}
