/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4.runners;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Set;

import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;

/**
 * Runner that does the testing of the class in a different {@link ClassLoader}.
 */
public class ArtifactClassloaderTestRunner extends AbstractRunnerDelegate
{

    private final Runner delegate;

    private final ArtifactClassLoaderRunnerConfig annotation;

    private ClassPathURLsProvider classPathURLsProvider;
    private MavenDependenciesResolver mavenDependenciesResolver;
    private MavenMultiModuleArtifactMapping mavenMultiModuleArtifactMapping;
    private ClassLoaderRunnerFactory classLoaderRunnerFactory;
    private ClassPathClassifier classPathClassifier;

    /**
     * Creates a Runner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public ArtifactClassloaderTestRunner(final Class<?> klass) throws Exception
    {
        annotation = klass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        classPathURLsProvider = getClassPathURLsProvider();
        mavenDependenciesResolver = getMavenDependenciesResolver();
        mavenMultiModuleArtifactMapping = getMavenMultiModuleArtifactMapping();

        classLoaderRunnerFactory = getClassLoaderRunnerFactory();
        classPathClassifier = getClassPathClassifier();

        ClassLoader classLoader = buildArtifactClassloader(klass);
        delegate = new ClassLoaderIsolatedTestRunner(classLoader, klass);
    }

    @Override
    protected Runner getDelegateRunner()
    {
        return delegate;
    }

    private ClassLoader buildArtifactClassloader(final Class<?> klass) throws IOException, URISyntaxException
    {
        final Set<URL> classPathURLs = this.classPathURLsProvider.getURLs();
        LinkedHashMap<MavenArtifact, Set<MavenArtifact>> allDependencies = mavenDependenciesResolver.buildDependencies(klass);

        ArtifactUrlClassification artifactUrlClassification = classPathClassifier.classify(klass, classPathURLs, allDependencies, mavenMultiModuleArtifactMapping);
        ClassLoader classLoader = classLoaderRunnerFactory.createClassLoader(klass, artifactUrlClassification);
        return classLoader;
    }

    public ClassPathURLsProvider getClassPathURLsProvider() throws IllegalAccessException, InstantiationException
    {
        ClassPathURLsProvider classPathURLsProvider = new DefaultClassPathURLsProvider();
        if (annotation != null)
        {
            classPathURLsProvider = annotation.classPathURLsProvider().newInstance();
        }
        return classPathURLsProvider;
    }

    private MavenDependenciesResolver getMavenDependenciesResolver() throws IllegalAccessException, InstantiationException
    {
        MavenDependenciesResolver mavenDependenciesResolver = new DependencyGraphMavenDependenciesResolver();
        if (annotation != null)
        {
            mavenDependenciesResolver = annotation.mavenDependenciesResolver().newInstance();
        }
        return mavenDependenciesResolver;
    }

    private MavenMultiModuleArtifactMapping getMavenMultiModuleArtifactMapping() throws IllegalAccessException, InstantiationException
    {
        MavenMultiModuleArtifactMapping mavenMultiModuleArtifactMapping = new MuleMavenMultiModuleArtifactMapping();
        if (annotation != null)
        {
            mavenMultiModuleArtifactMapping = annotation.mavenMultiModuleArtifactMapping().newInstance();
        }
        return mavenMultiModuleArtifactMapping;
    }

    private ClassLoaderRunnerFactory getClassLoaderRunnerFactory() throws IllegalAccessException, InstantiationException
    {
        ClassLoaderRunnerFactory classLoaderRunnerFactory = new MuleClassLoaderRunnerFactory();
        if (annotation != null)
        {
            classLoaderRunnerFactory = annotation.classLoaderRunnerFactory().newInstance();
        }
        return classLoaderRunnerFactory;
    }

    private ClassPathClassifier getClassPathClassifier() throws IllegalAccessException, InstantiationException
    {
        ClassPathClassifier classPathClassifier = new MuleClassPathClassifier();
        if (annotation != null)
        {
            classPathClassifier = annotation.classPathClassifier().newInstance();
        }
        return classPathClassifier;
    }

}
