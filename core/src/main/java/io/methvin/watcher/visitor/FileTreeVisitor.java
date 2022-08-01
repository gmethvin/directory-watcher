package io.methvin.watcher.visitor;

import java.io.IOException;

import java.nio.file.Path;

public interface FileTreeVisitor {

  /** The default file tree visitor instance, which uses Files.walkFileTree. */
  FileTreeVisitor DEFAULT_FILE_TREE_VISITOR = new DefaultFileTreeVisitor();

  interface Callback {
    void call(Path p) throws IOException;
  }

  void recursiveVisitFiles(Path file, Callback onDirectory, Callback onFile) throws IOException;
}
