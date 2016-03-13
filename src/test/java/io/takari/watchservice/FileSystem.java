package io.takari.watchservice;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;

import com.google.common.collect.Lists;

public class FileSystem {

  private final Path directoryToOperateOn;
  private final int waitBeforeDelete;
  private final List<FileSystemAction> actions;

  public FileSystem(Path directoryToOperateOn, int waitBeforeDelete) {
    this.directoryToOperateOn = directoryToOperateOn;
    this.waitBeforeDelete = waitBeforeDelete;
    this.actions = new ArrayList<FileSystemAction>();
  }

  public FileSystem create(String path) {
    return create(path, path);
  }

  public FileSystem create(String path, String content) {
    return create(new File(path), content);
  }

  public FileSystem create(File path, String content) {
    actions.add(new FileSystemAction(ENTRY_CREATE, new File(directoryToOperateOn.toFile(), path.getName()).toPath() , content));
    return this;
  }

  public FileSystem update(String path, String content) {
    return update(new File(path), content);
  }

  public FileSystem update(File path, String content) {
    actions.add(new FileSystemAction(ENTRY_MODIFY, new File(directoryToOperateOn.toFile(), path.getName()).toPath(), content));
    return this;
  }

  public FileSystem delete(String path) {
    return delete(new File(path));
  }

  public FileSystem delete(File path) {
    actions.add(new FileSystemAction(ENTRY_DELETE, new File(directoryToOperateOn.toFile(), path.getName()).toPath()));
    return this;
  }
  
  public FileSystem wait(int millis) {
    actions.add(new FileSystemAction(millis));
    return this;
  }
  
  public List<FileSystemAction> actions() {
    List<FileSystemAction> realActions = Lists.newArrayList();
    for(FileSystemAction a : actions) {
      if(a.millis == 0) {
        realActions.add(a);
      }
    }    
    return realActions;
  }
  
  public void playActions() throws Exception {
    for (FileSystemAction action : actions) {
      if (action.kind == ENTRY_CREATE) {
        FileUtils.fileWrite(action.path.toFile(), action.content);
      } else if (action.kind == ENTRY_MODIFY) {
        FileUtils.fileAppend(action.path.toFile().getAbsolutePath(), action.content);
        action.path.toFile().setLastModified(new Date().getTime());
      } else if (action.kind == ENTRY_DELETE) {
        action.path.toFile().delete();
      } else {
        try {
          Thread.sleep(action.millis);
        } catch (InterruptedException e) {
        }            
      }
    }
  }

  public static class FileSystemAction {
    enum Type {
      CREATE, UPDATE, DELETE, WAIT
    };

    final WatchEvent.Kind<Path> kind;
    final Path path;
    final String content;
    final int millis;
    
    FileSystemAction(int millis) {
      this.millis = millis;
      this.kind = null;
      this.path = null;
      this.content = null;      
    }

    FileSystemAction(WatchEvent.Kind<Path> type, Path path) {
      this(type, path, null);
    }

    FileSystemAction(WatchEvent.Kind<Path> type, Path path, String content) {
      this.kind = type;
      this.path = path;
      this.content = content;
      this.millis = 0;
    }
  }
}
