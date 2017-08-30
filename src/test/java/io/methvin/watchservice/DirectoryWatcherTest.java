package io.methvin.watchservice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.util.FileUtils;
import org.junit.Assume;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import io.methvin.watchservice.FileSystem.FileSystemAction;

public class DirectoryWatcherTest {

  @Test
  public void validateOsxDirectoryWatcher() throws Exception {
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("mac"));

    File directory = new File(new File("").getAbsolutePath(), "target/directory");
    FileUtils.deleteDirectory(directory);
    directory.mkdirs();
    runWatcher(directory.toPath(), new MacOSXListeningWatchService());
  }

  @Test
  public void validateOsxDirectoryWatcherPreExistingSubdir() throws Exception {
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("mac"));

    File directory = new File(new File("").getAbsolutePath(), "target/directory");
    FileUtils.deleteDirectory(directory);
    directory.mkdirs();

    //make a dir before the watch is started
    File zupDir = Paths.get(directory.toString(), "zup").toFile();
    zupDir.mkdir();

    assertTrue(zupDir.exists());

    //prep a new file for the watched directory
    File fileInZupDir = new File(zupDir, "fileInZupDir.txt");
    assertFalse(fileInZupDir.exists());

    //write it to the zup subdirectory of the watched directory
    Files.write(fileInZupDir.toPath(), "some data".getBytes());
    assertTrue(fileInZupDir.exists());

    //files are written and done, now start the watcher
    runWatcher(directory.toPath(), new MacOSXListeningWatchService());

  }

  @Test
  public void validateJdkDirectoryWatcher() throws Exception {
    // The JDK watch service is basically unusable on mac since it polls every 10s
    Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("mac"));

    File directory = new File(new File("").getAbsolutePath(), "target/directory");
    FileUtils.deleteDirectory(directory);
    directory.mkdirs();
    runWatcher(directory.toPath(), FileSystems.getDefault().newWatchService());
  }

  protected void runWatcher(Path directory, WatchService watchService) throws Exception {
    //
    // start our service
    // play our events
    // stop when all our events have been drained and processed
    //
    // We wait 100ms before deletes are executed because any faster and the MacOS implementation
    // appears to not see them because the create/delete pair happen so fast it's like the file
    // is never there at all.
    int waitInMs = 500;
    FileSystem fileSystem = new FileSystem(directory)
      .create("one.txt")
      .wait(waitInMs)
      .create("two.txt")
      .wait(waitInMs)
      .create("three.txt")
      .wait(waitInMs)
      .update("three.txt", " 111111")
      .wait(waitInMs)
      .update("three.txt", " 222222")
      .wait(waitInMs)
      .delete("one.txt")
      .wait(waitInMs)
      .directory("testDir")
      .wait(waitInMs)
      .create("testDir/file1InDir.txt")
      .wait(waitInMs)
      .create("testDir/file2InDir.txt", " 111111")
      .wait(waitInMs)
      .update("testDir/file2InDir.txt", " 222222")
      .wait(waitInMs);
    // Collect our filesystem actions
    List<FileSystemAction> actions = fileSystem.actions();

    TestDirectoryChangeListener listener = new TestDirectoryChangeListener(directory, actions);
    DirectoryWatcher watcher = new DirectoryWatcher(Collections.singletonList(directory), listener, watchService);

    // Fire up the filesystem watcher
    CompletableFuture<Void> future = watcher.watchAsync();
    // Play our filesystem events
    fileSystem.playActions();
    // Wait for the future to complete which is when the right number of events are captured
    future.get(10, TimeUnit.SECONDS);
    ListMultimap<String, WatchEvent.Kind<Path>> events = listener.events;
    // Close the watcher
    watcher.close();

    // Let's see if everything works!
    assertEquals(actions.size(), events.size());
    //
    // Now we make a map of the events keyed by the path. The order in which we
    // play the filesystem actions is not necessarily the order in which the events are
    // emitted. In the test above I often see the create file event for three.txt before
    // two.txt. We just want to make sure that the action for a particular path agrees
    // with the corresponding event for that file. For a given path we definitely want
    // the order of the played actions to match the order of the events emitted.
    //
    List<WatchEvent.Kind<Path>> one = events.get("one.txt");
    assertEquals(2, one.size());
    assertEquals(one.get(0), actions.get(0).kind);
    assertEquals(one.get(1), actions.get(5).kind);

    List<WatchEvent.Kind<Path>> two = events.get("two.txt");
    assertEquals(1, two.size());
    assertEquals(two.get(0), actions.get(1).kind);

    List<WatchEvent.Kind<Path>> three = events.get("three.txt");
    assertEquals(3, three.size());
    assertEquals(three.get(0), actions.get(2).kind);
    assertEquals(three.get(1), actions.get(3).kind);
    assertEquals(three.get(2), actions.get(4).kind);

    List<WatchEvent.Kind<Path>> four = events.get("testDir/file1InDir.txt");
    assertEquals(1, four.size());
    assertEquals(three.get(0), actions.get(6).kind);

    List<WatchEvent.Kind<Path>> five = events.get("testDir/file2InDir.txt");
    assertEquals(2, five.size());
    assertEquals(three.get(0), actions.get(7).kind);
    assertEquals(three.get(1), actions.get(8).kind);

  }

  private static Runnable watcher(final DirectoryWatcher watcher, final List<FileSystemAction> actions) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          watcher.watch();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
  }

  class TestDirectoryChangeListener implements DirectoryChangeListener {
    final Path directory;
    final List<FileSystemAction> actions;
    final ListMultimap<String, WatchEvent.Kind<Path>> events = ArrayListMultimap.create();
    final int totalActions;
    int actionsProcessed = 0;

    public TestDirectoryChangeListener(Path directory, List<FileSystemAction> actions) {
      this.directory = directory;
      this.actions = actions;
      this.totalActions = actions.size();
    }

    @Override
    public void onEvent(DirectoryChangeEvent event) throws IOException {
      updateActions(event.path(), event.eventType().getWatchEventKind());
    }

    void updateActions(Path path, WatchEvent.Kind<Path> kind) {
      System.out.println(kind + " ----> " + path);
      if (!path.toFile().isDirectory()) {
        events.put(directory.relativize(path).toString(), kind);
        actionsProcessed++;
        System.out.println(actionsProcessed + "/" + totalActions + " actions processed.");
      }
    }

    @Override
    public boolean isWatching() {
      return actionsProcessed < totalActions;
    }
  }
}
