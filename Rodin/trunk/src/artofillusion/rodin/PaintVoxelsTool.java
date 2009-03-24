/* Copyright (C) 2009 by Peter Eastman

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

  public PaintVoxelsTool(EditingWindow win, boolean negative)
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
//    tracer = mv.getWindow().getVoxelTracer().clone();
    int range[] = paintPoint(lastPos, obj);
    VoxelObjectEditorWindow win = (VoxelObjectEditorWindow) theWindow;
    win.objectChanged();
    VoxelOctree voxels = obj.getVoxels();
    win.getVoxelTracer().updateFlags(Math.max(0, range[0]-1), Math.min(voxels.getWidth()-1, range[1]+1),
        Math.max(0, range[2]-1), Math.min(voxels.getWidth()-1, range[3]+1), Math.max(0, range[4]-1),
        Math.min(voxels.getWidth()-1, range[5]+1));
    theWindow.updateImage();
//    view.repaint();
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
    win.getVoxelTracer().updateFlags(Math.max(0, range[0]-1), Math.min(voxels.getWidth()-1, range[1]+1),
        Math.max(0, range[2]-1), Math.min(voxels.getWidth()-1, range[3]+1), Math.max(0, range[4]-1),
        Math.min(voxels.getWidth()-1, range[5]+1));
    lastPos = pos;
    theWindow.updateImage();
//    view.repaint();
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
      voxels.growGrid(0.0f);
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
          double weight;
          if (dist >= radius+1.0)
            continue;
          if (dist <= radius-1.0)
            weight = 1.0;
          else
            weight = 0.5*(radius+1.0-dist);
          float oldValue = voxels.getValue(x, y, z);
          float newValue;
          if (negative)
            newValue = (float) Math.min(oldValue, 1.0-weight);
          else
            newValue = (float) Math.max(oldValue, weight);
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
    start = new Vec3(start.x*scale+0.5*(width-1), start.y*scale+0.5*(width-1), start.z*scale+0.5*(width-1));
    end = new Vec3(end.x*scale+0.5*(width-1), end.y*scale+0.5*(width-1), end.z*scale+0.5*(width-1));

    // Find the primary axis, and the range of voxels along that axis to paint.

    int startx = (int) Math.floor(Math.min(start.x, end.x));
    int endx = (int) Math.ceil(Math.max(start.x, end.x));
    int starty = (int) Math.floor(Math.min(start.y, end.y));
    int endy = (int) Math.ceil(Math.max(start.y, end.y));
    int startz = (int) Math.floor(Math.min(start.z, end.z));
    int endz = (int) Math.ceil(Math.max(start.z, end.z));
    int axis, startIndex, endIndex;
    double xlength = Math.abs(end.x-start.x);
    double ylength = Math.abs(end.y-start.y);
    double zlength = Math.abs(end.z-start.z);
    if (xlength >= ylength && xlength >= zlength)
    {
      axis = 0;
      startIndex = startx;
      endIndex = endx;
    }
    else if (ylength >= xlength && ylength >= zlength)
    {
      axis = 1;
      startIndex = starty;
      endIndex = endy;
    }
    else
    {
      axis = 2;
      startIndex = startz;
      endIndex = endz;
    }
    Vec3 udir = end.minus(start);
    double length = udir.length();
    udir.scale(1.0/length);

    // Walk along the primary axis, painting a disk at each point.

    Vec3 pos = new Vec3();
    double radius = ((VoxelObjectEditorWindow) theWindow).getRadius();
    double r = radius*Math.sqrt(2.0);
    for (int i = startIndex; i <= endIndex; i++)
    {
      // Convert (x,y,z) to (u,v,w) where u is the primary axis and v and w are perpendicular to it.

      double fract, posv, posw;
      if (axis == 0)
      {
        fract = (i-start.x)/(end.x-start.x);
        posv = start.y + (end.y-start.y)*fract;
        posw = start.z + (end.z-start.z)*fract;
      }
      else if (axis == 1)
      {
        fract = (i-start.y)/(end.y-start.y);
        posv = start.z + (end.z-start.z)*fract;
        posw = start.x + (end.x-start.x)*fract;
      }
      else
      {
        fract = (i-start.z)/(end.z-start.z);
        posv = start.x + (end.x-start.x)*fract;
        posw = start.y + (end.y-start.y)*fract;
      }
      int minv = (int) Math.floor(posv -r);
      int maxv = (int) Math.ceil(posv +r);
      int minw = (int) Math.floor(posw -r);
      int maxw = (int) Math.ceil(posw +r);
      for (int v = minv; v <= maxv; v++)
        for (int w = minw; w <= maxw; w++)
        {
          int x, y, z;
          if (axis == 0)
          {
            x = i;
            y = v;
            z = w;
          }
          else if (axis == 1)
          {
            y = i;
            z = v;
            x = w;
          }
          else
          {
            z = i;
            x = v;
            y = w;
          }
          pos.set(x-start.x, y-start.y, z-start.z);
          double u = pos.dot(udir);
          if (u < 0.0 || u > length)
            continue;
          double dist = Math.sqrt(pos.length2()-u*u);
          double weight;
          if (dist >= radius+1.0)
            continue;
          if (dist <= radius-1.0)
            weight = 1.0;
          else
            weight = 0.5*(radius+1.0-dist);
          float oldValue = voxels.getValue(x, y, z);
          float newValue;
          if (negative)
            newValue = (float) Math.min(oldValue, 1.0-weight);
          else
            newValue = (float) Math.max(oldValue, weight);
          if (newValue != oldValue)
            voxels.setValue(x, y, z, newValue);
        }
    }

    // Find the range of values that were modified.
    
    range[0] = Math.min(range[0], (int) Math.floor(Math.min(start.x, end.x)-radius));
    range[1] = Math.max(range[1], (int) Math.ceil(Math.max(start.x, end.x)+radius));
    range[2] = Math.min(range[2], (int) Math.floor(Math.min(start.y, start.y)-radius));
    range[3] = Math.max(range[3], (int) Math.ceil(Math.max(start.y, start.y)+radius));
    range[4] = Math.min(range[4], (int) Math.floor(Math.min(start.z, start.z)-radius));
    range[5] = Math.max(range[5], (int) Math.ceil(Math.max(start.z, start.z)+radius));
    return range;
  }
}