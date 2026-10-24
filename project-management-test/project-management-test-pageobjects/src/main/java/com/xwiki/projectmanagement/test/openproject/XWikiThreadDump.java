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
package com.xwiki.projectmanagement.test.openproject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs a thread dump of the XWiki instance under test, to find out what a macro that never finishes rendering is
 * waiting on. It only works when XWiki runs as a local process (the Jetty standalone servlet engine used on CI); in
 * a container, it logs that no dump could be taken.
 *
 * @version $Id$
 */
final class XWikiThreadDump
{
    private static final Logger LOGGER = LoggerFactory.getLogger(XWikiThreadDump.class);

    private XWikiThreadDump()
    {
    }

    /**
     * Log the thread dump of the local XWiki process, if there is one.
     *
     * @param reason why the dump is taken, logged with it
     */
    static void log(String reason)
    {
        List<ProcessHandle> processes = ProcessHandle.allProcesses()
            .filter(process -> process.pid() != ProcessHandle.current().pid())
            .filter(process -> process.info().commandLine().filter(XWikiThreadDump::isXWikiJetty).isPresent())
            .collect(Collectors.toList());
        if (processes.isEmpty()) {
            LOGGER.error("{} No local XWiki process found to take a thread dump of.", reason);
        }
        for (ProcessHandle process : processes) {
            LOGGER.error("{} Thread dump of the XWiki process [{}]:\n{}", reason, process.pid(),
                threadDump(process.pid()).orElse("(failed to take it)"));
        }
    }

    private static boolean isXWikiJetty(String commandLine)
    {
        return commandLine.contains("jetty") && commandLine.contains("xwiki");
    }

    private static Optional<String> threadDump(long pid)
    {
        Path jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd");
        try {
            Process process = new ProcessBuilder(jcmd.toString(), String.valueOf(pid), "Thread.print")
                .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return Optional.of(output);
        } catch (IOException e) {
            LOGGER.error("Failed to run [{}].", jcmd, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
