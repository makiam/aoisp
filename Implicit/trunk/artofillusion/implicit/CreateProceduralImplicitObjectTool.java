package artofillusion.implicit;

import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.*;
import artofillusion.animation.PositionTrack;
import artofillusion.animation.RotationTrack;
import artofillusion.object.Object3D;
import artofillusion.object.ObjectInfo;
import artofillusion.math.Vec3;
import artofillusion.math.CoordinateSystem;

import javax.swing.*;
import java.awt.*;

import buoy.event.WidgetMouseEvent;

/**
 * Editing Tool used to create an procedural implicit object.
 */
public class CreateProceduralImplicitObjectTool extends EditingTool
{
    private EditingWindow edw;
        private static Image icon, selectedIcon;
        protected static int counter = 1;
        boolean shiftDown;
        Point clickPoint;

        public CreateProceduralImplicitObjectTool(LayoutWindow layout)
        {
            super(layout);
            edw = layout;
            if ( CreateProceduralImplicitObjectTool.icon == null )
                CreateProceduralImplicitObjectTool.icon = new ImageIcon( getClass().getResource( "/artofillusion/implicit/Icons/procedural.gif" ) ).getImage();
            if ( CreateProceduralImplicitObjectTool.selectedIcon == null )
                CreateProceduralImplicitObjectTool.selectedIcon = new ImageIcon( getClass().getResource( "/artofillusion/implicit/Icons/selected/procedural.gif" ) ).getImage();
        }

        public void activate()
        {
            super.activate();
            setHelpText();
        }

        private void setHelpText()
        {
            theWindow.setHelpText(IPTranslate.text("createProceduralObjectTool.helpText" ) );

        }

        public int whichClicks()
        {
            return ALL_CLICKS;
        }

        public Image getIcon()
        {
            return CreateProceduralImplicitObjectTool.icon;
        }

        public Image getSelectedIcon()
        {
            return CreateProceduralImplicitObjectTool.selectedIcon;
        }

        public String getToolTipText()
        {
            return IPTranslate.text("createProceduralObjectTool.tipText");
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

            Object3D obj = new ProceduralImplicitObject( xsize, ysize, zsize);
            ObjectInfo info = new ObjectInfo(obj, new CoordinateSystem(orig, zdir, ydir), "ProceduralImplicitObject "+(CreateProceduralImplicitObjectTool.counter++));
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
