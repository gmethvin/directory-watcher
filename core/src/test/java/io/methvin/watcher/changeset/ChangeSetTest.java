package io.methvin.watcher.changeset;

import static junit.framework.TestCase.assertEquals;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNotEquals;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import io.methvin.watcher.hashing.FileHash;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChangeSetTest {

  private Path tmpDir;
  private DirectoryWatcher watcher;
  private ChangeSetListener listener;
  private EventRecorder recorder;
  private byte counter = 1;

  private boolean isWin() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private boolean isMac() {
    return System.getProperty("os.name").toLowerCase().contains("mac");
  }

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
    this.recorder = new EventRecorder();
    this.listener = new ChangeSetListener();
  }

  @After
  public void tearDown() throws IOException {
    try {
      FileUtils.deleteDirectory(this.tmpDir.toFile());
    } catch (Exception e) {
    }
  }

  @Test
  public void normaliseCreated() throws IOException, InterruptedException {
    // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is
    // consumed.
    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    waitRecorderSize(3, 1);

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 2);
    ChangeSet changeSet = listener.getChangeSet().get(d1);

    assertEquals(1, changeSet.created().size());
    assertEquals(0, changeSet.deleted().size());
    assertEquals(0, changeSet.modified().size());

    watcher.close();
  }

  @Test
  public void normaliseDeleted() throws IOException, InterruptedException {
    // Do not add DELETED entries, if it's CREATED and DELETED before consumption
    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    waitRecorderSize(3, 1);

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 2);

    Files.deleteIfExists(f1);
    waitRecorderSize(3, 3);

    ChangeSet changeSet = listener.getChangeSet().get(d1);

    // Even though there are
    assertEquals(0, changeSet.created().size());
    assertEquals(0, changeSet.deleted().size());
    assertEquals(0, changeSet.modified().size());

    watcher.close();
  }

  @Test
  public void transitionToModify() throws IOException {
    // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is
    // consumed.
    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    waitRecorderSize(3, 1);

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 2);

    ChangeSet changeSet = listener.getChangeSet().get(d1);
    assertEquals(1, changeSet.created().size());

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 3);

    changeSet = listener.getChangeSet().get(d1);
    assertEquals(0, changeSet.created().size());
    assertEquals(1, changeSet.modified().size());

    watcher.close();
  }

  @Test
  public void normaliseModify() throws IOException, InterruptedException {
    // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is
    // consumed.
    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    waitRecorderSize(3, 1);
    FileHash hash1 = recorder.events.get(0).hash();

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 2);
    FileHash hash2 = recorder.events.get(1).hash();

    ChangeSet changeSet = listener.getChangeSet().get(d1);
    assertEquals(1, changeSet.created().size());

    // Make sure the hash was also updated
    ArrayList<ChangeSetEntry> list = new ArrayList<>(changeSet.created());
    assertNotEquals(hash1, list.get(0).hash());
    assertEquals(hash2, list.get(0).hash());

    // This performance two writes, and thus two modify events
    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 3);

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 4);

    changeSet = listener.getChangeSet().get(d1);
    assertEquals(1, changeSet.modified().size());

    watcher.close();
  }

  @Test
  public void transitionToDeleted() throws IOException {
    // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is
    // consumed.
    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    waitRecorderSize(3, 1);

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 2);

    ChangeSet changeSet = listener.getChangeSet().get(d1);
    assertEquals(1, changeSet.created().size());

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 3);

    changeSet = listener.getChangeSet().get(d1);
    assertEquals(0, changeSet.created().size());
    assertEquals(1, changeSet.modified().size());

    Files.deleteIfExists(f1);
    waitRecorderSize(3, 4);
    changeSet = listener.getChangeSet().get(d1);
    assertEquals(1, changeSet.deleted().size());

    watcher.close();
  }

  @Test
  public void deletedThenCreated() throws IOException {
    // An entry that is deleted and created in same ChangeSet shoud be a modified one.
    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    waitRecorderSize(3, 1);

    Files.write(f1, new byte[] {counter++});
    waitRecorderSize(3, 2);

    ChangeSet changeSet = listener.getChangeSet().get(d1);
    assertEquals(1, changeSet.created().size());

    Files.deleteIfExists(f1);
    waitRecorderSize(3, 3);

    final Path f1v2 = Files.createFile(f1);
    waitRecorderSize(3, 4);

    changeSet = listener.getChangeSet().get(d1);
    assertEquals(0, changeSet.created().size());
    assertEquals(1, changeSet.modified().size());
    assertEquals(0, changeSet.deleted().size());

    watcher.close();
  }

  @Test
  public void multipleRootPaths() throws IOException, InterruptedException {
    Path d1 = this.tmpDir.resolve("d1");
    Path d2 = this.tmpDir.resolve("d2");
    Path d3 = this.tmpDir.resolve("d3");
    init(d1, d2, d3);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    final Path f2 = Files.createTempFile(d2, "f2-", ".dat");
    final Path f3 = Files.createTempFile(d3, "f3-", ".dat");
    waitRecorderSize(3, 3);

    Map<Path, ChangeSet> changeSets = listener.getChangeSet();
    ChangeSet changeSetD1 = changeSets.get(d1);
    assertEquals(1, changeSetD1.created().size());

    ChangeSet changeSetD2 = changeSets.get(d2);
    assertEquals(1, changeSetD2.created().size());

    ChangeSet changeSetD3 = changeSets.get(d3);
    assertEquals(1, changeSetD3.created().size());

    watcher.close();
  }

  private void init(Path... paths) throws IOException {
    for (Path p : paths) {
      Files.createDirectory(p);
    }

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(paths))
            .listener(DirectoryChangeListener.of(listener, recorder))
            .fileHashing(true)
            .build();

    watcher.watchAsync();
  }

  private void waitRecorderSize(int atMost, int untilSize) {
    await()
        .atMost(atMost, TimeUnit.SECONDS)
        .pollDelay(100, TimeUnit.MILLISECONDS)
        .until(() -> this.recorder.events.size() == untilSize);
  }

  class EventRecorder implements DirectoryChangeListener {

    private List<DirectoryChangeEvent> events = new ArrayList<>();

    @Override
    public void onEvent(DirectoryChangeEvent event) {
      this.events.add(event);
    }
  }
}
