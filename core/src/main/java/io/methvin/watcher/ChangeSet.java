package io.methvin.watcher;

import java.util.HashSet;
import java.util.Set;

public class ChangeSet {

  private Set<ChangeSetEntry> created = new HashSet<>();
  private Set<ChangeSetEntry> modified = new HashSet<>();
  private Set<ChangeSetEntry> deleted = new HashSet<>();

  public Set<ChangeSetEntry> created() {
    return created;
  }

  public Set<ChangeSetEntry> modified() {
    return modified;
  }

  public Set<ChangeSetEntry> deleted() {
    return deleted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ChangeSet changeSet = (ChangeSet) o;

    if (!created.equals(changeSet.created)) {
      return false;
    }
    if (!modified.equals(changeSet.modified)) {
      return false;
    }
    return deleted.equals(changeSet.deleted);
  }

  @Override
  public int hashCode() {
    int result = created.hashCode();
    result = 31 * result + modified.hashCode();
    result = 31 * result + deleted.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ChangeSet{"
        + "created="
        + created
        + ", modified="
        + modified
        + ", deleted="
        + deleted
        + '}';
  }
}
