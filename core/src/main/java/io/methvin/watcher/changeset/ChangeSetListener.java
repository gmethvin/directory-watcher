package io.methvin.watcher.changeset;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public final class ChangeSetListener implements DirectoryChangeListener {

  private Map<Path, ChangeSetBuilder> changeBuilders = new HashMap<>();

  private final Object lock = new Object() {};

  private BiConsumer<DirectoryChangeEvent.EventType, Map<Path, ChangeSet>> subscriber;

  public ChangeSetListener() {}

  public ChangeSetListener(
      BiConsumer<DirectoryChangeEvent.EventType, Map<Path, ChangeSet>> subscriber) {
    this.subscriber = subscriber;
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
      if (subscriber != null) {
        subscriber.accept(event.eventType(), this.getChangeSet());
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
}
