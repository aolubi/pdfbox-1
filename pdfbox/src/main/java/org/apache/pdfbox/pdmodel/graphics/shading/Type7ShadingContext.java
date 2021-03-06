/*
 * Copyright 2014 The Apache Software Foundation.
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
package org.apache.pdfbox.pdmodel.graphics.shading;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.common.PDRange;
import org.apache.pdfbox.util.Matrix;

/**
 * AWT PaintContext for tensor-product patch meshes (type 7) shading. This was
 * done as part of GSoC2014, Tilman Hausherr is the mentor.
 *
 * @author Shaola Ren
 */
class Type7ShadingContext extends PatchMeshesShadingContext
{
    /**
     * Constructor creates an instance to be used for fill operations.
     *
     * @param shading the shading type to be used
     * @param colorModel the color model to be used
     * @param xform transformation for user to device space
     * @param matrix the pattern matrix concatenated with that of the parent content stream
     * @param deviceBounds device bounds
     * @throws IOException if something went wrong
     */
    public Type7ShadingContext(PDShadingType7 shading, ColorModel colorModel, AffineTransform xform,
                               Matrix matrix, Rectangle deviceBounds) throws IOException
    {
        super(shading, colorModel, xform, matrix, deviceBounds);
        patchList = getTensorPatchList(xform, matrix);
        createPixelTable();
    }

    // get the patch list which forms the type 7 shading image from data stream
    private List<Patch> getTensorPatchList(AffineTransform xform, Matrix matrix) throws IOException
    {
        PDShadingType7 tensorShadingType = (PDShadingType7) patchMeshesShadingType;
        COSDictionary dict = tensorShadingType.getCOSDictionary();
        PDRange rangeX = tensorShadingType.getDecodeForParameter(0);
        PDRange rangeY = tensorShadingType.getDecodeForParameter(1);
        PDRange[] colRange = new PDRange[numberOfColorComponents];
        for (int i = 0; i < numberOfColorComponents; ++i)
        {
            colRange[i] = tensorShadingType.getDecodeForParameter(2 + i);
        }
        return getPatchList(xform, matrix, dict, rangeX, rangeY, colRange, 16);
    }

    @Override
    protected Patch generatePatch(Point2D[] points, float[][] color)
    {
        return new TensorPatch(points, color);
    }
}
