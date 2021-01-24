package io.methvin.watchservice;


import io.methvin.watcher.ChangeSet;
import io.methvin.watcher.ChangeSetListener;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.awaitility.Awaitility.await;

public class ChangeSetTest {

   private Path               tmpDir;
   private DirectoryWatcher   watcher;
   private ChangeSetListener  listener;
   private EventRecorder      recorder;
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
      // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is consumed.
      Path d1 = this.tmpDir.resolve("d1");
      init(d1);

      final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
      waitRecorderSize(3,1);

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,2);
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
      waitRecorderSize(3,1);

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,2);

      Files.deleteIfExists(f1);
      waitRecorderSize(3,3);


      ChangeSet changeSet = listener.getChangeSet().get(d1);

      // Even though there are
      assertEquals(0, changeSet.created().size());
      assertEquals(0, changeSet.deleted().size());
      assertEquals(0, changeSet.modified().size());

      watcher.close();
   }

   @Test
   public void transitionToModify() throws IOException {
      // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is consumed.
      Path d1 = this.tmpDir.resolve("d1");
      init(d1);

      final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
      waitRecorderSize(3,1);

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,2);

      ChangeSet changeSet = listener.getChangeSet().get(d1);
      assertEquals(1, changeSet.created().size());

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,3);


      changeSet = listener.getChangeSet().get(d1);
      assertEquals(0, changeSet.created().size());
      assertEquals(1, changeSet.modified().size());

      watcher.close();
   }

   @Test
   public void normaliseModify() throws IOException {
      // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is consumed.
      Path d1 = this.tmpDir.resolve("d1");
      init(d1);

      final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
      waitRecorderSize(3,1);

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,2);

      ChangeSet changeSet = listener.getChangeSet().get(d1);
      assertEquals(1, changeSet.created().size());


      // This performance two writes, and thus two modify events
      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,3);

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,4);

      changeSet = listener.getChangeSet().get(d1);
      assertEquals(1, changeSet.modified().size());

      watcher.close();
   }

   @Test
   public void transitionToDeleted() throws IOException {
      // An entry that is logged as CREATED, stays CREATED for all updates, until the ChangeSet is consumed.
      Path d1 = this.tmpDir.resolve("d1");
      init(d1);

      final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
      waitRecorderSize(3,1);

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,2);

      ChangeSet changeSet = listener.getChangeSet().get(d1);
      assertEquals(1, changeSet.created().size());

      Files.write(f1, new byte[] {counter++});
      waitRecorderSize(3,3);

      changeSet = listener.getChangeSet().get(d1);
      assertEquals(0, changeSet.created().size());
      assertEquals(1, changeSet.modified().size());

      Files.deleteIfExists(f1);
      waitRecorderSize(3,4);
      changeSet = listener.getChangeSet().get(d1);
      assertEquals(1, changeSet.deleted().size());

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
      waitRecorderSize(3,3);

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

      Composite composite = new Composite(listener, recorder);
      watcher =
         DirectoryWatcher.builder()
                         .paths(Arrays.asList(paths))
                         .listener(composite)
                         .fileHashing(true)
                         .build();

      watcher.watchAsync();
   }

   private void waitRecorderSize(int atMost, int untilSize) {
      await().atMost(atMost, TimeUnit.SECONDS)
             .pollDelay(100, TimeUnit.MILLISECONDS)
             .until(() -> this.recorder.events.size() == untilSize);
   }

   public static class Composite implements DirectoryChangeListener {
      private List<DirectoryChangeListener> listeners;

      public Composite(DirectoryChangeListener... listeners) {
         this.listeners = Arrays.asList(listeners);
      }

      @Override public void onEvent(DirectoryChangeEvent event) throws IOException {
         for(DirectoryChangeListener listener : listeners) {
            listener.onEvent(event);
         }
      }

   }

   class EventRecorder implements DirectoryChangeListener {

      private List<DirectoryChangeEvent> events = new ArrayList<>();

      @Override
      public void onEvent(DirectoryChangeEvent event) {
         this.events.add(event);
      }
   }

}
