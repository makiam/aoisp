/* Copyright (C) 2006 by Francois Guillet

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.implicit;

import buoy.widget.*;
import buoy.xml.WidgetDecoder;
import buoy.event.CommandEvent;
import artofillusion.object.ObjectInfo;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.ValueField;
import artofillusion.ModellingApp;
import artofillusion.math.Vec3;
import artofillusion.math.BoundingBox;

import javax.swing.border.TitledBorder;
import java.io.IOException;
import java.io.InputStream;
import java.awt.*;

/**
 * Editor window for ImplicitExpression
 */
public class ImplicitExpressionWindow extends BDialog
{
    private BorderContainer borderContainer1;
    private ColumnContainer paramContainer;
    private BTextField functionValueField;
    private BTextField cutoffValue;
    private BTextField cutoffDistanceValue;
    private BOutline boundsOutline;
    private BTextField xBoundsVF;
    private BTextField yBoundsVF;
    private BTextField zBoundsVF;
    private BButton okButton;
    private BButton cancelButton;
    private IPValueField cutoffVF, cutoffDistanceVF, xboxVF, yboxVF, zboxVF;
    /*** end of prototyping */
    private ImplicitExpression expression;

   public ImplicitExpressionWindow( EditingWindow parent, String title, ObjectInfo obj, Runnable onClose )
   {
       super((WindowWidget)parent, title, true);
       expression = (ImplicitExpression)obj.object;
       InputStream inputStream = null;
       try
       {
           WidgetDecoder decoder = new WidgetDecoder( inputStream = getClass().getResource("/artofillusion/implicit/interfaces/implicitWrapperEdit.xml").openStream(), IPTranslate.getResources() );
           borderContainer1 = (BorderContainer) decoder.getRootObject();
           paramContainer = ((ColumnContainer) decoder.getObject("paramContainer"));
           functionValueField = ((BTextField) decoder.getObject("FunctionValueField"));
           cutoffValue = ((BTextField) decoder.getObject("cutoffValue"));
           cutoffDistanceValue = ((BTextField) decoder.getObject("cutoffDistanceValue"));
           //boundsOutline = ((BOutline) decoder.getObject("boundsOutline"));
           xBoundsVF = ((BTextField) decoder.getObject("xBoundsVF"));
           yBoundsVF = ((BTextField) decoder.getObject("yBoundsVF"));
           zBoundsVF = ((BTextField) decoder.getObject("zBoundsVF"));
           okButton = ((BButton) decoder.getObject("okButton"));
           cancelButton = ((BButton) decoder.getObject("CancelButton"));
           /* end of prototyping */
           //TitledBorder titledBorder = (TitledBorder)boundsOutline.getBorder();
           //titledBorder.setTitle( IPTranslate.text("bounds"));
           okButton.addEventLink(CommandEvent.class, this, "doOK");
           cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
           functionValueField.setText(expression.getExpression());
           cutoffVF = new IPValueField( expression.getCutoff(), ValueField.POSITIVE );
           cutoffVF.setTextField( cutoffValue );
           cutoffDistanceVF = new IPValueField( expression.getCutoffDistance(), ValueField.NONE );
           cutoffDistanceVF.setTextField( cutoffDistanceValue );
           BoundingBox bounds = expression.getBounds();
           xboxVF = new IPValueField( bounds.maxx - bounds.minx, ValueField.POSITIVE );
           xboxVF.setTextField( xBoundsVF );
           yboxVF = new IPValueField( bounds.maxy - bounds.miny, ValueField.POSITIVE );
           yboxVF.setTextField( yBoundsVF );
           zboxVF = new IPValueField( bounds.maxz - bounds.minz, ValueField.POSITIVE );
           zboxVF.setTextField( zBoundsVF );

       }
       catch ( IOException ex )
       {
           ex.printStackTrace();
       }
       finally
       {
           try
           {
               if (inputStream != null)
                   inputStream.close();
           }
           catch(IOException ex)
           {
               ex.printStackTrace();
           }
       }
       setContent(borderContainer1);
       pack();
       ModellingApp.centerDialog((Dialog)this.getComponent(), (Window)(((Widget)parent).getComponent()));
   }

   private void doCancel()
   {
       dispose();
   }

   private void doOK()
   {
       try
       {
           expression.setExpression(functionValueField.getText());
           expression.setCutoff(cutoffVF.getValue());
           expression.setCutoffDistance(cutoffDistanceVF.getValue());
           expression.setBox(xboxVF.getValue(), yboxVF.getValue(), zboxVF.getValue());
           dispose();
       }
       catch (Exception ex)
       {
       }

   }
}
