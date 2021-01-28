package io.methvin.watcher.changeset;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

final class ChangeSetBuilder {

  private final Map<Path, ChangeSetEntry> createdMap = new HashMap<>();
  private final Map<Path, ChangeSetEntry> modifiedMap = new HashMap<>();
  private final Map<Path, ChangeSetEntry> deletedMap = new HashMap<>();

  void addCreated(ChangeSetEntry entry) {
    Path path = entry.path();

    // Remove any MODIFY, quicker to just remove than check and remove.
    modifiedMap.remove(path);

    // Only add to CREATE if DELETE does not already exist, else it's MODIFED.
    if (deletedMap.remove(path) == null) {
      createdMap.put(path, entry);
    } else {
      modifiedMap.put(path, entry);
    }
  }

  void addModified(ChangeSetEntry entry) {
    Path path = entry.path();

    // Only add the MODIFY if a CREATE does not already exist.
    if (createdMap.containsKey(path)) {
      createdMap.put(path, entry);
    } else {
      modifiedMap.put(path, entry);
    }
  }

  void addDeleted(ChangeSetEntry entry) {
    Path path = entry.path();

    // Do not add, if file was CREATED and DELETED, before consumption
    boolean created = createdMap.remove(path) != null;
    if (!created) {
      modifiedMap.remove(path);
      deletedMap.put(path, entry);
    }
  }

  ChangeSet toChangeSet() {
    return new ChangeSetImpl(
        new HashSet<>(createdMap.values()),
        new HashSet<>(modifiedMap.values()),
        new HashSet<>(deletedMap.values()));
  }
}
