package com.xwiki.projectmanagement.livadata;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;

/**
 * Some description.
 *
 * @version $Id$
 */
@Component
@Named("projectmanagement")
@Singleton
public class ProjectManagementConfigurationProvider implements Provider<LiveDataConfiguration>
{
    @Inject
    private Logger logger;

    @Inject
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    private String defaultConfigJSON;

    @Override
    public LiveDataConfiguration get()
    {
        // TODO: Might want to add a way to merge the configurations coming from other implementations, such as jira,
        //  open project, git etc.
        try {
            String testPath = "/home/teo/Desktop/customLiveDataConfiguration.json";
            File testFile = new File(testPath);
            if (testFile.exists()) {
                try (InputStream testFileInputStream = new FileInputStream(testFile)) {
                    this.defaultConfigJSON = IOUtils.toString(testFileInputStream, Charset.defaultCharset());
                } catch (Exception e) {
                    logger.error("Failed to open file [{}].", testPath, e);
                }
            } else {
                InputStream defaultConfigInputStream =
                    getClass().getResourceAsStream("/projectManagementLiveDataConfiguration.json");
                if (defaultConfigInputStream == null) {
                    this.defaultConfigJSON = "";
                } else {
                    this.defaultConfigJSON = IOUtils.toString(defaultConfigInputStream, "UTF-8");
                }
            }
            return this.stringLiveDataConfigResolver.resolve(this.defaultConfigJSON);
        } catch (IOException | LiveDataException e) {
            logger.error("Failed to read the default live data configuration for the live table source.", e);
            return null;
        }
    }
}
