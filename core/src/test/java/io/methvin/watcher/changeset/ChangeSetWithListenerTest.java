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
package io.methvin.watcher.changeset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class ChangeSetWithListenerTest {
  private Path tmpDir;
  private DirectoryWatcher watcher;
  private ChangeSetListener listener;
  private List<BiConsumer<DirectoryChangeEvent.EventType, Map<Path, ChangeSet>>> listeners =
      new ArrayList<>();
  private byte counter = 1;

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
    this.listener =
        new ChangeSetListener(
            (eventType, pathChangeSetMap) ->
                listeners.forEach(l -> l.accept(eventType, pathChangeSetMap)));
  }

  @After
  public void tearDown() throws IOException {
    try {
      FileUtils.deleteDirectory(this.tmpDir.toFile());
    } catch (Exception e) {
    }
  }

  @Test
  public void testEventFired() throws IOException {
    CountDownLatch latch = new CountDownLatch(1);

    Map<DirectoryChangeEvent.EventType, Map<Path, ChangeSet>> holder = new HashMap<>();

    listeners.add(
        (eventType, pathChangeSetMap) -> {
          holder.put(eventType, pathChangeSetMap);
          latch.countDown();
        });

    Path d1 = this.tmpDir.resolve("d1");
    init(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    Files.write(f1, new byte[] {counter++});

    try {
      latch.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    assertEquals(1, holder.size());

    Map.Entry<DirectoryChangeEvent.EventType, Map<Path, ChangeSet>> entry =
        holder.entrySet().iterator().next();

    assertEquals(DirectoryChangeEvent.EventType.CREATE, entry.getKey());
    assertEquals(1, entry.getValue().get(d1).created().size());
    assertEquals(0, entry.getValue().get(d1).deleted().size());
    assertEquals(0, entry.getValue().get(d1).modified().size());
    watcher.close();
  }

  private void init(Path... paths) throws IOException {
    for (Path p : paths) {
      Files.createDirectory(p);
    }

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(paths))
            .listener(listener)
            .fileHashing(true)
            .build();

    watcher.watchAsync();
  }
}
