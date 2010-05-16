/* Copyright (C) 2009-2010 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.ui.*;
import artofillusion.math.*;
import artofillusion.*;
import artofillusion.object.*;

import buoy.event.*;

import java.awt.*;

public abstract class EditVoxelsTool extends EditingTool
{
  protected int width, offset, padding;
  protected float values[], weight[];
  private UndoRecord undo;

  public EditVoxelsTool(EditingWindow win)
  {
    super(win);
  }

  @Override
  public void activate()
  {
    super.activate();
    theWindow.setHelpText(Translate.text("reshapeMeshTool.helpText"));
    ((VoxelObjectEditorWindow) theWindow).setShowBrush(true);
  }

  @Override
  public void deactivate()
  {
    super.deactivate();
    ((VoxelObjectEditorWindow) theWindow).setShowBrush(false);
  }

  @Override
  public int whichClicks()
  {
    return ALL_CLICKS;
  }

  @Override
  public String getToolTipText()
  {
    return Translate.text("reshapeMeshTool.tipText");
  }

  @Override
  public void mousePressed(WidgetMouseEvent e, ViewerCanvas view)
  {
    VoxelObjectViewer mv = (VoxelObjectViewer) view;
    ObjectInfo info = mv.getWindow().getObject();
    VoxelObject obj = (VoxelObject) info.getObject();
    VoxelObject copy = (VoxelObject) obj.duplicate();
    undo = new UndoRecord(theWindow, false, UndoRecord.COPY_OBJECT, new Object [] {obj, copy});

    // Construct workspace arrays.

    double radius = mv.getWindow().getRadius();
    offset = (int) Math.round(radius);
    computePadding(radius);
    width = (int) Math.floor(2*radius)+2*padding;
    values = new float[width*width*width];
    weight = new float[width*width*width];

    // Calculate the weights for each voxel.

    for (int i = 0; i < width; i++)
      for (int j = 0; j < width; j++)
        for (int k = 0; k < width; k++)
        {
          int dx = i-offset;
          int dy = j-offset;
          int dz = k-offset;
          double dist = Math.sqrt(dx*dx+dy*dy+dz*dz);
          int index = i*width*width+j*width+k;
          if (dist > radius)
            weight[index] = 0.0f;
          else
          {
            double d = dist/radius;
            weight[index] = (float) (1.0-d*d);
          }
        }
    apply(e, (VoxelObjectViewer) view);
  }

  @Override
  public void mouseDragged(WidgetMouseEvent e, ViewerCanvas view)
  {
    apply(e, (VoxelObjectViewer) view);
  }

  private void apply(WidgetMouseEvent e, VoxelObjectViewer view)
  {
    ObjectInfo info = view.getWindow().getObject();
    VoxelObject obj = (VoxelObject) info.getObject();

    // Find the location.

    Vec3 dir = new Vec3();
    Vec3 pos = findClickLocation(e.getPoint(), view, view.getWindow().getVoxelTracer(), dir);
    if (pos == null)
      return;
    pos = convertPointToVoxel(pos, obj);
    expandVoxelsIfNecessary(obj, pos);
    int x = (int) Math.round(pos.x);
    int y = (int) Math.round(pos.y);
    int z = (int) Math.round(pos.z);

    // Compute new values for affected voxels.

    VoxelOctree voxels = obj.getVoxels();
    int xbase = x-offset;
    int ybase = y-offset;
    int zbase = z-offset;
    findVoxelValues(voxels, xbase, ybase, zbase, values);
    for (int i = padding; i < width-padding; i++)
        for (int j = padding; j < width-padding; j++)
            for (int k = padding; k < width-padding; k++)
            {
              int index = i*width*width+j*width+k;
              if (weight[index] == 0.0f)
                continue;
              int xindex = xbase+i;
              int yindex = ybase+j;
              int zindex = zbase+k;
              float newValue = weight[index]*computeNewValue(i, j, k, dir)+(1.0f-weight[index])*voxels.getValue(xindex, yindex, zindex);
              if (newValue < Byte.MIN_VALUE)
                newValue = Byte.MIN_VALUE;
              if (newValue > Byte.MAX_VALUE)
                newValue = Byte.MAX_VALUE;
              voxels.setValue(xindex, yindex, zindex, (byte) newValue);
            }

    // Update the view.

    VoxelObjectEditorWindow win = (VoxelObjectEditorWindow) theWindow;
    win.objectChanged();
    win.voxelsChanged(x-offset, x-offset+width, y-offset, y-offset+width, z-offset, z-offset+width);
    theWindow.updateImage();
  }

  @Override
  public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
  {
    if (undo != null)
    {
      theWindow.setUndoRecord(undo);
      VoxelObjectViewer mv = (VoxelObjectViewer) view;
      ObjectInfo info = mv.getWindow().getObject();
      VoxelObject obj = (VoxelObject) info.getObject();
      obj.optimizeValues();
    }
    theWindow.updateImage();
    theWindow.setHelpText(Translate.text("reshapeMeshTool.helpText"));
    undo = null;
    values = null;
    weight = null;
  }

  protected abstract void computePadding(double radius);

  protected abstract float computeNewValue(int x, int y, int z, Vec3 dir);

  /**
   * Record the current values for a block of voxels into an array.
   */

  private void findVoxelValues(VoxelOctree voxels, int xbase, int ybase, int zbase, float values[])
  {
    for (int i = 0; i < width; i++)
      for (int j = 0; j < width; j++)
        for (int k = 0; k < width; k++)
        {
          int index = i*width*width+j*width+k;
          int x = i+xbase;
          int y = j+ybase;
          int z = k+zbase;
          values[index] = voxels.getValue(x, y, z);
        }
  }

  /**
   * Given a screen location, find the point in the object it is on top of.
   */

  private Vec3 findClickLocation(Point pos, VoxelObjectViewer view, VoxelTracer tracer, Vec3 dir)
  {
    Camera camera = view.getCamera();
    Vec3 origin, direction;
    if (camera.isPerspective())
    {
      origin = camera.getCameraCoordinates().getOrigin();
      direction = camera.convertScreenToWorld(pos, 1.0, false).minus(origin);
      direction.normalize();
    }
    else
    {
      origin = camera.convertScreenToWorld(pos, 0.0, false);
      direction = camera.getCameraCoordinates().getZDirection();
    }
    ObjectInfo info = view.getWindow().getObject();
    info.getCoords().toLocal().transform(origin);
    info.getCoords().toLocal().transformDirection(direction);
    double dist = tracer.findRayIntersection(origin, direction, null);
    if (dist == 0.0)
      return null;
    dir.set(direction);
    return origin.plus(direction.times(dist));
  }

  /**
   * Given a point in object coordinates, convert it to the correspond voxel coordinates.
   */

  private Vec3 convertPointToVoxel(Vec3 pos, VoxelObject obj)
  {
    int width = obj.getVoxels().getWidth();
    double scale = (width-1)/obj.getScale();
    return new Vec3(pos.x*scale+0.5*(width-1), pos.y*scale+0.5*(width-1), pos.z*scale+0.5*(width-1));
  }

  /**
   * Ensure that the voxel grid is large enough to handle a click at a certain point.
   */

  private void expandVoxelsIfNecessary(VoxelObject obj, Vec3 pos)
  {
    int minx = (int) Math.round(pos.x)-offset;
    int miny = (int) Math.round(pos.y)-offset;
    int minz = (int) Math.round(pos.z)-offset;
    int maxx = minx+width;
    int maxy = miny+width;
    int maxz = minz+width;
    VoxelOctree voxels = obj.getVoxels();
    int gridWidth = voxels.getWidth();
    while (minx < 0 || maxx >= gridWidth || miny < 0 || maxy >= gridWidth || minz < 0 || maxz >= gridWidth)
    {
      voxels.growGrid();
      minx += gridWidth/2;
      maxx += gridWidth/2;
      miny += gridWidth/2;
      maxy += gridWidth/2;
      minz += gridWidth/2;
      maxz += gridWidth/2;
      pos.x += gridWidth/2;
      pos.y += gridWidth/2;
      pos.z += gridWidth/2;
      gridWidth *= 2;
      obj.setScale(obj.getScale()*2);
    }
  }
}