package io.methvin.watchservice;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;

public class FileSystem {

  private final Path directoryToOperateOn;
  private final List<FileSystemAction> actions;

  public FileSystem(Path directoryToOperateOn) {
    this.directoryToOperateOn = directoryToOperateOn;
    this.actions = new ArrayList<FileSystemAction>();
  }

  public FileSystem create(String path) {
    return create(path, path);
  }

  public FileSystem create(String path, String content) {
    return create(new File(path), content);
  }

  public FileSystem create(File path, String content) {
    Path truePath = new File(directoryToOperateOn.toFile(), path.toString()).toPath();
    actions.add(FileSystemAction.create(truePath, content));
    return this;
  }

  public FileSystem directory(String dir) {
    actions.add(FileSystemAction.mkdir(Paths.get(directoryToOperateOn.toString(), dir)));
    return this;
  }

  public FileSystem update(String path, String content) {
    return update(new File(path), content);
  }

  public FileSystem update(File path, String content) {
    Path truePath = new File(directoryToOperateOn.toFile(), path.toString()).toPath();
    actions.add(FileSystemAction.update(truePath, content));
    return this;
  }

  public FileSystem delete(String path) {
    return delete(new File(path));
  }

  public FileSystem delete(File path) {
    Path truePath = new File(directoryToOperateOn.toFile(), path.toString()).toPath();
    actions.add(FileSystemAction.delete(truePath));
    return this;
  }

  public FileSystem wait(int millis) {
    actions.add(FileSystemAction.waitFor(millis));
    return this;
  }

  public List<FileSystemAction> actions() {
    List<FileSystemAction> realActions = new ArrayList<FileSystemAction>();
    for (FileSystemAction a : actions) {
      if (a.myType == FileSystemAction.Type.COUNTABLE || a.myType == FileSystemAction.Type.MKDIR) {
        System.out.println(String.format("adding action:%s:path:%s:myType:%s:", a.kind, (a.path != null) ? a.path : "", a.myType.name()));
        realActions.add(a);
      }
    }
    return realActions;
  }

  public void playActions() throws Exception {
    for (FileSystemAction action : actions) {
      if (action.kind == ENTRY_CREATE && action.content != null) {
        FileUtils.fileWrite(action.path.toFile(), action.content);
      } else if (action.kind == ENTRY_MODIFY) {
        FileUtils.fileAppend(action.path.toFile().getAbsolutePath(), action.content);
        action.path.toFile().setLastModified(new Date().getTime());
      } else if (action.kind == ENTRY_DELETE) {
        action.path.toFile().delete();
      } else {
        switch (action.myType) {
          case WAIT:
            try {
              Thread.sleep(action.millis);
            } catch (InterruptedException e) {
            }
            break;
          case MKDIR:
            if (!action.path.toFile().exists()) {
              action.path.toFile().mkdirs();
            }
            break;
          case COUNTABLE:
          case NOOP:
            break;
        }
      }
    }
  }

  public static class FileSystemAction {
    enum Type {
      WAIT, MKDIR, COUNTABLE, NOOP
    };

    final WatchEvent.Kind<Path> kind;
    final Path path;
    final String content;
    final int millis;
    final Type myType;

    static FileSystemAction create(Path path, String content) {
      return new FileSystemAction(ENTRY_CREATE, Type.COUNTABLE, path, content, 0);
    }

    static FileSystemAction mkdir(Path path) {
      return new FileSystemAction(ENTRY_CREATE, Type.MKDIR, path, null, 0);
    }

    static FileSystemAction update(Path path, String content) {
      return new FileSystemAction(ENTRY_MODIFY, Type.COUNTABLE, path, content, 0);
    }

    static FileSystemAction delete(Path path) {
      return new FileSystemAction(ENTRY_DELETE, Type.COUNTABLE, path, null, 0);
    }

    static FileSystemAction waitFor(int millis) {
      return new FileSystemAction(null, Type.WAIT, null, null, millis);
    }

    FileSystemAction(WatchEvent.Kind<Path> kind, Type type, Path path, String content, int millis) {
      this.kind = kind;
      this.path = path;
      this.content = content;
      this.millis = millis;
      this.myType = type;
    }
  }
}
