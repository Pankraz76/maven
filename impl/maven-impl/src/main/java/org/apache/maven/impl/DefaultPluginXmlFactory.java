/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.impl;

import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.plugin.descriptor.PluginDescriptor;
import org.apache.maven.api.services.xml.PluginXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.api.services.xml.XmlWriterException;
import org.apache.maven.api.services.xml.XmlWriterRequest;
import org.apache.maven.plugin.descriptor.io.PluginDescriptorStaxReader;
import org.apache.maven.plugin.descriptor.io.PluginDescriptorStaxWriter;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.maven.impl.StaxLocation.getLocation;
import static org.apache.maven.impl.StaxLocation.getMessage;

@Named
@Singleton
public class DefaultPluginXmlFactory implements PluginXmlFactory {

    public static final PluginDescriptorStaxWriter WRITER = new PluginDescriptorStaxWriter();
    private static final PluginDescriptorStaxReader READER = new PluginDescriptorStaxReader();
    @Override
    public PluginDescriptor read(@Nonnull XmlReaderRequest request) throws XmlReaderException {
        READER.setAddDefaultEntities(request.isAddDefaultEntities());
        return createReadDescriptor(validate(request));
    }

    @Override
    public void write(XmlWriterRequest<PluginDescriptor> request) throws XmlWriterException {
        createWriteDescriptor(validate(request));
    }

    /**
     * Simply parse the given xml string.
     *
     * @param xml the input XML string
     * @return the parsed object
     * @throws XmlReaderException if an error occurs during the parsing
     * @see #toXmlString(Object)
     */
    public static PluginDescriptor fromXml(@Nonnull String xml) throws XmlReaderException {
        return new DefaultPluginXmlFactory().fromXmlString(xml);
    }

    /**
     * Simply converts the given content to an XML string.
     *
     * @param content the object to convert
     * @return the XML string representation
     * @throws XmlWriterException if an error occurs during the transformation
     * @see #fromXmlString(String)
     */
    public static String toXml(@Nonnull PluginDescriptor content) throws XmlWriterException {
        return new DefaultPluginXmlFactory().toXmlString(content);
    }
    private static PluginDescriptor createReadDescriptor(XmlReaderRequest request) {
        try {
            if (request.getInputStream() != null) {
                return READER.read(request.getInputStream(), request.isStrict());
            } else if (request.getReader() != null) {
                return READER.read(request.getReader(), request.isStrict());
            } else if (request.getPath() != null) {
                try (InputStream is = Files.newInputStream(request.getPath())) {
                    return READER.read(is, request.isStrict());
                }
            }
            try (InputStream is = request.getURL().openStream()) {
                return READER.read(is, request.isStrict());
            }
        } catch (IOException | XMLStreamException e) {
            throw new XmlReaderException("Unable to read plugin: " + getMessage(e), getLocation(e), e);
        }
    }

    private static XmlReaderRequest validate(XmlReaderRequest request) {
        if (request.getInputStream() == null && request.getReader() == null && request.getPath() == null && request.getURL() == null) {
            throw new IllegalArgumentException("writer, outputStream or path must be non null");
        }
        return  request;
    }


    private static void createWriteDescriptor(XmlWriterRequest<PluginDescriptor> request) throws XmlWriterException {
        try {
            if (request.getWriter() != null) {
                WRITER.write(request.getWriter(), request.getContent());
            } else if (request.getOutputStream() != null) {
                WRITER.write(request.getOutputStream(), request.getContent());
            } else if (request.getPath() != null) {
                try (OutputStream os = Files.newOutputStream(request.getPath())) {
                    WRITER.write(os, request.getContent());
                }
            } else {
                throw new IllegalArgumentException("writer, outputStream or path must be non null");
            }
        } catch (XmlWriterException | XMLStreamException | IOException e) {
            throw new XmlWriterException("Unable to write plugin: " + getMessage(e), getLocation(e), e);
        }
    }

    private static XmlWriterRequest<PluginDescriptor> validate(XmlWriterRequest<PluginDescriptor> request) {
        if (request.getWriter() == null && request.getOutputStream() == null && request.getPath() == null) {
            throw new IllegalArgumentException("writer, outputStream or path must be non null");
        }
        return request;
    }


}
