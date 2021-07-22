/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Negative tests for simpleserver command-line tool
 * @library /test/lib
 * @modules jdk.httpserver
 * @run testng/othervm CommandLineNegativeTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import org.testng.SkipException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static org.testng.Assert.assertFalse;

public class CommandLineNegativeTest {

    static final Path JAVA_HOME = Path.of(System.getProperty("java.home"));
    static final String JAVA = getJava(JAVA_HOME);
    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("CommandLineNegativeTest");
    static final Path TEST_FILE = TEST_DIR.resolve("file.txt");

    @BeforeTest
    public void setup() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        Files.createDirectories(TEST_DIR);
        Files.createFile(TEST_FILE);
    }

    @DataProvider(name = "badOption")
    public Object[][] badOption() {
        return new Object[][] {
                {"--badOption"},
                {"null"}
        };
    }

    @Test(dataProvider = "badOption")
    public void testBadOption(String opt) throws Throwable {
        out.println("\n--- testBadOption, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt)
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: unknown option: " + opt);
    }

    @DataProvider(name = "tooManyOptionArgs")
    public Object[][] tooManyOptionArgs() {
        return new Object[][] {
                {"-b", "localhost"},
                {"-d", "/some/path"},
                {"-o", "none"},
                {"-p", "0"},
                {"--bind-address", "localhost"},
                {"--directory", "/some/path"},
                {"--output", "none"},
                {"--port", "0"}
                // doesn't fail for -h option
        };
    }

    @Test(dataProvider = "tooManyOptionArgs")
    public void testTooManyOptionArgs(String opt, String arg) throws Throwable {
        out.println("\n--- testTooManyOptionArgs, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, arg, arg)
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: unknown option: " + arg);
    }

    @DataProvider(name = "noArg")
    public Object[][] noArg() {
        return new Object[][] {
                {"-b"},
                {"-d"},
                {"-o"},
                {"-p"},
                {"--bind-address"},
                {"--directory"},
                {"--output"},
                {"--port"}
                // doesn't fail for -h option
        };
    }

    @Test(dataProvider = "noArg")
    public void testNoArg(String opt) throws Throwable {
        out.println("\n--- testNoArg, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt)
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: no value given for " + opt);
    }

    @DataProvider(name = "invalidValue")
    public Object[][] invalidValue() {
        return new Object[][] {
                {"-b", "[127.0.0.1]"},
                {"-b", "badhost"},
                {"--bind-address", "192.168.1.220..."},

//                {"-d", ""},
                // TODO: expect failure at Path::of, not at actual file system access
                //  need to be file system specific?

                {"-o", "bad-output-level"},
                {"--output", "bad-output-level"},

                {"-p", "+-"},
                {"--port", "+-"}
        };
    }

    @Test(dataProvider = "invalidValue")
    public void testInvalidValue(String opt, String val) throws Throwable {
        out.println("\n--- testInvalidValue, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, val)
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: invalid value given for " + opt + ": " + val);
    }

    @DataProvider
    public Object[][] portOptions() { return new Object[][] {{"-p"}, {"--port"}}; }

    @Test(dataProvider = "portOptions")
    public void testPortOutOfRange(String opt) throws Throwable {
        out.println("\n--- testPortOutOfRange, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "65536")  // range 0 to 65535
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: server config failed: " + "port out of range:65536");
    }

    @DataProvider
    public Object[][] directoryOptions() { return new Object[][] {{"-d"}, {"--directory"}}; }

    @Test(dataProvider = "directoryOptions")
    public void testRootNotAbsolute(String opt) throws Throwable {
        out.println("\n--- testRootNotAbsolute, opt=\"%s\" ".formatted(opt));
        var root = Path.of(".");
        assertFalse(root.isAbsolute());
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, root.toString())
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: server config failed: " + "Path is not absolute:");
    }

    @Test(dataProvider = "directoryOptions")
    public void testRootNotADirectory(String opt) throws Throwable {
        out.println("\n--- testRootNotADirectory, opt=\"%s\" ".formatted(opt));
        var file = TEST_FILE.toString();
        assertFalse(Files.isDirectory(TEST_FILE));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, file)
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: server config failed: " + "Path is not a directory: " + file);
    }

    @Test(dataProvider = "directoryOptions")
    public void testRootDoesNotExist(String opt) throws Throwable {
        out.println("\n--- testRootDoesNotExist, opt=\"%s\" ".formatted(opt));
        Path root = TEST_DIR.resolve("not/existent/dir");
        assertFalse(Files.exists(root));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, root.toString())
                .shouldNotHaveExitValue(0)
                .shouldContain("Error: server config failed: " + "Path does not exist: " + root.toString());
    }

    @Test(dataProvider = "directoryOptions")
    public void testRootNotReadable(String opt) throws Throwable {
        out.println("\n--- testRootNotReadable, opt=\"%s\" ".formatted(opt));
        if (Platform.isWindows()) {
            // Not applicable to Windows. Reason: cannot revoke an owner's read
            // access to a directory that was created by that owner
            throw new SkipException("cannot run on Windows");
        }
        Path root = Files.createDirectories(TEST_DIR.resolve("not/readable/dir"));
        try {
            root.toFile().setReadable(false, false);
            assertFalse(Files.isReadable(root));
            simpleserver(JAVA, "-m", "jdk.httpserver", opt, root.toString())
                    .shouldNotHaveExitValue(0)
                    .shouldContain("Error: server config failed: " + "Path is not readable: " + root.toString());
        } finally {
            root.toFile().setReadable(true, false);
        }
    }

    @AfterTest
    public void teardown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
    }

    // --- infra ---

    static String getJava(Path image) {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        Path java = image.resolve("bin").resolve(isWindows ? "java.exe" : "java");
        if (Files.notExists(java))
            throw new RuntimeException(java + " not found");
        return java.toAbsolutePath().toString();
    }

    static OutputAnalyzer simpleserver(String... args) throws Throwable {
        var pb = new ProcessBuilder(args)
                .directory(TEST_DIR.toFile());
        var outputAnalyser = ProcessTools.executeCommand(pb)
                .outputTo(System.out)
                .errorTo(System.out);
        return outputAnalyser;
    }
}
