package io.quarkiverse.mcp.servers.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.mcp.servers.filesystem.MCPServerFS;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusComponentTest
@TestConfigProperty(key = "bar", value = "true") 
class MCPServerFSTest {

    @Inject MCPServerFS mcpServerFS;
    
    @TempDir
    Path tempDir;

    @Test
    void test() {
    } 
} 