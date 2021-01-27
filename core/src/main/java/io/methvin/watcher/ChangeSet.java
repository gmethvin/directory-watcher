package io.methvin.watcher;

import java.util.Collection;

public interface ChangeSet {
   Collection<ChangeSetEntry> created();

   Collection<ChangeSetEntry> modified();

   Collection<ChangeSetEntry> deleted();
}
