package io.methvin.watcher;

import com.typesafe.config.Config;

public class Settings {

    private final boolean preventFileHashing;

    public Settings(Config config){
        preventFileHashing = config.getBoolean("io.methvin.prevent-file-hashing");
    }

    public boolean isPreventFileHashing() {
        return preventFileHashing;
    }
}
