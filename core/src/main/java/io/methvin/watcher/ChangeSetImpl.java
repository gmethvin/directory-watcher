package io.methvin.watcher;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ChangeSetImpl implements ChangeSet {

   private Map<Path, ChangeSetEntry> created  = new HashMap<>();
   private Map<Path, ChangeSetEntry> modified = new HashMap<>();
   private Map<Path, ChangeSetEntry> deleted  = new HashMap<>();

   @Override public Collection<ChangeSetEntry> created() {
      return created.values();
   }

   @Override public Collection<ChangeSetEntry> modified() {
      return modified.values();
   }

   @Override public Collection<ChangeSetEntry> deleted() {
      return deleted.values();
   }

   public Map<Path, ChangeSetEntry> createdMap() {
      return created;
   }

   public Map<Path, ChangeSetEntry> modifiedMap() {
      return modified;
   }

   public Map<Path, ChangeSetEntry> deletedMap() {
      return deleted;
   }

   @Override public boolean equals(Object o) {
      if (this == o) {
         return true;
      }
      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      ChangeSetImpl changeSet = (ChangeSetImpl) o;

      if (!created.equals(changeSet.created)) {
         return false;
      }
      if (!modified.equals(changeSet.modified)) {
         return false;
      }
      return deleted.equals(changeSet.deleted);
   }

   @Override public int hashCode() {
      int result = created.hashCode();
      result = 31 * result + modified.hashCode();
      result = 31 * result + deleted.hashCode();
      return result;
   }

   @Override public String toString() {
      return "ChangeSet{" +
             "created=" + created +
             ", modified=" + modified +
             ", deleted=" + deleted +
             '}';
   }
}
