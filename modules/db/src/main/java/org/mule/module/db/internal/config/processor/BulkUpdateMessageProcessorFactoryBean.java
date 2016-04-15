/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.internal.config.processor;

import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.processor.MessageProcessor;
import org.mule.module.db.internal.domain.autogeneratedkey.AutoGeneratedKeyStrategy;
import org.mule.module.db.internal.domain.executor.BulkQueryExecutorFactory;
import org.mule.module.db.internal.domain.query.Query;
import org.mule.module.db.internal.domain.query.QueryType;
import org.mule.module.db.internal.domain.transaction.TransactionalAction;
import org.mule.module.db.internal.metadata.QueryMetadataProvider;
import org.mule.module.db.internal.processor.AbstractBulkUpdateMessageProcessor;
import org.mule.module.db.internal.processor.DynamicBulkUpdateMessageProcessor;
import org.mule.module.db.internal.processor.PreparedBulkUpdateMessageProcessor;
import org.mule.module.db.internal.resolver.database.DbConfigResolver;
import org.mule.module.db.internal.resolver.param.DynamicParamValueResolver;
import org.mule.module.db.internal.resolver.query.QueryResolver;
import org.mule.module.db.internal.result.statement.StatementStreamingResultSetCloser;

import java.util.List;

import org.springframework.beans.factory.FactoryBean;

/**
 * Creates different {@link AbstractBulkUpdateMessageProcessor} implementations depending on
 * whether the supplied query is parameterized or dynamic.
 */
public class BulkUpdateMessageProcessorFactoryBean implements FactoryBean<MessageProcessor>, MuleContextAware
{

    private final DbConfigResolver dbConfigResolver;
    private final QueryResolver queryResolver;
    private final BulkQueryExecutorFactory bulkUpdateExecutorFactory;
    private final TransactionalAction transactionalAction;
    private final List<QueryType> validQueryTypes;
    private final Query query;
    private String source;
    private String target;
    private QueryMetadataProvider queryMetadataProvider;
    private AutoGeneratedKeyStrategy autoGeneratedKeyStrategy;
    private StatementStreamingResultSetCloser streamingResultSetCloser;
    private MuleContext muleContext;

    public BulkUpdateMessageProcessorFactoryBean(DbConfigResolver dbConfigResolver, QueryResolver queryResolver, BulkQueryExecutorFactory bulkUpdateExecutorFactory, TransactionalAction transactionalAction, List<QueryType> validQueryTypes, Query query)
    {
        this.dbConfigResolver = dbConfigResolver;
        this.queryResolver = queryResolver;
        this.bulkUpdateExecutorFactory = bulkUpdateExecutorFactory;
        this.transactionalAction = transactionalAction;
        this.validQueryTypes = validQueryTypes;
        this.query = query;
    }

    @Override
    public MessageProcessor getObject() throws Exception
    {
        AbstractBulkUpdateMessageProcessor bulkUpdateMessageProcessor;

        if (query.isDynamic())
        {
            bulkUpdateMessageProcessor = new DynamicBulkUpdateMessageProcessor(dbConfigResolver, queryResolver, bulkUpdateExecutorFactory, transactionalAction, validQueryTypes);
        }
        else
        {
            bulkUpdateMessageProcessor = new PreparedBulkUpdateMessageProcessor(dbConfigResolver, queryResolver, bulkUpdateExecutorFactory, transactionalAction, validQueryTypes, new DynamicParamValueResolver(muleContext.getExpressionManager()));
        }

        bulkUpdateMessageProcessor.setSource(source);
        bulkUpdateMessageProcessor.setTarget(target);
        bulkUpdateMessageProcessor.setQueryMetadataProvider(queryMetadataProvider);
        bulkUpdateMessageProcessor.setAutoGeneratedKeyStrategy(autoGeneratedKeyStrategy);
        bulkUpdateMessageProcessor.setStatementStreamingResultSetCloser(streamingResultSetCloser);

        return bulkUpdateMessageProcessor;
    }

    @Override
    public Class<?> getObjectType()
    {
        return MessageProcessor.class;
    }

    @Override
    public boolean isSingleton()
    {
        return true;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public void setTarget(String target)
    {
        this.target = target;
    }

    public void setQueryMetadataProvider(QueryMetadataProvider queryMetadataProvider)
    {
        this.queryMetadataProvider = queryMetadataProvider;
    }

    public void setAutoGeneratedKeyStrategy(AutoGeneratedKeyStrategy autoGeneratedKeyStrategy)
    {
        this.autoGeneratedKeyStrategy = autoGeneratedKeyStrategy;
    }
    
    public void setStatementStreamingResultSetCloser(StatementStreamingResultSetCloser streamingResultSetCloser)
    {
        this.streamingResultSetCloser = streamingResultSetCloser;
    }
    
    @Override
    public void setMuleContext(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }
}
