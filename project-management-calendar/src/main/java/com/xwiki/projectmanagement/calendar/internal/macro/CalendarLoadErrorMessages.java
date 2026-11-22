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
package com.xwiki.projectmanagement.calendar.internal.macro;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;

/**
 * The default messages explaining why the events of a calendar can't be loaded.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = CalendarLoadErrorMessages.class)
@Singleton
public class CalendarLoadErrorMessages
{
    @Inject
    private ContextualLocalizationManager l10n;

    /**
     * @param error the reason why the events can't be loaded
     * @return the text explaining the error, translated, or the translation key when it has no translation
     */
    public Block getHint(CalendarLoadError error)
    {
        Translation translation = this.l10n.getTranslation(error.getTranslationKey());
        return translation != null ? translation.render() : new WordBlock(error.getTranslationKey());
    }

    /**
     * @param error the reason why the events can't be loaded
     * @return the {@link #getHint(CalendarLoadError) hint} of the error, in a warning box when the user may be able to
     *     fix the error, in an error box otherwise
     */
    public List<Block> getMessage(CalendarLoadError error)
    {
        return List.of(new GroupBlock(List.of(getHint(error)),
            Map.of("class", error.isWarning() ? "box warningmessage" : "box errormessage")));
    }
}
