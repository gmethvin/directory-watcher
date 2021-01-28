package io.methvin.watcher.changeset;

import io.methvin.watcher.hashing.FileHash;
import java.nio.file.Path;
import java.util.Objects;

public final class ChangeSetEntry {

  private final Path path;
  private final boolean isDirectory;
  private final FileHash hash;
  private final Path rootPath;

  ChangeSetEntry(Path path, boolean isDirectory, FileHash hash, Path rootPath) {
    this.path = path;
    this.isDirectory = isDirectory;
    this.hash = hash;
    this.rootPath = rootPath;
  }

  public Path path() {
    return path;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public FileHash hash() {
    return hash;
  }

  public Path rootPath() {
    return rootPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ChangeSetEntry that = (ChangeSetEntry) o;

    return isDirectory == that.isDirectory
        && path.equals(that.path)
        && hash.equals(that.hash)
        && rootPath.equals(that.rootPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, isDirectory, rootPath, hash);
  }
}
