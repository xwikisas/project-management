package com.xwiki.projectmanagement.internal.chart;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

/**
 * Displays a PIE chart.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component
@Singleton
@Named(LineChartDisplayer.TYPE)
public class LineChartDisplayer extends AbstractChartJSDisplayer
{
    /**
     * The type of the chart.
     */
    public static final String TYPE = "line";

    @Override
    public String getChartType()
    {
        return TYPE;
    }
}
