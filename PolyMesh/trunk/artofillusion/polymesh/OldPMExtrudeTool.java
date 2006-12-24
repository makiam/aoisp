/*
 *  Copyright (C) 2005 by Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.awt.Image;
import java.awt.Point;

import javax.swing.ImageIcon;

import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.math.Vec3;
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import buoy.event.WidgetMouseEvent;
import buoy.widget.BComboBox;
import buoy.widget.Widget;

/**
 *  ExtrudeTool is an EditingTool used for extruding PolyMesh objects. written
 *  after bevel/extrude editing tool written by P. Eastman
 *
 *@author     Francois Guillet
 *@created    3 février 2005
 */

public class OldPMExtrudeTool extends EditingTool
{
    private boolean selected[], noSelection, separateFaces;
    private static Image icon, selectedIcon;
    private PolyMesh origMesh;
    private Point clickPoint;
    private double width, height;
    private MeshEditController controller;
    private short NO_EXTRUDE = 0;
    private short EXTRUDE_FACES = 1;
    private short EXTRUDE_FACE_GROUPS = 2;
    private int mode;
    private Vec3 clickPos;


    /**
     *  Constructor for the BevelExtrudeTool object
     *
     *@param  fr          Description of the Parameter
     *@param  controller  Description of the Parameter
     */
    public OldPMExtrudeTool( EditingWindow fr, MeshEditController controller )
    {
        super( fr );
        this.controller = controller;
        if ( icon == null )
            icon = new ImageIcon( getClass().getResource( "/artofillusion/polymesh/Icons/extrude.gif" ) ).getImage();
        if ( selectedIcon == null )
            selectedIcon = new ImageIcon( getClass().getResource( "/artofillusion/polymesh/Icons/selected/extrude.gif" ) ).getImage();
    }


    /**
     *  Record the current selection.
     */

    private void recordSelection()
    {
        selected = controller.getSelection();
        noSelection = false;
        for ( int i = 0; i < selected.length; i++ )
            if ( selected[i] )
                return;
        noSelection = true;
    }


    /**
     *  Description of the Method
     */
    public void activate()
    {
        super.activate();
        recordSelection();
        if ( noSelection )
            theWindow.setHelpText( PMTranslate.text( "extrudeTool.errorText" ) );
        else
            theWindow.setHelpText( PMTranslate.text( "extrudeTool.helpText" ) );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public int whichClicks()
    {
        return ALL_CLICKS;
    }


    /**
     *  Gets the icon attribute of the BevelExtrudeTool object
     *
     *@return    The icon value
     */
    public Image getIcon()
    {
        return icon;
    }


    /**
     *  Gets the selectedIcon attribute of the BevelExtrudeTool object
     *
     *@return    The selectedIcon value
     */
    public Image getSelectedIcon()
    {
        return selectedIcon;
    }


    /**
     *  Gets the toolTipText attribute of the BevelExtrudeTool object
     *
     *@return    The toolTipText value
     */
    public String getToolTipText()
    {
        return PMTranslate.text( "extrudeTool.tipText" );
    }


    /**
     *  Description of the Method
     *
     *@param  e     Description of the Parameter
     *@param  view  Description of the Parameter
     */
    public void mousePressed( WidgetMouseEvent e, ViewerCanvas view )
    {
        recordSelection();
        if ( noSelection )
            return;
        PolyMeshViewer mv = (PolyMeshViewer) view;
        selected = controller.getSelection();
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        origMesh = (PolyMesh) mesh.duplicate();
        int selectMode = controller.getSelectionMode();
        if ( selectMode == PolyMeshEditorWindow.FACE_MODE )
            mode = ( separateFaces ? EXTRUDE_FACE_GROUPS : EXTRUDE_FACES );
        else
            mode = NO_EXTRUDE;
        clickPoint = e.getPoint();
        clickPos = new Vec3( 0, 0, 0 );
    }


    /**
     *  Description of the Method
     *
     *@param  e     Description of the Parameter
     *@param  view  Description of the Parameter
     */
    public void mouseDragged( WidgetMouseEvent e, ViewerCanvas view )
    {
        if ( noSelection || mode == NO_EXTRUDE )
            return;
        if ( mode == NO_EXTRUDE )
            return;
        PolyMeshViewer mv = (PolyMeshViewer) view;
        PolyMesh mesh = (PolyMesh) origMesh.duplicate();
        Point dragPoint = e.getPoint();

        int dx = dragPoint.x - clickPoint.x;
        int dy = dragPoint.y - clickPoint.y;
        if ( e.isShiftDown() )
        {
            if ( Math.abs( dx ) > Math.abs( dy ) )
                dy = 0;
            else
                dx = 0;
        }
        Vec3 drag;
        if ( e.isControlDown() )
            drag = view.getCamera().getCameraCoordinates().getZDirection().times( -dy * 0.01 );
        else
            drag = view.getCamera().findDragVector( clickPos, dx, dy );
        // Update the mesh and redisplay.

        double value = drag.length();
        if ( drag.length() > 1e-12 )
            drag.normalize();
        if ( mode == EXTRUDE_FACES )
            mesh.extrudeRegion( selected, value, drag );
        else
            mesh.extrudeFaces( selected, value, drag );
        boolean[] sel = new boolean[mesh.getFaces().length];
        for ( int i = 0; i < selected.length; ++i )
            sel[i] = selected[i];
        controller.setMesh( mesh );
        controller.setSelection( sel );
        theWindow.setHelpText( PMTranslate.text( "extrudeTool.dragText",
                Math.round( drag.x * 1e5 ) / 1e5 + ", " + Math.round( drag.y * 1e5 ) / 1e5 + ", " + Math.round( drag.z * 1e5 ) / 1e5 ) );
    }


    /**
     *  Description of the Method
     *
     *@param  e     Description of the Parameter
     *@param  view  Description of the Parameter
     */
    public void mouseReleased( WidgetMouseEvent e, ViewerCanvas view )
    {
        if ( noSelection )
            return;
        if ( mode == NO_EXTRUDE )
            return;
        PolyMeshViewer mv = (PolyMeshViewer) view;
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        theWindow.setUndoRecord( new UndoRecord( theWindow, false, UndoRecord.COPY_OBJECT, new Object[]{mesh, origMesh} ) );
        controller.objectChanged();
        theWindow.updateImage();
        theWindow.setHelpText( PMTranslate.text( "extrudeTool.helpText" ) );
    }


    /**
     *  Description of the Method
     */
    public void iconDoubleClicked()
    {
        BComboBox c = new BComboBox( new String[]{
                Translate.text( "selectionAsWhole" ),
                Translate.text( "individualFaces" )
                } );
        c.setSelectedIndex( separateFaces ? 1 : 0 );
        ComponentsDialog dlg = new ComponentsDialog( theFrame, Translate.text( "applyExtrudeTo" ),
                new Widget[]{c}, new String[]{null} );
        if ( dlg.clickedOk() )
            separateFaces = ( c.getSelectedIndex() == 1 );
    }
}

