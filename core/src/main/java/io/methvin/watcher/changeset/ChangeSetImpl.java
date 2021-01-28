package io.methvin.watcher.changeset;

import java.util.Set;

final class ChangeSetImpl implements ChangeSet {

  private final Set<ChangeSetEntry> created;
  private final Set<ChangeSetEntry> modified;
  private final Set<ChangeSetEntry> deleted;

  ChangeSetImpl(
      Set<ChangeSetEntry> created, Set<ChangeSetEntry> modified, Set<ChangeSetEntry> deleted) {
    this.created = created;
    this.modified = modified;
    this.deleted = deleted;
  }

  public final Set<ChangeSetEntry> created() {
    return created;
  }

  public final Set<ChangeSetEntry> modified() {
    return modified;
  }

  public final Set<ChangeSetEntry> deleted() {
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

    ChangeSetImpl changeSet = (ChangeSetImpl) o;

    return created.equals(changeSet.created)
        && modified.equals(changeSet.modified)
        && deleted.equals(changeSet.deleted);
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
