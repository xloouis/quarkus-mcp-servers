import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

public class WritableFS {

    @Tool(description = "Create a new file or completely overwrite an existing file with new content. Use with caution as it will overwrite existing files without warning. Handles text content with proper encoding. Only works within allowed directories.")
    String write_file(@ToolArg(description = "Path where to write the file") String path,
            @ToolArg(description = "Content to write to the file") String content) {
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Make line-based edits to a text file. Each edit replaces exact line sequences with new content. Returns a git-style diff showing the changes made. Only works within allowed directories.")
    String edit_file(@ToolArg(description = "Path to the file to edit") String path,
            @ToolArg(description = "List of line edits to apply") List<Map<String, String>> edits) {
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Create a new directory or ensure a directory exists. Can create multiple nested directories in one operation. If the directory already exists, this operation will succeed silently. Perfect for setting up directory structures for projects or ensuring required paths exist. Only works within allowed directories.")
    String create_directory(@ToolArg(description = "Path of directory to create") String path) {
        throw new ToolCallException("Not implemented yet", null);
    }
}