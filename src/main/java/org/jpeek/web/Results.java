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
package org.jpeek.web;

import com.amazonaws.services.dynamodbv2.model.Select;
import com.jcabi.dynamo.Attributes;
import com.jcabi.dynamo.Credentials;
import com.jcabi.dynamo.QueryValve;
import com.jcabi.dynamo.Region;
import com.jcabi.dynamo.Table;
import com.jcabi.dynamo.mock.H2Data;
import com.jcabi.dynamo.mock.MkRegion;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.cactoos.io.ResourceOf;
import org.cactoos.iterable.Mapped;
import org.cactoos.iterable.PropertiesOf;
import org.cactoos.map.MapEntry;
import org.jpeek.Version;

/**
 * Futures for {@link AsyncReports}.
 *
 * <p>There is no thread-safety guarantee.
 *
 * @author Yegor Bugayenko (yegor256@gmail.com)
 * @version $Id$
 * @since 0.8
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class Results {

    /**
     * Score multiplier.
     */
    private static final Double MULTIPLIER = 100000.0d;

    /**
     * DynamoDB table.
     */
    private final Table table;

    /**
     * Ctor.
     * @throws IOException If fails
     */
    Results() throws IOException {
        this(Results.live());
    }

    /**
     * Ctor.
     * @param tbl Table
     */
    Results(final Table tbl) {
        this.table = tbl;
    }

    /**
     * Add result.
     * @param artifact The artifact, like "org.jpeek:jpeek"
     * @param score The score [0..10]
     * @throws IOException If fails
     */
    public void add(final String artifact, final double score)
        throws IOException {
        this.table.put(
            new Attributes()
                .with("artifact", artifact)
                .with("score", (long) (score * Results.MULTIPLIER))
                .with("version", new Version().value())
                .with(
                    "ttl",
                    System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(1L)
                        // @checkstyle MagicNumber (1 line)
                        + TimeUnit.DAYS.toSeconds(100L)
                )
        );
    }

    /**
     * Best artifacts.
     * @return List of them
     * @throws IOException If fails
     */
    public Iterable<Map.Entry<String, Double>> best() throws IOException {
        return new Mapped<>(
            this.table.frame()
                .where("version", new Version().value())
                .through(
                    new QueryValve()
                        .withScanIndexForward(false)
                        .withIndexName("scores")
                        .withConsistentRead(false)
                        // @checkstyle MagicNumber (1 line)
                        .withLimit(20)
                        .withSelect(Select.ALL_ATTRIBUTES)
                ),
            item -> new MapEntry<>(
                item.get("artifact").getS(),
                Double.parseDouble(item.get("score").getN())
                    / Results.MULTIPLIER
            )
        );
    }

    /**
     * Live DynamoDB table.
     * @return Table
     * @throws IOException If fails
     */
    private static Table live() throws IOException {
        final Properties props = Results.pros();
        final String key = props.getProperty("org.jpeek.dynamo.key");
        final Region reg;
        // @checkstyle MagicNumber (1 line)
        if (key.length() == 20) {
            reg = new Region.Simple(
                new Credentials.Simple(
                    key,
                    props.getProperty("org.jpeek.dynamo.secret")
                )
            );
        } else {
            reg = new MkRegion(
                new H2Data().with(
                    "jpeek-results",
                    new String[] {"artifact"},
                    "score", "ttl", "version"
                )
            );
        }
        return reg.table("jpeek-results");
    }

    /**
     * Properties.
     * @return Props
     * @throws IOException If fails
     */
    private static Properties pros() throws IOException {
        return new PropertiesOf(
            new ResourceOf(
                "org/jpeek/jpeek.properties"
            )
        ).value();
    }

}