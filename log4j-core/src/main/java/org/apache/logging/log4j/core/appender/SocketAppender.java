/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.appender;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.Advertiser;
import org.apache.logging.log4j.core.net.DatagramSocketManager;
import org.apache.logging.log4j.core.net.Protocol;
import org.apache.logging.log4j.core.net.SslSocketManager;
import org.apache.logging.log4j.core.net.TcpSocketManager;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.util.Booleans;

/**
 * An Appender that delivers events over socket connections. Supports both TCP and UDP.
 */
@Plugin(name = "Socket", category = "Core", elementType = "appender", printObject = true)
public class SocketAppender extends AbstractOutputStreamAppender<AbstractSocketManager> {

    /**
     * Subclasses can extend this abstract Builder.
     * 
     * Removed deprecated "delayMillis", use "reconnectionDelayMillis".
     * Removed deprecated "reconnectionDelay", use "reconnectionDelayMillis".
     * 
     * @param <B>
     *            This builder class.
     */
    public static class Builder<B extends Builder<B>> extends AbstractOutputStreamAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<SocketAppender> {

        @PluginBuilderAttribute
        private boolean advertise;

        @PluginConfiguration
        private Configuration configuration;

        @PluginBuilderAttribute
        private int connectTimeoutMillis;

        @PluginBuilderAttribute
        private String host;

        @PluginBuilderAttribute
        private boolean immediateFail = true;

        @PluginBuilderAttribute
        private int port;

        @PluginBuilderAttribute
        private Protocol protocol = Protocol.TCP;

        @PluginBuilderAttribute
        @PluginAliases({ "reconnectDelay, delayMillis" })
        private int reconnectDelayMillis;
        
        @PluginElement("SslConfiguration")
        @PluginAliases({ "SslConfig" })
        private SslConfiguration sslConfiguration;

        @SuppressWarnings("resource")
        @Override
        public SocketAppender build() {
            boolean immediateFlush = isImmediateFlush();
            Layout<? extends Serializable> layout = getLayout();
            if (layout == null) {
                layout = SerializedLayout.createLayout();
            }

            final String name = getName();
            if (name == null) {
                SocketAppender.LOGGER.error("No name provided for SocketAppender");
                return null;
            }

            final Protocol actualProtocol = protocol != null ? protocol : Protocol.TCP;
            if (actualProtocol == Protocol.UDP) {
                immediateFlush = true;
            }

            final AbstractSocketManager manager = SocketAppender.createSocketManager(name, actualProtocol, host, port,
                    connectTimeoutMillis, sslConfiguration, reconnectDelayMillis, immediateFail, layout);

            return new SocketAppender(name, layout, getFilter(), manager, isIgnoreExceptions(), immediateFlush,
                    advertise ? configuration.getAdvertiser() : null);
        }

        public boolean getAdvertise() {
            return advertise;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public Protocol getProtocol() {
            return protocol;
        }

        public SslConfiguration getSslConfiguration() {
            return sslConfiguration;
        }

        public boolean getImmediateFail() {
            return immediateFail;
        }

        public B withAdvertise(final boolean advertise) {
            this.advertise = advertise;
            return asBuilder();
        }

        public B withConfiguration(final Configuration configuration) {
            this.configuration = configuration;
            return asBuilder();
        }

        public B withConnectTimeoutMillis(final int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return asBuilder();
        }

        public B withHost(final String host) {
            this.host = host;
            return asBuilder();
        }

        public B withImmediateFail(final boolean immediateFail) {
            this.immediateFail = immediateFail;
            return asBuilder();
        }

        public B withPort(final int port) {
            this.port = port;
            return asBuilder();
        }

        public B withProtocol(final Protocol protocol) {
            this.protocol = protocol;
            return asBuilder();
        }

        public B withReconnectDelayMillis(final int reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return asBuilder();
        }

        public B withSslConfiguration(final SslConfiguration sslConfiguration) {
            this.sslConfiguration = sslConfiguration;
            return asBuilder();
        }
}
    
    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    private final Object advertisement;
    private final Advertiser advertiser;

    protected SocketAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
            final AbstractSocketManager manager, final boolean ignoreExceptions, final boolean immediateFlush,
            final Advertiser advertiser) {
        super(name, layout, filter, ignoreExceptions, immediateFlush, manager);
        if (advertiser != null) {
            final Map<String, String> configuration = new HashMap<>(layout.getContentFormat());
            configuration.putAll(manager.getContentFormat());
            configuration.put("contentType", layout.getContentType());
            configuration.put("name", name);
            this.advertisement = advertiser.advertise(configuration);
        } else {
            this.advertisement = null;
        }
        this.advertiser = advertiser;
    }

    @Override
    public void stop() {
        super.stop();
        if (this.advertiser != null) {
            this.advertiser.unadvertise(this.advertisement);
        }
    }

    /**
     * Creates a socket appender.
     *
     * @param host
     *            The name of the host to connect to.
     * @param port
     *            The port to connect to on the target host.
     * @param protocol
     *            The Protocol to use.
     * @param sslConfig
     *            The SSL configuration file for TCP/SSL, ignored for UPD.
     * @param connectTimeoutMillis
     *            the connect timeout in milliseconds.
     * @param reconnectDelayMillis
     *            The interval in which failed writes should be retried.
     * @param immediateFail
     *            True if the write should fail if no socket is immediately available.
     * @param name
     *            The name of the Appender.
     * @param immediateFlush
     *            "true" if data should be flushed on each write.
     * @param ignoreExceptions
     *            If {@code "true"} (default) exceptions encountered when appending events are logged; otherwise they
     *            are propagated to the caller.
     * @param layout
     *            The layout to use (defaults to SerializedLayout).
     * @param filter
     *            The Filter or null.
     * @param advertise
     *            "true" if the appender configuration should be advertised, "false" otherwise.
     * @param configuration
     *            The Configuration
     * @return A SocketAppender.
     * @deprecated Deprecated in 2.7; use {@link #newBuilder()}
     */
    @Deprecated
    @PluginFactory
    public static SocketAppender createAppender(
            // @formatter:off
            final String host,
            final int port,
            final Protocol protocol,
            final SslConfiguration sslConfig,
            final int connectTimeoutMillis,
            final int reconnectDelayMillis,
            final boolean immediateFail,
            final String name,
            boolean immediateFlush,
            final boolean ignoreExceptions,
            Layout<? extends Serializable> layout,
            final Filter filter,
            final boolean advertise,
            final Configuration configuration) {
            // @formatter:on

        if (true) {
        // @formatter:off
        return newBuilder()
            .withAdvertise(advertise)
            .withConfiguration(configuration)
            .withConnectTimeoutMillis(connectTimeoutMillis)
            .withFilter(filter)
            .withHost(host)
            .withIgnoreExceptions(ignoreExceptions)
            .withImmediateFail(immediateFail)
            .withLayout(layout)
            .withName(name)
            .withPort(port)
            .withProtocol(protocol)
            .withReconnectDelayMillis(reconnectDelayMillis)
            .withSslConfiguration(sslConfig)
            .build();
        // @formatter:on
        } else {
            if (layout == null) {
                layout = SerializedLayout.createLayout();
            }

            if (name == null) {
                LOGGER.error("No name provided for SocketAppender");
                return null;
            }

            final Protocol actualProtocol = protocol != null ? protocol : Protocol.TCP;
            if (actualProtocol == Protocol.UDP) {
                immediateFlush = true;
            }

            final AbstractSocketManager manager = createSocketManager(name, actualProtocol, host, port,
                    connectTimeoutMillis, sslConfig, reconnectDelayMillis, immediateFail, layout);

            return new SocketAppender(name, layout, filter, manager, ignoreExceptions, immediateFlush,
                    advertise ? configuration.getAdvertiser() : null);
        }
    }
    /**
     * Creates a socket appender.
     *
     * @param host
     *            The name of the host to connect to.
     * @param portNum
     *            The port to connect to on the target host.
     * @param protocolIn
     *            The Protocol to use.
     * @param sslConfig
     *            The SSL configuration file for TCP/SSL, ignored for UPD.
     * @param connectTimeoutMillis
     *            the connect timeout in milliseconds.
     * @param delayMillis
     *            The interval in which failed writes should be retried.
     * @param immediateFail
     *            True if the write should fail if no socket is immediately available.
     * @param name
     *            The name of the Appender.
     * @param immediateFlush
     *            "true" if data should be flushed on each write.
     * @param ignore
     *            If {@code "true"} (default) exceptions encountered when appending events are logged; otherwise they
     *            are propagated to the caller.
     * @param layout
     *            The layout to use (defaults to {@link SerializedLayout}).
     * @param filter
     *            The Filter or null.
     * @param advertise
     *            "true" if the appender configuration should be advertised, "false" otherwise.
     * @param config
     *            The Configuration
     * @return A SocketAppender.
     * @deprecated Deprecated in 2.5; use {@link #newBuilder()}
     */
    @Deprecated
    public static SocketAppender createAppender(
            // @formatter:off
            final String host,
            final String portNum,
            final String protocolIn,
            final SslConfiguration sslConfig,
            final int connectTimeoutMillis,
            // deprecated
            final String delayMillis,
            final String immediateFail,
            final String name,
            final String immediateFlush,
            final String ignore,
            final Layout<? extends Serializable> layout,
            final Filter filter,
            final String advertise,
            final Configuration config) {
            // @formatter:on
        final boolean isFlush = Booleans.parseBoolean(immediateFlush, true);
        final boolean isAdvertise = Boolean.parseBoolean(advertise);
        final boolean ignoreExceptions = Booleans.parseBoolean(ignore, true);
        final boolean fail = Booleans.parseBoolean(immediateFail, true);
        final int reconnectDelayMillis = AbstractAppender.parseInt(delayMillis, 0);
        final int port = AbstractAppender.parseInt(portNum, 0);
        final Protocol p = protocolIn == null ? Protocol.UDP : Protocol.valueOf(protocolIn);
        return createAppender(host, port, p, sslConfig, connectTimeoutMillis, reconnectDelayMillis, fail, name, isFlush,
                ignoreExceptions, layout, filter, isAdvertise, config);
    }

    /**
     * Creates an AbstractSocketManager for TCP, UDP, and SSL.
     *
     * @throws IllegalArgumentException
     *             if the protocol cannot be handled.
     */
    protected static AbstractSocketManager createSocketManager(final String name, Protocol protocol, final String host,
            final int port, final int connectTimeoutMillis, final SslConfiguration sslConfig, final int delayMillis,
            final boolean immediateFail, final Layout<? extends Serializable> layout) {
        if (protocol == Protocol.TCP && sslConfig != null) {
            // Upgrade TCP to SSL if an SSL config is specified.
            protocol = Protocol.SSL;
        }
        if (protocol != Protocol.SSL && sslConfig != null) {
            LOGGER.info("Appender {} ignoring SSL configuration for {} protocol", name, protocol);
        }
        switch (protocol) {
        case TCP:
            return TcpSocketManager.getSocketManager(host, port, connectTimeoutMillis, delayMillis, immediateFail,
                    layout);
        case UDP:
            return DatagramSocketManager.getSocketManager(host, port, layout);
        case SSL:
            return SslSocketManager.getSocketManager(sslConfig, host, port, connectTimeoutMillis, delayMillis,
                    immediateFail, layout);
        default:
            throw new IllegalArgumentException(protocol.toString());
        }
    }

    @Override
    protected void directEncodeEvent(final LogEvent event) {
        // Disable garbage-free logging for now:
        // problem with UDP: 8K buffer size means that largish messages get broken up into chunks
        writeByteArrayToManager(event); // revert to classic (non-garbage free) logging
    }
}
