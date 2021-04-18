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

package io.methvin.watcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class OnTimeoutListener implements DirectoryChangeListener {

  private final Object lock = new Object() {};

  private final int timeout;

  private ScheduledExecutorService service;
  private ScheduledFuture currentTask;

  public OnTimeoutListener(int timeout) {
    this.timeout = timeout;

    if (timeout >= 0) {
      service = Executors.newSingleThreadScheduledExecutor();
    }
  }

  @Override
  public void onEvent(DirectoryChangeEvent event) {
    synchronized (lock) {
      if (timeout >= 0 && currentTask != null) {
        currentTask.cancel(false);
        currentTask = null;
      }
    }
  }

  @Override
  public void onIdle(int count) {
    synchronized (lock) {
      if (timeout >= 0) {
        if (currentTask != null) {
          currentTask.cancel(false);
          currentTask = null;
        }
        currentTask = service.schedule(() -> onTimeout(count), timeout, TimeUnit.MILLISECONDS);
      }
    }
  }

  public abstract void onTimeout(int count);
}
