/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jpeek;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.jcabi.xml.XSL;
import com.jcabi.xml.XSLDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;
import org.cactoos.Scalar;
import org.cactoos.collection.Filtered;
import org.cactoos.collection.Joined;
import org.cactoos.io.ResourceOf;
import org.cactoos.iterable.Mapped;
import org.cactoos.iterable.PropertiesOf;
import org.xembly.Directive;
import org.xembly.Directives;
import org.xembly.Xembler;

/**
 * Index.
 *
 * <p>There is no thread-safety guarantee.
 *
 * @author Yegor Bugayenko (yegor256@gmail.com)
 * @version $Id$
 * @since 0.6
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class Index implements Scalar<String> {

    /**
     * XSL stylesheet.
     */
    private static final XSL STYLESHEET = XSLDocument.make(
        App.class.getResourceAsStream("index.xsl")
    );

    /**
     * Directory to save index to.
     */
    private final Path output;

    /**
     * Ctor.
     * @param target Target dir
     */
    Index(final Path target) {
        this.output = target;
    }

    @Override
    public String value() throws Exception {
        return Index.STYLESHEET.transform(
            new XMLDocument(
                new Xembler(
                    new Directives()
                        .add("metrics")
                        .append(
                            new Joined<>(
                                new Mapped<Path, Iterable<Directive>>(
                                    new Filtered<Path>(
                                        Files.list(this.output)
                                            .collect(Collectors.toList()),
                                        path -> path.toString().endsWith(".xml")
                                    ),
                                    Index::metric
                                )
                            )
                        )
                        .attr(
                            "date",
                            ZonedDateTime.now().format(
                                DateTimeFormatter.ISO_INSTANT
                            )
                        )
                        .attr(
                            "version",
                            new PropertiesOf(
                                new ResourceOf(
                                    "org/jpeek/jpeek.properties"
                                )
                            ).value().getProperty("org.jpeek.version")
                        )
                ).xmlQuietly()
            )
        ).toString();
    }

    /**
     * Metric to Xembly.
     * @param file The XML file with metric report
     * @return Xembly
     * @throws IOException If fails
     */
    private static Iterable<Directive> metric(final Path file)
        throws IOException {
        final String name = file.getFileName().toString();
        final XML xml = new XMLDocument(file.toFile());
        final Collection<Double> values = new org.cactoos.collection.Mapped<>(
            xml.xpath("//class/@value"),
            Double::parseDouble
        );
        return new Directives()
            .add("metric")
            .attr("name", name)
            .add("html").set(String.format("%s.html", name)).up()
            .add("xml").set(String.format("%s.xml", name)).up()
            .add("classes").set(values.size()).up()
            .add("average").set(Index.avg(values)).up()
            .up();
    }

    /**
     * Calculate average.
     * @param values Values
     * @return Average
     */
    private static double avg(final Collection<Double> values) {
        double sum = 0.0d;
        for (final double val : values) {
            sum += val;
        }
        double avg = 0.0d;
        if (values.isEmpty()) {
            avg = sum / (double) values.size();
        }
        return avg;
    }
}
