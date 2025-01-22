package io.quarkus.mcp.servers.filesystem;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * This is the main entry point for the filesystem server.
 * It will start the server and set the fileserver.paths property based on arguments if present.
 */
@QuarkusMain
class Application {
    public static void main(String[] args) {
        if(args.length > 0) {
            System.setProperty("fileserver.paths", String.join(",", args));
        }

        Quarkus.run(null, 
            (exitCode, exception) -> {
                if(exception != null) {
                    exception.printStackTrace();
                } 
                System.exit(exitCode);
                }, 
                args);
    }
}
