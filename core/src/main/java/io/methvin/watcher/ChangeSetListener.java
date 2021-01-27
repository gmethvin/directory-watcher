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
    Path path     = event.path();

    synchronized (lock) {
      // Maintain a ChangeSet per rootPath
      ChangeSetImpl changeSet = (ChangeSetImpl) changeSets.get(rootPath);
      if (changeSet == null) {
        changeSet = new ChangeSetImpl();
        changeSets.put(rootPath, changeSet);
      }

      ChangeSetEntry entry =
          new ChangeSetEntry(path, event.isDirectory(), event.hash(), event.rootPath());

      // This logic assumes events might come out of order, i.e. a delete before a create, and
      // attempts to handle this gracefully.
      switch (event.eventType()) {
        case CREATE:
          // Remove any MODIFY, quicker to just remove than check and remove.
          changeSet.modifiedMap().remove(path);

          // Only add to CREATE if DELETE does not already exist, else it's MODIFED.
          if (changeSet.deletedMap().remove(path) == null) {
            changeSet.createdMap().put(path, entry);
          } else {
            changeSet.modifiedMap().put(path, entry);
          }
          break;
        case MODIFY:
          if (!changeSet.createdMap().containsKey(path)) {
            // Only add the MODIFY if a CREATE does not already exist.
            changeSet.modifiedMap().put(path, entry);
          } else {
            changeSet.createdMap().put(path, entry);
          }
          break;
        case DELETE:
          // Do not add, if file was CREATED and DELETED, before consumption
          boolean created = changeSet.createdMap().remove(path) != null;
          if (!created) {
            changeSet.modifiedMap().remove(path);
          }

          if (!created) {
            changeSet.deletedMap().put(path, entry);
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
