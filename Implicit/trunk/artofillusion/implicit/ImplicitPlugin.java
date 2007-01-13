/* Copyright (C) 2006 by Francois Guillet

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.implicit;

import artofillusion.*;
import artofillusion.animation.PositionTrack;
import artofillusion.animation.RotationTrack;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.Vec3;
import artofillusion.object.*;
import artofillusion.ui.ToolPalette;
import artofillusion.ui.Translate;

import buoy.widget.*;

/**
 * This plugin provides new types of objects that take advantage of implicit surfaces
 * rendering. These objects are ImplicitExpression and ProceduralImplicitObject.
 */
public class ImplicitPlugin
     implements Plugin
{
    /**
     *  Process messages sent to plugin by AoI (see AoI API description)
     *
     *@param  message  The message
     *@param  args     Arguments depending on the message
     */
    public void processMessage( int message, Object args[] )
    {
        if ( message == Plugin.APPLICATION_STARTING )
        {
            IPTranslate.setLocale( ModellingApp.getPreferences().getLocale() );
            //for future use
        }
        else if ( message == Plugin.SCENE_WINDOW_CREATED )
        {
            LayoutWindow layout = (LayoutWindow) args[0];
            BMenuItem menuItem = IPTranslate.menuItem( "implicitObject", new CreateImplicitObject(layout), "doCreate" );
            BMenuBar menuBar = layout.getMenuBar();
            BMenu toolsMenu = menuBar.getChild( 2 );
            int count = toolsMenu.getChildCount();
            BMenu createMenu = (BMenu) toolsMenu.getChild(count-1);
            createMenu.add( menuItem );
            ToolPalette palette = layout.getToolPalette();
            palette.addTool( 8, new CreateProceduralImplicitObjectTool( layout ));
            palette.addTool( 8, new CreateImplicitExpressionTool( layout ));
            palette.toggleDefaultTool();
            palette.toggleDefaultTool();
            layout.layoutChildren();
        }
    }

    private class CreateImplicitObject
    {
        LayoutWindow window;

        public CreateImplicitObject( LayoutWindow w )
        {
            window = w;
        }

        private void doCreate()
        {
            Scene theScene = window.getScene();
            Object3D obj = new ImplicitExpression( 1.0, 1.0, 1.0);
            String name = "ImplicitObject "+(CreateImplicitExpressionTool.counter++);
            CoordinateSystem coords = new CoordinateSystem(new Vec3(), Vec3.vz(), Vec3.vy());
            ObjectInfo info = new ObjectInfo(obj, coords, name);
            if (obj.canSetTexture())
                info.setTexture(theScene.getDefaultTexture(), theScene.getDefaultTexture().getDefaultMapping());
            Vec3 orig = coords.getOrigin();
            double angles[] = coords.getRotationAngles();
            Vec3 size = info.getBounds().getSize();
            TransformDialog dlg = new TransformDialog(window, Translate.text("objectLayoutTitle", name),
                    new double [] {orig.x, orig.y, orig.z, angles[0], angles[1], angles[2],
                            size.x, size.y, size.z}, false, false);
            if (!dlg.clickedOk())
                return;
            double values[] = dlg.getValues();
            if (!Double.isNaN(values[0]))
                orig.x = values[0];
            if (!Double.isNaN(values[1]))
                orig.y = values[1];
            if (!Double.isNaN(values[2]))
                orig.z = values[2];
            if (!Double.isNaN(values[3]))
                angles[0] = values[3];
            if (!Double.isNaN(values[4]))
                angles[1] = values[4];
            if (!Double.isNaN(values[5]))
                angles[2] = values[5];
            if (!Double.isNaN(values[6]))
                size.x = values[6];
            if (!Double.isNaN(values[7]))
                size.y = values[7];
            if (!Double.isNaN(values[8]))
                size.z = values[8];
            coords.setOrigin(orig);
            coords.setOrientation(angles[0], angles[1], angles[2]);
            obj.setSize(size.x, size.y, size.z);
            info.clearCachedMeshes();
            info.addTrack(new PositionTrack(info), 0);
            info.addTrack(new RotationTrack(info), 1);
            UndoRecord undo = new UndoRecord(window, false);
            int sel[] = theScene.getSelection();
            window.addObject(info, undo);
            undo.addCommand(UndoRecord.SET_SCENE_SELECTION, new Object [] {sel});
            window.setSelection(theScene.getNumObjects()-1);
            window.setUndoRecord(undo);
            window.updateImage();
        }
    }
}
