/* Copyright (C) 2006 by Francois Guillet

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.implicit;

import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.*;
import artofillusion.animation.PositionTrack;
import artofillusion.animation.RotationTrack;
import artofillusion.object.ObjectInfo;
import artofillusion.object.Object3D;
import artofillusion.math.Vec3;
import artofillusion.math.CoordinateSystem;
import buoy.event.WidgetMouseEvent;

import javax.swing.*;
import java.awt.*;

/**
 * Editing Tool used to create an implicit expression object.
 */
public class CreateImplicitExpressionTool extends EditingTool
{
    private EditingWindow edw;
        private static Image icon, selectedIcon;
        protected static int counter = 1;
        boolean shiftDown;
        Point clickPoint;

        public CreateImplicitExpressionTool(LayoutWindow layout)
        {
            super(layout);
            edw = layout;
            if ( icon == null )
                icon = new ImageIcon( getClass().getResource( "/artofillusion/implicit/Icons/implicit.gif" ) ).getImage();
            if ( selectedIcon == null )
                selectedIcon = new ImageIcon( getClass().getResource( "/artofillusion/implicit/Icons/selected/implicit.gif" ) ).getImage();
        }

        public void activate()
        {
            super.activate();
            setHelpText();
        }

        private void setHelpText()
        {
            theWindow.setHelpText(IPTranslate.text("createImplicitObjectTool.helpText" ) );

        }

        public int whichClicks()
        {
            return ALL_CLICKS;
        }

        public Image getIcon()
        {
            return icon;
        }

        public Image getSelectedIcon()
        {
            return selectedIcon;
        }

        public String getToolTipText()
        {
            return IPTranslate.text("createImplicitObjectTool.tipText");
        }

        public void mousePressed(WidgetMouseEvent e, ViewerCanvas view)
        {
            clickPoint = e.getPoint();
            shiftDown = e.isShiftDown();
            ((SceneViewer) view).beginDraggingBox(clickPoint, shiftDown);
        }

        public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
        {
            Scene theScene = ((LayoutWindow) theWindow).getScene();
            Camera cam = view.getCamera();
            Point dragPoint = e.getPoint();
            Vec3 v1, v2, v3, orig, xdir, ydir, zdir;
            double xsize, ysize, zsize;
            int i;

            if (shiftDown)
            {
                if (Math.abs(dragPoint.x-clickPoint.x) > Math.abs(dragPoint.y-clickPoint.y))
                {
                    if (dragPoint.y < clickPoint.y)
                        dragPoint.y = clickPoint.y - Math.abs(dragPoint.x-clickPoint.x);
                    else
                        dragPoint.y = clickPoint.y + Math.abs(dragPoint.x-clickPoint.x);
                }
                else
                {
                    if (dragPoint.x < clickPoint.x)
                        dragPoint.x = clickPoint.x - Math.abs(dragPoint.y-clickPoint.y);
                    else
                        dragPoint.x = clickPoint.x + Math.abs(dragPoint.y-clickPoint.y);
                }
            }
            if (dragPoint.x == clickPoint.x || dragPoint.y == clickPoint.y)
            {
                ((SceneViewer) view).repaint();
                return;
            }
            v1 = cam.convertScreenToWorld(clickPoint, ModellingApp.DIST_TO_SCREEN);
            v2 = cam.convertScreenToWorld(new Point(dragPoint.x, clickPoint.y), ModellingApp.DIST_TO_SCREEN);
            v3 = cam.convertScreenToWorld(dragPoint, ModellingApp.DIST_TO_SCREEN);
            orig = v1.plus(v3).times(0.5);
            if (dragPoint.x < clickPoint.x)
                xdir = v1.minus(v2);
            else
                xdir = v2.minus(v1);
            if (dragPoint.y < clickPoint.y)
                ydir = v3.minus(v2);
            else
                ydir = v2.minus(v3);
            xsize = xdir.length();
            ysize = ydir.length();
            xdir = xdir.times(1.0/xsize);
            ydir = ydir.times(1.0/ysize);
            zdir = xdir.cross(ydir);
            zsize = Math.min(xsize, ysize);

            Object3D obj = new ImplicitExpression( xsize, ysize, zsize);
            ObjectInfo info = new ObjectInfo(obj, new CoordinateSystem(orig, zdir, ydir), "ImplicitObject "+(counter++));
            info.addTrack(new PositionTrack(info), 0);
            info.addTrack(new RotationTrack(info), 1);
            UndoRecord undo = new UndoRecord(theWindow, false);
            undo.addCommandAtBeginning(UndoRecord.SET_SCENE_SELECTION, new Object [] {theScene.getSelection()});
            ((LayoutWindow) theWindow).addObject(info, undo);
            theWindow.setUndoRecord(undo);
            ((LayoutWindow) theWindow).setSelection(theScene.getNumObjects()-1);
            theWindow.updateImage();
        }
}
