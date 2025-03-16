///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
// Update the Quarkus version to what you want here or run jbang with
// `-Dquarkus.version=<version>` to override it.
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.19.2}@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-jackson
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command
public class mcpconfig implements Runnable {

    @CommandLine.Option(names = {"-s", "--server"}, description = "The servers to update")
    List<String> servers;

    @Inject ObjectMapper mapper;

    record MCPServer(String name, String alias, String gav,String command, String... args) {
        public MCPServer(String name, String... args) {
            this(name, name + "@quarkiverse/quarkus-mcp-servers", "io.quarkus.mcp.servers:mcp-server-" + name + ":999-SNAPSHOT", "jbang", args);
        }

        String[] realargs(boolean gav) {
            List<String> args = new ArrayList<>();
            args.addAll(Arrays.asList("--java", "21"));
            if(gav) {
                args.add(gav());
            } else {
                args.add(alias());
            }

            args.addAll(Arrays.asList(this.args));
            return args.toArray(new String[0]);
        }
    }

    MCPServer server(String name, String... args) {
        return new MCPServer(name, args);
    }


    @Override
    public void run() {
    
        var servers = new ArrayList<MCPServer>();
        servers.addAll(
            List.of(
                server("kubernetes"),
                server("jdbc", "jdbc:sqlite:%{https://github.com/jpwhite3/northwind-SQLite3/raw/refs/heads/main/dist/northwind.db}"),
                server("jfx"),
                server("filesystem", "~/code/quarkusio/quarkus", "~/code/jbangdev/jbang")
            ));
                

        String configPath = System.getProperty("user.home") + 
            "/Library/Application Support/Claude/claude_desktop_config.json";

        try {
            
            JsonNode rootNode = mapper.readTree(new File(configPath));
            ObjectNode mcpServers = (ObjectNode) rootNode.get("mcpServers");

            for (MCPServer server : servers) {
                // Create new server config
                ObjectNode serverConfig = mapper.createObjectNode();
                serverConfig.put("command", server.command());
                ArrayNode argsArray = serverConfig.putArray("args");
                for (String arg : server.realargs(true)) {
                    argsArray.add(arg);
                }

                if(mcpServers.has(server.name())) {
                    System.out.println("Updating " + server.name());
                } else {
                    System.out.println("Adding " + server.name());
                }

                mcpServers.set(server.name(), serverConfig);
            }

            // Write back to file
            var prettyPrinter = new DefaultPrettyPrinter();
            prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

            mapper.writer(prettyPrinter)
                .writeValue(new File(configPath), rootNode);

            System.out.println("Successfully updated config for servers " + configPath);

        } catch (IOException e) {
            System.err.println("Error processing config file: " + e.getMessage());
            System.exit(1);
        }
    }

}

@Dependent
class GreetingService {
    void sayHello(String name) {
        System.out.println("Hello " + name + "!");
    }
}