/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.mule.tck.size.SmallTest;

import java.util.function.Predicate;

import org.junit.Test;

/**
 * Tests the {@link java.util.function.Predicate} for {@link MavenArtifact}
 */
@SmallTest
public class MavenArtifactMatcherPredicateTest
{

    @Test
    public void matchByGroupId()
    {
        Predicate<MavenArtifact> predicate = new MavenArtifactMatcherPredicate("org.mule", "*", "*");

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("test-jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-validations").withType("jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule.transports").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("commons-collections").withArtifactId("commons-collections").withType("jar").build()), equalTo(false));
    }

    @Test
    public void matchByGroupIdStartsWith()
    {
        Predicate<MavenArtifact> predicate = new MavenArtifactMatcherPredicate("org.mule*", "*", "*");

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("test-jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-validations").withType("jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule.transports").withArtifactId("mule-validations").withType("jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("commons-collections").withArtifactId("commons-collections").withType("jar").build()), equalTo(false));
    }

    @Test
    public void matchByArtifactId()
    {
        Predicate<MavenArtifact> predicate = new MavenArtifactMatcherPredicate("org.mule", "mule-core", "*");

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("test-jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule.transports").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("commons-collections").withArtifactId("commons-collections").withType("jar").build()), equalTo(false));
    }

    @Test
    public void matchByArtifactIdStartsWith()
    {
        Predicate<MavenArtifact> predicate = new MavenArtifactMatcherPredicate("org.mule", "mule-*", "*");

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("test-jar").build()), equalTo(true));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-validations").withType("jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule.transports").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("commons-collections").withArtifactId("commons-collections").withType("jar").build()), equalTo(false));
    }

    @Test
    public void matchByType()
    {
        Predicate<MavenArtifact> predicate = new MavenArtifactMatcherPredicate("org.mule", "mule-core", "jar");

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("test-jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule.transports").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("commons-collections").withArtifactId("commons-collections").withType("jar").build()), equalTo(false));
    }

    @Test
    public void orMatching()
    {
        Predicate<MavenArtifact> predicate = new MavenArtifactMatcherPredicate("org.mule", "mule-core", "jar").or(new MavenArtifactMatcherPredicate("org.mule.transports", "*", "*"));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-core").withType("test-jar").build()), equalTo(false));
        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule").withArtifactId("mule-validations").withType("jar").build()), equalTo(false));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("org.mule.transports").withArtifactId("mule-validations").withType("jar").build()), equalTo(true));

        assertThat(predicate.test(MavenArtifact.builder().withGroupId("commons-collections").withArtifactId("commons-collections").withType("jar").build()), equalTo(false));
    }

}
