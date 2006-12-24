package artofillusion.polymesh;

import java.awt.Image;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.ImageIcon;

import artofillusion.MeshEditorWindow;
import artofillusion.MeshViewer;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import buoy.widget.BComboBox;
import buoy.widget.Widget;

/** AdvancedExtrudeTool is the stool used to extrude selection.
 * In addition, it can scale/rotate the selection (e.g. extruded faces.*/

public class AdvancedBevelExtrudeTool extends AdvancedEditingTool
{
    private Vec3 baseVertPos[];
    private UndoRecord undo;
    private static Image icon, selectedIcon;
    private HashMap mouseDragManipHashMap;
    private boolean selected[], separateFaces;
    private PolyMesh origMesh;
    private short NO_EXTRUDE = 0;
    private short EXTRUDE_FACES = 1;
    private short EXTRUDE_FACE_GROUPS = 2;
    private int mode;
    private static ImageIcon bevelExtrudeFacesIcon, bevelExtrudeEdgesIcon, bevelExtrudeVerticesIcon;

    public AdvancedBevelExtrudeTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr, controller);
        if ( icon == null )
            icon = loadImage( "bevel.gif" );
        if ( selectedIcon == null )
            selectedIcon = loadImage( "selected/bevel.gif" );
        if (AdvancedBevelExtrudeTool.bevelExtrudeFacesIcon == null)
        {
            AdvancedBevelExtrudeTool.bevelExtrudeFacesIcon = new ImageIcon( getClass().getResource( "/artofillusion/polymesh/Icons/bevelextrudefaces.gif" ) );
            AdvancedBevelExtrudeTool.bevelExtrudeEdgesIcon = new ImageIcon( getClass().getResource( "/artofillusion/polymesh/Icons/bevelextrudeedges.gif" ) );
            AdvancedBevelExtrudeTool.bevelExtrudeVerticesIcon = new ImageIcon( getClass().getResource( "/artofillusion/polymesh/Icons/bevelextrudevertices.gif" ) );
        }
       mouseDragManipHashMap = new HashMap();
    }

    public void activateManipulators(ViewerCanvas view)
    {
        if (! mouseDragManipHashMap.containsKey(view))
        {
            PolymeshValueWidget valueWidget = null;
            Manipulator mouseDragManip = new MouseDragManipulator(this, view, AdvancedBevelExtrudeTool.bevelExtrudeFacesIcon);
            mouseDragManip.addEventLink(Manipulator.ManipulatorPrepareChangingEvent.class, this, "doManipulatorPrepareShapingMesh");
            mouseDragManip.addEventLink(Manipulator.ManipulatorCompletedEvent.class, this, "doManipulatorShapedMesh");
            mouseDragManip.addEventLink(Manipulator.ManipulatorAbortChangingEvent.class, this, "doAbortChangingMesh");
            mouseDragManip.addEventLink(MouseDragManipulator.ManipulatorMouseDragEvent.class, this, "doManipulatorMouseDragEvent");
            mouseDragManip.setActive(true);
            ((PolyMeshViewer)view).setManipulator(mouseDragManip);
            mouseDragManipHashMap.put(view, mouseDragManip);
            selectionModeChanged(controller.getSelectionMode());
        }
        else
        {
            ((PolyMeshViewer)view).addManipulator((Manipulator)mouseDragManipHashMap.get(view));
        }
    }

    public void activate()
    {
        super.activate();
        selectionModeChanged(controller.getSelectionMode());
        theWindow.setHelpText(PMTranslate.text("advancedBevelExtrudeTool.helpText"));
    }

    public void deactivate()
    {
        Iterator iter = mouseDragManipHashMap.keySet().iterator();
        PolyMeshViewer view;
        while (iter.hasNext())
        {
            view = (PolyMeshViewer)iter.next();
            view.removeManipulator((Manipulator)mouseDragManipHashMap.get(view));
        }
    }

    public int whichClicks()
    {
        return ALL_CLICKS;
    }

    public Image getIcon()
    {
        return AdvancedBevelExtrudeTool.icon;
    }

    public Image getSelectedIcon()
    {
        return AdvancedBevelExtrudeTool.selectedIcon;
    }

    public String getToolTipText()
    {
        return PMTranslate.text("advancedBevelExtrudeTool.tipText");
    }

    private void doManipulatorPrepareShapingMesh(Manipulator.ManipulatorEvent e)
    {
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        baseVertPos = mesh.getVertexPositions();
        origMesh = (PolyMesh) mesh.duplicate();
        selected = controller.getSelection();
        int selectMode = controller.getSelectionMode();
        if ( selectMode == PolyMeshEditorWindow.FACE_MODE )
            mode = ( separateFaces ? EXTRUDE_FACE_GROUPS : EXTRUDE_FACES );
        else
            mode = NO_EXTRUDE;
    }

    private void doAbortChangingMesh()
    {
        if (origMesh != null)
            controller.setMesh(origMesh);
        origMesh = null;
        baseVertPos = null;
        theWindow.setHelpText(PMTranslate.text("advancedBevelExtrudeTool.helpText"));
        controller.objectChanged();
        theWindow.updateImage();
    }

    private void doManipulatorShapedMesh(Manipulator.ManipulatorEvent e)
    {
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        undo = new UndoRecord(theWindow, false, UndoRecord.COPY_OBJECT, new Object [] {mesh, origMesh});
        theWindow.setUndoRecord(undo);
        baseVertPos = null;
        origMesh = null;
        theWindow.setHelpText(PMTranslate.text("advancedBevelExtrudeTool.helpText"));
        theWindow.updateImage();
    }

    private void doManipulatorMouseDragEvent(MouseDragManipulator.ManipulatorMouseDragEvent e)
    {
        MeshViewer mv = (MeshViewer) e.getView();
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        Vec2 drag = e.getDrag();
        Vec3 camZ = mv.getCamera().getCameraCoordinates().getZDirection();
        int selectMode = controller.getSelectionMode();
        boolean shiftMod =  e.isShiftDown() &&  e.isCtrlDown();
        boolean ctrlMod = ( ! e.isShiftDown() ) &&  e.isCtrlDown();

        if ( selectMode == MeshEditorWindow.FACE_MODE )
        {
            if ( e.isShiftDown() &&  !e.isCtrlDown() )
            {
                if ( Math.abs( drag.x ) > Math.abs( drag.y ) )
                    drag.y = 0.0;
                else
                    drag.x = 0.0;
            }
        }
        else
        {
            if ( e.isShiftDown() &&  !e.isCtrlDown() )
                drag.y = 0.0;
            if ( drag.x < 0.0 )
                drag.x = 0.0;
        }

        if ( selectMode == PolyMeshEditorWindow.POINT_MODE )
        {
            mesh = (PolyMesh)origMesh.duplicate();
            boolean[] sel = mesh.bevelVertices( selected, drag.y );
            theWindow.setHelpText( PMTranslate.text( "advancedBevelExtrudeTool.pointEdgeDragText", new Double( drag.y ) ) );
            for ( int i = 0; i < selected.length; ++i )
                sel[i] = selected[i];
            controller.setMesh( mesh );
            controller.setSelection( sel );
        }
        else if ( selectMode == PolyMeshEditorWindow.EDGE_MODE )
        {
            mesh = (PolyMesh)origMesh.duplicate();
            boolean[] sel = mesh.bevelEdges( selected, drag.y );
            theWindow.setHelpText( PMTranslate.text( "advancedBevelExtrudeTool.pointEdgeDragText", new Double( drag.y ) ) );
            for ( int i = 0; i < selected.length; ++i )
                sel[i] = selected[i];
            controller.setMesh( mesh );
            controller.setSelection( sel );
        }
        else
        {
            mesh = (PolyMesh)origMesh.duplicate();
            if ( mode == EXTRUDE_FACES )
                mesh.extrudeRegion( selected, drag.y, (Vec3) null, Math.abs( 1.0 - drag.x ), camZ, ctrlMod, shiftMod  );
            else
                mesh.extrudeFaces( selected, drag.y, (Vec3) null, Math.abs( 1.0 - drag.x ), camZ, ctrlMod, shiftMod  );
            boolean[] sel = new boolean[mesh.getFaces().length];
            for ( int i = 0; i < selected.length; ++i )
                sel[i] = selected[i];
            theWindow.setHelpText( PMTranslate.text( "advancedBevelExtrudeTool.faceDragText", new Double( 1.0 - drag.x ), new Double( drag.y ) ) );
            controller.setMesh( mesh );
            controller.setSelection( sel );
        }
        controller.objectChanged();
        theWindow.updateImage();
    }

    public void selectionModeChanged(int selectionMode)
    {
        ImageIcon image = null;
        switch (selectionMode)
        {
            case MeshEditorWindow.POINT_MODE:
                 image = AdvancedBevelExtrudeTool.bevelExtrudeVerticesIcon;
                break;
            case MeshEditorWindow.EDGE_MODE:
                 image = AdvancedBevelExtrudeTool.bevelExtrudeEdgesIcon;
                break;
            case MeshEditorWindow.FACE_MODE:
                 image = AdvancedBevelExtrudeTool.bevelExtrudeFacesIcon;
                break;
        }
        Iterator iter = mouseDragManipHashMap.keySet().iterator();
        PolyMeshViewer view;
        while (iter.hasNext())
        {
            view = (PolyMeshViewer)iter.next();
            ((MouseDragManipulator)mouseDragManipHashMap.get(view)).setImage(image);
        }
    }

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