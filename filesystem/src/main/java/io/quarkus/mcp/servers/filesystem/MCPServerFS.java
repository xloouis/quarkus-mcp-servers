package io.quarkus.mcp.servers.filesystem;

import static java.nio.file.Files.exists;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.logging.Log;

public class MCPServerFS {
    
    private List<String> allowedPaths;
    private ObjectMapper mapper;

    public MCPServerFS(
            @ConfigProperty(name = "fileserver.paths") List<String> allowedPaths,
            ObjectMapper mapper) {
        this.allowedPaths = allowedPaths.stream().map(this::expandHome).collect(Collectors.toList());
        this.mapper = mapper;
    }

    String expandHome(String filepath) {
        if (filepath.startsWith("~/") || filepath.equals("~")) {
            Log.info("Expanding home path: " + filepath);
            return Path.of(System.getProperty("user.home"), filepath.substring(1))
                    .toString();
        }
        return filepath;
    }

    Path validateAndResolvePath(String path) {
        path = expandHome(path);
        
        Path resolvedPath = Path.of(path).normalize();
        Log.info("Resolved path: " + resolvedPath);
        if (!allowedPaths.stream().anyMatch(allowedPath -> resolvedPath.startsWith(Path.of(allowedPath).normalize()))) {
            throw new ToolCallException("Access denied: Path is not within allowed directories", null);
        }
        return resolvedPath;
    }

    Path validatePath(String requestedPath) throws IOException {
        String expandedPath = expandHome(requestedPath);
        
        // resolve relative paths to current working dir if need bePat
        Path absolute = Path.of(expandedPath).isAbsolute() ? Path.of(expandedPath)
                : Path.of(System.getProperty("user.dir")).resolve(expandedPath);

        Path normalizedRequested = absolute.normalize();

        // Check if path is within allowed directories
        boolean isAllowed = allowedPaths.stream()
                .map(dir -> Path.of(dir).normalize())
                .anyMatch(dir -> normalizedRequested.startsWith(dir));

        if (!isAllowed) {
            throw new ToolCallException(
                    String.format("Access denied - path outside allowed directories: %s not in %s",
                            absolute, String.join(", ", allowedPaths)),
                    null);
        }

        try {
            // Handle symlinks by checking their real path
            Path realPath = absolute.toRealPath();
            Path normalizedReal = realPath.normalize();

            boolean isRealPathAllowed = allowedPaths.stream()
                    .map(dir -> Path.of(dir).normalize())
                    .anyMatch(dir -> normalizedReal.startsWith(dir));

            if (!isRealPathAllowed) {
                throw new ToolCallException("Access denied - symlink target outside allowed directories", null);
            }
            return realPath;

        } catch (IOException e) {
            // For new files that don't exist yet, verify parent directory
            Path parentDir = absolute.getParent();
            try {
                Path realParentPath = parentDir.toRealPath();
                Path normalizedParent = realParentPath.normalize();

                boolean isParentAllowed = allowedPaths.stream()
                        .map(dir -> Path.of(dir).normalize())
                        .anyMatch(dir -> normalizedParent.startsWith(dir));

                if (!isParentAllowed) {
                    throw new ToolCallException("Access denied - parent directory outside allowed directories",
                            null);
                }
                return absolute;

            } catch (IOException ex) {
                throw new ToolCallException("Parent directory does not exist: " + parentDir, ex);
            }
        }
    }

    @Tool(description = "Read the complete contents of a file from the file system. Handles various text encodings and provides detailed error messages if the file cannot be read. Use this tool when you need to examine the contents of a single file. Only works within allowed directories.")
    String read_file(@ToolArg(description = "Path to the file to read") String path) {
        Path resolvedPath = validateAndResolvePath(path);
        if (!exists(resolvedPath)) {
            throw new ToolCallException("Path does not exist: " + path, null);
        }
        try {
            return Files.readString(resolvedPath);
        } catch (IOException e) {
            throw new ToolCallException("Failed to read file: " + e.getMessage(), e);
        }
    }

    String valueAsString(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new ToolCallException("Failed to serialize object: " + e.getMessage(), e);
        }
    }

    @Tool(description = "Read the contents of multiple files simultaneously. This is more efficient than reading files one by one when you need to analyze or compare multiple files. Each file's content is returned with its path as a reference. Failed reads for individual files won't stop the entire operation. Only works within allowed directories.")
    String read_multiple_files(@ToolArg(description = "List of file paths to read") List<String> paths) {

        Map<String, String> result = new HashMap<>();
        
        try {
            for (String path : paths) {
                var realpath = validateAndResolvePath(path);
                result.put(path, Files.readString(realpath));
            }
        } catch (IOException e) {
            throw new ToolCallException("Failed to read file: " + e.getMessage(), e);
        }
        return valueAsString(result);
    }

    @Tool(description = "Create a new file or completely overwrite an existing file with new content. Use with caution as it will overwrite existing files without warning. Handles text content with proper encoding. Only works within allowed directories.")
    String write_file(@ToolArg(description = "Path where to write the file") String path,
            @ToolArg(description = "Content to write to the file") String content) {
        // TODO: Implement file writing logic
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Make line-based edits to a text file. Each edit replaces exact line sequences with new content. Returns a git-style diff showing the changes made. Only works within allowed directories.")
    String edit_file(@ToolArg(description = "Path to the file to edit") String path,
            @ToolArg(description = "List of line edits to apply") List<Map<String, String>> edits) {
        // TODO: Implement file editing logic
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Create a new directory or ensure a directory exists. Can create multiple nested directories in one operation. If the directory already exists, this operation will succeed silently. Perfect for setting up directory structures for projects or ensuring required paths exist. Only works within allowed directories.")
    String create_directory(@ToolArg(description = "Path of directory to create") String path) {
        // TODO: Implement directory creation logic
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Get a detailed listing of all files and directories in a specified path. Results clearly distinguish between files and directories with [FILE] and [DIR] prefixes. This tool is essential for understanding directory structure and finding specific files within a directory. Only works within allowed directories.")
    String list_directory(@ToolArg(description = "Path to list contents of") String path, McpLog logger) {
        Path resolvedPath = validateAndResolvePath(path);
        logger.info("Listing directory: " + resolvedPath);
        if (!exists(resolvedPath)) {
            throw new ToolCallException("Path does not exist: " + path, null);
        }
        if (!Files.isDirectory(resolvedPath)) {
            throw new ToolCallException("Path is not a directory: " + path, null);
        }

        try {
            StringBuilder listing = new StringBuilder();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolvedPath)) {
                for (Path entry : stream) {
                    String prefix = Files.isDirectory(entry) ? "[DIR]  " : "[FILE] ";
                    listing.append(prefix).append(entry.getFileName()).append("\n");
                }
            }
            return listing.toString();
        } catch (IOException e) {
            throw new ToolCallException("Failed to list directory: " + e.getMessage(), e);
        }
    }


    @Tool(description = "Get a recursive tree view of files and directories as a JSON structure. Each entry includes 'name', 'type' (file/directory), and 'children' for directories. Files have no children array, while directories always have a children array (which may be empty). The output is formatted with 2-space indentation for readability. Only works within allowed directories.")
    String directory_tree(@ToolArg(description = "Root path to create tree from") String path) {
        Path resolvedPath = validateAndResolvePath(path);
        if (!exists(resolvedPath)) {
            throw new ToolCallException("Path does not exist: " + path, null);
        }

        record TreeEntry(String name, String type, List<TreeEntry> children) {
        }

        try {
            var buildTree = new Function<Path, TreeEntry>() {
                @Override
                public TreeEntry apply(Path path) {
                    try {
                        String name = path.getFileName().toString();
                        if (Files.isDirectory(path)) {
                            List<TreeEntry> children = new ArrayList<>();
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                                for (Path child : stream) {
                                    children.add(this.apply(child));
                                }
                            }
                            return new TreeEntry(name, "directory", children);
                        } else {
                            return new TreeEntry(name, "file", null);
                        }
                    } catch (IOException e) {
                        throw new ToolCallException("Failed to build directory tree: " + e.getMessage(), e);
                    }
                }
            };

            TreeEntry root = buildTree.apply(resolvedPath);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            Throwable cause = e instanceof ToolCallException ? e : e.getCause();
            throw new ToolCallException("Failed to build directory tree: " + cause.getMessage(), cause);
        }
    }

    @Tool(description = "Move or rename files and directories. Can move files between directories and rename them in a single operation. If the destination exists, the operation will fail. Works across different directories and can be used for simple renaming within the same directory. Both source and destination must be within allowed directories.")
    String move_file(@ToolArg(description = "Source path") String source,
            @ToolArg(description = "Destination path") String destination) {
        // TODO: Implement move file logic
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Recursively search for files and directories matching a pattern. Searches through all subdirectories from the starting path. The search is case-insensitive and matches partial names. Returns full paths to all matching items. Great for finding files when you don't know their exact location. Only searches within allowed directories.")
    String search_files(@ToolArg(description = "Starting path for search") String path,
            @ToolArg(description = "Pattern to search for") String pattern) {
        // TODO: Implement file search logic
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Retrieve detailed metadata about a file or directory. Returns comprehensive information including size, creation time, last modified time, permissions, and type. This tool is perfect for understanding file characteristics without reading the actual content. Only works within allowed directories.")
    String get_file_info(@ToolArg(description = "Path to get info for") String path) {
        // TODO: Implement file info logic
        throw new ToolCallException("Not implemented yet", null);
    }

    @Tool(description = "Returns the list of directories that this server is allowed to access. Use this to understand which directories are available before trying to access files.")
    String list_allowed_directories() {
        try {
            return mapper.writeValueAsString(allowedPaths);
        } catch (Exception e) {
            throw new ToolCallException("Failed to list allowed directories: " + e.getMessage(), e);
        }
    }

}