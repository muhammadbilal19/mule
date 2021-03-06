/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.processor;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang.StringUtils.EMPTY;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.runtime.core.util.SystemUtils.getDefaultEncoding;
import static org.mule.runtime.module.extension.internal.metadata.PartAwareMetadataKeyBuilder.newKey;
import static org.mule.runtime.module.extension.internal.util.ExtensionsTestUtils.TYPE_BUILDER;
import static org.mule.runtime.module.extension.internal.util.ExtensionsTestUtils.mockClassLoaderModelProperty;
import static org.mule.runtime.module.extension.internal.util.ExtensionsTestUtils.toMetadataType;
import static org.mule.test.metadata.extension.resolver.TestNoConfigMetadataResolver.KeyIds.BOOLEAN;
import static org.mule.test.metadata.extension.resolver.TestNoConfigMetadataResolver.KeyIds.STRING;

import org.mule.metadata.api.model.StringType;
import org.mule.runtime.api.message.MuleMessage;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MetadataKey;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.metadata.descriptor.ComponentMetadataDescriptor;
import org.mule.runtime.api.metadata.resolving.MetadataResult;
import org.mule.runtime.core.DefaultMuleMessage;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.lifecycle.Disposable;
import org.mule.runtime.core.api.lifecycle.Initialisable;
import org.mule.runtime.core.api.lifecycle.Lifecycle;
import org.mule.runtime.core.api.lifecycle.Startable;
import org.mule.runtime.core.api.lifecycle.Stoppable;
import org.mule.runtime.core.internal.connection.ConnectionManagerAdapter;
import org.mule.runtime.core.internal.connection.ConnectionProviderWrapper;
import org.mule.runtime.core.internal.connection.DefaultConnectionManager;
import org.mule.runtime.extension.api.introspection.ImmutableOutputModel;
import org.mule.runtime.extension.api.introspection.OutputModel;
import org.mule.runtime.extension.api.introspection.RuntimeExtensionModel;
import org.mule.runtime.extension.api.introspection.config.RuntimeConfigurationModel;
import org.mule.runtime.extension.api.introspection.declaration.type.ExtensionsTypeLoaderFactory;
import org.mule.runtime.extension.api.introspection.exception.ExceptionEnricherFactory;
import org.mule.runtime.extension.api.introspection.metadata.MetadataResolverFactory;
import org.mule.runtime.extension.api.introspection.operation.RuntimeOperationModel;
import org.mule.runtime.extension.api.introspection.parameter.ParameterModel;
import org.mule.runtime.extension.api.introspection.property.MetadataContentModelProperty;
import org.mule.runtime.extension.api.introspection.property.MetadataKeyIdModelProperty;
import org.mule.runtime.extension.api.introspection.property.MetadataKeyPartModelProperty;
import org.mule.runtime.extension.api.introspection.property.SubTypesModelProperty;
import org.mule.runtime.extension.api.runtime.ConfigurationInstance;
import org.mule.runtime.extension.api.runtime.ConfigurationProvider;
import org.mule.runtime.extension.api.runtime.OperationContext;
import org.mule.runtime.extension.api.runtime.OperationExecutor;
import org.mule.runtime.extension.api.runtime.OperationExecutorFactory;
import org.mule.runtime.module.extension.internal.manager.ExtensionManagerAdapter;
import org.mule.runtime.module.extension.internal.runtime.OperationContextAdapter;
import org.mule.runtime.module.extension.internal.runtime.exception.NullExceptionEnricher;
import org.mule.runtime.module.extension.internal.runtime.resolver.ResolverSet;
import org.mule.runtime.module.extension.internal.runtime.resolver.ResolverSetResult;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.junit4.matcher.MetadataKeyMatcher;
import org.mule.tck.size.SmallTest;
import org.mule.test.metadata.extension.resolver.TestNoConfigMetadataResolver;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class OperationMessageProcessorTestCase extends AbstractMuleContextTestCase
{

    private static final String CONFIG_NAME = "config";
    private static final String OPERATION_NAME = "operation";
    private static final String TARGET_VAR = "myFlowVar";

    @Mock
    private RuntimeExtensionModel extensionModel;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private RuntimeConfigurationModel configurationModel;

    @Mock
    private RuntimeOperationModel operationModel;

    @Mock
    private ExtensionManagerAdapter extensionManager;

    @Mock
    private ConnectionManagerAdapter connectionManagerAdapter;
    @Mock
    private OperationExecutorFactory operationExecutorFactory;

    @Mock(extraInterfaces = {Lifecycle.class, MuleContextAware.class})
    private OperationExecutor operationExecutor;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ResolverSet resolverSet;

    @Mock
    private ResolverSetResult parameters;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private MuleEvent event;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private MuleContext context;

    @Mock
    private ConfigurationInstance<Object> configurationInstance;

    @Mock
    private Object configuration;

    @Mock
    private ExceptionEnricherFactory exceptionEnricherFactory;

    @Mock
    private MetadataResolverFactory metadataResolverFactory;

    @Mock
    private ConnectionProviderWrapper connectionProviderWrapper;

    @Mock
    private ParameterModel contentMock;

    @Mock
    private ParameterModel keyParamMock;

    @Mock
    private OutputModel outputMock;

    @Mock
    private StringType stringType;

    @Mock
    private ConfigurationProvider<Object> configurationProvider;

    private OperationMessageProcessor messageProcessor;

    private String configurationName = CONFIG_NAME;
    private String target = EMPTY;

    private DefaultConnectionManager connectionManager;

    @Before
    public void before() throws Exception
    {
        configureMockEvent(event);

        when(operationModel.getName()).thenReturn(getClass().getName());
        when(operationModel.getOutput()).thenReturn(new ImmutableOutputModel("MuleMessage.Payload", toMetadataType(String.class), false, emptySet()));
        when(operationModel.getExecutor()).thenReturn(operationExecutorFactory);
        when(operationModel.getModelProperty(MetadataKeyIdModelProperty.class)).thenReturn(Optional.of(new MetadataKeyIdModelProperty(ExtensionsTypeLoaderFactory.getDefault().createTypeLoader().load(String.class))));
        when(operationExecutorFactory.createExecutor()).thenReturn(operationExecutor);

        when(operationModel.getName()).thenReturn(OPERATION_NAME);
        when(operationModel.getExceptionEnricherFactory()).thenReturn(Optional.of(exceptionEnricherFactory));

        when(exceptionEnricherFactory.createEnricher()).thenReturn(new NullExceptionEnricher());

        when(operationModel.getMetadataResolverFactory()).thenReturn(metadataResolverFactory);
        when(metadataResolverFactory.getKeyResolver()).thenReturn(new TestNoConfigMetadataResolver());
        when(metadataResolverFactory.getContentResolver()).thenReturn(new TestNoConfigMetadataResolver());
        when(metadataResolverFactory.getOutputResolver()).thenReturn(new TestNoConfigMetadataResolver());
        when(metadataResolverFactory.getOutputAttributesResolver()).thenReturn(new TestNoConfigMetadataResolver());

        when(keyParamMock.getName()).thenReturn("type");
        when(keyParamMock.getType()).thenReturn(stringType);
        when(keyParamMock.getModelProperty(MetadataKeyPartModelProperty.class)).thenReturn(Optional.of(new MetadataKeyPartModelProperty(0)));
        when(keyParamMock.getModelProperty(MetadataContentModelProperty.class)).thenReturn(Optional.empty());

        when(contentMock.getName()).thenReturn("content");
        when(contentMock.getType()).thenReturn(stringType);
        when(contentMock.getModelProperty(MetadataContentModelProperty.class)).thenReturn(Optional.of(new MetadataContentModelProperty()));
        when(contentMock.getModelProperty(MetadataKeyPartModelProperty.class)).thenReturn(Optional.empty());

        when(operationModel.getParameterModels()).thenReturn(Arrays.asList(keyParamMock, contentMock));

        when(outputMock.getType()).thenReturn(stringType);
        when(outputMock.hasDynamicType()).thenReturn(true);
        when(operationModel.getOutput()).thenReturn(outputMock);
        when(operationModel.getOutputAttributes()).thenReturn(outputMock);

        when(operationExecutorFactory.createExecutor()).thenReturn(operationExecutor);

        when(resolverSet.resolve(event)).thenReturn(parameters);

        when(configurationInstance.getName()).thenReturn(CONFIG_NAME);
        when(configurationInstance.getModel()).thenReturn(configurationModel);
        when(configurationInstance.getValue()).thenReturn(configuration);
        when(configurationInstance.getConnectionProvider()).thenReturn(Optional.of(connectionProviderWrapper));

        when(configurationProvider.get(event)).thenReturn(configurationInstance);
        when(configurationProvider.getModel()).thenReturn(configurationModel);

        when(configurationModel.getOperationModel(OPERATION_NAME)).thenReturn(Optional.of(operationModel));

        connectionManager = new DefaultConnectionManager(context);
        connectionManager.initialise();
        when(connectionProviderWrapper.getRetryPolicyTemplate()).thenReturn(connectionManager.getDefaultRetryPolicyTemplate());

        when(extensionModel.getModelProperty(SubTypesModelProperty.class)).thenReturn(Optional.empty());
        mockClassLoaderModelProperty(extensionModel, getClass().getClassLoader());
        when(extensionManager.getConfiguration(anyString(), anyObject())).thenReturn(configurationInstance);
        when(extensionManager.getConfiguration(extensionModel, event)).thenReturn(configurationInstance);
        when(configurationProvider.get(anyObject())).thenReturn(configurationInstance);
        when(extensionManager.getConfigurationProvider(extensionModel)).thenReturn(Optional.of(configurationProvider));
        when(extensionManager.getConfigurationProvider(CONFIG_NAME)).thenReturn(Optional.of(configurationProvider));

        messageProcessor = createOperationMessageProcessor();
    }

    @Test
    public void operationExecutorIsInvoked() throws Exception
    {
        messageProcessor.process(event);
        verify(operationExecutor).execute(any(OperationContext.class));
    }

    @Test
    public void operationContextIsWellFormed() throws Exception
    {
        ArgumentCaptor<OperationContext> operationContextCaptor = ArgumentCaptor.forClass(OperationContext.class);
        messageProcessor.process(event);

        verify(operationExecutor).execute(operationContextCaptor.capture());
        OperationContext operationContext = operationContextCaptor.getValue();

        assertThat(operationContext, is(instanceOf(OperationContextAdapter.class)));
        OperationContextAdapter operationContextAdapter = (OperationContextAdapter) operationContext;

        assertThat(operationContextAdapter.getEvent(), is(sameInstance(event)));
        assertThat(operationContextAdapter.getConfiguration().getValue(), is(sameInstance(configuration)));
    }

    @Test
    public void operationReturnsMuleMessageWichKeepsNoValues() throws Exception
    {
        Object payload = new Object();
        DataType dataType = DataType.builder(DataType.OBJECT).charset(getDefaultEncoding(context)).build();
        Serializable attributes = mock(Serializable.class);

        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(new DefaultMuleMessage(payload, dataType, attributes));

        ArgumentCaptor<DefaultMuleMessage> captor = ArgumentCaptor.forClass(DefaultMuleMessage.class);

        messageProcessor.process(event);

        verify(event).setMessage(captor.capture());
        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));

        assertThat(message.getPayload(), is(sameInstance(payload)));
        assertThat(message.getAttributes(), is(sameInstance(attributes)));
        assertThat(message.getDataType(), is(sameInstance(dataType)));
    }

    @Test
    public void operationReturnsMuleMessageOnTarget() throws Exception
    {
        target = TARGET_VAR;
        messageProcessor = createOperationMessageProcessor();

        Object payload = new Object();
        DataType dataType = DataType.builder(DataType.OBJECT).charset(getDefaultEncoding(context)).build();
        Serializable attributes = mock(Serializable.class);

        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(new DefaultMuleMessage(payload, dataType, attributes));

        messageProcessor.process(event);

        verify(event, never()).setMessage(any(org.mule.runtime.core.api.MuleMessage.class));

        ArgumentCaptor<DefaultMuleMessage> captor = ArgumentCaptor.forClass(DefaultMuleMessage.class);
        verify(event).setFlowVariable(same(TARGET_VAR), captor.capture());
        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));

        assertThat(message.getPayload(), is(sameInstance(payload)));
        assertThat(message.getAttributes(), is(sameInstance(attributes)));
        assertThat(message.getDataType(), is(sameInstance(dataType)));
    }

    @Test
    public void operationReturnsMuleMessageButKeepsAttributes() throws Exception
    {
        Object payload = new Object();
        DataType dataType = DataType.builder(DataType.OBJECT).charset(getDefaultEncoding(context)).build();
        Serializable oldAttributes = mock(Serializable.class);

        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(new DefaultMuleMessage(payload, dataType));
        when(event.getMessage()).thenReturn(new DefaultMuleMessage("", DataType.OBJECT, oldAttributes));
        ArgumentCaptor<DefaultMuleMessage> captor = ArgumentCaptor.forClass(DefaultMuleMessage.class);

        messageProcessor.process(event);

        verify(event).setMessage(captor.capture());
        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));

        assertThat(message.getPayload(), is(sameInstance(payload)));
        assertThat(message.getAttributes(), is(sameInstance(oldAttributes)));
        assertThat(message.getDataType(), is(sameInstance(dataType)));
    }

    @Test
    public void operationReturnsMuleMessageThatOnlySpecifiesPayload() throws Exception
    {
        Object payload = "hello world!";
        Serializable oldAttributes = mock(Serializable.class);

        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(new DefaultMuleMessage(payload));
        when(event.getMessage()).thenReturn(new DefaultMuleMessage("", DataType.OBJECT, oldAttributes));
        ArgumentCaptor<DefaultMuleMessage> captor = ArgumentCaptor.forClass(DefaultMuleMessage.class);

        messageProcessor.process(event);

        verify(event).setMessage(captor.capture());
        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));

        assertThat(message.getPayload(), is(sameInstance(payload)));
        assertThat(message.getAttributes(), is(sameInstance(oldAttributes)));
        assertThat(message.getDataType().getType().equals(String.class), is(true));
    }

    @Test
    public void operationReturnsMuleMessageWithPayloadAndAttributes() throws Exception
    {
        Object payload = "hello world!";
        Serializable attributes = mock(Serializable.class);

        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(new DefaultMuleMessage(payload, attributes));
        ArgumentCaptor<DefaultMuleMessage> captor = ArgumentCaptor.forClass(DefaultMuleMessage.class);

        messageProcessor.process(event);

        verify(event).setMessage(captor.capture());
        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));

        assertThat(message.getPayload(), is(sameInstance(payload)));
        assertThat(message.getAttributes(), is(sameInstance(attributes)));
        assertThat(message.getDataType().getType().equals(String.class), is(true));
    }

    @Test
    public void operationReturnsPayloadValue() throws Exception
    {
        Object value = new Object();
        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(value);

        messageProcessor.process(event);

        ArgumentCaptor<org.mule.runtime.core.api.MuleMessage> captor = ArgumentCaptor.forClass(org.mule.runtime.core.api.MuleMessage.class);
        verify(event).setMessage(captor.capture());

        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));
        assertThat(message.getPayload(), is(sameInstance(value)));
    }

    @Test
    public void operationReturnsPayloadValueWithTarget() throws Exception
    {
        target = TARGET_VAR;
        messageProcessor = createOperationMessageProcessor();

        Object value = new Object();
        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(value);

        messageProcessor.process(event);

        verify(event, never()).setMessage(any(org.mule.runtime.core.api.MuleMessage.class));

        ArgumentCaptor<DefaultMuleMessage> captor = ArgumentCaptor.forClass(DefaultMuleMessage.class);
        verify(event).setFlowVariable(same(TARGET_VAR), captor.capture());

        MuleMessage message = captor.getValue();
        assertThat(message, is(notNullValue()));
        assertThat(message.getPayload(), is(sameInstance(value)));
    }

    @Test
    public void operationIsVoid() throws Exception
    {
        when(operationModel.getOutput()).thenReturn(new ImmutableOutputModel("MuleMessage.Payload", toMetadataType(void.class), false, emptySet()));
        messageProcessor = createOperationMessageProcessor();

        when(operationExecutor.execute(any(OperationContext.class))).thenReturn(null);
        assertThat(messageProcessor.process(event), is(sameInstance(event)));
        verify(event, never()).setMessage(any(org.mule.runtime.core.api.MuleMessage.class));
    }

    @Test
    public void executesWithDefaultConfig() throws Exception
    {
        configurationName = null;
        messageProcessor = createOperationMessageProcessor();

        Object defaultConfigInstance = new Object();
        when(configurationInstance.getValue()).thenReturn(defaultConfigInstance);
        when(extensionManager.getConfiguration(extensionModel, event)).thenReturn(configurationInstance);

        ArgumentCaptor<OperationContext> operationContextCaptor = ArgumentCaptor.forClass(OperationContext.class);
        messageProcessor.process(event);
        verify(operationExecutor).execute(operationContextCaptor.capture());

        OperationContext operationContext = operationContextCaptor.getValue();

        assertThat(operationContext, is(instanceOf(OperationContextAdapter.class)));
        assertThat(operationContext.getConfiguration().getValue(), is(sameInstance(defaultConfigInstance)));
    }

    @Test
    public void initialise() throws Exception
    {
        verify((MuleContextAware) operationExecutor).setMuleContext(context);
        verify((Initialisable) operationExecutor).initialise();
    }

    @Test
    public void start() throws Exception
    {
        messageProcessor.start();
        verify((Startable) operationExecutor).start();
    }

    @Test
    public void stop() throws Exception
    {
        messageProcessor.stop();
        verify((Stoppable) operationExecutor).stop();
    }

    @Test
    public void dispose() throws Exception
    {
        messageProcessor.dispose();
        verify((Disposable) operationExecutor).dispose();
    }

    @Test
    public void getMetadataKeys() throws Exception
    {
        MetadataResult<Set<MetadataKey>> metadataKeysResult = messageProcessor.getMetadataKeys();

        verify(operationModel).getMetadataResolverFactory();
        verify(metadataResolverFactory).getKeyResolver();

        assertThat(metadataKeysResult.isSuccess(), is(true));
        final Set<MetadataKey> metadataKeys = metadataKeysResult.get();
        assertThat(metadataKeys.size(), is(2));

        assertThat(metadataKeys, hasItem(MetadataKeyMatcher.metadataKeyWithId(BOOLEAN.name())));
        assertThat(metadataKeys, hasItem(MetadataKeyMatcher.metadataKeyWithId(STRING.name())));
    }

    @Test
    public void getOperationStaticMetadata() throws Exception
    {
        MetadataResult<ComponentMetadataDescriptor> metadata = messageProcessor.getMetadata();

        verify(metadataResolverFactory, never()).getContentResolver();
        verify(metadataResolverFactory, never()).getOutputResolver();

        assertThat(metadata.isSuccess(), is(true));

        assertThat(metadata.get().getOutputMetadata().getPayloadMetadata().getType(), is(outputMock.getType()));

        assertThat(metadata.get().getContentMetadata().get().getType(), is(stringType));

        assertThat(metadata.get().getParametersMetadata().size(), is(1));
        assertThat(metadata.get().getParametersMetadata().get(0).getType(), is(stringType));
    }


    @Test
    public void getOperationDynamicMetadata() throws Exception
    {
        MetadataResult<ComponentMetadataDescriptor> metadata = messageProcessor.getMetadata(newKey("person", "Person").build());

        assertThat(metadata.isSuccess(), is(true));

        assertThat(metadata.get().getOutputMetadata().getPayloadMetadata().getType(), is(TYPE_BUILDER.booleanType().build()));
        assertThat(metadata.get().getOutputMetadata().getAttributesMetadata().getType(), is(TYPE_BUILDER.booleanType().build()));

        assertThat(metadata.get().getContentMetadata().get().getType(), is(TYPE_BUILDER.stringType().build()));

        assertThat(metadata.get().getParametersMetadata().size(), is(1));
        assertThat(metadata.get().getParametersMetadata().get(0).getType(), is(stringType));
    }

    private OperationMessageProcessor createOperationMessageProcessor() throws Exception
    {
        OperationMessageProcessor messageProcessor = new OperationMessageProcessor(extensionModel, operationModel, configurationName,
                                                                                   target, resolverSet, extensionManager);
        messageProcessor.setMuleContext(context);
        messageProcessor.initialise();
        muleContext.getInjector().inject(messageProcessor);
        return messageProcessor;
    }

    private MuleEvent configureMockEvent(MuleEvent mockEvent)
    {
        when(mockEvent.getMessage().getDataType().getMediaType()).thenReturn(MediaType.create("*", "*", defaultCharset()));
        return mockEvent;
    }
}
