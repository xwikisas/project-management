package org.xwiki.properties.annotation;

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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time copy of the annotation added in XWiki 17.5.0, used to order the properties in the UI. By convention, a
 * lower value means a higher priority, and only values greater than 0 are taken into account.
 * <p>
 * It must keep the same package, name, retention and members as the XWiki one. It is never shipped: on XWiki 17.5.0+
 * the core annotation is used, and on older versions the annotation is ignored since its class doesn't exist. Remove
 * this copy once the minimal supported XWiki version is 17.5.0 or more.
 *
 * @version $Id$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
@Inherited
public @interface PropertyOrder
{
    /**
     * @return the order of the property
     */
    int value();
}
