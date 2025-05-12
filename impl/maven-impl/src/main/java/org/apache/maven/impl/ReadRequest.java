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

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.plugin.descriptor.PluginDescriptor;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.plugin.descriptor.io.PluginDescriptorStaxReader;

import static org.apache.maven.impl.StaxLocation.getLocation;
import static org.apache.maven.impl.StaxLocation.getMessage;

record ReadRequest(
        Path path, URL url, Reader reader, InputStream inputStream, boolean addDefaultEntities, boolean strict) {

    private static final PluginDescriptorStaxReader READER = new PluginDescriptorStaxReader();

    ReadRequest(XmlReaderRequest request) {
        this(
                request.getPath(),
                request.getURL(),
                request.getReader(),
                request.getInputStream(),
                request.isAddDefaultEntities(),
                request.isStrict());
        if (inputStream == null && reader == null && path == null && url == null) {
            throw new IllegalArgumentException("writer, outputStream or path must be non null");
        }
        READER.setAddDefaultEntities(addDefaultEntities);
    }

    PluginDescriptor read() throws XmlReaderException {
        try {
            if (inputStream != null) {
                return ReadRequest.READER.read(inputStream, strict);
            } else if (reader != null) {
                return ReadRequest.READER.read(reader, strict);
            } else if (path != null) {
                try (InputStream is = Files.newInputStream(path)) {
                    return ReadRequest.READER.read(is, strict);
                }
            }
            try (InputStream is = url.openStream()) {
                return ReadRequest.READER.read(is, strict);
            }
        } catch (IOException | XMLStreamException e) {
            throw new XmlReaderException("Unable to read plugin: " + getMessage(e), getLocation(e), e);
        }
    }
}
