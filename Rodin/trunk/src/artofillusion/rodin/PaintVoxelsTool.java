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

public class PaintVoxelsTool extends EditingTool
{
  private final boolean negative;
  private Vec3 lastPos;
  private VoxelTracer tracer;
  private UndoRecord undo;

  public PaintVoxelsTool(VoxelObjectEditorWindow win, boolean negative)
  {
    super(win);
    this.negative = negative;
    initButton("movePoints");
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
    lastPos = findClickLocation(e.getPoint(), mv, mv.getWindow().getVoxelTracer());
    if (lastPos == null)
      return;
    ObjectInfo info = mv.getWindow().getObject();
    VoxelObject obj = (VoxelObject) info.getObject();
    VoxelObject copy = (VoxelObject) obj.duplicate();
    undo = new UndoRecord(theWindow, false, UndoRecord.COPY_OBJECT, new Object [] {obj, copy});
    tracer = new VoxelTracer(copy);
    int range[] = paintPoint(lastPos, obj);
    VoxelObjectEditorWindow win = (VoxelObjectEditorWindow) theWindow;
    win.objectChanged();
    VoxelOctree voxels = obj.getVoxels();
    win.voxelsChanged(Math.max(0, range[0]-1), Math.min(voxels.getWidth()-1, range[1]+1),
        Math.max(0, range[2]-1), Math.min(voxels.getWidth()-1, range[3]+1), Math.max(0, range[4]-1),
        Math.min(voxels.getWidth()-1, range[5]+1));
    theWindow.updateImage();
  }

  @Override
  public void mouseDragged(WidgetMouseEvent e, ViewerCanvas view)
  {
    if (lastPos == null)
      return;
    VoxelObjectViewer mv = (VoxelObjectViewer) view;
    ObjectInfo info = mv.getWindow().getObject();
    VoxelObject obj = (VoxelObject) info.getObject();
    Vec3 pos = findClickLocation(e.getPoint(), mv, tracer);
    if (pos == null)
    {
      view.getCamera().setObjectTransform(info.getCoords().fromLocal());
      double depth = view.getCamera().getObjectToView().times(lastPos).z;
      pos = info.getCoords().toLocal().times(view.getCamera().convertScreenToWorld(e.getPoint(), depth));
    }
    int range[] = paintLine(lastPos, pos, obj);
    VoxelObjectEditorWindow win = (VoxelObjectEditorWindow) theWindow;
    win.objectChanged();
    VoxelOctree voxels = obj.getVoxels();
    win.voxelsChanged(Math.max(0, range[0]-1), Math.min(voxels.getWidth()-1, range[1]+1),
        Math.max(0, range[2]-1), Math.min(voxels.getWidth()-1, range[3]+1), Math.max(0, range[4]-1),
        Math.min(voxels.getWidth()-1, range[5]+1));
    lastPos = pos;
    theWindow.updateImage();
  }

  @Override
  public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
  {
    tracer = null;
    if (undo != null)
      theWindow.setUndoRecord(undo);
    theWindow.updateImage();
    theWindow.setHelpText(Translate.text("reshapeMeshTool.helpText"));
    undo = null;
  }

  /**
   * Given a screen location, find the point in the object it is on top of.
   */

  private Vec3 findClickLocation(Point pos, VoxelObjectViewer view, VoxelTracer tracer)
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
    return origin.plus(direction.times(dist));
  }

  private int[] paintPoint(Vec3 pos, VoxelObject obj)
  {
    VoxelOctree voxels = obj.getVoxels();
    int width = voxels.getWidth();
    double scale = (width-1)/obj.getScale();
    pos = new Vec3(pos.x*scale+0.5*(width-1), pos.y*scale+0.5*(width-1), pos.z*scale+0.5*(width-1));
    double radius = ((VoxelObjectEditorWindow) theWindow).getRadius();
    int minx = (int) Math.floor(pos.x-radius);
    int maxx = (int) Math.ceil(pos.x+radius);
    int miny = (int) Math.floor(pos.y-radius);
    int maxy = (int) Math.ceil(pos.y+radius);
    int minz = (int) Math.floor(pos.z-radius);
    int maxz = (int) Math.ceil(pos.z+radius);
    while (minx < 0 || maxx >= width || miny < 0 || maxy >= width || minz < 0 || maxz >= width)
    {
      voxels.growGrid();
      minx += width/2;
      maxx += width/2;
      miny += width/2;
      maxy += width/2;
      minz += width/2;
      maxz += width/2;
      pos.x += width/2;
      pos.y += width/2;
      pos.z += width/2;
      width *= 2;
      obj.setScale(obj.getScale()*2);
    }
    for (int x = minx; x <= maxx; x++)
      for (int y = miny; y <= maxy; y++)
        for (int z = minz; z <= maxz; z++)
        {
          double dx = pos.x-x;
          double dy = pos.y-y;
          double dz = pos.z-z;
          double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
          if (dist >= radius+1.0)
            continue;
          byte oldValue = voxels.getValue(x, y, z);
          byte newValue;
          if (negative)
          {
            if (dist <= radius-1.0)
              newValue = Byte.MIN_VALUE;
            else
              newValue = (byte) Math.min(oldValue, Byte.MAX_VALUE*(dist-radius));
          }
          else
          {
            if (dist <= radius-1.0)
              newValue = Byte.MAX_VALUE;
            else
              newValue = (byte) Math.max(oldValue, Byte.MAX_VALUE*(radius-dist));
          }
          if (newValue != oldValue)
            voxels.setValue(x, y, z, newValue);
        }
    return new int[] {minx, maxx, miny, maxy, minz, maxz};
  }

  private int[] paintLine(Vec3 start, Vec3 end, VoxelObject obj)
  {
    int range[] = paintPoint(end, obj);
    VoxelOctree voxels = obj.getVoxels();
    int width = voxels.getWidth();
    double scale = (width-1)/obj.getScale();
    int numPoints = (int) Math.ceil(scale*Math.max(Math.max(Math.abs(end.x-start.x), Math.abs(end.y-start.y)), Math.abs(end.z-start.z)));
    Vec3 offset = end.minus(start).times(1.0/numPoints);
    Vec3 pos = new Vec3();

    // Walk along the line, painting a sphere at each point.

    for (int i = 0; i < numPoints; i++)
    {
      pos.set(start.x+offset.x*i, start.y+offset.y*i, start.z+offset.z*i);
      int pointRange[] = paintPoint(pos, obj);
      range[0] = Math.min(range[0], pointRange[0]);
      range[1] = Math.max(range[1], pointRange[1]);
      range[2] = Math.min(range[2], pointRange[2]);
      range[3] = Math.max(range[3], pointRange[3]);
      range[4] = Math.min(range[4], pointRange[4]);
      range[5] = Math.max(range[5], pointRange[5]);
    }
    return range;
  }
}