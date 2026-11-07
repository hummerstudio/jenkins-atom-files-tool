package atom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.testing.Atom;
import com.jenkinschina.atom.sdk.testing.AtomResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The files-tool atom, started the way the runner starts it.
 *
 * <p>These are the checks the old {@code tools/smoke.sh} made: touch, sha256, find (including the {@code **}{@code /}
 * zero-directory boundary) and readCSV.
 */
class FilesToolIT {

    private Atom atom;

    @BeforeEach
    void setUp() throws Exception {
        atom = Atom.of();
    }

    @Test
    @DisplayName("touch creates every file and the directories in between")
    void touches() throws Exception {
        AtomResult run = atom.input("{\"operation\": \"touch\", \"files\": \"a/b/one.txt, two.txt\"}").run();

        run.assertOk();
        assertTrue(run.exists("a/b/one.txt"), "a/b/one.txt");
        assertTrue(run.exists("two.txt"), "two.txt");
    }

    @Test
    @DisplayName("sha256 writes the checksums of the matched files")
    void hashes() throws Exception {
        AtomResult run = atom.file("dist/a.jar", "hello")
                .input("{\"operation\": \"sha256\", \"pattern\": \"dist/*.jar\", \"output\": \"checksums.sha256\"}")
                .run();

        run.assertOk();
        assertTrue(run.file("checksums.sha256")
                        .contains("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"),
                "checksum:\n" + run.file("checksums.sha256"));
    }

    @Test
    @DisplayName("find counts every matching file")
    void finds() throws Exception {
        AtomResult run = atom.file("x/one.txt", "a")
                .file("y.txt", "b")
                .input("{\"operation\": \"find\", \"pattern\": \"**/*.txt\"}")
                .run();

        run.assertOk();
        assertEquals("2", run.field("count"));
    }

    @Test
    @DisplayName("readcsv returns the first value of the named column")
    void readsCsv() throws Exception {
        AtomResult run = atom.file("pkg.csv", "name,version\nparcel,2.4.1\n")
                .input("{\"operation\": \"readcsv\", \"file\": \"pkg.csv\", \"column\": \"version\"}")
                .run();

        run.assertOk();
        assertEquals("2.4.1", run.field("first"));
    }

    @Test
    @DisplayName("**/ matches zero directories: src/**/*.py finds src/a.py and src/x/b.py")
    void globStarStarMatchesZeroDirectories() throws Exception {
        AtomResult run = atom.file("src/top.py", "a=1")
                .file("src/nested/deep.py", "b=2")
                .file("other.txt", "c=3")
                .input("{\"operation\": \"find\", \"pattern\": \"src/**/*.py\"}")
                .run();

        run.assertOk();
        assertEquals("2", run.field("count"),
                "expected 2 files for src/**/*.py, got " + run.field("count") + ":\n" + run.file("output.json"));
    }
}
