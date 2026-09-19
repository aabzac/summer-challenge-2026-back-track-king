package btk.submit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only link from the compiled local merger and current source files to an exported artifact. */
public final class ArtifactCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("spec artifact-directory");
        Path directory = Path.of(args[1]);
        String generated = FileMerger.merge(args[0]);
        FileMerger.checkSourceSize(generated);
        byte[] existing = Files.readAllBytes(directory.resolve("Player.java"));
        if (!java.util.Arrays.equals(generated.getBytes(StandardCharsets.UTF_8), existing))
            throw new IllegalStateException("Export differs from the current local source/configuration: " + directory);
        var expected = JsonParser.parseString(new GsonBuilder().create().toJson(FileMerger.manifest(args[0], generated)));
        var actual = JsonParser.parseString(Files.readString(directory.resolve("manifest.json")));
        if (!expected.equals(actual)) throw new IllegalStateException("Export manifest differs: " + directory);
        System.out.println("Exact current-source artifact verified: " + args[0] + " " + FileMerger.sha256(generated));
    }
}
