package io.methvin.watcher;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ChangeSetListener implements DirectoryChangeListener {

  private Map<Path, ChangeSet> changeSets;

  // Lock is used as a simple Exchange pattern, so the UserInput thread can safely consume
  // normalisedChangedSet
  private Object lock = new Object() {};

  public ChangeSetListener() {
    this.changeSets = new HashMap<>();
  }

  @Override
  public void onEvent(DirectoryChangeEvent event) {
    Path rootPath = event.rootPath();
    Path path = event.path();

    synchronized (lock) {
      // Maintain a ChangeSet per rootPath
      ChangeSet changeSet = changeSets.get(rootPath);
      if (changeSet == null) {
        changeSet = new ChangeSet();
        changeSets.put(rootPath, changeSet);
      }

      ChangeSetEntry entry =
          new ChangeSetEntry(path, event.isDirectory(), event.hash(), event.rootPath());

      // This logic assumes events might come out of order, i.e. a delete before a create, and
      // attempts to handle this gracefully.
      switch (event.eventType()) {
        case CREATE:
          // Remove any MODIFY, quicker to just remove than check and remove.
          changeSet.modified().remove(entry);

          // Only add if DELETE does not already exist.
          if (!changeSet.deleted().remove(entry)) {
            changeSet.created().add(entry);
          }
          break;
        case MODIFY:
          if (!changeSet.deleted().contains(entry) && !changeSet.created().contains(entry)) {
            // Only add the MODIFY if a CREATE or DELETE does not already exist.
            changeSet.modified().add(entry);
          }
          break;
        case DELETE:
          // Do not add, if file was CREATED and DELETED, before consumption
          boolean created = changeSet.created().remove(entry);
          if (!created) {
            changeSet.modified().remove(entry);
          }

          if (!created) {
            changeSet.deleted().add(entry);
          }
          break;
        case OVERFLOW:
          throw new IllegalStateException("OVERFLOW not yet handled");
      }
    }
  }

  public Map<Path, ChangeSet> getChangeSet() {
    Map<Path, ChangeSet> returnMap;
    synchronized (lock) {
      returnMap = changeSets;
      changeSets = new HashMap<>();
    }
    return returnMap;
  }
}
