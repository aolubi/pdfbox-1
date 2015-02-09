/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.fontbox.util.autodetect;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Implementers provide find method for searching native operating system for available fonts. This class is based on a
 * class provided by Apache FOP. see org.apache.fop.fonts.autodetect.FontDirFinder
 */
public interface FontDirFinder
{

    /**
     * Finds a list of font files.
     * 
     * @return list of font files.
     * @throws IOException In case of an I/O problem
     */
    List<File> find() throws IOException;

    /**
     * Provides a list of platform specific ttf name mappings.
     * 
     * @return a fontname mapping
     */
    Map<String, String> getCommonTTFMapping();

}
