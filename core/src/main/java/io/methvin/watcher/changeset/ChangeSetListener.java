package io.methvin.watcher.changeset;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ChangeSetListener implements DirectoryChangeListener {

  private Map<Path, ChangeSetBuilder> changeBuilders = new HashMap<>();

  private final Object lock = new Object() {};

  private final int timeout;

  private ScheduledExecutorService service;
  private ScheduledFuture currentTask;

  private Consumer<Integer> onIdleListener;

  public ChangeSetListener() {
    this(-1, null);
  }

  public ChangeSetListener(int timeout, Consumer<Integer> onIdleListener) {
    this.timeout = timeout;
    this.onIdleListener = onIdleListener;

    if (timeout > 0) {
      service = Executors.newSingleThreadScheduledExecutor();
    }
  }

  @Override
  public void onEvent(DirectoryChangeEvent event) {
    Path rootPath = event.rootPath();
    Path path = event.path();

    synchronized (lock) {
      // Maintain a ChangeSet per rootPath
      ChangeSetBuilder builder = changeBuilders.get(rootPath);
      if (builder == null) {
        builder = new ChangeSetBuilder();
        changeBuilders.put(rootPath, builder);
      }

      ChangeSetEntry entry =
          new ChangeSetEntry(path, event.isDirectory(), event.hash(), event.rootPath());

      switch (event.eventType()) {
        case CREATE:
          builder.addCreated(entry);
          break;
        case MODIFY:
          builder.addModified(entry);
          break;
        case DELETE:
          builder.addDeleted(entry);
          break;
        case OVERFLOW:
          throw new IllegalStateException("OVERFLOW not yet handled");
      }

      if (timeout > 0 && currentTask != null) {
        currentTask.cancel(false);
        currentTask = null;
      }
    }
  }

  public Map<Path, ChangeSet> getChangeSet() {
    Map<Path, ChangeSetBuilder> returnBuilders;
    synchronized (lock) {
      returnBuilders = changeBuilders;
      changeBuilders = new HashMap<>();
    }
    return returnBuilders.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toChangeSet()));
  }

  @Override
  public void onIdle(int count) {
    synchronized (lock) {
      if (timeout > 0) {
        if (currentTask != null) {
          currentTask.cancel(false);
          currentTask = null;
        }
        currentTask =
            service.schedule(() -> onIdleListener.accept(count), timeout, TimeUnit.MILLISECONDS);
      }
    }
  }
}
