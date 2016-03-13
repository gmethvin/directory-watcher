package io.takari.watcher;

import java.io.IOException;
import java.nio.file.Path;

public interface DirectoryChangeListener {
  void onCreate(Path file) throws IOException;

  void onModify(Path file) throws IOException;

  void onDelete(Path path) throws IOException;

  boolean stopWatching();
}
