package io.methvin.watcher;

import java.util.Set;

public interface ChangeSet {
  Set<ChangeSetEntry> created();

  Set<ChangeSetEntry> modified();

  Set<ChangeSetEntry> deleted();
}
