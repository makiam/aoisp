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

public class SmudgeVoxelsTool extends EditingTool
{
  private int width, offset;
  private float lastValues[], values[], weight[];
  private float smoothing = 1.0f;
  private int lastx, lasty, lastz;
  private Vec3 lastPos;
  private UndoRecord undo;

  public SmudgeVoxelsTool(EditingWindow win)
  {
    super(win);
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
    ObjectInfo info = mv.getWindow().getObject();
    VoxelObject obj = (VoxelObject) info.getObject();
    lastPos = findClickLocation(e.getPoint(), mv, mv.getWindow().getVoxelTracer());
    if (lastPos == null)
      return;
    VoxelObject copy = (VoxelObject) obj.duplicate();
    undo = new UndoRecord(theWindow, false, UndoRecord.COPY_OBJECT, new Object [] {obj, copy});

    // Construct workspace arrays.

    double radius = mv.getWindow().getRadius();
    offset = (int) Math.round(radius);
    width = (int) Math.floor(2*radius);
    lastValues = new float[width*width*width];
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
            d *= d;
            weight[index] = (float) (d*d-2.0*d+1.0);
          }
        }

    // Record the values at the click location.

    Vec3 coords = convertPointToVoxel(lastPos, obj);
    expandVoxelsIfNecessary(obj, coords);
    lastx = (int) Math.round(coords.x);
    lasty = (int) Math.round(coords.y);
    lastz = (int) Math.round(coords.z);
    findVoxelValues(obj.getVoxels(), lastx-offset, lasty-offset, lastz-offset, lastValues);
  }

  @Override
  public void mouseDragged(WidgetMouseEvent e, ViewerCanvas view)
  {
    if (lastPos == null)
      return;
    VoxelObjectViewer mv = (VoxelObjectViewer) view;
    ObjectInfo info = mv.getWindow().getObject();
    VoxelObject obj = (VoxelObject) info.getObject();

    // Find the drag location.

    Vec3 pos = findClickLocation(e.getPoint(), mv, mv.getWindow().getVoxelTracer());
    if (pos == null)
    {
      view.getCamera().setObjectTransform(info.getCoords().fromLocal());
      double depth = view.getCamera().getObjectToView().times(lastPos).z;
      pos = info.getCoords().toLocal().times(view.getCamera().convertScreenToWorld(e.getPoint(), depth));
    }
    lastPos = pos;
    pos = convertPointToVoxel(pos, obj);
    expandVoxelsIfNecessary(obj, pos);
    int x = (int) Math.round(pos.x);
    int y = (int) Math.round(pos.y);
    int z = (int) Math.round(pos.z);
    if (x == lastx && y == lasty && z == lastz)
      return;
    int minx = Math.min(x, lastx)-offset;
    int miny = Math.min(y, lasty)-offset;
    int minz = Math.min(z, lastz)-offset;
    int maxx = Math.max(x, lastx)-offset+width;
    int maxy = Math.max(y, lasty)-offset+width;
    int maxz = Math.max(z, lastz)-offset+width;

    // Initialize variables for stepping along the line.

    Vec3 dir = new Vec3(x-lastx, y-lasty, z-lastz);
    dir.normalize();
    int stepx, stepy, stepz;
    int finalx, finaly, finalz;
    double tdeltax, tdeltay, tdeltaz;
    double tmaxx, tmaxy, tmaxz;
    if (x < lastx)
    {
      stepx = -1;
      finalx = x-1;
      tdeltax = -1.0/dir.x;
      tmaxx = 1.0/dir.x;
    }
    else if (x > lastx)
    {
      stepx = 1;
      finalx = x+1;
      tdeltax = 1.0/dir.x;
      tmaxx = 1.0/dir.x;
    }
    else
    {
      stepx = 0;
      finalx = 0;
      tdeltax = 0.0;
      tmaxx = Double.MAX_VALUE;
    }
    if (y < lasty)
    {
      stepy = -1;
      finaly = y-1;
      tdeltay = -1.0/dir.y;
      tmaxy = 1.0/dir.y;
    }
    else if (y > lasty)
    {
      stepy = 1;
      finaly = y+1;
      tdeltay = 1.0/dir.y;
      tmaxy = 1.0/dir.y;
    }
    else
    {
      stepy = 0;
      finaly = 0;
      tdeltay = 0.0;
      tmaxy = Double.MAX_VALUE;
    }
    if (z < lastz)
    {
      stepz = -1;
      finalz = z-1;
      tdeltaz = -1.0/dir.z;
      tmaxz = 1.0/dir.z;
    }
    else if (z > lastz)
    {
      stepz = 1;
      finalz = z+1;
      tdeltaz = 1.0/dir.z;
      tmaxz = 1.0/dir.z;
    }
    else
    {
      stepz = 0;
      finalz = 0;
      tdeltaz = 0.0;
      tmaxz = Double.MAX_VALUE;
    }

    // Step from the last location to the new location.

    VoxelOctree voxels = obj.getVoxels();
    while (true)
    {
      // Look up the new values, then smooth the old and new values.

      int xbase = x-offset;
      int ybase = y-offset;
      int zbase = z-offset;
      int lastxbase = lastx-offset;
      int lastybase = lasty-offset;
      int lastzbase = lastz-offset;
      findVoxelValues(voxels, xbase, ybase, zbase, values);
      for (int i = 0; i < width; i++)
          for (int j = 0; j < width; j++)
              for (int k = 0; k < width; k++)
              {
                int index = i*width*width+j*width+k;
                if (values[index] >= lastValues[index] || weight[index] == 0.0f)
                  continue;
                float transfer = 0.5f*smoothing*weight[index]*(lastValues[index]-values[index]);
                int xindex = lastxbase+i;
                int yindex = lastybase+j;
                int zindex = lastzbase+k;
                voxels.setValue(xindex, yindex, zindex, (byte) (voxels.getValue(xindex, yindex, zindex)-transfer));
                xindex = xbase+i;
                yindex = ybase+j;
                zindex = zbase+k;
                voxels.setValue(xindex, yindex, zindex, (byte) (voxels.getValue(xindex, yindex, zindex)+transfer));
              }
      lastx = x;
      lasty = y;
      lastz = z;
      float temp[] = lastValues;
      lastValues = values;
      values = temp;

      // Advance to the next voxel.

      if (tmaxx < tmaxy)
      {
        if (tmaxx < tmaxz)
        {
          x += stepx;
          if (x == finalx)
            break;
          tmaxx += tdeltax;
        }
        else
        {
          z += stepz;
          if (z == finalz)
            break;
          tmaxz += tdeltaz;
        }
      }
      else
      {
        if (tmaxy < tmaxz)
        {
          y += stepy;
          if (y == finaly)
            break;
          tmaxy += tdeltay;
        }
        else
        {
          z += stepz;
          if (z == finalz)
            break;
          tmaxz += tdeltaz;
        }
      }
    }

    // Update the view.

    VoxelObjectEditorWindow win = (VoxelObjectEditorWindow) theWindow;
    win.objectChanged();
    win.getVoxelTracer().updateFlags(minx, maxx, miny, maxy, minz, maxz);
//    theWindow.updateImage();
    view.repaint();
  }

  @Override
  public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
  {
    if (undo != null)
      theWindow.setUndoRecord(undo);
    theWindow.updateImage();
    theWindow.setHelpText(Translate.text("reshapeMeshTool.helpText"));
    undo = null;
    lastValues = null;
    values = null;
    weight = null;
  }

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