package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldSaveAndRestoreBoundsWithoutDiscardingOtherSettings() throws IOException {
        Path configFile = temporaryDirectory.resolve("config.ini");
        Files.writeString(configFile, "existing.setting=value\n");
        WindowConfig config = new WindowConfig(configFile);
        Rectangle bounds = new Rectangle(80, 120, 760, 380);

        config.saveBounds("main", bounds);

        assertEquals(bounds, config.loadBounds("main"));
        assertEquals("value", readSetting(configFile, "existing.setting"));
    }

    @Test
    void shouldSaveWhetherAWindowWasMaximized() throws IOException {
        Path configFile = temporaryDirectory.resolve("config.ini");
        WindowConfig config = new WindowConfig(configFile);

        config.saveBounds("results", new Rectangle(80, 120, 760, 380), true);

        assertEquals("true", readSetting(configFile, "results.maximized"));
    }

    @Test
    void shouldReturnNullWhenNoGeometryIsSaved() throws IOException {
        WindowConfig config = new WindowConfig(temporaryDirectory.resolve("config.ini"));

        assertNull(config.loadBounds("main"));
    }

    @Test
    void shouldRejectIncompleteOrInvalidBounds() throws IOException {
        Path configFile = temporaryDirectory.resolve("config.ini");
        WindowConfig config = new WindowConfig(configFile);
        Files.writeString(configFile, "main.x=10\nmain.y=20\n");
        assertThrows(IOException.class, () -> config.loadBounds("main"));

        Files.writeString(configFile, "main.x=10\nmain.y=20\nmain.width=0\nmain.height=300\n");
        assertThrows(IOException.class, () -> config.loadBounds("main"));
    }

    private String readSetting(Path configFile, String name) throws IOException {
        return Files.readAllLines(configFile).stream()
                .filter(line -> line.startsWith(name + "="))
                .map(line -> line.substring(name.length() + 1))
                .findFirst()
                .orElse(null);
    }
}
