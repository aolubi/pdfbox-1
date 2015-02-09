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
package org.apache.pdfbox.service.interactive.form;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.COSWriter;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.interactive.action.PDFormFieldAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;
import org.apache.pdfbox.contentstream.operator.Operator;

/**
 * Create the AcroForms filed appearance helper.
 * <p>
 * A helper class to the {@link AppearanceGenerator} to generate update an AcroForm field appearance.
 * </p>
 * @author Stephan Gerhard
 * @author Ben Litchfield
 */
class AppearanceGeneratorHelper
{
    private static final Log LOG = LogFactory.getLog(AppearanceGeneratorHelper.class);

    private final PDVariableText parent;

    private String value;
    private final DefaultAppearanceHandler defaultAppearanceHandler;

    private final PDAcroForm acroForm;
    private List<COSObjectable> widgets = new ArrayList<COSObjectable>();

    /**
     * Constructs a COSAppearance from the given field.
     *
     * @param theAcroForm the AcroForm that this field is part of.
     * @param field the field which you wish to control the appearance of
     * @throws IOException 
     */
    public AppearanceGeneratorHelper(PDAcroForm theAcroForm, PDVariableText field) throws IOException
    {
        acroForm = theAcroForm;
        parent = field;

        widgets = field.getKids();
        if (widgets == null)
        {
            widgets = new ArrayList<COSObjectable>();
            widgets.add(field.getWidget());
        }
        defaultAppearanceHandler = new DefaultAppearanceHandler(getDefaultAppearance());
    }

    /**
     * Returns the default appearance of a textbox. If the textbox does not have one,
     * then it will be taken from the AcroForm.
     * 
     * @return The DA element
     */
    private String getDefaultAppearance()
    {
        return parent.getDefaultAppearance();
    }

    private int getQ()
    {
        return parent.getQ();
    }

    /**
     * Extracts the original appearance stream into a list of tokens.
     *
     * @return The tokens in the original appearance stream
     */
    private List<Object> getStreamTokens(PDAppearanceStream appearanceStream) throws IOException
    {
        List<Object> tokens = new ArrayList<Object>();
        if (appearanceStream != null)
        {
            tokens = getStreamTokens(appearanceStream.getCOSStream());
        }
        return tokens;
    }

    private List<Object> getStreamTokens(COSStream stream) throws IOException
    {
        List<Object> tokens = new ArrayList<Object>();
        if (stream != null)
        {
            PDFStreamParser parser = new PDFStreamParser(stream);
            parser.parse();
            tokens = parser.getTokens();
            parser.close();
        }
        return tokens;
    }

    /**
     * Tests if the appearance stream already contains content.
     * 
     * @param streamTokens individual tokens within the appearance stream
     *
     * @return true if it contains any content
     */
    private boolean containsMarkedContent(List<Object> streamTokens)
    {
        return streamTokens.contains(Operator.getOperator("BMC"));
    }

    /**
     * This is the public method for setting the appearance stream.
     *
     * @param apValue the String value which the appearance should represent
     *
     * @throws IOException If there is an error creating the stream.
     */
    public void setAppearanceValue(String apValue) throws IOException
    {
        value = apValue;
        Iterator<COSObjectable> widgetIter = widgets.iterator();
        
        while (widgetIter.hasNext())
        {
            COSObjectable next = widgetIter.next();
            PDField field = null;
            PDAnnotationWidget widget;
            if (next instanceof PDField)
            {
                field = (PDField) next;
                widget = field.getWidget();
            }
            else
            {
                widget = (PDAnnotationWidget) next;
            }
            PDFormFieldAdditionalActions actions = null;
            if (field != null)
            {
                actions = field.getActions();
            }

            // in case all tests fail the field will be formatted by acrobat
            // when it is opened. See FreedomExpressions.pdf for an example of this.  
            if (actions == null || actions.getF() == null || 
                    widget.getDictionary().getDictionaryObject(COSName.AP) != null)
            {
                PDAppearanceDictionary appearance = widget.getAppearance();
                if (appearance == null)
                {
                    appearance = new PDAppearanceDictionary();
                    widget.setAppearance(appearance);
                }

                PDAppearanceEntry normalAppearance = appearance.getNormalAppearance();
                // TODO support more than one appearance stream
                PDAppearanceStream appearanceStream = 
                        normalAppearance.isStream() ? normalAppearance.getAppearanceStream() : null;
                if (appearanceStream == null)
                {
                    COSStream cosStream = acroForm.getDocument().getDocument().createCOSStream();
                    appearanceStream = new PDAppearanceStream(cosStream);
                    appearanceStream.setBBox(widget.getRectangle()
                            .createRetranslatedRectangle());
                    appearance.setNormalAppearance(appearanceStream);
                }

                List<Object> tokens = getStreamTokens(appearanceStream);

                PDFont pdFont = getFontAndUpdateResources(appearanceStream);
                
                if (!containsMarkedContent(tokens))
                {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    // BJL 9/25/2004 Must prepend existing stream
                    // because it might have operators to draw things like
                    // rectangles and such
                    ContentStreamWriter writer = new ContentStreamWriter(output);
                    writer.writeTokens(tokens);
                    output.write("/Tx BMC\n".getBytes("ISO-8859-1"));
                    insertGeneratedAppearance(widget, output, pdFont, tokens, appearanceStream);
                    output.write("EMC".getBytes("ISO-8859-1"));
                    writeToStream(output.toByteArray(), appearanceStream);
                }
                else
                {
                    if (!defaultAppearanceHandler.getTokens().isEmpty())
                    {
                        int bmcIndex = tokens.indexOf(Operator.getOperator("BMC"));
                        int emcIndex = tokens.indexOf(Operator.getOperator("EMC"));
                        if (bmcIndex != -1 && emcIndex != -1 && emcIndex == bmcIndex + 1)
                        {
                            // if the EMC immediately follows the BMC index then should
                            // insert the daTokens inbetween the two markers.
                            tokens.addAll(emcIndex, defaultAppearanceHandler.getTokens());
                        }
                    }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ContentStreamWriter writer = new ContentStreamWriter(output);
                    float fontSize = calculateFontSize(pdFont,
                            appearanceStream.getBBox(), tokens);
                    int setFontIndex = tokens.indexOf(Operator.getOperator("Tf"));
                    tokens.set(setFontIndex - 1, new COSFloat(fontSize));

                    int bmcIndex = tokens.indexOf(Operator.getOperator("BMC"));
                    int emcIndex = tokens.indexOf(Operator.getOperator("EMC"));

                    if (bmcIndex != -1)
                    {
                        writer.writeTokens(tokens, 0, bmcIndex + 1);
                    }
                    else
                    {
                        writer.writeTokens(tokens);
                    }
                    output.write("\n".getBytes("ISO-8859-1"));
                    insertGeneratedAppearance(widget, output, pdFont, tokens, appearanceStream);
                    if (emcIndex != -1)
                    {
                        writer.writeTokens(tokens, emcIndex, tokens.size());
                    }
                    writeToStream(output.toByteArray(), appearanceStream);
                }
            }
        }
    }

    private void insertGeneratedAppearance(PDAnnotationWidget fieldWidget, OutputStream output,
            PDFont font, List<Object> tokens, PDAppearanceStream appearanceStream)
            throws IOException
    {
        PrintWriter printWriter = new PrintWriter(output, true);
        float fontSize = 0.0f;
        PDRectangle boundingBox = appearanceStream.getBBox();
        if (boundingBox == null)
        {
            boundingBox = fieldWidget.getRectangle().createRetranslatedRectangle();
        }
        printWriter.println("BT");
        if (!defaultAppearanceHandler.getTokens().isEmpty())
        {
            fontSize = calculateFontSize(font, boundingBox, tokens);
            defaultAppearanceHandler.setFontSize(fontSize);
            ContentStreamWriter daWriter = new ContentStreamWriter(output);
            daWriter.writeTokens(defaultAppearanceHandler.getTokens());
        }

        PDRectangle borderEdge = getSmallestDrawnRectangle(boundingBox, tokens);

        // Acrobat calculates the left and right padding dependent on the offset of the border edge
        // This calculation works for forms having been generated by Acrobat.
        // Need to revisit this for forms being generated with other software.
        float paddingLeft = Math.max(2, Math.round(4 * borderEdge.getLowerLeftX()));
        float paddingRight = Math.max(2,
                Math.round(4 * (boundingBox.getUpperRightX() - borderEdge.getUpperRightX())));
        float verticalOffset = getVerticalOffset(boundingBox, font, fontSize, tokens);

        // Acrobat shifts the value so it aligns to the bottom if
        // the font's caps are larger than the height of the borderEdge
        //
        // This is based on a small sample of test files and might not be generally the case.
        // The fontHeight calculation has been taken from getVerticalOffset().
        // We potentially need to revisit that calculation
        float fontHeight = boundingBox.getHeight() - verticalOffset * 2;

        if (fontHeight + 2 * borderEdge.getLowerLeftX() > borderEdge.getHeight())
        {
            verticalOffset = font.getBoundingBox().getHeight() / 1000 * fontSize
                    - borderEdge.getHeight();
        }

        float leftOffset = 0f;

        // Acrobat aligns left regardless of the quadding if the text is wider than the remaining width
        float stringWidth = (font.getStringWidth(value) / 1000) * fontSize;
        
        int q = getQ();
        if (q == PDTextField.QUADDING_LEFT
                || stringWidth > borderEdge.getWidth() - paddingLeft - paddingRight)
        {
            leftOffset = paddingLeft;
        }
        else if (q == PDTextField.QUADDING_CENTERED)
        {
            leftOffset = (boundingBox.getWidth() - stringWidth) / 2;
        }
        else if (q == PDTextField.QUADDING_RIGHT)
        {
            leftOffset = boundingBox.getWidth() - stringWidth - paddingRight;
        }
        else
        {
            // Unknown quadding value - default to left
            printWriter.println(paddingLeft + " " + verticalOffset + " Td");
            LOG.debug("Unknown justification value, defaulting to left: " + q);
        }

        printWriter.println(leftOffset + " " + verticalOffset + " Td");

        // show the text
        if (!isMultiLineValue(value) || stringWidth > borderEdge.getWidth() - paddingLeft -
                paddingRight)
        {
            printWriter.flush();
            COSWriter.writeString(font.encode(value), output); 
            printWriter.println(" Tj");
        }
        else
        {
            String[] paragraphs = value.split("\n");
            for (int i = 0; i < paragraphs.length; i++)
            {
                boolean lastLine = i == paragraphs.length - 1;
                printWriter.flush();
                COSWriter.writeString(font.encode(value), output);
                printWriter.println(lastLine ? " Tj\n" : "> Tj 0 -13 Td");
            }
        }
        printWriter.println("ET");
        printWriter.flush();
    }

    /*
     * To update an existing appearance stream first copy any needed resources from the
     * document’s DR dictionary into the stream’s Resources dictionary.
     * If the DR and Resources dictionaries contain resources with the same name,
     * the one already in the Resources dictionary shall be left intact,
     * not replaced with the corresponding value from the DR dictionary. 
     */
    private PDFont getFontAndUpdateResources(PDAppearanceStream appearanceStream) throws IOException
    {
        PDFont font = null;
        PDResources streamResources = appearanceStream.getResources();
        PDResources formResources = acroForm.getDefaultResources();
        
        if (streamResources == null && formResources == null)
        {
            throw new IOException("Unable to generate field appearance - missing required resources");
        }
        
        COSName cosFontName = defaultAppearanceHandler.getFontName();
        
        if (streamResources != null)
        {
            font = streamResources.getFont(cosFontName);
            if (font != null)
            {
                return font;
            }
        }
        else
        {
            streamResources = new PDResources();
            appearanceStream.setResources(streamResources);
        }
        
        if (formResources != null)
        {
            font = formResources.getFont(cosFontName);
            if( font == null )
            {
                font = (PDFont) formResources.getFonts().get( fontName );
                // If the font required could not be found either in streamResources and formResources 
                // then one of the available formResources' font is returned
                if ( font == null ) 
                {
                    font = (PDFont) formResources.getFonts().values().toArray()[ formResources.getFonts().size() - 1 ];
                }
            }
            
            if (font != null)
            {
                streamResources.put(cosFontName, font);
                return font;
            }
        }        
        
        // if we get here the font might be there but under a different name
        // which is incorrect but try to treat the resource name as the font name
        font = resolveFont(streamResources, formResources, cosFontName);
            
        if (font != null)
        {
            streamResources.put(cosFontName, font);
            return font;
        }
        else
        {
            throw new IOException("Unable to generate field appearance - missing required font resources: " + cosFontName);
        }
    }
    
    /**
     * Get the font from the resources.
     * @return the retrieved font
     * @throws IOException 
     */
    private PDFont resolveFont(PDResources streamResources, PDResources formResources, COSName cosFontName)
            throws IOException
    {
        // if the font couldn't be retrieved it might be because the font name
        // in the DA string didn't point to the font resources dictionary entry but
        // is the name of the font itself. So try to resolve that.
        
        PDFont font = null;
        if (streamResources != null)
        {
            for (COSName fontName : streamResources.getFontNames()) 
            {
                font = streamResources.getFont(fontName);
                if (font.getName().equals(cosFontName.getName()))
                {
                    return font;
                }
            }
        }

        if (formResources != null)
        {
            for (COSName fontName : formResources.getFontNames()) 
            {
                font = formResources.getFont(fontName);
                if (font.getName().equals(cosFontName.getName()))
                {
                    return font;
                }
            }
        }
        return null;
    }

    
    private boolean isMultiLineValue(String multiLineValue)
    {
        return (parent instanceof PDTextField && ((PDTextField) parent).isMultiline() && multiLineValue.contains("\n"));
    }

    /**
     * Writes the stream to the actual stream in the COSStream.
     *
     * @throws IOException If there is an error writing to the stream
     */
    private void writeToStream(byte[] data, PDAppearanceStream appearanceStream) throws IOException
    {
        OutputStream out = appearanceStream.getCOSStream().createUnfilteredStream();
        out.write(data);
        out.flush();
    }

    /**
     * w in an appearance stream represents the lineWidth.
     * 
     * @return the linewidth
     */
    private float getLineWidth(List<Object> tokens)
    {
        float retval = 1;
        if (tokens != null)
        {
            int btIndex = tokens.indexOf(Operator.getOperator("BT"));
            int wIndex = tokens.indexOf(Operator.getOperator("w"));
            // the w should only be used if it is before the first BT.
            if ((wIndex > 0) && (wIndex < btIndex))
            {
                retval = ((COSNumber) tokens.get(wIndex - 1)).floatValue();
            }
        }
        return retval;
    }

    private PDRectangle getSmallestDrawnRectangle(PDRectangle boundingBox, List<Object> tokens)
    {
        PDRectangle smallest = boundingBox;
        for (int i = 0; i < tokens.size(); i++)
        {
            Object next = tokens.get(i);
            if (next == Operator.getOperator("re"))
            {
                COSNumber x = (COSNumber) tokens.get(i - 4);
                COSNumber y = (COSNumber) tokens.get(i - 3);
                COSNumber width = (COSNumber) tokens.get(i - 2);
                COSNumber height = (COSNumber) tokens.get(i - 1);
                PDRectangle potentialSmallest = new PDRectangle();
                potentialSmallest.setLowerLeftX(x.floatValue());
                potentialSmallest.setLowerLeftY(y.floatValue());
                potentialSmallest.setUpperRightX(x.floatValue() + width.floatValue());
                potentialSmallest.setUpperRightY(y.floatValue() + height.floatValue());
                if (smallest == null
                        || smallest.getLowerLeftX() < potentialSmallest.getLowerLeftX()
                        || smallest.getUpperRightY() > potentialSmallest.getUpperRightY())
                {
                    smallest = potentialSmallest;
                }
            }
        }
        return smallest;
    }

    /**
     * My "not so great" method for calculating the fontsize. It does not work superb, but it
     * handles ok.
     * 
     * @return the calculated font-size
     *
     * @throws IOException If there is an error getting the font height.
     */
    private float calculateFontSize(PDFont pdFont, PDRectangle boundingBox, List<Object> tokens) throws IOException
    {
        float fontSize = 0;
        if (!defaultAppearanceHandler.getTokens().isEmpty())
        {
            fontSize = defaultAppearanceHandler.getFontSize();
        }

        float widthBasedFontSize = Float.MAX_VALUE;

        // TODO review the calculation as this seems to not reflect how Acrobat calculates the font size
        if (parent instanceof PDTextField && ((PDTextField) parent).doNotScroll())
        {
            // if we don't scroll then we will shrink the font to fit into the text area.
            float widthAtFontSize1 = pdFont.getStringWidth(value) / 1000.f;
            float availableWidth = getAvailableWidth(boundingBox, getLineWidth(tokens));
            widthBasedFontSize = availableWidth / widthAtFontSize1;
        }
        if (fontSize == 0)
        {
            float lineWidth = getLineWidth(tokens);
            float height = pdFont.getFontDescriptor().getFontBoundingBox().getHeight() / 1000f;
            float availHeight = getAvailableHeight(boundingBox, lineWidth);
            fontSize = Math.min((availHeight / height), widthBasedFontSize);
        }
        return fontSize;
    }

    /**
     * Calculates where to start putting the text in the box. The positioning is not quite as
     * accurate as when Acrobat places the elements, but it works though.
     *
     * @return the sting for representing the start position of the text
     *
     * @throws IOException If there is an error calculating the text position.
     */
    private float getVerticalOffset(PDRectangle boundingBox, PDFont pdFont, float fontSize,
            List<Object> tokens) throws IOException
    {
        float lineWidth = getLineWidth(tokens);
        float verticalOffset;
        if (parent instanceof PDTextField && ((PDTextField) parent).isMultiline())
        {
            int rows = (int) (getAvailableHeight(boundingBox, lineWidth) / ((int) fontSize));
            verticalOffset = ((rows) * fontSize) - fontSize;
        }
        else
        {
            // BJL 9/25/2004
            // This algorithm is a little bit of black magic. It does
            // not appear to be documented anywhere. Through examining a few
            // PDF documents and the value that Acrobat places in there I
            // have determined that the below method of computing the position
            // is correct for certain documents, but maybe not all. It does
            // work f1040ez.pdf and Form_1.pdf
            PDFontDescriptor fd = pdFont.getFontDescriptor();
            float bBoxHeight = boundingBox.getHeight();
            float fontHeight = fd.getFontBoundingBox().getHeight() + 2 * fd.getDescent();
            fontHeight = (fontHeight / 1000) * fontSize;
            verticalOffset = (bBoxHeight - fontHeight) / 2;
        }
        return verticalOffset;
    }

    /**
     * calculates the available width of the box.
     * 
     * @return the calculated available width of the box
     */
    private float getAvailableWidth(PDRectangle boundingBox, float lineWidth)
    {
        return boundingBox.getWidth() - 2 * lineWidth;
    }

    /**
     * calculates the available height of the box.
     * 
     * @return the calculated available height of the box
     */
    private float getAvailableHeight(PDRectangle boundingBox, float lineWidth)
    {
        return boundingBox.getHeight() - 2 * lineWidth;
    }
}
