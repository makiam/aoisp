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
import artofillusion.view.*;
import artofillusion.math.*;
import buoy.widget.*;
import buoy.event.*;

import java.awt.*;
import java.awt.image.*;

public class VoxelObjectViewer extends ViewerCanvas
{
  private VoxelObjectEditorWindow window;

  public VoxelObjectViewer(VoxelObjectEditorWindow window, RowContainer controls)
  {
    super(false);
    this.window = window;
    buildChoices(controls);
  }

  public VoxelObjectEditorWindow getWindow()
  {
    return window;
  }

  public double[] estimateDepthRange()
  {
    return new double[0];
  }

  @Override
  public void updateImage()
  {
    super.updateImage();

    // Draw the rest of the objects in the scene.

//    if (showScene && window.getScene() != null)
//    {
//      Scene theScene = window.getScene();
//      Vec3 viewdir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
//      for (int i = 0; i < theScene.getNumObjects(); i++)
//      {
//        ObjectInfo obj = theScene.getObject(i);
//        if (!obj.isVisible() || obj == thisObjectInScene)
//          continue;
//        Mat4 objectTransform = obj.getCoords().fromLocal();
//        if (!useWorldCoords && thisObjectInScene != null)
//          objectTransform = thisObjectInScene.getCoords().toLocal().times(objectTransform);
//        theCamera.setObjectTransform(objectTransform);
//        obj.getObject().renderObject(obj, this, viewdir);
//      }
//    }

    // Draw the object being edited.

//    theCamera.setObjectTransform(getDisplayCoordinates().fromLocal());
//    drawObject();

//    theCamera.setObjectTransform(window.getObject().getCoords().fromLocal());
//    RenderingMesh mesh = window.getObject().getPreviewMesh();
//    Vec3 viewDir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
//    VertexShader shader = new FlatVertexShader(mesh, surfaceRGBColor, viewDir);
//    renderMesh(window.getObject().getPreviewMesh(), shader, theCamera, true, null);

    Rectangle bounds = getBounds();
    ThreadManager threads = getWindow().getThreadManager();
    threads.setNumIndices(bounds.width*bounds.height);
    threads.setTask(new RenderTask());
    threads.run();

    // Finish up.

    drawBorder();
    if (showAxes)
      drawCoordinateAxes();
  }
  protected void mousePressed(WidgetMouseEvent e)
  {
    requestFocus();

    // Determine which tool is active.

    if (metaTool != null && e.isMetaDown())
      activeTool = metaTool;
    else if (altTool != null && e.isAltDown())
      activeTool = altTool;
    else
      activeTool = currentTool;
    activeTool.mousePressed(e, this);
  }

  protected void mouseDragged(WidgetMouseEvent e)
  {
    activeTool.mouseDragged(e, this);
  }

  protected void mouseReleased(WidgetMouseEvent e)
  {
    activeTool.mouseReleased(e, this);
    currentTool.getWindow().updateMenus();
  }

  /**
   * This is a ThreadManager task for rendering the viewing in parallel.
   */

  private class RenderTask implements ThreadManager.Task
  {
    private int width;
    private Vec3 origin, direction, base, dx, dy, viewDir;
    private int[] pixel;
    private VoxelTracer tracer;
    ThreadLocal<ThreadInfo> threadInfo = new ThreadLocal<ThreadInfo>() {
      @Override
      protected ThreadInfo initialValue()
      {
        ThreadInfo info = new ThreadInfo();
        info.origin = new Vec3(origin);
        info.direction = new Vec3(direction);
        info.normal = new Vec3();
        info.color = new RGBColor();
        return info;
      }
    };

    public RenderTask()
    {
      Mat4 toLocal = getWindow().getObject().getCoords().toLocal();
      if (theCamera.isPerspective())
      {
        origin = theCamera.getCameraCoordinates().getOrigin();
        toLocal.transform(origin);
        direction = new Vec3();
        base = theCamera.convertScreenToWorld(new Point(0, 0), 1.0, false);
        dx = theCamera.convertScreenToWorld(new Point(1, 0), 1.0, false).minus(base);
        dy = theCamera.convertScreenToWorld(new Point(0, 1), 1.0, false).minus(base);
      }
      else
      {
        origin = new Vec3();
        direction = theCamera.getCameraCoordinates().getZDirection();
        toLocal.transformDirection(direction);
        base = theCamera.convertScreenToWorld(new Point(0, 0), 0.0, false);
        dx = theCamera.convertScreenToWorld(new Point(1, 0), 0.0, false).minus(base);
        dy = theCamera.convertScreenToWorld(new Point(0, 1), 0.0, false).minus(base);
      }
      width = getBounds().width;
      viewDir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
      SoftwareCanvasDrawer drawer = (SoftwareCanvasDrawer) getCanvasDrawer();
      pixel = ((DataBufferInt) ((BufferedImage) drawer.getImage()).getRaster().getDataBuffer()).getData();
      tracer = getWindow().getVoxelTracer();
    }

    public void execute(int index)
    {
      int i = index%width;
      int j = index/width;
      ThreadInfo info = threadInfo.get();
      if (theCamera.isPerspective())
      {
        info.direction.set(base.x+i*dx.x+j*dy.x, base.y+i*dx.y+j*dy.y, base.z+i*dx.z+j*dy.z);
        info.direction.subtract(origin);
        info.direction.normalize();
      }
      else
      {
        info.origin.set(base.x+i*dx.x+j*dy.x, base.y+i*dx.y+j*dy.y, base.z+i*dx.z+j*dy.z);
      }
      double dist = tracer.findRayIntersection(info.origin, info.direction, info.normal);
      if (dist > 0)
      {
        info.color.copy(surfaceRGBColor);
        info.color.scale(0.1f+0.8f*Math.abs((float) viewDir.dot(info.normal)));
        pixel[i+j*width] = info.color.getARGB();
      }
    }

    public void cleanup()
    {
    }

    private class ThreadInfo
    {
      public Vec3 origin, direction, normal;
      public RGBColor color;
    }
  }
}
