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
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.plugin.descriptor.PluginDescriptor;
import org.apache.maven.api.services.xml.XmlWriterException;
import org.apache.maven.api.services.xml.XmlWriterRequest;
import org.apache.maven.plugin.descriptor.io.PluginDescriptorStaxWriter;

import static org.apache.maven.impl.StaxLocation.getLocation;
import static org.apache.maven.impl.StaxLocation.getMessage;

record WriteRequest(Path path, OutputStream outputStream, Writer writer, PluginDescriptor content) {

    private static final PluginDescriptorStaxWriter WRITER = new PluginDescriptorStaxWriter();

    WriteRequest(XmlWriterRequest<PluginDescriptor> request) {
        this(request.getPath(), request.getOutputStream(), request.getWriter(), request.getContent());
        if (writer == null && outputStream == null && path == null) {
            throw new IllegalArgumentException("writer, outputStream or path must be non null");
        }
    }

    void write() throws XmlWriterException {
        try {
            if (writer != null) {
                WRITER.write(writer, content);
            } else if (outputStream != null) {
                WRITER.write(outputStream, content);
            } else {
                try (OutputStream os = Files.newOutputStream(path)) {
                    WRITER.write(os, content);
                }
            }
        } catch (XmlWriterException | XMLStreamException | IOException e) {
            throw new XmlWriterException("Unable to write plugin: " + getMessage(e), getLocation(e), e);
        }
    }
}
