/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.helper;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.LinkedList;
import java.util.List;

/**
 * Mock appender of log4j2 logger
 */
public class TestLoggerAppender extends AbstractAppender {
    private final List<LogEvent> logEvents = new LinkedList<>();

    /**
     * Constructor
     *
     * @param name name of the logger
     */
    public TestLoggerAppender(String name) {
        super(name, null, null, true, null);
    }

    /**
     * Append event to list
     *
     * @param event log event object
     */
    @Override
    public void append(LogEvent event) {
        logEvents.add(event.toImmutable());
    }

    /**
     * Get list of events
     *
     * @return list of log events
     */
    public List<LogEvent> getLogEvents() {
        return new LinkedList<>(logEvents);
    }

    /**
     * Clean all events
     */
    public void clean() {
        logEvents.clear();
    }
}
