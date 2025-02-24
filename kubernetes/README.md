# Model Context Protocol Server for Kubernetes

This Model Context Protocol(MCP) server enables Large Language Models (LLMs) to interact with your Kubernetes cluster.

The server is implemented using Quarkus MCP and the Fabric8 Kubernetes Client.

Initial idea and implementation is from [Marc Nuri(@manusa)](https://github.com/manusa)

See demo video [here](https://youtu.be/JZn7fUKbRHU).

## General Usage 

1. Install [jbang](https://www.jbang.dev/download/)
2. Configure your MCP Client to run the server as `jbang kubernetes@quarkiverse/quarkus-mcp-servers` (see [Claude Desktop Config](#claude-desktop-config) below)

For now this server uses your already configured Kubernetes configuration. Thus make sure you have a valid kubeconfig that has a valid context setup.

## Claude Desktop Config and [mcp-cli](https://github.com/chrishayuk/mcp-cli) <a id="claude-desktop-config"/>

Add this to your `claude_desktop_config.json` or `server_config.json` file:

```json
{
  "mcpServers": {
    "kubernetes": {
      "command": "jbang",
      "args": [
        "kubernetes@quarkiverse/quarkus-mcp-servers"
      ]
    }
  }
}
```

## Native Image/Executable

## Native Image/Executable

Using native image, the startup time is almost instant. 

You can download the native images from the [release page](https://github.com/quarkiverse/quarkus-mcp-servers/releases).

Then use the executable for your platform in your MCP client.

Example for MacOS arm64 (M1, M2, etc.):

```json
{
  "mcpServers": {
    "kubernetes": {
      "command": "jbang",
      "args": [
        "mcp-server-kubernetes-osx-aarch_64"
      ]
    }
  }
}
```

You can of course also rename the executable to something else, like `mcp-server-kubernetes` if you want.

## How to build/test

```bash
mvn clean install
```

There are profiles to add right JDBC driver to the build for easy testing.

i.e. `h2`, `postgresql`, `sqlite` etc.

Examples:

h2 on an empty in-memory database:

```bash
mvn quarkus:dev -Dh2
```

postgresql with a pre-configured database running in a container:

```bash
podman run -p 5432:5432 -d sakiladb/postgres:latest
mvn quarkus:dev -Dpostgresql
```

sqlite with a pre-configured database running in a container
using `./test.db` as the database file:

```bash
mvn quarkus:dev -Dsqlite -Djdbc.url=jdbc:sqlite:./test.db
```


