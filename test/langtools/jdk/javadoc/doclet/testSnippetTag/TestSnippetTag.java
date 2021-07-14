/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8201533
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main TestSnippetTag
 */

import builder.ClassBuilder;
import builder.ClassBuilder.MethodBuilder;
import javadoc.tester.JavadocTester;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// FIXME
//   0. Add tests for snippets in all types of elements: e.g., fields
//      and constructors (i.e. not only methods.)
//   1. Add tests for nested structure under "snippet-files/"
//   2. Add negative tests for region
//   3. Add tests for hybrid snippets

/*
 * Some of the below tests could benefit from using a combinatorics library
 * as they are otherwise very wordy.
 */
public class TestSnippetTag extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    private TestSnippetTag() { }

    public static void main(String... args) throws Exception {
        new TestSnippetTag().runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    /*
     * While the "id" and "lang" attributes are advertised in JEP 413, they are
     * currently unused by the implementation. The goal of this test is to make
     * sure that specifying these attributes causes no errors and exhibits no
     * unexpected behavior.
     */
    @Test
    public void testIdAndLangAttributes(Path base) throws IOException {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        final var snippets = List.of(
                """
                {@snippet id="foo" :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet id="foo":
                    Hello, Snippet!
                }
                """,
                """
                {@snippet id='foo' :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet id='foo':
                    Hello, Snippet!
                }
                """,
                """
                {@snippet id=foo :
                    Hello, Snippet!
                }
                """,
// (1) Haven't yet decided on this one. It's a consistency issue. On the one
// hand, `:` is considered a part of a javadoc tag's name; on the other hand,
// snippet markup treats `:` (next-line modifier) as a value terminator.
//                """
//                {@snippet id=foo:
//                    Hello, Snippet!
//                }
//                """,
                """
                {@snippet id="" :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet id="":
                    Hello, Snippet!
                }
                """,
                """
                {@snippet id='':
                    Hello, Snippet!
                }
                """,
// Similarly to (1), haven't yet decided on this one.
//                """
//                {@snippet id=:
//                    Hello, Snippet!
//                }
//                """,
                """
                {@snippet lang="java" :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang="java":
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang='java' :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang='java':
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang=java :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang="properties" :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang="text" :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang="" :
                    Hello, Snippet!
                }
                """,
                """
                {@snippet lang="foo" id="bar" :
                    Hello, Snippet!
                }
                """
        );
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(snippets, (s, i) -> {
            classBuilder.addMembers(
                    MethodBuilder.parse("public void case%s() { }".formatted(i))
                            .setComments(s));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        for (int j = 0; j < snippets.size(); j++) {
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                            Hello, Snippet!
                        </pre>
                        </div>
                        """.formatted(j));
        }
    }

    /*
     * This is a convenience method to iterate through a list.
     * Unlike List.forEach, this method provides the consumer not only with an
     * element but also that element's index.
     *
     * See JDK-8184707.
     */
    private static <T> void forEachNumbered(List<T> list, ObjIntConsumer<? super T> action) {
        for (var iterator = list.listIterator(); iterator.hasNext(); ) {
            action.accept(iterator.next(), iterator.previousIndex());
        }
    }

    @Test
    public void testBadTagSyntax(Path base) throws IOException {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        final var badSnippets = List.of(
                // No newline after `:`
                """
                {@snippet :}
                """,
                """
                {@snippet : }
                """,
                """
                {@snippet
                : }
                """,
                """
                {@snippet
                 : }
                """,
                // Attribute issues
                """
                {@snippet file="}
                """,
                """
                {@snippet file="
                }
                """,
                """
                {@snippet file='}
                """,
                """
                {@snippet file='
                }
                """,
                """
                {@snippet file='
                    }
                """,
                """
                {@snippet
                file='
                    }
                """,
                """
                {@snippet
                file='}
                """,
// This is an interesting case: closing curly terminates the tag, leaving the
// attribute value empty.
                """
                {@snippet
                file=}
                """
// The below commented out cases are worth testing if only to fixate the result.
// It's not that we can do a lot about them anyway.
//                ,
// A missing `:`
//                """
//                {@snippet
//                  Hello  there
//                }
//                """
// A missing `:`
//                """
//                {@snippet
//                    List<String> strings = List.of();
//                }
//                """
        );
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(badSnippets, (s, i) -> {
            classBuilder.addMembers(
                    MethodBuilder.parse("public void case%s() { }".formatted(i)).setComments(s));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        // Not very specific, but good enough
        long actual = Pattern.compile("error: ").matcher(getOutput(Output.OUT)).results().count();
        checking("Number of errors");
        int expected = badSnippets.size();
        if (actual == expected) {
            passed("");
        } else {
            failed(actual + " vs " + expected);
        }
        checkNoCrashes();
    }

    /*
     * When checking for errors, it is important not to confuse one error with
     * another. This method checks that there are no crashes (which are also
     * errors) by checking for stack traces. We never expect crashes.
     */
    private void checkNoCrashes() {
        checking("check crashes");
        Matcher matcher = Pattern.compile("\s*at.*\\(.*\\.java:\\d+\\)")
                .matcher(getOutput(Output.STDERR));
        if (!matcher.find()) {
            passed("");
        } else {
            failed("Looks like a stacktrace: " + matcher.group());
        }
    }

    /*
     * A colon that is not separated from a tag name by whitespace is considered
     * a part of that name. This behavior is historical. For more context see,
     * for example, JDK-4750173.
     */
    @Test
    public void testUnknownTag(Path base) throws IOException {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        final var unknownTags = List.of(
                """
                {@snippet:}
                """,
                """
                {@snippet:
                }
                """
        );
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(unknownTags, (s, i) -> {
            classBuilder.addMembers(
                    MethodBuilder.parse("public void case%s() { }".formatted(i))
                            .setComments(s));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        long actual = Pattern.compile("error: unknown tag: snippet:")
                .matcher(getOutput(Output.OUT)).results().count();
        checking("Number of errors");
        int expected = unknownTags.size();
        if (actual == expected) {
            passed("");
        } else {
            failed(actual + " vs " + expected);
        }
        checkNoCrashes();
    }

    @Test
    public void testInline(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        record TestCase(String input, String expectedOutput) { }

        final var testCases = List.of(
                // minimal empty
                new TestCase("""
                             {@snippet :
                             }
                             """,
                             """
                             """),
                // empty with a newline before `:` as a separator
                new TestCase("""
                             {@snippet
                             :
                             }
                             """,
                             """
                             """),
                // empty with a newline followed by whitespace before `:`
                new TestCase("""
                             {@snippet
                                       :
                             }
                             """,
                             """
                             """),
                // empty with whitespace followed by a newline before `:`
                new TestCase("""
                             {@snippet    \s
                             :
                             }
                             """,
                             """
                             """),
                // basic
                new TestCase("""
                             {@snippet :
                                 Hello, Snippet!
                             }
                             """,
                             """
                                 Hello, Snippet!
                             """),
                // leading whitespace before `:`
                new TestCase("""
                             {@snippet       :
                                 Hello, Snippet!
                             }
                             """,
                             """
                                 Hello, Snippet!
                             """),
                // trailing whitespace after `:`
                new TestCase("""
                             {@snippet :      \s
                                 Hello, Snippet!
                             }
                             """,
                             """
                                 Hello, Snippet!
                             """),
                // attributes do not interfere with body
                new TestCase("""
                             {@snippet  attr1="val1"    :
                                 Hello, Snippet!
                             }
                             """,
                             """
                                 Hello, Snippet!
                             """),
                // multi-line
                new TestCase("""
                             {@snippet :
                                 Hello
                                 ,
                                  Snippet!
                             }
                             """,
                             """
                                 Hello
                                 ,
                                  Snippet!
                             """),
                // leading empty line
                new TestCase("""
                             {@snippet :

                                 Hello
                                 ,
                                  Snippet!
                             }
                             """,
                             """

                                 Hello
                                 ,
                                  Snippet!
                             """),
                // trailing empty line
                new TestCase("""
                             {@snippet :
                                 Hello
                                 ,
                                  Snippet!

                             }
                             """,
                             """
                                 Hello
                                 ,
                                  Snippet!

                             """),
                // controlling indent with `}`
                new TestCase("""
                             {@snippet :
                                 Hello
                                 ,
                                  Snippet!
                                 }
                             """,
                             """
                             Hello
                             ,
                              Snippet!
                             """
                ),
                // no trailing newline before `}
                new TestCase("""
                             {@snippet :
                                 Hello
                                 ,
                                  Snippet!}
                             """,
                             """
                             Hello
                             ,
                              Snippet!"""),
                // trailing space is stripped
                new TestCase("""
                             {@snippet :
                                 Hello
                                 ,    \s
                                  Snippet!
                             }
                             """,
                             """
                                 Hello
                                 ,
                                  Snippet!
                             """),
                // escapes of Text Blocks and string literals are not interpreted
                new TestCase("""
                             {@snippet :
                                 \\b\\t\\n\\f\\r\\"\\'\\\
                                 Hello\\
                                 ,\\s
                                  Snippet!
                             }
                             """,
                             """
                                 \\b\\t\\n\\f\\r\\"\\'\\    Hello\\
                                 ,\\s
                                  Snippet!
                             """),
                // HTML is not interpreted
                new TestCase("""
                             {@snippet :
                                 </pre>
                                     <!-- comment -->
                                 <b>&trade;</b> &#8230; " '
                             }
                             """,
                             """
                                 &lt;/pre&gt;
                                     &lt;!-- comment --&gt;
                                 &lt;b&gt;&amp;trade;&lt;/b&gt; &amp;#8230; " '
                             """)
        );
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(testCases, (t, id) -> {
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments(t.input()));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        forEachNumbered(testCases, (t, id) -> {
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                        %s</pre>
                        </div>""".formatted(id, t.expectedOutput()));
        });
    }

    @Test
    public void testExternalFile(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        record TestCase(String input, Function<String, String> expectedTransformation) {

            TestCase(String input) {
                this(input, Function.identity());
            }
        }

        final var testCases = List.of(
                new TestCase("""
                             Hello, Snippet!
                             """),
                new TestCase("""
                                 Hello, Snippet!
                             """),
                new TestCase("""
                                 Hello
                                 ,
                                  Snippet!
                             """),
                new TestCase("""

                                 Hello
                                 ,
                                  Snippet!
                             """),
                new TestCase("""
                                 Hello
                                 ,
                                  Snippet!

                             """),
                new TestCase("""
                                 Hello
                                 ,        \s
                                  Snippet!
                             """,
                             String::stripIndent),
                new TestCase("""
                             Hello
                             ,
                              Snippet!"""),
                new TestCase("""
                                 \\b\\t\\n\\f\\r\\"\\'\\\
                                 Hello\\
                                 ,\\s
                                  Snippet!
                             """),
                new TestCase("""
                                 </pre>
                                     <!-- comment -->
                                 <b>&trade;</b> &#8230; " '
                             """,
                             s -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")),
                new TestCase("""
                                 &lt;/pre&gt;
                                     &lt;!-- comment --&gt;
                                 &lt;b&gt;&amp;trade;&lt;/b&gt; &amp;#8230; " '
                             """,
                             s -> s.replaceAll("&", "&amp;"))
        );
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(testCases, (t, id) -> {
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                 {@snippet file="%s.txt"}
                                                 """.formatted(id)));
            addSnippetFile(srcDir, "pkg", "%s.txt".formatted(id), t.input());
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        forEachNumbered(testCases, (testCase, index) -> {
            String expectedOutput = testCase.expectedTransformation().apply(testCase.input());
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                        %s</pre>
                        </div>""".formatted(index, expectedOutput));
        });
    }

    // FIXME
    //   Explore the toolbox.ToolBox.writeFile and toolbox.ToolBox.writeJavaFiles methods:
    //   see if any of them could be used instead of this one
    private void addSnippetFile(Path srcDir, String packageName, String fileName, String content) throws UncheckedIOException {
        String[] components = packageName.split("\\.");
        Path snippetFiles = Path.of(components[0], Arrays.copyOfRange(components, 1, components.length)).resolve("snippet-files");
        try {
            Path p = Files.createDirectories(srcDir.resolve(snippetFiles));
            Files.writeString(p.resolve(fileName), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testInlineSnippetInDocFiles(Path base) throws IOException {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        // If there is no *.java files, javadoc will not create an output
        // directory; so this class is created solely to trigger output.
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void m() { }")
                                // a (convenience) user entry point to the html file (not used by test)
                                .setComments("<a href=\"doc-files/file.html\">A document</a>"))
                .write(srcDir);
        var content = """
                              Unlike Java files, HTML files don't mind hosting
                              the */ sequence in a @snippet tag
                      """;
        String html = """
                      <!DOCTYPE html>
                      <html lang="en">
                        <head>
                          <meta charset="utf-8">
                          <title>title</title>
                        </head>
                        <body>
                          <!-- yet another user entry point to the html file (not used by test): through an index page -->
                          {@index this A document}
                          {@snippet :
                              %s}
                        </body>
                      </html>
                      """.formatted(content);
        Path p = Files.createDirectories(srcDir.resolve("pkg").resolve("doc-files"));
        Files.writeString(p.resolve("file.html"), html, StandardOpenOption.CREATE_NEW);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/doc-files/file.html", true, content);
    }

    @Test
    public void testExternalSnippetInDocFiles(Path base) throws IOException {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        // If there is no *.java files, javadoc will not create an output
        // directory; so this class is created solely to trigger output.
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void m() { }")
                                // a (convenience) user entry point to the html file (not used by test)
                                .setComments("<a href=\"doc-files/file.html\">A document</a>"))
                .write(srcDir);
        String html = """
                      <!DOCTYPE html>
                      <html lang="en">
                        <head>
                          <meta charset="utf-8">
                          <title>title</title>
                        </head>
                        <body>
                          <!-- yet another user entry point to the html file (not used by test): through an index page -->
                          {@index this A document}
                          {@snippet file="file.txt"}
                        </body>
                      </html>
                      """;
        Path p = Files.createDirectories(srcDir.resolve("pkg").resolve("doc-files"));
        Files.writeString(p.resolve("file.html"), html, StandardOpenOption.CREATE_NEW);
        String content = """
                            Unlike Java files, text files don't mind hosting
                            the */ sequence in a @snippet tag
                         """;
        addSnippetFile(srcDir, "pkg", "file.txt", content);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/doc-files/file.html", true, content);
    }

    @Test
    public void testExternalFileNotFound(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet file="%s"}
                                             """.formatted(fileName)))
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: File not found: %s""".formatted(fileName));
        checkNoCrashes();
    }

    @Test // FIXME perhaps this could be unified with testExternalFile
    public void testExternalFileModuleSourcePath(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "snippet.txt";
        String MODULE_NAME = "mdl1";
        String PACKAGE_NAME = "pkg1.pkg2";
        Path moduleDir = new ModuleBuilder(tb, MODULE_NAME)
                .exports(PACKAGE_NAME)
                .write(srcDir);
        new ClassBuilder(tb, String.join(".", PACKAGE_NAME, "A"))
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet file="%s"}
                                             """.formatted(fileName)))
                .write(moduleDir);
        addSnippetFile(moduleDir, PACKAGE_NAME, fileName, "content");
        javadoc("-d", outDir.toString(),
                "--module-source-path", srcDir.toString(),
                "--module", MODULE_NAME);
        checkExit(Exit.OK);
    }

    @Test // FIXME perhaps this could be unified with testExternalFileNotFound
    public void testExternalFileNotFoundModuleSourcePath(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        var MODULE_NAME = "mdl1";
        var PACKAGE_NAME = "pkg1.pkg2";
        Path moduleDir = new ModuleBuilder(tb, MODULE_NAME)
                .exports(PACKAGE_NAME)
                .write(srcDir);
        new ClassBuilder(tb, String.join(".", PACKAGE_NAME, "A"))
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet file="%s"}
                                             """.formatted(fileName)))
                .write(moduleDir);
        javadoc("-d", outDir.toString(),
                "--module-source-path", srcDir.toString(),
                "--module", MODULE_NAME);
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: File not found: %s""".formatted(fileName));
        checkNoCrashes();
    }

    @Test
    public void testNoContents(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet}
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:3: error: @snippet does not specify contents""");
        checkNoCrashes();
    }

    @Test
    public void testConflict20(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet file="" class="" :
                                 Hello, Snippet!
                             }
                             """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        // FIXME
        //   In this and all similar tests check that there are no other errors, let alone errors related to {@snippet}
        //   To achieve that, we might need to change JavadocTester (i.e. add "consume output", "check that the output is empty", etc.)
        checkOutput(Output.OUT, true,
                    """
                    A.java:3: error: @snippet specifies multiple external contents, which is ambiguous""");
        checkNoCrashes();
    }

    @Test
    public void testConflict30(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet class="" file="" :
                                 Hello, Snippet!
                             }
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutputEither(Output.OUT,
                          """
                          A.java:3: error: @snippet specifies multiple external contents, which is ambiguous""");
        checkNoCrashes();
    }

    // FIXME: perhaps this method could be added to JavadocTester
    private void checkOutputEither(Output out, String first, String... other) {
        checking("checkOutputEither");
        String output = getOutput(out);
        Stream<String> strings = Stream.concat(Stream.of(first), Stream.of(other));
        Optional<String> any = strings.filter(output::contains).findAny();
        if (any.isPresent()) {
            passed(": following text is found:\n" + any.get());
        } else {
            failed(": nothing found");
        }
    }

    @Test
    public void testConflict60(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet file="" file=""}
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:3: error: repeated attribute: "file\"""");
        checkNoCrashes();
    }

    @Test
    public void testConflict70(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet class="" class="" }
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:3: error: repeated attribute: "class\"""");
        checkNoCrashes();
    }

    @Test
    public void testConflict80(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet class="" class="" :
                                 Hello, Snippet!
                             }
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutputEither(Output.OUT,
                          """
                          A.java:3: error: repeated attribute: "class\"""",
                          """
                          A.java:3: error: @snippet specifies external and inline contents, which is ambiguous""");
        checkNoCrashes();
    }

    @Test
    public void testConflict90(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet file="" file="" :
                                 Hello, Snippet!
                             }
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutputEither(Output.OUT,
                          """
                          A.java:3: error: repeated attribute: "file\"""",
                          """
                          A.java:3: error: @snippet specifies external and inline contents, which is ambiguous""");
        checkNoCrashes();
    }

    // Those are excerpts from the diagnostic messages for two different tags that sit on the same line:
    //
    //     A.java:3: error: @snippet does not specify contents
    //     A.java:3: error: @snippet does not specify contents
    //
    // FIXME: fix and uncomment this test if and when that problem with diagnostic output has been resolved
    //
    //@Test
    public void testErrorPositionResolution(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                             {@snippet} {@snippet}
                             """)
                .setModifiers("public", "class")
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:3: error: @snippet does not specify contents""",
                    """
                    A.java:3: error: @snippet does not specify contents""");
        checkNoCrashes();
    }

    @Test
    public void testRegion(Path base) throws Exception {
        record TestCase(Snippet snippet, String expectedOutput) { }
        final var testCases = List.of(
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // @start region=here :
                                           Hello
                                           ,
                                            Snippet!
                                           // @end
                                           """)
                                     .region("here")
                                     .build(),
                             """
                             Hello
                             ,
                              Snippet!
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                               // @start region=here :
                                               Hello
                                               ,
                                                Snippet!
                                           // @end
                                               """)
                                     .region("here")
                                     .build(),
                             """
                                 Hello
                                 ,
                                  Snippet!
                             """)
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                               // @start region=here :
                                               Hello
                                               ,
                                                Snippet!// @end
                                           """)
                                     .region("here")
                                     .build(),
                             """
                             Hello
                             ,
                              Snippet!\
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // @start region=there :
                                           // @end

                                               // @start region=here :
                                               Hello
                                               ,
                                                Snippet!
                                               // @end
                                                  """)
                                     .region("here")
                                     .build(),
                             """
                             Hello
                             ,
                              Snippet!
                             """
                )
                ,
//                entry(newSnippetBuilder()
//                              .body("""
//                                    // @start region=here :
//                                        Hello
//                                    // @end
//
//                                         , Snippet!
//                                    // @end
//                                        """)
//                              .region("here")
//                              .build()
//                        ,
//                      """
//                          Hello
//                      """
//                )
//                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // @start region=here :
                                               This is the only line you should see.
                                           // @end
                                           // @start region=hereafter :
                                               You should NOT see this.
                                           // @end
                                               """)
                                     .region("here")
                                     .build(),
                             """
                                 This is the only line you should see.
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // @start region=here :
                                               You should NOT see this.
                                           // @end
                                           // @start region=hereafter :
                                               This is the only line you should see.
                                           // @end
                                               """)
                                     .region("hereafter")
                                     .build(),
                             """
                                 This is the only line you should see.
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // @start region=beforehand :
                                               You should NOT see this.
                                           // @end
                                           // @start region=before :
                                               This is the only line you should see.
                                           // @end
                                               """)
                                     .region("before")
                                     .build(),
                             """
                                 This is the only line you should see.
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // @start region=beforehand :
                                               This is the only line you should see.
                                           // @end
                                           // @start region=before :
                                               You should NOT see this.
                                           // @end
                                               """)
                                     .region("beforehand")
                                     .build(),
                             """
                                 This is the only line you should see.
                             """
                )
        );
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(testCases, (t, id) -> {
            var snippet = t.snippet();
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                 {@snippet region="%s" :
                                                 %s}
                                                 """.formatted(snippet.regionName(), snippet.body())));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        forEachNumbered(testCases, (t, index) -> {
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                        %s</pre>
                        </div>""".formatted(index, t.expectedOutput()));
        });
    }

    private static Snippet.Builder newSnippetBuilder() {
        return new Snippet.Builder();
    }

    private record Snippet(String regionName, String body, String fileContent) {

        static class Builder {

            private String regionName;
            private String body;
            private String fileContent;

            Builder region(String name) {
                this.regionName = name;
                return this;
            }

            Builder body(String content) {
                this.body = content;
                return this;
            }

            Builder fileContent(String fileContent) {
                this.fileContent = fileContent;
                return this;
            }

            Snippet build() {
                return new Snippet(regionName, body, fileContent);
            }
        }
    }

    @Test
    public void testAttributeValueSyntaxUnquotedCurly(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        /*
         * The snippet region attribute's value is empty because the tag is
         * terminated by the first }
         *
         *    v                v
         *    {@snippet region=} :
         *        // @start region="}" @end
         *    }
         */
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void case0() { }")
                                .setComments("""
                                             {@snippet region=} :
                                                 // @start region="}" @end
                                             }
                                             """));
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: @snippet does not specify contents""");
        checkNoCrashes();
    }

    @Test
    public void testAttributeValueSyntaxCurly(Path base) throws Exception {
        /*
         * The snippet has to be external, otherwise its content would
         * interfere with the test: that internal closing curly would
         * terminate the @snippet tag:
         *
         *     v
         *     {@snippet region="}" :
         *         // @start region="}" @end
         *                           ^
         *     }
         */
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        addSnippetFile(srcDir, "pkg", "file.txt", """
                                                  // @start region="}" @end
                                                  """
        );
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void case0() { }")
                                .setComments("""
                                             {@snippet region="}" file="file.txt"}
                                             """))
                .addMembers(
                        MethodBuilder
                                .parse("public void case1() { }")
                                .setComments("""
                                             {@snippet region='}' file="file.txt"}
                                             """));
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/A.html", true,
                    """
                    <span class="element-name">case0</span>()</div>
                    <div class="block">
                    <pre class="snippet">
                    </pre>
                    </div>""");
        checkOutput("pkg/A.html", true,
                    """
                    <span class="element-name">case1</span>()</div>
                    <div class="block">
                    <pre class="snippet">
                    </pre>
                    </div>""");
    }

    @Test
    public void testAttributeValueSyntax(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        // Test most expected use cases for external snippet
        final var snippets = List.of(
                """
                {@snippet file=file region=region}
                """,
                """
                {@snippet file=file region= region}
                """,
                """
                {@snippet file=file region="region"}
                """,
                """
                {@snippet file=file region='region'}
                """,
                """
                {@snippet file= file region=region}
                """,
                """
                {@snippet file= file region= region}
                """,
                """
                {@snippet file= file region="region"}
                """,
                """
                {@snippet file= file region='region'}
                """,
                """
                {@snippet file="file" region=region}
                """,
                """
                {@snippet file="file" region= region}
                """,
                """
                {@snippet file="file" region="region"}
                """,
                """
                {@snippet file="file" region='region'}
                """,
                """
                {@snippet file='file' region=region}
                """,
                """
                {@snippet file='file' region= region}
                """,
                """
                {@snippet file='file' region="region"}
                """,
                """
                {@snippet file='file' region='region'}
                """,
                // ---------------------------------------------------------------
                """
                {@snippet region=region file=file}
                """,
                """
                {@snippet region=region file="file"}
                """,
                """
                {@snippet region="region" file="file"}
                """,
                """
                {@snippet file="file"
                          region="region"}
                """,
                """
                {@snippet file="file"
                          region=region}
                """
        );
        addSnippetFile(srcDir, "pkg", "file", """
                                              1 // @start region=bar @end
                                              2 // @start region=region @end
                                              3 // @start region=foo @end
                                              """);
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(snippets, (s, i) -> {
            classBuilder.addMembers(
                    MethodBuilder.parse("public void case%s() { }".formatted(i)).setComments(s));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        for (int j = 0; j < snippets.size(); j++) {
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                        2</pre>
                        </div>
                        """.formatted(j));
        }
    }

    @Test
    public void testComment(Path base) throws Exception {
        record TestCase(Snippet snippet, String expectedOutput) { }
        final var testCases = List.of(
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // // @replace substring="//" replacement="Hello"
                                           ,
                                            Snippet!""")
                                     .build(),
                             """
                             Hello
                             ,
                              Snippet!"""
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           //             // @replace substring="//" replacement="Hello"
                                           ,
                                            Snippet!""")
                                     .build(),
                             """
                             Hello
                             ,
                              Snippet!"""
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           // // @replace substring="//" replacement=" Hello"
                                           ,
                                            Snippet!""")
                                     .build(),
                             """
                              Hello
                             ,
                              Snippet!"""
                )
// Uncomment when parser has improved (this would allow to write meta snippets,
// i.e. snippets showing how to write snippets.
//
//                ,
//                entry(newSnippetBuilder()
//                              .body("""
//                                    // snippet-comment : // snippet-comment : my comment""")
//                              .build(),
//                      """
//                      // snippet-comment : my comment"""
//                )
        );
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(testCases, (t, id) -> {
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                 {@snippet :
                                                 %s}
                                                 """.formatted(t.snippet().body())));
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        forEachNumbered(testCases, (t, index) -> {
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                        %s</pre>
                        </div>""".formatted(index, t.expectedOutput()));
        });
    }

    @Test
    public void testRedundantFileNotFound(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet file="%s":
                                                 Hello, Snippet!}
                                             """.formatted(fileName)))
                .write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: File not found: %s""".formatted(fileName));
        checkNoCrashes();
    }

    @Test
    public void testRedundantRegionNotFound(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        var region = "here";
        var content =
                """
                Hello, Snippet!""";

        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet region="%s" file="%s":
                                             %s}
                                             """.formatted(region, fileName, content)))
                .write(srcDir);
        addSnippetFile(srcDir, "pkg", fileName, content);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: region not found: "%s\"""".formatted(region));
        checkNoCrashes();
    }

    @Test
    public void testRedundantMismatch(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        var content =
                """
                Hello, Snippet!""";
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet file="%s":
                                             %s}
                                             """.formatted(fileName, content)))
                .write(srcDir);
        addSnippetFile(srcDir, "pkg", fileName, content + "...more");
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: contents mismatch""");
        checkNoCrashes();
    }

    @Test
    public void testRedundantRegionRegionMismatch(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        var region = "here";
        var content =
                """
                Hello, Snippet!""";
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet region="%s" file="%s":
                                             Above the region.
                                             // @start region="%s" :
                                             %s ...more
                                             // @end
                                             Below the region}
                                             """.formatted(region, fileName, region, content)))
                .write(srcDir);
        addSnippetFile(srcDir, "pkg", fileName,
                       """
                       This line is above the region.
                       // @start region="%s" :
                       %s
                       // @end
                       This line is below the region.""".formatted(region, content));
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: contents mismatch""");
        checkNoCrashes();
    }

    @Test
    public void testRedundantRegion1Mismatch(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        var region = "here";
        var content =
                """
                Hello, Snippet!""";
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet region="%s" file="%s":
                                             Above the region.
                                             // @start region="%s" :
                                             %s ...more
                                             // @end
                                             Below the region}
                                             """.formatted(region, fileName, region, content)))
                .write(srcDir);
        addSnippetFile(srcDir, "pkg", fileName, content);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: contents mismatch""");
        checkNoCrashes();
    }

    @Test
    public void testRedundantRegion2Mismatch(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        var fileName = "text.txt";
        var region = "here";
        var content =
                """
                Hello, Snippet!""";
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                             {@snippet region="%s" file="%s":
                                             %s}
                                             """.formatted(region, fileName, content)))
                .write(srcDir);
        addSnippetFile(srcDir, "pkg", fileName,
                       """
                       Above the region.
                       // @start region="%s" :
                       %s ...more
                       // @end
                       Below the region
                       """.formatted(region, content));
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                    """
                    A.java:4: error: contents mismatch""");
        checkNoCrashes();
    }

    @Test
    public void testRedundant(Path base) throws Exception {
        record TestCase(Snippet snippet, String expectedOutput) { }
        final var testCases = List.of(
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           Hello
                                           ,
                                            Snippet!""")
                                     .fileContent(
                                             """
                                             Hello
                                             ,
                                              Snippet!""")
                                     .build(),
                             """
                             Hello
                             ,
                              Snippet!"""
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                             Hello
                                             ,
                                              Snippet!
                                           """)
                                     .region("here")
                                     .fileContent(
                                             """
                                             Above the region.
                                             // @start region=here :
                                               Hello
                                               ,
                                                Snippet!
                                             // @end
                                             Below the region.
                                             """)
                                     .build(),
                             """
                               Hello
                               ,
                                Snippet!
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           Above the region.
                                           // @start region=here :
                                             Hello
                                             ,
                                              Snippet!
                                           // @end
                                           Below the region.
                                           """)
                                     .region("here")
                                     .fileContent(
                                             """
                                               Hello
                                               ,
                                                Snippet!
                                             """)
                                     .build(),
                             """
                               Hello
                               ,
                                Snippet!
                             """
                )
                ,
                new TestCase(newSnippetBuilder()
                                     .body("""
                                           Above the region.
                                           // @start region=here :
                                             Hello
                                             ,
                                              Snippet!
                                           // @end
                                           Below the region.
                                           """)
                                     .region("here")
                                     .fileContent(
                                             """
                                             Above the region.
                                             // @start region=here :
                                               Hello
                                               ,
                                                Snippet!
                                             // @end
                                             Below the region.
                                             """)
                                     .build(),
                             """
                               Hello
                               ,
                                Snippet!
                             """
                )
        );
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");
        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");
        forEachNumbered(testCases, (t, id) -> {
            var snippet = t.snippet();
            final String r = snippet.regionName() == null ? "" : "region=\"" + snippet.regionName() + "\"";
            final String f = snippet.fileContent() == null ? "" : "file=\"%s.txt\"".formatted(id);
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                 {@snippet %s %s:
                                                 %s}
                                                 """.formatted(r, f, snippet.body())));
            addSnippetFile(srcDir, "pkg", "%s.txt".formatted(id), snippet.fileContent());
        });
        classBuilder.write(srcDir);
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");
        checkExit(Exit.OK);
        forEachNumbered(testCases, (t, index) -> {
            checkOutput("pkg/A.html", true,
                        """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        <pre class="snippet">
                        %s</pre>
                        </div>""".formatted(index, t.expectedOutput()));
        });
    }
}
