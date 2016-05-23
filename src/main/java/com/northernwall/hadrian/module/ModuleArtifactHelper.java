/*
 * Copyright 2015 Richard Thurston.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.northernwall.hadrian.module;

import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.parameters.ParameterChangeListener;
import com.northernwall.hadrian.parameters.Parameters;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class ModuleArtifactHelper implements ParameterChangeListener {
    protected final Parameters parameters;
    private final SematicVersionComparator mavenVersionComparator;
    private int maxMavenVersions;

    public ModuleArtifactHelper(Parameters parameters) {
        this.parameters = parameters;
        this.mavenVersionComparator = new SematicVersionComparator();
    }

    public void setup() {
        maxMavenVersions = parameters.getInt(Const.MAVEN_MAX_VERSIONS, Const.MAVEN_MAX_VERSIONS_DEFAULT);
    }

    @Override
    public void onChange(List<String> keys) {
        setup();
    }
    
    public abstract List<String> readMavenVersions(String groupId, String artifactId);

    protected List<String> processMavenStream(InputStream inputStream) throws Exception {
        List<String> versions = new LinkedList<>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        Element root = doc.getDocumentElement();
        Node versionsNode = root.getElementsByTagName("versions").item(0);
        for (int i = 0; i < versionsNode.getChildNodes().getLength(); i++) {
            Node child = versionsNode.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && !child.getTextContent().endsWith(Const.MAVEN_SNAPSHOT)) {
                versions.add(child.getTextContent());
            }
        }
        Collections.sort(versions, mavenVersionComparator);
        if (versions.size() > maxMavenVersions) {
            return versions.subList(0, maxMavenVersions);
        }
        return versions;
    }

}