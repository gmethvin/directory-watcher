/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.methvin.watcher;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.Objects;

public final class DirectoryChangeEvent {
  public enum EventType {

    /* A new file was created */
    CREATE(StandardWatchEventKinds.ENTRY_CREATE),

    /* An existing file was modified */
    MODIFY(StandardWatchEventKinds.ENTRY_MODIFY),

    /* A file was deleted */
    DELETE(StandardWatchEventKinds.ENTRY_DELETE),

    /* An overflow occurred; some events were lost */
    OVERFLOW(StandardWatchEventKinds.OVERFLOW);

    private WatchEvent.Kind<?> kind;

    EventType(WatchEvent.Kind<?> kind) {
      this.kind = kind;
    }

    public WatchEvent.Kind<?> getWatchEventKind() {
      return kind;
    }
  }

  private final EventType eventType;
  private final Path path;
  private final int count;
  private final Path rootPath;
  private final boolean isDirectory;

  public DirectoryChangeEvent(
      EventType eventType, Path path, int count, Path rootPath, boolean isDirectory) {
    this.eventType = eventType;
    this.path = path;
    this.count = count;
    this.rootPath = rootPath;
    this.isDirectory = isDirectory;
  }

  public EventType eventType() {
    return eventType;
  }

  public Path path() {
    return path;
  }

  public int count() {
    return count;
  }

  public Path rootPath() {
    return rootPath;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DirectoryChangeEvent that = (DirectoryChangeEvent) o;

    return count == that.count
        && eventType == that.eventType
        && Objects.equals(path, that.path)
        && Objects.equals(rootPath, that.rootPath)
        && Objects.equals(isDirectory, that.isDirectory);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventType, path, count, rootPath, isDirectory);
  }

  @Override
  public String toString() {
    return "DirectoryChangeEvent{"
        + "eventType="
        + eventType
        + ", path="
        + path
        + ", count="
        + count
        + ", rootPath="
        + rootPath
        + ", isDirectory="
        + isDirectory
        + '}';
  }
}
