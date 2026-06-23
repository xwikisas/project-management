package com.xwiki.projectmanagement.relations.store;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.objects.BaseObject;

/**
 * The base object wrapper that enables the manipulation if Work Package links.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
public class ProjectManagementRelation
{
    /**
     * The String reference of the class defining the object which contains an OIDC configuration.
     */
    public static final String CLASS_FULLNAME = "ProjectManagement.Code.RelationClass";

    /**
     * The local reference of the configuration class.
     */
    public static final LocalDocumentReference CLASS_REFERENCE = new LocalDocumentReference(Arrays.asList(
        "ProjectManagement", "Code"), "RelationClass");

    /**
     * The name of the client property within the XClass.
     */
    public static final String FIELD_CLIENT = "client";

    /**
     * The name of the project property within the XClass.
     */
    public static final String FIELD_PROJECT = "project";

    /**
     * The name of the work package property within the XClass.
     */
    public static final String FIELD_WORK_ITEM = "workItem";

    /**
     * The name of the client params property within the XClass.
     */
    public static final String FIELD_CLIENT_PARAMS = "clientParams";

    private final BaseObject xobject;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param xobject the xobject that stores the work package link information.
     */
    public ProjectManagementRelation(BaseObject xobject)
    {
        this.xobject = xobject;
    }

    /**
     * @return the id of the project management client. i.e. openproject.
     */
    public String getClient()
    {
        return this.xobject.getStringValue(FIELD_CLIENT);
    }

    /**
     * @param client see {@link #getClient()} .
     */
    public void setClient(String client)
    {
        this.xobject.setStringValue(FIELD_CLIENT, client);
    }

    /**
     * @return the id of the OpenProject project that is linked to a xwiki page.
     */
    public String getProject()
    {
        return this.xobject.getStringValue(FIELD_PROJECT);
    }

    /**
     * @param project see {@link #getProject()}.
     */
    public void setProject(String project)
    {
        this.xobject.setStringValue(FIELD_PROJECT, project);
    }

    /**
     * @return the id of the OpenProject work package that is linked to a xwiki page.
     */
    public String getWorkItem()
    {
        return this.xobject.getStringValue(FIELD_WORK_ITEM);
    }

    /**
     * @param workPackage see {@link #getWorkItem()} ()}.
     */
    public void setWorkItem(String workPackage)
    {
        this.xobject.setStringValue(FIELD_WORK_ITEM, workPackage);
    }

    /**
     * @return the stored client params in a string format. The storage format is defined by the client.
     */
    public String getClientParams()
    {
        return this.xobject.getLargeStringValue(FIELD_CLIENT_PARAMS);
    }

    /**
     * @param clientParams see {@link #getClientParams()}.
     */
    public void setClientParams(String clientParams)
    {
        this.xobject.setLargeStringValue(FIELD_CLIENT_PARAMS, clientParams);
    }

    /**
     * @return the reference of the document that own this object.
     */
    public DocumentReference getDocumentReference()
    {
        return this.xobject.getDocumentReference();
    }

    /**
     * @return a map out of the client params.
     * @throws JsonProcessingException if the client params are serialized as a valid json.
     */
    public Map<String, String> getClientParamsMap() throws JsonProcessingException
    {
        String params = getClientParams();
        if (StringUtils.isEmpty(params)) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(params, new TypeReference<Map<String, String>>()
        {
        });
    }

    /**
     * @return a model object that contains all the relevant data taken from this object.
     */
    public com.xwiki.projectmanagement.relations.model.ProjectManagementRelation toModel()
    {
        com.xwiki.projectmanagement.relations.model.ProjectManagementRelation model =
            new com.xwiki.projectmanagement.relations.model.ProjectManagementRelation();
        model.setClient(getClient());
        model.setClientParams(getClientParams());
        model.setProject(getProject());
        model.setWorkItem(getWorkItem());
        return model;
    }
}
