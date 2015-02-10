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
package org.apache.pdfbox.pdmodel.interactive.form;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.interactive.action.PDFormFieldAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.util.PDFOperator;

/**
 * This one took me a while, but i'm proud to say that it handles the appearance
 * of a textbox. This allows you to apply a value to a field in the document and
 * handle the appearance so that the value is actually visible too. The problem
 * was described by Ben Litchfield, the author of the example:
 * org.apache.pdfbox.examlpes.fdf.ImportFDF. So Ben, here is the solution.
 * 
 * @author sug
 * @author Ben Litchfield
 * @version $Revision: 1.20 $
 */
public class PDAppearance {
    private final PDVariableText parent;

    private String               value;
    private final COSString      defaultAppearance;

    private final PDAcroForm     acroForm;
    private List<COSObjectable>  widgets         = new ArrayList<COSObjectable>();

    /**
     * The highlight color
     * 
     * The color setting is used by Adobe to display the highlight box for
     * selected entries in a list box.
     * 
     * Regardless of other settings in an existing appearance stream Adobe will
     * always use this value.
     */
    private static final String  HIGHLIGHT_COLOR = "0.600006 0.756866 0.854904 rg";

    /**
     * The default padding.
     * 
     * Adobe adds a default padding of 1 to the widgets bounding box.
     * 
     */
    private static final int     DEFAULT_PADDING = 1;

    /**
     * The padding area.
     * 
     * The box from where he padding into the content area will be calculated.
     * 
     * The default value is to do a padding of 1 on each side of the widgets
     * bounding box.
     * 
     * This might be overwritten by a new setting within the BMC/EMC sequence
     */
    private PDRectangle          paddingEdge     = null;

    /**
     * The content area.
     * 
     * The inner box where the content will be printed The default value is to
     * do a padding of 1 on each side of the paddingEdge.
     * 
     * This might be overwritten by a new setting within the BMC/EMC sequence
     */
    private PDRectangle          contentArea     = null;

    /**
     * Constructs a COSAppearnce from the given field.
     * 
     * @param theAcroForm
     *            the acro form that this field is part of.
     * @param field
     *            the field which you wish to control the appearance of
     * @throws IOException
     *             If there is an error creating the appearance.
     */
    public PDAppearance(PDAcroForm theAcroForm, PDVariableText field) throws IOException {
        this.acroForm = theAcroForm;
        this.parent = field;

        this.widgets = field.getKids();
        if (this.widgets == null) {
            this.widgets = new ArrayList<COSObjectable>();
            this.widgets.add(field.getWidget());
        }

        this.defaultAppearance = this.getDefaultAppearance();

    }

    /**
     * Returns the default appearance of a textbox. If the textbox does not have
     * one, then it will be taken from the AcroForm.
     * 
     * @return The DA element
     */
    private COSString getDefaultAppearance() {

        COSString dap = this.parent.getDefaultAppearance();
        if (dap == null) {
            COSArray kids = (COSArray) this.parent.getDictionary().getDictionaryObject(COSName.KIDS);
            if (kids != null && kids.size() > 0) {
                COSDictionary firstKid = (COSDictionary) kids.getObject(0);
                dap = (COSString) firstKid.getDictionaryObject(COSName.DA);
            }
            if (dap == null) {
                dap = (COSString) this.acroForm.getDictionary().getDictionaryObject(COSName.DA);
            }
        }
        return dap;
    }

    private int getQ() {
        int q = this.parent.getQ();
        if (this.parent.getDictionary().getDictionaryObject(COSName.Q) == null) {
            COSArray kids = (COSArray) this.parent.getDictionary().getDictionaryObject(COSName.KIDS);
            if (kids != null && kids.size() > 0) {
                COSDictionary firstKid = (COSDictionary) kids.getObject(0);
                COSNumber qNum = (COSNumber) firstKid.getDictionaryObject(COSName.Q);
                if (qNum != null) {
                    q = qNum.intValue();
                }
            }
        }
        return q;
    }

    /**
     * Extracts the original appearance stream into a list of tokens.
     * 
     * @return The tokens in the original appearance stream
     */
    private List getStreamTokens(PDAppearanceStream appearanceStream) throws IOException {
        List tokens = null;
        if (appearanceStream != null) {
            tokens = this.getStreamTokens(appearanceStream.getStream());
        }
        return tokens;
    }

    private List getStreamTokens(COSString string) throws IOException {
        PDFStreamParser parser;

        List tokens = null;
        if (string != null) {
            ByteArrayInputStream stream = new ByteArrayInputStream(string.getBytes());
            parser = new PDFStreamParser(stream, this.acroForm.getDocument().getDocument().getScratchFile());
            parser.parse();
            tokens = parser.getTokens();
        }
        return tokens;
    }

    private List getStreamTokens(COSStream stream) throws IOException {
        PDFStreamParser parser;

        List tokens = null;
        if (stream != null) {
            parser = new PDFStreamParser(stream);
            parser.parse();
            tokens = parser.getTokens();
        }
        return tokens;
    }

    /**
     * Tests if the apperance stream already contains content.
     * 
     * @return true if it contains any content
     */
    private boolean containsMarkedContent(List stream) {
        return stream.contains(PDFOperator.getOperator("BMC"));
    }

    /**
     * Apply padding to a rectangle.
     * 
     * Padding is used to create different boxes within the widgets 'box model'.
     * 
     * @return a new rectangle with padding applied
     */
    private PDRectangle applyPadding(PDRectangle bbox, float padding) {
        PDRectangle area = new PDRectangle(bbox.getCOSArray());

        area.setLowerLeftX(area.getLowerLeftX() + padding);
        area.setLowerLeftY(area.getLowerLeftY() + padding);
        area.setUpperRightX(area.getUpperRightX() - padding);
        area.setUpperRightY(area.getUpperRightY() - padding);

        return area;
    }

    /**
     * This is the public method for setting the appearance stream.
     * 
     * @param apValue
     *            the String value which the appearance should represent
     * 
     * @throws IOException
     *             If there is an error creating the stream.
     */
    public void setAppearanceValue(String apValue) throws IOException {
        this.value = apValue;

        Iterator<COSObjectable> widgetIter = this.widgets.iterator();
        while (widgetIter.hasNext()) {
            COSObjectable next = widgetIter.next();
            PDField field = null;
            PDAnnotationWidget widget;
            if (next instanceof PDField) {
                field = (PDField) next;
                widget = field.getWidget();
            } else {
                widget = (PDAnnotationWidget) next;
            }
            PDFormFieldAdditionalActions actions = null;
            if (field != null) {
                actions = field.getActions();
            }
            if (actions != null && actions.getF() != null
                    && widget.getDictionary().getDictionaryObject(COSName.AP) == null) {
                // do nothing because the field will be formatted by acrobat
                // when it is opened. See FreedomExpressions.pdf for an example
                // of this.
            } else {

                PDAppearanceDictionary appearance = widget.getAppearance();
                if (appearance == null) {
                    appearance = new PDAppearanceDictionary();
                    widget.setAppearance(appearance);
                }

                Map normalAppearance = appearance.getNormalAppearance();
                PDAppearanceStream appearanceStream = (PDAppearanceStream) normalAppearance.get("default");
                if (appearanceStream == null) {
                    COSStream cosStream = this.acroForm.getDocument().getDocument().createCOSStream();
                    appearanceStream = new PDAppearanceStream(cosStream);
                    appearanceStream.setBoundingBox(widget.getRectangle().createRetranslatedRectangle());
                    appearance.setNormalAppearance(appearanceStream);
                }

                List tokens = this.getStreamTokens(appearanceStream);
                List daTokens = this.getStreamTokens(this.getDefaultAppearance());
                PDFont pdFont = this.getFontAndUpdateResources(tokens, appearanceStream);

                // Special handling for listboxes to address PDFBOX-2249
                // TODO: Shall be addressed properly in a future release
                if (this.parent instanceof PDChoiceField
                        && (this.parent.getFieldFlags() & ((PDChoiceField) this.parent).FLAG_COMBO) == 0) {
                    this.generateListboxAppearance(widget, pdFont, tokens, daTokens, appearanceStream, this.value);
                } else {
                    if (!this.containsMarkedContent(tokens)) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();

                        // BJL 9/25/2004 Must prepend existing stream
                        // because it might have operators to draw things like
                        // rectangles and such
                        ContentStreamWriter writer = new ContentStreamWriter(output);
                        writer.writeTokens(tokens);

                        output.write(" /Tx BMC\n".getBytes("ISO-8859-1"));
                        this.insertGeneratedAppearance(widget, output, pdFont, tokens, appearanceStream);
                        output.write(" EMC".getBytes("ISO-8859-1"));
                        this.writeToStream(output.toByteArray(), appearanceStream);
                    } else {
                        if (tokens != null) {
                            if (daTokens != null) {
                                int bmcIndex = tokens.indexOf(PDFOperator.getOperator("BMC"));
                                int emcIndex = tokens.indexOf(PDFOperator.getOperator("EMC"));
                                if (bmcIndex != -1 && emcIndex != -1 && emcIndex == bmcIndex + 1) {
                                    // if the EMC immediately follows the BMC
                                    // index then should
                                    // insert the daTokens inbetween the two
                                    // markers.
                                    tokens.addAll(emcIndex, daTokens);
                                }
                            }
                            ByteArrayOutputStream output = new ByteArrayOutputStream();
                            ContentStreamWriter writer = new ContentStreamWriter(output);
                            float fontSize = this.calculateFontSize(pdFont, appearanceStream.getBoundingBox(), tokens,
                                    daTokens);
                            boolean foundString = false;
                            int indexOfString = -1;

                            int setFontIndex = tokens.indexOf(PDFOperator.getOperator("Tf"));
                            tokens.set(setFontIndex - 1, new COSFloat(fontSize));

                            int bmcIndex = tokens.indexOf(PDFOperator.getOperator("BMC"));
                            int emcIndex = tokens.indexOf(PDFOperator.getOperator("EMC"));

                            if (bmcIndex != -1) {
                                writer.writeTokens(tokens, 0, bmcIndex + 1);
                            } else {
                                writer.writeTokens(tokens);
                            }
                            output.write("\n".getBytes("ISO-8859-1"));
                            this.insertGeneratedAppearance(widget, output, pdFont, tokens, appearanceStream);
                            if (emcIndex != -1) {
                                writer.writeTokens(tokens, emcIndex, tokens.size());
                            }

                            this.writeToStream(output.toByteArray(), appearanceStream);
                        } else {
                            // hmm?
                        }
                    }
                }
            }
        }

    }

    private void generateListboxAppearance(PDAnnotationWidget fieldWidget, PDFont pdFont, List tokens, List daTokens,
            PDAppearanceStream appearanceStream, String fieldValue) throws IOException {

        // create paddingEdge and contentArea from bounding box
        // Default the contentArea to the boundingBox
        // taking the padding into account
        this.paddingEdge = this.applyPadding(appearanceStream.getBoundingBox(), DEFAULT_PADDING);
        this.contentArea = this.applyPadding(this.paddingEdge, DEFAULT_PADDING);

        if (!this.containsMarkedContent(tokens)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            // BJL 9/25/2004 Must prepend existing stream
            // because it might have operators to draw things like
            // rectangles and such
            ContentStreamWriter writer = new ContentStreamWriter(output);
            writer.writeTokens(tokens);

            output.write(" /Tx BMC\n".getBytes("ISO-8859-1"));
            this.insertGeneratedListboxAppearance(fieldWidget, output, pdFont, tokens, appearanceStream);
            output.write(" EMC".getBytes("ISO-8859-1"));
            this.writeToStream(output.toByteArray(), appearanceStream);
        } else {
            if (tokens != null) {
                if (daTokens != null) {
                    int bmcIndex = tokens.indexOf(PDFOperator.getOperator("BMC"));
                    int emcIndex = tokens.indexOf(PDFOperator.getOperator("EMC"));
                    if (bmcIndex != -1 && emcIndex != -1 && emcIndex == bmcIndex + 1) {
                        // if the EMC immediately follows the BMC index then
                        // should
                        // insert the daTokens inbetween the two markers.
                        tokens.addAll(emcIndex, daTokens);
                    }
                }

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ContentStreamWriter writer = new ContentStreamWriter(output);
                float fontSize = this.calculateListboxFontSize(pdFont, appearanceStream.getBoundingBox(), tokens,
                        daTokens);
                boolean foundString = false;

                int setFontIndex = tokens.indexOf(PDFOperator.getOperator("Tf"));
                tokens.set(setFontIndex - 1, new COSFloat(fontSize));

                int bmcIndex = tokens.indexOf(PDFOperator.getOperator("BMC"));
                /*
                 * Get the contentArea.
                 * 
                 * There might be an inner box defined which defines the area
                 * where the text is printed. This typically looks like ... q 1
                 * 1 98 70 re W ...
                 */

                {
                    int beginTextIndex = tokens.indexOf(PDFOperator.getOperator("BT"));
                    if (beginTextIndex != -1) {

                        ListIterator innerTokens = tokens.listIterator(bmcIndex);

                        while (innerTokens.hasNext()) {
                            if (innerTokens.next() == PDFOperator.getOperator("re")
                                    && innerTokens.next() == PDFOperator.getOperator("W")) {

                                COSArray array = new COSArray();
                                array.add((COSNumber) tokens.get(innerTokens.previousIndex() - 5));
                                array.add((COSNumber) tokens.get(innerTokens.previousIndex() - 4));
                                array.add((COSNumber) tokens.get(innerTokens.previousIndex() - 3));
                                array.add((COSNumber) tokens.get(innerTokens.previousIndex() - 2));

                                this.paddingEdge = new PDRectangle(array);

                                // as the re operator is using start and
                                // width/height adjust the generated
                                // dimensions
                                this.paddingEdge.setUpperRightX(this.paddingEdge.getLowerLeftX()
                                        + this.paddingEdge.getUpperRightX());
                                this.paddingEdge.setUpperRightY(this.paddingEdge.getLowerLeftY()
                                        + this.paddingEdge.getUpperRightY());

                                this.contentArea = this.applyPadding(this.paddingEdge, this.paddingEdge.getLowerLeftX()
                                        - appearanceStream.getBoundingBox().getLowerLeftX());

                                break;
                            }
                        }
                    }
                }

                int emcIndex = tokens.indexOf(PDFOperator.getOperator("EMC"));

                if (bmcIndex != -1) {
                    writer.writeTokens(tokens, 0, bmcIndex + 1);
                } else {
                    writer.writeTokens(tokens);
                }
                output.write("\n".getBytes("ISO-8859-1"));
                this.insertGeneratedListboxAppearance(fieldWidget, output, pdFont, tokens, appearanceStream);
                if (emcIndex != -1) {
                    writer.writeTokens(tokens, emcIndex, tokens.size());
                }

                this.writeToStream(output.toByteArray(), appearanceStream);
            } else {
                // hmm?
            }
        }
    }

    private void insertGeneratedAppearance(PDAnnotationWidget fieldWidget, OutputStream output, PDFont pdFont,
            List tokens, PDAppearanceStream appearanceStream) throws IOException {
        PrintWriter printWriter = new PrintWriter(output, true);
        float fontSize = 0.0f;
        PDRectangle boundingBox = appearanceStream.getBoundingBox();
        if (boundingBox == null) {
            boundingBox = fieldWidget.getRectangle().createRetranslatedRectangle();
        }

        // Handle a field with the comb flag being set differently to
        // address PDFBOX-91
        // TODO: Shall be addressed properly in a future release
        if (this.parent.shouldComb()) {
            this.insertGeneratedPaddingEdge(printWriter, appearanceStream);
        }

        printWriter.println("BT");

        if (this.defaultAppearance != null) {
            String daString = this.defaultAppearance.getString();
            PDFStreamParser daParser = new PDFStreamParser(new ByteArrayInputStream(daString.getBytes("ISO-8859-1")),
                    null);
            daParser.parse();
            List<Object> daTokens = daParser.getTokens();
            fontSize = this.calculateFontSize(pdFont, boundingBox, tokens, daTokens);
            int fontIndex = daTokens.indexOf(PDFOperator.getOperator("Tf"));
            if (fontIndex != -1) {
                daTokens.set(fontIndex - 1, new COSFloat(fontSize));
            }
            ContentStreamWriter daWriter = new ContentStreamWriter(output);
            daWriter.writeTokens(daTokens);
        }

        // Handle a field with the comb flag being set differently to
        // address PDFBOX-91
        // TODO: Shall be addressed properly in a future release
        if (this.parent.shouldComb() && this.parent.getDictionary().getInt("MaxLen") != -1) {
            this.insertGeneratedCombAppearance(printWriter, pdFont, appearanceStream, fontSize);
        } else {
            printWriter.println(this.getTextPosition(boundingBox, pdFont, fontSize, tokens));
            int q = this.getQ();
            if (q == PDTextbox.QUADDING_LEFT) {
                // do nothing because left is default
            } else if (q == PDTextbox.QUADDING_CENTERED || q == PDTextbox.QUADDING_RIGHT) {
                float fieldWidth = boundingBox.getWidth();
                float stringWidth = (pdFont.getStringWidth(this.value) / 1000) * fontSize;
                float adjustAmount = fieldWidth - stringWidth - 4;

                if (q == PDTextbox.QUADDING_CENTERED) {
                    adjustAmount = adjustAmount / 2.0f;
                }

                printWriter.println(adjustAmount + " 0 Td");
            } else {
                throw new IOException("Error: Unknown justification value:" + q);
            }
            // add the value as hex string to deal with non ISO-8859-1 data
            // values
            if (!this.isMultiLineValue(this.value)) {
                printWriter.println("<" + new COSString(this.value).getHexString() + "> Tj");
            } else {
                String[] lines = this.value.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    boolean lastLine = i == (lines.length - 1);
                    String endingTag = lastLine ? "> Tj\n" : "> Tj 0 -13 Td";
                    printWriter.print("<" + new COSString(lines[i]).getHexString() + endingTag);
                }
            }
        }
        printWriter.println("ET");
        printWriter.flush();
    }

    private void insertGeneratedPaddingEdge(PrintWriter printWriter, PDAppearanceStream appearanceStream) {
        // create paddingEdge and contentArea from bounding box
        // Default the contentArea to the boundingBox
        // taking the padding into account
        this.paddingEdge = this.applyPadding(appearanceStream.getBoundingBox(), DEFAULT_PADDING);

        // print the paddingEdge
        printWriter.println("q");

        printWriter.println(this.paddingEdge.getLowerLeftX() + " " + this.paddingEdge.getLowerLeftY() + " "
                + this.paddingEdge.getWidth() + " " + this.paddingEdge.getHeight() + " " + " re");
        printWriter.println("W");
        printWriter.println("n");
    }

    private void insertGeneratedCombAppearance(PrintWriter printWriter, PDFont pdFont,
            PDAppearanceStream appearanceStream, float fontSize) throws IOException {

        // TODO: Currently the quadding is not taken into account
        // so the comb is always filled from left to right.

        int maxLen = this.parent.getDictionary().getInt("MaxLen");

        int numChars = maxLen;

        if (this.value.length() < maxLen) {
            numChars = this.value.length();
        }

        float combWidth = appearanceStream.getBoundingBox().getWidth() / maxLen;
        float ascentAtFontSize = pdFont.getFontDescriptor().getAscent() / 1000 * fontSize;
        float baselineOffset = this.paddingEdge.getLowerLeftY()
                + (appearanceStream.getBoundingBox().getHeight() - ascentAtFontSize) / 2;

        float prevCharWidth = 0f;
        float currCharWidth = 0f;

        float xOffset = combWidth / 2;

        String combString = "";

        for (int i = 0; i < numChars; i++) {
            combString = this.value.substring(i, i + 1);
            currCharWidth = pdFont.getStringWidth(combString) / 1000 * fontSize / 2;

            xOffset = xOffset + prevCharWidth / 2 - currCharWidth / 2;

            printWriter.println(xOffset + " " + baselineOffset + " Td");
            printWriter.println("<" + new COSString(combString).getHexString() + "> Tj");

            baselineOffset = 0;
            prevCharWidth = currCharWidth;
            xOffset = combWidth;
        }
    }

    private void insertGeneratedListboxAppearance(PDAnnotationWidget fieldWidget, OutputStream output, PDFont pdFont,
            List tokens, PDAppearanceStream appearanceStream) throws IOException {
        PrintWriter printWriter = new PrintWriter(output, true);
        float fontSize = 0.0f;
        PDRectangle boundingBox = appearanceStream.getBoundingBox();
        if (boundingBox == null) {
            boundingBox = fieldWidget.getRectangle().createRetranslatedRectangle();
        }

        List<Object> daTokens = null;

        if (this.defaultAppearance != null) {
            String daString = this.defaultAppearance.getString();
            PDFStreamParser daParser = new PDFStreamParser(new ByteArrayInputStream(daString.getBytes("ISO-8859-1")),
                    null);
            daParser.parse();
            daTokens = daParser.getTokens();

            fontSize = this.calculateListboxFontSize(pdFont, this.contentArea, tokens, daTokens);
            int fontIndex = daTokens.indexOf(PDFOperator.getOperator("Tf"));
            if (fontIndex != -1) {
                daTokens.set(fontIndex - 1, new COSFloat(fontSize));
            }
        }

        // print the paddingEdge
        printWriter.println("q");

        printWriter.println(this.paddingEdge.getLowerLeftX() + " " + this.paddingEdge.getLowerLeftY() + " "
                + this.paddingEdge.getWidth() + " " + this.paddingEdge.getHeight() + " " + " re");
        printWriter.println("W");
        printWriter.println("n");

        // print the highlight color
        printWriter.println(HIGHLIGHT_COLOR);

        /*
         * for a listbox output the rectangle highlighting the selected value
         */

        COSArray indexEntries = ((PDChoiceField) this.parent).getSelectedOptions();

        int selectedIndex = ((COSInteger) indexEntries.get(0)).intValue();

        // The first entry which shall be presented might be adjusted by the
        // optional TI key
        // If this entry is present the first entry to be displayed is the keys
        // value otherwise
        // display starts with the first entry in Opt.
        int topIndex = ((PDChoiceField) this.parent).getTopIndex();

        float highlightBoxHeight = pdFont.getFontBoundingBox().getHeight() / 1000 * fontSize;

        printWriter.println(this.paddingEdge.getLowerLeftX() + " "
                + (this.paddingEdge.getUpperRightY() - highlightBoxHeight * (selectedIndex - topIndex + 1)) + " "
                + this.paddingEdge.getWidth() + " " + (highlightBoxHeight) + " re");
        printWriter.println("f");
        printWriter.println("0 g");
        printWriter.println("0 G");
        printWriter.println("1 w");

        // start of text output
        printWriter.println("BT");

        if (this.defaultAppearance != null) {
            ContentStreamWriter daWriter = new ContentStreamWriter(output);
            daWriter.writeTokens(daTokens);
        }

        int q = this.getQ();
        if (q == PDTextbox.QUADDING_LEFT) {
            // do nothing because left is default
        } else if (q == PDTextbox.QUADDING_CENTERED || q == PDTextbox.QUADDING_RIGHT) {
            float fieldWidth = boundingBox.getWidth();
            float stringWidth = (pdFont.getStringWidth(this.value) / 1000) * fontSize;
            float adjustAmount = fieldWidth - stringWidth - 4;

            if (q == PDTextbox.QUADDING_CENTERED) {
                adjustAmount = adjustAmount / 2.0f;
            }

            printWriter.println(adjustAmount + " 0 Td");
        } else {
            throw new IOException("Error: Unknown justification value:" + q);
        }

        COSArray options = ((PDChoiceField) this.parent).getOptions();

        float yTextPos = this.contentArea.getUpperRightY();

        for (int i = topIndex; i < options.size(); i++) {
            COSBase option = options.getObject(i);
            COSArray optionPair = (COSArray) option;
            COSString optionKey = (COSString) optionPair.getObject(0);
            COSString optionValue = (COSString) optionPair.getObject(1);

            if (i == topIndex) {
                yTextPos = yTextPos - pdFont.getFontDescriptor().getAscent() / 1000 * fontSize;
            } else {
                yTextPos = yTextPos - pdFont.getFontBoundingBox().getHeight() / 1000 * fontSize;
                printWriter.println("BT");
            }

            printWriter.println(this.contentArea.getLowerLeftX() + " " + yTextPos + " Td");
            printWriter.println("<" + optionValue.getHexString() + "> Tj");

            if (i - topIndex != (options.size() - 1)) {
                printWriter.println("ET");
            }

        }

        printWriter.println("ET");
        printWriter.println("Q");
        printWriter.flush();
    }

    private PDFont getFontAndUpdateResources(List tokens, PDAppearanceStream appearanceStream) throws IOException {
        PDFont retval = null;
        PDResources streamResources = appearanceStream.getResources();
        PDResources formResources = this.acroForm.getDefaultResources();
        if (formResources != null) {
            if (streamResources == null) {
                streamResources = new PDResources();
                appearanceStream.setResources(streamResources);
            }

            COSString da = this.getDefaultAppearance();
            if (da != null) {
                String data = da.getString();
                PDFStreamParser streamParser = new PDFStreamParser(
                        new ByteArrayInputStream(data.getBytes("ISO-8859-1")), null);
                streamParser.parse();
                tokens = streamParser.getTokens();
            }

            int setFontIndex = tokens.indexOf(PDFOperator.getOperator("Tf"));
            COSName cosFontName = (COSName) tokens.get(setFontIndex - 2);
            String fontName = cosFontName.getName();
            retval = (PDFont) streamResources.getFonts().get(fontName);
            if (retval == null) {
                retval = (PDFont) formResources.getFonts().get(fontName);
                // If the font required could not be found either in
                // streamResources and formResources
                // then one of the available formResources' font is returned
                if (retval == null) {
                    retval = (PDFont) formResources.getFonts().values().toArray()[formResources.getFonts().size() - 1];
                }
                streamResources.addFont(retval, fontName);
            }
        }
        return retval;
    }

    private boolean isMultiLineValue(String value) {
        return (this.parent.isMultiline() && value.contains("\n"));
    }

    /**
     * Writes the stream to the actual stream in the COSStream.
     * 
     * @throws IOException
     *             If there is an error writing to the stream
     */
    private void writeToStream(byte[] data, PDAppearanceStream appearanceStream) throws IOException {
        OutputStream out = appearanceStream.getStream().createUnfilteredStream();
        out.write(data);
        out.flush();
    }

    /**
     * w in an appearance stream represents the lineWidth.
     * 
     * @return the linewidth
     */
    private float getLineWidth(List tokens) {

        float retval = 1;
        if (tokens != null) {
            int btIndex = tokens.indexOf(PDFOperator.getOperator("BT"));
            int wIndex = tokens.indexOf(PDFOperator.getOperator("w"));
            // the w should only be used if it is before the first BT.
            if ((wIndex > 0) && (wIndex < btIndex)) {
                retval = ((COSNumber) tokens.get(wIndex - 1)).floatValue();
            }
        }
        return retval;
    }

    private PDRectangle getSmallestDrawnRectangle(PDRectangle boundingBox, List tokens) {
        PDRectangle smallest = boundingBox;
        for (int i = 0; i < tokens.size(); i++) {
            Object next = tokens.get(i);
            if (next == PDFOperator.getOperator("re")) {
                COSNumber x = (COSNumber) tokens.get(i - 4);
                COSNumber y = (COSNumber) tokens.get(i - 3);
                COSNumber width = (COSNumber) tokens.get(i - 2);
                COSNumber height = (COSNumber) tokens.get(i - 1);
                PDRectangle potentialSmallest = new PDRectangle();
                potentialSmallest.setLowerLeftX(x.floatValue());
                potentialSmallest.setLowerLeftY(y.floatValue());
                potentialSmallest.setUpperRightX(x.floatValue() + width.floatValue());
                potentialSmallest.setUpperRightY(y.floatValue() + height.floatValue());
                if (smallest == null || smallest.getLowerLeftX() < potentialSmallest.getLowerLeftX()
                        || smallest.getUpperRightY() > potentialSmallest.getUpperRightY()) {
                    smallest = potentialSmallest;
                }

            }
        }
        return smallest;
    }

    /**
     * My "not so great" method for calculating the fontsize. It does not work
     * superb, but it handles ok.
     * 
     * @return the calculated font-size
     * 
     * @throws IOException
     *             If there is an error getting the font height.
     */
    private float calculateFontSize(PDFont pdFont, PDRectangle boundingBox, List<Object> tokens, List<Object> daTokens)
            throws IOException {
        float fontSize = 0;
        if (daTokens != null) {
            // daString looks like "BMC /Helv 3.4 Tf EMC"
            // use the fontsize of the default existing apperance stream
            int fontIndex = daTokens.indexOf(PDFOperator.getOperator("Tf"));
            if (fontIndex != -1) {
                fontSize = ((COSNumber) daTokens.get(fontIndex - 1)).floatValue();
            }
        }

        float widthBasedFontSize = Float.MAX_VALUE;

        if (this.parent.doNotScroll()) {
            // if we don't scroll then we will shrink the font to fit into the
            // text area.
            float widthAtFontSize1 = pdFont.getStringWidth(this.value) / 1000.f;
            float availableWidth = this.getAvailableWidth(boundingBox, this.getLineWidth(tokens));
            widthBasedFontSize = availableWidth / widthAtFontSize1;
        } else if (fontSize == 0) {
            float lineWidth = this.getLineWidth(tokens);
            float stringWidth = pdFont.getStringWidth(this.value);
            float height = 0;
            if (pdFont instanceof PDSimpleFont) {
                height = ((PDSimpleFont) pdFont).getFontBoundingBox().getHeight();
            } else {
                // now much we can do, so lets assume font is square and use
                // width
                // as the height
                height = pdFont.getAverageFontWidth();
            }
            height = height / 1000f;

            float availHeight = this.getAvailableHeight(boundingBox, lineWidth);
            fontSize = Math.min((availHeight / height), widthBasedFontSize);
        }
        return fontSize;
    }

    /**
     * Calculate the fontsize if autosizing was set.
     * 
     * @return the calculated font-size
     * 
     * @throws IOException
     *             If there is an error getting the font height.
     */
    private float calculateListboxFontSize(PDFont pdFont, PDRectangle contentArea, List tokens, List daTokens)
            throws IOException {
        float fontSize = 0;
        if (daTokens != null) {
            // daString looks like "BMC /Helv 3.4 Tf EMC"

            int fontIndex = daTokens.indexOf(PDFOperator.getOperator("Tf"));
            if (fontIndex != -1) {
                fontSize = ((COSNumber) daTokens.get(fontIndex - 1)).floatValue();
            }
        }

        // font size of 0 means that the font size has to be calculated
        // dependent on the strings length so that the string with the
        // largest width fits into the box available.
        if (fontSize == 0) {

            COSArray options = ((PDChoiceField) this.parent).getOptions();

            float maxOptWidth = 0;

            for (int i = 0; i < options.size(); i++) {

                COSBase option = options.getObject(i);
                COSArray optionPair = (COSArray) option;
                COSString optionValue = (COSString) optionPair.getObject(1);
                maxOptWidth = Math.max(pdFont.getStringWidth(optionValue.getString()) / 1000.f, maxOptWidth);
            }

            float availableWidth = this.getAvailableWidth(contentArea, this.getLineWidth(tokens));
            fontSize = availableWidth / maxOptWidth;
        }

        return fontSize;
    }

    /**
     * Calculates where to start putting the text in the box. The positioning is
     * not quite as accurate as when Acrobat places the elements, but it works
     * though.
     * 
     * @return the sting for representing the start position of the text
     * 
     * @throws IOException
     *             If there is an error calculating the text position.
     */
    private String getTextPosition(PDRectangle boundingBox, PDFont pdFont, float fontSize, List tokens)
            throws IOException {
        float lineWidth = this.getLineWidth(tokens);
        float pos = 0.0f;
        if (this.parent.isMultiline()) {
            int rows = (int) (this.getAvailableHeight(boundingBox, lineWidth) / ((int) fontSize));
            pos = ((rows) * fontSize) - fontSize;
        } else {
            if (pdFont instanceof PDSimpleFont) {
                // BJL 9/25/2004
                // This algorithm is a little bit of black magic. It does
                // not appear to be documented anywhere. Through examining a few
                // PDF documents and the value that Acrobat places in there I
                // have determined that the below method of computing the
                // position
                // is correct for certain documents, but maybe not all. It does
                // work f1040ez.pdf and Form_1.pdf
                PDFontDescriptor fd = ((PDSimpleFont) pdFont).getFontDescriptor();
                float bBoxHeight = boundingBox.getHeight();
                float fontHeight = fd.getFontBoundingBox().getHeight() + 2 * fd.getDescent();
                fontHeight = (fontHeight / 1000) * fontSize;
                pos = (bBoxHeight - fontHeight) / 2;
            } else {
                throw new IOException("Error: Don't know how to calculate the position for non-simple fonts");
            }
        }
        PDRectangle innerBox = this.getSmallestDrawnRectangle(boundingBox, tokens);
        float xInset = 2 + 2 * (boundingBox.getWidth() - innerBox.getWidth());
        return Math.round(xInset) + " " + pos + " Td";
    }

    /**
     * calculates the available width of the box.
     * 
     * @return the calculated available width of the box
     */
    private float getAvailableWidth(PDRectangle boundingBox, float lineWidth) {
        return boundingBox.getWidth() - 2 * lineWidth;
    }

    /**
     * calculates the available height of the box.
     * 
     * @return the calculated available height of the box
     */
    private float getAvailableHeight(PDRectangle boundingBox, float lineWidth) {
        return boundingBox.getHeight() - 2 * lineWidth;
    }
}
