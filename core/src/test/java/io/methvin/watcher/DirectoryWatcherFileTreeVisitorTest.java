package io.methvin.watcher;

import io.methvin.watcher.visitor.FileTreeVisitor;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class DirectoryWatcherFileTreeVisitorTest {
  private Path tmpDir;
  private DirectoryWatcher watcher;

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
  }

  @After
  public void tearDown() {
    try {
      FileUtils.deleteDirectory(this.tmpDir.toFile());
    } catch (Exception e) {
    }
  }

  @Test
  public void testWatcherContinuesOnVisitFileFailed() throws IOException, InterruptedException {
    Assume.assumeTrue(!isWin());
    List<Path> nonReadableFiles = new ArrayList<>();

    // Create two non executable folders with 5 files each.
    for (int i = 0; i < 2; i++) {
      Path tempDirectory = Files.createTempDirectory(tmpDir, "non-executable");
      for (int z = 0; z < 5; z++)
        nonReadableFiles.add(Files.createTempFile(tempDirectory, "non-executable-file", ".dat"));

      // Make the folder non executable in order for the visitor to thorw a visitFileFailed
      // exception
      assertTrue(tempDirectory.toFile().setExecutable(false, false));
    }

    // 10 non executable files to countdown on fail and 1 to countdown on new file created
    CountDownLatch signal = new CountDownLatch(1 + nonReadableFiles.size());
    FileTreeVisitor failingTreeVisitor =
        (file, onDirectory, onFile) -> {
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
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                    throws IOException {
                  if (nonReadableFiles.remove(file)) {
                    signal.countDown();
                  }
                  return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                  return FileVisitResult.CONTINUE;
                }
              };
          Files.walkFileTree(file, visitor);
        };

    Path fileToBeCreated = tmpDir.resolve("new-file.dat");
    watcher =
        DirectoryWatcher.builder()
            .path(tmpDir)
            .fileTreeVisitor(failingTreeVisitor)
            .listener(
                event -> {
                  if (event.eventType().equals(DirectoryChangeEvent.EventType.CREATE)
                      && event.path().equals(fileToBeCreated)) {
                    signal.countDown();
                  }
                })
            .build();

    try {
      watcher.watchAsync();
      Files.createFile(fileToBeCreated);
      assertTrue(signal.await(5, TimeUnit.SECONDS));
    } finally {
      watcher.close();
    }
  }

  private boolean isWin() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }
}
