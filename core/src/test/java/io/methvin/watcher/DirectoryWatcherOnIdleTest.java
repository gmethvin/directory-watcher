package io.methvin.watcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.methvin.watcher.changeset.ChangeSetListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectoryWatcherOnIdleTest {

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

  private boolean isMac() {
    return System.getProperty("os.name").toLowerCase().contains("mac");
  }

  @Test
  public void testOnIdle() throws IOException {
    Path d1 = this.tmpDir.resolve("idle1");
    Files.createDirectory(d1);

    AtomicLong started = new AtomicLong();
    AtomicLong updated = new AtomicLong();
    AtomicInteger counter = new AtomicInteger(-1);

    Consumer<Integer> consumer =
        value -> {
          updated.set(System.currentTimeMillis() - started.get());
          counter.set(value);

          try {
            watcher.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        };

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(d1))
            .listener(new Composite(consumer))
            .fileHashing(true)
            .onIdleTimeout(100)
            .build();

    started.set(System.currentTimeMillis());
    watcher.watchAsync();

    doDelay(400);
    assertTrue(updated.get() > 0 && updated.get() < 200);
    assertEquals(0, counter.get());
  }

  @Test
  public void testOnIdleFilesCreationAdded() throws IOException {
    Path d1 = this.tmpDir.resolve("idle2");
    Files.createDirectory(d1);

    AtomicLong started = new AtomicLong();
    AtomicLong updated = new AtomicLong();
    AtomicInteger counter = new AtomicInteger(-1);
    AtomicInteger onIdleCalled = new AtomicInteger(0);

    Consumer<Integer> consumer =
        value -> {
          updated.set(System.currentTimeMillis() - started.get());
          counter.set(value);
          onIdleCalled.incrementAndGet();
        };

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(d1))
            .listener(new Composite(consumer))
            .fileHashing(true)
            .onIdleTimeout(1000)
            .build();

    started.set(System.currentTimeMillis());
    watcher.watchAsync();

    Files.createTempFile(d1, "parent1-", ".dat");
    doDelay(800);
    Files.createTempFile(d1, "parent2-", ".dat");
    Files.createTempFile(d1, "parent3-", ".dat");
    doDelay(1200);
    Files.createTempFile(d1, "parent4-", ".dat");
    doDelay(100);
    Files.createTempFile(d1, "parent5-", ".dat");
    doDelay(2000);
    Files.createTempFile(d1, "parent6-", ".dat");
    doDelay(800);
    Files.createTempFile(d1, "parent7-", ".dat");
    doDelay(1100);

    assertEquals(3, onIdleCalled.get());
    assertEquals(isMac() ? 15 : 7, counter.get());
    watcher.close();
  }

  private void doDelay(long timeout) {
    try {
      Thread.sleep(timeout);
    } catch (InterruptedException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  public static class Composite implements DirectoryChangeListener {

    private Consumer<Integer> counter;

    public Composite(Consumer<Integer> counter) {
      this.counter = counter;
    }

    @Override
    public void onEvent(DirectoryChangeEvent event) {}

    @Override
    public void onIdle(int count) {
      counter.accept(count);
    }
  }
}
