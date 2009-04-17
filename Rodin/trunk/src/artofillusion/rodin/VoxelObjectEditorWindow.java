/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.*;
import artofillusion.util.*;
import artofillusion.object.*;
import artofillusion.ui.*;
import buoy.widget.*;

import java.awt.*;

public class VoxelObjectEditorWindow extends ObjectEditorWindow
{
  protected BMenu editMenu, viewMenu;
  protected BMenuItem undoItem, redoItem, templateItem, axesItem, splitViewItem;
  private VoxelObject oldObject;
  private VoxelTracer tracer;
  private ThreadManager threads;
  private ValueSlider radiusSlider;
  private Runnable onClose;
  private boolean showBrush;

  public VoxelObjectEditorWindow(EditingWindow parent, String title, ObjectInfo obj, Runnable onClose)
  {
    super(parent, title, obj);
    oldObject = (VoxelObject) obj.getObject();
    tracer = new VoxelTracer((VoxelObject) getObject().getObject());
    threads = new ThreadManager();
    this.onClose = onClose;
    initialize();
    FormContainer content = new FormContainer(new double [] {0, 1}, new double [] {1, 0, 0, 0});
    setContent(content);
    content.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.BOTH, null, null));
    RowContainer controls = new RowContainer();
    controls.add(Translate.label("Radius"));
    controls.add(radiusSlider = new ValueSlider(1.0, 20.0, 38, 5.0));
    content.add(controls, 0, 1, 2, 1);
    content.add(helpText = new BLabel(), 0, 2, 2, 1);
    content.add(viewsContainer, 1, 0);
    RowContainer buttons = new RowContainer();
    buttons.add(Translate.button("ok", this, "doOk"));
    buttons.add(Translate.button("cancel", this, "doCancel"));
    content.add(buttons, 0, 3, 2, 1, new LayoutInfo());
    content.add(tools = new ToolPalette(1, 5), 0, 0);
    EditingTool metaTool, altTool;
    tools.addTool(defaultTool = new PaintVoxelsTool(this, false));
    tools.addTool(new PaintVoxelsTool(this, true));
    tools.addTool(new SmudgeVoxelsTool(this));
    tools.addTool(metaTool = new MoveViewTool(this));
    tools.addTool(altTool = new RotateViewTool(this));
    tools.setDefaultTool(defaultTool);
    tools.selectTool(defaultTool);
    for (int i = 0; i < theView.length; i++)
    {
      VoxelObjectViewer view = (VoxelObjectViewer) theView[i];
      view.setMetaTool(metaTool);
      view.setAltTool(altTool);
    }
    createEditMenu();
    createViewMenu();
    recursivelyAddListeners(this);
    UIUtilities.applyDefaultFont(content);
    UIUtilities.applyDefaultBackground(content);
    Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    Dimension windowDim = new Dimension((screenBounds.width*3)/4, (screenBounds.height*3)/4);
    setBounds(new Rectangle((screenBounds.width-windowDim.width)/2, (screenBounds.height-windowDim.height)/2, windowDim.width, windowDim.height));
    tools.requestFocus();
    updateMenus();
  }

  void createEditMenu()
  {
    editMenu = Translate.menu("edit");
    menubar.add(editMenu);
    editMenu.add(undoItem = Translate.menuItem("undo", this, "undoCommand"));
    editMenu.add(redoItem = Translate.menuItem("redo", this, "redoCommand"));
  }

  protected void createViewMenu()
  {
    viewMenu = Translate.menu("view");
    menubar.add(viewMenu);
    viewMenu.add(splitViewItem = Translate.menuItem(numViewsShown == 1 ? "fourViews" : "oneView", this, "toggleViewsCommand"));
    viewMenu.add(Translate.menuItem("grid", this, "setGridCommand"));
    viewMenu.add(axesItem = Translate.menuItem(theView[currentView].getShowAxes() ? "hideCoordinateAxes" : "showCoordinateAxes", this, "showAxesCommand"));
    viewMenu.add(templateItem = Translate.menuItem("showTemplate", this, "showTemplateCommand"));
    viewMenu.add(Translate.menuItem("setTemplate", this, "setTemplateCommand"));
    if (ArtOfIllusion.getPreferences().getObjectPreviewRenderer() != null)
    {
      viewMenu.addSeparator();
      viewMenu.add(Translate.menuItem("renderPreview", this, "renderPreviewCommand"));
    }
  }

  protected ViewerCanvas createViewerCanvas(int index, RowContainer controls)
  {
    return new VoxelObjectViewer(this, controls);
  }

  protected void doOk()
  {
    VoxelObject theObject = (VoxelObject) objInfo.getObject();
    oldObject.copyObject(theObject);
    oldObject = null;
    parentWindow.getScene().objectModified(oldObject);
    dispose();
    if (onClose != null)
      onClose.run();
    parentWindow.updateImage();
    parentWindow.updateMenus();
  }

  protected void doCancel()
  {
    oldObject = null;
    dispose();
  }

  @Override
  public void dispose()
  {
    super.dispose();
    threads.finish();
  }

  public void updateMenus()
  {
    VoxelObjectViewer view = (VoxelObjectViewer) theView[currentView];
    undoItem.setEnabled(undoStack.canUndo());
    redoItem.setEnabled(undoStack.canRedo());
    templateItem.setEnabled(view.getTemplateImage() != null);
    templateItem.setText(view.getTemplateShown() ? Translate.text("menu.hideTemplate") : Translate.text("menu.showTemplate"));
    splitViewItem.setText(numViewsShown == 1 ? Translate.text("menu.fourViews") : Translate.text("menu.oneView"));
    axesItem.setText(Translate.text(view.getShowAxes() ? "menu.hideCoordinateAxes" : "menu.showCoordinateAxes"));
  }

  public Scene getScene()
  {
    return parentWindow.getScene();
  }

  /** Get the object being edited in this window. */

  public ObjectInfo getObject()
  {
    return objInfo;
  }

  /** Get a VoxelTracer for tracing rays through the object. */

  public VoxelTracer getVoxelTracer()
  {
    return tracer;
  }

  /** Get a ThreadManager which the VoxelObjectViewers in the window can use for rendering. */

  public ThreadManager getThreadManager()
  {
    return threads;
  }

  /** This should be called whenever the object has changed. */

  public void objectChanged()
  {
    getObject().clearCachedMeshes();
    ((VoxelObject) getObject().getObject()).clearCachedMeshes();
  }

  /** This should be called whenever the VoxelObject has changed. */

  public void voxelsChanged()
  {
    tracer.updateFlags();
    for (int i = 0; i < theView.length; i++)
      ((VoxelObjectViewer) theView[i]).voxelsChanged();
  }

  /** This should be called whenever a block of values in the VoxelObject have changed. */

  public void voxelsChanged(int fromx, int tox, int fromy, int toy, int fromz, int toz)
  {
    tracer.updateFlags(fromx, tox, fromy, toy, fromz, toz);
    for (int i = 0; i < theView.length; i++)
      ((VoxelObjectViewer) theView[i]).voxelsChanged(fromx, tox, fromy, toy, fromz, toz);
  }

  /** Get the radius to use for drawing. */

  public double getRadius()
  {
    return radiusSlider.getValue();
  }

  /** Get whether a circle should be shown at the cursor location indicating the bounds for drawing. */

  public boolean getShowBrush()
  {
    return showBrush;
  }

  /** Set whether a circle should be shown at the cursor location indicating the bounds for drawing. */

  public void setShowBrush(boolean show)
  {
    showBrush = show;
  }

  /** Render a preview of the object. */

  public void renderPreviewCommand()
  {
    ((VoxelObjectViewer) theView[currentView]).previewObject();
  }

  @Override
  public void undoCommand()
  {
    super.undoCommand();
    objectChanged();
    voxelsChanged();
    updateImage();
  }

  @Override
  public void redoCommand()
  {
    super.redoCommand();
    objectChanged();
    voxelsChanged();
    updateImage();
  }
}
