/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.dsl.api.xml;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.java.utils.JavaTypeUtils;
import org.mule.runtime.extension.api.annotation.Alias;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * Utilities for manipulating names of supported by the DSL. Mostly for validating the configuration content.
 *
 * @since 4.0
 */
public class NameUtils
{

    private static final List<Inflection> plural = new ArrayList<>();
    private static final List<Inflection> singular = new ArrayList<>();
    private static final List<String> uncountable = new ArrayList<>();

    static
    {
        // plural is "singular to plural form"
        // singular is "plural to singular form"
        plural("$", "s");
        plural("s$", "s");
        plural("(ax|test)is$", "$1es");
        plural("(octop|vir)us$", "$1i");
        plural("(alias|status)$", "$1es");
        plural("(bu)s$", "$1ses");
        plural("(buffal|tomat)o$", "$1oes");
        plural("([ti])um$", "$1a");
        plural("sis$", "ses");
        plural("(?:([^f])fe|([lr])f)$", "$1$2ves");
        plural("(hive)$", "$1s");
        plural("([^aeiouy]|qu)y$", "$1ies");
        plural("(x|ch|ss|sh)$", "$1es");
        plural("(matr|vert|ind)ix|ex$", "$1ices");
        plural("([m|l])ouse$", "$1ice");
        plural("^(ox)$", "$1en");
        plural("(quiz)$", "$1zes");

        singular("s$", "");
        singular("(n)ews$", "$1ews");
        singular("([ti])a$", "$1um");
        singular("((a)naly|(b)a|(d)iagno|(p)arenthe|(p)rogno|(s)ynop|(t)he)ses$", "$1$2sis");
        singular("(^analy)ses$", "$1sis");
        singular("([^f])ves$", "$1fe");
        singular("(hive)s$", "$1");
        singular("(tive)s$", "$1");
        singular("([lr])ves$", "$1f");
        singular("([^aeiouy]|qu)ies$", "$1y");
        singular("(s)eries$", "$1eries");
        singular("(m)ovies$", "$1ovie");
        singular("(x|ch|ss|sh)es$", "$1");
        singular("([m|l])ice$", "$1ouse");
        singular("(bus)es$", "$1");
        singular("(o)es$", "$1");
        singular("(shoe)s$", "$1");
        singular("(cris|ax|test)es$", "$1is");
        singular("(octop|vir)i$", "$1us");
        singular("(alias|status)es$", "$1");
        singular("^(ox)en", "$1");
        singular("(vert|ind)ices$", "$1ex");
        singular("(matr)ices$", "$1ix");
        singular("(quiz)zes$", "$1");

        // irregular
        irregular("person", "people");
        irregular("man", "men");
        irregular("child", "children");
        irregular("sex", "sexes");
        irregular("move", "moves");

        uncountable("equipment");
        uncountable("information");
        uncountable("rice");
        uncountable("money");
        uncountable("species");
        uncountable("series");
        uncountable("fish");
        uncountable("sheep");
    }

    private NameUtils()
    {
    }

    /**
     * Registers a plural {@code replacement} for the given {@code pattern}
     *
     * @param pattern     the pattern for which you want to register a plural form
     * @param replacement the replacement pattern
     */
    private static void plural(String pattern, String replacement)
    {
        plural.add(0, new Inflection(pattern, replacement));
    }

    /**
     * Registers a singular {@code replacement} for the given {@code pattern}
     *
     * @param pattern     the pattern for which you want to register a plural form
     * @param replacement the replacement pattern
     */
    private static void singular(String pattern, String replacement)
    {
        singular.add(0, new Inflection(pattern, replacement));
    }

    private static void irregular(String s, String p)
    {
        plural("(" + s.substring(0, 1) + ")" + s.substring(1) + "$", "$1" + p.substring(1));
        singular("(" + p.substring(0, 1) + ")" + p.substring(1) + "$", "$1" + s.substring(1));
    }

    private static void uncountable(String word)
    {
        uncountable.add(word);
    }

    /**
     * Transforms a camel case value into a hyphenizedone.
     * <p>
     * For example:
     * {@code messageProcessor} would be transformed to {@code message-processor}
     *
     * @param camelCaseName a {@link String} in camel case form
     * @return the {@code camelCaseName} in hypenized form
     */
    public static String hyphenize(String camelCaseName)
    {
        if (StringUtils.isBlank(camelCaseName))
        {
            return camelCaseName;
        }

        String result = "";
        String[] parts = camelCaseName.split("(?<!^)(?=[A-Z])");

        for (int i = 0; i < parts.length; i++)
        {
            result += parts[i].toLowerCase() + (i < parts.length - 1 ? "-" : "");
        }

        return result;
    }

    /**
     * Return the pluralized version of a word.
     *
     * @param word The word
     * @return The pluralized word
     */
    public static String pluralize(String word)
    {
        if (isUncountable(word))
        {
            return word;
        }
        else
        {
            for (Inflection inflection : plural)
            {
                if (inflection.match(word))
                {
                    return inflection.replace(word);
                }
            }
            return word;
        }
    }

    /**
     * Return the singularized version of a word.
     *
     * @param word The word
     * @return The singularized word
     */
    public static String singularize(String word)
    {
        if (isUncountable(word))
        {
            return word;
        }
        else
        {
            for (Inflection inflection : singular)
            {
                if (inflection.match(word))
                {
                    return inflection.replace(word);
                }
            }
        }
        return word;
    }

    /**
     * Return true if the word is uncountable.
     *
     * @param word The word
     * @return True if it is uncountable
     */
    public static boolean isUncountable(String word)
    {
        if (StringUtils.isBlank(word))
        {
            for (String w : uncountable)
            {
                if (w.equalsIgnoreCase(word))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns a hypenized name of the give top level {@code metadataType}.
     * <p>
     * It uses {@link JavaTypeUtils#getType(MetadataType)} to obtain the
     * {@link Class} that the {@code metadataType} represents. Then, it
     * checks if the  {@link Alias} annotation is present. If so, the
     * {@link Alias#value()} is used. Otherwise, the class name will
     * be considered.
     *
     * @param metadataType the {@link MetadataType} which name you want
     * @return the hypenized name for the given {@code type}
     */
    public static String getTopLevelTypeName(MetadataType metadataType)
    {
        Class<?> type = JavaTypeUtils.getType(metadataType);
        Alias alias = type.getAnnotation(Alias.class);
        String name = alias != null ? alias.value() : type.getSimpleName();

        return hyphenize(name);
    }

    /**
     * Removes everything that's not a word, a dot nor a hyphen
     *
     * @param originalName name that needs the removal of invalid characters
     * @return name without invalid characters
     */
    public static String sanitizeName(String originalName)
    {
        return originalName.replaceAll("[^\\w|\\.\\-]", EMPTY);
    }

    private static class Inflection
    {

        private String pattern;
        private String replacement;
        private boolean ignoreCase;

        public Inflection(String pattern, String replacement)
        {
            this(pattern, replacement, true);
        }

        public Inflection(String pattern, String replacement, boolean ignoreCase)
        {
            this.pattern = pattern;
            this.replacement = replacement;
            this.ignoreCase = ignoreCase;
        }


        /**
         * Does the given word match?
         *
         * @param word The word
         * @return True if it matches the inflection pattern
         */
        public boolean match(String word)
        {
            int flags = 0;
            if (ignoreCase)
            {
                flags = flags | java.util.regex.Pattern.CASE_INSENSITIVE;
            }
            return java.util.regex.Pattern.compile(pattern, flags).matcher(word).find();
        }

        /**
         * Replace the word with its pattern.
         *
         * @param word The word
         * @return The result
         */
        public String replace(String word)
        {
            int flags = 0;
            if (ignoreCase)
            {
                flags = flags | java.util.regex.Pattern.CASE_INSENSITIVE;
            }
            return java.util.regex.Pattern.compile(pattern, flags).matcher(word).replaceAll(replacement);
        }
    }
}
