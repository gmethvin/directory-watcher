package io.takari.watchservice;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.Watchable;
import java.util.concurrent.atomic.AtomicBoolean;

class MacOSXWatchKey extends AbstractWatchKey {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final boolean reportCreateEvents;
    private final boolean reportModifyEvents;
    private final boolean reportDeleteEvents;

    public MacOSXWatchKey(AbstractWatchService macOSXWatchService, Iterable<? extends WatchEvent.Kind<?>> events) {
        super(macOSXWatchService, null, events);
        boolean reportCreateEvents = false;
        boolean reportModifyEvents = false;
        boolean reportDeleteEvents = false;

        for (WatchEvent.Kind<?> event : events) {
            if (event == StandardWatchEventKinds.ENTRY_CREATE) {
                reportCreateEvents = true;
            } else if (event == StandardWatchEventKinds.ENTRY_MODIFY) {
                reportModifyEvents = true;
            } else if (event == StandardWatchEventKinds.ENTRY_DELETE) {
                reportDeleteEvents = true;
            }
        }
        this.reportCreateEvents = reportCreateEvents;
        this.reportDeleteEvents = reportDeleteEvents;
        this.reportModifyEvents = reportModifyEvents;
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isReportCreateEvents() {
        return reportCreateEvents;
    }

    public boolean isReportModifyEvents() {
        return reportModifyEvents;
    }

    public boolean isReportDeleteEvents() {
        return reportDeleteEvents;
    }

    @Override
    public Watchable watchable() {
      return null;
    }
}
