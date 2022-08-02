package io.methvin.watcher.visitor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class DefaultFileTreeVisitor implements FileTreeVisitor {
  @Override
  public void recursiveVisitFiles(Path file, Callback onDirectory, Callback onFile)
      throws IOException {
    SimpleFileVisitor<Path> visitor =
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            onDirectory.call(dir);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            onFile.call(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            onFailure(file, exc);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) onFailure(dir, exc);
            return FileVisitResult.CONTINUE;
          }
        };
    Files.walkFileTree(file, visitor);
  }

  // To be overridden if needed
  protected void onFailure(Path path, IOException exception) throws IOException {}
}
