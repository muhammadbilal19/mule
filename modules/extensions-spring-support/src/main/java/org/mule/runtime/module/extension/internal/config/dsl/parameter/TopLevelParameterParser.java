/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.config.dsl.parameter;

import static org.mule.metadata.utils.MetadataTypeUtils.getDefaultValue;
import static org.mule.runtime.config.spring.dsl.api.AttributeDefinition.Builder.fromFixedValue;
import static org.mule.runtime.config.spring.dsl.api.TypeDefinition.fromType;
import static org.mule.runtime.extension.api.introspection.declaration.type.TypeUtils.getExpressionSupport;
import static org.mule.runtime.config.spring.dsl.api.xml.NameUtils.getTopLevelTypeName;
import static org.mule.runtime.config.spring.dsl.api.xml.NameUtils.hyphenize;
import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.DictionaryType;
import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.api.model.ObjectFieldType;
import org.mule.metadata.api.model.ObjectType;
import org.mule.metadata.api.visitor.MetadataTypeVisitor;
import org.mule.runtime.config.spring.dsl.api.ComponentBuildingDefinition.Builder;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.extension.api.introspection.parameter.ExpressionSupport;
import org.mule.runtime.module.extension.internal.config.dsl.ExtensionDefinitionParser;
import org.mule.runtime.module.extension.internal.runtime.resolver.ValueResolver;

/**
 * A {@link ExtensionDefinitionParser} for parsing extension objects that can
 * be defined as named top level elements and be placed in the mule registry.
 * <p>
 * These objects are parsed as {@link ValueResolver}s which are later
 * resolved by a {@link TopLevelParameterObjectFactory} instance
 *
 * @since 4.0
 */
public class TopLevelParameterParser extends ExtensionDefinitionParser
{

    private final ObjectType type;

    public TopLevelParameterParser(Builder definition, ObjectType type)
    {
        super(definition);
        this.type = type;
    }

    @Override
    protected void doParse(Builder definitionBuilder) throws ConfigurationException
    {
        definitionBuilder.withIdentifier(hyphenize(getTopLevelTypeName(type)))
                .withTypeDefinition(fromType(ValueResolver.class))
                .withObjectFactoryType(TopLevelParameterObjectFactory.class)
                .withConstructorParameterDefinition(fromFixedValue(type).build());

        for (ObjectFieldType objectField : type.getFields())
        {
            final MetadataType fieldType = objectField.getValue();
            final String parameterName = objectField.getKey().getName().getLocalPart();
            final Object defaultValue = getDefaultValue(fieldType).orElse(null);
            final ExpressionSupport expressionSupport = getExpressionSupport(fieldType);

            fieldType.accept(new MetadataTypeVisitor()
            {

                @Override
                protected void defaultVisit(MetadataType metadataType)
                {
                    parseAttributeParameter(parameterName, parameterName, metadataType, defaultValue, expressionSupport, false);
                }

                @Override
                public void visitObject(ObjectType objectType)
                {
                    parseObjectParameter(parameterName, parameterName, objectType, defaultValue, expressionSupport, false);
                }

                @Override
                public void visitArrayType(ArrayType arrayType)
                {
                    parseCollectionParameter(parameterName, parameterName, arrayType, defaultValue, expressionSupport, false);
                }

                @Override
                public void visitDictionary(DictionaryType dictionaryType)
                {
                    parseMapParameters(parameterName, parameterName, dictionaryType, defaultValue, expressionSupport, false);
                }
            });
        }
    }
}
